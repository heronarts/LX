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
import heronarts.lx.LXEffect;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXParameter;

public class DesaturationEffect extends LXEffect {

  private final CompoundParameter attack =
    new CompoundParameter("Attack", 100, 0, 1000)
    .setDescription("Sets the attack time of the desaturation");

  private final CompoundParameter decay =
    new CompoundParameter("Decay", 100, 0, 1000)
    .setDescription("Sets the decay time of the desaturation");

  private final CompoundParameter amount =
    new CompoundParameter("Amount", 1.)
    .setDescription("Sets the amount of desaturation to apply");


  public DesaturationEffect(LX lx) {
    super(lx);
    addParameter("amount", this.amount);
    addParameter("attack", this.attack);
    addParameter("decay", this.decay);
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
    double d = amount * this.amount.getValue();
    if (d > 0) {
      d = 1-d;
      for (int i = 0; i < colors.length; ++i) {
        this.colors[i] = LXColor.hsb(
          LXColor.h(this.colors[i]),
          Math.max(0, LXColor.s(colors[i]) * d),
          LXColor.b(colors[i])
        );
      }
    }
  }

}
