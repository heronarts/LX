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
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXGroup;
import heronarts.lx.pattern.LXPattern;

public class LXChannelClip extends LXAbstractChannelClip implements LXChannel.Listener {

  public final PatternClipLane patternLane = new PatternClipLane(this);

  public final LXChannel channel;

  public LXChannelClip(LX lx, LXChannel channel, int index) {
    super(lx, channel, index, false);
    this.channel = channel;
    this.mutableLanes.add(this.patternLane);

    channel.addListener(this);
    for (LXPattern pattern : channel.patterns) {
      registerComponent(pattern);
    }
  }

  @Override
  public void dispose() {
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
  public void indexChanged(LXAbstractChannel channel) {}

  @Override
  public void groupChanged(LXChannel channel, LXGroup group) {}

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

}
