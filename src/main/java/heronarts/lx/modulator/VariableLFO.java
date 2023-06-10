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

package heronarts.lx.modulator;

import heronarts.lx.LXCategory;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.FixedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.ObjectParameter;

/**
 * A sawtooth LFO oscillates from one extreme value to another. When the later
 * value is hit, the oscillator rests to its initial value.
 */
@LXCategory(LXCategory.CORE)
@LXModulator.Global("LFO")
@LXModulator.Device("LFO")
public class VariableLFO extends LXVariablePeriodModulator implements LXWaveshape, LXOscComponent {

  /**
   * Parameter of {@link LXWaveshape} objects that select the wave shape used by this LFO.
   * Default options are the waveshapes predefined in {@link LXWaveshape}, but you can pass your own.
   */
  public final ObjectParameter<LXWaveshape> waveshape;

  /** Period of the waveform, in ms */
  public final CompoundParameter periodCustom;

  public final CompoundParameter skew =
    new CompoundParameter("Skew", 0, -1, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Sets a skew coefficient for the waveshape")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter shape =
    new CompoundParameter("Shape", 0, -1, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Applies shaping to the waveshape")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter exp =
    new CompoundParameter("Exp", 0, -1, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Applies exponential scaling to the waveshape")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter bias =
    new CompoundParameter("Bias", 0, -1, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Bias towards or away from the center of the waveform")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter phase =
    new CompoundParameter("Phase", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Shifts the phase of the waveform");

  public VariableLFO() {
    this("LFO", null, null);
  }

  public VariableLFO(String label) {
    this(label, null, null);
  }

  public VariableLFO(String label, LXWaveshape[] waveshapes) {
    this(label, waveshapes, null);
  }

  /**
   * Constructs a VariableLFO with a custom list of waveshapes
   * @param label LFO label
   * @param waveshapes Optional list of custom {@link LXWaveshape}.  If null, will use predefined ones
   *                   in {@link LXWaveshape}
   * @param period Optional. Parameter that supplies custom waveform period, in ms.  Default goes 100-60000ms.
   */
  public VariableLFO(String label, LXWaveshape[] waveshapes, CompoundParameter period) {
    super(label, new FixedParameter(0), new FixedParameter(1), new FixedParameter(1000));

    if (waveshapes == null) {
      waveshapes = new LXWaveshape[] {
          LXWaveshape.SIN,
          LXWaveshape.TRI,
          LXWaveshape.SQUARE,
          LXWaveshape.UP,
          LXWaveshape.DOWN
      };
    }

    this.waveshape = new ObjectParameter<LXWaveshape>("Wave", waveshapes);
    this.waveshape.setDescription("Selects the wave shape used by this LFO");

    if (period != null) {
      this.periodCustom = period;
      addParameter("period", this.periodCustom);
      setPeriod(this.periodCustom);
    } else {
      this.periodCustom = null;
      setPeriod(this.periodFast);
    }

    addParameter("wave", this.waveshape);
    addParameter("skew", this.skew);
    addParameter("shape", this.shape);
    addParameter("bias", this.bias);
    addParameter("phase", this.phase);
    addParameter("exp", this.exp);
  }

  public LXWaveshape getWaveshape() {
    return this.waveshape.getObject();
  }

  @Override
  protected final double computeNormalizedValue(double deltaMs, double basis) {
    return compute(basis);
  }

  @Override
  protected final double computeNormalizedBasis(double basis, double normalizedValue) {
    return invert(normalizedValue, basis);
  }

  @Override
  public double compute(double basis) {
    return compute(basis, this.phase.getValue(), this.bias.getValue(), this.skew.getValue(), this.shape.getValue(), this.exp.getValue());
  }

  public double computeBase(double basis) {
    return compute(basis, this.phase.getBaseValue(), this.bias.getBaseValue(), this.skew.getBaseValue(), this.shape.getBaseValue(), this.exp.getBaseValue());
  }

  private double compute(double basis, double phase, double bias, double skew, double shape, double exp) {
    basis = basis + phase;
    if (basis > 1.) {
      basis = basis % 1.;
    }

    if (bias != 0) {
      double biasPower = 1 + 3 * Math.abs(bias);
      double midp = .25 - .15 * bias;
      double midInv = 1 / midp;

      double midAlt = .5 - midp;
      double midAltInv = 1 / midAlt;

      if (basis < midp) {
        basis = midp * Math.pow(midInv * basis, biasPower);
      } else if (basis < .5) {
        basis = .5f - midAlt * Math.pow(midAltInv * (.5f - basis), biasPower);
      } else if (basis < .5 + midAlt) {
        basis = .5f + midAlt * Math.pow(midAltInv * (basis - .5f), biasPower);
      } else {
        basis = 1 - midp * Math.pow(midInv * (1 - basis), biasPower);
      }
    }

    double skewPower = (skew >= 0) ? (1 + 3*skew) : (1 / (1-3*skew));
    if (skewPower != 1) {
      basis = Math.pow(basis, skewPower);
    }
    double value = getWaveshape().compute(basis);
    double shapePower = (shape <= 0) ? (1 - 3*shape) : (1 / (1+3*shape));
    if (shapePower != 1) {
      if (value >= 0.5) {
        value = 0.5 + 0.5 * Math.pow(2*(value-0.5), shapePower);
      } else {
        value = 0.5 - 0.5 * Math.pow(2*(0.5 - value), shapePower);
      }
    }
    double expPower = (exp >= 0) ? (1 + 3*exp) : (1 / (1 - 3*exp));
    if (expPower != 1) {
      value = Math.pow(value, expPower);
    }
    return value;
  }

  @Override
  public double invert(double value, double basisHint) {
    // TODO(mcslee): implement shape and bias inversion properly??
    return getWaveshape().invert(value, basisHint);
  }
}