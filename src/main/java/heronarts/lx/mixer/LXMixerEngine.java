/**
 * Copyright 2020- Mark C. Slee, Heron Arts LLC
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXEngine;
import heronarts.lx.LXRegistry;
import heronarts.lx.LXSerializable;
import heronarts.lx.ModelBuffer;
import heronarts.lx.blend.AddBlend;
import heronarts.lx.blend.LXBlend;
import heronarts.lx.clip.LXClip;
import heronarts.lx.color.LXColor;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.model.LXModel;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.osc.LXOscEngine;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.ObjectParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.utils.LXUtils;

/**
 * Encapsulation of all the LX channel blending and mixer
 */
public class LXMixerEngine extends LXComponent implements LXOscComponent {

  public static class PatternFriendAccess {
    private PatternFriendAccess() {}
  }

  static final PatternFriendAccess patternFriendAccess = new PatternFriendAccess();

  public interface Listener {
    public void channelAdded(LXMixerEngine mixer, LXAbstractChannel channel);
    public void channelRemoved(LXMixerEngine mixer, LXAbstractChannel channel);
    public void channelMoved(LXMixerEngine mixer, LXAbstractChannel channel);
  }

  private final List<Listener> listeners = new ArrayList<Listener>();

  public final LXMixerEngine addListener(Listener listener) {
    Objects.requireNonNull(listener, "May not add null LXMixerEngine.Listener");
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannod add mixer listener twice: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  public final LXMixerEngine removeListener(Listener listener) {
    this.listeners.remove(listener);
    return this;
  }

  private final List<LXAbstractChannel> mutableChannels = new ArrayList<LXAbstractChannel>();
  public final List<LXAbstractChannel> channels = Collections.unmodifiableList(this.mutableChannels);

  public final LXMasterBus masterBus;

  private final AddBlend addBlend;

  public final DiscreteParameter focusedChannel =
    new DiscreteParameter("Channel", 1)
    .setDescription("Which channel is currently focused in the UI");

  public final DiscreteParameter focusedChannelAux =
    new DiscreteParameter("Aux", 1)
    .setDescription("Which channel is currently focused in the auxiliary UI");

  public final CompoundParameter crossfader = new CompoundParameter("Crossfader", 0.5)
  .setDescription("Applies blending between output groups A and B")
  .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final ObjectParameter<LXBlend> crossfaderBlendMode;
  private LXBlend activeCrossfaderBlend;

  public final BooleanParameter cueA =
    new BooleanParameter("Cue-A", false)
    .setDescription("Enables cue preview of crossfade group A");

  public final BooleanParameter cueB =
    new BooleanParameter("Cue-B", false)
    .setDescription("Enables cue preview of crossfade group B");

  public final BooleanParameter auxA =
    new BooleanParameter("Aux-A", false)
    .setDescription("Enables aux preview of crossfade group A");

  public final BooleanParameter auxB =
    new BooleanParameter("Aux-B", false)
    .setDescription("Enables aux preview of crossfade group B");

  final ModelBuffer backgroundBlack;
  final ModelBuffer backgroundTransparent;
  private final ModelBuffer blendBufferLeft;
  private final ModelBuffer blendBufferRight;

  public final BooleanParameter viewCondensed =
    new BooleanParameter("View Condensed", false)
    .setDescription("Whether the mixer view should be condensed");

  public final BooleanParameter viewStacked =
    new BooleanParameter("View Stacked", false)
    .setDescription("Whether the mixer view is stacked on the device bin");

  public final BooleanParameter viewDeviceBin =
    new BooleanParameter("View Device Bin", true)
    .setDescription("Whether the device bin is shown in stacked view");

  public LXMixerEngine(LX lx) {
    super(lx, "Mixer");

    // Background and blending buffers
    this.backgroundBlack = new ModelBuffer(lx, LXColor.BLACK);
    this.backgroundTransparent = new ModelBuffer(lx, 0);
    this.blendBufferLeft = new ModelBuffer(lx);
    this.blendBufferRight = new ModelBuffer(lx);
    LX.initProfiler.log("Engine: Mixer: Buffers");

    // Set up global add blend
    this.addBlend = new AddBlend(lx);
    this.addBlend.onActive();

    // Master crossfader blend modes
    this.crossfaderBlendMode =
      new ObjectParameter<LXBlend>("Crossfader Blend", new LXBlend[1])
      .setDescription("Sets the blend mode used for the master crossfader");
    updateCrossfaderBlendOptions();
    LX.initProfiler.log("Engine: Mixer: Blends");

    // Master channel
    addChild("master", this.masterBus = new LXMasterBus(lx));
    LX.initProfiler.log("Engine: Mixer: Master Channel");

    // LX top level model listener
    lx.addListener(new LX.Listener() {
      @Override
      public void modelChanged(LX lx, LXModel model) {
        for (LXBus bus : channels) {
          bus.setModel(model);
        }
        masterBus.setModel(model);
      }
    });

    lx.registry.addListener(new LXRegistry.Listener() {
      @Override
      public void channelBlendsChanged(LX lx) {
        for (LXAbstractChannel channel : channels) {
          channel.updateChannelBlendOptions();
        }
      }

      @Override
      public void transitionBlendsChanged(LX lx) {
        for (LXAbstractChannel channel : channels) {
          if (channel instanceof LXChannel) {
            ((LXChannel) channel).updateTransitionBlendOptions();
          }
        }
      }

      @Override
      public void crossfaderBlendsChanged(LX lx) {
        updateCrossfaderBlendOptions();
      }
    });

    // Register channels array
    addArray("channel", this.channels);

    // Parameters
    addParameter("crossfader", this.crossfader);
    addParameter("crossfaderBlendMode", this.crossfaderBlendMode);
    addParameter("focusedChannel", this.focusedChannel);
    addParameter("focusedChannelAux", this.focusedChannelAux);
    addParameter("cueA", this.cueA);
    addParameter("cueB", this.cueB);
    addParameter("auxA", this.auxA);
    addParameter("auxB", this.auxB);
    addParameter("viewCondensed", this.viewCondensed);
    addParameter("viewStacked", this.viewStacked);
    addParameter("viewDeviceBin", this.viewDeviceBin);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (this.crossfaderBlendMode == p) {
      this.activeCrossfaderBlend.onInactive();
      this.activeCrossfaderBlend = this.crossfaderBlendMode.getObject();
      this.activeCrossfaderBlend.onActive();
    } else if (this.cueA == p) {
      if (this.cueA.isOn()) {
        this.cueB.setValue(false);
        for (LXAbstractChannel channel : this.channels) {
          channel.cueActive.setValue(false);
        }
      }
    } else if (this.cueB == p) {
      if (this.cueB.isOn()) {
        this.cueA.setValue(false);
        for (LXAbstractChannel channel : this.channels) {
          channel.cueActive.setValue(false);
        }
      }
    } else if (this.auxA == p) {
      if (this.auxA.isOn()) {
        this.auxB.setValue(false);
        for (LXAbstractChannel channel : this.channels) {
          channel.auxActive.setValue(false);
        }
      }
    } else if (this.auxB == p) {
      if (this.auxB.isOn()) {
        this.auxA.setValue(false);
        for (LXAbstractChannel channel : this.channels) {
          channel.auxActive.setValue(false);
        }
      }
    } else if (this.focusedChannel == p) {
      LXClip clip = this.lx.engine.clips.getFocusedClip();
      if (clip != null && clip.bus != getFocusedChannel()) {
        this.lx.engine.clips.setFocusedClip(null);
      }
    }
  }

  public static final String PATH_CHANNEL = "channel";
  public static final String PATH_FOCUSED = "focused";
  public static final String PATH_MASTER = "master";

  @Override
  public boolean handleOscMessage(OscMessage message, String[] parts, int index) {
    String path = parts[index];
    if (path.equals(PATH_CHANNEL)) {
      String channelIndex = parts[index+1];
      LXBus channel = null;
      if (channelIndex.equals(PATH_FOCUSED)) {
        channel = getFocusedChannel();
      } else if (channelIndex.equals(PATH_MASTER)) {
        channel = this.masterBus;
      } else if (channelIndex.matches("\\d+")) {
        channel = this.channels.get(Integer.parseInt(channelIndex) - 1);
      } else {
        for (LXAbstractChannel bus : this.channels) {
          if (bus.getOscLabel().equals(channelIndex)) {
            channel = bus;
            break;
          }
        }
      }
      if (channel == null) {
        LXOscEngine.error("Engine has no channel at path: " + channelIndex);
        return false;
      } else {
        if (channel instanceof LXChannel) {
          return ((LXChannel)channel).handleOscMessage(message, parts, index+2);
        } else {
          return channel.handleOscMessage(message, parts, index+2);
        }
      }
    }
    return super.handleOscMessage(message, parts, index);
  }

  private LXBlend[] instantiateBlends(List<Class<? extends LXBlend>> blendTypes) {
    List<LXBlend> blends = new ArrayList<LXBlend>(blendTypes.size());
    for (Class<? extends LXBlend> blend : blendTypes) {
      try {
        blends.add(this.lx.instantiateBlend(blend));
      } catch (LX.InstantiationException x) {
        this.lx.pushError(x, "Cannot instantiate blend class: " + blend.getName() + ". Check that content files are not missing?");
      }
    }
    return blends.toArray(new LXBlend[0]);
  }

  public LXBlend[] instantiateChannelBlends() {
    return instantiateBlends(this.lx.registry.channelBlends);
  }

  protected LXBlend[] instantiateTransitionBlends() {
    return instantiateBlends(this.lx.registry.transitionBlends);
  }

  protected LXBlend[] instantiateCrossfaderBlends() {
    return instantiateBlends(this.lx.registry.crossfaderBlends);
  }

  private void updateCrossfaderBlendOptions() {
    for (LXBlend blend : this.crossfaderBlendMode.getObjects()) {
      if (blend != null) {
        LX.dispose(blend);
      }
    }
    this.crossfaderBlendMode.setObjects(instantiateCrossfaderBlends());
    this.activeCrossfaderBlend = this.crossfaderBlendMode.getObject();
    this.activeCrossfaderBlend.onActive();
  }

  public List<LXAbstractChannel> getChannels() {
    return this.channels;
  }

  public LXChannel getDefaultChannel() {
    for (LXAbstractChannel channel : this.channels) {
      if (channel instanceof LXChannel) {
        return (LXChannel) channel;
      }
    }
    return null;
  }

  public LXAbstractChannel getLastChannel() {
    return this.channels.get(this.channels.size() - 1);
  }

  public LXAbstractChannel getChannel(int channelIndex) {
    return this.mutableChannels.get(channelIndex);
  }

  public LXAbstractChannel getChannel(String label) {
    for (LXAbstractChannel channel : this.mutableChannels) {
      if (channel.getLabel().equals(label)) {
        return channel;
      }
    }
    return null;
  }

  public LXBus getFocusedChannel() {
    if (this.focusedChannel.getValuei() == this.mutableChannels.size()) {
      return this.masterBus;
    }
    return getChannel(this.focusedChannel.getValuei());
  }

  public LXMixerEngine setFocusedChannel(LXBus channel) {
    if (channel == this.masterBus) {
      this.focusedChannel.setValue(this.mutableChannels.size());
    } else {
      this.focusedChannel.setValue(this.mutableChannels.indexOf(channel));
    }
    return this;
  }

  public LXBus getFocusedChannelAux() {
    if (this.focusedChannelAux.getValuei() == this.mutableChannels.size()) {
      return this.masterBus;
    }
    return getChannel(this.focusedChannelAux.getValuei());
  }

  public LXMixerEngine setFocusedChannelAux(LXBus channel) {
    if (channel == this.masterBus) {
      this.focusedChannelAux.setValue(this.mutableChannels.size());
    } else {
      this.focusedChannelAux.setValue(this.mutableChannels.indexOf(channel));
    }
    return this;
  }

  public LXMixerEngine deselectChannel(LXBus bus) {
    boolean otherSelected = false;
    for (LXAbstractChannel channel : this.channels) {
      if (channel != bus && channel.selected.isOn()) {
        otherSelected = true;
        break;
      }
    }
    if (this.masterBus != bus && this.masterBus.selected.isOn()) {
      otherSelected = true;
    }
    if (otherSelected) {
      bus.selected.setValue(false);
    }
    return this;
  }

  public LXMixerEngine selectChannel(LXBus bus) {
    return selectChannel(bus, false);
  }

  public LXMixerEngine selectChannel(LXBus bus, boolean multipleSelection) {
    multipleSelection =
      multipleSelection &&
      (this.masterBus != bus) &&
      !this.masterBus.selected.isOn();
    if (!multipleSelection) {
      for (LXAbstractChannel channel : this.channels) {
        if (channel != bus) {
          channel.selected.setValue(false);
        }
      }
      if (this.masterBus != bus) {
        this.masterBus.selected.setValue(false);
      }
    } else {
      // In multiple selection mode, de-select anything from another group
      LXGroup busGroup = bus.getGroup();
      for (LXAbstractChannel channel : this.channels) {
        if (channel.getGroup() != busGroup) {
          channel.selected.setValue(false);
        }
      }
    }
    bus.selected.setValue(true);
    return this;
  }

  public LXMixerEngine selectChannelRange(LXBus destination) {
    final int focusIndex = this.focusedChannel.getValuei();
    final int selectIndex = destination.getIndex();
    final LXGroup selectedGroup = getFocusedChannel().getGroup();

    final int minIndex = LXUtils.min(focusIndex, selectIndex);
    final int maxIndex = LXUtils.max(focusIndex, selectIndex);

    for (LXAbstractChannel bus : this.channels) {
      final int busIndex = bus.getIndex();
      bus.selected.setValue(
        (bus.getGroup() == selectedGroup) &&
        (busIndex >= minIndex) &&
        (busIndex <= maxIndex)
      );
    }
    this.masterBus.selected.setValue(false);
    return this;
  }

  public LXChannel addChannel() {
    return addChannel(-1);
  }

  public LXChannel addChannel(int index, JsonObject channelObj) {
    return addChannel(index, channelObj, new LXPattern[0]);
  }

  public LXChannel addChannel(JsonObject channelObj) {
    return addChannel(-1, channelObj, new LXPattern[0]);
  }

  public LXChannel addChannel(int index) {
    return addChannel(index, null, new LXPattern[0]);
  }

  public LXChannel addChannel(LXPattern[] patterns) {
    return addChannel(-1, null, patterns);
  }

  public LXChannel addChannel(int index, LXPattern[] patterns) {
    return addChannel(index, null, patterns);
  }

  public LXChannel addChannel(int index, JsonObject channelObj, LXPattern[] patterns) {
    if (index > this.mutableChannels.size()) {
      throw new IllegalArgumentException("Invalid channel index: " + index);
    }
    if (index < 0) {
      index = this.mutableChannels.size();
    }
    LXChannel channel = new LXChannel(this.lx, index, patterns);
    channel.fader.setValue(this.channels.isEmpty() ? 1 : 0);
    if (channelObj != null) {
      channel.load(this.lx, channelObj);
    }

    _addChannel(channel, index);

    // This new channel is focused now!
    if (this.focusedChannel.getValuei() == index) {
      this.focusedChannel.bang();
    } else {
      this.focusedChannel.setValue(index);
    }

    // For the aux bus as well
    if (this.focusedChannelAux.getValuei() == index) {
      this.focusedChannelAux.bang();
    } else {
      this.focusedChannelAux.setValue(index);
    }

    return channel;
  }

  public LXMixerEngine group(LXGroup group, LXChannel channel, int index) {
    if (channel.getGroup() != null) {
      throw new IllegalStateException("Cannot group channel that is already in another group");
    }
    if (index <= group.getIndex() || (index > group.getIndex() + group.channels.size() + 1)) {
      throw new IllegalArgumentException("Invalid index specified to group channel: " + index);
    }
    this.mutableChannels.remove(channel);
    this.mutableChannels.add(index, channel);
    _reindexChannels();
    group.addChannel(channel);
    for (Listener listener : this.listeners) {
      listener.channelMoved(this, channel);
    }
    return this;
  }

  public LXMixerEngine ungroup(LXChannel channel) {
    boolean focused = this.focusedChannel.getValuei() == channel.index;
    boolean focusedAux = this.focusedChannelAux.getValuei() == channel.index;
    LXGroup group = channel.getGroup();
    if (group != null) {
      group.removeChannel(channel);
      this.mutableChannels.remove(channel);
      this.mutableChannels.add(group.getIndex() + group.channels.size() + 1, channel);
      _reindexChannels();
      for (Listener listener : this.listeners) {
        listener.channelMoved(this, channel);
      }
      if (focused) {
        this.focusedChannel.setValue(channel.index);
      }
      if (focusedAux) {
        this.focusedChannelAux.setValue(channel.index);
      }
    }
    return this;
  }

  public LXGroup addGroupFromSelection() {
    return addGroup(getSelectedChannelsForGroup());
  }

  public LXGroup addGroup() {
    return addGroup(-1);
  }

  public LXGroup addGroup(int index) {
    if (index > this.mutableChannels.size()) {
      throw new IllegalArgumentException("Invalid group index: " + index);
    }
    if (index < 0) {
      index = this.mutableChannels.size();
    }

    LXGroup group = new LXGroup(this.lx, index);
    _addChannel(group, group.getIndex());
    return group;

  }

  public LXGroup addGroup(List<LXChannel> groupChannels) {
    if (groupChannels.isEmpty()) {
      return null;
    }
    int groupIndex = groupChannels.get(0).index;
    LXGroup group = new LXGroup(this.lx, groupIndex);
    int reindex = groupIndex;
    for (LXChannel channel : groupChannels) {
      // Put the group channels in order in their group
      this.mutableChannels.remove(channel);
      this.mutableChannels.add(reindex++, channel);
      group.addChannel(channel);
    }
    _addChannel(group, group.getIndex());

    // Fix indexing on all channels
    _reindexChannels();

    // This new group channel is focused now!
    if (this.focusedChannel.getValuei() == groupIndex) {
      this.focusedChannel.bang();
    } else {
      this.focusedChannel.setValue(groupIndex);
    }
    if (this.focusedChannelAux.getValuei() == groupIndex) {
      this.focusedChannelAux.bang();
    } else {
      this.focusedChannelAux.setValue(groupIndex);
    }

    selectChannel(group);
    return group;
  }

  public List<LXChannel> getSelectedChannelsForGroup() {
    List<LXChannel> groupChannels = new ArrayList<LXChannel>();
    for (LXAbstractChannel channel : this.channels) {
      if (channel.isChannel() && channel.selected.isOn() && !channel.isInGroup()) {
        groupChannels.add((LXChannel) channel);
      }
    }
    return groupChannels;
  }

  private void _addChannel(LXAbstractChannel channel, int index) {
    channel.setMixer(this);
    this.mutableChannels.add(index, channel);
    this.focusedChannel.setRange(this.mutableChannels.size() + 1);
    this.focusedChannelAux.setRange(this.mutableChannels.size() + 1);
    for (Listener listener : this.listeners) {
      listener.channelAdded(this, channel);
    }
    _reindexChannels();
  }

  private void _reindexChannels() {
    int i = 0;
    for (LXAbstractChannel channelBus : this.channels) {
      channelBus.setIndex(i++);
    }
  }

  public void removeSelectedChannels() {
    List<LXAbstractChannel> toRemove = new ArrayList<LXAbstractChannel>();
    for (LXAbstractChannel channel : this.channels) {
      if (channel.selected.isOn() && !toRemove.contains(channel.getGroup())) {
        toRemove.add(channel);
      }
    }
    for (LXAbstractChannel channel : toRemove) {
      removeChannel(channel);
    }
  }

  public void removeChannel(LXAbstractChannel channel) {
    if (!this.mutableChannels.contains(channel)) {
      throw new IllegalStateException("Engine does not contain channel: " + channel);
    }

    // Group channel? Remove all of the children first...
    if (channel instanceof LXGroup) {
      LXGroup group = (LXGroup) channel;
      List<LXChannel> removeGroupChannels = new ArrayList<LXChannel>(group.channels);
      for (LXChannel c : removeGroupChannels) {
        removeChannel(c);
      }
    }

    // Are we in a group? Get out of it
    if (channel instanceof LXChannel) {
      LXGroup group = channel.getGroup();
      if (group != null) {
        group.removeChannel((LXChannel) channel);
      }
    }

    // Remove ourselves
    this.mutableChannels.remove(channel);

    // Fix indexing on all channels
    _reindexChannels();

    // Update the focused channel ranges
    final int numChannels = this.mutableChannels.size();
    final int focused = this.focusedChannel.getValuei();
    final int focusedAux = this.focusedChannelAux.getValuei();
    if (focused > numChannels) {
      this.focusedChannel.setValue(numChannels, false);
    } else if ((numChannels > 0) && (focused == numChannels)) {
      this.focusedChannel.setValue(numChannels - 1, false);
    }
    if (focusedAux > numChannels) {
      this.focusedChannelAux.setValue(numChannels, false);
    } else if ((numChannels > 0) && (focusedAux == numChannels)) {
      this.focusedChannelAux.setValue(numChannels - 1, false);
    }
    this.focusedChannel.setRange(numChannels + 1);
    this.focusedChannelAux.setRange(numChannels + 1);

    // Notify both focused channel listeners after all the focus ranges have been updated,
    // otherwise a listener to focusedChannel changing could retrieve an invalid value for
    // the aux value
    this.focusedChannel.bang();
    this.focusedChannelAux.bang();

    // Notify listeners
    for (Listener listener : this.listeners) {
      listener.channelRemoved(this, channel);
    }

    // Nothing selected? Select focused...
    boolean selected = false;
    for (LXAbstractChannel bus : this.channels) {
      if (bus.selected.isOn()) {
        selected = true;
        break;
      }
    }
    if (!selected) {
      getFocusedChannel().selected.setValue(true);
    }

    LX.dispose(channel);
  }

  /**
   * Move a channel by a relative increment. Handles group overlap moves automatically.
   *
   * @param channel Channel to move
   * @param delta Relative amount to move by
   */
  public void moveChannel(LXAbstractChannel channel, int delta) {
    if (delta != 1 && delta != -1) {
      throw new IllegalArgumentException("moveChannel() may only be called with delta of -1 or 1");
    }

    final int index = channel.getIndex() + delta;
    if (index < 0 || index >= this.mutableChannels.size()) {
      return;
    }

    final LXBus focused = getFocusedChannel();
    final LXBus focusedAux = getFocusedChannelAux();

    final LXGroup group = channel.getGroup();
    if (group != null) {
      // Channel is within a group, cannot be moved out of it
      if (index <= group.getIndex() || index > (group.getIndex() + group.channels.size())) {
        return;
      }
      this.mutableChannels.remove(channel);
      this.mutableChannels.add(index, channel);
    } else {
      // Channel is top-level, need to move groups in chunks
      boolean isGroup = channel instanceof LXGroup;
      if (isGroup && delta > 0) {
        delta += ((LXGroup) channel).channels.size();
      }
      int neighborIndex = channel.getIndex() + delta;
      if (neighborIndex < 0 || neighborIndex >= this.mutableChannels.size()) {
        return;
      }

      // Figure out who our neighbor is
      LXAbstractChannel neighbor = this.mutableChannels.get(neighborIndex);
      LXGroup neighborGroup = (neighbor instanceof LXGroup) ? (LXGroup) neighbor : neighbor.getGroup();
      if (neighborGroup != null) {
        // Our neighbor is a group, flip-flop entirely with them
        if (delta > 0) {
          // Neighboring group is to our right, move their start position to our position
          int startIndex = channel.getIndex();
          this.mutableChannels.remove(neighbor);
          this.mutableChannels.add(startIndex, neighbor);
          for (LXChannel subchannel : ((LXGroup) neighbor).channels) {
            this.mutableChannels.remove(subchannel);
            this.mutableChannels.add(++startIndex, subchannel);
          }
        } else {
          // Neighboring group is to our left, move us to their start position
          int startIndex = neighborGroup.getIndex();
          this.mutableChannels.remove(channel);
          this.mutableChannels.add(startIndex, channel);
          if (isGroup) {
            for (LXChannel subchannel : ((LXGroup) channel).channels) {
              this.mutableChannels.remove(subchannel);
              this.mutableChannels.add(++startIndex, subchannel);
            }
          }
        }
      } else {
        // Our neighbor is a single channel
        if (delta > 0) {
          // Neighbor is to our right, move their start position to our position
          int startIndex = channel.getIndex();
          this.mutableChannels.remove(neighbor);
          this.mutableChannels.add(startIndex, neighbor);
        } else {
          // Neighbor is to our left, move them past us
          int endIndex = channel.getIndex();
          if (isGroup) {
            endIndex += ((LXGroup) channel).channels.size();
          }
          this.mutableChannels.remove(neighbor);
          this.mutableChannels.add(endIndex, neighbor);
        }

      }
    }

    // Fix indexing on all channels
    _reindexChannels();

    // Focused channel may have moved
    this.focusedChannel.setValue(focused.getIndex());
    this.focusedChannelAux.setValue(focusedAux.getIndex());

    for (Listener listener : this.listeners) {
      listener.channelMoved(this, channel);
    }
  }

  /**
   * Move a channel to a specified index, possibly adding or removing to a group
   *
   * @param bus Channel to move
   * @param index Index position to move to
   * @param group Group the channel should belong to, or null
   */
  public void moveChannel(LXAbstractChannel bus, int index, LXGroup group) {
    if (index < 0 || index >= this.channels.size()) {
      throw new IllegalArgumentException("Cannot move a channel to illegal index outside of mixer bounds: " + index);
    }

    final LXBus focused = getFocusedChannel();
    final LXBus focusedAux = getFocusedChannelAux();
    List<LXAbstractChannel> members = null;

    if (bus.isGroup()) {
      if (group != null) {
        throw new IllegalArgumentException("Cannot place a group into another group: " + bus + " -> " + group);
      }

      // Move the group *and* all its sub-channels
      this.mutableChannels.remove(bus);

      members = new ArrayList<LXAbstractChannel>();
      for (LXAbstractChannel candidate : this.channels) {
        if (candidate.getGroup() == bus) {
          members.add(candidate);
        }
      }
      for (LXAbstractChannel member : members) {
        this.mutableChannels.remove(member);
      }
      if (index > bus.getIndex()) {
        index -= members.size();
      }

      // Move all the sub-channels along
      this.mutableChannels.add(index++, bus);
      for (LXAbstractChannel member : members) {
        this.mutableChannels.add(index++, member);
      }

    } else if (bus instanceof LXChannel) {
      final LXChannel channel = (LXChannel) bus;
      if (channel.getGroup() != group) {
        if (channel.getGroup() != null) {
          channel.getGroup().removeChannel(channel);
        }
        if (group != null) {
          group.addChannel(channel);
        }
      }
      this.mutableChannels.remove(channel);
      this.mutableChannels.add(index, channel);
    } else {
      throw new IllegalStateException("Bus is neither a group nor a channel: " + bus);
    }

    // Fix indexing on all channels
    _reindexChannels();

    // Focused channel may have moved
    this.focusedChannel.setValue(focused.getIndex());
    this.focusedChannelAux.setValue(focusedAux.getIndex());

    // Fire listeners for channels that have moved
    for (Listener listener : this.listeners) {
      listener.channelMoved(this, bus);
      if (members != null) {
        for (LXAbstractChannel member : members) {
          listener.channelMoved(this, member);
        }
      }
    }
  }

  public LXMixerEngine enableChannelCue(LXAbstractChannel channel, boolean isExclusive) {
    channel.cueActive.setValue(true);
    if (isExclusive) {
      for (LXAbstractChannel c : getChannels()) {
        if (channel != c) {
          c.cueActive.setValue(false);
        }
      }
    }
    return this;
  }

  public LXMixerEngine enableChannelAux(LXAbstractChannel channel, boolean isExclusive) {
    channel.auxActive.setValue(true);
    if (isExclusive) {
      for (LXAbstractChannel c : getChannels()) {
        if (channel != c) {
          c.auxActive.setValue(false);
        }
      }
    }
    return this;
  }

  private class BlendStack {

    private int[] destination;
    private int[] output;

    void initialize(int[] destination, int[] output) {
      this.destination = destination;
      this.output = output;

      if (this.destination == this.output) {
        LX.error(new Exception("BlendStack initialized with the same destination/output"));
      } else {
        // We need to splat the output array right away. Channels may have views applied
        // which mean blend calls might not touch all the pixels. So we've got to get them
        // all re-initted upfront.
        System.arraycopy(this.destination, 0, this.output, 0, this.destination.length);
        this.destination = this.output;
      }
    }

    void blend(LXBlend blend, BlendStack that, double alpha, LXModel model) {
      blend(blend, that.destination, alpha, model);
    }

    void blend(LXBlend blend, int[] src, double alpha, LXModel model) {
      blend.blend(this.destination, src, alpha, this.output, model);
      this.destination = this.output;
    }

    void transition(LXBlend blend, int[] src, double lerp, LXModel model) {
      blend.lerp(this.destination, src, lerp, this.output, model);
      this.destination = this.output;
    }

    void copyFrom(BlendStack that) {
      System.arraycopy(that.destination, 0, this.output, 0, that.destination.length);
      this.destination = this.output;
    }

  }

  private final BlendStack blendStackMain = new BlendStack();
  private final BlendStack blendStackCue = new BlendStack();
  private final BlendStack blendStackAux = new BlendStack();
  private final BlendStack blendStackLeft = new BlendStack();
  private final BlendStack blendStackRight = new BlendStack();

  public void loop(LXEngine.Frame render, double deltaMs) {
    long channelStart = System.nanoTime();

    // Initialize blend stacks
    this.blendStackMain.initialize(this.backgroundBlack.getArray(), render.getMain());
    this.blendStackCue.initialize(this.backgroundBlack.getArray(), render.getCue());
    this.blendStackAux.initialize(this.backgroundBlack.getArray(), render.getAux());
    this.blendStackLeft.initialize(this.backgroundBlack.getArray(), this.blendBufferLeft.getArray());
    this.blendStackRight.initialize(this.backgroundBlack.getArray(), this.blendBufferRight.getArray());

    double crossfadeValue = this.crossfader.getValue();

    boolean leftBusActive = crossfadeValue < 1.;
    boolean rightBusActive = crossfadeValue > 0.;
    boolean cueBusActive = false;
    boolean auxBusActive = false;

    final boolean isChannelMultithreaded = this.lx.engine.isChannelMultithreaded.isOn();
    final boolean isPerformanceMode = this.lx.engine.performanceMode.isOn();

    // Step 1a: Loop all of the channels
    if (isChannelMultithreaded) {
      // If we are in super-threaded mode, run the channels on their own threads!
      for (LXAbstractChannel channel : this.channels) {
        synchronized (channel.thread) {
          channel.thread.signal.workDone = false;
          channel.thread.deltaMs = deltaMs;
          channel.thread.workReady = true;
          channel.thread.notify();
          if (!channel.thread.hasStarted) {
            channel.thread.hasStarted = true;
            channel.thread.start();
          }
        }
      }

      // Wait for all the channel threads to finish
      for (LXAbstractChannel channel : this.mutableChannels) {
        synchronized (channel.thread.signal) {
          while (!channel.thread.signal.workDone) {
            try {
              channel.thread.signal.wait();
            } catch (InterruptedException ix) {
              Thread.currentThread().interrupt();
              break;
            }
          }
          channel.thread.signal.workDone = false;
        }
      }
    } else {
      // We are not in super-threaded mode, just loop all the channels
      for (LXAbstractChannel channel : this.channels) {
        channel.loop(deltaMs);
      }
    }
    // Step 1b: Run the master channel (it may have clips on it)
    this.masterBus.loop(deltaMs);
    this.lx.engine.profiler.channelNanos = System.nanoTime() - channelStart;

    // Step 2: composite any group channels
    for (LXAbstractChannel channel : this.channels) {
      if (channel instanceof LXGroup && channel.isAnimating) {
        ((LXGroup) channel).afterLoop(deltaMs);
      }
    }

    // Check for performance quality
    long nanoLimit = (long) (1000000000 / this.lx.engine.framesPerSecond.getValuef() * .5);
    for (LXAbstractChannel channel : this.channels) {
      long renderNanos = channel.profiler.renderNanos();
      if (renderNanos > nanoLimit) {
        ++channel.performanceWarningFrameCount;
      } else {
        channel.performanceWarningFrameCount = 0;
      }
      channel.performanceWarning.setValue(channel.performanceWarningFrameCount >= 5);
    }

    // Step 3: blend the channel buffers down
    boolean blendLeft = leftBusActive || this.cueA.isOn() || (isPerformanceMode && this.auxA.isOn());
    boolean blendRight = rightBusActive || this.cueB.isOn() || (isPerformanceMode && this.auxB.isOn());
    boolean leftExists = false, rightExists = false;
    for (LXAbstractChannel channel : this.channels) {
      long blendStart = System.nanoTime();

      // Is this a group sub-channel? Those don't blend, they are already composited
      // into their group
      boolean isSubChannel = channel.getGroup() != null;

      // Blend into the output buffer
      if (!isSubChannel) {
        BlendStack blendStack = null;

        // Which output group is this channel mapped to
        switch (channel.crossfadeGroup.getEnum()) {
        case A:
          leftExists = true;
          blendStack = blendLeft ? this.blendStackLeft : null;
          break;
        case B:
          rightExists = true;
          blendStack = blendRight ? this.blendStackRight : null;
          break;
        default:
        case BYPASS:
          blendStack = blendStackMain;
          break;
        }

        if (blendStack != null && channel.enabled.isOn()) {
          double alpha = channel.fader.getValue();
          if (alpha > 0) {
            blendStack.blend(channel.blendMode.getObject(), channel.getColors(), alpha, channel.getModelView());
          }
        }
      }

      // Blend into the cue buffer, always a direct add blend for any type of channel
      if (channel.cueActive.isOn()) {
        cueBusActive = true;
        this.blendStackCue.blend(this.addBlend, channel.getColors(), 1, channel.getModelView());
      }

      // Blend into the aux buffer when in performance mode
      if (isPerformanceMode && channel.auxActive.isOn()) {
        auxBusActive = true;
        this.blendStackAux.blend(this.addBlend, channel.getColors(), 1, channel.getModelView());
      }

      ((LXAbstractChannel.Profiler) channel.profiler).blendNanos = System.nanoTime() - blendStart;
    }

    // Check if the crossfade group buses are cued
    if (this.cueA.isOn()) {
      this.blendStackCue.copyFrom(this.blendStackLeft);
      cueBusActive = true;
    } else if (this.cueB.isOn()) {
      this.blendStackCue.copyFrom(this.blendStackRight);
      cueBusActive = true;
    }

    // Crossfade groups can be aux-cued in performance mode
    if (isPerformanceMode) {
      if (this.auxA.isOn()) {
        this.blendStackAux.copyFrom(this.blendStackLeft);
        auxBusActive = true;
      } else if (this.auxB.isOn()) {
        this.blendStackAux.copyFrom(this.blendStackRight);
        auxBusActive = true;
      }
    }

    // Step 4: now we have three output buses that need mixing... the left/right crossfade
    // groups plus the main buffer. We figure out which of them are active and blend appropriately
    // Note that the A+B crossfade groups are additively mixed AFTER the main buffer
    final boolean leftContent = leftBusActive && leftExists;
    final boolean rightContent = rightBusActive && rightExists;
    final LXModel model = this.lx.getModel();

    if (leftContent && rightContent) {
      // There are left and right channels assigned!
      LXBlend blend = this.crossfaderBlendMode.getObject();
      blendStackLeft.transition(blend, blendStackRight.destination, crossfadeValue, model);
      // Add the crossfaded groups to the main buffer
      this.blendStackMain.blend(this.addBlend, blendStackLeft, 1., model);
    } else if (leftContent) {
      // Add the left group to the main buffer
      this.blendStackMain.blend(this.addBlend, this.blendStackLeft, Math.min(1, 2. * (1-crossfadeValue)), model);
    } else if (rightContent) {
      // Add the right group to the main buffer
      this.blendStackMain.blend(this.addBlend, this.blendStackRight, Math.min(1, 2. * crossfadeValue), model);
    }

    // Step 5: Time to apply master FX to the main blended output
    long effectStart = System.nanoTime();
    for (LXEffect effect : this.masterBus.getEffects()) {
      effect.setBuffer(render);
      effect.setModel(effect.getModelView());
      effect.loop(deltaMs);
    }
    ((LXBus.Profiler) this.masterBus.profiler).effectNanos = System.nanoTime() - effectStart;

    // Step 6: If the master fader is POST-visualizer/output, apply global scaling now
    if (this.masterBus.previewMode.getEnum() == LXMasterBus.PreviewMode.POST) {
      double fader = this.masterBus.fader.getValue();
      if (fader == 0) {
        // Don't multiply if it's just zero!
        Arrays.fill(this.blendStackMain.output, LXColor.BLACK);
      } else if (fader < 1.) {
        // Apply a pass to scale brightness
        final int mult = LXColor.gray(100. * fader);
        final int[] output = this.blendStackMain.output;
        for (int i = 0; i < output.length; ++i) {
          output[i] = LXColor.multiply(output[i], mult, LXColor.BLEND_ALPHA_FULL);
        }
      }
    }

    // Mark the cue active state of the buffer
    render.setCueOn(cueBusActive);
    render.setAuxOn(auxBusActive);
  }

  private static final String KEY_CHANNELS = "channels";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_CHANNELS, LXSerializable.Utils.toArray(lx, this.mutableChannels));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    if (obj.has(KEY_RESET)) {
      this.parameters.reset();
    }

    // Add the new channels
    if (obj.has(KEY_CHANNELS)) {
      JsonArray channelsArray = obj.getAsJsonArray(KEY_CHANNELS);
      for (JsonElement channelElement : channelsArray) {
        loadChannel(channelElement.getAsJsonObject());
      }
    }

    // Load the parameters after restoring the channels!
    super.load(lx, obj);

    // Notify all the active patterns
    for (LXAbstractChannel channel : this.channels) {
      if (channel instanceof LXChannel) {
        LXPattern pattern = ((LXChannel) channel).getActivePattern();
        if (pattern != null) {
          pattern.activate(LXMixerEngine.patternFriendAccess);
        }
      }
    }
  }

  @Override
  public void dispose() {
    clear();
    LX.dispose(this.masterBus);
    super.dispose();
  }

  /**
   * Removes all channels and clears the master bus
   */
  public void clear() {
    for (int i = this.mutableChannels.size() - 1; i >= 0; --i) {
      removeChannel(this.mutableChannels.get(i));
    }
    this.masterBus.clear();
  }

  public void loadChannel(JsonObject channelObj) {
    loadChannel(channelObj, -1);
  }

  public void loadChannel(JsonObject channelObj, int index) {
    String channelClass = channelObj.get(KEY_CLASS).getAsString();
    boolean isGroup = channelObj.has(LXChannel.KEY_IS_GROUP);
    LXAbstractChannel channel;
    if (isGroup || channelClass.equals("heronarts.lx.mixer.LXGroup")
        // NOTE(mcslee): horrible backwards-compatibility hack, remove at some point
        || channelClass.equals("heronarts.lx.LXGroup")) {
      channel = addGroup(index);
    } else {
      channel = addChannel(index);
    }
    channel.load(this.lx, channelObj);
  }
}
