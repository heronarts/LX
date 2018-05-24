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
import heronarts.lx.ModelBuffer;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.CompoundParameter;

@LXCategory(LXCategory.TEXTURE)
public class BlurEffect extends LXEffect {

  public final CompoundParameter amount =
    new CompoundParameter("Amount", 0)
    .setDescription("Sets the amount of blur to apply");

  private final ModelBuffer blurBuffer;

  public BlurEffect(LX lx) {
    super(lx);
    this.blurBuffer = new ModelBuffer(lx);
    int[] blurArray = blurBuffer.getArray();
    for (int i = 0; i < blurArray.length; ++i) {
      blurArray[i] = LXColor.BLACK;
    }
    addParameter("amount", this.amount);
  }

  @Override
  protected void onEnable() {
    int[] blurArray = this.blurBuffer.getArray();
    for (int i = 0; i < blurArray.length; ++i) {
      blurArray[i] = LXColor.BLACK;
    }
  }

  @Override
  public void run(double deltaMs, double amount) {
    float blurf = (float) (amount * this.amount.getValuef());
    if (blurf > 0) {
      blurf = 1 - (1 - blurf) * (1 - blurf) * (1 - blurf);
      int[] blurArray = this.blurBuffer.getArray();

      // Screen blend the colors onto the blur array
      for (int i = 0; i < blurArray.length; ++i) {
        blurArray[i] = LXColor.screen(blurArray[i], this.colors[i], 0x100);
      }

      // Lerp onto the colors based upon amount
      int blurAlpha = (int) (0x100 * blurf);
      for (int i = 0; i < blurArray.length; ++i) {
        this.colors[i] = LXColor.lerp(this.colors[i], blurArray[i], blurAlpha);
      }

      // Copy colors into blur array for next frame
      System.arraycopy(this.colors, 0, blurArray, 0, this.colors.length);
    }

  }
}
