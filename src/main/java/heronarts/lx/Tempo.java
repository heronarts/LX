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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import heronarts.lx.modulator.LXTriggerSource;
import heronarts.lx.modulator.LinearEnvelope;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.osc.LXOscEngine;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

/**
 * Class to represent a musical tempo at which patterns are operating. This can
 * be updated in real-time via invoking the {@link #tap()} method. Note there is no automatic beat detection -
 * the Tempo object must be explicitly {@link #tap()}'d to learn the tempo.
 *
 * "Beats" are indicated by the return value of {@link #beat()}. {@link #ramp()} returns the current beat phase
 * from 0 to 1
 *
 * The {@link #bpm} parameter indicates the current BPM, and {@link #period} can be used to invert the beat
 * frequency (BPM) into a listenable period (ms per beat).
 *
 * Additional utility functions are available that assume beats represent the tempo:
 *   - {@link #measure()} can be polled to check measure beats, respectively.
 *   - {@link Listener}'s can be added to trigger on beats or measures without polling the Tempo object.
 */
public class Tempo extends LXModulatorComponent implements LXOscComponent, LXTriggerSource {

  public final static double DEFAULT_MIN_BPM = 20;
  public final static double DEFAULT_MAX_BPM = 240;

  private static final double MAX_SLEW_CORRECTION = 3.9;

  private double minOscBpm = DEFAULT_MIN_BPM;
  private double maxOscBpm = DEFAULT_MAX_BPM;

  public static enum Division {

    SIXTEENTH(4, "1/16"),
    EIGHTH_TRIPLET(3, "1/8T"),
    EIGHTH(2, "1/8"),
    EIGHTH_DOT(1.5, "3/16"),
    QUARTER_TRIPLET(4/3., "1/4T"),
    QUARTER(1, "1/4"),
    HALF_TRIPLET(.75, "1/2T"),
    QUARTER_DOT(2/3., "3/8"),
    HALF(.5, "1/2"),
    HALF_DOT(1/3., "3/4"),
    WHOLE(.25, "1"),
    WHOLE_DOT(1/6., "3/2"),
    DOUBLE(1/8., "2"),
    FOUR(1/16., "4"),
    EIGHT(1/32., "8"),
    SIXTEEN(1/64., "16");

    public final double multiplier;
    public final String label;

    Division(double multiplier, String label) {
      this.multiplier = multiplier;
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public enum ClockSource {
    INTERNAL,
    MIDI,
    OSC;

    public boolean isExternal() {
      return this != INTERNAL;
    }

    @Override
    public String toString() {
      switch (this) {
      case MIDI: return "MIDI";
      case OSC: return "OSC";
      default: case INTERNAL: return "Int";
      }
    }
  }

  public interface Listener {
    public default void onBeat(Tempo tempo, int beat) {};

    public default void onBar(Tempo tempo, int bar) { onMeasure(tempo); }

    @Deprecated
    public default void onMeasure(Tempo tempo) {};

  }

  private final static double MS_PER_MINUTE = 60000;
  private final static double DEFAULT_BPM = 120;
  private final static int MAX_BEATS_PER_BAR = 16;

  public final EnumParameter<ClockSource> clockSource =
    new EnumParameter<ClockSource>("Clock", ClockSource.INTERNAL)
    .setMappable(false)
    .setDescription("Source of the tempo clock");

  public final DiscreteParameter beatsPerBar =
    new DiscreteParameter("Time Signature", 4, 1, MAX_BEATS_PER_BAR + 1)
    .setDescription("Beats per bar");

  public final BoundedParameter bpm = (BoundedParameter)
    new BoundedParameter("BPM", DEFAULT_BPM, this.minOscBpm, this.maxOscBpm)
    .setOscMode(BoundedParameter.OscMode.ABSOLUTE)
    .setDescription("Beats per minute of the master tempo");

  public final TriggerParameter trigger =
    new TriggerParameter("Trigger", () -> {
      this.lx.engine.osc.sendMessage(getOscAddress() + "/" + PATH_BEAT, 1 + beatCountWithinBar());
    })
    .setDescription("Listeable trigger which is set on each beat");

  public final BooleanParameter enabled =
    new BooleanParameter("Enabled")
    .setDescription("Whether tempo trigger modulation is enabled");

  public final BooleanParameter tap =
    new BooleanParameter("Tap")
    .setDescription("When pressed repeatedlly, tempo is learned from the timing between taps")
    .setMode(BooleanParameter.Mode.MOMENTARY);

  public final BooleanParameter nudgeUp =
    new BooleanParameter("Nudge+")
    .setDescription("Temporarily increases tempo while engaged");

  public final BooleanParameter nudgeDown =
    new BooleanParameter("Nudge-")
    .setDescription("Temporarily decreases tempo while engaged");

  private final LinearEnvelope nudge = new LinearEnvelope(1, 1, 5000);

  public final MutableParameter period = (MutableParameter)
    new MutableParameter(MS_PER_MINUTE / DEFAULT_BPM)
    .setUnits(MutableParameter.Units.MILLISECONDS)
    .setDescription("Reports the duration between beats (ms)");

  private final List<Listener> listeners = new ArrayList<Listener>();

  private boolean resetOnNextBeat = false;
  private boolean didTrigger = false;
  private long triggerNanoTime = -1;
  private boolean running = true;
  private boolean oscParIsPlaying = true;

  private class Cursor {
    private boolean isBeat = false;
    private int beatCount = 0;
    private double basis = 0;

    private void advance(double progress) {
      advance(progress, true);
    }

    private void advance(double progress, boolean clearBeat) {
      boolean isBeat = false;
      this.basis += progress;
      if (this.basis >= 1.) {
        // NOTE: this is overkill but for some crazy fast tempo and slow engine rate we
        // could hop multiple beats in a go?!?
        this.beatCount += (int) this.basis;
        this.basis = this.basis % 1.;
        isBeat = true;
      }
      if (clearBeat || isBeat) {
        this.isBeat = isBeat;
      }
    }

    private void reset() {
      this.basis = 0;
      this.beatCount = 0;
      this.isBeat = false;
    }

    private void set(Cursor that) {
      this.basis = that.basis;
      this.beatCount = that.beatCount;
      this.isBeat = that.isBeat;
    }

    private int barCount() {
      return this.beatCount / beatsPerBar.getValuei();
    }

    private int beatCountWithinBar() {
      return this.beatCount % beatsPerBar.getValuei();
    }

    private double getCompositeBasis() {
      return this.beatCount + this.basis;
    }

    // Note: need these helpers! Using composite basis comparisons can create
    // bugs from subtle rounding errors where the integer bar count summed with
    // the basis is not exactly the right value
    private boolean isEqualTo(Cursor that) {
      return
        (this.beatCount == that.beatCount) &&
        (this.basis == that.basis);
    }

    // NB: again, need explicit comparisons
    private boolean isBehind(Cursor that) {
      return
        (this.beatCount < that.beatCount) || (
          (this.beatCount == that.beatCount) &&
          (this.basis < that.basis)
        );
    }

    private double getCompositeDistance(Cursor that) {
      return Math.abs(getCompositeBasis() - that.getCompositeBasis());
    }

  }

  private final Cursor target = new Cursor();
  private final Cursor smooth = new Cursor();
  private final Cursor slew = new Cursor();

  private long firstTapNanos = 0;
  private long lastTapNanos = 0;
  private int tapCount = 0;

  private boolean inBpmPeriodUpdate = false;

  private int oscBeatCount = -1;

  public Tempo(LX lx) {
    super(lx);
    addParameter("clockSource", this.clockSource);
    addParameter("period", this.period);
    addParameter("bpm", this.bpm);
    addParameter("tap", this.tap);
    addParameter("nudgeUp", this.nudgeUp);
    addParameter("nudgeDown", this.nudgeDown);
    addParameter("beatsPerBar", this.beatsPerBar);
    addParameter("trigger", this.trigger);
    addParameter("enabled", this.enabled);
    addModulator("nudge", this.nudge);

    addLegacyParameter("beatsPerMeasure", this.beatsPerBar);
  }

  private static final String PATH_BEAT = "beat";
  private static final String PATH_BEAT_WITHIN_BAR = "beat-within-bar";
  private static final String PATH_SET_BPM = "setBPM";
  private static final String PATH_OSC_PAR = "osc-par";
  private static final String PATH_OSC_PAR_BPM = "BPM";
  private static final String PATH_OSC_PAR_BEAT_POS = "BeatPOS";
  private static final String PATH_OSC_PAR_IS_PLAYING = "isPlaying";

  public Tempo setOscBpmRange(double min, double max) {
    if (min <= 0.0 || min >= max) {
      // do not set to invalid range of BPMs
      throw new IllegalArgumentException("Tried to set invalid bpm range!");
    }
    this.minOscBpm = min;
    this.maxOscBpm = max;
    return this;
  }

  public boolean isValidOscBpm(double bpm) {
    return bpm >= this.minOscBpm && bpm <= this.maxOscBpm;
  }

  @Override
  public boolean handleOscMessage(OscMessage message, String[] parts, int index) {
    if (parts[index].equals(PATH_SET_BPM)) {
      if (message.size() > 0) {
        float newBpm = message.getFloat();
        if (isValidOscBpm(newBpm)) {
          this.bpm.setValue(newBpm);
        }
        return true;
      }
    } else if (parts[index].equals(PATH_BEAT)) {
      if (this.clockSource.getObject() == Tempo.ClockSource.OSC) {
        if (message.size() > 0) {
          // Message specifies an absolute 1-indexed beat count
          trigger(message.getInt()-1, message);
        } else {
          // Oof, these raw beat messages are risky business...
          if (this.oscBeatCount < 0) {
            trigger(this.oscBeatCount = 0, message);
          } else {
            trigger(++this.oscBeatCount, message);
          }
        }
      }
      return true;
    } else if (parts[index].equals(PATH_BEAT_WITHIN_BAR)) {
      if (this.clockSource.getObject() == Tempo.ClockSource.OSC) {
        if (message.size() > 0) {
          triggerBeatWithinBar(message.getInt(), message);
        } else {
          LXOscEngine.error(PATH_BEAT_WITHIN_BAR + " message missing argument: " + message.toString());
        }
      }
      return true;
    } else if (parts[index].equals(PATH_OSC_PAR)) {
      handleOscParMessage(message, parts, index+1);
      return true;
    }
    return super.handleOscMessage(message, parts, index);
  }

  private void handleOscParMessage(OscMessage message, String[] parts, int index) {
    if (index >= parts.length) {
      LXOscEngine.error(PATH_OSC_PAR + " message address is too short: " + message.toString());
      return;
    }
    if (message.size() < 1) {
      LXOscEngine.error(PATH_OSC_PAR + " message missing argument: " + message.toString());
      return;
    }
    if (PATH_OSC_PAR_BPM.equals(parts[index])) {
      this.bpm.setValue(message.getFloat());
    } else if (PATH_OSC_PAR_BEAT_POS.equals(parts[index])) {
      trigger(message.getInt(), message);
    } else if (PATH_OSC_PAR_IS_PLAYING.equals(parts[index])) {
      this.running = this.oscParIsPlaying = message.getBoolean();
    }
  }

  @Override
  public String getLabel() {
    return "Tempo";
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.period) {
      if (!this.inBpmPeriodUpdate) {
        this.inBpmPeriodUpdate = true;
        this.bpm.setValue(MS_PER_MINUTE / this.period.getValue());
        this.inBpmPeriodUpdate = false;
      }
    } else if (p == this.bpm) {
      if (!this.inBpmPeriodUpdate) {
        this.inBpmPeriodUpdate = true;
        this.period.setValue(MS_PER_MINUTE / this.bpm.getValue());
        this.inBpmPeriodUpdate = false;
      }
    } else if (p == this.tap) {
      if (this.tap.isOn()) {
        tap();
      }
    } else if (p == this.nudgeUp) {
      updateNudge(this.nudgeUp, this.nudgeDown, .90);
    } else if (p == this.nudgeDown) {
      updateNudge(this.nudgeDown, this.nudgeUp, 1.1);
    } else if (p == this.clockSource) {
      // NOTE: this looks funny but is intentional, we don't know
      // if we're using OSC-PAR, we set this flag to true and wait
      // to actually see an "isPlaying 0" message from OSC-PAR before
      // we'll actually stop the metronome because of it
      this.oscParIsPlaying = true;

      if (this.clockSource.getEnum().isExternal()) {
        // Reset and stop clock, wait for a trigger
        this.resetOnNextBeat = false;
        this.oscBeatCount = -1;
        this.running = false;
        this.target.reset();
        this.smooth.reset();
      } else {
        this.running = true;
        if (this.target.basis >= 1) {
          this.target.basis = this.smooth.basis = 0;
        }
      }
    }
  }

  private void updateNudge(BooleanParameter changed, BooleanParameter other, double target) {
    if (changed.isOn()) {
      if (other.isOn()) {
        // If the other nudge button was already down, do nothing, first one to be
        // pressed has the priority as long as it is held. See below for what happens
        // upon the release.
      } else {
        this.nudge.setRange(1, target).reset().start();
      }
    } else {
      // We released this button, if the other one was being held, it takes over now
      if (other.isOn()) {
        this.nudge.setRange(1, target).reset().start();
      } else {
        // Both buttons released, nudge goes back to 1
        this.nudge.stop();
        this.nudge.setValue(1);
      }
    }
  }

  public Tempo addListener(Listener listener) {
    Objects.requireNonNull("May not add null Tempo.Listener");
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate Tempo.Listener: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  public Tempo removeListener(Listener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot remove non-existent Tempo.Listener: " + listener);
    }
    this.listeners.remove(listener);
    return this;
  }

  /**
   * Method to indicate when we are on-beat, assuming quarter-notes being given
   * one beat.
   *
   * @return true if we are on a quarter-note beat
   */
  public boolean beat() {
    return this.smooth.isBeat;
  }

  /**
   * Returns the count of what bar number we are on
   *
   * @return Bar count
   */
  public int barCount() {
    return this.smooth.beatCount / this.beatsPerBar.getValuei();
  }

  /**
   * Gets the count of the current beat we are on, 0-indexed and absolute
   *
   * @return Beat count
   */
  public int beatCount() {
    return this.smooth.beatCount;
  }

  /**
   * Gets the count of the current beat we are on, 0-indexed and within the
   * range [0, beatsPerBar-1]
   *
   * @return Beat count within bar
   */
  public int beatCountWithinBar() {
    return this.smooth.beatCountWithinBar();
  }

  /**
   * Gets the composite basis of the tempo, which is the beatCount combined with the current
   * basis cycle.
   *
   * @return Number of full beats completed since beginning of tempo
   */
  public double getCompositeBasis() {
    return this.smooth.getCompositeBasis();
  }

  /**
   * Gets the cycle count at this tempo division
   *
   * @param division Division level
   * @return Measure count
   */
  public int getCycleCount(Division division) {
    return getCycleCount(division.multiplier);
  }

  public int getCycleCount(double multiplier) {
    // NB - a bit of tricky casting and math games needed here to avoid rounding
    // errors when summing
    final double beatMultiple = this.smooth.beatCount * multiplier;
    final double basisMultiple = this.smooth.basis * multiplier;
    return
      (int) beatMultiple +
      (int) basisMultiple +
      (int) ((beatMultiple % 1.) + (basisMultiple % 1.));
  }

  /**
   * Gets the basis of the tempo, relative to a tempo division. The result is between
   * 0 and strictly less than 1
   *
   * @param division Tempo division
   * @return Relative tempo division cycle basis from 0-1
   */
  public double getBasis(Division division) {
    // NB the repeated % operations, this is to deal with rounding errors that could
    // unfortunately push values up, e.g. beatCount=19 basis=0.9999999999999996 and
    // division = WHOLE would end up rounding up to 5.0 when it should be strictly less
    // than 5, and this can cause problems with clients of this method like a periodic
    // LFO wrapping around when they should not. Doing the modulo operations independently
    // keeps the resulting value < 1. as it should be.
    final double beatMultiple = this.smooth.beatCount * division.multiplier;
    final double basisMultiple = this.smooth.basis * division.multiplier;
    return ((beatMultiple % 1.) + (basisMultiple % 1.)) % 1.;
  }

  /**
   * Method to indicate the start of a measure.
   *
   * @deprecated Use bar()
   * @return true if we are on a measure-beat
   */
  @Deprecated
  public boolean measure() {
    return bar();
  }

  /**
   * Method to indicate the start of a bar.
   *
   * @return true if we are on a bar-beat
   */
  public boolean bar() {
    return beat() && (0 == beatCountWithinBar());
  }

  /**
   * Indicates phase of the current beat. On the beat the value will be 0, then
   * ramp up to 1 before the next beat triggers.
   *
   * @return value from 0-1 indicating phase of beat
   */
  public double basis() {
    return this.smooth.basis;
  }

  public float basisf() {
    return (float) this.smooth.basis;
  }

  /**
   * Indicates phase of the current beat. On the beat the value will be 0, then
   * ramp up to 1 before the next beat triggers. This is deprecated, should
   * use {@link #basis()} instead.
   *
   * @return value from 0-1 indicating phase of beat
   */
  @Deprecated
  public double ramp() {
    return basis();
  }

  /**
   * Indicates beat phase in floating point. This is deprecated, should
   * use {@link #basisf()} instead.
   *
   * @return value from 0-1 indicating phase of beat
   */
  @Deprecated
  public float rampf() {
    return (float) ramp();
  }

  /**
   * Returns the current tempo in Beats Per Minute
   *
   * @return Current tempo
   */
  public double bpm() {
    return this.bpm.getValue();
  }

  /**
   * Returns the tempo in floating point
   *
   * @return Current tempo in float
   */
  public float bpmf() {
    return (float) this.bpm();
  }

  /**
   * Sets the BPM to the given value
   *
   * @param bpm Number of beats per minute
   * @return this
   */
  public Tempo setBpm(double bpm) {
    this.bpm.setValue(bpm);
    return this;
  }

  /**
   * Adjust the BPM by the given amount
   *
   * @param amount Amount to adjust BPM by
   * @return this
   */
  public Tempo adjustBpm(double amount) {
    this.bpm.setValue(this.bpm.getValue() + amount);
    return this;
  }

  /**
   * Sets the period of one beat
   *
   * @param beatMillis Milliseconds in a beat
   * @return this
   */
  public Tempo setPeriod(double beatMillis) {
    this.period.setValue(beatMillis);
    return this;
  }

  /**
   * When in internal clock mode, the next beat will reset the
   * beat count to 0.
   */
  public Tempo resetOnNextBeat() {
    if (this.clockSource.getEnum() == ClockSource.INTERNAL) {
      this.resetOnNextBeat = true;
    }
    return this;
  }

  /**
   * Re-triggers the metronome, so that it immediately beats. Also resetting the
   * beat count to be at the beginning of a measure.
   */
  public void trigger() {
    trigger(true);
  }

  /**
   * Triggers the given beat within bar, keeping count over subsequent bars if
   * desired.
   *
   * @param beatWithinBar Beat within bar, 1-indexed
   * @param message OSC message that triggered the beat, for timing adjustment
   */
  public void triggerBeatWithinBar(int beatWithinBar, OscMessage message) {
    triggerBeatWithinBar(beatWithinBar, message.nanoTime);
  }

  /**
   * Triggers the given beat within bar, keeping count over subsequent bars if
   * desired.
   *
   * @param beatWithinBar Beat within bar, 1-indexed
   * @param nanoTime System.nanoTime() value for the event causing trigger
   */
  public void triggerBeatWithinBar(int beatWithinBar, long nanoTime) {
    // Message specifies a relative 1-indexed beat count within the bar
    final int currentBeat = this.target.beatCountWithinBar();
    final int nextBeat = beatWithinBar - 1;
    final int currentBar = this.target.barCount();
    if (nextBeat < currentBeat) {
      // The beat has wrapped around, e.g. 2, 3, 4 -> 1
      // We are moving onto the next bar!
      trigger((currentBar+1) * this.beatsPerBar.getValuei() + nextBeat, nanoTime);
    } else {
      // We are still in the same bar, possibly on the same beat which
      // reached us a tad late, or stepping forwards
      trigger(this.target.beatCount + (nextBeat - currentBeat), nanoTime);
    }
  }

  /**
   * Trigger the given bar and beat position, both 1-indexed
   *
   * @param bar Bar number, 1-indexed
   * @param beat Beat number, 1-indexed
   */
  public void triggerBarAndBeat(int bar, int beat) {
    triggerBarAndBeat(bar, beat, System.nanoTime());
  }

  /**
   * Trigger the given bar and beat position, both 1-indexed
   *
   * @param bar Bar number, 1-indexed
   * @param beat Beat number, 1-indexed
   * @param message Source message, for timing adjustment
   */
  public void triggerBarAndBeat(int bar, int beat, OscMessage message) {
    triggerBarAndBeat(bar, beat, message.nanoTime);
  }

  /**
   * Trigger the given bar and beat position, both 1-indexed
   *
   * @param bar Bar number, 1-indexed
   * @param beat Beat number, 1-indexed
   * @param nanoTime System.nanoTime() value of the event that caused the trigger
   */
  public void triggerBarAndBeat(int bar, int beat, long nanoTime) {
    if (bar < 1 || beat < 1) {
      throw new IllegalArgumentException("Bar and beat must be 1 or greater: " + bar + "." + beat);
    }
    trigger((bar-1) * this.beatsPerBar.getValuei() + beat - 1, nanoTime);
  }

  /**
   * Triggers a beat, optionally resetting the beat count
   *
   * @param resetBeat True if the beat count should be reset to 0
   */
  public void trigger(boolean resetBeat) {
    trigger(resetBeat ? 0 : this.target.beatCount + 1);
  }

  /**
   * Triggers the metronome, setting the beat count to the given explicit value
   *
   * @param beat Beat count, 0-indexed
   */
  public void trigger(int beat) {
    trigger(beat, System.nanoTime());
  }

  /**
   * Triggers the metronome, setting the beat count to the given value
   * with a specified offset into the beat.
   *
   * @param beat Beat count, 0-indexed
   * @param message OSC message that caused the trigger, for timing adjustment
   */
  public void trigger(int beat, OscMessage message) {
    trigger(beat, message.nanoTime);
  }

  /**
   * Triggers the metronome, setting the beat count to the given value
   * with a specified offset into the beat.
   *
   * @param beat Beat count, 0-indexed
   * @param nanoTime System.nanoTime() value of event that caused the trigger
   */
  public void trigger(int beat, long nanoTime) {
    this.target.beatCount = beat;
    this.triggerNanoTime = nanoTime;
    this.didTrigger = true;
  }

  /**
   * Adjusts the tempo in realtime by tapping. Whenever tap() is invoked the
   * time between previous taps is averaged to compute a new tempo. At least
   * three taps are required to compute a tempo. Otherwise, tapping just
   * re-triggers the beat. It is better to use the trigger() method directly if
   * this is all that is desired.
   */
  public void tap() {
    tap(System.nanoTime());
  }

  /**
   * Adjusts the tempo, specifying an exact timestamp in milliseconds
   * of when the tap event occurred.
   *
   * @param nanoTime Timestamp of event, should be equivalent to System.nanoTime()
   */
  public void tap(long nanoTime) {
    if (nanoTime - this.lastTapNanos > 2000000000) {
      this.firstTapNanos = nanoTime;
      this.tapCount = 0;
    }
    this.lastTapNanos = nanoTime;
    ++this.tapCount;
    if (this.tapCount > 3) {
      double beatPeriodMs = (this.lastTapNanos - this.firstTapNanos) / 1000000. / (this.tapCount - 1.);
      setBpm(MS_PER_MINUTE / beatPeriodMs);
    }
    trigger(this.tapCount - 1);
  }

  /**
   * Stop the metronome running
   */
  public void stop() {
    this.running = false;
  }

  @Override
  public void loop(double deltaMs) {
    // Run modulators
    super.loop(deltaMs);

    final double progress = deltaMs / (this.period.getValue() * this.nudge.getValue());

    final boolean oscIsPaused =
      (this.clockSource.getEnum() == ClockSource.OSC) &&
      !this.oscParIsPlaying;

    // Explicit beat trigger, back to the start of the beat
    if (this.didTrigger) {
      this.didTrigger = false;

      // NOTE: potentially overkill, but on a slow framerate the time that the engine gets around
      // to processing the input event which triggered a beat, we could be *into* that beat period,
      // so let's compute the basis here and set it for sane values (< 1., if we're past that, the
      // engine isn't even keeping up with an entire beat and tempo will be entirely fucked regardless)
      final double triggerBasis =
        (this.lx.engine.nowNanoTime <= this.triggerNanoTime) ? 0. :
        (this.lx.engine.nowNanoTime - this.triggerNanoTime) / 1000000. / this.period.getValue();
      this.target.basis = (triggerBasis < 1.) ? triggerBasis : 0.;
      this.target.isBeat = true;
      this.running = true;
    } else if (this.running && !oscIsPaused) {
      // Non-trigger situation, advance + wrap the target cursor
      this.target.advance(progress);
    } else {
      this.target.isBeat = false;
    }

    // If we get isPlaying 0 from OSC-PAR we stop until it comes back
    if (oscIsPaused) {
      this.running = false;
    }

    // If the reset flag was set, cronk our target beat down to 0
    if (this.target.isBeat) {
      if (this.resetOnNextBeat) {
        this.resetOnNextBeat = false;
        this.target.beatCount = 0;
      }
    }

    // Now it's time to update the smoothed transport
    this.slew.set(this.smooth);
    this.slew.isBeat = false;
    if (this.running) {
      this.slew.advance(progress);
    }

    // Were you dragging, or rushing?
    final double slewError = this.slew.getCompositeDistance(this.target);
    final double correctionLerp = LXUtils.min(1, slewError);

    if (slewError >= MAX_SLEW_CORRECTION) {
      // We're so far off that there is no point smoothing this, just jump
      // to the new state.
      this.smooth.set(this.target);
    } else if (this.slew.isEqualTo(this.target)) {
      // We're bang on! Incredible. Use the slew state (it may have a
      // different isBeat state)
      this.smooth.set(this.slew);
    } else if (this.slew.isBehind(this.target)) {
      // We're dragging - boost on ahead
      // LX.log(this.smooth.getCompositeBasis() + " --->>> " + this.target.getCompositeBasis());
      double accel = LXUtils.lerp(.1, 2, correctionLerp);
      this.slew.advance(LXUtils.min(slewError, progress * accel), false);
      this.smooth.set(this.slew);
    } else {
      // We're rushing! Need to slow it back down, but only if we are running,
      // since we never want a playhead moving in reverse. Note that we operate
      // directly on smooth here, not slew. Slew was advanced too far ahead, we're
      // going to advance *less* far ahead
      this.smooth.isBeat = false;
      if (this.running) {
        // LX.log(this.target.getCompositeBasis() + " <<<--- " + this.smooth.getCompositeBasis());
        double decel = LXUtils.lerp(.9, .25, correctionLerp);
        this.smooth.advance(progress * decel);
      }
    }

    // Check if the smoothed tempo has crossed a beat threshold, if so it is
    // time to fire off the listeners
    if (this.smooth.isBeat) {
      final int beatsPerBar = this.beatsPerBar.getValuei();
      final int beatIndex = this.smooth.beatCount % beatsPerBar;
      final int barIndex = this.smooth.beatCount / beatsPerBar;

      if (beatIndex == 0) {
        for (Listener listener : this.listeners) {
          listener.onBar(this, barIndex);
        }
      }
      for (Listener listener : this.listeners) {
        listener.onBeat(this, beatIndex);
      }
      if (this.enabled.isOn()) {
        this.trigger.trigger();
      }
    }
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.trigger;
  }

}
