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

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXSerializable;
import heronarts.lx.midi.LXMidiListener;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

/**
 * Modulator that provides randomization within normalized value range.
 */
@LXModulator.Global("Randomizer")
@LXModulator.Device("Randomizer")
@LXCategory(LXCategory.CORE)
public class Randomizer extends LXPeriodicModulator implements LXNormalizedParameter, LXTriggerSource, LXMidiListener, LXOscComponent {

  public enum TriggerMode {
    INTERNAL("Internal"),
    EXTERNAL("External");

    public final String label;

    private TriggerMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public enum InterpolationMode {
    DIRECT("Direct"),
    DAMPING("Damped"),
    SMOOTHING("Smooth");

    public final String label;

    private InterpolationMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final EnumParameter<TriggerMode> triggerMode =
    new EnumParameter<TriggerMode>("Trig Mode", TriggerMode.INTERNAL)
    .setDescription("Whether triggers are internally or externally generated");

  public final EnumParameter<InterpolationMode> lerpMode =
    new EnumParameter<InterpolationMode>("Lerp Mode", InterpolationMode.DIRECT)
    .setDescription("Whether triggers are internally or externally generated");

  public final CompoundParameter periodMs =
    new CompoundParameter("Interval", 1000, 10, 1000*60)
    .setExponent(3)
    .setUnits(CompoundParameter.Units.MILLISECONDS)
    .setDescription("Base interval for random target value updates");

  public final CompoundParameter randomMs =
    new CompoundParameter("Random", 0, 0, 1000*60)
    .setExponent(3)
    .setUnits(CompoundParameter.Units.MILLISECONDS)
    .setDescription("Range of random time added to each interval");

  public final CompoundParameter chance =
    new CompoundParameter("Chance", 100, 0, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Chance that the randomizer fires on each interval");

  private double randomInterval = 0;

  private final FunctionalParameter totalMs = new FunctionalParameter() {
    @Override
    public double getValue() {
      return triggerMode.getEnum() == TriggerMode.EXTERNAL ?
        Double.POSITIVE_INFINITY :
        periodMs.getValue() + randomInterval * randomMs.getValue();
    }
  };

  public final CompoundParameter speed =
    new CompoundParameter("Speed", 5, .01, 10)
    .setExponent(2)
    .setDescription("Speed of value update");

  public final CompoundParameter accelTimeSecs =
    new CompoundParameter("Accel Time", .5, 0, 5)
    .setUnits(CompoundParameter.Units.SECONDS)
    .setExponent(2)
    .setDescription("Number of seconds to accelerate up to speed");

  public final CompoundParameter smoothingWindow =
    new CompoundParameter("Time", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Smoothing window time");

  public final BoundedParameter smoothingWindowRangeMs =
    new BoundedParameter("Range", 1000, 100, 60000)
    .setUnits(BoundedParameter.Units.MILLISECONDS)
    .setDescription("Range of smoothing window control");

  public final CompoundParameter min =
    new CompoundParameter("Minimum", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Minimum output value");

  public final CompoundParameter max =
    new CompoundParameter("Maximum", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Maximum output value");

  public final TriggerParameter triggerIn =
    new TriggerParameter("Trigger In", this::onTriggerIn)
    .setDescription("Engages the randomizer directly");

  public final TriggerParameter triggerOut =
    new TriggerParameter("Trigger Out")
    .setDescription("Engages when the randomizer triggers");

  private final MutableParameter target = new MutableParameter(0.5);

  private double smoothedValue = 0;

  private final DampedParameter damper = new DampedParameter(
    this.target,
    this.speed,
    FunctionalParameter.create(() -> {
      final double accelTimeSecs = this.accelTimeSecs.getValue();
      return (accelTimeSecs == 0) ? 0 : (this.speed.getValue() / accelTimeSecs);
    })
  );

  public Randomizer() {
    this("Random");
  }

  private Randomizer(String label) {
    super(label, null);
    this.midiFilter.enabled.setValue(false);

    setPeriod(this.totalMs);

    addParameter("triggerMode", this.triggerMode);
    addParameter("leprMode", this.lerpMode);

    addParameter("periodMs", this.periodMs);
    addParameter("randomMs", this.randomMs);
    addParameter("chance", this.chance);

    addParameter("speed", this.speed);
    addParameter("accelTimeSecs", this.accelTimeSecs);

    addParameter("smoothingWindow", this.smoothingWindow);
    addParameter("smoothingWindowRangeMs", this.smoothingWindowRangeMs);

    addParameter("min", this.min);
    addParameter("max", this.max);

    addParameter("triggerIn", this.triggerIn);
    addParameter("triggerOut", this.triggerOut);

    this.damper.start();

    setDescription("Random value updated with specified interval and range");
  }

  private static final String KEY_LEGACY_ACCEL = "accel";
  private static final String KEY_LEGACY_DAMPING = "damping";

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (LXSerializable.Utils.hasParameter(obj, KEY_LEGACY_ACCEL)) {
      // Acceleration was formerly stored as an absolute value, if that key is still there, then
      // we set the new parameter based upon the ratio
      final double legacyAccel = LXSerializable.Utils.getParameter(obj, KEY_LEGACY_ACCEL).getAsDouble();
      this.accelTimeSecs.setValue((legacyAccel == 0) ? 0 : (this.speed.getValue() / legacyAccel));
    }
    if (LXSerializable.Utils.hasParameter(obj, KEY_LEGACY_DAMPING)) {
      if (LXSerializable.Utils.getParameter(obj, KEY_LEGACY_DAMPING).getAsBoolean()) {
        this.lerpMode.setValue(InterpolationMode.DAMPING);
      }
    }
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.triggerMode) {
      if (this.triggerMode.getEnum() == TriggerMode.EXTERNAL) {
        this.tempoSync.setValue(false);
        setBasis(0);
      }
    }
  }

  private void onTriggerIn() {
    if (this.running.isOn() && (this.triggerMode.getEnum() == TriggerMode.EXTERNAL)) {
      onManualTrigger(true);
    }
  }

  private void onManualTrigger(boolean resetBasis) {
    if (Math.random()*100 < this.chance.getValue()) {
      fire();
      if (resetBasis) {
        setBasis(0);
      }
    }
  }

  private void fire() {
    this.randomInterval = Math.random();
    this.target.setValue(LXUtils.lerp(this.min.getValue(), this.max.getValue(), Math.random()));
    this.triggerOut.trigger();
  }

  @Override
  protected double computeValue(double deltaMs, double basis) {
    if (this.triggerMode.getEnum() == TriggerMode.INTERNAL) {
      if (loop() || finished()) {
        if (Math.random()*100 < this.chance.getValue()) {
          fire();
        }
      }
    }
    switch (this.lerpMode.getEnum()) {
    case DAMPING:
      return LXUtils.constrain(this.damper.getValue(), 0, 1);
    case SMOOTHING:
      return this.smoothedValue;
    default:
    case DIRECT:
      return this.smoothedValue = this.target.getValue();
    }
  }

  @Override
  protected void postRun(double deltaMs) {
    this.damper.loop(deltaMs);

    this.smoothedValue = LXUtils.lerp(
      getValue(),
      this.target.getValue(),
      LXUtils.min(1, deltaMs / (this.smoothingWindow.getValue() * this.smoothingWindowRangeMs.getValue()))
    );

    switch (this.lerpMode.getEnum()) {
    case DAMPING:
      updateValue(LXUtils.constrain(this.damper.getValue(), 0, 1));
      break;
    case SMOOTHING:
      updateValue(this.smoothedValue);
      break;
    default:
      break;
    }
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    this.target.setValue(value);
    return this;
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.triggerOut;
  }

  @Override
  protected double computeBasis(double basis, double value) {
    return 0;
  }

  @Override
  public void noteOnReceived(MidiNoteOn note) {
    onManualTrigger(false);
  }

}
