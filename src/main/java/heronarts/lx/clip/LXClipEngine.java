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

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.midi.surface.MixerSurface;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

public class LXClipEngine extends LXComponent implements LXOscComponent {

  public enum GridMode {
    PATTERNS("Patterns"),
    CLIPS("Clips");

    public final String label;

    private GridMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

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

  public static final int MIN_SCENES = 8;
  public static final int MAX_SCENES = 128;

  private final TriggerParameter[] scenes = new TriggerParameter[MAX_SCENES];

  private final TriggerParameter[] patternScenes = new TriggerParameter[MAX_SCENES];

  public final FocusedClipParameter focusedClip = new FocusedClipParameter();

  public final BooleanParameter gridViewExpanded =
    new BooleanParameter("Grid View", false)
    .setDescription("Whether the clip grid view is expanded");

  public final DiscreteParameter gridViewOffset =
    new DiscreteParameter("Grid View Offset", 0, 1)
    .setDescription("Offset of the clip grid view");

  public final EnumParameter<GridMode> gridMode =
    new EnumParameter<GridMode>("Grid Mode", GridMode.PATTERNS)
    .setDescription("Whether the grid activates patterns or clips");

  public final DiscreteParameter gridPatternOffset =
    new DiscreteParameter("Grid Pattern Offset", 0, 1)
    .setDescription("Offset of the pattern grid view");

  public final BooleanParameter clipInspectorExpanded =
    new BooleanParameter("Clip Inspector", false)
    .setDescription("Whether the clip inspector is expanded");

  public final DiscreteParameter numScenes =
    new DiscreteParameter("Num Scenes", MIN_SCENES, MAX_SCENES + 1)
    .setDescription("Number of active scenes");

  public final DiscreteParameter numPatterns =
    new DiscreteParameter("Num Patterns", MIN_SCENES, 4097)
    .setDescription("Number of active patterns");

  public final TriggerParameter stopClips =
    new TriggerParameter("Stop Clips", this::stopClips)
    .setDescription("Stops all clips running in the whole project");

  public final TriggerParameter triggerPatternCycle =
    new TriggerParameter("Trigger Pattern Cycle", this::launchPatternCycle)
    .setDescription("Triggers a pattern cycle on every eligble channel");

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

  /**
   * A semaphore used to keep count of how many remote control surfaces may be
   * controlling this component. This may be used by UI implementations to indicate
   * to the user that this component is under remote control.
   */
  public final MutableParameter controlSurfaceSemaphore = (MutableParameter)
    new MutableParameter("Control-Surfaces", 0)
    .setDescription("How many control surfaces are controlling this component");

  // Must use a thread-safe set here because it's also accessed from the UI thread!
  private final CopyOnWriteArraySet<MixerSurface> controlSurfaces =
    new CopyOnWriteArraySet<MixerSurface>();

  public LXClipEngine(LX lx) {
    super(lx);
    addParameter("focusedClip", this.focusedClip);
    addParameter("numScenes", this.numScenes);
    addParameter("snapshotTransitionEnabled", this.snapshotTransitionEnabled);
    addParameter("snapshotTransitionTimeSecs", this.snapshotTransitionTimeSecs);
    addParameter("stopClips", this.stopClips);
    addParameter("triggerPatternCycle", this.triggerPatternCycle);
    addParameter("gridMode", this.gridMode);
    addParameter("gridViewOffset", this.gridViewOffset);
    addParameter("gridPatternOffset", this.gridPatternOffset);
    addParameter("gridViewExpanded", this.gridViewExpanded);
    addParameter("clipInspectorExpanded", this.clipInspectorExpanded);

    // Scenes
    for (int i = 0; i < this.scenes.length; ++i) {
      final int sceneIndex = i;
      this.scenes[i] =
        new TriggerParameter("Scene-" + (i+1), () -> { launchScene(sceneIndex); })
        .setDescription("Launches scene " + (i+1));
      addParameter("scene-" + (i+1), this.scenes[i]);

      this.patternScenes[i] =
        new TriggerParameter("Pattern-" + (i+1), () -> { launchPatternScene(sceneIndex); })
        .setDescription("Triggers all patterns at index " + (i+1));
      addParameter("pattern-" + (i+1), this.patternScenes[i]);
    }
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (this.numScenes == p) {
      this.gridViewOffset.setRange(this.numScenes.getValuei() - MIN_SCENES + 1);
    }
  }

  public DiscreteParameter getGridOffsetParameter() {
    switch (this.gridMode.getEnum()) {
    case CLIPS:
      return this.gridViewOffset;
    default:
    case PATTERNS:
      return this.gridPatternOffset;
    }
  }

  public DiscreteParameter getGridSizeParameter() {
    switch (this.gridMode.getEnum()) {
    case CLIPS:
      return this.numScenes;
    default:
    case PATTERNS:
      return this.numPatterns;
    }
  }

  public int getGridOffset() {
    return getGridOffsetParameter().getValuei();
  }

  public int getGridSize() {
    return getGridSizeParameter().getValuei();
  }

  public void updatePatternGridSize() {
    int max = 0;
    for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
      if (channel instanceof LXChannel) {
        max = LXUtils.max(max, ((LXChannel) channel).patterns.size());
      }
    }
    this.numPatterns.setValue(LXUtils.max(MIN_SCENES, max));
    this.gridPatternOffset.setRange(LXUtils.max(1, max));
  }

  public LXComponent addControlSurface(MixerSurface surface) {
    if (this.controlSurfaces.contains(surface)) {
      throw new IllegalStateException("Cannot add same control surface to device twice: " + surface);
    }
    this.controlSurfaces.add(surface);
    this.controlSurfaceSemaphore.increment();
    return this;
  }

  public LXComponent removeControlSurface(MixerSurface surface) {
    if (!this.controlSurfaces.contains(surface)) {
      throw new IllegalStateException("Cannot remove control surface that is not added: " + surface);
    }
    this.controlSurfaces.remove(surface);
    this.controlSurfaceSemaphore.decrement();
    return this;
  }

  public Set<MixerSurface> getControlSurfaces() {
    return this.controlSurfaces;
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
   * Get the boolean parameter that launches a pattern scene
   *
   * @param index Index of pattern scene
   * @return Pattern scene at index
   */
  public TriggerParameter getPatternScene(int index) {
    if (index < 0) {
      throw new IllegalArgumentException("Cannot request scene less than 0: " + index);
    }
    return this.patternScenes[index];
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
   * Launches all patterns at the given index
   *
   * @param index Pattern index
   * @return this
   */
  public LXClipEngine launchPatternScene(int index) {
    for (LXAbstractChannel channel : lx.engine.mixer.channels) {
      if (channel instanceof LXChannel) {
        LXChannel c = (LXChannel) channel;
        if (index < c.patterns.size()) {
          c.goPatternIndex(index);
        }
      }
    }
    return this;
  }

  /**
   * Cycle the patterns on every eligible channel
   *
   * @return this
   */
  public LXClipEngine launchPatternCycle() {
    for (LXAbstractChannel channel : lx.engine.mixer.channels) {
      if (channel instanceof LXChannel) {
        LXChannel c = (LXChannel) channel;
        if (c.compositeMode.getEnum() == LXChannel.CompositeMode.PLAYLIST) {
          c.triggerPatternCycle.trigger();
        }
      }
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

  private static void legacyNumScenes(JsonObject obj) {
    // Legacy support for update from min 5 -> 8 scenes
    if (LXSerializable.Utils.hasParameter(obj, "numScenes")) {
      int numScenes = LXSerializable.Utils.getParameter(obj, "numScenes").getAsInt();
      if (numScenes < MIN_SCENES) {
        obj.get(LXComponent.KEY_PARAMETERS).getAsJsonObject().addProperty("numScenes", MIN_SCENES);
      }
    }
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    legacyNumScenes(obj);
    super.load(lx, obj);
    if (obj.has(LXComponent.KEY_RESET)) {
      this.gridViewOffset.reset();
      this.numScenes.reset();
    }
  }

}
