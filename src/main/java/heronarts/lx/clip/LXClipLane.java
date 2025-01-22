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
import heronarts.lx.LXSerializable;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.utils.LXUtils;

public abstract class LXClipLane implements LXSerializable {

  public final MutableParameter onChange = new MutableParameter();

  public final LXClip clip;

  // NOTE(mcslee): think about whether CopyOnWrite is the best solution here for UI drawing
  // or whether synchronized or locking around multi-edits is preferable, as those are currently
  // going to be super costly
  protected final List<LXClipEvent> mutableEvents = new CopyOnWriteArrayList<LXClipEvent>();
  public final List<LXClipEvent> events = Collections.unmodifiableList(this.mutableEvents);

  protected LXClipLane(LXClip clip) {
    this.clip = clip;
  }

  public int getIndex() {
    return this.clip.lanes.indexOf(this);
  }

  protected LXClipLane appendEvent(LXClipEvent event) {
    this.mutableEvents.add(event);
    this.onChange.bang();
    return this;
  }

  private void _insertEvent(LXClipEvent event) {
    // TODO(mcslee): make this more efficient using a binary search...
    int index = 0;
    while (index < this.events.size()) {
      if (event.cursor < this.events.get(index).cursor) {
        break;
      }
      ++index;
    }
    this.mutableEvents.add(index, event);
  }

  protected LXClipLane insertEvent(LXClipEvent event) {
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

  protected LXClipEvent getPreviousEvent() {
    // TODO(mcslee): make this more efficient using a binary search...
    LXClipEvent previous = null;
    for (LXClipEvent event : this.events) {
      if (this.clip.cursor < event.cursor) {
        break;
      }
      previous = event;
    }
    return previous;
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

  public abstract String getLabel();

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

  public void save(LX lx, JsonObject obj) {
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
