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

/**
 * Encapsulation of all the LX channel blending and mixer
 */
public class LXMixerEngine extends LXComponent implements LXOscComponent {

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


  final ModelBuffer backgroundBlack;
  final ModelBuffer backgroundTransparent;
  private final ModelBuffer blendBufferLeft;
  private final ModelBuffer blendBufferRight;

  private static final int MAX_SCENES = 5;

  private final BooleanParameter[] scenes = new BooleanParameter[MAX_SCENES];

  public final BooleanParameter viewCondensed =
    new BooleanParameter("Condensed", false)
    .setDescription("Whether the mixer view should be condensed");


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

    // Scenes
    for (int i = 0; i < this.scenes.length; ++i) {
      this.scenes[i] = new BooleanParameter("Scene-" + (i+1)).setMode(BooleanParameter.Mode.MOMENTARY);
      addParameter("scene-" + (i+1), this.scenes[i]);
    }

    // LX top level model listener
    lx.addListener(new LX.Listener() {
      @Override
      public void modelChanged(LX lx, LXModel model) {
        for (LXBus bus : channels) {
          bus.setModel(model);
        }
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
    addParameter("cueA", this.cueA);
    addParameter("cueB", this.cueB);
    addParameter("viewCondensed", this.viewCondensed);

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
    } else if (this.focusedChannel == p) {
      LXClip clip = this.lx.engine.clips.getFocusedClip();
      if (clip != null && clip.bus != getFocusedChannel()) {
        this.lx.engine.clips.setFocusedClip(null);
      }
    } else {
      for (int i = 0; i < this.scenes.length; ++i) {
        if (this.scenes[i] == p) {
          if (this.scenes[i].isOn()) {
            launchScene(i);
            this.scenes[i].setValue(false);
          }
        }
      }
    }
  }


  /**
   * Get the boolean parameter that launches a scene
   *
   * @param index Index of scene
   * @return Scene at index
   */
  public BooleanParameter getScene(int index) {
    return this.scenes[index];
  }

  /**
   * Launches the scene at given index
   *
   * @param index Scene index
   * @return this
   */
  public LXMixerEngine launchScene(int index) {
    LXClip clip;
    for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
      clip = channel.getClip(index);
      if (clip != null) {
        clip.trigger();
      }
    }
    clip = this.masterBus.getClip(index);
    if (clip != null) {
      clip.trigger();
    }
    return this;
  }

  /**
   * Stops all running clips
   *
   * @return this
   */
  public LXMixerEngine stopClips() {
    for (LXAbstractChannel channel : this.channels) {
      for (LXClip clip : channel.clips) {
        if (clip != null) {
          clip.stop();
        }
      }
    }
    for (LXClip clip : this.masterBus.clips) {
      if (clip != null) {
        clip.stop();
      }
    }
    return this;
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

  protected LXBlend[] instantiateChannelBlends() {
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
        blend.dispose();
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
    LXGroup selectedGroup = null;
    int minIndex = -1, maxIndex = -1;
    int selectIndex = destination.getIndex();

    for (LXAbstractChannel bus : this.channels) {
      if (bus.selected.isOn()) {
        selectedGroup = bus.getGroup();
        if (minIndex == -1) {
          minIndex = bus.getIndex();
        }
        maxIndex = bus.getIndex();
      }
    }

    if (selectIndex < minIndex) {
      maxIndex = minIndex;
      minIndex = selectIndex;
    } else {
      minIndex = maxIndex;
      maxIndex = selectIndex;
    }

    for (LXAbstractChannel bus : this.channels) {
      int busIndex = bus.getIndex();
      boolean selected = (bus.getGroup() == selectedGroup) && (busIndex >= minIndex) && (busIndex <= maxIndex);
      bus.selected.setValue(selected);
    }

    this.masterBus.selected.setValue(false);

    return this;
  }

  public LXChannel addChannel() {
    return addChannel(-1);
  }

  public LXChannel addChannel(int index) {
    return addChannel(index, new LXPattern[0]);
  }

  public LXChannel addChannel(LXPattern[] patterns) {
    return addChannel(-1, patterns);
  }

  public LXChannel addChannel(int index, LXPattern[] patterns) {
    if (index > this.mutableChannels.size()) {
      throw new IllegalArgumentException("Invalid channel index: " + index);
    }
    if (index < 0) {
      index = this.mutableChannels.size();
    }
    LXChannel channel = new LXChannel(this.lx, index, patterns);
    _addChannel(channel, index);

    // This new channel is focused now!
    if (this.focusedChannel.getValuei() == index) {
      this.focusedChannel.bang();
    } else {
      this.focusedChannel.setValue(index);
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
    for (Listener listener : this.listeners) {
      listener.channelAdded(this, channel);
    }
    _reindexChannels();
  }

  private void _reindexChannels() {
    int i = 0;
    for (LXAbstractChannel channelBus : this.mutableChannels) {
      channelBus.setIndex(i++);
    }
  }

  public void removeSelectedChannels() {
    List<LXAbstractChannel> toRemove = new ArrayList<LXAbstractChannel>();
    for (LXAbstractChannel channel : this.mutableChannels) {
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

    boolean notified = false;
    if (this.focusedChannel.getValuei() > this.mutableChannels.size()) {
      notified = true;
      this.focusedChannel.decrement();
    }
    this.focusedChannel.setRange(this.mutableChannels.size() + 1);
    if (!notified) {
      this.focusedChannel.bang();
    }
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

    channel.dispose();
  }

  public void moveChannel(LXAbstractChannel channel, int delta) {
    if (delta != 1 && delta != -1) {
      throw new IllegalArgumentException("moveChannel() may only be called with delta of -1 or 1");
    }
    LXBus focused = getFocusedChannel();

    int index = channel.getIndex() + delta;
    if (index < 0 || index >= this.mutableChannels.size()) {
      return;
    }

    LXGroup group = channel.getGroup();
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

    for (Listener listener : this.listeners) {
      listener.channelMoved(this, channel);
    }
  }

  private class BlendStack {

    private int[] destination;
    private int[] output;
    private boolean hasOutput;

    void initialize(int[] destination, int[] output) {
      this.destination = destination;
      this.output = output;
      this.hasOutput = false;
    }

    void blend(LXBlend blend, BlendStack that, double alpha) {
      blend(blend, that.destination, alpha);
    }

    void blend(LXBlend blend, int[] src, double alpha) {
      blend.blend(this.destination, src, alpha, this.output);
      this.destination = this.output;
      this.hasOutput = true;
    }

    void transition(LXBlend blend, int[] src, double lerp) {
      blend.lerp(this.destination, src, lerp, this.output);
      this.destination = this.output;
      this.hasOutput = true;
    }

    void copyFrom(BlendStack that) {
      System.arraycopy(that.destination, 0, this.output, 0, that.destination.length);
      this.destination = this.output;
      this.hasOutput = true;
    }

    void flatten() {
      if (!this.hasOutput) {
        System.arraycopy(this.destination, 0, this.output, 0, this.destination.length);
        this.destination = this.output;
        this.hasOutput = true;
      }
    }

  }

  private final BlendStack blendStackMain = new BlendStack();
  private final BlendStack blendStackCue = new BlendStack();
  private final BlendStack blendStackLeft = new BlendStack();
  private final BlendStack blendStackRight = new BlendStack();

  public void loop(LXEngine.Frame render, double deltaMs) {
    long channelStart = System.nanoTime();

    // Initialize blend stacks
    this.blendStackMain.initialize(this.backgroundBlack.getArray(), render.getMain());
    this.blendStackCue.initialize(this.backgroundBlack.getArray(), render.getCue());
    this.blendStackLeft.initialize(this.backgroundBlack.getArray(), this.blendBufferLeft.getArray());
    this.blendStackRight.initialize(this.backgroundBlack.getArray(), this.blendBufferRight.getArray());

    double crossfadeValue = this.crossfader.getValue();

    boolean leftBusActive = crossfadeValue < 1.;
    boolean rightBusActive = crossfadeValue > 0.;
    boolean cueBusActive = false;

    boolean isChannelMultithreaded = this.lx.engine.isChannelMultithreaded.isOn();

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

    // Step 3: blend the channel buffers down
    boolean blendLeft = leftBusActive || this.cueA.isOn();
    boolean blendRight = rightBusActive || this.cueB.isOn();
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
            blendStack.blend(channel.blendMode.getObject(), channel.getColors(), alpha);
          }
        }
      }

      // Blend into the cue buffer, always a direct add blend for any type of channel
      if (channel.cueActive.isOn()) {
        cueBusActive = true;
        this.blendStackCue.blend(this.addBlend, channel.getColors(), 1);
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

    // Step 4: now we have three output buses that need mixing... the left/right crossfade
    // groups plus the main buffer. We figure out which of them are active and blend appropriately
    // Note that the A+B crossfade groups are additively mixed AFTER the main buffer
    boolean leftContent = leftBusActive && leftExists;
    boolean rightContent = rightBusActive && rightExists;

    if (leftContent && rightContent) {
      // There are left and right channels assigned!
      LXBlend blend = this.crossfaderBlendMode.getObject();
      blendStackLeft.transition(blend, blendStackRight.destination, crossfadeValue);
      // Add the crossfaded groups to the main buffer
      this.blendStackMain.blend(this.addBlend, blendStackLeft, 1.);
    } else if (leftContent) {
      // Add the left group to the main buffer
      this.blendStackMain.blend(this.addBlend, this.blendStackLeft, Math.min(1, 2. * (1-crossfadeValue)));
    } else if (rightContent) {
      // Add the right group to the main buffer
      this.blendStackMain.blend(this.addBlend, this.blendStackRight, Math.min(1, 2. * crossfadeValue));
    }

    // Check for edge case of all channels being off, don't leave stale data in blend buffer!
    this.blendStackMain.flatten();

    // Time to apply master FX to the main blended output
    long effectStart = System.nanoTime();
    for (LXEffect effect : this.masterBus.getEffects()) {
      effect.setBuffer(render);
      effect.loop(deltaMs);
    }
    ((LXBus.Profiler) this.masterBus.profiler).effectNanos = System.nanoTime() - effectStart;

    // Mark the cue active state of the buffer
    render.setCueOn(cueBusActive);
  }

  private static final String KEY_CHANNELS = "channels";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_CHANNELS, LXSerializable.Utils.toArray(lx, this.mutableChannels));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    // Add the new channels
    if (obj.has(KEY_CHANNELS)) {
      JsonArray channelsArray = obj.getAsJsonArray(KEY_CHANNELS);
      for (JsonElement channelElement : channelsArray) {
        loadChannel(channelElement.getAsJsonObject());
      }
    } else {
      addChannel().fader.setValue(1);
    }

    // Load the parameters after restoring the channels!
    super.load(lx, obj);

    // Notify all the active patterns
    for (LXAbstractChannel channel : this.channels) {
      if (channel instanceof LXChannel) {
        LXPattern pattern = ((LXChannel) channel).getActivePattern();
        if (pattern != null) {
          pattern.onActive();
        }
      }
    }
  }

  @Override
  public void dispose() {
    List<LXAbstractChannel> toRemove = new ArrayList<LXAbstractChannel>(this.channels);
    Collections.reverse(toRemove);
    for (LXAbstractChannel channel : toRemove) {
      removeChannel(channel);
    }
    this.masterBus.dispose();
    super.dispose();
  }

  public void clear() {
    // Remove all channels
    for (int i = this.mutableChannels.size() - 1; i >= 0; --i) {
      removeChannel(this.mutableChannels.get(i));
    }
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
