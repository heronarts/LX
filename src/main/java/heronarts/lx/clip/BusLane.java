/**
 * Copyright 2025- Justin K. Belcher, Heron Arts LLC
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
 * @author Justin K. Belcher <justin@jkb.studio>
 */

package heronarts.lx.clip;

import com.google.gson.JsonObject;
import heronarts.lx.LX;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXParameterListener;

/**
 * A composition lane representing a mixer channel
 */
public class BusLane extends LXClipLane<BusLaneEvent> {

  public final Composition composition;
  public final LXBus bus;

  BusLane(Composition composition, LXBus bus) {
    super(composition);
    this.composition = composition;
    this.bus = bus;

    this.bus.hasRunningClip.addListener(this.hasRunningClipListener);
  }

  @Override
  public String getLabel() {
    return this.bus.getLabel();
  }

  @Override
  public String getPath() {
    return "lane/" + getIndex();
  }

  // Recording

  private final LXParameterListener hasRunningClipListener = (p) -> {
    if (isRecording()) {
      final boolean busHasRunningClip = ((BooleanParameter) p).isOn();
      if (busHasRunningClip) {
        recordRunningClipStart();
      } else {
        recordRunningClipStop();
      }
    }
  };

  private boolean isRecording() {
    return clip.isRecording();
  }

  private BusLaneEvent recordingEvent = null;

  private void recordRunningClipStart() {
    final LXClip original = this.bus.getRunningClip();
    this.recordingEvent = new BusLaneEvent(this.lx, this, original);
    this.recordingEvent.startRecording();
    recordEvent(this.recordingEvent);
    this.onChange.bang(); // needed for ui redraw?
  }

  private void recordRunningClipStop() {
    if (this.recordingEvent != null) {
      this.recordingEvent.stop();
      this.recordingEvent = null;
      this.onChange.bang(); // needed for ui redraw?
    }
  }

  @Override
  void overdubCursor(Cursor from, Cursor to, boolean inclusive) {
    boolean changed = false;

    // TODO: review overdub behavior

    // Commit any newly recorded events from the record queue
    if (!this.recordQueue.isEmpty()) {
      commitRecordQueue(false);
      changed = true;
    }

    // Play existing events during overdub, but only when not actively recording
    if (this.recordingEvent == null) {
      playCursor(from, to, inclusive);
    }

    if (changed) {
      this.onChange.bang();
    }
  }

  protected void run(double deltaMs) {
    if (this.recordingEvent != null) {
      this.recordingEvent.runInternalClip(deltaMs);
    } else if (this.playbackEvent != null) {
      this.playbackEvent.runInternalClip(deltaMs);
    }
  }

  // Playback (Is delegated to the event, which could be longer than the clip if clip is looping)

  BusLaneEvent playbackEvent = null;

  /**
   * Playback started from cursor position
   */
  @Override
  void initializeCursorPlayback(Cursor from) {
    this.playbackEvent = null;
    BusLaneEvent event = getEventContainingCursor(from);
    if (event != null) {
      this.playbackEvent = event;
      Cursor eventFrom = from.subtract(event.cursor);
      this.playbackEvent.initializeCursorPlayback(eventFrom);
    }
  }

  /**
   * Cursor position jumped mid-playback
   */
  @Override
  void jumpCursor(Cursor from, Cursor to) {
    _jumpCursor(from, to, false);
  }

  @Override
  void loopCursor(Cursor from, Cursor to) {
    _jumpCursor(from, to, true);
  }

  private void _jumpCursor(Cursor from, Cursor to, boolean isLoop) {
    BusLaneEvent toEvent = getEventContainingCursor(to);
    if (this.playbackEvent != toEvent) {
      // Jump crosses an event boundary
      if (this.playbackEvent != null) {
        this.playbackEvent.stop();
      }
      this.playbackEvent = toEvent;
      if (this.playbackEvent != null) {
        this.playbackEvent.initializeCursorPlayback(to.subtract(this.playbackEvent.cursor));
      }
    } else {
      // Jump was within the same event
      if (this.playbackEvent != null) {
        if (isLoop) {
          this.playbackEvent.loopCursor(from.subtract(this.playbackEvent.cursor), to.subtract(this.playbackEvent.cursor));
        } else {
          this.playbackEvent.jumpCursor(from.subtract(this.playbackEvent.cursor), to.subtract(this.playbackEvent.cursor));
        }
      }
    }
  }

  private BusLaneEvent getEventContainingCursor(Cursor cursor) {
    BusLaneEvent event = getPreviousEvent(cursor);
    if (event != null && CursorOp().isBeforeOrEqual(cursor, event.end)) {
      return event;
    }
    return null;
  }

  /**
   * Normal cursor advancement during playback
   */
  @Override
  void playCursor(Cursor from, Cursor to, boolean inclusive) {
    if (this.playbackEvent != null) {
      final Cursor eventEnd = playbackEvent.end;
      // Is "to" past the end of the current event? Includes looping.
      if (CursorOp().isAfter(to, eventEnd)) {  // TODO: consider inclusive
        // Play to the event end
        this.playbackEvent.playCursor(from.subtract(this.playbackEvent.cursor), eventEnd, true);
        this.playbackEvent.stop();
        this.playbackEvent = null;
        // Find the next event, start & play it, until no more events are found.
        playEventsTo(eventEnd, to, inclusive);
      } else {
        // Normal. Play to "to"
        this.playbackEvent.playCursor(from.subtract(this.playbackEvent.cursor), to.subtract(this.playbackEvent.cursor), inclusive);
      }
    } else {
      // There was no current playback event
      // Find the next event, start & play it, until no more events are found.
      playEventsTo(from, to, inclusive);
    }

    // explicitly not calling super
  }

  private void playEventsTo(Cursor from, Cursor to, boolean inclusive) {
    // Find the next event at or after "from"
    final Cursor.Operator c = CursorOp();
    for (int index = cursorPlayIndex(from); index < this.events.size(); ++index) {
      BusLaneEvent event = this.events.get(index);

      // Is event beyond our range?
      if (c.isAfterOrEqual(event.cursor, to)) {
        break;
      }

      // Start this event
      event.initializeCursorPlayback(Cursor.ZERO);

      if (c.isAfter(to, event.end)) {
        // Entire event is contained within this timeframe
        if (!CursorOp().isZero(event.length)) {
          event.playCursor(Cursor.ZERO, event.length, true);
        }
        event.stop();
      } else {
        // This event extends past "to". Play the overlap, if any.
        if (!CursorOp().isEqual(to, event.end)) {
          // Play first blip of event
          event.playCursor(Cursor.ZERO, event.end.subtract(to), false);
        }
        this.playbackEvent = event;
        return;
      }
    }
  }

  // Deleting events

  @Override
  public boolean removeRange(Cursor from, Cursor to) {
    // TODO: remove range of BusLaneEvents

    // If removed
    //   this.onChange.bang();
    //   return true;
    return false;
  }

  @Override
  protected void onRemoveEvent(BusLaneEvent event) {
    event.dispose();
  }

  @Override
  public void dispose() {
    this.bus.hasRunningClip.removeListener(this.hasRunningClipListener);
    super.dispose();
    // Dev check:
    if (!this.events.isEmpty()) {
      LX.error("Unexpected state: BusLane disposed without clearing events");
    }
  }

  // Serialization

  @Override
  public BusLane removeEvent(BusLaneEvent event) {
    // Unfocus the clip so clip editor will release listeners before clip is disposed
    if (event.isFocusedClip()) {
      this.lx.engine.composition.setFocusedClip(null);
    }
    super.removeEvent(event);
    return this;
  }

  public static final String KEY_BUS_ID = "busId";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_BUS_ID, this.bus.getId());
  }

  @Override
  protected BusLaneEvent loadEvent(LX lx, JsonObject eventObj) {
    BusLaneEvent event = new BusLaneEvent(this.lx, this);
    event.load(lx, eventObj);
    return event;
  }
}
