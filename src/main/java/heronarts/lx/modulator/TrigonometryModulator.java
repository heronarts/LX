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
 * @author Dan Oved <oveddan@gmail.com>
 */

package heronarts.lx.modulator;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.utils.LXUtils;

/**
 * Applies a unary mathematical function to a continuously sampled modulation input.
 *
 * <p>The output is {@code gain * function(freq * input + phase) + offset}. Angles are in
 * radians. The final value is constrained to the normalized modulation range {@code [0, 1]}.
 * This is also the explicit safety policy for {@link Function#TAN}: values near an asymptote
 * saturate at an endpoint rather than escaping the modulation range. Undefined function
 * results, such as the square root of a negative number, produce zero.
 *
 * <p>{@link Function#POW} is the unary square operation {@code x^2}. A configurable exponent
 * would require another parameter and is intentionally outside the six-parameter surface of
 * this modulator.
 */
@LXModulator.Global("Trigonometry")
@LXModulator.Device("Trigonometry")
@LXCategory(LXCategory.CORE)
public class TrigonometryModulator extends LXModulator implements LXNormalizedParameter, LXOscComponent {

  public interface Apply {
    public double apply(double value, double shape);
  }

  /** Unary functions available to the operator. */
  public enum Function {
    SIN("Sin", LX.TWO_PI, (v,s) -> Math.sin(v)),
    COS("Cos", LX.TWO_PI, (v,s) -> Math.cos(v)),
    TAN("Tan", LX.TWO_PI, (v,s) -> Math.tan(v)),
    ASIN("Asin", LX.TWO_PI, (v,s) -> Math.asin(v)),
    ACOS("Acos", LX.TWO_PI, (v,s) -> Math.acos(v)),
    ATAN("Atan", LX.TWO_PI, (v,s) -> Math.atan(v)),
    SQRT("Sqrt", 1, (v,s) -> Math.sqrt(v)),
    ABS("Abs", 1, (v,s) -> Math.abs(v)),
    EXP("Exp", 1, (v,s) -> Math.exp(v)),
    POW("Pow", 1, (v,s) -> Math.pow(v,s));

    private final String label;
    private final double domain;
    private final Apply apply;

    Function(String label, double domain, Apply apply) {
      this.label = label;
      this.domain = domain;
      this.apply = apply;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final CompoundParameter input =
    new CompoundParameter("Input", 0)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Input value, map a modulation source here");

  public final EnumParameter<Function> function =
    new EnumParameter<Function>("Function", Function.SIN)
    .setDescription("Unary function applied to input * domain + phase");

  public final CompoundParameter shape =
    new CompoundParameter("Shape", 2, 0, 5)
    .setDescription("Shaping applied to some curves");

  public final CompoundParameter freq =
    new CompoundParameter("Freq", 1, -8, 8)
    .setUnits(LXParameter.Units.RADIANS)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Angular input multiplier in radians");

  public final CompoundParameter phase =
    new CompoundParameter("Phase", 0, -1, 1)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Angular offset added before applying the function");

  public final CompoundParameter gain =
    new CompoundParameter("Gain", .5, -4, 4)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Multiplier applied to the function result");

  public final CompoundParameter offset =
    new CompoundParameter("Offset", .5, -2, 2)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Value added after applying gain");

  public TrigonometryModulator() {
    super("Trigonometry");
    addParameter("input", this.input);
    addParameter("function", this.function);
    addParameter("shape", this.shape);
    addParameter("freq", this.freq);
    addParameter("phase", this.phase);
    addParameter("gain", this.gain);
    addParameter("offset", this.offset);
    setDescription("Applies a scaled unary math function to a modulation input");
  }

  public double compute(double input) {
    final Function function = this.function.getEnum();
    final double basis = function.domain * (this.freq.getValue() * input + this.phase.getValue());
    final double value = function.apply.apply(basis, this.shape.getValuef()) * this.gain.getValue() + this.offset.getValue();
    if (!Double.isFinite(value)) {
      return 0;
    }
    return LXUtils.constrain(value, 0, 1);
  }

  @Override
  protected double computeValue(double deltaMs) {
    return compute(this.input.getValue());
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException("Trigonometry value comes from its input and function; it cannot be set directly");
  }

}
