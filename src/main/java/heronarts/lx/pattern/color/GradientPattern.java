/**
 * Copyright 2016- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.pattern.color;

import heronarts.lx.LXCategory;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.pattern.LXPattern;

@LXCategory(LXCategory.COLOR)
public class GradientPattern extends LXPattern {

  public final CompoundParameter gradient =
    new CompoundParameter("Gradient", 0, -360, 360)
    .setDescription("Amount of total color gradient")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter spreadX =
    new CompoundParameter("XSprd", 0, -1, 1)
    .setDescription("Sets the amount of hue spread on the X axis")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter spreadY =
    new CompoundParameter("YSprd", 0, -1, 1)
    .setDescription("Sets the amount of hue spread on the Y axis")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter spreadZ =
    new CompoundParameter("ZSprd", 0, -1, 1)
    .setDescription("Sets the amount of hue spread on the Z axis")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter offsetX =
    new CompoundParameter("XOffs", 0, -1, 1)
    .setDescription("Sets the offset of the hue spread point on the X axis")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter offsetY =
    new CompoundParameter("YOffs", 0, -1, 1)
    .setDescription("Sets the offset of the hue spread point on the Y axis")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter offsetZ =
    new CompoundParameter("ZOffs", 0, -1, 1)
    .setDescription("Sets the offset of the hue spread point on the Z axis")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter spreadR =
    new CompoundParameter("RSprd", 0, -1, 1)
    .setDescription("Sets the amount of hue spread in the radius from center")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final BooleanParameter mirror =
    new BooleanParameter("Mirror", true)
    .setDescription("If engaged, the hue spread is mirrored from center");

  public GradientPattern(LX lx) {
    super(lx);
    addParameter("gradient", this.gradient);
    addParameter("spreadX", this.spreadX);
    addParameter("spreadY", this.spreadY);
    addParameter("spreadZ", this.spreadZ);
    addParameter("spreadR", this.spreadR);
    addParameter("offsetX", this.offsetX);
    addParameter("offsetY", this.offsetY);
    addParameter("offsetZ", this.offsetZ);
    addParameter("mirror", this.mirror);
  }

  @Override
  public void run(double deltaMs) {
    int paletteColor = palette.getColor();
    float paletteHue = LXColor.h(paletteColor);
    float paletteSaturation = LXColor.s(paletteColor);
    float gradient = this.gradient.getValuef();
    float spreadX = this.spreadX.getValuef();
    float spreadY = this.spreadY.getValuef();
    float spreadZ = this.spreadZ.getValuef();
    float spreadR = this.spreadR.getValuef();
    float offsetX = this.offsetX.getValuef();
    float offsetY = this.offsetY.getValuef();
    float offsetZ = this.offsetZ.getValuef();
    float rRangeInv = (model.rRange == 0) ? 1 : (1 / model.rRange);
    boolean mirror = this.mirror.isOn();

    for (LXPoint p : model.points) {
      float dx = p.xn - .5f - .5f * offsetX;
      float dy = p.yn - .5f - .5f * offsetY;
      float  dz = p.zn - .5f - .5f * offsetZ;
      float dr = (p.r - model.rMin) * rRangeInv;
      if (mirror) {
        dx = Math.abs(dx);
        dy = Math.abs(dy);
        dz = Math.abs(dz);
      }
      float hue =
        paletteHue +
        gradient * (
          spreadX * dx +
          spreadY * dy +
          spreadZ * dz +
          spreadR * dr
        );

      colors[p.index] = LXColor.hsb(360. + hue, paletteSaturation, 100);
    }
  }
}
