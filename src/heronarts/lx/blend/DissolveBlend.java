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

package heronarts.lx.blend;

import heronarts.lx.LX;
import heronarts.lx.color.LXColor;

/**
 * The dissolve blend is a special blend used in the crossfader. It is a normal linear
 * blend except that full alpha on this blend represents an averaging of the two colors,
 * and it also disregards the alpha channel of the individual pixels.
 */
public class DissolveBlend extends LXBlend {

  public DissolveBlend(LX lx) {
    super(lx);
  }

  @Override
  public void blend(int[] dst, int[] src, double alpha, int[] output) {
    // Multiply the src alpha only by half!
    int srcAlpha = (int) (alpha * 0x80);
    for (int i = 0; i < src.length; ++i) {
      int dstAlpha = 0x100 - srcAlpha;
      output[i] = 0xff << LXColor.ALPHA_SHIFT |
          ((dst[i] & LXColor.RB_MASK) * dstAlpha + (src[i] & LXColor.RB_MASK) * srcAlpha) >>> 8 & LXColor.RB_MASK |
          ((dst[i] & LXColor.G_MASK) * dstAlpha + (src[i] & LXColor.G_MASK) * srcAlpha) >>> 8 & LXColor.G_MASK;
    }
  }
}
