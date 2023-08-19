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
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.utils.LXUtils;

@LXModulator.Global("Scaler")
@LXModulator.Device("Scaler")
@LXCategory(LXCategory.CORE)
public class Scaler extends LXModulator implements LXOscComponent, LXNormalizedParameter {

  public final CompoundParameter input =
    new CompoundParameter("Input", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Input value to the smoother");

  public final EnumParameter<LXParameter.Polarity> inputPolarity =
    new EnumParameter<LXParameter.Polarity>("Input Polarity", LXParameter.Polarity.UNIPOLAR)
    .setDescription("Input parameter polarity");

  public final EnumParameter<LXParameter.Polarity> gainPolarity =
    new EnumParameter<LXParameter.Polarity>("Gain Polarity", LXParameter.Polarity.UNIPOLAR)
    .setDescription("Gain polarity");

  public final BoundedParameter gainFactor =
    new BoundedParameter("Factor", 1, -10, 10)
    .setDescription("Gain range");

  public final CompoundParameter gainUnipolar =
    new CompoundParameter("Gain", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Gain applied to signal");

  public final CompoundParameter gainBipolar =
    new CompoundParameter("Gain", 1, -1, 1)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Gain applied to signal");

  public final CompoundParameter offset =
    new CompoundParameter("Offset", 0, -1, 1)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("DC Offset applied to signal");

  public final CompoundParameter shaping =
    new CompoundParameter("Shaping", 0, -1, 1)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Shaping applied to signal");

  public final BooleanParameter showPreview =
    new BooleanParameter("Preview", false)
    .setDescription("Whether the shaper preview is visible in the modulator UI");

  public Scaler() {
    this("Scaler");
  }

  public Scaler(String label) {
    super(label);
    addParameter("input", this.input);
    addParameter("inputPolarity", this.inputPolarity);
    addParameter("gainFactor", this.gainFactor);
    addParameter("gainUnipolar", this.gainUnipolar);
    addParameter("gainBipolar", this.gainBipolar);
    addParameter("gainPolarity", this.gainPolarity);
    addParameter("offset", this.offset);
    addParameter("shaping", this.shaping);
    addParameter("showPreview", this.showPreview);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.inputPolarity) {
      this.input.setPolarity(this.inputPolarity.getEnum());
    }
  }

  public double compute(double input) {
    final double shaping = this.shaping.getValue();
    final double gain =
      this.gainFactor.getValue() * (
      (this.gainPolarity.getEnum() == Polarity.UNIPOLAR) ?
        this.gainUnipolar.getValue() :
        this.gainBipolar.getValue());
    final double offset = this.offset.getValue();

    double exponent = 0;
    final double maxShape = 2;
    if (shaping > 0) {
      exponent = 1 + maxShape*shaping;
    } else if (shaping < 0) {
      exponent = 1 - maxShape*shaping;
    }

    switch (this.inputPolarity.getEnum()) {
    case UNIPOLAR:
      if (gain >= 0) {
        input *= gain;
      } else {
        input = LXUtils.lerp(0, 1-input, -gain);
      }
      input = LXUtils.constrain(input + offset, 0, 1);
      if (shaping > 0) {
        input = Math.pow(input, exponent);
      } else if (shaping < 0) {
        input = 1 - Math.pow(1 - input, exponent);
      }
      break;
    case BIPOLAR:
      input = .5 + (input - .5) * gain;
      input = LXUtils.constrain(input + offset, 0, 1);;
      if (shaping > 0) {
        if (input > .5) {
          input = .5 + .5 * Math.pow(2*(input - .5), exponent);
        } else if (input < .5) {
          input = .5 - .5 * Math.pow(1 - 2*input, exponent);
        }
      } else if (shaping < 0) {
        if (input > .5) {
          input = 1 - .5 * Math.pow(2*(1-input), exponent);
        } else if (input < .5) {
          input = .5 * Math.pow(2*input, exponent);
        }
      }
      break;
    }

    return input;
  }

  @Override
  protected double computeValue(double deltaMs) {
    return compute(this.input.getValue());
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new IllegalArgumentException("Cannot setNormalized() on Scaler");
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

}
