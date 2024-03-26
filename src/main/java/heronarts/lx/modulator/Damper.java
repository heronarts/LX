/**
 * Copyright 2022- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.modulator;

import java.util.Calendar;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.Tempo;
import heronarts.lx.midi.LXMidiListener;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.TimeParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

/**
 * Modulator that provides randomization within normalized value range.
 */
@LXModulator.Global("Damper")
@LXModulator.Device("Damper")
@LXCategory(LXCategory.CORE)
public class Damper extends LXModulator implements LXNormalizedParameter, LXTriggerSource, LXOscComponent, LXMidiListener {

  public final BooleanParameter toggle =
    new BooleanParameter("Toggle", false)
    .setDescription("Toggle whether the damper is engaged");

  public final TriggerParameter triggerEngage =
    new TriggerParameter("Engage")
    .setDescription("Trigger the damper to engage");

  public final TriggerParameter triggerRelease =
    new TriggerParameter("Release")
    .setDescription("Trigger the damper to release");

  public final CompoundParameter periodMs =
    new CompoundParameter("Interval", 1000, 10, 1000*60*5)
    .setExponent(3)
    .setUnits(CompoundParameter.Units.MILLISECONDS)
    .setDescription("Base interval for random target value updates");

  public final BooleanParameter tempoSync =
    new BooleanParameter("Sync", false)
    .setDescription("Whether this modulator syncs to a tempo");

  public final EnumParameter<Tempo.Division> tempoDivision =
    new EnumParameter<Tempo.Division>("Division", Tempo.Division.QUARTER)
    .setDescription("Tempo division when in sync mode");

  public final BooleanParameter sinShaping =
    new BooleanParameter("Ease", false)
    .setDescription("Whether to apply sinusoidal easing");

  public final BooleanParameter timing =
    new BooleanParameter("Timing", false)
    .setDescription("Whether to apply automatic timing");

  public final TimeParameter engageTime =
    new TimeParameter("Engage Time")
    .setDescription("What time of day the timer engages");

  public final TimeParameter releaseTime =
    new TimeParameter("Release Time")
    .setDescription("What time of day the timer releases");

  public final BooleanParameter engageTimerOut =
    new BooleanParameter("Engage Timer")
    .setDescription("Indicates when the engage timer fires")
    .setMode(BooleanParameter.Mode.MOMENTARY);

  public final BooleanParameter releaseTimerOut =
    new BooleanParameter("Release Timer")
    .setDescription("Indicates when the release timer fires")
    .setMode(BooleanParameter.Mode.MOMENTARY);

  private double basis = 0;

  public Damper() {
    this("Damper");
  }

  private Damper(String label) {
    super(label);

    this.midiFilter.enabled.setValue(false);

    addParameter("toggle", this.toggle);
    addParameter("triggerEngage", this.triggerEngage);
    addParameter("triggerRelease", this.triggerRelease);
    addParameter("periodMs", this.periodMs);
    addParameter("tempoSync", this.tempoSync);
    addParameter("tempoDivision", this.tempoDivision);
    addParameter("sinShaping", this.sinShaping);

    addParameter("timing", this.timing);
    addParameter("engageTime", this.engageTime);
    addParameter("releaseTime", this.releaseTime);
    addParameter("engageTimerOut", this.engageTimerOut);
    addParameter("releaseTimerOut", this.releaseTimerOut);

    setDescription("Damped value that moves from 0 to 1 with multiple triggers");
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.toggle) {
      start();
    } else if (p == this.engageTimerOut) {
      if (this.engageTimerOut.isOn()) {
        this.triggerEngage.setValue(true);
      }
    } else if (p == this.releaseTimerOut) {
      if (this.releaseTimerOut.isOn()) {
        this.triggerRelease.setValue(true);
      }
    } else if (p == this.triggerEngage) {
      if (this.triggerEngage.isOn()) {
        this.toggle.setValue(true);
        start();
      }
    } else if (p == this.triggerRelease) {
      if (this.triggerRelease.isOn()) {
        this.toggle.setValue(false);
        start();
      }
    }
  }

  private final Calendar calendar = Calendar.getInstance();

  @Override
  protected double computeValue(double deltaMs) {
    if (this.timing.isOn()) {
      this.calendar.setTimeInMillis(System.currentTimeMillis());
      final int thisSeconds = TimeParameter.getSecondsOfDay(this.calendar);
      final int engageSeconds = this.engageTime.getSecondsOfDay();
      final int releaseSeconds = this.releaseTime.getSecondsOfDay();
      this.engageTimerOut.setValue(engageSeconds == thisSeconds);
      this.releaseTimerOut.setValue(releaseSeconds == thisSeconds);
    }

    final double periodMs = this.tempoSync.isOn() ?
      this.lx.engine.tempo.period.getValue() * this.tempoDivision.getEnum().multiplier :
      this.periodMs.getValue();
    final double sign = this.toggle.isOn() ? 1 : -1;

    this.basis = LXUtils.clamp(this.basis + sign * deltaMs / periodMs, 0., 1.);

    if (this.sinShaping.isOn() ) {
      return .5 + .5 * Math.sin(-LX.HALF_PI + Math.PI * this.basis);
    }
    return this.basis;
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    if (this.sinShaping.isOn()) {
      final double radians = Math.asin(2 * (value - .5));
      this.basis = (radians + LX.HALF_PI) / Math.PI;
    } else {
      this.basis = value;
    }
    return this;
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.toggle;
  }

  private boolean noteOn = false;

  @Override
  public void noteOnReceived(MidiNoteOn note) {
    this.toggle.setValue(this.noteOn = true);
  }

  @Override
  public void noteOffReceived(MidiNote note) {
    this.toggle.setValue(this.noteOn = false);
  }

  @Override
  public void midiPanicReceived() {
    if (this.noteOn) {
      this.toggle.setValue(this.noteOn = false);
    }
  }

}
