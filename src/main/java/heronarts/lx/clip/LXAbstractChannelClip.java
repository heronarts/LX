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

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXAbstractChannel;

public abstract class LXAbstractChannelClip extends LXGridClip  {

  public final LXAbstractChannel channel;
  public final MidiNoteClipLane midiNoteLane;

  protected LXAbstractChannelClip(LX lx, LXAbstractChannel channel, int index, boolean registerListener) {
    super(lx, channel, index, registerListener);
    this.channel = channel;
    this.midiNoteLane = new MidiNoteClipLane(this);
    this.mutableLanes.add(this.midiNoteLane);
    registerParameter(channel.enabled);
  }

  @Override
  protected boolean isPermanentClipLane(LXClipLane<?> lane) {
    return
      (lane == this.midiNoteLane) ||
      super.isPermanentClipLane(lane);
  }

  @Override
  protected void onStopPlayback() {
    super.onStopPlayback();
    this.midiNoteLane.onStopPlayback();
  }

  @Override
  protected void onStopRecording() {
    super.onStopRecording();
    this.midiNoteLane.onStopRecording();
  }

  @Override
  public LXClipLane<?> loadClipLane(LX lx, JsonObject laneObj, int index) {
    switch (getClipLaneType(laneObj)) {
      case LXClipLane.VALUE_LANE_TYPE_MIDI_NOTE -> {
        this.midiNoteLane.load(lx, laneObj);
        return this.midiNoteLane;
      }
      default -> {
        return super.loadClipLane(lx, laneObj, index);
      }
    }
  }

  @Override
  public void dispose() {
    unregisterParameter(this.channel.enabled);
    super.dispose();
  }

}
