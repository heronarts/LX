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
import heronarts.lx.clip.LXClip.Cursor;
import heronarts.lx.parameter.MutableParameter;

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

  protected int cursorPlayIndex(Cursor cursor) {
    int left = 0;
    int right = this.events.size() - 1;

    // Starting assumption is everything is < cursor until
    // we find something >= cursor
    int result = right + 1;

    while (left <= right) {
      int mid = left + (right - left) / 2;
      if (this.events.get(mid).cursor.isAfterOrEqual(cursor)) {
        // If the current element is greater or equal, it is a potential result,
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

  protected int cursorInsertIndex(Cursor cursor) {
    int left = 0;
    int right = this.events.size() - 1;

    // Starting assumption is everything is <= cursor until
    // we find something > cursor
    int result = right + 1;

    while (left <= right) {
      int mid = left + (right - left) / 2;
      if (this.events.get(mid).cursor.isAfter(cursor)) {
        // If the current element is greater, it could be a potential result,
        // but something to the left could still be greater than us
        result = mid;
        right = mid - 1;
      } else {
        // Nope, look on the right side
        left = mid + 1;
      }
    }
    return result;
  }

  private void _insertEvent(T event) {
    if (event.cursor.isAfterOrEqual(lastEventCursor())) {
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

  public LXClipLane<T> moveEvent(T event, LXClip.Cursor cursor) {
    Cursor min = Cursor.ZERO;
    Cursor max = this.clip.length.cursor;
    final int index = this.events.indexOf(event);
    if (index > 0) {
      min = this.events.get(index-1).cursor;
    }
    if (index < this.events.size() - 1) {
      max = this.events.get(index+1).cursor;
    }
    cursor.constrain(min, max);
    if (!event.cursor.equals(cursor)) {
      event.cursor.set(cursor);
      this.onChange.bang();
    }
    return this;
  }

  /**
   * Gets the last event occurring before this cursor value, if any. Events
   * already in the array with a cursor exactly equal to this cursor are
   * continued to all be previous.
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
   * Gets the last event in the lane occuring at or before the time value
   * of the current cursor position.
   *
   * @return Last event equal to or before this cursor position
   */
  protected T getPreviousEvent() {
    return getPreviousEvent(this.clip.cursor);
  }

  public void setEventsCursors(Map<T, Cursor> cursors) {
    boolean changed = false;

    // TODO(clips): we could probably make this a lot more efficient with stricter
    // assumptions about the values coming in, whether re-ordering may have occurred
    // or not... but in the meantime we do an insertion-sort per-element to avoid
    // mucking up the state
    //
    // Almost surely want to improve this because it's currently an underlying
    // CopyOnWriteArrayList which will make *many* copies if we edit loads of
    // items at once here. Make our own copy and clear()/addAll() or we should use
    // the stable .sort() method, or possibly Arrays.sort() methods that can sort
    // a sub-range of the array if we know there's no overlapping!
    //
    // Also a problem right now that the ordering of the Map matters... it needs to be
    // a linked hashmap, or we need to do a stable sort
    for (Map.Entry<T, Cursor> entry : cursors.entrySet()) {
      final T event = entry.getKey();
      if (this.events.contains(event)) {
        this.mutableEvents.remove(event);
        event.setCursor(entry.getValue().constrain(this.clip.length.cursor));
        _insertEvent(event);
        changed = true;
      } else {
        LX.error("LXClipLane.setEventsCursors contains an event not in the events array: " + event);
      }
    }
    if (changed) {
      this.onChange.bang();
    }
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
    final ListIterator<T> iter = this.events.listIterator(cursorPlayIndex(from));
    while (iter.hasNext()) {
      T event = iter.next();
      if (event.cursor.isAfterOrEqual(to)) {
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

  void advanceCursor(Cursor from, Cursor to) {
    final ListIterator<T> iter = this.events.listIterator(cursorPlayIndex(from));
    while (iter.hasNext()) {
      T event = iter.next();
      if (event.cursor.isAfterOrEqual(to)) {
        break;
      }
      event.execute();
    }
  }

  public boolean removeRange(Cursor from, Cursor to) {
    final List<LXClipEvent<?>> toRemove = new ArrayList<>();
    final ListIterator<T> iter = this.events.listIterator(cursorPlayIndex(from));
    while (iter.hasNext()) {
      T event = iter.next();
      if (event.cursor.isAfter(to)) {
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
