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

import heronarts.lx.LXCategory;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
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
public class Randomizer extends LXPeriodicModulator implements LXNormalizedParameter, LXTriggerSource, LXOscComponent {

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

  public final EnumParameter<TriggerMode> triggerMode =
    new EnumParameter<TriggerMode>("Trig Mode", TriggerMode.INTERNAL)
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

  public final BooleanParameter damping =
    new BooleanParameter("Damping", true)
    .setDescription("Apply damping to the movement of the random value");

  private final FunctionalParameter totalMs = new FunctionalParameter() {
    @Override
    public double getValue() {
      return triggerMode.getEnum() == TriggerMode.EXTERNAL ?
        Double.POSITIVE_INFINITY :
        periodMs.getValue() + randomInterval * randomMs.getValue();
    }
  };

  public final CompoundParameter speed =
    new CompoundParameter("Speed", 5, .1, 10)
    .setExponent(2)
    .setDescription("Speed of value update");

  public final CompoundParameter accel =
    new CompoundParameter("Acceleration", 5, .1, 10)
    .setExponent(2)
    .setDescription("Acceleration on value change");

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

  private final DampedParameter damper = new DampedParameter(this.target, this.speed, this.accel);

  public Randomizer() {
    this("Random");
  }

  private Randomizer(String label) {
    super(label, null);
    setPeriod(this.totalMs);

    addParameter("triggerMode", this.triggerMode);
    addParameter("periodMs", this.periodMs);
    addParameter("randomMs", this.randomMs);
    addParameter("chance", this.chance);

    addParameter("damping", this.damping);
    addParameter("speed", this.speed);
    addParameter("accel", this.accel);

    addParameter("min", this.min);
    addParameter("max", this.max);

    addParameter("triggerIn", this.triggerIn);
    addParameter("triggerOut", this.triggerOut);

    this.damper.start();

    setDescription("Random value updated with specified interval and range");
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
      if (Math.random()*100 < this.chance.getValue()) {
        fire();
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
    return this.damping.isOn() ?
      LXUtils.constrain(this.damper.getValue(), 0, 1) :
      this.target.getValue();
  }

  @Override
  protected void postRun(double deltaMs) {
    this.damper.loop(deltaMs);
    if (this.damping.isOn()) {
      updateValue(LXUtils.constrain(this.damper.getValue(), 0, 1));
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

}
