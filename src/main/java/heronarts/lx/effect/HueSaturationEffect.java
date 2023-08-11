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

package heronarts.lx.effect;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXComponentName;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.COLOR)
@LXComponentName("Hue + Saturation")
public class HueSaturationEffect extends LXEffect {

  public final CompoundParameter hue =
    new CompoundParameter("Hue", 0, -360, 360)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription("Sets the amount of hue shift to apply");

  public final CompoundParameter saturation =
    new CompoundParameter("Saturation", 0, -100, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription("Sets the amount to increase or decrease saturation");

  public final CompoundParameter brightness =
    new CompoundParameter("Brightness", 0, -100, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription("Sets the amount to increase or decrease brightness");

  public HueSaturationEffect(LX lx) {
    super(lx);
    addParameter("hue", this.hue);
    addParameter("saturation", this.saturation);
    addParameter("brightness", this.brightness);
  }

  @Override
  protected void run(double deltaMs, double amount) {
    float hue = this.hue.getValuef();
    float saturation = this.saturation.getValuef();
    float brightness = this.brightness.getValuef();
    float amountf = (float) amount;

    if (amount < 1) {
      for (LXPoint p : model.points) {
        int i = p.index;
        int c = colors[i];
        float h = LXColor.h(c);
        float s = LXColor.s(c);
        float b = LXColor.b(c);
        colors[i] = LXColor.hsb(
          LXUtils.lerpf(h, h + hue, amountf),
          LXUtils.lerpf(s, LXUtils.clampf(s + saturation, 0, 100), amountf),
          LXUtils.lerpf(b, LXUtils.clampf(b + brightness, 0, 100), amountf)
        );
      }
    } else {
      for (LXPoint p : model.points) {
        int i = p.index;
        int c = colors[i];
        float h = LXColor.h(c);
        float s = LXColor.s(c);
        float b = LXColor.b(c);
        colors[i] = LXColor.hsb(
          h + hue,
          LXUtils.clampf(s + saturation, 0, 100),
          LXUtils.clampf(b + brightness, 0, 100)
        );
      }
    }
  }

}
