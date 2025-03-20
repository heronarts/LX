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
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.utils.LXEngineThreadArrayList;
import heronarts.lx.utils.LXUtils;

public abstract class LXClipLane<T extends LXClipEvent<?>> extends LXComponent {

  public final MutableParameter uiHeight = new MutableParameter("UI Height");
  public final BooleanParameter uiExpanded = new BooleanParameter("UI Expanded", true);
  public final BooleanParameter uiMaximized = new BooleanParameter("UI Maximized", false);

  public final MutableParameter onChange = new MutableParameter();

  public final LXClip clip;

  protected boolean overdubActive = false;

  protected final LXEngineThreadArrayList<T> mutableEvents = new LXEngineThreadArrayList<>();

  public final List<T> events = Collections.unmodifiableList(this.mutableEvents);

  protected Cursor.Operator CursorOp() {
    return this.clip.CursorOp();
  }

  protected LXClipLane(LXClip clip) {
    setParent(clip);
    this.clip = clip;
    addInternalParameter("uiHeight", this.uiHeight);
    addInternalParameter("uiExpanded", this.uiExpanded);
    addInternalParameter("uiMaximized", this.uiMaximized);
  }

  void resetOverdub() {
    this.overdubActive = false;
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

  protected final LXClipLane<T> recordEvent(T event) {
    this.recordQueue.add(event);
    return this;
  }

  LXClipLane<T> commitRecordQueue(boolean notify) {
    if (!this.recordQueue.isEmpty()) {
      this.mutableEvents.begin();
      for (T event : this.recordQueue) {
        _insertEvent(event);
      }
      this.recordQueue.clear();
      this.mutableEvents.commit();
      if (notify) {
        this.onChange.bang();
      }
    }
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
    return events.listIterator(eventIndex(events, fromCursor, inclusive));
  }

  public int eventIndex(List<T> events, Cursor fromCursor, boolean inclusive) {
    return eventIndex(events, fromCursor, inclusive, 0);
  }

  public int eventIndex(List<T> events, Cursor fromCursor, boolean inclusive, int offset) {
    return LXUtils.constrain(_cursorIndex(events, fromCursor, inclusive) + offset, 0, events.size());
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

  protected void _insertEvent(T event) {
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
    // is insane, and obsessing over this put me in a foul mood for multiple days. After lots of back and forth,
    // I ended up going back to an explicit switch expression that spells out the precise behavior of each
    // particular operation.

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

    final boolean reverse = operation.isReverse();
    final boolean clear = operation.isClear();

    // Make our own mutable copy of the original events
    originalEvents = new ArrayList<T>(originalEvents);

    // Determine selection bounds
    int selectFrom = cursorPlayIndex(originalEvents, fromSelectionMin);
    int selectTo = cursorInsertIndex(originalEvents, fromSelectionMax);
    int deleteFrom = -1, deleteTo = -1;
    int moveTo = -1;

    // Dummy variables for additional points added to stitch together the edit
    // with existing data
    T stitchInnerMin = null, stitchInnerMax = null, stitchOuterMin = null, stitchOuterMax = null, stitchMoveMin = null, stitchMoveMax = null;

    // Oh, fuck it, nothing beats the clarity of explicitly enumerating the options
    switch (operation) {
      // Performed with the left handle
      case STRETCH_TO_LEFT -> {
        deleteFrom = cursorPlayIndex(originalEvents, toSelectionMin);
        deleteTo = selectFrom;
        stitchOuterMin = stitchOuter(originalEvents, toSelectionMin, deleteFrom);
      }
      case SHORTEN_FROM_LEFT -> {
        stitchOuterMin = stitchOuter(originalEvents, fromSelectionMin, selectFrom);
      }
      case CLEAR_FROM_LEFT -> {
        stitchOuterMin = stitchOuter(originalEvents, fromSelectionMin, selectFrom);
      }
      case REVERSE_LEFT_TO_RIGHT -> {
        stitchOuterMin = stitchOuter(originalEvents, fromSelectionMin, selectFrom);
        deleteFrom = selectTo;
        deleteTo = cursorInsertIndex(originalEvents, toSelectionMax);
        stitchOuterMax = stitchOuter(originalEvents, toSelectionMax, deleteTo);
      }

      // Performed with the right handle
      case STRETCH_TO_RIGHT -> {
        deleteFrom = selectTo;
        deleteTo = cursorInsertIndex(originalEvents, toSelectionMax);
        stitchOuterMax = stitchOuter(originalEvents, toSelectionMax, deleteTo);
      }
      case SHORTEN_FROM_RIGHT -> {
        stitchOuterMax = stitchOuter(originalEvents, fromSelectionMax, selectTo);
      }
      case CLEAR_FROM_RIGHT -> {
        stitchOuterMax = stitchOuter(originalEvents, fromSelectionMax, selectTo);
      }
      case REVERSE_RIGHT_TO_LEFT -> {
        stitchOuterMax = stitchOuter(originalEvents, fromSelectionMax, selectTo);
        deleteFrom = cursorPlayIndex(originalEvents, toSelectionMin);
        deleteTo = selectFrom;
        stitchOuterMin = stitchOuter(originalEvents, toSelectionMin, deleteFrom);
      }

      // Performed by move-dragging
      case MOVE_LEFT -> {
        deleteFrom = cursorPlayIndex(originalEvents, toSelectionMin);
        stitchOuterMin = stitchOuter(originalEvents, toSelectionMin, deleteFrom);
        stitchOuterMax = stitchOuter(originalEvents, fromSelectionMax, selectTo);
        if (CursorOp().isAfterOrEqual(toSelectionMax, fromSelectionMin)) {
          // No re-ordering needed, move overlaps itself, just deleting material
          // on the left side
          deleteTo = selectFrom;
        } else {
          // Need to re-order, stuff to the left of us may end up to the right of us
          moveTo = deleteFrom;
          deleteTo = cursorInsertIndex(originalEvents, toSelectionMax);
          stitchMoveMax = stitchOuter(originalEvents, toSelectionMax, deleteTo);
          stitchMoveMin = stitchOuter(originalEvents, fromSelectionMin, selectFrom);
        }
      }
      case MOVE_RIGHT -> {
        deleteTo = cursorInsertIndex(originalEvents, toSelectionMax);
        stitchOuterMin = stitchOuter(originalEvents, fromSelectionMin, selectFrom);
        stitchOuterMax = stitchOuter(originalEvents, toSelectionMax, deleteTo);
        if (CursorOp().isBeforeOrEqual(toSelectionMin, fromSelectionMax)) {
          // No re-ordering needed, move overlaps itself, just deleting material
          // on the right side
          deleteFrom = selectTo;
        } else {
          // Need to re-order, stuff to the right of us may end up to the left of us
          moveTo = deleteTo;
          deleteFrom = cursorPlayIndex(originalEvents, toSelectionMin);
          stitchMoveMax = stitchOuter(originalEvents, fromSelectionMax, selectTo);
          stitchMoveMin = stitchOuter(originalEvents, toSelectionMin, deleteFrom);
        }
      }

      default -> throw new IllegalStateException("Unhandled SetCursors.Operation: " + operation);
    }

    // Generate inner stitches
    stitchInnerMin = stitchInnerMin(originalEvents, fromSelectionMin, selectFrom, clear);
    stitchInnerMax = stitchInnerMax(originalEvents, fromSelectionMax, selectTo, clear);

    // Perform deletion for moves/extensions/reverses
    if (deleteTo > deleteFrom) {
      int numDelete = deleteTo - deleteFrom;
      originalEvents.subList(deleteFrom, deleteTo).clear();
      if (deleteFrom < selectFrom) {
        selectFrom -= numDelete;
        selectTo -= numDelete;
      }
      if (deleteFrom < moveTo) {
        moveTo -= numDelete;
      }
    }

    // Clear selection for clear operations
    if (clear && (selectTo > selectFrom)) {
      originalEvents.subList(selectFrom, selectTo).clear();
      selectTo = selectFrom;
    }

    // Insert inner stitches
    if (stitchInnerMin != null) {
      originalEvents.add(selectFrom, stitchInnerMin);
      if (moveTo > selectFrom) {
        ++moveTo;
      }
      ++selectTo;
    }
    if (stitchInnerMax != null) {
      originalEvents.add(selectTo, stitchInnerMax);
      if (moveTo >= selectTo) {
        ++moveTo;
      }
      ++selectTo;
    }

    // Reverse the selection (if any exists, including added stitches)
    if (reverse && (selectTo > selectFrom)) {
      reverseEvents(originalEvents.subList(selectFrom, selectTo));
    }
    // Move the internal stitches to their now positions
    if (stitchInnerMin != null) {
      stitchInnerMin.setCursor(reverse ? toSelectionMax : toSelectionMin);
    }
    if (stitchInnerMax != null) {
      stitchInnerMax.setCursor(reverse ? toSelectionMin : toSelectionMax);
    }
    // Update the cursor positions (unless we cleared them off)
    if (!clear) {
      for (Map.Entry<T, Cursor> entry : toCursors.entrySet()) {
        entry.getKey().setCursor(entry.getValue().bound(this.clip));
      }
    }

    // Perform a move
    int numSelected = selectTo - selectFrom;
    if ((moveTo >= 0) && (numSelected > 0) && (moveTo != selectFrom)) {
      final List<T> selection = originalEvents.subList(selectFrom, selectTo);
      final ArrayList<T> copy = new ArrayList<>(selection);
      selection.clear();
      if (moveTo > selectFrom) {
        moveTo -= numSelected;
      }
      originalEvents.addAll(moveTo, copy);
      selectFrom = moveTo;
      selectTo = selectFrom + numSelected;
    }

    // Add outer stitches if needed
    if (stitchOuterMin != null) {
      int outerMinIndex = stitchInsertIfNeeded(originalEvents, stitchOuterMin, false);
      if ((outerMinIndex >= 0) && (outerMinIndex <= selectFrom)) {
        ++selectFrom;
        ++selectTo;
      }
    }
    if (stitchMoveMax != null) {
      int moveMaxIndex = stitchInsertIfNeeded(originalEvents, stitchMoveMax, true);
      if ((moveMaxIndex >= 0) && (moveMaxIndex <= selectFrom)) {
        ++selectFrom;
        ++selectTo;
      }
    }
    if (stitchMoveMin != null) {
      int moveMinIndex = stitchInsertIfNeeded(originalEvents, stitchMoveMin, false);
      if ((moveMinIndex >= 0) && (moveMinIndex <= selectFrom)) {
        ++selectFrom;
        ++selectTo;
      }
    }
    if (stitchOuterMax != null) {
      int outerMaxIndex = stitchInsertIfNeeded(originalEvents, stitchOuterMax, true);
      if ((outerMaxIndex >= 0) && (outerMaxIndex <= selectFrom)) {
        ++selectFrom;
        ++selectTo;
      }
    }

    // Remove the inner stitches if they turned out to be pointless
    if (stitchRemoveIfRedundant(originalEvents, stitchInnerMin, reverse ? selectTo - 1 : selectFrom)) {
      --selectTo;
    }
    stitchRemoveIfRedundant(originalEvents, stitchInnerMax, reverse ? selectFrom : selectTo - 1);

    // Set the mutable events array
    this.mutableEvents.set(originalEvents);
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

  protected T stitchInner(List<T> events, Cursor cursor, int rightIndex, boolean isMin, boolean force) {
    return null;
  }

  private final T stitchInnerMin(List<T> events, Cursor cursor, int rightIndex, boolean force) {
    return stitchInner(events, cursor, rightIndex, true, force);
  }

  private final T stitchInnerMax(List<T> events, Cursor cursor, int rightIndex, boolean force) {
    return stitchInner(events, cursor, rightIndex, false, force);
  }

  protected T stitchOuter(List<T> events, Cursor cursor, int rightIndex) {
    return null;
  }

  protected int stitchInsertIfNeeded(List<T> events, T stitch, boolean after) {
    return -1;
  }

  protected boolean stitchRemoveIfRedundant(List<T> events, T stitch, int index) {
    return false;
  }

  @Override
  public abstract String getLabel();

  /**
   * Subclasses may override to take action when playback starts from a cursor position
   *
   * @param to Cursor position to start playback from
   */
  void initializeCursorPlayback(Cursor to) {}

  /**
   * Subclasses may override to take action when cursor position jumps mid-playback
   *
   * @param to Cursor position to jump playback to
   */
  void jumpCursor(Cursor from, Cursor to) {}

  /**
   * Subclasses may override this method if they need to take an action when
   * looping is performed and the cursor returns to a prior position.
   *
   * @param from End of loop that cursor rewound from
   * @param to Cursor rewound to loop position
   */
  void loopCursor(Cursor from, Cursor to) {}

  abstract void overdubCursor(Cursor from, Cursor to, boolean inclusive);

  void playCursor(Cursor from, Cursor to, boolean inclusive) {
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
    return removeRange(from, to, true);
  }

  protected boolean removeRange(Cursor from, Cursor to, boolean notify) {
    if (!this.mutableEvents.isEmpty()) {
      int fromIndex = cursorPlayIndex(from);
      int toIndex = cursorInsertIndex(to);
      if (toIndex > fromIndex) {
        this.mutableEvents.removeRange(fromIndex, toIndex);
        if (notify) {
          this.onChange.bang();
        }
        return true;
      }
    }
    return false;
  }

  /**
   * Remove the given event from this clip lane
   *
   * @param event Event to remove
   * @return this
   */
  public LXClipLane<T> removeEvent(T event) {
    this.mutableEvents.remove(event);
    this.onChange.bang();
    return this;
  }

  /**
   * Remove events at the given indices, which must be sorted ascending
   *
   * @param eventIndices List of event indices to remove, sorted ascending
   * @return this
   */
  public LXClipLane<T> removeEvents(List<Integer> eventIndices) {
    if (!eventIndices.isEmpty()) {
      List<T> toRemove = new ArrayList<>();
      for (int index : eventIndices) {
        toRemove.add(this.mutableEvents.get(index));
      }
      // Use removeAll to avoid N array-shifting operations on the
      // underlying ArrayList
      this.mutableEvents.removeAll(toRemove);
      this.onChange.bang();
    }
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
    super.load(lx, obj);

    final List<T> loadEvents = new ArrayList<>();
    if (obj.has(KEY_EVENTS)) {
      beginLoadEvents(loadEvents);
      JsonArray eventsArr = obj.get(KEY_EVENTS).getAsJsonArray();
      for (JsonElement eventElem : eventsArr) {
        JsonObject eventObj = eventElem.getAsJsonObject();
        T event = loadEvent(lx, eventObj);
        if (event != null) {
          event.load(lx, eventObj);
          loadEvents.add(event);
        }
      }
      endLoadEvents(loadEvents);
    }

    // Update underlying threaded array list in one fell swoop
    this.mutableEvents.set(loadEvents);
    this.onChange.bang();
  }

  protected void beginLoadEvents(List<T> loadEvents) {}

  protected void endLoadEvents(List<T> loadEvents) {}

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
