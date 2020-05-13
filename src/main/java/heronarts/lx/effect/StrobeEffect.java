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
import heronarts.lx.color.LXColor;
import heronarts.lx.modulator.LXWaveshape;
import heronarts.lx.modulator.SawLFO;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.utils.LXUtils;

public class StrobeEffect extends LXEffect {

  public enum Waveshape {
    SIN,
    TRI,
    UP,
    DOWN,
    SQUARE
  };

  public final EnumParameter<Waveshape> shape =
    new EnumParameter<Waveshape>("Shape", Waveshape.SIN)
    .setDescription("Wave shape of strobing");

  public final CompoundParameter frequency = (CompoundParameter)
    new CompoundParameter("Freq", 1, .05, 10)
    .setExponent(2)
    .setUnits(LXParameter.Units.HERTZ)
    .setDescription("Frequency of strobing");

  public final CompoundParameter depth =
    new CompoundParameter("Depth", 0.5)
    .setDescription("Depth of the strobe effect");

  private final SawLFO basis = (SawLFO) startModulator(new SawLFO(1, 0, new FunctionalParameter() {
    @Override
    public double getValue() {
      return 1000 / frequency.getValue();
  }}));

  public StrobeEffect(LX lx) {
    super(lx);
    addParameter("frequency", this.frequency);
    addParameter("shape", this.shape);
    addParameter("depth", this.depth);
  }

  @Override
  protected void onEnable() {
    this.basis.setBasis(0).start();
  }

  private LXWaveshape getWaveshape() {
    switch (this.shape.getEnum()) {
    case SIN: return LXWaveshape.SIN;
    case TRI: return LXWaveshape.TRI;
    case UP: return LXWaveshape.UP;
    case DOWN: return LXWaveshape.DOWN;
    case SQUARE: return LXWaveshape.SQUARE;
    }
    return LXWaveshape.SIN;
  }

  @Override
  public void run(double deltaMs, double amount) {
    float amt = this.enabledDamped.getValuef() * this.depth.getValuef();
    if (amt > 0) {
      float strobef = this.basis.getValuef();
      strobef = (float) getWaveshape().compute(strobef);
      strobef = LXUtils.lerpf(1, strobef, amt);
      if (strobef < 1) {
        if (strobef == 0) {
          setColors(LXColor.BLACK);
        } else {
          int src = LXColor.gray(100 * strobef);
          for (int i = 0; i < this.colors.length; ++i) {
            this.colors[i] = LXColor.multiply(this.colors[i], src, 0x100);
          }
        }
      }
    }
  }
}