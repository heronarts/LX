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
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;

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
  public void blend(int[] dst, int[] src, double alpha, int[] output, LXModel model) {
    // Multiply the src alpha only by half!
    final int srcAlpha = (int) (alpha * LXColor.BLEND_ALPHA_HALF);
    final int dstAlpha = LXColor.BLEND_ALPHA_FULL - srcAlpha;
    for (LXPoint p : model.points) {
      final int i = p.index;
      output[i] = LXColor.add(LXColor.add(LXColor.CLEAR, dst[i], dstAlpha), src[i], srcAlpha);
    }
  }
}
