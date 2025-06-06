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
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.pattern.LXPattern;

public class LXChannelClip extends LXAbstractChannelClip implements LXChannel.Listener {

  public final LXChannel channel;
  public final PatternClipLane patternLane;

  public LXChannelClip(LX lx, LXChannel channel, int index) {
    super(lx, channel, index, false);
    this.channel = channel;
    this.mutableLanes.add(this.patternLane = new PatternClipLane(this));

    // Note that we passed false to the parent class's register listener, because
    // we're going to do it here ourselves, and a channel listener supersedes
    // a bus listener
    channel.addListener(this);
    for (LXPattern pattern : channel.patterns) {
      registerPattern(pattern);
    }
  }

  private final LXPattern.Listener patternEffectListener = new LXPattern.Listener() {
    public void effectAdded(LXPattern pattern, LXEffect effect) {
      registerComponent(effect);
    }
    public void effectRemoved(LXPattern pattern, LXEffect effect) {
      unregisterComponent(effect);
    }
    public void effectMoved(LXPattern pattern, LXEffect effect) {}
  };

  protected void registerPattern(LXPattern pattern) {
    registerComponent(pattern);
    for (LXEffect effect : pattern.effects) {
      registerComponent(effect);
    }
    pattern.addListener(this.patternEffectListener);
  }

  protected void unregisterPattern(LXPattern pattern) {
    unregisterComponent(pattern);
    for (LXEffect effect : pattern.effects) {
      unregisterComponent(effect);
    }
    pattern.removeListener(this.patternEffectListener);
  }

  @Override
  public void dispose() {
    this.channel.removeListener(this);
    for (LXPattern pattern : this.channel.patterns) {
      unregisterPattern(pattern);
    }
    super.dispose();
  }

  @Override
  protected void onStartRecording(boolean isOverdub) {
    if (this.channel.compositeMode.getEnum() == LXChannel.CompositeMode.PLAYLIST) {
      LXPattern targetPattern = this.channel.getTargetPattern();
      if (targetPattern != null) {
        // If we're overdubbing - only record a pattern event at the start of recording if the present
        // state is *different* from what was already in the pattern clip lane
        PatternClipEvent previousPattern = this.patternLane.getPreviousEvent(this.cursor);
        if (!isOverdub || ((previousPattern != null) && (previousPattern.getPattern() != targetPattern))) {
          this.patternLane.recordEvent(new PatternClipEvent(this.patternLane, targetPattern));
        }
      }
    }
  }

  @Override
  public void patternAdded(LXChannel channel, LXPattern pattern) {
    registerPattern(pattern);
  }

  @Override
  public void patternRemoved(LXChannel channel, LXPattern pattern) {
    unregisterPattern(pattern);
  }

  @Override
  public void patternWillChange(LXChannel channel, LXPattern pattern, LXPattern nextPattern) {
    if (isRecording()) {
      this.patternLane.recordPatternEvent(nextPattern);
    }
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
