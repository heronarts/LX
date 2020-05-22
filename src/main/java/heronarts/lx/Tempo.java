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

import heronarts.lx.modulator.Click;
import heronarts.lx.modulator.LinearEnvelope;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.MutableParameter;

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
public class Tempo extends LXModulatorComponent implements LXOscComponent {

  public final static double MIN_BPM = 20;
  public final static double MAX_BPM = 240;

  public static enum Multiplier {

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

    Multiplier(double multiplier, String label) {
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

    @Override
    public String toString() {
      switch (this) {
      case MIDI: return "Midi";
      case OSC: return "Osc";
      default: case INTERNAL: return "Int";
      }
    }
  }

  public interface Listener {
    public void onBeat(Tempo tempo, int beat);
    public void onMeasure(Tempo tempo);
  }

  /**
   * Default Tempo {@link Listener} that does a no-op for all types beats.
   *
   * Extend this to avoid boilerplate for beat types you don't care about.
   */
  public static class AbstractListener implements Listener {
    @Override
    public void onBeat(Tempo tempo, int beat) {
      // default is no-op, override to add custom
    }

    @Override
    public void onMeasure(Tempo tempo) {
      // default is no-op, override to add custom
    }
  }

  private final static double MS_PER_MINUTE = 60000;
  private final static double DEFAULT_BPM = 120;

  public final EnumParameter<ClockSource> clockSource =
    new EnumParameter<ClockSource>("Clock", ClockSource.INTERNAL)
    .setMappable(false)
    .setDescription("Source of the tempo clock");

  public final DiscreteParameter beatsPerMeasure = new DiscreteParameter("Beats", 4, 1, 9)
    .setDescription("Beats per measure");

  public final BoundedParameter bpm =
    new BoundedParameter("BPM", DEFAULT_BPM, MIN_BPM, MAX_BPM)
    .setDescription("Beats per minute of the master tempo object");

  public final BooleanParameter trigger =
    new BooleanParameter("Trigger")
    .setDescription("Listeable trigger which is set on each beat")
    .setMode(BooleanParameter.Mode.MOMENTARY);

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

  public final MutableParameter period = (MutableParameter) new MutableParameter(MS_PER_MINUTE / DEFAULT_BPM)
    .setDescription("Reports the duration between beats (ms)");

  private final List<Listener> listeners = new ArrayList<Listener>();

  private final Click click = new Click("Beat", new FunctionalParameter() {
    @Override
    public double getValue() {
      return period.getValue() * nudge.getValue();
    }
  });

  private long firstTap = 0;
  private long lastTap = 0;
  private int tapCount = 0;

  private int beatCount = 0;
  private boolean manuallyTriggered = false;

  private boolean parameterUpdate = false;

  public Tempo(LX lx) {
    super(lx);
    addParameter("clockSource", this.clockSource);
    addParameter("period", this.period);
    addParameter("bpm", this.bpm);
    addParameter("tap", this.tap);
    addParameter("nudgeUp", this.nudgeUp);
    addParameter("nudgeDown", this.nudgeDown);
    addParameter("beatsPerMeasure", this.beatsPerMeasure);
    addParameter("trigger", this.trigger);
    addParameter("enabled", this.enabled);
    addModulator("nudge", this.nudge);
    startModulator(this.click);
  }

  private static final String PATH_BEAT = "beat";
  private static final String PATH_SET_BPM = "setBPM";

  @Override
  public boolean handleOscMessage(OscMessage message, String[] parts, int index) {
    if (parts[index].equals(PATH_SET_BPM)) {
      if (message.size() > 0) {
        this.bpm.setValue(message.getFloat());
        return true;
      }
    } else if (parts[index].equals(PATH_BEAT)) {
      if (this.clockSource.getObject() == Tempo.ClockSource.OSC) {
        if (message.size() > 0) {
          // Message specifies a beat count
          trigger(message.getInt()-1);
        } else {
          // Message is a raw trigger only
          trigger(false);
        }
      }
      return true;
    }
    return super.handleOscMessage(message, parts, index);
  }

  @Override
  public String getLabel() {
    return "Tempo";
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.period) {
      if (!this.parameterUpdate) {
        this.parameterUpdate = true;
        this.bpm.setValue(MS_PER_MINUTE / this.period.getValue());
        this.parameterUpdate = false;
      }
    } else if (p == this.bpm) {
      if (!this.parameterUpdate) {
        this.parameterUpdate = true;
        this.period.setValue(MS_PER_MINUTE / this.bpm.getValue());
        this.parameterUpdate = false;
      }
    } else if (p == this.tap) {
      if (this.tap.isOn()) {
        tap();
      }
    } else if (p == this.nudgeUp) {
      updateNudge(this.nudgeUp, this.nudgeDown, .90);
    } else if (p == this.nudgeDown) {
      updateNudge(this.nudgeDown, this.nudgeUp, 1.1);
    } else if (p == this.trigger) {
      if (this.trigger.isOn()) {
        this.trigger.setValue(false);
      }
    } else if (p == this.clockSource) {
      if (this.clockSource.getEnum() == ClockSource.INTERNAL) {
        this.click.setLooping(true).trigger();
      } else {
        this.click.setLooping(false);
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
    return this.click.click();
  }

  /**
   * Gets the count of the current beat we are on
   *
   * @return Beat count
   */
  public int beatCount() {
    return this.beatCount;
  }

  /**
   * Gets the composite basis of the tempo, which is the beatCount combined with the current
   * basis cycle
   *
   * @return Number of full beats completed since beginning of tempo
   */
  public double compositeBasis() {
    return this.beatCount + this.click.getBasis();
  }

  /**
   * Method to indicate the start of a measure.
   *
   * @return true if we are on a measure-beat
   */
  public boolean measure() {
    return beat() && (this.beatCount % this.beatsPerMeasure.getValuei() == 0);
  }

  /**
   * Indicates phase of the current beat. On the beat the value will be 0, then
   * ramp up to 1 before the next beat triggers.
   *
   * @return value from 0-1 indicating phase of beat
   */
  public double ramp() {
    return this.click.getBasis();
  }

  /**
   * Indicates beat phase in floating point
   *
   * @return value from 0-1 indicating phase of beat
   */
  public float rampf() {
    return (float) this.ramp();
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
   * Re-triggers the metronome, so that it immediately beats. Also resetting the
   * beat count to be at the beginning of a measure.
   */
  public void trigger() {
    trigger(true);
  }

  public void trigger(int beat) {
    this.beatCount = beat;
    this.click.fire();
    this.manuallyTriggered = true;
  }

  /**
   * Triggers a beat, optionally resetting the beat count
   *
   * @param resetBeat True if the beat count should be reset to 0
   */
  public void trigger(boolean resetBeat) {
    if (!beat()) {
      this.beatCount = resetBeat ? 0 : this.beatCount + 1;
      this.click.fire();
    } else if (resetBeat) {
      this.beatCount = 0;
    }
    this.manuallyTriggered = true;
  }

  /**
   * Adjusts the tempo in realtime by tapping. Whenever tap() is invoked the
   * time between previous taps is averaged to compute a new tempo. At least
   * three taps are required to compute a tempo. Otherwise, tapping just
   * re-triggers the beat. It is better to use the trigger() method directly if
   * this is all that is desired.
   */
  public void tap() {
    tap(System.currentTimeMillis());
  }

  /**
   * Adjusts the tempo, specifying an exact timestamp in milliseconds
   * of when the tap event occurred.
   *
   * @param now Timestamp of event, should be equivalent to System.currentTimeMillis()
   */
  public void tap(long now) {
    if (now - this.lastTap > 2000) {
      this.firstTap = now;
      this.tapCount = 0;
    }
    this.lastTap = now;
    ++this.tapCount;
    if (this.tapCount > 3) {
      double beatPeriod = (this.lastTap - this.firstTap) / (double) (this.tapCount - 1);
      setBpm(MS_PER_MINUTE / beatPeriod);
    }
    trigger();
  }

  @Override
  public void loop(double deltaMs) {
    super.loop(deltaMs);
    if (beat()) {
      if (!this.manuallyTriggered) {
        ++this.beatCount;
      }
      int beatIndex = this.beatCount % this.beatsPerMeasure.getValuei();
      if (beatIndex == 0) {
        for (Listener listener : listeners) {
          listener.onMeasure(this);
        }
      }
      for (Listener listener : listeners) {
        listener.onBeat(this, beatIndex);
      }
      if (this.enabled.isOn()) {
        this.trigger.setValue(true);
      }
    }
    this.manuallyTriggered = false;
  }
}
