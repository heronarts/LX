/**
 * Copyright 2018- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.mixer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.LXModulatorComponent;
import heronarts.lx.blend.LXBlend;
import heronarts.lx.clip.LXClip;
import heronarts.lx.clip.LXGroupClip;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.parameter.LXParameter;

public class LXGroup extends LXAbstractChannel {

  public class Profiler extends LXAbstractChannel.Profiler {
    public long compositeNanos;
  }

  @Override
  protected LXModulatorComponent.Profiler constructProfiler() {
    return new Profiler();
  }

  private final List<LXChannel> mutableChannels = new ArrayList<LXChannel>();
  public final List<LXChannel> channels = Collections.unmodifiableList(this.mutableChannels);

  public LXGroup(LX lx, int index) {
    super(lx, index, "Group-" + (index+1));
  }

  @Override
  protected LXClip constructClip(int index) {
    return new LXGroupClip(this.lx, this, index);
  }

  public LXGroup addChannel(LXChannel channel) {
    if (this.channels.contains(channel)) {
      throw new IllegalStateException("Cannot add channel to group twice: " + channel + " " + this);
    }
    this.mutableChannels.add(channel);
    channel.setGroup(this);
    return this;
  }

  LXGroup removeChannel(LXChannel channel) {
    if (!this.channels.contains(channel)) {
      throw new IllegalStateException("Cannot remove channel not in group: " + channel + " " + this);
    }
    this.mutableChannels.remove(channel);
    channel.setGroup(null);
    return this;
  }

  /**
   * Get the last channel in this group
   *
   * @return The last channel in this group
   */
  public LXChannel getLastChannel() {
    return this.channels.get(this.channels.size() - 1);
  }

  public void ungroup() {
    // Remove all our channels
    for (int i = this.channels.size() - 1; i >= 0; --i) {
      removeChannel(this.channels.get(i));
    }
    // Remove ourselves
    this.lx.engine.mixer.removeChannel(this);
  }

  @Override
  public void onParameterChanged(LXParameter parameter) {
    super.onParameterChanged(parameter);
    if (parameter == this.selected) {
      if (this.selected.isOn()) {
        for (LXChannel channel : this.channels) {
          channel.selected.setValue(false);
        }
      }
    }
  }

  void afterLoop(double deltaMs) {
    // Composite all the channels in this group
    long compositeStart = System.nanoTime();
    int[] blendDestination = this.lx.engine.mixer.backgroundTransparent.getArray();
    int[] blendOutput = this.blendBuffer.getArray();
    for (LXChannel channel : this.channels) {
      if (channel.enabled.isOn()) {
        LXBlend blend = channel.blendMode.getObject();
        blend.blend(blendDestination, channel.getColors(), channel.fader.getValue(), blendOutput);
        blendDestination = blendOutput;
      }
    }
    this.colors = blendDestination;
    ((LXGroup.Profiler) this.profiler).compositeNanos = System.nanoTime() - compositeStart;

    // Run group effects
    long effectStart = System.nanoTime();
    if (this.effects.size() > 0) {
      if (blendDestination != blendOutput) {
        System.arraycopy(blendDestination, 0, blendOutput, 0, blendOutput.length);
      }
      for (LXEffect effect : this.effects) {
        effect.setBuffer(this.blendBuffer);
        effect.loop(deltaMs);
      }
      this.colors = blendOutput;
    }
    ((LXBus.Profiler) this.profiler).effectNanos = System.nanoTime() - effectStart;

  }

  @Override
  public void dispose() {
    if (!this.channels.isEmpty()) {
      throw new IllegalStateException("Cannot dispose of LXGroup that still has channels");
    }
    super.dispose();
  }

}
