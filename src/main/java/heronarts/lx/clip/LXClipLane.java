/**
 * Copyright 2017- Mark C. Slee, Heron Arts LLC
 *
 * This file is part of the LX Studio software library. By using
 * LX, you agree to the terms of the LX Studio Software License
 * and Distribution Agreement, available at: http://lx.studio/license
 *
 * Please note that the LX license is not open-source. The license
 * allows for free, non-commercial use.
 *
 * HERON ARTS MAKES NO WARRANTY, EXPRESS, IMPLIED, STATUTORY, OR
 * OTHERWISE, AND SPECIFICALLY DISCLAIMS ANY WARRANTY OF
 * MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR
 * PURPOSE, WITH RESPECT TO THE SOFTWARE.
 *
 * @author Mark C. Slee <mark@heronarts.com>
 */

package heronarts.lx.clip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.utils.LXUtils;

public abstract class LXClipLane<T extends LXClipEvent<?>> extends LXComponent {

  public final MutableParameter uiHeight = new MutableParameter("UI Height");

  public final MutableParameter onChange = new MutableParameter();

  public final LXClip clip;

  protected boolean overdubActive = false;
  protected T overdubLastOriginalEvent = null;

  // NOTE(mcslee): think about whether CopyOnWrite is the best solution here for UI drawing
  // or whether synchronized or locking around multi-edits is preferable, as those are currently
  // going to be super costly
  protected final CopyOnWriteArrayList<T> mutableEvents = new CopyOnWriteArrayList<>();
  public final List<T> events = Collections.unmodifiableList(this.mutableEvents);

  protected Cursor.Operator CursorOp() {
    return this.clip.CursorOp();
  }

  protected LXClipLane(LXClip clip) {
    setParent(clip);
    this.clip = clip;
    addInternalParameter("uiHeight", this.uiHeight);
  }

  void resetOverdub() {
    this.overdubActive = false;
    this.overdubLastOriginalEvent = null;
  }

  public int getIndex() {
    return this.clip.lanes.indexOf(this);
  }

  private Cursor lastEventCursor() {
    if (!this.events.isEmpty()) {
      return this.events.get(this.events.size() - 1).cursor;
    }
    return Cursor.ZERO;
  }

  final List<T> recordQueue = new ArrayList<>();

  protected LXClipLane<T> recordEvent(T event) {
    this.recordQueue.add(event);
    return this;
  }

  LXClipLane<T> commitRecordEvents() {
    for (T event : this.recordQueue) {
      _insertEvent(event);
    }
    this.recordQueue.clear();
    this.onChange.bang();
    return this;
  }

  private ListIterator<T> eventIterator(List<T> events, Cursor fromCursor, boolean inclusive) {
    int index = LXUtils.constrain(_cursorIndex(events, fromCursor, inclusive), 0, events.size());
    return events.listIterator(index);
  }

  /**
   * Gets an iterator over this clip lane's events, starting from the position specified
   * by the cursor. The iterator will start at the first event with time equal to or after
   * that cursor.
   *
   * @param fromCursor Cursor to begin iteration from (inclusive)
   * @return Iterator over events equal to or after the cursor
   */
  public ListIterator<T> eventIterator(Cursor fromCursor) {
    return eventIterator(fromCursor, 0);
  }

  /**
   * Gets an iterator over this clip lane's events, starting from the position specified
   * by the cursor. The iterator will start at the first event with time equal to or after
   * that cursor, with an offset specified in # of events
   *
   * @param fromCursor Cursor to begin iteration from (inclusive)
   * @param offset Offset the iterator by a number of events from the cursor
   * @return Iterator over events equal to or after the cursor, plus offset
   */
  public ListIterator<T> eventIterator(Cursor fromCursor, int offset) {
    int index = LXUtils.constrain(cursorPlayIndex(fromCursor) + offset, 0, this.events.size());
    return this.mutableEvents.listIterator(index);
  }

  private int _cursorIndex(List<T> events, Cursor cursor, boolean inclusive) {
    final int geq = inclusive ? -1 : 0;
    int left = 0;
    int right = events.size() - 1;

    // Starting assumption is everything is < cursor until
    // we find something >= cursor
    int result = right + 1;

    final Cursor.Operator CursorOp = CursorOp();

    while (left <= right) {
      int mid = left + (right - left) / 2;
      if (CursorOp.compare(events.get(mid).cursor, cursor) > geq) {
        // If the current element is greater (or equal), it is a potential result,
        // but something to the left could still also be >=, and we want the lowest
        // one that is equal
        result = mid;
        right = mid - 1;
      } else {
        // Nope, look on the right side
        left = mid + 1;
      }
    }
    return result;
  }

  protected int cursorPlayIndex(Cursor cursor) {
    return _cursorIndex(this.events, cursor, true);
  }

  protected int cursorInsertIndex(Cursor cursor) {
    return _cursorIndex(this.events, cursor, false);
  }

  private void _insertEvent(T event) {
    if (CursorOp().isAfterOrEqual(event.cursor, lastEventCursor())) {
      // Quick check... shortcut in normal recording mode when we're not
      // overdubbing and the cursor is past all the prior events anyways
      this.mutableEvents.add(event);
    } else {
      this.mutableEvents.add(cursorInsertIndex(event.cursor), event);
    }
  }

  public LXClipLane<T> insertEvent(T event) {
    _insertEvent(event);
    this.onChange.bang();
    return this;
  }

  public LXClipLane<T> moveEvent(T event, Cursor cursor) {
    Cursor min = Cursor.ZERO;
    Cursor max = this.clip.length.cursor;
    final int index = this.events.indexOf(event);
    if (index > 0) {
      min = this.events.get(index-1).cursor;
    }
    if (index < this.events.size() - 1) {
      max = this.events.get(index+1).cursor;
    }
    CursorOp().constrain(cursor, min, max);
    if (!event.cursor.equals(cursor)) {
      event.cursor.set(cursor);
      this.onChange.bang();
    }
    return this;
  }

  /**
   * Gets the last event occurring before this cursor insert position, if any. Events
   * already in the array with a cursor exactly equal to this cursor are
   * considered to all be previous.
   *
   * @param cursor Cursor position
   * @return Last event with time equal to or less than this cursor
   */
  protected T getPreviousEvent(Cursor cursor) {
    int previousIndex = cursorInsertIndex(cursor) - 1;
    if (previousIndex >= 0) {
      return this.events.get(previousIndex);
    }
    return null;
  }

  /**
   * Gets the last event in the lane occurring at or before the time value
   * of the current cursor position.
   *
   * @return Last event equal to or before this cursor position
   */
  protected T getPreviousEvent() {
    return getPreviousEvent(this.clip.cursor);
  }

  /**
   * Set the cursors for a set of events in a range. This event will also destructively clobber any events
   * that the new stretched selection range overlaps with (other than those strictly contained in the set
   * of modified cursors).
   *
   * @param originalEvents The original reference event list to modify
   * @param fromSelectionMin Original lower bound on selection range
   * @param fromSelectionMax Original upper bound on selection range
   * @param toSelectionMin New lower bound on selection range
   * @param toSelectionMax New upper bound on selection range
   * @param fromCursors Ordered map of original position of events pre-modification
   * @param toCursors Ordered map of events to re-position from within the original range
   * @param reverse Whether the events have been reversed (e.g. by dragging start past end or vice-versa)
   */
  public void setEventsCursors(List<T> originalEvents, Cursor fromSelectionMin, Cursor fromSelectionMax, Cursor toSelectionMin, Cursor toSelectionMax, Map<T, Cursor> fromCursors, Map<T, Cursor> toCursors, boolean reverse) {
    // Only reverse if there's actually content to reverse!
    reverse = reverse && !toCursors.isEmpty();

    final Cursor.Operator CursorOp = CursorOp();

    // Put everything back how it was, note that this may be called many times in the course of a
    // mouse drag operation, we need to operate on the original array with the modified events in their
    // initial position.
    for (Map.Entry<T, Cursor> entry : fromCursors.entrySet()) {
      final T event = entry.getKey();
      event.setCursor(entry.getValue());
    }

    // Test if the selection bounds have expanded, if so we will nuke any events
    // that lie outside of the original selection bounds but within the new
    // selection bounds
    final List<T> removeEvents = new ArrayList<>();
    if (CursorOp.isBefore(toSelectionMin, fromSelectionMin)) {
      ListIterator<T> iter = eventIterator(originalEvents, toSelectionMin, true);
      while (iter.hasNext()) {
        T removeEvent = iter.next();
        if (!CursorOp.isBefore(removeEvent.cursor, fromSelectionMin)) {
          break;
        }
        removeEvents.add(removeEvent);
      }
    }
    if (CursorOp.isBefore(fromSelectionMax, toSelectionMax)) {
      ListIterator<T> iter = eventIterator(originalEvents, fromSelectionMax, false);
      while (iter.hasNext()) {
        T removeEvent = iter.next();
        if (CursorOp.isAfter(removeEvent.cursor, toSelectionMax)) {
          break;
        }
        removeEvents.add(removeEvent);
      }
    }

    // We're gonna need a mutable copy of the original events, either to chop
    // stuff out of, or to reverse the order of the modified section
    if (!removeEvents.isEmpty() || reverse) {
      originalEvents = new ArrayList<T>(originalEvents);
      originalEvents.removeAll(removeEvents);
      if (reverse) {
        // These will be put back in reverse-order in the loop below
        originalEvents.removeAll(toCursors.keySet());
      }
    }

    // There are cursors that need modifying
    if (!toCursors.isEmpty()) {
      final int insertIndex = reverse ? _cursorIndex(originalEvents, toSelectionMin, false) : 0;
      for (Map.Entry<T, Cursor> entry : toCursors.entrySet()) {
        final T event = entry.getKey();
        if (reverse) {
          originalEvents.add(insertIndex, event);
        }
        event.setCursor(entry.getValue().bound(this.clip));
      }
    }

    this.mutableEvents.clear();
    this.mutableEvents.addAll(originalEvents);
    this.onChange.bang();
  }

  @Override
  public abstract String getLabel();

  /**
   * Subclasses may override this method if they need to take an action when
   * looping is performed and the cursor returns to a prior position.
   */
  void loopCursor(Cursor to) {}

  void overdubCursor(Cursor from, Cursor to) {
    final List<T> toRemove = new ArrayList<>();
    final ListIterator<T> iter = eventIterator(from);
    final Cursor.Operator CursorOp = CursorOp();
    while (iter.hasNext()) {
      T event = iter.next();
      if (CursorOp.isAfterOrEqual(event.cursor, to)) {
        break;
      }
      this.overdubLastOriginalEvent = event;
      if (this.overdubActive) {
        toRemove.add(event);
      }
    }
    if (!toRemove.isEmpty()) {
      this.mutableEvents.removeAll(toRemove);
      this.onChange.bang();
    }
  }

  void postOverdubCursor(Cursor from, Cursor to) {}

  void advanceCursor(Cursor from, Cursor to, boolean inclusive) {
    final Cursor.Operator CursorOp = CursorOp();
    final int limit = inclusive ? 0 : -1;
    final ListIterator<T> iter = eventIterator(from);
    while (iter.hasNext()) {
      T event = iter.next();
      if (CursorOp.compare(event.cursor, to) > limit) {
        break;
      }
      event.execute();
    }
  }

  public boolean removeRange(Cursor from, Cursor to) {
    final List<LXClipEvent<?>> toRemove = new ArrayList<>();
    final ListIterator<T> iter = eventIterator(from);
    final Cursor.Operator CursorOp = CursorOp();
    while (iter.hasNext()) {
      T event = iter.next();
      if (CursorOp.isAfter(event.cursor, to)) {
        break;
      }
      toRemove.add(event);
    }

    // Do the removal in a single operation, since we are using
    // a CopyOnWriteArrayList
    if (!toRemove.isEmpty()) {
      this.mutableEvents.removeAll(toRemove);
      this.onChange.bang();
      return true;

    }
    return false;
  }

  public LXClipLane<T> removeEvent(T event) {
    this.mutableEvents.remove(event);
    this.onChange.bang();
    return this;
  }

  void clear() {
    this.mutableEvents.clear();
    this.onChange.bang();
  }

  private static final String KEY_EVENTS = "events";
  protected static final String KEY_LANE_TYPE = "laneType";
  protected static final String VALUE_LANE_TYPE_PARAMETER = "parameter";
  protected static final String VALUE_LANE_TYPE_PATTERN = "pattern";
  protected static final String VALUE_LANE_TYPE_MIDI_NOTE = "midiNote";

  @Override
  public void load(LX lx, JsonObject obj) {
    this.mutableEvents.clear();
    if (obj.has(KEY_EVENTS)) {
      List<T> loadEvents = new ArrayList<>();

      JsonArray eventsArr = obj.get(KEY_EVENTS).getAsJsonArray();
      for (JsonElement eventElem : eventsArr) {
        JsonObject eventObj = eventElem.getAsJsonObject();
        T event = loadEvent(lx, eventObj);
        if (event != null) {
          event.load(lx, eventObj);
          loadEvents.add(event);
        }
      }

      // Because we're using an underlying CopyOnWriteArrayList, do this
      // in a single addAll() operation
      if (!loadEvents.isEmpty()) {
        this.mutableEvents.addAll(loadEvents);
      }
    }
    this.onChange.bang();
  }

  protected abstract T loadEvent(LX lx, JsonObject eventObj);

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    if (this instanceof ParameterClipLane) {
      obj.addProperty(KEY_LANE_TYPE, VALUE_LANE_TYPE_PARAMETER);
    } else if (this instanceof PatternClipLane) {
      obj.addProperty(KEY_LANE_TYPE, VALUE_LANE_TYPE_PATTERN);
    } else if (this instanceof MidiNoteClipLane) {
      obj.addProperty(KEY_LANE_TYPE, VALUE_LANE_TYPE_MIDI_NOTE);
    }
    obj.add(KEY_EVENTS, LXSerializable.Utils.toArray(lx, this.events));
  }


}
