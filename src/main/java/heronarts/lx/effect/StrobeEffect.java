/**
 * Copyright 2017- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.effect;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.Tempo;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.modulator.LXWaveshape;
import heronarts.lx.modulator.SawLFO;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.ObjectParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.CORE)
public class StrobeEffect extends LXEffect {

  public final ObjectParameter<LXWaveshape> waveshape =
    new ObjectParameter<LXWaveshape>("Waveshape", new LXWaveshape[] {
      LXWaveshape.SIN,
      LXWaveshape.TRI,
      LXWaveshape.SQUARE,
      LXWaveshape.UP,
      LXWaveshape.DOWN
    });

  public final BoundedParameter maxFrequency =
    new BoundedParameter("Max Freq", 5, 1, 30)
    .setDescription("Maximum strobing frequency")
    .setUnits(LXParameter.Units.HERTZ);

  public final BoundedParameter minFrequency =
    new BoundedParameter("Min Freq", .5, .1, 1)
    .setDescription("Minimium strobing frequency")
    .setUnits(LXParameter.Units.HERTZ);

  public final CompoundParameter speed =
    new CompoundParameter("Speed", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setExponent(2)
    .setDescription("Speed of the strobe effect");

  public final CompoundParameter depth =
    new CompoundParameter("Depth", 0.5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Depth of the strobe effect");

  public final CompoundParameter bias =
    new CompoundParameter("Bias", 0, -1, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription("Bias of the strobe effect");

  public final BooleanParameter tempoSync =
    new BooleanParameter("Sync", false)
    .setDescription("Whether to sync the tempo to a clock division");

  public final EnumParameter<Tempo.Division> tempoDivision =
    new EnumParameter<Tempo.Division>("Division", Tempo.Division.QUARTER)
    .setDescription("Which tempo division to use when in sync mode");

  public final BoundedParameter tempoPhaseOffset =
    new BoundedParameter("Phase Offset", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Shifts the phase of the strobe LFO relative to tempo");

  private final SawLFO basis = startModulator(new SawLFO(0, 1, new FunctionalParameter() {
    @Override
    public double getValue() {
      return 1000 / LXUtils.lerp(minFrequency.getValue(), maxFrequency.getValue(), speed.getValue());
  }}));

  public StrobeEffect(LX lx) {
    super(lx);
    addParameter("speed", this.speed);
    addParameter("depth", this.depth);
    addParameter("bias", this.bias);
    addParameter("waveshape", this.waveshape);
    addParameter("tempoSync", this.tempoSync);
    addParameter("tempoDivision", this.tempoDivision);
    addParameter("tempoPhaseOffset", this.tempoPhaseOffset);
    addParameter("minFrequency", this.minFrequency);
    addParameter("maxFrequency", this.maxFrequency);
  }

  @Override
  protected void onEnable() {
    this.basis.setBasis(0).start();
  }

  public float compute(double basis, boolean useBaseValue) {
    double strobe = this.waveshape.getObject().compute(basis);
    double bias = useBaseValue ? this.bias.getBaseValue() : this.bias.getValue();
    double expPower = (bias >= 0) ? (1 + 3*bias) : (1 / (1 - 3*bias));
    if (expPower != 1) {
      strobe = Math.pow(strobe, expPower);
    }
    return LXUtils.lerpf(1, (float) strobe, useBaseValue ? this.depth.getBaseValuef() : this.depth.getValuef());
  }

  private double getTempoBasis() {
    double basis = this.lx.engine.tempo.getBasis(this.tempoDivision.getEnum());
    return (basis + this.tempoPhaseOffset.getValue()) % 1.;
  }

  @Override
  public void run(double deltaMs, double enabledAmount) {
    float amt = (float) enabledAmount * this.depth.getValuef();
    if (amt > 0) {
      double strobeBasis = this.tempoSync.isOn() ? getTempoBasis() : this.basis.getValue();
      float strobe = LXUtils.lerpf(1, compute(strobeBasis, false), (float) enabledAmount);

      if (strobe < 1) {
        if (strobe == 0) {
          setColors(LXColor.BLACK);
        } else {
          int src = LXColor.gray(100 * strobe);
          for (LXPoint p : model.points) {
            this.colors[p.index] = LXColor.multiply(this.colors[p.index], src, LXColor.BLEND_ALPHA_FULL);
          }
        }
      }
    }
  }
}
