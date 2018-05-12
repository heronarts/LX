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

package heronarts.lx;

import heronarts.lx.clip.LXClip;
import heronarts.lx.model.LXModel;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Abstract representation of a channel, which could be a normal channel with patterns
 * or the master channel.
 */
public abstract class LXBus extends LXModelComponent implements LXOscComponent {

  /**
   * Listener interface for objects which want to be notified when the internal
   * channel state is modified.
   */
  public interface Listener {
    public void effectAdded(LXBus channel, LXEffect effect);
    public void effectRemoved(LXBus channel, LXEffect effect);
    public void effectMoved(LXBus channel, LXEffect effect);
  }

  public interface ClipListener {
    public void clipAdded(LXBus bus, LXClip clip);
    public void clipRemoved(LXBus bus, LXClip clip);
  }

  public class Timer extends LXModulatorComponent.Timer {
    public long effectNanos;
  }

  @Override
  protected LXModulatorComponent.Timer constructTimer() {
    return new Timer();
  }

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

  protected final LX lx;

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
    this.lx = lx;
    addParameter("arm", this.arm);
    addParameter("selected", this.selected);
  }

  public abstract int getIndex();

  @Override
  protected void onModelChanged(LXModel model) {
    for (LXEffect effect : this.mutableEffects) {
      effect.setModel(model);
    }
  }

  public final void addListener(Listener listener) {
    this.listeners.add(listener);
  }

  public final void removeListener(Listener listener) {
    this.listeners.remove(listener);
  }

  public LXBus addClipListener(ClipListener listener) {
    this.clipListeners.add(listener);
    return this;
  }

  public LXBus removeClipListener(ClipListener listener) {
    this.clipListeners.remove(listener);
    return this;
  }

  public LXGroup getGroup() {
    return null;
  }

  public final LXBus addEffect(LXEffect effect) {
    this.mutableEffects.add(effect);
    effect.setBus(this);
    effect.setIndex(this.mutableEffects.size() - 1);
    for (Listener listener : this.listeners) {
      listener.effectAdded(this, effect);
    }
    return this;
  }

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
      effect.dispose();
    }
    return this;
  }

  public LXBus moveEffect(LXEffect effect, int index) {
    this.mutableEffects.remove(effect);
    this.mutableEffects.add(index, effect);
    int i = 0;
    for (LXEffect e : this.mutableEffects) {
      e.setIndex(i++);
    }
    for (Listener listener : this.listeners) {
      listener.effectMoved(this, effect);
    }
    return this;
  }

  public final List<LXEffect> getEffects() {
    return this.effects;
  }

  public LXEffect getEffect(int i) {
    return this.effects.get(i);
  }

  public LXEffect getEffect(String label) {
    for (LXEffect effect : this.effects) {
      if (effect.getLabel().equals(label)) {
        return effect;
      }
    }
    return null;
  }

  public LXClip getClip(int index) {
    return getClip(index, false);
  }

  public LXClip getClip(int index, boolean create) {
    if (index < this.clips.size()) {
      return this.clips.get(index);
    }
    if (create) {
      return addClip(index);
    }
    return null;
  }

  public LXClip addClip() {
    return addClip(this.mutableClips.size());
  }

  public LXClip addClip(int index) {
    while (this.mutableClips.size() <= index) {
      this.mutableClips.add(null);
    }
    LXClip clip = constructClip(index);
    clip.label.setValue("Clip-" + (index+1));
    this.mutableClips.set(index, clip);
    for (ClipListener listener : this.clipListeners) {
      listener.clipAdded(this, clip);
    }
    return clip;
  }

  public LXBus stopClips() {
    for (LXClip clip : this.clips) {
      if (clip != null) {
        clip.stop();
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
    LXClip clip = this.mutableClips.get(index);
    this.mutableClips.set(index, null);
    for (ClipListener listener : this.clipListeners) {
      listener.clipRemoved(this, clip);
    }
    clip.dispose();
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
      super.loop(deltaMs);
    }

    this.timer.loopNanos = System.nanoTime() - loopStart;
  }

  @Override
  public void dispose() {
    for (LXEffect effect : this.mutableEffects) {
      effect.dispose();
    }
    this.mutableEffects.clear();
    super.dispose();
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
    // Add the effects
    JsonArray effectsArray = obj.getAsJsonArray(KEY_EFFECTS);
    for (JsonElement effectElement : effectsArray) {
      JsonObject effectObj = (JsonObject) effectElement;
      LXEffect effect = this.lx.instantiateEffect(effectObj.get("class").getAsString());
      effect.load(lx, effectObj);
      addEffect(effect);
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
