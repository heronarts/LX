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
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.CompoundParameter;

@LXCategory(LXCategory.COLOR)
public class InvertEffect extends LXEffect {

  public final CompoundParameter amount =
    new CompoundParameter("Amount", 1)
    .setDescription("Amount of inversion to apply");

  public InvertEffect(LX lx) {
    super(lx);
    addParameter("amount", this.amount);
  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    double lerp = enabledAmount * this.amount.getValue();
    if (lerp > 0) {
      for (int i = 0; i < this.colors.length; ++i) {
        this.colors[i] = LXColor.lerp(this.colors[i], LXColor.subtract(0xffffffff, this.colors[i]), lerp);
      }
    }
  }
}
