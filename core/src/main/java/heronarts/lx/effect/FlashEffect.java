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
import heronarts.lx.LXEffect;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXParameter;

@LXCategory(LXCategory.FORM)
public class FlashEffect extends LXEffect {

  private final CompoundParameter sat =
    new CompoundParameter("Saturation", 0)
    .setDescription("Sets the color saturation level of the flash");

  public final CompoundParameter attack =
    new CompoundParameter("Attack", 100, 1000)
    .setDescription("Sets the attack time of the flash");

  public final CompoundParameter decay =
    new CompoundParameter("Decay", 1500, 3000)
    .setDescription("Sets the decay time of the flash");

  public final BoundedParameter intensity =
    new BoundedParameter("Intensity", 1)
    .setDescription("Sets the intensity level of the flash");

  public FlashEffect(LX lx) {
    super(lx);
    addParameter("attack", this.attack);
    addParameter("decay", this.decay);
    addParameter("intensity", this.intensity);
    addParameter("saturation", this.sat);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.attack) {
      this.enabledDampingAttack.setValue(p.getValue());
    } else if (p == this.decay) {
      this.enabledDampingRelease.setValue(p.getValue());
    }
  }

  @Override
  protected void run(double deltaMs, double amount) {
    float flashValue = (float) (amount * this.intensity.getValuef());
    double satValue = this.sat.getValue() * 100.;
    double hueValue = this.lx.palette.getHue();
    if (flashValue > 0) {
      for (int i = 0; i < this.colors.length; ++i) {
        this.colors[i] = LXColor.lerp(this.colors[i], LXColor.hsb(hueValue, satValue, 100.), flashValue);
      }
    }
  }
}
