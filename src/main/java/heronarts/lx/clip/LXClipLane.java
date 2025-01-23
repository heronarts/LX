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

import java.util.Collections;
import java.util.List;
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

public abstract class LXClipLane extends LXComponent {

  public final MutableParameter uiHeight = new MutableParameter("UI Height");

  public final MutableParameter onChange = new MutableParameter();

  public final LXClip clip;

  // NOTE(mcslee): think about whether CopyOnWrite is the best solution here for UI drawing
  // or whether synchronized or locking around multi-edits is preferable, as those are currently
  // going to be super costly
  protected final List<LXClipEvent> mutableEvents = new CopyOnWriteArrayList<LXClipEvent>();
  public final List<LXClipEvent> events = Collections.unmodifiableList(this.mutableEvents);

  protected LXClipLane(LXClip clip) {
    setParent(clip);
    this.clip = clip;
    addInternalParameter("uiHeight", this.uiHeight);
  }

  public int getIndex() {
    return this.clip.lanes.indexOf(this);
  }

  private double lastEventCursor() {
    if (!this.events.isEmpty()) {
      return this.events.get(this.events.size() - 1).cursor;
    }
    return 0;
  }

  protected LXClipLane recordEvent(LXClipEvent event) {
    _insertEvent(event);
    this.onChange.bang();
    return this;
  }

  private int _insertIndex(double cursor) {
    int left = 0;
    int right = this.events.size() - 1;

    // Starting assumption is everything is <= cursor until
    // we find something > cursor
    int result = right + 1;

    while (left <= right) {
      int mid = left + (right - left) / 2;
      if (this.events.get(mid).cursor > cursor) {
        // If the current element is greater, it could be a potential result,
        // but something to the left could still be lower
        result = mid;
        right = mid - 1;
      } else {
        // Nope, look on the right side
        left = mid + 1;
      }
    }
    return result;
  }

  private void _insertEvent(LXClipEvent event) {
    if (event.cursor >= lastEventCursor()) {
      // Quick check... shortcut in normal recording mode when we're not
      // overdubbing and the cursor is past all the prior events anyways
      this.mutableEvents.add(event);
    } else {
      this.mutableEvents.add(_insertIndex(event.cursor), event);
    }
  }

  public LXClipLane insertEvent(LXClipEvent event) {
    _insertEvent(event);
    this.onChange.bang();
    return this;
  }

  public LXClipLane moveEvent(LXClipEvent event, double basis) {
    double clipLength = this.clip.getLength();
    double min = 0;
    double max = clipLength;
    int index = this.events.indexOf(event);
    if (index > 0) {
      min = this.events.get(index-1).cursor;
    }
    if (index < this.events.size() - 1) {
      max = this.events.get(index+1).cursor;
    }
    double newCursor = LXUtils.constrain(basis * clipLength, min, max);
    if (event.cursor != newCursor) {
      event.cursor = newCursor;
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
  protected LXClipEvent getPreviousEvent(double cursor) {
    int previousIndex = _insertIndex(cursor) - 1;
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
  protected LXClipEvent getPreviousEvent() {
    return getPreviousEvent(this.clip.cursor);
  }

  public void setEventsCursors(Map<? extends LXClipEvent, Double> cursors) {
    boolean changed = false;
    final double clipLength = this.clip.length.getValue();

    // TODO(mcslee): we could probably make this a lot more efficient with stricter
    // assumptions about the values coming in, whether re-ordering may have occurred
    // or not... but in the meantime we do an insertion-sort per-element to avoid
    // mucking up the state
    //
    // Almost surely want to improve this because it's currently an underlying
    // CopyOnWriteArrayList which will make *many* copies if we edit loads of
    // items at once here. Make our own copy and clear()/addAll() or we should use
    // the stable .sort() method, or possibly Arrays.sort() methods that can sort
    // a sub-range of the array if we know there's no overlapping!
    for (Map.Entry<? extends LXClipEvent, Double> entry : cursors.entrySet()) {
      final LXClipEvent event = entry.getKey();
      if (this.events.contains(event)) {
        this.mutableEvents.remove(event);
        event.setCursor(LXUtils.constrain(entry.getValue(), 0, clipLength));
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
  void loopCursor(double to) {}

  void advanceCursor(double from, double to) {
    for (LXClipEvent event : this.mutableEvents) {
      if (from <= event.cursor && event.cursor < to) {
        event.execute();
      }
    }
  }

  public LXClipLane clearSelection(double fromBasis, double toBasis) {
    double from = fromBasis * this.clip.length.getValue();
    double to = toBasis * this.clip.length.getValue();
    int i = 0;
    boolean removed = false;
    while (i < this.mutableEvents.size()) {
      LXClipEvent event = this.mutableEvents.get(i);
      if (from <= event.cursor) {
        if (event.cursor > to) {
          break;
        }
        removed = true;
        this.mutableEvents.remove(i);
      } else {
        ++i;
      }
    }
    if (removed) {
      this.onChange.bang();
    }
    return this;
  }

  public LXClipLane removeEvent(LXClipEvent event) {
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
      JsonArray eventsArr = obj.get(KEY_EVENTS).getAsJsonArray();
      for (JsonElement eventElem : eventsArr) {
        JsonObject eventObj = eventElem.getAsJsonObject();
        LXClipEvent event = loadEvent(lx, eventObj);
        if (event != null) {
          event.load(lx, eventObj);
          this.mutableEvents.add(event);
        }
      }
    }
    this.onChange.bang();
  }

  protected abstract LXClipEvent loadEvent(LX lx, JsonObject eventObj);

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
