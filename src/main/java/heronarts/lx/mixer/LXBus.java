/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
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

import heronarts.lx.LX;
import heronarts.lx.LXModelComponent;
import heronarts.lx.LXModulatorComponent;
import heronarts.lx.LXPresetComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.clip.LXClip;
import heronarts.lx.clip.LXClipEngine;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.modulation.LXModulationContainer;
import heronarts.lx.modulation.LXModulationEngine;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.osc.LXOscEngine;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.QuantizedTriggerParameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Abstract representation of a channel, which could be a normal channel with patterns
 * or the master channel.
 */
public abstract class LXBus extends LXModelComponent implements LXPresetComponent, LXOscComponent, LXModulationContainer, LXEffect.Container {

  /**
   * Listener interface for objects which want to be notified when the internal
   * channel state is modified.
   */
  public interface Listener {
    public default void effectAdded(LXBus channel, LXEffect effect) {}
    public default void effectRemoved(LXBus channel, LXEffect effect) {}
    public default void effectMoved(LXBus channel, LXEffect effect) {}
  }

  public interface ClipListener {
    public void clipAdded(LXBus bus, LXClip clip);
    public void clipRemoved(LXBus bus, LXClip clip);
  }

  public class Profiler extends LXModulatorComponent.Profiler {
    public long effectNanos;

    @Override
    public long renderNanos() {
      return super.renderNanos() + this.effectNanos;
    }
  }

  @Override
  protected LXModulatorComponent.Profiler constructProfiler() {
    return new Profiler();
  }

  public final LXModulationEngine modulation;

  /**
   * Level fader for this bus
   */
  public final CompoundParameter fader =
    new CompoundParameter("Fader", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Sets the alpha level of the output of this channel");

  /**
   * Arms the channel for clip recording.
   */
  public final BooleanParameter arm =
    new BooleanParameter("Arm")
    .setDescription("Arms the channel for clip recording");

  /**
   * Whether channel is selected in the UI
   */
  public final BooleanParameter selected =
    new BooleanParameter("Selected")
    .setDescription("Whether the channel is selected");

  public final QuantizedTriggerParameter stopClips =
    new QuantizedTriggerParameter(lx, "Stop Clips", this::stopClips)
    .setDescription("Stops all clips running on the bus");

  public final BooleanParameter controlsExpandedCue =
    new BooleanParameter("Expanded Cue", true)
    .setDescription("Whether the control elements for this channel are expanded in cue view");

  public final BooleanParameter controlsExpandedAux =
    new BooleanParameter("Expanded Aux", true)
    .setDescription("Whether the control elements for this channel are expanded in aux view");

  public final BooleanParameter modulationExpanded =
    new BooleanParameter("Modulation Expanded", false)
    .setDescription("Whether the device modulation section is expanded");

  protected final List<LXEffect> mutableEffects = new ArrayList<LXEffect>();
  public final List<LXEffect> effects = Collections.unmodifiableList(mutableEffects);

  private final List<LXClip> mutableClips = new ArrayList<LXClip>();
  public final List<LXClip> clips = Collections.unmodifiableList(this.mutableClips);

  private final List<Listener> listeners = new ArrayList<Listener>();
  private final List<ClipListener> clipListeners = new ArrayList<ClipListener>();

  LXBus(LX lx) {
    this(lx, null);
  }

  LXBus(LX lx, String label) {
    super(lx, label);
    addChild("modulation", this.modulation = new LXModulationEngine(lx));
    addArray("effect", this.effects);
    addParameter("fader", this.fader);
    addArray("clip", this.clips);
    addParameter("arm", this.arm);
    addParameter("selected", this.selected);
    addParameter("stopClips", this.stopClips);

    // NOTE(mcslee): weird one here, the LXMasterBus is constructed within the LXEngine
    // constructor, and lx.engine.tempo won't resolve at that point, because lx.engine
    // is still awaiting assignment. Ugly mess. That one will get handled by Tempo.initialize()
    // but all other LXBus instances can just assign it here.
    if (!(this instanceof LXMasterBus)) {
      this.stopClips.setQuantization(lx.engine.tempo.launchQuantization);
    }

    addInternalParameter("controlsExpandedCue", this.controlsExpandedCue);
    addInternalParameter("controlsExpandedAux", this.controlsExpandedAux);
    addInternalParameter("modulationExpanded", this.modulationExpanded);
  }

  public abstract int getIndex();

  protected void setMixer(LXMixerEngine mixer) {
    setParent(mixer);
  }

  public final void addListener(Listener listener) {
    Objects.requireNonNull(listener, "May not add null LXBus.Listener");
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("May not add duplicate LXBus.Listener: " + listener);
    }
    this.listeners.add(listener);
  }

  public final void removeListener(Listener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("May not remove non-registered Bus.Listener: " + listener);
    }
    this.listeners.remove(listener);
  }

  public LXBus addClipListener(ClipListener listener) {
    Objects.requireNonNull(listener, "May not add null LXBus.ClipListener");
    if (this.clipListeners.contains(listener)) {
      throw new IllegalStateException("May not remove add duplicate LXBus.ClipListener: " + listener);
    }
    this.clipListeners.add(listener);
    return this;
  }

  public LXBus removeClipListener(ClipListener listener) {
    if (!this.clipListeners.contains(listener)) {
      throw new IllegalStateException("May not remove non-registered LXBus.ClipListener: " + listener);
    }
    this.clipListeners.remove(listener);
    return this;
  }

  public static final String PATH_EFFECT = "effect";

  @Override
  public boolean handleOscMessage(OscMessage message, String[] parts, int index) {
    String path = parts[index];
    if (path.equals(PATH_EFFECT)) {
      String effectId = parts[index+1];
      if (effectId.matches("\\d+")) {
        return this.effects.get(Integer.parseInt(effectId) - 1).handleOscMessage(message, parts, index+2);
      }
      for (LXEffect effect : this.effects) {
        if (effect.getOscLabel().equals(effectId)) {
          return effect.handleOscMessage(message, parts, index+2);
        }
      }
      LXOscEngine.error("Channel " + getLabel() + " does not have effect at path: " + effectId + " (" + message + ")");
      return false;
    }
    return super.handleOscMessage(message, parts, index);

  }

  /**
   * Get the modulation engine associated with this bus
   */
  @Override
  public LXModulationEngine getModulationEngine() {
    return this.modulation;
  }

  @Override
  public BooleanParameter getModulationExpanded() {
    return this.modulationExpanded;
  }

  /**
   * Returns the group that this channel belongs to
   *
   * @return Group that this channel belongs to, or null
   */
  public LXGroup getGroup() {
    return null;
  }

  /**
   * Returns true if this is a group channel
   *
   * @return True if this is a group channel
   */
  public boolean isGroup() {
    return this instanceof LXGroup;
  }

  /**
   * Returns true if this is an empty group with no channels
   *
   * @return true if this is a group with no subchannels
   */
  public boolean isEmptyGroup() {
    return isGroup() && (((LXGroup) this).channels.size() == 0);
  }

  /**
   * Returns true if this is a basic channel
   *
   * @return True if this is a basic channel
   */
  public boolean isChannel() {
    return this instanceof LXChannel;
  }

  /**
   * Returns true if this channel belongs to a group
   *
   * @return True if this channel is part of a group
   */
  public boolean isInGroup() {
    return getGroup() != null;
  }

  @Override
  public final LXBus addEffect(LXEffect effect, int index) {
    if (index > this.mutableEffects.size()) {
      throw new IllegalArgumentException("Illegal effect index: " + index);
    }
    if (index < 0) {
      index = this.mutableEffects.size();
    }
    this.mutableEffects.add(index, effect);
    effect.setBus(this);
    _reindexEffects();
    for (Listener listener : this.listeners) {
      listener.effectAdded(this, effect);
    }
    return this;
  }

  @Override
  public final LXBus removeEffect(LXEffect effect) {
    int index = this.mutableEffects.indexOf(effect);
    if (index >= 0) {
      effect.setIndex(-1);
      this.mutableEffects.remove(index);
      while (index < this.mutableEffects.size()) {
        this.mutableEffects.get(index).setIndex(index);
        ++index;
      }
      for (Listener listener : this.listeners) {
        listener.effectRemoved(this, effect);
      }
      LX.dispose(effect);
    }
    return this;
  }

  private void _reindexEffects() {
    int i = 0;
    for (LXEffect e : this.mutableEffects) {
      e.setIndex(i++);
    }
  }

  @Override
  public LXBus moveEffect(LXEffect effect, int index) {
    if (index < 0 || index >= this.mutableEffects.size()) {
      throw new IllegalArgumentException("Cannot move effect to invalid index: " + index);
    }
    if (!this.mutableEffects.contains(effect)) {
      throw new IllegalStateException("Cannot move effect that is not on channel: " + this + " " + effect);
    }
    this.mutableEffects.remove(effect);
    this.mutableEffects.add(index, effect);
    _reindexEffects();
    for (Listener listener : this.listeners) {
      listener.effectMoved(this, effect);
    }
    return this;
  }

  @Override
  public final List<LXEffect> getEffects() {
    return this.effects;
  }

  public LXClip getClip(int index) {
    if (index >= lx.engine.clips.numScenes.getValuei()) {
      return null;
    }
    if (index < this.clips.size()) {
      return this.clips.get(index);
    }
    return null;
  }

  public LXClip addClip() {
    return addClip(this.mutableClips.size());
  }

  public LXClip addClip(int index) {
    return addClip(index, false);
  }

  public LXClip addClip(int index, boolean enableSnapshot) {
    return addClip(null, index, enableSnapshot);
  }

  public LXClip addClip(JsonObject clipObj, int index) {
    return addClip(clipObj, index, false);
  }

  private LXClip addClip(JsonObject clipObj, int index, boolean enableSnapshot) {
    if (index >= LXClipEngine.MAX_SCENES) {
      throw new IllegalArgumentException("Cannot add clip at index >= " + LXClipEngine.MAX_SCENES);
    }
    if (getClip(index) != null) {
      throw new IllegalStateException("Cannot add clip at index " + index + " which already holds a clip: " + this);
    }
    while (this.mutableClips.size() <= index) {
      this.mutableClips.add(null);
    }
    LXClip clip = constructClip(index);
    if (clipObj != null) {
      clip.load(this.lx, clipObj);
    } else {
      clip.snapshotEnabled.setValue(enableSnapshot);
      clip.label.setValue(getClipLabel() + "-" + (index+1));
    }
    this.mutableClips.set(index, clip);
    for (ClipListener listener : this.clipListeners) {
      listener.clipAdded(this, clip);
    }
    return clip;
  }

  protected String getClipLabel() {
    return "Clip";
  }

  /**
   * Stops all clips, subject to launch quantization
   *
   * @return this
   */
  public LXBus stopClips() {
    return stopClips(true);
  }

  /**
   * Stop all clips, optionally observing launch quantization
   *
   * @param quantized Whether to observe launch quantization
   * @return
   */
  public LXBus stopClips(boolean quantized) {
    for (LXClip clip : this.clips) {
      if (clip != null) {
        if (quantized) {
          clip.stop.trigger();
        } else {
          clip.stop();
        }
      }
    }
    return this;
  }

  protected abstract LXClip constructClip(int index);

  public void removeClip(LXClip clip) {
    int index = this.mutableClips.indexOf(clip);
    if (index < 0) {
      throw new IllegalArgumentException("Clip is not owned by channel: " + clip + " " + this);
    }
    removeClip(index);
  }

  public void removeClip(int index) {
    LXClip clip = getClip(index);
    if (clip != null) {
      this.mutableClips.set(index, null);
      if (this.lx.engine.clips.getFocusedClip() == clip) {
        this.lx.engine.clips.setFocusedClip(null);
      }
      for (ClipListener listener : this.clipListeners) {
        listener.clipRemoved(this, clip);
      }
      LX.dispose(clip);
    }
  }

  @Override
  public void loop(double deltaMs) {
    loop(deltaMs, true);
  }

  protected void loop(double deltaMs, boolean runComponents) {
    long loopStart = System.nanoTime();

    // Run the active clip...
    // TODO(mcslee): keep tabs of which is active rather than looping?
    for (LXClip clip : this.clips) {
      if (clip != null) {
        clip.loop(deltaMs);
      }
    }

    // Run modulators and components
    if (runComponents) {
      this.modulation.loop(deltaMs);
      super.loop(deltaMs);
    }

    this.profiler.loopNanos = System.nanoTime() - loopStart;
  }

  protected void disposeClips() {
    for (LXClip clip : this.mutableClips) {
      if (clip != null) {
        LX.dispose(clip);
      }
    }
    this.mutableClips.clear();
  }

  @Override
  public void dispose() {
    disposeClips();
    for (LXEffect effect : this.mutableEffects) {
      LX.dispose(effect);
    }
    this.mutableEffects.clear();
    super.dispose();
    this.listeners.forEach(listener -> LX.warning("Stranded LXBus.Listener: " + listener));
    this.clipListeners.forEach(listener -> LX.warning("Stranded LXBus.ClipListener: " + listener));
    this.listeners.clear();
    this.clipListeners.clear();
  }

  @Override
  public Class<?> getPresetClass() {
    // groups and the master bus can be interchangeable
    return LXBus.class;
  }

  @Override
  public void postProcessPreset(LX lx, JsonObject obj) {
    LXSerializable.Utils.stripParameter(obj, this.fader);
    LXSerializable.Utils.stripParameter(obj, this.arm);
    LXSerializable.Utils.stripParameter(obj, this.selected);
  }

  public void clear() {
    // Remove clips
    for (LXClip clip : this.clips) {
      if (clip != null) {
        removeClip(clip);
      }
    }
    // Remove effects
    for (int i = this.mutableEffects.size() - 1; i >= 0; --i) {
      removeEffect(this.mutableEffects.get(i));
    }
  }

  private static final String KEY_EFFECTS = "effects";
  private static final String KEY_CLIPS = "clips";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);;
    obj.add(KEY_EFFECTS, LXSerializable.Utils.toArray(lx, this.mutableEffects));
    JsonArray clipsArr = new JsonArray();
    for (LXClip clip : this.clips) {
      if (clip != null) {
        clipsArr.add(LXSerializable.Utils.toObject(lx, clip));
      }
    }
    obj.add(KEY_CLIPS, clipsArr);
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    clear();

    // Add the effects
    if (obj.has(KEY_EFFECTS)) {
      for (JsonElement effectElement : obj.getAsJsonArray(KEY_EFFECTS)) {
        loadEffect(this.lx, (JsonObject) effectElement, -1);
      }
    }

    // Add the new clips
    if (obj.has(KEY_CLIPS)) {
      JsonArray clipsArr = obj.get(KEY_CLIPS).getAsJsonArray();
      for (JsonElement clipElem : clipsArr) {
        JsonObject clipObj = clipElem.getAsJsonObject();
        int clipIndex = clipObj.get(LXClip.KEY_INDEX).getAsInt();
        LXClip clip = addClip(clipIndex);
        clip.load(lx, clipObj);
      }
    }

    super.load(lx, obj);
  }

}
