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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.Tempo;
import heronarts.lx.midi.surface.MixerSurface;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.parameter.ObjectParameter;
import heronarts.lx.parameter.QuantizedTriggerParameter;
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

  public static class Grid extends LXComponent implements LXOscComponent {

    public interface Listener {
      public void onGridChanged(Grid grid);
    }

    public enum Mode {
      ADAPTIVE("A"),
      FIXED("F");

      public final String label;

      private Mode(String label) {
        this.label = label;
      }

      @Override
      public String toString() {
        return this.label;
      }
    }

    public enum Spacing {
      NARROWEST("Narrowest", 64),
      NARROW("Narrow", 32),
      MEDIUM("Medium", 16),
      WIDE("Wide", 8),
      WIDEST("Widest", 4);

      public final String label;
      public final int divisions;

      private Spacing(String label, int divisions) {
        this.label = label;
        this.divisions = divisions;
      }

      @Override
      public String toString() {
        return this.label;
      }
    }

    public enum TimeDivision {
      QUARTER_SECOND("¼ second", 1000/4f),
      HALF_SECOND("½ second", 1000/2f),
      SECOND("1 second", 1000),
      TWO_SECONDS("2 seconds", 2000),
      FOUR_SECONDS("4 seconds", 4000);

      public final String label;
      public final float millis;

      private TimeDivision(String label, float millis) {
        this.label = label;
        this.millis = millis;
      }

      @Override
      public String toString() {
        return this.label;
      }
    }

    public final BooleanParameter snap =
      new BooleanParameter("Snap", true)
      .setDescription("Toggles grid snapping on or off");

    public final EnumParameter<Mode> mode =
      new EnumParameter<Mode>("Mode", Mode.ADAPTIVE)
      .setDescription("Whether grid lines are fixed or adaptive to the visible region");

    public final EnumParameter<Spacing> adaptiveSpacing =
      new EnumParameter<>("Spacing", Spacing.MEDIUM)
      .setDescription("Relative spacing of grid lines in Adaptive mode")
      .setWrappable(false);

    public final EnumParameter<TimeDivision> fixedSpacingAbsolute =
      new EnumParameter<TimeDivision>("Fixed Grid Spacing", TimeDivision.SECOND)
      .setDescription("Grid line spacing in Fixed mode when time scale is absolute")
      .setWrappable(false);

    public final ObjectParameter<Tempo.Quantization> fixedSpacingTempo =
      new ObjectParameter<Tempo.Quantization>("Fixed Grid Spacing", new Tempo.Quantization[] {
        Tempo.Division.EIGHT.toQuantization("8 Bars"),
        Tempo.Division.FOUR.toQuantization("4 Bars"),
        Tempo.Division.DOUBLE.toQuantization("2 Bars"),
        Tempo.Division.WHOLE.toQuantization("1 Bar"),
        Tempo.Division.HALF,
        Tempo.Division.QUARTER,
        Tempo.Division.EIGHTH,
        Tempo.Division.SIXTEENTH
      })
      .setDescription("Grid line spacing in Fixed mode when time scale is tempo")
      .setWrappable(false);

    private Grid(LX lx) {
      super(lx);
      addParameter("snap", this.snap);
      addParameter("mode", this.mode);
      addParameter("adaptiveSpacing", this.adaptiveSpacing);
      addParameter("fixedSpacingAbsolute", this.fixedSpacingAbsolute);
      addParameter("fixedSpacingTempo", this.fixedSpacingTempo);
    }

    @Override
    public void onParameterChanged(LXParameter p) {
      super.onParameterChanged(p);
      for (Listener listener : this.listeners) {
        listener.onGridChanged(this);
      }
    }

    private final List<Listener> listeners = new ArrayList<Listener>();

    public final void addListener(Listener listener) {
      Objects.requireNonNull(listener, "May not add null LXClipEngine.Grid.Listener");
      if (this.listeners.contains(listener)) {
        throw new IllegalStateException("May not add duplicate LXClipEngine.Grid.Listener: " + listener);
      }
      this.listeners.add(listener);
    }

    public final void removeListener(Listener listener) {
      if (!this.listeners.contains(listener)) {
        throw new IllegalStateException("May not remove non-registered LXClipEngine.Grid.Listener: " + listener);
      }
      this.listeners.remove(listener);
    }
  }

  public static final int MIN_SCENES = 8;
  public static final int MAX_SCENES = 128;

  private final QuantizedTriggerParameter[] scenes = new QuantizedTriggerParameter[MAX_SCENES];

  private final QuantizedTriggerParameter[] patternScenes = new QuantizedTriggerParameter[MAX_SCENES];

  public final FocusedClipParameter focusedClip = new FocusedClipParameter();

  public final Grid grid;

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

  public final QuantizedTriggerParameter stopClips =
    new QuantizedTriggerParameter(lx, "Stop Clips", this::_stopClipsQuantized)
    .onSchedule(this::_stopClipsScheduled)
    .setDescription("Stops all clips running in the whole project");

  public final QuantizedTriggerParameter triggerPatternCycle =
    new QuantizedTriggerParameter(lx, "Trigger Pattern Cycle", this::triggerPatternCycle)
    .setDescription("Triggers a pattern cycle on every eligible channel");

  // NB(mcslee): chain parameters in case there are modulation mappings from the trigger cycle parameter!
  public final QuantizedTriggerParameter launchPatternCycle =
    new QuantizedTriggerParameter(lx, "Launch Pattern Cycle", this.triggerPatternCycle::trigger)
    .setDescription("Triggers a pattern cycle on every eligible channel");

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

  public final EnumParameter<Cursor.TimeBase> timeBaseDefault =
    new EnumParameter<Cursor.TimeBase>("Time-Base Default", Cursor.TimeBase.TEMPO)
    .setDescription("Which time-base new clips use by default");

  public final BooleanParameter clipSnapshotDefault =
    new BooleanParameter("Clip Snapshot Default", false)
    .setDescription("Whether new clips have a snapshot by default");

  /**
   * A semaphore used to keep count of how many remote control surfaces may be
   * controlling this component. This may be used by UI implementations to indicate
   * to the user that this component is under remote control.
   */
  public final MutableParameter controlSurfaceSemaphore =
    new MutableParameter("Control-Surfaces", 0)
    .setDescription("How many control surfaces are controlling this component");

  // Must use a thread-safe set here because it's also accessed from the UI thread!
  private final CopyOnWriteArraySet<MixerSurface> controlSurfaces =
    new CopyOnWriteArraySet<MixerSurface>();

  private final LXParameterListener clipSceneListener = this::_launchClipSceneUnique;
  private final LXParameterListener patternSceneListener = this::_launchPatternSceneUnique;

  public LXClipEngine(LX lx) {
    super(lx, "Clips");
    addParameter("focusedClip", this.focusedClip);
    addParameter("numScenes", this.numScenes);
    addParameter("snapshotTransitionEnabled", this.snapshotTransitionEnabled);
    addParameter("snapshotTransitionTimeSecs", this.snapshotTransitionTimeSecs);
    addParameter("stopClips", this.stopClips);
    addParameter("launchPatternCycle", this.launchPatternCycle);
    addParameter("triggerPatternCycle", this.triggerPatternCycle);
    addParameter("timeBaseDefault", this.timeBaseDefault);
    addParameter("clipSnapshotDefault", this.clipSnapshotDefault);
    addParameter("gridMode", this.gridMode);
    addParameter("gridViewOffset", this.gridViewOffset);
    addParameter("gridPatternOffset", this.gridPatternOffset);
    addParameter("gridViewExpanded", this.gridViewExpanded);
    addParameter("clipInspectorExpanded", this.clipInspectorExpanded);

    addChild("grid", this.grid = new Grid(lx));

    this.launchPatternCycle.addListener(this.patternSceneListener);
    this.stopClips.addListener(this.clipSceneListener);

    // Scenes
    for (int i = 0; i < this.scenes.length; ++i) {
      final int sceneIndex = i;
      this.scenes[i] =
        new QuantizedTriggerParameter(lx, "Launch Scene-" + (i+1), (quantized) -> _launchClipSceneQuantized(sceneIndex, quantized))
        .onSchedule(() -> _launchClipSceneScheduled(sceneIndex))
        .setDescription("Launches scene " + (i+1));
      this.scenes[i].addListener(this.clipSceneListener);
      addParameter("scene-" + (i+1), this.scenes[i]);

      this.patternScenes[i] =
        new QuantizedTriggerParameter(lx, "Launch Pattern-" + (i+1), (quantized) -> _launchPatternSceneQuantized(sceneIndex, quantized))
        .onSchedule(() -> _launchPatternSceneScheduled(sceneIndex))
        .setDescription("Launches all patterns at index " + (i+1));
      this.patternScenes[i].addListener(this.patternSceneListener);
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
  public QuantizedTriggerParameter getScene(int index) {
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
  public QuantizedTriggerParameter getPatternScene(int index) {
    if (index < 0) {
      throw new IllegalArgumentException("Cannot request scene less than 0: " + index);
    }
    return this.patternScenes[index];
  }

  /**
   * Launches the scene at given index, subject to launch quantization
   *
   * @param index Scene index
   * @return this
   */
  public LXClipEngine launchScene(int index) {
    this.scenes[index].trigger();
    return this;
  }

  // Ensure that two clip scenes can't be pending at once
  private void _launchClipSceneUnique(LXParameter p) {
    if (p.getValue() > 0) {
      for (QuantizedTriggerParameter scene : this.scenes) {
        if (p != scene) {
          scene.cancel();
        }
      }
    }
    if (p != this.stopClips) {
      this.stopClips.cancel();
    }
  }

  private void _launchClipSceneScheduled(int index) {
    boolean didSomething = false;
    for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
      LXClip clip = channel.getClip(index);
      if (clip != null) {
        clip.launch.trigger();
        didSomething = true;
      }
    }
    LXClip clip = this.lx.engine.mixer.masterBus.getClip(index);
    if (clip != null) {
      clip.launch.trigger();
      didSomething = true;
    }
    if (!didSomething) {
      this.scenes[index].cancel();
    }
  }

  private void _launchClipSceneQuantized(int index, boolean quantized) {
    if (!quantized) {
      triggerScene(index);
    }
  }

  /**
   * Triggers the scene at given index immediately
   *
   * @param index Scene index
   * @return this
   */
  public LXClipEngine triggerScene(int index) {
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
   * Launches the pattern scene at given index, subject to launch quantization
   *
   * @param index Pattern scene index
   * @return this
   */
  public LXClipEngine launchPatternScene(int index) {
    this.patternScenes[index].trigger();
    return this;
  }

  // Ensure that two pattern scenes can't be pending at once
  private void _launchPatternSceneUnique(LXParameter p) {
    if (p.getValue() > 0) {
      for (QuantizedTriggerParameter pattern : this.patternScenes) {
        if (p != pattern) {
          pattern.cancel();
        }
      }
      if (p != this.launchPatternCycle) {
        this.launchPatternCycle.cancel();
      }
    }
  }

  private void _launchPatternSceneScheduled(int index) {
    boolean didSomething = false;
    for (LXAbstractChannel bus : lx.engine.mixer.channels) {
      if (bus instanceof LXChannel channel) {
        if ((index < channel.patterns.size()) && channel.isPlaylist()) {
          channel.getPattern(index).launch.trigger();
          didSomething = true;
        }
      }
    }
    if (!didSomething) {
      this.patternScenes[index].cancel();
    }
  }

  private void _launchPatternSceneQuantized(int index, boolean quantized) {
    if (!quantized) {
      triggerPatternScene(index);
    }
  }

  /**
   * Triggers all patterns at the given index
   *
   * @param index Pattern index
   * @return this
   */
  public LXClipEngine triggerPatternScene(int index) {
    for (LXAbstractChannel bus : lx.engine.mixer.channels) {
      if (bus instanceof LXChannel channel) {
        if (index < channel.patterns.size()) {
          channel.goPatternIndex(index);
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
  public LXClipEngine triggerPatternCycle() {
    for (LXAbstractChannel bus : this.lx.engine.mixer.channels) {
      if (bus instanceof LXChannel channel) {
        if (channel.isPlaylist()) {
          channel.triggerPatternCycle.trigger();
        }
      }
    }
    return this;
  }

  private void _stopClipsScheduled() {
    boolean hasPendingStop = false;
    for (LXBus bus : this.lx.engine.mixer.channels) {
      bus.stopClips.trigger();
      if (bus.stopClips.pending.isOn()) {
        hasPendingStop = true;
      }
    }
    this.lx.engine.mixer.masterBus.stopClips.trigger();
    if (this.lx.engine.mixer.masterBus.stopClips.pending.isOn()) {
      hasPendingStop = true;
    }
    if (!hasPendingStop) {
      this.stopClips.cancel();
    }
  }

  private void _stopClipsQuantized(boolean quantized) {
    if (!quantized) {
      stopClips();
    }
  }

  /**
   * Stops all running clips
   *
   * @return this
   */
  public LXClipEngine stopClips() {
    for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
      channel.stopClips();
    }
    this.lx.engine.mixer.masterBus.stopClips();
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

  @Override
  public void dispose() {
    this.launchPatternCycle.removeListener(this.patternSceneListener);
    this.stopClips.removeListener(this.clipSceneListener);
    for (QuantizedTriggerParameter scene : this.scenes) {
      scene.removeListener(this.clipSceneListener);
    }
    for (QuantizedTriggerParameter pattern : this.patternScenes) {
      pattern.removeListener(this.patternSceneListener);
    }
    super.dispose();
  }

}
