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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.command.LXCommand.Clip.Event.SetCursors.Operation;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.utils.LXEngineThreadArrayList;
import heronarts.lx.utils.LXUtils;

public abstract class LXClipLane<T extends LXClipEvent<?>> extends LXComponent {

  public final MutableParameter uiHeight = new MutableParameter("UI Height");

  public final MutableParameter onChange = new MutableParameter();

  public final LXClip clip;

  protected boolean overdubActive = false;
  protected T overdubLastOriginalEvent = null;

  protected final LXEngineThreadArrayList<T> mutableEvents = new LXEngineThreadArrayList<>();

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

  public List<T> getUIThreadEvents() {
    return this.mutableEvents.getUIThreadList();
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
    return this.events.listIterator(index);
  }

  /**
   * Gets an iterator over this clip lane's events, starting from the position specified
   * by the cursor. The iterator will start at the first event with time equal to or after
   * that cursor, with an offset specified in # of events
   *
   * @param events Event list
   * @param fromCursor Cursor to begin iteration from (inclusive)
   * @param offset Offset the iterator by a number of events from the cursor
   * @return Iterator over events equal to or after the cursor, plus offset
   */
  public ListIterator<T> eventIterator(List<T> events, Cursor fromCursor, int offset) {
    int index = LXUtils.constrain(cursorPlayIndex(fromCursor) + offset, 0, events.size());
    return events.listIterator(index);
  }

  /**
   * Gets an iterator over the the events beginning at a given cursor position
   *
   * @param events Events to get an iterator for
   * @param fromCursor Cursor to iterate from
   * @param inclusive Whether to include events strictly at fromCursor
   * @return Iterator beginning at fromCursor
   */
  public ListIterator<T> eventIterator(List<T> events, Cursor fromCursor, boolean inclusive) {
    int index = LXUtils.constrain(_cursorIndex(events, fromCursor, inclusive), 0, events.size());
    return events.listIterator(index);
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

  protected int cursorPlayIndex(List<T> events, Cursor cursor) {
    return _cursorIndex(events, cursor, true);
  }

  protected int cursorInsertIndex(List<T> events, Cursor cursor) {
    return _cursorIndex(events, cursor, false);
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
   * Clears events from the array in given range, inclusive
   *
   * @param events Events array
   * @param from Start cursor position, inclusive
   * @param to End cursor position, inclusive
   */
  protected void clearEvents(List<T> events, Cursor from, Cursor to) {
    int clearFrom = cursorPlayIndex(events, from);
    int clearTo = cursorInsertIndex(events, to);
    if (clearTo > clearFrom) {
      events.subList(clearFrom, clearTo).clear();
    }
  }

  /**
   * Gets the last event occurring before this cursor insert position, if any. Events
   * already in the array with a cursor exactly equal to this cursor are
   * considered to all be previous.
   *
   * @param events List of events
   * @param cursor Cursor position
   * @return Last event with time equal to or less than this cursor
   */
  protected T getPreviousEvent(List<T> events, Cursor cursor) {
    int previousIndex = cursorInsertIndex(events, cursor) - 1;
    if (previousIndex >= 0) {
      return events.get(previousIndex);
    }
    return null;
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
   * @param fromValues Ordered map of original event values, pre-modification
   * @param fromCursors Ordered map of original position of events pre-modification
   * @param toCursors Ordered map of events to re-position from within the original range
   * @param operation What kind of modification operation this is
   */
  public void setEventsCursors(ArrayList<T> originalEvents, Cursor fromSelectionMin, Cursor fromSelectionMax, Cursor toSelectionMin, Cursor toSelectionMax, Map<T, Double> fromValues, Map<T, Cursor> fromCursors, Map<T, Cursor> toCursors, Operation operation) {

    // NOTE(mcslee): Let it stand for the record that attempting to generalize the logic in this method
    // was outrageously painful. The number of stitching cases for expansion/contraction/overlapping-moves/reverses
    // is insane, and obsessing over this put me in a foul mood for multiple days. I still kind of believe
    // there must be a more elegant solution than the below, but this was the best I could do without special-casing
    // it all out into a switch statement by Operation type (which was explored and would mean maintaining a *lot* more
    // code). All that to say, tread carefully if modifying this logic.

    final boolean reverse = operation.isReverse();
    final boolean clear = operation.isClear();

    // Put everything back how it was, note that this may be called many times in the course of a
    // mouse drag operation, we need to operate on the original array with the modified events in their
    // initial positions (and potentially values).
    for (Map.Entry<T, Cursor> entry : fromCursors.entrySet()) {
      entry.getKey().setCursor(entry.getValue());
    }
    // NOTE(mcslee): very tricky case... full explanation in ParameterClipLane.reverseEvents
    for (Map.Entry<T, Double> entry : fromValues.entrySet()) {
      setEventNormalized(entry.getKey(), entry.getValue());
    }

    // Was this a non-edit? Bail fast after restoring original state
    if (operation == Operation.NONE) {
      this.mutableEvents.set(originalEvents);
      this.onChange.bang();
      return;
    }

    final Cursor.Operator CursorOp = CursorOp();

    // Make our own mutable copy of the original events
    originalEvents = new ArrayList<T>(originalEvents);

    // Dummy variables for additional points added to stitch together the edit
    // with existing data
    T stitchInnerMin = null, stitchInnerMax = null, stitchOuterMin = null, stitchOuterMax = null;

    // If the destination selection bounds are outside of the source bounds,
    // compute the outer stitch values from the original data, falling totally
    // outside of modifiedEvents
    if (CursorOp.isBefore(toSelectionMin, fromSelectionMin)) {
      // STRETCH_TO_LEFT, MOVE_LEFT, REVERSE_RIGHT_TO_LEFT
      stitchOuterMin = stitchOuterMin(originalEvents, toSelectionMin);
    }
    if (CursorOp.isAfter(toSelectionMax, fromSelectionMax)) {
      // STRETCH_TO_RIGHT, MOVE_RIGHT, REVERSE_LEFT_TO_RIGHT
      stitchOuterMax = stitchOuterMax(originalEvents, toSelectionMax);
    }

    // Determine all the stuff that's being modified, we may need apply stitching
    // to it.
    int stitchFrom = cursorPlayIndex(originalEvents, fromSelectionMin);
    int stitchTo = cursorInsertIndex(originalEvents, fromSelectionMax);
    // NOTE(mcslee): subList.clear() is the most efficient way to remove a range from an ArrayList
    List<T> subList = originalEvents.subList(stitchFrom, stitchTo);
    ArrayList<T> modifiedEvents = new ArrayList<T>(stitchTo - stitchFrom);
    for (T copy : subList) {
      modifiedEvents.add(copy); // avoids spurious toArray() copies from using Collections.
    }
    subList.clear();

    // Add stitches on the inner ends of the modified range, if needed
    stitchInnerMin = stitchSelectionMin(originalEvents, modifiedEvents, fromSelectionMin, stitchFrom, clear);
    stitchInnerMax = stitchSelectionMax(originalEvents, modifiedEvents, fromSelectionMax, stitchFrom, clear);

    // Clear operation? e.g. drag-resized to 0, nuke everything
    if (clear) {
      modifiedEvents.clear();
    }
    // Add the inner stitches (note that these are POST-clear - for a clear operation we replace the whole
    // range by its start/end boundary values at this single point in time
    if (stitchInnerMin != null) {
      modifiedEvents.add(0, stitchInnerMin);
    }
    if (stitchInnerMax != null) {
      modifiedEvents.add(stitchInnerMax);
    }

    // If the destination selection bounds are within or cross over the source bounds (in the case of MOVE)
    // compute the outer stitch values now, they'll be the edges of the selection range
    if (CursorOp.isAfter(toSelectionMin, fromSelectionMin)) {
      // SHORTEN_FROM_LEFT, CLEAR_FROM_LEFT, REVERSE_LEFT_TO_RIGHT, MOVE_RIGHT
      if (operation == Operation.MOVE_RIGHT) {
        stitchOuterMin = stitchOuterMin(originalEvents, toSelectionMin);
      } else {
        stitchOuterMin = stitchSelectionMin(originalEvents, modifiedEvents, fromSelectionMin, stitchFrom, true);
      }
    }
    if (CursorOp.isBefore(toSelectionMax, fromSelectionMax)) {
      // SHORTEN_FROM_RIGHT, CLEAR_FROM_RIGHT, REVERSE_RIGHT_TO_LEFT, MOVE_LEFT
      if (operation == Operation.MOVE_LEFT) {
        stitchOuterMax = stitchOuterMax(originalEvents, toSelectionMax);
      } else {
        stitchOuterMax = stitchSelectionMax(originalEvents, modifiedEvents, fromSelectionMax, stitchFrom, true);
      }
    }

    // Reverse the modified stuff
    if (reverse) {
      reverseEvents(modifiedEvents);
    }

    // Remove everything pre-existing in the target range
    clearEvents(originalEvents, toSelectionMin, toSelectionMax);

    // Update the cursor positions (unless we cleared them off)
    if (!clear) {
      for (Map.Entry<T, Cursor> entry : toCursors.entrySet()) {
        entry.getKey().setCursor(entry.getValue().bound(this.clip));
      }
    }

    // Move the internal stitches to their now positions
    if (stitchInnerMin != null) {
      stitchInnerMin.setCursor(reverse ? toSelectionMax : toSelectionMin);
    }
    if (stitchInnerMax != null) {
      stitchInnerMax.setCursor(reverse ? toSelectionMin : toSelectionMax);
    }

    // Put all the modified stuff back
    int stitchIndex = cursorInsertIndex(originalEvents, toSelectionMin);
    originalEvents.addAll(stitchIndex, modifiedEvents);
    int numModified = modifiedEvents.size();

    // Add outer stitches if needed
    if (stitchOuterMin != null) {
      if (stitchInsertIfNeeded(originalEvents, stitchOuterMin, stitchIndex)) {
        ++stitchIndex;
      }
    }
    if (stitchOuterMax != null) {
      stitchInsertIfNeeded(originalEvents, stitchOuterMax, stitchIndex + numModified);
    }

    // Remove the inner stitches if they're pointless
    if (stitchRemoveIfRedundant(originalEvents, stitchInnerMin, reverse ? stitchIndex + numModified - 1 : stitchIndex)) {
      --numModified;
    }
    stitchRemoveIfRedundant(originalEvents, stitchInnerMax, reverse ? stitchIndex : stitchIndex + numModified - 1);

    this.mutableEvents.clear();
    this.mutableEvents.addAll(originalEvents);
    this.onChange.bang();
  }

  protected void reverseEvents(List<T> events) {
    Collections.reverse(events);
  }

  protected void setEventNormalized(T event, double value) {}

  protected T stitchSelectionMin(List<T> originalEvents, List<T> modifiedEvents, Cursor selectionMin, int stitchIndex, boolean force) {
    return null;
  }

  protected T stitchSelectionMax(List<T> originalEvents, List<T> modifiedEvents, Cursor selectionMax, int stitchIndex, boolean force) {
    return null;
  }

  protected T stitchOuterMin(List<T> events, Cursor selectionMin) {
    return null;
  }

  protected T stitchOuterMax(List<T> events, Cursor selectionMax) {
    return null;
  }

  protected boolean stitchInsertIfNeeded(List<T> events, T stitch, int index) {
    events.add(index, stitch);
    return true;
  }

  protected boolean stitchRemoveIfRedundant(List<T> events, T stitch, int index) {
    return false;
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
