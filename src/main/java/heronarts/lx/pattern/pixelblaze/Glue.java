/**
 * Copyright 2022- Ben Hencke
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
 * @author Ben Hencke <hencke@gmail.com>
 */

package heronarts.lx.pattern.pixelblaze;

import heronarts.lx.color.LXColor;
import heronarts.lx.utils.LXUtils;

public class Glue {

  public static int hsv(float h, float s, float v) {
    // NOTE(mcslee): Unclear this is necessary?
    // LXColor.hsb() handles negative/wrapped h values
    h = h % 1f;
    if (h < 1)
      h += 1f;

    s = LXUtils.constrainf(s, 0, 1);
    v = LXUtils.constrainf(v, 0, 1);
    return LXColor.hsb(360.0f * (h % 1), 100.0f * s, 100.0f * v);
  }

  public static int rgb(float r, float g, float b) {
    r = LXUtils.constrainf(r, 0, 1);
    g = LXUtils.constrainf(g, 0, 1);
    b = LXUtils.constrainf(b, 0, 1);
    return LXColor.rgb((int) (r*255), (int) (g*255), (int) (b*255));
  }

  public static int rgba(float r, float g, float b, float a) {
    r = LXUtils.constrainf(r, 0, 1);
    g = LXUtils.constrainf(g, 0, 1);
    b = LXUtils.constrainf(b, 0, 1);
    a = LXUtils.constrainf(a, 0, 1);
    return LXColor.rgba((int) (r*255), (int) (g*255), (int) (b*255), (int) (a*255));
  }

  public static int setAlpha(int color, float a) {
    return LXUtils.constrain((int) (a * 255f), 0, 255) << LXColor.ALPHA_SHIFT | (color & LXColor.RGB_MASK);
  }
}
