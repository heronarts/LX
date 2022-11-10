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

package heronarts.lx.clip;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.parameter.TriggerParameter;

public class LXClipEngine extends LXComponent implements LXOscComponent {

  public class FocusedClipParameter extends MutableParameter {

    private LXClip clip = null;

    private FocusedClipParameter() {
      super("Focused Clip");
      setDescription("Parameter which indicate the globally focused clip");
    }

    public FocusedClipParameter setClip(LXClip clip) {
      if (this.clip != clip) {
        this.clip = clip;
        bang();
      }
      return this;
    }

    public LXClip getClip() {
      return this.clip;
    }
  };

  public static final int MIN_SCENES = 5;
  public static final int MAX_SCENES = 128;

  private final TriggerParameter[] scenes = new TriggerParameter[MAX_SCENES];

  public final FocusedClipParameter focusedClip = new FocusedClipParameter();

  public final BooleanParameter clipViewExpanded =
    new BooleanParameter("Clip View", false)
    .setDescription("Whether the clip grid view is expanded");

  public final DiscreteParameter clipViewGridOffset =
    new DiscreteParameter("Clip View Grid Offset", 0, 1)
    .setDescription("Offset of the clip grid view");

  public final DiscreteParameter numScenes =
    new DiscreteParameter("Num Scenes", MIN_SCENES, MAX_SCENES + 1)
    .setDescription("Number of active scenes");

  /**
   * Amount of time taken in seconds to transition into a new snapshot view
   */
  public final BoundedParameter snapshotTransitionTimeSecs =
    new BoundedParameter("Snapshot Transition Time", 5, .1, 180)
    .setDescription("Sets the duration of interpolated transitions between clip snapshots")
    .setUnits(LXParameter.Units.SECONDS);

  public final BooleanParameter snapshotTransitionEnabled =
    new BooleanParameter("Snapshot Transitions", false)
    .setDescription("When enabled, transitions between clip snapshots use interpolation");

  public LXClipEngine(LX lx) {
    super(lx);
    addParameter("focusedClip", this.focusedClip);
    addParameter("numScenes", this.numScenes);
    addParameter("snapshotTransitionEnabled", this.snapshotTransitionEnabled);
    addParameter("snapshotTransitionTimeSecs", this.snapshotTransitionTimeSecs);
    addParameter("clipViewGridOffset", this.clipViewGridOffset);
    addParameter("clipViewExpanded", this.clipViewExpanded);

    // Scenes
    for (int i = 0; i < this.scenes.length; ++i) {
      final int sceneIndex = i;
      this.scenes[i] =
        new TriggerParameter("Scene-" + (i+1), () -> { launchScene(sceneIndex); })
        .setDescription("Launches scene " + (i+1));
      addParameter("scene-" + (i+1), this.scenes[i]);
    }

  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (this.numScenes == p) {
      this.clipViewGridOffset.setRange(this.numScenes.getValuei() - MIN_SCENES + 1);
    }
  }

  public LXClip getFocusedClip() {
    return this.focusedClip.getClip();
  }

  public LXClipEngine setFocusedClip(LXClip clip) {
    this.focusedClip.setClip(clip);
    return this;
  }

  /**
   * Get the boolean parameter that launches a scene
   *
   * @param index Index of scene
   * @return Scene at index
   */
  public TriggerParameter getScene(int index) {
    if (index < 0) {
      throw new IllegalArgumentException("Cannot request scene less than 0: " + index);
    }
    return this.scenes[index];
  }

  /**
   * Launches the scene at given index
   *
   * @param index Scene index
   * @return this
   */
  public LXClipEngine launchScene(int index) {
    LXClip clip;
    for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
      clip = channel.getClip(index);
      if (clip != null) {
        clip.trigger();
      }
    }
    clip = this.lx.engine.mixer.masterBus.getClip(index);
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
  public LXClipEngine stopClips() {
    for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
      for (LXClip clip : channel.clips) {
        if (clip != null) {
          clip.stop();
        }
      }
    }
    for (LXClip clip : this.lx.engine.mixer.masterBus.clips) {
      if (clip != null) {
        clip.stop();
      }
    }
    return this;
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(LXComponent.KEY_RESET)) {
      this.clipViewGridOffset.reset();
      this.numScenes.reset();
    }
  }

}
