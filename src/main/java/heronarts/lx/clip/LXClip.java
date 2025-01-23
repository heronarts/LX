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
import heronarts.lx.LXLayer;
import heronarts.lx.LXLayeredComponent;
import heronarts.lx.LXPath;
import heronarts.lx.LXRunnableComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.parameter.QuantizedTriggerParameter;
import heronarts.lx.snapshot.LXClipSnapshot;
import heronarts.lx.utils.LXUtils;

public abstract class LXClip extends LXRunnableComponent implements LXOscComponent, LXComponent.Renamable, LXBus.Listener {

  private static final double LOOP_LENGTH_MINIMUM = 125;

  public interface Listener {
    public default void cursorChanged(LXClip clip, double from, double to) {};
    public default void clipLaneMoved(LXClip clip, LXClipLane lane, int index) {};
    public default void parameterLaneAdded(LXClip clip, ParameterClipLane lane) {};
    public default void parameterLaneRemoved(LXClip clip, ParameterClipLane lane) {};
  }

  private final List<Listener> listeners = new ArrayList<Listener>();

  double cursor = 0;

  public static class TimestampParameter extends MutableParameter {

    public TimestampParameter(String label) {
      this(label, 0);
    }

    public TimestampParameter(String label, double value) {
      super(label, value);
      setMinimum(0);
      setUnits(LXParameter.Units.MILLISECONDS);
    }

    @Override
    public TimestampParameter setDescription(String description) {
      super.setDescription(description);
      return this;
    }
  }

  // TODO: also change length to a shielded (read-only) parameter, like cursor?
  public final TimestampParameter length =
    new TimestampParameter("Length")
    .setDescription("The length of the clip");

  public final BooleanParameter loop =
    new BooleanParameter("Loop")
    .setDescription("Whether to loop the clip");

  public final TimestampParameter loopStart =
    new TimestampParameter("Loop Start")
    .setDescription("Where the clip will loop to when loop is enabled");

  public final TimestampParameter loopLength =
    new TimestampParameter("Loop Length")
    .setDescription("Length of the loop in milliseconds");

  public final TimestampParameter playStart =
    new TimestampParameter("Play Start")
    .setDescription("Where the loop will start playing when it is launched");

  public final TimestampParameter playEnd =
    new TimestampParameter("Play End")
    .setDescription("Where an unlooped clip will stop playing");

  public final TimestampParameter launchFrom =
    new TimestampParameter("Launch From")
    .setDescription("The next launch will start from this position");

  public final QuantizedTriggerParameter launch =
    new QuantizedTriggerParameter.Launch(lx, "Launch", this::trigger)
    .setDescription("Launch this clip");

  public final QuantizedTriggerParameter stop =
    new QuantizedTriggerParameter.Launch(lx, "Stop", this::stop)
    .setDescription("Stop this clip");

  protected final List<LXClipLane> mutableLanes = new ArrayList<LXClipLane>();
  public final List<LXClipLane> lanes = Collections.unmodifiableList(this.mutableLanes);

  public final BooleanParameter snapshotEnabled =
    new BooleanParameter("Snapshot", true)
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

  public final LXBus bus;
  public final LXClipSnapshot snapshot;

  // Whether a timeline has been created. If a clip has never been run in recording mode,
  // that won't exist yet. The flag is set the first time a clip is recorded to, or if
  // a clip is loaded that had a non-zero length.
  private boolean hasTimeline = false;

  private int index;
  private final boolean busListener;

  private final LXParameterListener parameterRecorder = this::recordParameterChange;

  private void recordParameterChange(LXParameter p) {
    if (isRunning() && this.bus.arm.isOn()) {
      LXListenableNormalizedParameter parameter = (LXListenableNormalizedParameter) p;
      ParameterClipLane lane = getParameterLane(parameter, true);

      // NOTE(mcslee): need to sanitize this for overdub mode, not necessarily an append
      // depending upon the cursor position!
      lane.appendEvent(new ParameterClipEvent(lane, parameter));
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
    addParameter("launch", this.launch);
    addParameter("stop", this.stop);
    addParameter("length", this.length);
    addParameter("loop", this.loop);
    addParameter("loopStart", this.loopStart);
    addParameter("loopLength", this.loopLength);
    addParameter("playStart", this.playStart);
    addParameter("playEnd", this.playEnd);
    addParameter("snapshotEnabled", this.snapshotEnabled);
    addParameter("snapshotTransitionEnabled", this.snapshotTransitionEnabled);
    addParameter("automationEnabled", this.automationEnabled);
    addParameter("customSnapshotTransition", this.customSnapshotTransition);

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

  /**
   * Launches the clip, subject to global launch quantization
   *
   * @return this
   */
  public LXClip launch() {
    return launchFrom(this.playStart.getValue());
  }

  /**
   * Launches the clip from a specified start position,
   * subject to global launch quantization.
   *
   * @param cursor Position to launch from
   * @return this
   */
  public LXClip launchFrom(double cursor) {
    this.launchFrom.setValue(LXUtils.constrain(cursor, 0, this.length.getValue()));
    this.launch.trigger();
    return this;
  }

  /**
   * Launches the clip from the current cursor position
   *
   * @return this
   */
  public LXClip launchFromCursor() {
    return launchFrom(this.cursor);
  }

  /**
   * Play clip from cursor position without quantization delay.
   * Does nothing if the clip is already running or if it has
   * not been initialized.
   *
   * @param cursor
   * @return this
   */
  public LXClip playFrom(double cursor) {
    if (!isRunning() && this.hasTimeline) {
      this.launchFrom.setValue(LXUtils.constrain(cursor, 0, this.length.getValue()));
      trigger();
    }
    return this;
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
   * If stopped, play from Play Start marker.
   * If playing or recording, stop.
   */
  public LXClip toggleAction(boolean quantize) {
    if (isRunning()) {
      if (quantize) {
        this.stop.trigger();
      } else {
        stop();
      }
    } else {
      if (quantize) {
        launch();
      } else {
        this.launchFrom.setValue(LXUtils.constrain(this.playStart.getValue(), 0, this.length.getValue()));
        trigger();
      }
    }
    return this;
  }

  @Override
  public String getPath() {
    return "clip/" + (index + 1);
  }

  @Override
  protected void onTrigger() {
    super.onTrigger();
    if (isRunning()) {
      // This is a "restart" operation, we were already running but we've been re-triggered
      // Retrieve and apply the launchFrom position
      setCursor(LXUtils.constrain(this.launchFrom.getValue(), 0, this.length.getValue()));
    } else {
      // TODO(mcslee): need to figure this out, we don't want to recall the snapshot when the trigger
      // event came from internal APIs, not from the UX. For now we've got a messy assumption here that
      // if we weren't already playing, this was a grid trigger event that started us off. That may
      // *not* be the case.
      //
      // Quantized launch events via the timeline UI or internal UIs need to use a different codepath,
      // we should reserve LXClip.launch/trigger for the UX operations
      //
      // This cursor == 0 check should probably go away once that is sorted out
      if (!isArmed() && this.cursor == 0 && this.snapshotEnabled.isOn()) {
        this.snapshot.recall();
      }
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    this.snapshot.stopTransition();
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
    this.listeners.clear();
    super.dispose();
  }


  public double getLength() {
    return this.length.getValue();
  }

  private ParameterClipLane getParameterLane(LXNormalizedParameter parameter, boolean create) {
    for (LXClipLane lane : this.lanes) {
      if (lane instanceof ParameterClipLane) {
        if (((ParameterClipLane) lane).parameter == parameter) {
          return (ParameterClipLane) lane;
        }
      }
    }
    if (create) {
      ParameterClipLane lane = ParameterClipLane.create(this, parameter, this.parameterDefaults.get(parameter));
      this.mutableLanes.add(lane);
      for (Listener listener : this.listeners) {
        listener.parameterLaneAdded(this, lane);
      }
      return lane;
    }
    return null;
  }

  private LXClip _removeLane(LXClipLane lane) {
    this.mutableLanes.remove(lane);
    if (lane instanceof ParameterClipLane) {
      for (Listener listener : this.listeners) {
        listener.parameterLaneRemoved(this, (ParameterClipLane) lane);
      }
    }
    LX.dispose(lane);
    return this;
  }

  public LXClip removeParameterLane(ParameterClipLane lane) {
    return _removeLane(lane);
  }

  public LXClip moveClipLane(LXClipLane lane, int index) {
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

  /**
   * Move the cursor and notify listeners
   */
  protected void setCursor(double cursor) {
    if (this.cursor != cursor) {
      double from = this.cursor;
      this.cursor = cursor;
      for (Listener listener : this.listeners) {
        listener.cursorChanged(this, from, cursor);
      }
    }
  }

  public double getCursor() {
    return this.cursor;
  }

  public double getBasis() {
    double lengthValue = this.length.getValue();
    if (lengthValue > 0) {
      return this.cursor / lengthValue;
    }
    return 0;
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

  // Limiters

  private double getBraceLengthMinimum() {
    // If time units was bars rather than milliseconds, this would be different
    return LOOP_LENGTH_MINIMUM;
  }

  // Markers, including virtual (loop brace)

  public double getLoopStart() {
    return this.loopStart.getValue();
  }

  /**
   * Get the effective position of the brace (in time units).
   * For example callers may follow with a call to setLoopBrace() to apply a location change.
   *
   * @return position of the loop brace
   */
  public double getLoopBrace() {
    return this.loopStart.getValue();
  }

  public double getLoopEnd() {
    return LXUtils.min(this.loopStart.getValue() + this.loopLength.getValue(), this.length.getValue());
  }

  public double getPlayStart() {
    return this.playStart.getValue();
  }

  public double getPlayEnd() {
    return this.playEnd.getValue();
  }

  /**
   * Move the loop start position by a given amount of time.
   * Maintains the illusion of the end marker staying in place.
   * If end marker is overrun it will be moved with the start.
   *
   * @param relativeMs Amount of time to move, in milliseconds. Can be negative.
   */
  public LXClip moveLoopStart(double relativeMs) {
    return setLoopStart(this.loopStart.getValue() + relativeMs);
  }

  /**
   * Move the brace by a given amount of time.
   *
   * @param relativeMs Amount of time to move, in milliseconds. Can be negative.
   */
  public LXClip moveLoopBrace(double relativeMs) {
    return setLoopBrace(getLoopBrace() + relativeMs);
  }

  /**
   * Move the loop end position by a given amount of time.
   *
   * @param relativeMs Amount of time to move, in milliseconds.  Can be negative.
   */
  public LXClip moveLoopEnd(double relativeMs) {
    return setLoopEnd(getLoopEnd() + relativeMs);
  }

  /**
   * Move the play start position by a given amount of time.  Can be negative.
   * Can not be moved to a position later than the play end marker.
   *
   * @param relativeMs Amount of time to move, in milliseconds
   */
  public LXClip movePlayStart(double relativeMs) {
    return setPlayStart(this.playStart.getValue() + relativeMs);
  }

  /**
   * Move the play end marker by a given amount of time.  Can be negative.
   * Can not be moved to a position earlier than the play start marker.
   *
   * @param relativeMs Amount of time to move, in milliseconds
   */
  public LXClip movePlayEnd(double relativeMs) {
    return setPlayEnd(this.playEnd.getValue() + relativeMs);
  }

  /**
   * Safely set the loop start marker to a specific value (in time units)
   *
   * @param absoluteMs position on the timeline, in time units
   */
  public LXClip setLoopStart(double absoluteMs) {
    double loopEnd = this.loopStart.getValue() + this.loopLength.getValue();
    double value = LXUtils.constrain(absoluteMs, 0, this.length.getValue() - getBraceLengthMinimum());
    value = LXUtils.constrain(value, 0, loopEnd - getBraceLengthMinimum());
    double delta = value - this.loopStart.getValue();
    if (delta < 0) {
      // Move left
      this.loopStart.setValue(value);
      this.loopLength.setValue(this.loopLength.getValue() - delta);
    } else {
      // Move right
      this.loopLength.setValue(this.loopLength.getValue() - delta);
      this.loopStart.setValue(value);
    }
    return this;
  }

  /**
   * Safely set the loop brace to a position on the timeline (in time units)
   *
   * @param absoluteMs position on the timeline, in time units
   */
  public LXClip setLoopBrace(double absoluteMs) {
    double oldEnd = this.loopStart.getValue() + this.loopLength.getValue();
    // Loop end is defined by Length, so moving the start has the appearance of moving the brace
    // Restrict right-direction move to remaining space after the brace
    double value = LXUtils.min(absoluteMs, this.length.getValue() - this.loopLength.getValue());
    // Restrict left side to zero, may have been a left move or loop length may have been smaller than gridSnapUnits
    value = LXUtils.max(0, value);
    this.loopStart.setValue(value);
    // Check for cursor capture while playing
    captureCursorWithLoopMove(oldEnd);
    return this;
  }

  /**
   * Safely set the loop end marker to a specific value (in time units)
   *
   * @param absoluteMs position on the timeline, in time units
   */
  public LXClip setLoopEnd(double absoluteMs) {
    double oldEnd = getLoopEnd();
    double value = LXUtils.max(getBraceLengthMinimum(), absoluteMs - this.loopStart.getValue());
    value = LXUtils.constrain(value, 0, this.length.getValue() - this.loopStart.getValue());
    this.loopLength.setValue(value);
    // Check for cursor capture while playing
    captureCursorWithLoopMove(oldEnd);
    return this;
  }

  /**
   * Safely set the play start marker to a specific value (in time units)
   *
   * @param absoluteMs position on the timeline, in time units
   */
  public LXClip setPlayStart(double absoluteMs) {
    double value = LXUtils.min(absoluteMs, this.playEnd.getValue() - getBraceLengthMinimum());
    value = LXUtils.max(0, value);
    this.playStart.setValue(value);
    return this;
  }

  /**
   * Safely set the play end marker to a specific value (in time units)
   *
   * @param absoluteMs position on the timeline, in time units
   */
  public LXClip setPlayEnd(double absoluteMs) {
    double oldEnd = this.playEnd.getValue();
    double value = LXUtils.max(absoluteMs, this.playStart.getValue() + getBraceLengthMinimum());
    value = LXUtils.min(value, this.length.getValue());
    this.playEnd.setValue(value);

    // If we cross the cursor going left, while we are the relevant marker, stop playback
    if (!this.loop.isOn() && isRunning() && !this.bus.arm.isOn()) {
      double newEnd = this.playEnd.getValue();
      if (this.cursor < oldEnd && this.cursor > newEnd) {
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
  private void captureCursorWithLoopMove(double oldEnd) {
    if (this.loop.isOn() && isRunning() && !this.bus.arm.isOn()) {
      double newEnd = this.loopStart.getValue() + this.loopLength.getValue();
      if (this.cursor < oldEnd && this.cursor > newEnd) {
        // Advance cursor to prior end of loop
        advanceCursor(this.cursor, oldEnd);
        // Wrap cursor to start of loop
        setCursor(this.loopStart.getValue());
        // If loop start is very beginning, run snapshot
        // NOTE(mcslee): Don't think so, snapshot recall should only be on explicit
        // grid or control surface triggers
        // if (this.cursor == 0 && this.snapshotEnabled.isOn()) {
        //  this.snapshot.recall();
        // }
      }
    }
  }

  // State management layer 1

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.running) {
      if (this.running.isOn()) {
        startRunning();
      } else {
        stopRunning();
      }
    } else if (p == this.bus.arm) {
      if (isRunning()) {
        if (this.bus.arm.isOn()) {
          startHotOverdub();
        } else {
          if (this.hasTimeline) {
            stopHotOverdub();
          }
        }
      }
    }
  }

  // If recording was stopped by turning off the bus arm, we can no longer use bus.arm.isOn()
  // to know if we were running.  And so... tracking it here.
  private boolean isRecording = false;

  /**
   * Start from a stopped state
   */
  private void startRunning() {
    // Stop other clips on the bus
    for (LXClip clip : this.bus.clips) {
      if (clip != null && clip != this) {
        clip.stop();
      }
    }
    // Retrieve and apply the launchFrom position
    setCursor(LXUtils.constrain(this.launchFrom.getValue(), 0, this.length.getValue()));
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
   * Start overdubbing from an already-playing state
   */
  public void startHotOverdub() {
    // TODO: mcslee to review
    stopPlayback();
    this.isRecording = true;
    startOverdub();
  }

  /**
   * Stop overdubbing from a hot-overdub state
   */
  public void stopHotOverdub() {
    stopOverdub();
    this.isRecording = false;
    // cursor advancement will continue...
  }

  /**
   * Stop from a rec/play state
   */
  private void stopRunning() {
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
  }

  // State management layer 2

  private void startFirstRecording() {
    this.cursor = 0;
    this.length.setValue(0);
    this.loopLength.setValue(0);
    this.loopStart.setValue(0);
    this.playStart.setValue(0);
    this.playEnd.setValue(0);

    // Begin recording automation
    this.automationEnabled.setValue(true);
    updateParameterDefaults();
    onStartRecording();
  }

  private void startOverdub() {
    // TODO(mcslee): toggle an overdub / replace recording mode
    updateParameterDefaults();
    onStartRecording();
  }

  private void startPlayback() {
    setCursor(LXUtils.constrain(this.launchFrom.getValue(), 0, this.length.getValue()));
  }

  private void stopFirstRecording() {
    this.length.setValue(this.cursor);
    this.loopStart.setValue(0);
    this.loopLength.setValue(this.cursor);
    this.playStart.setValue(0);
    this.playEnd.setValue(this.cursor);
    this.hasTimeline = true;
    onStopRecording();
  }

  private void stopOverdub() {
    onStopRecording();
  }

  private void stopPlayback() {

  }

  // State change notifications to subclasses

  protected void onStartRecording() {
    // Subclasses may override
  }

  protected void onStopRecording() {
    // Subclasses may override
  }

  private void clearLanes() {
    Iterator<LXClipLane> iter = this.mutableLanes.iterator();
    while (iter.hasNext()) {
      LXClipLane lane = iter.next();
      if (lane instanceof ParameterClipLane) {
        iter.remove();
        for (Listener listener : this.listeners) {
          listener.parameterLaneRemoved(this, (ParameterClipLane) lane);
        }
      } else {
        lane.clear();
      }
    }
  }

  private final Map<LXNormalizedParameter, Double> parameterDefaults = new HashMap<>();

  private void updateParameterDefaults() {
    for (LXNormalizedParameter p : this.parameterDefaults.keySet()) {
      this.parameterDefaults.put(p, p.getBaseNormalized());
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

  protected void registerComponent(LXComponent component) {
    for (LXParameter p : component.getParameters()) {
      if (p instanceof LXListenableNormalizedParameter) {
        registerParameter((LXListenableNormalizedParameter) p);
      }
    }
    if (component instanceof LXLayeredComponent) {
      for (LXLayer layer : ((LXLayeredComponent) component).getLayers()) {
        registerComponent(layer);
      }
    }
  }

  protected void unregisterComponent(LXComponent component) {
    for (LXParameter p : component.getParameters()) {
      if (p instanceof LXListenableNormalizedParameter) {
        unregisterParameter((LXListenableNormalizedParameter) p);
        ParameterClipLane lane = getParameterLane((LXNormalizedParameter) p, false);
        if (lane != null) {
          this.mutableLanes.remove(lane);
          for (Listener listener : this.listeners) {
            listener.parameterLaneRemoved(this, lane);
          }
        }
      }
    }
    if (component instanceof LXLayeredComponent) {
      for (LXLayer layer : ((LXLayeredComponent) component).getLayers()) {
        unregisterComponent(layer);
      }
    }
  }

  public int getIndex() {
    return this.index;
  }

  public LXClip setIndex(int index) {
    this.index = index;
    return this;
  }


  private void advanceCursor(double from, double to) {
    for (LXClipLane lane : this.lanes) {
      lane.advanceCursor(from, to);
    }
  }

  @Override
  protected void run(double deltaMs) {
    double nextCursor = this.cursor + deltaMs;
    if (this.bus.arm.isOn()) {
      if (!this.hasTimeline) {
        // Recording mode... lane and event listeners will pick up and record
        // all the events. All we need to do here is update the clip length
        this.length.setValue(nextCursor);
      } else {
        // Overdubbing!
        // TODO: Figure out how overdub works
        // TODO: Stop recording if we cross the play end marker?

        // Extend length once the end of clip is reached
        if (this.length.getValue() < nextCursor) {
          this.length.setValue(nextCursor);
        }
        // TODO: play existing automations during overdub
        // Should user be able to arm Clip Lanes individually for overdub?
      }
      setCursor(nextCursor);
    } else {
      // Not record
      boolean automationFinished = true;
      if (this.automationEnabled.isOn()) {
        // Play Automation
        boolean isLoop = this.loop.isOn();
        double loopStart = this.loopStart.getValue();
        double loopLength = this.loopLength.getValue();
        double loopEnd = loopStart + loopLength;
        double lengthValue = this.length.getValue();
        double playEnd = this.playEnd.getValue();
        double endValue = isLoop ? loopEnd : playEnd;
        // End markers only apply when the cursor passes over them. If playback was started
        // past them, the effective end point will be the clip length.
        if (this.cursor > endValue) {
          endValue = this.length.getValue();
        }

        automationFinished = false;
        // TODO(mcslee): make this more efficient, keep track of our indices?

        if (nextCursor < endValue) {
          // Normal play frame
          advanceCursor(this.cursor, nextCursor);
          setCursor(nextCursor);
        } else {

          // TODO(mcslee): definitely need some special MIDI lane processing here
          // and in various other stop points, to ensure that we send a MIDI note off
          // for any MIDI events that have had a Note On, but for which the Note Off
          // lies outside of the loop region. This happens in the looping case, but
          // could also happen in any case of stopping recording or stopping playback
          // where notes are hanging. Need to identify and handle those as well.

          // Reached the end
          // Play automation events right up to the end but not past
          advanceCursor(this.cursor, endValue);

          if (isLoop && lengthValue > 0) {
            // Loop
            if (loopLength > 0) {
              // Wrap into new loop, play automations up to next position
              while (nextCursor >= endValue) {
                nextCursor -= loopLength;
                // If loop was to very beginning, run snapshot
                // NOTE(mcslee): I don't think so, snapshot should be on an explicit UX trigger command
                // if (loopStart == 0 && this.snapshotEnabled.isOn()) {
                //   this.snapshot.recall();
                // }
                if (endValue < nextCursor) {
                  // Loop is smaller than frame, run automations for each time we would have passed them
                  advanceCursor(loopStart, endValue);
                } else {
                  // Normal expected behavior, we're now within the loop.
                  // Run animations between start of loop and new position
                  advanceCursor(loopStart, nextCursor);
                }
              }
              setCursor(nextCursor);
            } else {
              // Clip is non-zero length but loop is zero length. Boring!
              setCursor(loopStart);
              // If we want it to keep playing with loop length zero,
              // we would need to add a flag to prevent the cursor from advancing
              // once the loop has been engaged.
              // Note(jkb): this was fully tested before limits were established for loop length
              automationFinished = true;
            }
          } else {
            // Reached the end, either no loop or no clip length. Stop.
            setCursor(endValue);
            automationFinished = true;
          }
        }
      }

      // NOTE(mcslee): the snapshot does not finish its interpolation if
      // someone manually stops the clip before it's finished. I *think* we can treat
      // that as expected semantics? Stopping via grid UI will kill the interpolation
      // as far as it got, which may be useful/desirable. Just keep this in mind.
      if (this.snapshotEnabled.isOn()) {
        this.snapshot.loop(deltaMs);
      }

      // Did we finish automation and snapshot playback, stop!
      if (automationFinished && !this.snapshot.isInTransition()) {
        stop();
      }
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

  private static final String KEY_LANES = "parameterLanes";
  public static final String KEY_INDEX = "index";

  @Override
  public void load(LX lx, JsonObject obj) {
    clearLanes();
    if (obj.has(KEY_LANES)) {
      JsonArray lanesArr = obj.get(KEY_LANES).getAsJsonArray();
      for (JsonElement laneElement : lanesArr) {
        JsonObject laneObj = laneElement.getAsJsonObject();
        String laneType = laneObj.get(LXClipLane.KEY_LANE_TYPE).getAsString();
        loadLane(lx, laneType, laneObj);
      }
    }
    super.load(lx, obj);
    // For legacy clips, set loop and play markers
    if (obj.has(KEY_PARAMETERS)) {
      final JsonObject parametersObj = obj.getAsJsonObject(KEY_PARAMETERS);
      if (!LXSerializable.Utils.hasParameter(parametersObj, this.loopLength.getPath())) {
        setLoopEnd(this.length.getValue());
      }
      if (!LXSerializable.Utils.hasParameter(parametersObj, this.playEnd.getPath())) {
        setPlayEnd(this.length.getValue());
      }
    }

    this.hasTimeline = this.length.getValue() > 0;
  }

  protected void loadLane(LX lx, String laneType, JsonObject laneObj) {
    if (laneType.equals(LXClipLane.VALUE_LANE_TYPE_PARAMETER)) {
      LXParameter parameter;
      if (laneObj.has(LXComponent.KEY_PATH)) {
        String parameterPath = laneObj.get(KEY_PATH).getAsString();
        parameter = LXPath.getParameter(this.bus, parameterPath);
        if (parameter == null) {
          LX.error("No parameter found for saved parameter clip lane on bus " + this.bus + " at path: " + parameterPath);
          return;
        }
      } else {
        int componentId = laneObj.get(KEY_COMPONENT_ID).getAsInt();
        LXComponent component = lx.getProjectComponent(componentId);
        if (component == null) {
          LX.error("No component found for saved parameter clip lane on bus " + this.bus + " with id: " + componentId);
          return;
        }
        String parameterPath = laneObj.get(KEY_PARAMETER_PATH).getAsString();
        parameter = component.getParameter(parameterPath);
        if (parameter == null) {
          LX.error("No parameter found for saved parameter clip lane on component " + component + " at path: " + parameterPath);
          return;
        }
      }
      LXClipLane lane = getParameterLane((LXNormalizedParameter) parameter, true);
      lane.load(lx, laneObj);
    }
  }

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_INDEX, this.index);
    obj.add(KEY_LANES, LXSerializable.Utils.toArray(lx, this.lanes));
  }
}
