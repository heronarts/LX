/**
 * Copyright 2017- Mark C. Slee, Heron Arts LLC
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.LXLayeredComponent;
import heronarts.lx.LXPath;
import heronarts.lx.LXRunnableComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.Tempo;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.AggregateParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.parameter.QuantizedTriggerParameter;
import heronarts.lx.snapshot.LXClipSnapshot;

public abstract class LXClip extends LXRunnableComponent implements LXOscComponent, LXComponent.Renamable, LXBus.Listener {

  public interface Listener {
    public default void cursorChanged(LXClip clip, Cursor cursor) {}
    public default void clipLaneMoved(LXClip clip, LXClipLane<?> lane, int index) {}
    public default void parameterLaneAdded(LXClip clip, ParameterClipLane lane) {}
    public default void parameterLaneRemoved(LXClip clip, ParameterClipLane lane) {}
    public default void patternLaneAdded(LXClip clip, PatternClipLane lane) {}
    public default void patternLaneRemoved(LXClip clip, PatternClipLane lane) {}
  }

  final List<Listener> listeners = new ArrayList<Listener>();

  /**
   * Current playback/recording cursor for the clip
   */
  public final Cursor cursor = new Cursor();

  /**
   * Cursor holding position next launch operation, if a custom
   * launch position was set
   */
  public final Cursor launchFromCursor = new Cursor();

  // Internal helpers
  private final Cursor nextCursor = new Cursor();

  private final List<CursorParameter> cursorParameters = new ArrayList<>();

  public class CursorParameter extends AggregateParameter {

    public final LXClip clip;

    public final Cursor cursor = new Cursor();

    public final MutableParameter millis =
      new MutableParameter("Millis")
      .setMinimum(0)
      .setUnits(LXParameter.Units.MILLISECONDS);

    public final DiscreteParameter beatCount =
      new DiscreteParameter("Beat Count", 0, Integer.MAX_VALUE);

    public final BoundedParameter beatBasis =
      new BoundedParameter("Beat Basis", 0, 1);

    public CursorParameter(String label) {
      super(label);

      this.clip = LXClip.this;

      // NOTE: critical that beatBasis comes last, highest specificity so
      // that on load() operations the update happens when that's set
      addSubparameter("millis", this.millis);
      addSubparameter("beatCount", this.beatCount);
      addSubparameter("beatBasis", this.beatBasis);

      cursorParameters.add(this);
    }

    private boolean inSetCursor = false;

    private CursorParameter set(CursorParameter cursor) {
      return set(cursor.cursor);
    }

    private CursorParameter set(Cursor cursor) {
      this.inSetCursor = true;
      if (!this.cursor.equals(cursor)) {
        this.millis.setValue(cursor.getMillis());
        this.beatCount.setValue(cursor.getBeatCount());
        this.beatBasis.setValue(cursor.getBeatBasis());
        this.cursor.set(cursor);
        bang();
      }
      this.inSetCursor = false;
      return this;
    }

    @Override
    public CursorParameter reset() {
      set(Cursor.ZERO);
      return this;
    }

    @Override
    protected double onUpdateValue(double value) {
      if (!this.inSetCursor) {
        throw new IllegalStateException("Cannot update CursorParameter with direct setValue() call");
      }
      return value;
    }

    @Override
    protected void updateSubparameters(double value) {
      // Ignored, we hold these values ourselves
    }

    @Override
    protected void onSubparameterUpdate(LXParameter p) {
      if (!this.inSetCursor) {
        if (p == this.millis) {
          set(constructAbsoluteCursor(this.millis.getValue()));
        } else if (p == this.beatCount || p == this.beatBasis) {
          set(constructTempoCursor(this.beatCount.getValuei(), this.beatBasis.getValue()));
        }
      }
    }

    @Override
    public CursorParameter setDescription(String description) {
      super.setDescription(description);
      return this;
    }
  }

  public enum ClipView {
    AUTOMATION,
    SNAPSHOT;
  }

  public final EnumParameter<Cursor.TimeBase> timeBase =
    new EnumParameter<Cursor.TimeBase>("Time Base", Cursor.TimeBase.ABSOLUTE)
    .setDescription("Whether clip timing is absolute or tempo-based");

  public final CursorParameter length =
    new CursorParameter("Length")
    .setDescription("The length of the clip");

  public final BooleanParameter loop =
    new BooleanParameter("Loop")
    .setDescription("Whether to loop the clip");

  public final CursorParameter loopStart =
    new CursorParameter("Loop Start")
    .setDescription("Where the clip will loop to when loop is enabled");

  public final CursorParameter loopEnd =
    new CursorParameter("Loop End")
    .setDescription("End of the loop in milliseconds");

  public final CursorParameter loopLength =
    new CursorParameter("Loop Length")
    .setDescription("Length of the loop in milliseconds");

  public final CursorParameter playStart =
    new CursorParameter("Play Start")
    .setDescription("Where the loop will start playing when it is launched");

  public final CursorParameter playEnd =
    new CursorParameter("Play End")
    .setDescription("Where an unlooped clip will stop playing");

  /**
   * Launches the clip, including both snapshot recall and automation playback
   */
  public final QuantizedTriggerParameter launch =
    new QuantizedTriggerParameter.Launch(lx, "Launch", this::_launch)
    .onSchedule(this::_launchAutomationScheduled)
    .setDescription("Launch this clip");

  /**
   * Launches the clip's automation playback, if there is any
   */
  public final QuantizedTriggerParameter launchAutomation =
    new QuantizedTriggerParameter.Launch(lx, "Launch", this::_launchAutomation)
    .onSchedule(this::_launchAutomationScheduled);

  /**
   * Stop playback of the clip
   */
  public final QuantizedTriggerParameter stop =
    new QuantizedTriggerParameter.Launch(lx, "Stop", this::_launchStop)
    .setDescription("Stop this clip");

  protected final List<LXClipLane<?>> mutableLanes = new ArrayList<>();
  public final List<LXClipLane<?>> lanes = Collections.unmodifiableList(this.mutableLanes);

  public final BooleanParameter snapshotEnabled =
    new BooleanParameter("Snapshot", false)
    .setDescription("Whether snapshot recall is enabled for this clip");

  public final BooleanParameter snapshotTransitionEnabled =
    new BooleanParameter("Transition", true)
    .setDescription("Whether snapshot transition is enabled for this clip");

  public final BooleanParameter automationEnabled =
    new BooleanParameter("Automation", false)
    .setDescription("Whether automation playback is enabled for this clip");

  public final BooleanParameter customSnapshotTransition =
    new BooleanParameter("Custom Snapshot Transition")
    .setDescription("Whether to use custom snapshot transition settings for this clip");

  public final BoundedParameter referenceBpm =
    new BoundedParameter("Reference BPM", Tempo.DEFAULT_BPM, Tempo.MIN_BPM, Tempo.MAX_BPM)
    .setOscMode(BoundedParameter.OscMode.ABSOLUTE)
    .setDescription("Reference BPM of the clip");

  public final EnumParameter<ClipView> clipView =
    new EnumParameter<>("Clip View", ClipView.AUTOMATION);

  public final MutableParameter zoom = new MutableParameter("Zoom", 1);

  public final Cursor.Operator CursorOp() {
    return this.timeBase.getEnum().operator;
  }

  public final LXBus bus;

  public final LXClipSnapshot snapshot;

  private final Cursor startTransportReference = new Cursor();
  private final Cursor startCursorReference = new Cursor();

  // Whether a timeline has been created. If a clip has never been run in recording mode,
  // that won't exist yet. The flag is set the first time a clip is recorded to, or if
  // a clip is loaded that had a non-zero length.
  private boolean hasTimeline = false;

  private int index;
  private final boolean busListener;

  private final LXParameterListener parameterRecorder = this::recordParameterChange;

  private void recordParameterChange(LXParameter p) {
    if (isRecording()) {
      LXListenableNormalizedParameter parameter = (LXListenableNormalizedParameter) p;
      ParameterClipLane lane = getParameterLane(parameter, true);
      if (!lane.isInPlayback() && lane.shouldRecordParameterChange(parameter)) {
        lane.recordParameterEvent(new ParameterClipEvent(lane));
      }
    }
  }

  public LXClip(LX lx, LXBus bus, int index) {
    this(lx, bus, index, true);
  }

  protected LXClip(LX lx, LXBus bus, int index, boolean registerListener) {
    super(lx);
    this.label.setDescription("The name of this clip");
    this.bus = bus;
    this.index = index;
    this.busListener = registerListener;
    setParent(this.bus);

    // Use reference BPM value at time of clip creation, this is used to preserve accurate
    // conversions between the two different TimeBase options (e.g. Cursor objects in this clip's
    // lanes hold millis values, based upon this reference tempo. The global tempo may be changed
    // later, storing this value will allow us to correctly interpret the millis values.
    this.referenceBpm.setValue(lx.engine.tempo.bpm.getValue());

    // NOTE: crucial that referenceBpm is loaded *before*
    // all the CursorParameters, which will need
    addParameter("referenceBpm", this.referenceBpm);

    // Time-base defaults to the project setting
    this.timeBase.setValue(lx.engine.clips.timeBaseDefault.getEnum());
    addParameter("timeBase", this.timeBase);
    addParameter("launch", this.launch);
    addParameter("stop", this.stop);
    addParameter("loop", this.loop);

    // Cursors
    addParameter("length", this.length);
    addParameter("loopStart", this.loopStart);
    addParameter("loopLength", this.loopLength);
    addParameter("playStart", this.playStart);
    addParameter("playEnd", this.playEnd);

    // Behavior configuration
    addParameter("snapshotEnabled", this.snapshotEnabled);
    addParameter("snapshotTransitionEnabled", this.snapshotTransitionEnabled);
    addParameter("automationEnabled", this.automationEnabled);
    addParameter("customSnapshotTransition", this.customSnapshotTransition);

    addInternalParameter("clipView", this.clipView);
    addInternalParameter("launchAutomation", this.launchAutomation);
    addInternalParameter("zoom", this.zoom);
    addChild("snapshot", this.snapshot = new LXClipSnapshot(lx, this));
    addArray("lane", this.lanes);

    for (LXEffect effect : bus.effects) {
      registerComponent(effect);
    }

    // This class is not always registered as a listener... in the case of LXChannelClip,
    // that parent class will take care of registering as a listener and this will avoid
    // having duplicated double-listeners
    if (registerListener) {
      bus.addListener(this);
    }
    bus.arm.addListener(this);
  }

  public Cursor.TimeBase getTimeBase() {
    return this.timeBase.getEnum();
  }

  public boolean isPending() {
    return this.launch.pending.isOn() || this.launchAutomation.pending.isOn();
  }

  /**
   * Launches the clip, subject to global launch quantization, which will also
   * trigger recall of a snapshot if enabled
   *
   * @return this
   */
  public LXClip launch() {
    this.launchFromCursor.set(this.playStart.cursor);
    this.launch.trigger();
    return this;
  }

  /**
   * Launches clip automation playback, subject to global launch quantization
   *
   * @return this
   */
  public LXClip launchAutomation() {
    return launchAutomationFrom(this.playStart.cursor);
  }

  /**
   * Launches the clip from a specified start position,
   * subject to global launch quantization.
   *
   * @param cursor Position to launch from
   * @return this
   */
  public LXClip launchAutomationFrom(Cursor cursor) {
    this.launchFromCursor.set(cursor.bound(this));
    this.launchAutomation.trigger();
    return this;
  }

  /**
   * Launches the clip from the current cursor position
   *
   * @return this
   */
  public LXClip launchAutomationFromCursor() {
    return launchAutomationFrom(this.cursor);
  }

  /**
   * Play clip from cursor position without quantization delay.
   * Does nothing if the clip is already running or if it has
   * not been initialized.
   *
   * @param cursor
   * @return this
   */
  public LXClip playFrom(Cursor cursor) {
    if (!isRunning() && this.hasTimeline) {
      _playFrom(cursor);
    }
    return this;
  }

  private void _playFrom(Cursor cursor) {
    if (this.hasTimeline) {
      this.launchFromCursor.set(CursorOp().bound(cursor, this));
      trigger();
    }
  }

  /**
   * Play clip from cursor position without quantization delay.
   * Does nothing unless clip is not running.
   *
   * @return this
   */
  public LXClip playFromCursor() {
    return playFrom(this.cursor);
  }

  /**
   * Trigger the clip, whether from control surface or UI
   *
   * @param focus Whether to focus the clip
   * @return this
   */
  public LXClip triggerAction(boolean focus) {
    return triggerAction(focus, true);
  }

  /**
   * Trigger the clip, whether from control surface or UI
   *
   * @param focus Whether to focus the clip
   * @param fromGrid Whether this is a quantized grid launch
   * @return this
   */
  public LXClip triggerAction(boolean focus, boolean fromGrid) {
    if (isRecording()) {
      stop();
    } else {
      if (!fromGrid) {
        if (isRunning()) {
          stop();
        } else {
          _playFrom(this.playStart.cursor);
        }
      } else if (isRunning()) {
        launchAutomation();
      } else {
        launch();
      }
      if (focus) {
        this.lx.engine.clips.focusedClip.setClip(this);
      }
    }
    return this;
  }

  @Override
  public String getPath() {
    return "clip/" + (this.index + 1);
  }

  private boolean isQuantizedLaunch = false;
  private Tempo.Division isQuantizedStop = null;

  private void _launchAutomationScheduled() {
    for (LXClip clip : this.bus.clips) {
      if ((clip != null) && (clip != this)) {
        clip.launch.cancel();
        clip.launchAutomation.cancel();
      }
    }
  }

  /**
   * Invoked when we launch from the main launch() function or grid trigger. In this case
   * we also recall snapshots.
   */
  private void _launch(boolean quantized) {
    // Grid/master launch is always from the play start position
    this.launchFromCursor.set(this.playStart.cursor.bound(this));
    _launchAutomation(quantized);
    if (!isArmed() && this.snapshotEnabled.isOn()) {
      this.snapshot.recall();
    }
  }

  /**
   * Invoked when we launch from the main launch() function or grid trigger. In this case
   * we also recall snapshots
   */
  private void _launchAutomation(boolean quantized) {
    this.isQuantizedLaunch = quantized;
    this.trigger.trigger();
  }

  private void _launchStop(boolean quantized) {
    if (isRunning()) {
      this.isQuantizedStop = (quantized && (this.timeBase.getEnum() == Cursor.TimeBase.TEMPO)) ?
        this.lx.engine.tempo.getLaunchQuantization() :
        null;
      if (this.isQuantizedStop == null) {
        stop();
      }
    }
  }

  private void setTransportReference(boolean quantize) {
    setTransportReference(constructTransportCursor(), quantize);
  }

  private void setTransportReference(Cursor transportCursor, boolean quantize) {
    // Set a reference to the global transport position when the clip started, as
    // well as the cursor position we started from. When using TimeBase.TEMPO,
    // the cursor position will be computed via difference from the global
    // tempo clock. When we are in TEMPO mode and there is global launch quantization
    // then we also snap the launch reference to the quantization point. This is crucial
    // because the global Tempo object will almost never land on exactly beatBasis == 0.
    //
    // The QuantizedTriggerParameter.Launch will fire some number of milliseconds after that
    // when the boundary has been passed. But we don't want to treat the clip as starting
    // 10 milliseconds after the start of the bar. The clip should have started in-between
    // rendering frames at exactly 1bar.0.0beats.
    //
    // The first frame of playback/recording will then naturally take care of processing
    // any events between the startTransportReference and the actual transport position
    if (quantize) {
      snapLaunchQuantization(transportCursor, true);
    }
    this.startTransportReference.set(transportCursor);
    this.startCursorReference.set(this.cursor);
  }

  private void launchTransport() {
    setCursor(this.launchFromCursor.constrain(this));
    setTransportReference(this.isQuantizedLaunch);
    this.isQuantizedLaunch = false;
  }

  @Override
  protected final void onTrigger() {
    super.onTrigger();
    if (isRunning()) {
      // This is a "restart" operation, we were already running but we've been re-triggered
      // Call launchTransport directly here because onStart() will not be invoked as per a
      // normal launch-from-stopped situation
      Cursor from = this.cursor.clone();
      launchTransport();
      if (!CursorOp().isEqual(from, this.cursor)) {
        jumpCursor(from, this.cursor);
      }
    }

  }

  @Override
  public void dispose() {
    for (LXEffect effect : bus.effects) {
      unregisterComponent(effect);
    }
    if (this.busListener) {
      this.bus.removeListener(this);
    }
    this.bus.arm.removeListener(this);
    for (int i = this.lanes.size() - 1; i >= 0; --i) {
      _removeLane(this.lanes.get(i));
    }
    LX.dispose(this.snapshot);
    super.dispose();
    this.listeners.forEach(listener -> LX.warning("Stranded LXClip.Listener: " + listener));
    this.listeners.clear();
  }

  public double getLength() {
    return this.length.getValue();
  }

  private ParameterClipLane getParameterLane(LXNormalizedParameter parameter, boolean create) {
    return getParameterLane(parameter, create, -1);
  }

  private ParameterClipLane getParameterLane(LXNormalizedParameter parameter, boolean create, int index) {
    for (LXClipLane<?> lane : this.lanes) {
      if (lane instanceof ParameterClipLane parameterLane) {
        if (parameterLane.parameter == parameter) {
          return parameterLane;
        }
      }
    }
    if (create) {
      ParameterClipLane lane = ParameterClipLane.create(this, parameter, this.parameterDefaults.get(parameter));
      if (index < 0) {
        this.mutableLanes.add(lane);
      } else {
        this.mutableLanes.add(index, lane);
      }
      for (Listener listener : this.listeners) {
        listener.parameterLaneAdded(this, lane);
      }
      return lane;
    }
    return null;
  }

  LXClip _removeLane(LXClipLane<?> lane) {
    this.mutableLanes.remove(lane);
    switch (lane) {
    case ParameterClipLane parameterLane -> this.listeners.forEach(l -> l.parameterLaneRemoved(this, parameterLane));
    case PatternClipLane patternLane -> this.listeners.forEach(l -> l.patternLaneRemoved(this, patternLane));
    default -> {}
    };
    LX.dispose(lane);
    return this;
  }

  public LXClip removeParameterLane(ParameterClipLane lane) {
    return removeClipLane(lane);
  }

  public LXClip removeClipLane(LXClipLane<?> lane) {
    if (lane instanceof MidiNoteClipLane) {
      throw new IllegalArgumentException("May not remove the MidiNoteClipLane");
    }
    if (lane instanceof PatternClipLane patternLane) {
      if (patternLane.engine.component instanceof LXChannel) {
        throw new IllegalArgumentException("May not remove master LXChannelClip PatternClipLane");
      }
    }
    return _removeLane(lane);
  }

  public LXClip moveClipLane(LXClipLane<? >lane, int index) {
    this.mutableLanes.remove(lane);
    this.mutableLanes.add(index, lane);
    for (Listener listener : this.listeners) {
      listener.clipLaneMoved(this, lane, index);
    }
    return this;
  }

  public LXClip addListener(Listener listener) {
    Objects.requireNonNull(listener, "May not add null LXClip.Listener");
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Already registered LXClip.Listener: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  public LXClip removeListener(Listener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot remove non-registered LXClip.Listener: " + listener);
    }
    this.listeners.remove(listener);
    return this;
  }

  protected void setCursor(CursorParameter timestamp) {
    setCursor(timestamp.cursor);
  }

  /**
   * Move the cursor and notify listeners
   */
  protected void setCursor(Cursor cursor) {
    if (!this.cursor.equals(cursor)) {
      this.cursor.set(cursor);
      for (Listener listener : this.listeners) {
        listener.cursorChanged(this, this.cursor);
      }
    }
  }

  public Cursor getCursor() {
    return this.cursor;
  }

  /**
   * Whether the clip is armed for recording
   */
  public boolean isArmed() {
    return this.bus.arm.isOn();
  }

  /**
   * Whether the clip is actively recording.
   */
  public boolean isRecording() {
    return isRunning() && this.bus.arm.isOn();
  }

  public boolean isOverdub() {
    return isRecording() && this.hasTimeline;
  }

  /**
   * Safely set the loop start marker to a specific value (in time units)
   *
   * @param loopStart Cursor position on the timeline
   */
  public LXClip setLoopStart(Cursor loopStart) {

    // Loop start cannot go past loop end, subject to min loop length
    loopStart = CursorOp().bound(loopStart, this.loopEnd.cursor.subtract(Cursor.MIN_LOOP));

    if (CursorOp().isAfter(loopStart, this.loopStart.cursor)) {
      // Shortening the loop, shorten length first, then update start
      this.loopLength.set(this.loopEnd.cursor.subtract(loopStart));
      this.loopStart.set(loopStart);
    } else {
      // Lengthening the loop, move start back first then update length
      Cursor newLength = this.loopEnd.cursor.subtract(loopStart);
      this.loopStart.set(loopStart);
      this.loopLength.set(newLength);
    }

    return this;
  }

  /**
   * Safely set the loop brace to a position on the timeline (in time units)
   *
   * @param loopBrace Cursor position on the timeline
   */
  public LXClip setLoopBrace(Cursor loopBrace) {
    final Cursor oldEnd = this.loopEnd.cursor.clone();
    // Loop end is defined by Length, so moving the start has the appearance of moving the brace
    // Restrict right-direction move to remaining space after the brace
    Cursor max = CursorOp().isBefore(this.loopLength.cursor, this.length.cursor) ?
      this.length.cursor.subtract(this.loopLength.cursor) :
      Cursor.ZERO; // wtf- loop length longer than clip length? can't put the loop anywhere

    loopBrace = CursorOp().bound(loopBrace, max);
    this.loopStart.set(loopBrace);

    // Check for cursor capture while playing
    captureCursorWithLoopMove(oldEnd);
    return this;
  }

  /**
   * Safely set the loop end marker to a specific value (in time units)
   *
   * @param loopEnd Cursor position on the timeline
   * @return this
   */
  public LXClip setLoopEnd(Cursor loopEnd) {
    final Cursor oldEnd = this.loopEnd.cursor.clone();

    // Bound loop end to [start,length]
    loopEnd = CursorOp().bound(loopEnd, this.loopStart.cursor, this.length.cursor);

    // Calculate new length as given loopEnd - this.loopStart, constrain it to
    // the shortest loop allowed, or the maximum space available
    this.loopLength.set(CursorOp().bound(
      loopEnd.subtract(this.loopStart.cursor),
      Cursor.MIN_LOOP,
      this.length.cursor.subtract(this.loopStart.cursor))
    );

    // Check for cursor capture while playing
    captureCursorWithLoopMove(oldEnd);
    return this;
  }

  /**
   * Set the loop length
   *
   * @param loopLength Loop length
   * @return this
   */
  public LXClip setLoopLength(Cursor loopLength) {
    return setLoopEnd(this.loopStart.cursor.add(loopLength));
  }

  /**
   * Safely set the play start marker to a specific value (in time units)
   *
   * @param playStart Cursor position on the timeline
   */
  public LXClip setPlayStart(Cursor playStart) {
    if (CursorOp().isAfter(this.playEnd.cursor, Cursor.MIN_LOOP)) {
      playStart = CursorOp().bound(playStart, this.playEnd.cursor.subtract(Cursor.MIN_LOOP));
    } else {
      playStart = Cursor.ZERO;
    }
    this.playStart.set(playStart);
    return this;
  }

  /**
   * Safely set the play end marker to a specific value (in time units)
   *
   * @param playEnd Cursor position on the timeline
   */
  public LXClip setPlayEnd(Cursor playEnd) {
    final Cursor oldEnd = this.playEnd.cursor.clone();
    playEnd = CursorOp().bound(
      playEnd,
      this.playStart.cursor.add(Cursor.MIN_LOOP),
      this.length.cursor
    );
    this.playEnd.set(playEnd);

    // If we cross the cursor going left, while we are the relevant marker, stop playback
    if (!this.loop.isOn() && isRunning() && !this.bus.arm.isOn()) {
      if (CursorOp().isBefore(this.cursor, oldEnd) && CursorOp().isAfter(this.cursor, this.playEnd.cursor)) {
        stop();
      }
    }
    return this;
  }

  /**
   * Performs a safety check when moving loop end or loop brace:
   * If playing, and cursor was before the end marker, don't let it escape.
   *
   * @param oldEnd value of the loop end marker before the move
   */
  private void captureCursorWithLoopMove(Cursor oldEnd) {
    if (this.loop.isOn() && isRunning() && !this.bus.arm.isOn()) {
      if (CursorOp().isBefore(this.cursor, oldEnd) && CursorOp().isAfter(this.cursor, this.loopEnd.cursor)) {
        // Advance cursor to previous loop end, playing everything
        playCursor(this.cursor, oldEnd, true);
        // Wrap cursor back to start of loop
        setCursor(this.loopStart);

        // NOTE(mcslee): DORF! We may fall out of hard tempo-sync here, but for
        // now this is treated as acceptable cost of doing business for manually
        // moving your loop cursors over the playhead in real-time. Future improvement
        // here might try to retain some kind of relative tempo-sync if there is
        // global launch quantization by examining how many "beats ahead" the
        // play cursor was in the old loop scheme and maintaining a comparable
        // offset
        setTransportReference(false);
      }
    }
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    // The super call will invoke onStart() + onStop()
    super.onParameterChanged(p);

    // Now check our own stuff
    if (p == this.bus.arm) {
      if (isRunning()) {
        if (this.bus.arm.isOn()) {
          if (this.hasTimeline) {
            startHotOverdub();
          } else {
            // Super rare/weird case, thing is running because a snapshot is recalling
            // and then we set bus arm to true in the midst of that happening!
            startHotFirstRecording();
          }
        } else if (this.isRecording) {
          if (this.hasTimeline) {
            stopHotOverdub();
          } else {
            stopHotFirstRecording();
          }
        }
      }
    } else if (p == this.automationEnabled) {
      if (!this.automationEnabled.isOn() && this.isRecording) {
        stop();
      }
    } else if (p == this.loopStart || p == this.loopLength) {
      // Keep loopEnd updated to always be accurately derived
      this.loopEnd.set(this.loopStart.cursor.add(this.loopLength.cursor));
    } else if (p == this.snapshotEnabled) {
      // Check load flag, when loading from JsonObj the snapshotEnabled
      // param will be restored before the snapshot gets loaded
      if (this.snapshotEnabled.isOn() && !this.inLoad) {
        this.snapshot.initialize();
      }
    }
  }

  // If recording was stopped by turning off the bus arm, we can no longer use bus.arm.isOn()
  // to know if we were running.  And so... tracking it here.
  private boolean isRecording = false;

  // If an overdub recording resulted in the length being extended (we may snap its length at
  // the end of the overdub recording if so)
  private boolean isOverdubExtension = false;

  /**
   * Start from a stopped state, e.g. this.running has transitioned false -> true
   */
  @Override
  protected final void onStart() {
    // Stop other clips on the bus
    for (LXClip clip : this.bus.clips) {
      if ((clip != null) && (clip != this)) {
        clip.stop();
      }
    }

    this.bus.onClipStart(this);

    // Kick off the transport
    launchTransport();

    // Perform any cursor initialization at this point
    for (LXClipLane<?> lane : this.lanes) {
      lane.initializeCursorPlayback(this.cursor);
    }

    // Check for recording state
    if (this.bus.arm.isOn()) {
      this.isRecording = true;
      if (this.hasTimeline) {
        startOverdub();
      } else {
        startFirstRecording();
      }
    } else {
      this.isRecording = false;
      startPlayback();
    }
  }

  /**
   * Stop from a rec/play state, fires when this.running has transitioned true -> false
   */
  @Override
  protected final void onStop() {
    super.onStop();

    // Wrap up any recording/playback state
    if (this.isRecording) {
      this.isRecording = false;
      this.bus.arm.setValue(false);
      if (!this.hasTimeline) {
        stopFirstRecording();
      } else {
        stopOverdub();
      }
    } else {
      stopPlayback();
    }

    // Clear extension flag
    this.isOverdubExtension = false;

    // Finish snapshot transition
    this.snapshot.stopTransition();

    this.bus.onClipStop(this);
  }

  private void _startRecording(boolean isOverdub) {
    this.automationEnabled.setValue(true);
    updateParameterDefaults();
    resetRecordingState();
    onStartRecording(isOverdub);
  }

  private void startFirstRecording() {
    this.cursor.reset();
    this.length.reset();
    this.loopLength.reset();
    this.loopStart.reset();
    this.playStart.reset();
    this.playEnd.reset();
    _startRecording(false);
  }

  private void startHotFirstRecording() {
    startFirstRecording();
    setTransportReference(false);
  }

  private void startOverdub() {
    _startRecording(true);
  }

  private void startHotOverdub() {
    this.isRecording = true;
    startOverdub();
  }


  private void resetRecordingState() {
    this.isOverdubExtension = false;
    this.lanes.forEach(lane -> lane.resetRecordingState());
  }

  private void startPlayback() {}

  private void setRecordingLength(Cursor length, boolean isOverdub, boolean hotStop) {
    if ((this.timeBase.getEnum() == Cursor.TimeBase.TEMPO) && this.lx.engine.tempo.hasLaunchQuantization()) {
      setQuantizedRecordingLength(length, isOverdub, hotStop);
    } else {
      this.length.set(length);
    }
  }

  private void setQuantizedRecordingLength(Cursor length, boolean isOverdub, boolean hotStop) {
    // If we stopped a recording in tempo mode with launch quanitzation, we round the clip length to
    // the nearest quantization boundary so it will loop cleanly.
    final Cursor.Operator CursorOp = CursorOp();
    final Cursor snapSize = constructTempoCursor(this.lx.engine.tempo.getLaunchQuantization());
    Cursor snap = CursorOp().snap(length.clone(), this, snapSize);

    // But, that could *shorten* the length
    if (CursorOp.isBefore(snap, length)) {
      // Check if we would be clobbering data by shortening
      for (LXClipLane<?> lane : this.lanes) {
        if (!lane.events.isEmpty()) {
          final LXClipEvent<?> lastEvent = lane.events.get(lane.events.size() - 1);
          if (CursorOp.isAfter(lastEvent.cursor, snap)) {
            // If so, force snap to the longer snap value
            snap = CursorOp().snapCeiling(length.clone(), this, constructTempoCursor(this.lx.engine.tempo.getLaunchQuantization()));
            break;
          }
        }
      }
    }
    this.length.set(snap);
    this.playEnd.set(this.length);
    if (isOverdub) {
      if (hotStop) {
        // Leave cursor where it is unless we shortened the clip
        this.cursor.bound(this);
      } else {
        this.cursor.set(this.length.cursor);
      }
    } else {
      // First recording sets loop to true
      this.loop.setValue(true);
      if (hotStop) {
        // Hot stop cursor keeps on playing from where it was, or
        // jumps back into loop if shortened
        if (CursorOp.isAfter(this.cursor, this.length.cursor)) {
          this.cursor.set(this.cursor.subtract(this.length.cursor));
          setTransportReference(false);
        }
      } else {
        // Otherwise put the cursor at the end, wherever it was
        this.cursor.set(this.length.cursor);
      }
    }
  }

  private void _stopFirstRecording(boolean hotStop) {
    this.loopStart.reset();
    this.playStart.reset();
    setRecordingLength(this.cursor, false, hotStop);
    this.loopLength.set(this.length);
    this.playEnd.set(this.length);
    this.hasTimeline = true;
    resetRecordingState();
    onStopRecording();
  }

  private void stopFirstRecording() {
    _stopFirstRecording(false);
  }

  private void stopHotFirstRecording() {
    _stopFirstRecording(true);
  }

  private void _stopOverdub(boolean hotStop) {
    if (this.isOverdubExtension) {
      setRecordingLength(this.cursor, true, hotStop);
    }
    resetRecordingState();
    onStopRecording();
  }

  private void stopOverdub() {
    _stopOverdub(false);
  }

  private void stopHotOverdub() {
    _stopOverdub(true);
    this.isRecording = false;
    // cursor advancement will continue...
  }


  private void stopPlayback() {
    onStopPlayback();
  }

  // State change notifications to subclasses

  /**
   * Subclasses may override
   *
   * @param isOverdub Whether this was overdub recording
   */
  protected void onStartRecording(boolean isOverdub) {}

  /**
   * Subclasses may override
   */
  protected void onStopRecording() {}

  /**
   * Subclasses may override
   */
  protected void onStopPlayback() {}

  private void clearLanes() {
    Iterator<LXClipLane<?>> iter = this.mutableLanes.iterator();
    while (iter.hasNext()) {
      LXClipLane<?> lane = iter.next();
      if (lane instanceof ParameterClipLane) {
        iter.remove();
        for (Listener listener : this.listeners) {
          listener.parameterLaneRemoved(this, (ParameterClipLane) lane);
        }
        LX.dispose(lane);
      } else {
        lane.clear();
      }
    }
  }

  private final Map<LXNormalizedParameter, Double> parameterDefaults = new HashMap<>();

  private void updateParameterDefaults() {
    for (LXNormalizedParameter p : this.parameterDefaults.keySet()) {
      double defaultValue = p.getBaseNormalized();
      this.parameterDefaults.put(p, defaultValue);
      ParameterClipLane lane = getParameterLane(p, false);
      if (lane != null) {
        lane.updateDefaultValue(defaultValue);
      }
    }
  }

  protected void registerParameter(LXListenableNormalizedParameter p) {
    this.parameterDefaults.put(p, p.getBaseNormalized());
    p.addListener(this.parameterRecorder);
  }

  protected void unregisterParameter(LXListenableNormalizedParameter p) {
    this.parameterDefaults.remove(p);
    p.removeListener(this.parameterRecorder);
  }

  // Be explicit storing these in a tree, we need to ensure we unregister everything
  // correctly in the case that the children or layers of a component are modified
  // between the time we register and unregister.
  private final Map<LXComponent, List<LXComponent>> registeredChildren = new HashMap<>();

  // Same for params, be explicit about what we listened to.
  private final Map<LXComponent, List<LXListenableNormalizedParameter>> registeredParameters = new HashMap<>();

  private List<LXListenableNormalizedParameter> _registeredParameters(LXComponent component) {
    List<LXListenableNormalizedParameter> list = this.registeredParameters.get(component);
    if (list == null) {
      list = new ArrayList<>();
      this.registeredParameters.put(component, list);
    }
    return list;
  }

  private List<LXComponent> _registeredChildren(LXComponent component) {
    List<LXComponent> list = this.registeredChildren.get(component);
    if (list == null) {
      list = new ArrayList<>();
      this.registeredChildren.put(component, list);
    }
    return list;
  }

  private boolean isEligibleParameter(LXComponent component, LXListenableNormalizedParameter parameter) {
    if (component instanceof LXDeviceComponent device) {
      return device.isClipAutomationControl(parameter);
    }
    return true;
  }

  protected void registerComponent(LXComponent component) {
    final List<LXListenableNormalizedParameter> registeredParameters = _registeredParameters(component);
    for (LXParameter p : component.getParameters()) {
      if (p instanceof LXListenableNormalizedParameter listenable) {
        if (isEligibleParameter(component, listenable)) {
          registeredParameters.add(listenable);
          registerParameter(listenable);
        }
      }
    }
    final List<LXComponent> registeredChildren = _registeredChildren(component);
    if (component instanceof LXLayeredComponent layered) {
      layered.getLayers().forEach(layer -> {
        registeredChildren.add(layer);
        registerComponent(layer);
      });
    }
    if (component instanceof LXDeviceComponent device) {
      device.automationChildren.values().forEach(child -> {
        registeredChildren.add(child);
        registerComponent(child);
      });
    }
  }

  public List<LXClipLane<?>> findClipLanes(LXComponent component) {
    List<LXClipLane<?>> removedLanes = null;
    for (LXClipLane<?> lane : this.mutableLanes) {
      switch (lane) {
        case ParameterClipLane parameterLane -> {
          if (parameterLane.parameter.isDescendant(component)) {
            if (removedLanes == null) {
              removedLanes = new ArrayList<>();
            }
            removedLanes.add(parameterLane);
          }
        }
        case PatternClipLane patternLane -> {
          if (patternLane.engine.component == component) {
            if (removedLanes == null) {
              removedLanes = new ArrayList<>();
            }
            removedLanes.add(patternLane);
          }
        }
        default -> {}
      }
    }
    return removedLanes;
  }

  protected void unregisterComponent(LXComponent component) {
    final List<LXListenableNormalizedParameter> registeredParameters =
      this.registeredParameters.remove(component);
    if (registeredParameters != null) {
      for (LXListenableNormalizedParameter parameter : registeredParameters) {
        unregisterParameter(parameter);
        ParameterClipLane lane = getParameterLane(parameter, false);
        if (lane != null) {
          removeParameterLane(lane);
        }
      }
    }

    final List<LXComponent> registeredChildren = this.registeredChildren.remove(component);
    if (registeredChildren != null) {
      registeredChildren.forEach(child -> unregisterComponent(child));
    }
  }

  public int getIndex() {
    return this.index;
  }

  public LXClip setIndex(int index) {
    this.index = index;
    return this;
  }

  private void jumpCursor(Cursor from, Cursor to) {
    this.lanes.forEach(lane -> lane.jumpCursor(from, to));
  }

  private void loopCursor(Cursor from, Cursor to) {
    this.lanes.forEach(lane -> lane.loopCursor(from, to));
  }

  private void playCursor(Cursor from, Cursor to, boolean inclusive) {
    this.lanes.forEach(lane -> lane.playCursor(from, to, inclusive));
  }

  private void overdubCursor(Cursor from, Cursor to, boolean inclusive) {
    this.lanes.forEach(lane -> lane.overdubCursor(from, to, inclusive));
  }

  private void computeNextCursor(double deltaMs) {
    switch (this.timeBase.getEnum()) {
    case TEMPO:
      final Cursor transportCursor = constructTransportCursor();
      if (CursorOp().isBefore(transportCursor, this.startTransportReference)) {
        // TODO(clips): need a real solution for this situation!!
        // Test this for instance with sync from Ableton Live but Ableton running
        // in a loop that periodically resets the bar position. Perhaps the Tempo
        // class needs to announce to listeners when there's an external clock
        // rewind so that we can sync back to it properly?
        // This frame is fucked, just reset the tempo references to wherever we're
        // at now and carry on...
        LX.warning("LXClip detected global transport rewind: " + transportCursor + " < " + this.startTransportReference);
        setTransportReference(transportCursor, false);
        this.nextCursor.set(this.cursor);
      } else {
        // Compute the elapsed cursor-time since the reference tempo position, this will
        // ensure that we smoothly process any global tempo-changes or slewing to external
        // clock sources that the Tempo class deals with. We must explicitly *not* compute
        // things based upon deltaMs and BPM-math here, the Tempo class is smarter than
        // that and may be incorporating skew-correction, nudging, etc.
        if (this.isQuantizedStop != null) {
          // We need to keep running up to the stop tempo position
          CursorOp().snapFloor(transportCursor, this, constructTempoCursor(this.isQuantizedStop));
        }
        final Cursor elapsed = transportCursor.subtract(this.startTransportReference);

        // Then add that delta to the reference start cursor position
        this.nextCursor.set(this.startCursorReference.add(elapsed));
      }
      break;

    default:
    case ABSOLUTE:
      // Advance the cursor from prior position by the given number of milliseconds
      this.nextCursor.set(constructAbsoluteCursor(this.cursor.getMillis() + deltaMs));
      break;
    }
  }

  @Override
  protected void run(double deltaMs) {
    // Compute the cursor position
    computeNextCursor(deltaMs);

    if (this.bus.arm.isOn()) {
      // Recording
      if (!this.hasTimeline) {
        runFirstRecording();
      } else {
        runOverdub();
      }
    } else {
      // Playback
      boolean runAutomation = false;

      // Run clip automation if enabled
      if (this.automationEnabled.isOn()) {
        runAutomation = runAutomation(false);
      }

      // NOTE(mcslee): the snapshot does not finish its interpolation if
      // someone manually stops the clip before it's finished. I *think* we can treat
      // that as expected semantics? Stopping via grid UI will kill the interpolation
      // as far as it got, which may be useful/desirable. Just keep this in mind.
      if (this.snapshotEnabled.isOn()) {
        this.snapshot.loop(deltaMs);
      }

      // If automation and snapshot are both finished, stop
      if (!runAutomation && !this.snapshot.isInTransition()) {
        stop();
      }
    }

    // Explicit quantized stop
    if (this.isQuantizedStop != null) {
      stop();
      this.isQuantizedStop = null;
    }
  }

  private void runFirstRecording() {
    // Write any queued events
    this.lanes.forEach(lane -> lane.commitRecordQueue(true));

    // Recording mode... lane and event listeners will pick up and record
    // all the events. All we need to do here is update the clip length
    this.length.set(this.nextCursor);
    this.loopStart.reset();
    this.loopLength.set(this.nextCursor);
    this.playStart.reset();
    this.playEnd.set(this.nextCursor);

    // First recording extends length continuously
    setCursor(this.nextCursor);
  }

  private void runOverdub() {
    runAutomation(true);
  }

  /**
   * Runs the clip automation
   *
   * @param isOverdub Whether in overdub recording mode
   * @return <code>true</code> if we should keep running, <code>false</code> otherwise
   */
  private boolean runAutomation(boolean isOverdub) {

    final Cursor.Operator CursorOp = CursorOp();
    boolean isLoop = this.loop.isOn();
    boolean extendOverdub = false;

    // Determine playback finish position
    Cursor endCursor = this.playEnd.cursor;
    if (isLoop) {
      endCursor = this.loopEnd.cursor;
    } else if (isOverdub) {
      endCursor = this.length.cursor;
    }

    // End markers only apply when the cursor passes over them. If playback was started
    // past them, the effective end point will be the clip length.
    if (CursorOp.isAfter(this.cursor, endCursor)) {
      endCursor = this.length.cursor;
      isLoop = false;
    }

    // Check if we're overdubbing out of a loop and extending length
    if (isOverdub && !isLoop && CursorOp.isAfter(this.nextCursor, endCursor)) {
      endCursor = this.nextCursor;
      extendOverdub = true;
    }

    if (CursorOp.isBefore(this.nextCursor, endCursor)) {
      // Normal play frame, no looping. Execute this content, move
      // the cursor, and continue
      if (isOverdub) {
        overdubCursor(this.cursor, this.nextCursor, false);
      } else {
        playCursor(this.cursor, this.nextCursor, false);
      }
      setCursor(this.nextCursor);
      return true;
    }

    // We have reached the end, play everything up to the end *inclusive*
    if (isOverdub) {
      overdubCursor(this.cursor, endCursor, true);
      if (extendOverdub) {
        this.isOverdubExtension = true;
        this.length.set(endCursor);
        this.playEnd.set(endCursor);
      }
    } else {
      playCursor(this.cursor, endCursor, true);
    }

    // If the clip has no length, or is not in a loop, then we're done at the end
    if (CursorOp.isZero(this.length.cursor) || !isLoop) {
      setCursor(endCursor);
      return false;
    }

    // We are in a loop!
    if (CursorOp().isZero(this.loopLength.cursor)) {
      // Clip is non-zero length but loop is zero length, wtf? Should have been prevented
      // by loop length limits... bail out
      LX.warning("LXClip has loop set with zero length, stopping");
      setCursor(this.loopStart.cursor);
      return false;
    }

    runAutomationLoop(isOverdub);
    return true;
  }

  private void runAutomationLoop(boolean isOverdub) {

    // Wrap into new loop, play automation up to next position
    while (true) {

      // Rewind by loop length
      this.nextCursor._subtract(this.loopLength.cursor);
      loopCursor(this.loopEnd.cursor, this.loopStart.cursor);

      if (CursorOp().isBefore(this.nextCursor, this.loopEnd.cursor)) {
        // Normal expected behavior, we're back within the loop but
        // have not reached its end. Run animations from start of loop
        // up to the new position
        if (isOverdub) {
          overdubCursor(this.loopStart.cursor, this.nextCursor, false);
        } else {
          playCursor(this.loopStart.cursor, this.nextCursor, false);
        }
        break;
      }

      // Loop length is equal or smaller than frame, wtf?! Should be exceedingly
      // rare unless framerate is super low, but run through the *entire* loop,
      // inclusive and then we'll take another pass
      if (isOverdub) {
        overdubCursor(this.loopStart.cursor, this.loopEnd.cursor, true);
      } else {
        playCursor(this.loopStart.cursor, this.loopEnd.cursor, true);
      }
    }

    // Leave the cursor as far into the loop as it got
    setCursor(this.nextCursor);

    // NOTE(mcslee): we could proably just as well setTransportReference(false) in all
    // cases here, but when in launch quantization mode it feels preferable to maintain
    // the reference start tempo playback point at the strict loop start, similar to how
    // quantized launches use launchFromCursor as the reference
    Cursor loopDelta = this.nextCursor.subtract(this.loopStart.cursor);
    Cursor transport = constructTransportCursor();
    if (CursorOp().isAfter(loopDelta, transport)) {
      // WTF?!?! The transport is so tiny it can't handle subtracting a small portion of loop?? Fall
      // back to just hard-syncing transport references to the current position
      LX.warning("Transport somehow smaller than loop delta - transport:" + transport + " < loopDelta:" + loopDelta);
      setTransportReference(false);
    } else {
      this.startCursorReference.set(this.loopStart.cursor);
      this.startTransportReference.set(transport.subtract(loopDelta));
    }
  }

  @Override
  public void effectAdded(LXBus channel, LXEffect effect) {
    registerComponent(effect);
  }

  @Override
  public void effectRemoved(LXBus channel, LXEffect effect) {
    unregisterComponent(effect);
  }

  @Override
  public void effectMoved(LXBus channel, LXEffect effect) {
  }

  /**
   * Constructs a cursor from the global transport playback position,
   * with millis computed using this clip's reference BPM
   *
   * @return Cursor for given time division using clip's reference BPM
   */
  public Cursor constructTransportCursor() {
    return constructTempoCursor(this.lx.engine.tempo.beatCount(), this.lx.engine.tempo.basis());
  }

  /**
   * Constructs a cursor using a tempo-division reference, with millisecond
   * value computed using this clip's reference BPM
   *
   * @param division Tempo division
   * @return Cursor for given time division using clip's reference BPM
   */
  public Cursor constructTempoCursor(Tempo.Division division) {
    int beatCount = (int) (1. / division.multiplier);
    double beatBasis = (1. / division.multiplier) % 1.;
    return constructTempoCursor(beatCount, beatBasis);
  }

  /**
   * Constructs a cursor using tempo-based timing with the millisecond
   * value computed using the clip's reference BPM
   *
   * @param beatCount Beat count
   * @param beatBasis Beat basis
   * @return Cursor for given time using clip's reference BPM
   */
  public Cursor constructTempoCursor(int beatCount, double beatBasis) {
    return new Cursor(
      (beatCount + beatBasis) * 60000 / this.referenceBpm.getValue(),
      beatCount,
      beatBasis
    );
  }

  /**
   * Constructs a cursor using absolute-timing, with the beat fields
   * computed using the clip's reference BPM
   *
   * @param millis Absolute cursor time
   * @return Cursor for given time using clip's reference BPM
   */
  public Cursor constructAbsoluteCursor(double millis) {
    final double beatCountBasis = millis * this.referenceBpm.getValue() / 60000;
    return new Cursor(
      millis,
      (int) beatCountBasis,
      beatCountBasis % 1.
    );
  }

  /**
   * Snap a value to the global quantization setting
   *
   * @param cursor Cursor to snap
   * @return Cursor with snapping applied
   */
  public Cursor snapLaunchQuantization(Cursor cursor) {
    return snapLaunchQuantization(cursor, false);
  }

  private Cursor snapLaunchQuantization(Cursor cursor, boolean snapToFloor) {
    if (this.timeBase.getEnum() == Cursor.TimeBase.TEMPO) {
      Tempo.Quantization globalQ = this.lx.engine.tempo.launchQuantization.getObject();
      if (globalQ.hasDivision()) {
        return snapTempo(cursor, globalQ.getDivision(), snapToFloor);
      }
    }
    return cursor;
  }

  /**
   * Snap a value to a tempo division
   *
   * @param cursor Value to snap
   * @param division Value will be rounded to the nearest multiple of this tempo division
   * @return Cursor with snapping applied
   */
  public Cursor snapTempo(Cursor cursor, Tempo.Division division) {
    return snapTempo(cursor, division, false);
  }

  private Cursor snapTempo(Cursor cursor, Tempo.Division division, boolean snapToFloor) {
    Cursor snapSize = constructTempoCursor(division);
    return snapToFloor ?
      CursorOp().snapFloor(cursor, this, snapSize) :
      CursorOp().snap(cursor, this, snapSize);
  }

  private static final String KEY_LANES = "parameterLanes";
  public static final String KEY_INDEX = "index";

  private void loadLegacyCursor(JsonObject parametersObj, CursorParameter cursor) {
    // Load legacy-mode if we find a value for the raw parameter path but not for
    // the AggregateParameter sub-field, meaning it was written as a simple double
    if (parametersObj.has(cursor.getPath()) && !parametersObj.has(cursor.millis.getPath())) {
      double millis = parametersObj.get(cursor.getPath()).getAsDouble();
      cursor.set(constructAbsoluteCursor(millis));
    }
  }

  private void loadLegacyMarker(JsonObject parametersObj, CursorParameter marker) {
    // Check that the legacy marker is missing, and that it wasn't written AggregateParameter
    if (!parametersObj.has(marker.getPath()) && !parametersObj.has(marker.millis.getPath())) {
      marker.set(this.length);
    }
  }

  private boolean inLoad = false;

  @Override
  public void load(LX lx, JsonObject obj) {
    clearLanes();

    // Load parameters before lanes, which need to know clip timing mode
    this.inLoad = true;
    this.timeBase.reset(); // For legacy case where it doesn't exist
    super.load(lx, obj);
    this.inLoad = false;

    // For legacy clips, restore raw values, set loop and play markers
    if (obj.has(KEY_PARAMETERS)) {
      final JsonObject parametersObj = obj.getAsJsonObject(KEY_PARAMETERS);
      for (CursorParameter cursor : this.cursorParameters) {
        loadLegacyCursor(parametersObj, cursor);
      }
      loadLegacyMarker(parametersObj, this.loopLength);
      loadLegacyMarker(parametersObj, this.playEnd);
    }

    this.hasTimeline = !CursorOp().isZero(this.length.cursor);

    // Load clip lanes
    if (obj.has(KEY_LANES)) {
      JsonArray lanesArr = obj.get(KEY_LANES).getAsJsonArray();
      for (JsonElement laneElement : lanesArr) {
        loadLane(lx, laneElement.getAsJsonObject(), -1);
      }
    }
  }

  public ParameterClipLane addParameterLane(LX lx, JsonObject laneObj, int index) {
    LXParameter parameter;
    if (laneObj.has(LXComponent.KEY_PATH)) {
      String parameterPath = laneObj.get(KEY_PATH).getAsString();
      parameter = LXPath.getParameter(this.bus, parameterPath);
      if (parameter == null) {
        LX.error("No parameter found for saved parameter clip lane on bus " + this.bus + " at path: " + parameterPath);
        return null;
      }
    } else {
      int componentId = laneObj.get(KEY_COMPONENT_ID).getAsInt();
      LXComponent component = lx.getProjectComponent(componentId);
      if (component == null) {
        LX.error("No component found for saved parameter clip lane on bus " + this.bus + " with id: " + componentId);
        return null;
      }
      String parameterPath = laneObj.get(KEY_PARAMETER_PATH).getAsString();
      parameter = component.getParameter(parameterPath);
      if (parameter == null) {
        LX.error("No parameter found for saved parameter clip lane on component " + component + " at path: " + parameterPath);
        return null;
      }
    }
    ParameterClipLane lane = getParameterLane((LXNormalizedParameter) parameter, true, index);
    lane.load(lx, laneObj);
    return lane;
  }

  protected String getLaneType(JsonObject laneObj) {
    return laneObj.get(LXClipLane.KEY_LANE_TYPE).getAsString();
  }

  public LXClipLane<?> loadLane(LX lx, JsonObject laneObj, int index) {
    final String laneType = getLaneType(laneObj);
    if (laneType.equals(LXClipLane.VALUE_LANE_TYPE_PARAMETER)) {
      return addParameterLane(lx, laneObj, index);
    }
    LX.error("Cannot load unknown clip lane type: " + laneType);
    return null;
  }

  public LXClipLane<?> moveLane(LX lx, JsonObject laneObj, int index, String fromPath, String toPath) {
    final String laneType = getLaneType(laneObj);
    if (laneType.equals(LXClipLane.VALUE_LANE_TYPE_PARAMETER)) {
      final JsonObject moveObj = laneObj.deepCopy();
      final String lanePath = moveObj.get(LXComponent.KEY_PATH).getAsString();
      moveObj.addProperty(LXComponent.KEY_PATH, LXPath.replacePrefix(lanePath, fromPath, toPath, this.bus));
      return addParameterLane(lx, moveObj, index);
    }
    LX.error("Cannot move unknown clip lane type: " + laneType);
    return null;
  }

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_INDEX, this.index);
    obj.add(KEY_LANES, LXSerializable.Utils.toArray(lx, this.lanes));
  }
}
