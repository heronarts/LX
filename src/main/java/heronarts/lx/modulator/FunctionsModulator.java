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
import heronarts.lx.parameter.BoundedParameter;
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
@LXModulator.Global("Functions")
@LXModulator.Device("Functions")
@LXCategory(LXCategory.CORE)
public class FunctionsModulator extends LXModulator implements LXNormalizedParameter, LXOscComponent {

  public interface Apply {
    public double apply(double value, double shape);
  }

  private static final double INV_HALF_PI = 2 / Math.PI;
  private static final double INV_E_MINUS_1 = 1 / (Math.E - 1);

  /** Unary functions available to the operator. */
  public enum Function {
    SIN("Sin", false, true, (v,s) -> Math.sin(LX.TWO_PI * v)),
    COS("Cos", false, true, (v,s) -> Math.cos(LX.TWO_PI * v)),
    TAN("Tan", true, true, (v,s) -> 0.1 * Math.tan(LX.HALF_PI * v)),
    TANH("Tanh", true, true, (v,s) -> Math.tanh(v)),
    TANH_POS("Tanh+", false, false, (v,s) -> Math.tanh(v)),
    ASIN("Asin", true, true, (v,s) -> INV_HALF_PI * Math.asin(v)),
    ACOS("Acos", true, true, (v,s) -> INV_HALF_PI * Math.acos(v) - 1),
    ATAN("Atan", true, true, (v,s) -> INV_HALF_PI * Math.atan(v)),
    TRI("Tri", false, true, (v,s) -> 1 - 2*Math.abs(2*(v-Math.floor(v)) - 1)),
    TRI_POS("Tri+", false, false, (v,s) -> 1 - Math.abs(2*(v-Math.floor(v)) - 1)),
    SAW("Saw", false, true, (v,s) -> -1 + 2 * (v-Math.floor(v))),
    SAW_POS("Saw+", false, false, (v,s) -> v-Math.floor(v)),
    SQRT("Sqrt", false, false, (v,s) -> Math.sqrt(v)),
    EXP("Exp", false, false, (v,s) -> INV_E_MINUS_1*(Math.exp(v)-1)),
    LOG("Log", false, false, (v,s) -> Math.log(1+v)),
    INV("Inv", false, false, (v,s) -> 1/(1+v*s)),
    POW("Pow(y)", false, false, (v,s) -> Math.pow(v,s)),
    EXPY("Exp(y)", false, false, (v,s) -> (Math.pow(s,v) - 1) / (s-1));

    private final String label;
    public final boolean bidirectional;
    public final boolean bipolar;

    private final Apply apply;

    Function(String label, boolean bidirectional, boolean bipolar, Apply apply) {
      this.label = label;
      this.bidirectional = bidirectional;
      this.bipolar = bipolar;
      this.apply = apply;
    }

    public boolean hasPhase() {
      return switch (this) {
        case SIN, COS, TAN, SAW, SAW_POS, TRI, TRI_POS -> true;
        default -> false;
      };
    }

    public boolean hasShape() {
      return switch (this) {
        case POW, EXPY, INV -> true;
        default -> false;
      };
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final EnumParameter<Function> function =
    new EnumParameter<Function>("Function", Function.SIN)
    .setDescription("Unary function applied to input * domain + phase");

  public final CompoundParameter input =
    new CompoundParameter("Input", 0)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setWrappable(true)
    .setDescription("Input value, map a modulation source here");

  public final CompoundParameter shape =
    new CompoundParameter("Shape", .5)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Shaping applied to curves");

  public final BoundedParameter shapeRange =
    new BoundedParameter("Shape Range", 4, 0, 100)
    .setDescription("Maximum shape factor");

  public final CompoundParameter domain =
    new CompoundParameter("Domain", 1, -1, 1)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Angular input multiplier in radians");

  public final BoundedParameter domainRange =
    new BoundedParameter("Domain Range", 1, 0, 8)
    .setDescription("Maximum domain factor");

  public final CompoundParameter phase =
    new CompoundParameter("Phase", 0, -1, 1)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Angular offset added before applying the function");

  public final CompoundParameter gain =
    new CompoundParameter("Gain", 1, -1, 1)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setDescription("Multiplier applied to the function result");

  public final BoundedParameter gainRange =
    new BoundedParameter("Gain Range", 1, 0, 4)
    .setDescription("Maximum gain factor");

  public final CompoundParameter offset =
    new CompoundParameter("Offset", 0, -1, 1)
    .setPolarity(LXParameter.Polarity.BIPOLAR)
    .setUnits(LXParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Value added after applying gain");

  public final BoundedParameter offsetRange =
    new BoundedParameter("Offset Range", 1, 0, 2)
    .setDescription("Maximum offset factor");

  public FunctionsModulator() {
    super("Functions");
    addParameter("function", this.function);
    addParameter("input", this.input);
    addParameter("shape", this.shape);
    addParameter("shapeRange", this.shapeRange);
    addParameter("domain", this.domain);
    addParameter("domainRange", this.domainRange);
    addParameter("phase", this.phase);
    addParameter("gain", this.gain);
    addParameter("gainRange", this.gainRange);
    addParameter("offset", this.offset);
    addParameter("offsetRange", this.offsetRange);
    setDescription("Applies a scaled math function to a modulation input");
  }

  public double compute(double input) {
    final Function function = this.function.getEnum();
    if (function.bidirectional) {
      input = LXUtils.lerp(-1, 1, input);
    }
    final double fgain = function.bipolar ? .5 : 1;
    final double foffset = function.bipolar ? .5 : 0;
    double basis = this.domain.getValue() * this.domainRange.getValue() * input;
    if (function.hasPhase()) {
      basis += this.phase.getValue();
    }
    final double gain = this.gain.getValue() * this.gainRange.getValue();
    final double offset = this.offset.getValue() * this.offsetRange.getValue();
    final double value = function.apply.apply(basis, this.shape.getValue() * this.shapeRange.getValue()) * fgain * gain + foffset + offset;
    if (!Double.isFinite(value)) {
      return (value == Double.POSITIVE_INFINITY) ? 1 : 0;
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
