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

public class MultiplyBlend extends LXBlend {

  public MultiplyBlend(LX lx) {
    super(lx);
  }

  @Override
  public void blend(int[] dst, int[] src, double alpha, int[] output) {
    multiply(dst, src, alpha, output);
  }

  public static void multiply(int[] dst, int src, double alpha, int[] output) {
    int alphaAdjust = (int) (alpha * 0x100);
    int a = (((src >>> ALPHA_SHIFT) * alphaAdjust) >> 8) & 0xff;
    int srcAlpha = a + (a >= 0x7F ? 1 : 0);
    int dstAlpha = 0x100 - srcAlpha;
    for (int i = 0; i < dst.length; ++i) {
      int dstG = (dst[i] & G_MASK);
      int dstR = (dst[i] & R_MASK) >> R_SHIFT;
      int dstB = (dst[i] & B_MASK);

      int rb = ((src & R_MASK) * (dstR + 1) | (src & B_MASK) * (dstB + 1)) >>> 8 & RB_MASK;
      int g = (src & G_MASK) * (dstG + 0x100) >>> 16 & G_MASK;

      output[i] = min((dst[i] >>> ALPHA_SHIFT) + a, 0xff) << ALPHA_SHIFT |
          ((dst[i] & RB_MASK) * dstAlpha + rb * srcAlpha) >>> 8 & RB_MASK |
          (dstG * dstAlpha + g * srcAlpha) >>> 8 & G_MASK;
    }
  }

  public static void multiply(int[] dst, int[] src, double alpha, int[] output) {
    int alphaAdjust = (int) (alpha * 0x100);
    for (int i = 0; i < src.length; ++i) {
      int a = (((src[i] >>> ALPHA_SHIFT) * alphaAdjust) >> 8) & 0xff;

      int srcAlpha = a + (a >= 0x7F ? 1 : 0);
      int dstAlpha = 0x100 - srcAlpha;

      int dstG = (dst[i] & G_MASK);
      int dstR = (dst[i] & R_MASK) >> R_SHIFT;
      int dstB = (dst[i] & B_MASK);

      int rb = ((src[i] & R_MASK) * (dstR + 1) | (src[i] & B_MASK) * (dstB + 1)) >>> 8 & RB_MASK;
      int g = (src[i] & G_MASK) * (dstG + 0x100) >>> 16 & G_MASK;

      output[i] = min((dst[i] >>> ALPHA_SHIFT) + a, 0xff) << ALPHA_SHIFT |
          ((dst[i] & RB_MASK) * dstAlpha + rb * srcAlpha) >>> 8 & RB_MASK |
          (dstG * dstAlpha + g * srcAlpha) >>> 8 & G_MASK;
    }
  }
}
