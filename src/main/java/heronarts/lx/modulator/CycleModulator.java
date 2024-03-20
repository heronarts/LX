/**
 * Copyright 2024- Mark C. Slee, Heron Arts LLC
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
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;

@LXModulator.Global("Cycle")
@LXModulator.Device("Cycle")
@LXCategory(LXCategory.CORE)
public class CycleModulator extends LXModulator implements LXNormalizedParameter, LXOscComponent {

  private double basis = 0.;

  public final CompoundParameter speedUnipolar =
    new CompoundParameter("Speed", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Speed of motion (unipolar)");

  public final CompoundParameter speedBipolar =
    new CompoundParameter("Speed", 1, -1, 1)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Speed of motion (bipolar)");

  public final CompoundParameter speedRangeHz =
    new CompoundParameter("Range", 1, 0, 10)
    .setUnits(BoundedParameter.Units.HERTZ)
    .setDescription("Maximum range of the speed control in hz");

  public final EnumParameter<LXParameter.Polarity> speedPolarity =
    new EnumParameter<LXParameter.Polarity>("Speed Polarity", LXParameter.Polarity.UNIPOLAR)
    .setDescription("Speed polarity");

  public CycleModulator() {
    this("Cycle");
  }

  public CycleModulator(String label) {
    super(label);
    addParameter("speedUnipolar", this.speedUnipolar);
    addParameter("speedBipolar", this.speedBipolar);
    addParameter("speedRangeHz", this.speedRangeHz);
    addParameter("speedPolarity", this.speedPolarity);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.speedPolarity) {
      if (this.speedPolarity.getEnum() == LXParameter.Polarity.UNIPOLAR) {
        this.speedUnipolar.setValue(Math.abs(this.speedBipolar.getValue()));
      } else {
        this.speedBipolar.setValue(this.speedUnipolar.getValue());
      }
    }
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    this.basis = value;
    return this;
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

  @Override
  protected double computeValue(double deltaMs) {
    final double speed = (this.speedPolarity.getEnum() == LXParameter.Polarity.BIPOLAR) ?
      this.speedBipolar.getValue() :
      this.speedUnipolar.getValue();
    this.basis += deltaMs / 1000. * this.speedRangeHz.getValue() * speed;
    if (this.basis < 0) {
      this.basis = 1. + (this.basis % 1.);
    } else {
      this.basis = this.basis % 1.;
    }
    return this.basis;
  }

}
