/**
 * Copyright 2023- Mark C. Slee, Heron Arts LLC
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
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.LXNormalizedParameter;

@LXModulator.Global("Stepper")
@LXModulator.Device("Stepper")
@LXCategory(LXCategory.CORE)
public class Stepper extends StepModulator implements LXNormalizedParameter, LXTriggerSource, LXOscComponent {

  public final BoundedParameter[] steps = new BoundedParameter[MAX_STEPS];

  public Stepper() {
    super("Stepper");

    for (int i = 0; i < this.steps.length; ++i) {
      this.steps[i] =
        new BoundedParameter("Step-" + (i+1))
        .setUnits(BoundedParameter.Units.PERCENT_NORMALIZED)
        .setDescription("Stepper value on step " + (i+1));
      addParameter("step-" + (i+1), this.steps[i]);
    }

    setDescription("Step modulator that changes values on trigger events");
  }

  @Override
  protected void onStep(boolean trigger) {
    if (trigger) {
      this.triggerOut.trigger();
    }
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException("Stepper does not support setNormalized()");
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

  @Override
  protected double getStepValue(double deltaMs, double basis) {
    return this.steps[this.step].getValue();
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.triggerOut;
  }

}
