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

public class ScreenBlend extends LXBlend {

  public ScreenBlend(LX lx) {
    super(lx);
  }

  @Override
  public void blend(int[] dst, int[] src, double alpha, int[] output) {
    screen(dst, src, alpha, output);
  }

  public static void screen(int[] dst, int[] src, double alpha, int[] output) {
    int alphaAdjust = (int) (alpha * 0x100);
    for (int i = 0; i < src.length; ++i) {
      int a = (((src[i] >>> ALPHA_SHIFT) * alphaAdjust) >> 8) & 0xff;

      int srcAlpha = a + (a >= 0x7F ? 1 : 0);
      int dstAlpha = 0x100 - srcAlpha;

      int dstRb = dst[i] & RB_MASK;
      int dstGn = dst[i] & G_MASK;
      int srcGn = src[i] & G_MASK;
      int dstR = (dst[i] & R_MASK) >> R_SHIFT;
      int dstB = dst[i] & B_MASK;

      int rbSub = (
          (src[i] & R_MASK) * (dstR + 1) |
          (src[i] & B_MASK) * (dstB + 1)
        ) >>> 8 & RB_MASK;
      int gnSub = srcGn * (dstGn + 0x100) >> 16 & G_MASK;

      output[i] = min((dst[i] >>> ALPHA_SHIFT) + a, 0xff) << ALPHA_SHIFT |
        (dstRb * dstAlpha + (dstRb + (src[i] & RB_MASK) - rbSub) * srcAlpha) >>> 8 & RB_MASK |
        (dstGn * dstAlpha + (dstGn + srcGn - gnSub) * srcAlpha) >>> 8 & G_MASK;
    }
  }
}
