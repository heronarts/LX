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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXMixerEngine;
import heronarts.lx.utils.LXUtils;

/**
 * Utility class to access a fixed number of mixer channels.
 * Adds channel banking and an option to hide channels.
 */
public class MixerSurface {

  public interface Listener {
    /**
     * Selected bank has changed
     */
    public void onBankChanged(int bank);

    /**
     * The channel assigned to a surface position has changed.
     * @param index Index relative to surface
     * @param channel New channel value, could be null
     * @param previousChannel Previous channel value, could be null
     */
    public void onChannelChanged(int index, LXAbstractChannel channel, LXAbstractChannel previousChannel);
  }

  private final Listener listener;
  private final LX lx;
  private final int bankSize;

  private int bank = 0;

  private final Map<LXAbstractChannel, Boolean> channelAllowance = new HashMap<LXAbstractChannel, Boolean>();
  private final List<LXAbstractChannel> allowedChannels = new ArrayList<LXAbstractChannel>();
  private final List<LXAbstractChannel> mutableChannels = new ArrayList<LXAbstractChannel>();
  public final List<LXAbstractChannel> channels = Collections.unmodifiableList(this.mutableChannels);

  public MixerSurface(LX lx, Listener listener, int bankSize) {
    this.lx = lx;
    this.listener = listener;
    this.bankSize = bankSize;

    for (int i = 0; i < this.bankSize; i++) {
      this.mutableChannels.add(null);
    }
  }

  /**
   * Child class may hide channels by returning false to this method.
   * Method will be called once per channel.
   */
  public boolean isChannelAllowed(LXAbstractChannel channel) {
    return true;
  }

  public void setChannelAllowed(LXAbstractChannel channel, boolean allowed) {
    if (!this.channelAllowance.containsKey(channel)) {
      throw new IllegalArgumentException("Channel unknown by MixerSurface");
    }

    if (this.channelAllowance.get(channel) != allowed) {
      this.channelAllowance.put(channel, allowed);
      refreshAllowedChannels();
    }
  }

  private int getIndexOffset() {
    return this.bank * this.bankSize;
  }

  public boolean incrementBank() {
    return setBank(this.bank + 1);
  }

  public boolean decrementBank() {
    if (this.bank > 0) {
      return setBank(this.bank - 1);
    }
    return false;
  }

  public boolean setBank(int bank) {
    if (bank < 0) {
      throw new IllegalArgumentException("Channel bank may not be less than zero");
    }
    if (this.bank == bank) {
      return false;
    }

    this.bank = bank;
    if (this.isRegistered) {
      refreshChannels();
    }
    listener.onBankChanged(this.bank);
    return true;
  }

  /**
   * Retrieve channel for a given surface index, relative to current bank.
   */
  public LXAbstractChannel get(int index) {
    if (index >= 0 && index < this.channels.size()) {
      return this.channels.get(index);
    }
    return null;
  }

  /**
   * Retrieve the index of a channel relative to surface.
   * If channel is not assigned to a surface position, null will be returned.
   */
  public int getIndex(LXAbstractChannel channel) {
    return this.channels.indexOf(channel);
  }

  private final LXMixerEngine.Listener mixerEngineListener = new LXMixerEngine.Listener() {
    @Override
    public void channelAdded(LXMixerEngine mixer, LXAbstractChannel channel) {
      registerChannel(channel);
      refreshAllowedChannels();
    }

    @Override
    public void channelMoved(LXMixerEngine mixer, LXAbstractChannel channel) {
      refreshAllowedChannels();
    }

    @Override
    public void channelRemoved(LXMixerEngine mixer, LXAbstractChannel channel) {
      channelAllowance.remove(channel);
      if (allowedChannels.remove(channel)) {
        refreshAllowedChannels();
      }
    }
  };

  private boolean isRegistered = false;

  public void register() {
    if (this.isRegistered) {
      throw new IllegalStateException("Cannot double-register MixerSurface");
    }

    this.lx.engine.mixer.addListener(this.mixerEngineListener);

    for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
      registerChannel(channel);
    }
    refreshAllowedChannels();

    this.isRegistered = true;
  }

  public void unregister() {
    if (!this.isRegistered) {
      throw new IllegalStateException("Cannot unregister non-registered MixerSurface");
    }

    this.lx.engine.mixer.removeListener(this.mixerEngineListener);

    this.channelAllowance.clear();
    this.allowedChannels.clear();
    refreshChannels();

    this.isRegistered = false;
  }

  private void registerChannel(LXAbstractChannel channel) {
    this.channelAllowance.put(channel, isChannelAllowed(channel));
  }

  private void refreshAllowedChannels() {
    this.allowedChannels.clear();
    for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
      if (this.channelAllowance.get(channel)) {
        this.allowedChannels.add(channel);
      }
    }
    refreshChannels();
  }

  private void refreshChannels() {
    final int indexOffset = getIndexOffset();
    LXAbstractChannel channel, previous;
    for (int i = 0; i < this.bankSize; i++) {
      channel = i + indexOffset < this.allowedChannels.size() ? this.allowedChannels.get(i + indexOffset) : null;
      previous = this.mutableChannels.get(i);
      if (!LXUtils.equals(channel, previous)) {
        this.mutableChannels.set(i, channel);
        this.listener.onChannelChanged(i, channel, previous);
      }
    }
  }

  public void dispose() {
    if (this.isRegistered) {
      unregister();
    }
  }
}
