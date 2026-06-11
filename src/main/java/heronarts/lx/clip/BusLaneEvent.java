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

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import com.google.gson.JsonObject;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXGroup;
import heronarts.lx.parameter.LXParameterListener;

/**
 * A composition bus lane event is a light container around... a clip! What fun!
 */
public class BusLaneEvent extends LXCompositionEvent<BusLaneEvent> {

  private static final int INVALID_CLIP_ID = -1;

  private final LX lx;
  public final BusLane lane;
  private final LXClip internalClip;
  private final LXParameterListener internalClipListener;
  private int originalClipId = INVALID_CLIP_ID;

  BusLaneEvent(LX lx, BusLane lane) {
    this(lx, lane, null);
  }

  BusLaneEvent(LX lx, BusLane lane, LXClip original) {
    super(lane);
    this.lx = lx;
    this.lane = lane;
    LXBus bus = lane.bus;

    // The clip's index is zero because it is the only clip within the event. The relevant index is the event's index.
    if (bus instanceof LXChannel channel) {
      this.internalClip = new LXChannelClip(lx, channel, lane, lx.engine.composition, 0);
    } else if (bus instanceof LXGroup group) {
      this.internalClip = new LXGroupClip(lx, group, lane, lx.engine.composition, 0);
    } else if (bus == lx.engine.mixer.masterBus) {
      this.internalClip = new LXMasterClip(lx, lane, lx.engine.composition, 0);
    } else {
      throw new UnsupportedOperationException("BusLaneEvent cannot be created. Bus type not recognized: " + bus.getClass().getSimpleName());
    }

    if (original != null) {
      this.originalClipId = original.getId();
      this.internalClip.label.setValue(original.getLabel());
      this.internalClip.automationEnabled.setValue(original.automationEnabled.isOn());

      // Make exact copy of snapshot data
      if (original.snapshotEnabled.isOn()) {
        JsonObject snapshotObj = new JsonObject();
        original.snapshot.save(lx, snapshotObj);
        snapshotObj.remove(LXComponent.KEY_ID);
        this.internalClip.snapshot.load(lx, snapshotObj);
      }
      // Now enabling snapshot won't save a fresh (wrong) snapshot
      this.internalClip.snapshotEnabled.setValue(original.snapshotEnabled.isOn());
    }

    this.internalClipListener = (p) -> {
      if (p == this.internalClip.length) {
        internalLengthChanged();
      } else if (p == this.internalClip.playStart) {
        internalPlayStartChanged();
      } else if (p == this.internalClip.playEnd) {
        internalPlayEndChanged();
      } else if (p == this.internalClip.loop) {
        internalLoopChanged();
      }
    };
    this.internalClip.length.addListener(this.internalClipListener);
    this.internalClip.playStart.addListener(this.internalClipListener);
    this.internalClip.playEnd.addListener(this.internalClipListener);
    this.internalClip.loop.addListener(this.internalClipListener);
  }

  // Accessors

  public String getLabel() {
    return this.internalClip.getLabel();
  }

  /**
   * Whether this event only recalls a snapshot, with no automation content.
   */
  public boolean isSnapshotOnly() {
    return this.internalClip.snapshotEnabled.isOn()
      && !this.internalClip.automationEnabled.isOn();
  }

  // Length

  private void internalLengthChanged() {
    if (this.lane.composition.isRecording()) {
      // TODO: more factors to include here
      setLength(this.internalClip.length.cursor);
    }
  }

  private void internalPlayStartChanged() {
    if (!this.internalClip.loop.isOn()) {
      setLength(this.internalClip.playEnd.cursor.subtract(this.internalClip.playStart.cursor));
    }
  }

  private void internalPlayEndChanged() {
    if (!this.internalClip.loop.isOn()) {
      setLength(this.internalClip.playEnd.cursor.subtract(this.internalClip.playStart.cursor));
    }
  }

  private void internalLoopChanged() {
    if (!this.internalClip.loop.isOn()) {
      setLength(this.internalClip.playEnd.cursor.subtract(this.internalClip.playStart.cursor));
    }
  }

  /**
   * Move event start without moving the end
   * @param start new value for the start position, in composition time
   */
  @Override
  public void setEventStart(Cursor start) {
    final Cursor.Operator c = CursorOp();

    // Constrain to valid range
    c.constrain(start, Cursor.ZERO, end.subtract(Cursor.MIN_LOOP));

    final boolean increase = c.isAfter(start, this.cursor);
    final Cursor delta = increase ? start.subtract(this.cursor) : this.cursor.subtract(start);

    final Cursor loopLength = this.internalClip.loopLength.cursor;
    final boolean isLoop = this.internalClip.loop.isOn() && !c.isEqual(loopLength, Cursor.ZERO);
    Cursor newPlayStart = this.internalClip.playStart.cursor.clone();
    if (isLoop ) {
      if (increase) {
        // Increasing
        final boolean runsIntoLoop = c.isBefore(newPlayStart, this.internalClip.loopEnd.cursor);
        newPlayStart = newPlayStart.add(delta);
        if (runsIntoLoop) {
          // Wrap if playStart was before loopEnd
          while (c.isAfterOrEqual(newPlayStart, this.internalClip.loopEnd.cursor)) {
            newPlayStart = newPlayStart.subtract(loopLength);
          }
        }
      } else {
        // Decreasing
        final boolean decreasesIntoLoop = c.isAfterOrEqual(newPlayStart, this.internalClip.loopStart.cursor);
        newPlayStart = newPlayStart.subtract(delta);  // TODO: allow negative cursor here
        if (decreasesIntoLoop) {
          // Wrap if playStart was after loopStart
          while (c.isBefore(newPlayStart, this.internalClip.loopStart.cursor)) {
            newPlayStart = newPlayStart.add(loopLength);
          }
        }
      }
    } else {
      // Not a loop
      newPlayStart = c.applyDelta(newPlayStart, delta, increase); // TODO: allow negative cursor here
    }

    setCursor(start);
    this.internalClip.setPlayStart(newPlayStart);
    if (isLoop) {
      setLength(c.applyDelta(this.length, delta, !increase));
    }
  }

  /**
   * Move event end without moving the start
   *
   * @param endCursor new value for the end position, in composition time
   */
  @Override
  public void setEventEnd(Cursor endCursor) {
    final Cursor.Operator c = CursorOp();

    // Constrain to valid range
    endCursor = c.max(endCursor, this.cursor.add(Cursor.MIN_LOOP));

    if (internalClip.loop.isOn()) {
      // Loop is enabled. ONLY modify event length.
      setLength(endCursor.subtract(this.cursor));
    } else {
      // Loop is disabled. Pass through to internal clip playEnd.
      if (c.isAfter(endCursor, this.end)) {
        Cursor delta = endCursor.subtract(this.end);
        internalClip.setPlayEnd(c.applyDelta(this.internalClip.playEnd.cursor, delta, true));
      } else {
        Cursor delta = this.end.subtract(endCursor);
        internalClip.setPlayEnd(c.applyDelta(this.internalClip.playEnd.cursor, delta, false));
      }
    }
  }

  // Recording

  void startRecording() {
    this.internalClip.start();
  }

  /**
   * Refresh the contents of this clip from the original.
   */
  public void refreshFromOriginalClip() {
    if (this.originalClipId != INVALID_CLIP_ID) {
      // TODO: implement right-click "refresh from original".
      // Composition clips are a copy of the original, but the user could pull updates
      // from the original. Or push updates from the original to all recorded copies.
    }
  }

  // Playback
  //   The bus lane converts from absolute composition time to event time (zero at start of event).
  //   The event (this class) converts from event time to clip time.

  /**
   * Initialize playback at a position within this event.
   * Called when composition playback starts and the cursor is within this event.
   *
   * @param eventFrom Cursor in event time (0 = event start)
   */
  void initializeCursorPlayback(Cursor eventFrom) {
    if (this.internalClip.snapshotEnabled.isOn()) {
      if (this.internalClip.loop.isOn()) {
        // Looped: recall snapshot when crossing the event start
        if (CursorOp().isZero(eventFrom)) {
          this.internalClip.snapshot.recall();
        }
      } else {
        // Non-looped: recall snapshot when at clip time 0
        if (CursorOp().isZero(toInternalClipFromEvent(eventFrom))) {
          this.internalClip.snapshot.recall();
        }
      }
    }
    startInternalClip(toInternalClipFromEvent(eventFrom));
  }

  void runInternalClip(double deltaMs) {
    this.internalClip.run(deltaMs);
  }

  /**
   * Normal cursor advancement within this event. Monitors for a threshold to fire
   * the snapshot. Actual playback is driven by runInternalClip(deltaMs).
   */
  void playCursor(Cursor eventFrom, Cursor eventTo, boolean inclusive) {
    // Non-looped: Recall snapshot when cursor crosses clip time 0
    if (this.internalClip.snapshotEnabled.isOn() && !this.internalClip.loop.isOn()) {
      final Cursor.Operator c = CursorOp();
      Cursor clipFrom = toInternalClipFromEvent(eventFrom);
      Cursor clipTo = toInternalClipFromEvent(eventTo);
      if (c.isBefore(clipFrom, Cursor.ZERO) && !c.isBefore(clipTo, Cursor.ZERO)) {
        this.internalClip.snapshot.recall();
      }
    }
  }

  /**
   * Handle a cursor jump mid-playback within this event.
   *
   * @param eventFrom Previous cursor in event time
   * @param eventTo New cursor in event time
   */
  void jumpCursor(Cursor eventFrom, Cursor eventTo) {
    startInternalClip(toInternalClipFromEvent(eventTo));
  }

  /**
   * Handle the composition looping back to an earlier position within this event.
   *
   * @param from End-of-loop cursor in event time
   * @param to Loop-start cursor in event time
   */
  void loopCursor(Cursor from, Cursor to) {
    startInternalClip(toInternalClipFromEvent(to));
  }

  /**
   * Start the internal clip at a cursor position
   *
   * @param cursor Position within the internal clip to start from
   */
  private void startInternalClip(Cursor cursor) {
    this.internalClip.launchFromCursor.set(cursor);
    this.internalClip.trigger();
  }

  /**
   * Convert event time to the corresponding position within
   * the internal clip, accounting for playStart and loop wrapping.
   *
   * @param eventTime Cursor in event time (0 = event start)
   * @return Corresponding cursor position within the internal clip
   */
  private Cursor toInternalClipFromEvent(Cursor eventTime) {
    final Cursor.Operator c = CursorOp();
    final Cursor playStart = this.internalClip.playStart.cursor;

    // Not loop
    if (!this.internalClip.loop.isOn()) {
      return playStart.add(eventTime);
    }

    // Loop
    final Cursor loopEnd = this.internalClip.loopEnd.cursor;
    final Cursor firstSegment = loopEnd.subtract(playStart);
    // eventTime is before the first loop wrap?
    if (c.isBefore(eventTime, firstSegment)) {
      return playStart.add(eventTime);
    }

    // Remaining time in event after the loop end
    final Cursor remaining = eventTime.subtract(firstSegment);
    final Cursor loopLength = this.internalClip.loopLength.cursor;
    if (c.isZero(loopLength)) {
      // Loop enabled with zero length, not ideal
      return this.internalClip.loopStart.cursor.clone();
    }

    final double loopsRemaining = c.getRatio(remaining, loopLength);
    // Percent of current loop elapsed
    final double loopPercentElapsed = loopsRemaining - Math.floor(loopsRemaining);
    return this.internalClip.loopStart.cursor.add(loopLength.scale(loopPercentElapsed));
  }

  void stop() {
    this.internalClip.stop();
  }

  @Override
  public void execute() {
    // Called by parent lane when event cursor is crossed during playback.
    // Ignore this, we need to know the exact time elapsed so we can relay to internal clip.
  }

  // Composition window clip editor

  public void focusClip() {
    this.lx.engine.composition.setFocusedClip(this.internalClip);
  }

  public boolean isFocusedClip() {
    return this.internalClip != null && this.internalClip == this.lx.engine.composition.focusedClip.getClip();
  }

  // Disposal

  public void dispose() {
    if (this.internalClip != null) {
      this.internalClip.length.removeListener(this.internalClipListener);
      this.internalClip.playStart.removeListener(this.internalClipListener);
      this.internalClip.playEnd.removeListener(this.internalClipListener);
      this.internalClip.loop.removeListener(this.internalClipListener);
      this.internalClip.dispose();
    }
  }

  // Serialization

  private static final String KEY_ORIGINAL_CLIP = "originalClipId";
  private static final String KEY_INTERNAL_CLIP = "internalClip";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_ORIGINAL_CLIP, this.originalClipId);
    if (this.internalClip != null) {
      JsonObject clipObj = new JsonObject();
      this.internalClip.save(lx, clipObj);
      obj.add(KEY_INTERNAL_CLIP, clipObj);
    }
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(KEY_ORIGINAL_CLIP)) {
      try {
        this.originalClipId = obj.get(KEY_ORIGINAL_CLIP).getAsInt();
      } catch (NumberFormatException ex) {
        LX.error("Corrupt componentId for original clip in " + getClass().getSimpleName() + ". Reference will be lost.");
        this.originalClipId = INVALID_CLIP_ID;
      }
    } else {
      this.originalClipId = INVALID_CLIP_ID;
    }
    if (obj.has(KEY_INTERNAL_CLIP) && this.internalClip != null) {
      this.internalClip.load(lx, obj.get(KEY_INTERNAL_CLIP).getAsJsonObject());
    }
  }
}
