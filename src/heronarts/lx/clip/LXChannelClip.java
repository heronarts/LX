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
 * ##library.name##
 * ##library.sentence##
 * ##library.url##
 *
 * @author      ##author##
 * @modified    ##date##
 * @version     ##library.prettyVersion## (##library.version##)
 */

package heronarts.lx.clip;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXChannel;
import heronarts.lx.LXPattern;
import heronarts.lx.midi.LXShortMessage;
import heronarts.lx.midi.MidiNote;

public class LXChannelClip extends LXClip implements LXChannel.Listener, LXChannel.MidiListener {

  public final PatternClipLane patternLane = new PatternClipLane(this);
  public final MidiNoteClipLane midiNoteLane = new MidiNoteClipLane(this);

  public final LXChannel channel;

  public LXChannelClip(LX lx, LXChannel channel, int index) {
    super(lx, channel, index, false);
    this.channel = channel;
    this.mutableLanes.add(this.patternLane);
    this.mutableLanes.add(this.midiNoteLane);

    channel.addListener(this);
    channel.addMidiListener(this);
    channel.fader.addListener(this.parameterRecorder);
    channel.enabled.addListener(this.parameterRecorder);

    for (LXPattern pattern : channel.patterns) {
      registerComponent(pattern);
    }
  }

  @Override
  public void dispose() {
    this.channel.removeListener(this);
    this.channel.removeMidiListener(this);
    this.channel.fader.removeListener(this.parameterRecorder);
    this.channel.enabled.removeListener(this.parameterRecorder);
    this.channel.removeListener(this);
    for (LXPattern pattern : this.channel.patterns) {
      unregisterComponent(pattern);
    }
    super.dispose();
  }

  @Override
  protected void onStartRecording() {
    this.patternLane.appendEvent(new PatternClipEvent(this.patternLane, this.channel, this.channel.getActivePattern()));
  }

  @Override
  public void indexChanged(LXChannel channel) {}

  @Override
  public void patternAdded(LXChannel channel, LXPattern pattern) {
    registerComponent(pattern);
  }

  @Override
  public void patternRemoved(LXChannel channel, LXPattern pattern) {
    unregisterComponent(pattern);
  }

  @Override
  public void patternMoved(LXChannel channel, LXPattern pattern) {
  }

  @Override
  public void patternWillChange(LXChannel channel, LXPattern pattern, LXPattern nextPattern) {
    if (isRunning() && this.bus.arm.isOn()) {
      this.patternLane.appendEvent(new PatternClipEvent(this.patternLane, channel, nextPattern));
    }
  }

  @Override
  public void patternDidChange(LXChannel channel, LXPattern pattern) {

  }

  @Override
  protected void loadLane(LX lx, String laneType, JsonObject laneObj) {
    if (laneType.equals(LXClipLane.VALUE_LANE_TYPE_PATTERN)) {
      this.patternLane.load(lx, laneObj);
    } else if (laneType.equals(LXClipLane.VALUE_LANE_TYPE_MIDI_NOTE)) {
      this.midiNoteLane.load(lx, laneObj);
    } else {
      super.loadLane(lx, laneType, laneObj);
    }
  }

  @Override
  public void midiReceived(LXChannel channel, LXShortMessage message) {
    if (message instanceof MidiNote) {
      this.midiNoteLane.appendEvent(new MidiNoteClipEvent(this.midiNoteLane, (MidiNote) message));
    }
  }
}
