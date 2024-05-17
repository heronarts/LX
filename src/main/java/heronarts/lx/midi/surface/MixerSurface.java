/**
 * Copyright 2024- Justin Belcher, Mark C. Slee, Heron Arts LLC
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
 * @author Justin K. Belcher <justin@jkb.studio>
 */

package heronarts.lx.midi.surface;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClipEngine;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXMixerEngine;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.utils.LXUtils;

/**
 * Utility class to access a fixed number of mixer channels, potentially also
 * with clip/pattern grid control.
 */
public class MixerSurface implements LXParameterListener {

  public interface Listener {

    /**
     * A channel previously assigned to a surface index has been changed
     *
     * @param index Index relative to surface
     * @param channel Channel that has been removed
     * @param previous Previous channel that was assigned
     */
    public void onChannelChanged(int index, LXAbstractChannel channel, LXAbstractChannel previous);

    public void onGridOffsetChanged();

  }

  private final LX lx;

  private final Listener listener;

  /**
   * How many mixer channels this surface can control at once
   */
  public final int bankWidth;

  private int bankHeight = 0;

  public final DiscreteParameter channelNumber =
    new DiscreteParameter("Channel Num", 1, 2)
    .setDescription("Channel number that the mixer surface begins at");

  public final DiscreteParameter gridClipOffset =
    new DiscreteParameter("Grid Clip Offset", 0, 1)
    .setDescription("Row offset for grid clip triggering");

  public final DiscreteParameter gridPatternOffset =
    new DiscreteParameter("Grid Pattern Offset", 0, 1)
    .setDescription("Row offset for grid pattern triggering");

  private final LXAbstractChannel[] channels;

  private boolean hasGrid = false;

  private LXClipEngine.GridMode gridMode = LXClipEngine.GridMode.PATTERNS;

  private final LXParameterListener onChannelIndexChanged = this::onChannelIndexChanged;
  private final LXParameterListener onGridOffsetChanged = this::onGridOffsetChanged;

  public MixerSurface(LX lx, Listener listener, int bankWidth) {
    this(lx, listener, bankWidth, 0);
  }

  public MixerSurface(LX lx, Listener listener, int bankWidth, int bankHeight) {
    this.lx = lx;
    this.listener = listener;
    this.bankWidth = bankWidth;
    this.bankHeight = bankHeight;
    this.channels = new LXAbstractChannel[bankWidth];
    setChannelIndexRange();
    lx.engine.clips.numScenes.addListener(this, true);
    lx.engine.clips.numPatterns.addListener(this, true);
  }

  public void onParameterChanged(LXParameter p) {
    if (p == this.lx.engine.clips.numScenes) {
      this.gridClipOffset.setRange(this.lx.engine.clips.numScenes.getValuei());
    } else if (p == this.lx.engine.clips.numPatterns) {
      this.gridPatternOffset.setRange(this.lx.engine.clips.numPatterns.getValuei());
    }
  }

  public MixerSurface setGridMode(LXClipEngine.GridMode gridMode) {
    if (this.gridMode != gridMode) {
      this.hasGrid = (gridMode != null);
      this.gridMode = gridMode;
      if (this.isRegistered) {
        this.lx.engine.clips.controlSurfaceSemaphore.bang();
      }
    }
    return this;
  }

  public boolean hasGrid() {
    return this.hasGrid;
  }

  public LXClipEngine.GridMode getGridMode() {
    return this.gridMode;
  }

  private void setChannelIndexRange() {
    this.channelNumber.setRange(1, LXUtils.max(2, 1 + this.lx.engine.mixer.channels.size()));
  }

  public int getChannelIndex() {
    return this.channelNumber.getValuei() - 1;
  }

  public void incrementGridOffset() {
    switch (this.gridMode) {
    case CLIPS:
      this.gridClipOffset.increment();
      break;
    case PATTERNS:
      this.gridPatternOffset.increment();
      break;
    }
  }

  public void decrementGridOffset() {
    switch (this.gridMode) {
    case CLIPS:
      this.gridClipOffset.decrement();
      break;
    case PATTERNS:
      this.gridPatternOffset.decrement();
      break;
    }
  }

  public int getGridOffset() {
    switch (this.gridMode) {
    case CLIPS:
      return this.gridClipOffset.getValuei();
    case PATTERNS:
      return this.gridPatternOffset.getValuei();
    }
    return 0;
  }

  public int getGridClipOffset() {
    return this.gridClipOffset.getValuei();
  }

  public int getGridPatternOffset() {
    return this.gridPatternOffset.getValuei();
  }

  public int getBankWidth() {
    return this.bankWidth;
  }

  public int getBankHeight() {
    return this.bankHeight;
  }

  public void incrementChannel() {
    this.channelNumber.increment(1);
  }

  public void decrementChannel() {
    this.channelNumber.decrement(1);
  }

  public void incrementBank() {
    this.channelNumber.increment(this.bankWidth);
  }

  public void decrementBank() {
    this.channelNumber.decrement(this.bankWidth);
  }

  private void onChannelIndexChanged(LXParameter p) {
    if (this.isRegistered) {
      lx.engine.clips.controlSurfaceSemaphore.bang();
      refreshChannels();
    }
  }

  private void onGridOffsetChanged(LXParameter p) {
    if (this.isRegistered) {
      this.listener.onGridOffsetChanged();
      lx.engine.clips.controlSurfaceSemaphore.bang();
    }
  }

  /**
   * Retrieve channel for a given surface index, relative to current index.
   */
  public LXAbstractChannel getChannel(int index) {
    if (index >= 0 && index < this.channels.length) {
      return this.channels[index];
    }
    return null;
  }

  public boolean contains(LXAbstractChannel channel) {
    for (LXAbstractChannel contains : this.channels) {
      if (contains == channel) {
        return true;
      }
    }
    return false;
  }

  /**
   * Retrieve the index of a channel relative to surface.
   * If channel is not assigned to a surface position, -1 will be returned.
   *
   * @param channel The channel to find
   * @return Relative index of channel on surface, or -1 if not found
   */
  public int getIndex(LXAbstractChannel channel) {
    for (int i = 0; i < this.channels.length; ++i) {
      if (this.channels[i] == channel) {
        return i;
      }
    }
    return -1;
  }

  private final LXMixerEngine.Listener mixerEngineListener = new LXMixerEngine.Listener() {
    @Override
    public void channelAdded(LXMixerEngine mixer, LXAbstractChannel channel) {
      setChannelIndexRange();
      refreshChannels();
    }

    @Override
    public void channelMoved(LXMixerEngine mixer, LXAbstractChannel channel) {
      refreshChannels();
    }

    @Override
    public void channelRemoved(LXMixerEngine mixer, LXAbstractChannel channel) {
      setChannelIndexRange();
      refreshChannels();
    }
  };

  private boolean isRegistered = false;

  public void register() {
    if (this.isRegistered) {
      throw new IllegalStateException("Cannot double-register MixerSurface");
    }

    setChannelIndexRange();
    this.channelNumber.addListener(this.onChannelIndexChanged);
    this.gridClipOffset.addListener(this.onGridOffsetChanged);
    this.gridPatternOffset.addListener(this.onGridOffsetChanged);
    this.lx.engine.clips.addControlSurface(this);
    this.lx.engine.mixer.addListener(this.mixerEngineListener);
    refreshChannels();

    this.isRegistered = true;
  }

  public void unregister() {
    if (!this.isRegistered) {
      throw new IllegalStateException("Cannot unregister non-registered MixerSurface");
    }

    this.channelNumber.removeListener(this.onChannelIndexChanged);
    this.gridClipOffset.removeListener(this.onGridOffsetChanged);
    this.gridPatternOffset.removeListener(this.onGridOffsetChanged);
    this.lx.engine.mixer.removeListener(this.mixerEngineListener);
    this.lx.engine.clips.removeControlSurface(this);

    clearChannels();
    this.isRegistered = false;
  }

  private void clearChannels() {
    for (int i = 0; i < this.channels.length; ++i) {
      setChannel(i, null);
    }
  }

  private void setChannel(int index, LXAbstractChannel channel) {
    if (index < 0 || index >= this.bankWidth) {
      throw new IllegalArgumentException("setChannel() index must be in bounds: " + index);
    }
    if (this.channels[index] != channel) {
      LXAbstractChannel previous = this.channels[index];
      this.channels[index] = channel;
      this.listener.onChannelChanged(index, channel, previous);
    }
  }

  private void refreshChannels() {
    final int indexOffset = getChannelIndex();
    final int numChannels = lx.engine.mixer.channels.size();
    for (int i = 0; i < this.bankWidth; i++) {
      int targetIndex = indexOffset + i;
      setChannel(i, (targetIndex < numChannels) ? lx.engine.mixer.channels.get(targetIndex) : null);
    }
  }

  public void dispose() {
    if (this.isRegistered) {
      unregister();
      this.isRegistered = false;
    }
    this.lx.engine.clips.numScenes.removeListener(this);
    this.lx.engine.clips.numPatterns.removeListener(this);
  }
}
