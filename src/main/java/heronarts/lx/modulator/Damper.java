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

import heronarts.lx.LX;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;

/**
 * Modulator that provides randomization within normalized value range.
 */
@LXModulator.Global("Damper")
@LXModulator.Device("Damper")
public class Damper extends LXPeriodicModulator implements LXNormalizedParameter, LXOscComponent {

  public final CompoundParameter periodMs = (CompoundParameter)
    new CompoundParameter("Interval", 1000, 10, 1000*60*5)
    .setExponent(3)
    .setUnits(CompoundParameter.Units.MILLISECONDS)
    .setDescription("Base interval for random target value updates");

  public final BooleanParameter sinShaping =
    new BooleanParameter("Ease")
    .setDescription("Whether to apply sinusoidal easing");

  public final BooleanParameter toggle =
    new BooleanParameter("Toggle", false)
    .setDescription("Toggle whether the damper is engaged");

  public final BooleanParameter triggerEngage =
    new BooleanParameter("Engage", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Trigger the damper to engage");

  public final BooleanParameter triggerRelease =
    new BooleanParameter("Release", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Trigger the damper to release");

  public Damper() {
    this("Damper");
  }

  private Damper(String label) {
    super(label, null);
    disableAutoStart();
    disableAutoReset();
    this.looping.setValue(false);
    this.tempoLock.setValue(false);
    setPeriod(this.periodMs);

    addParameter("periodMs", this.periodMs);
    addParameter("sinShaping", this.sinShaping);
    addParameter("toggle", this.toggle);
    addParameter("triggerEngage", this.triggerEngage);
    addParameter("triggerRelease", this.triggerRelease);

    setDescription("Damped value that moves from 0 to 1 with multiple triggers");
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.toggle) {
      setBasis(1 - getBasis());
      start();
    } else if (p == this.triggerEngage) {
      if (this.triggerEngage.isOn()) {
        this.triggerEngage.setValue(false);
        this.toggle.setValue(true);
        start();
      }
    } else if (p == this.triggerRelease) {
      if (this.triggerRelease.isOn()) {
        this.triggerRelease.setValue(false);
        this.toggle.setValue(false);
        start();
      }
    }
  }

  @Override
  protected double computeValue(double deltaMs, double basis) {
    basis = this.toggle.isOn() ? basis : (1-basis);
    if (this.sinShaping.isOn() ) {
      return .5 + .5 * Math.sin(-LX.HALF_PI + Math.PI * basis);
    }
    return basis;
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    setBasis(computeBasis(getBasis(), value));
    return this;
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

  @Override
  protected double computeBasis(double basis, double value) {
    if (this.sinShaping.isOn()) {
      final double radians = Math.asin(2 * (value - .5));
      return (radians + LX.HALF_PI) / Math.PI;
    } else {
      return this.toggle.isOn() ? value : (1-value);
    }
  }

}
