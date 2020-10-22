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

package heronarts.lx.color;

import heronarts.lx.parameter.DiscreteParameter;

/**
 * Utility class with a discrete selection of colors that are shown by a UI picker
 * device. Keeps things looking sane in the UI.
 */
public class DiscreteColorParameter extends DiscreteParameter {

  public static final int NUM_PRIMARY_COLORS = 8;

//  Colors are sampled from:
//  for (int i = 0; i < 12; ++i) {
//    print("0x" + Integer.toHexString(color(i * 360 / 12, 100, 100)) + ", ");
//  }
//  for (int i = 0; i < 12; ++i) {
//    print("0x" + Integer.toHexString(color(i * 360 / 12, 50, 100)) + ", ");
//  }

  public static final int[] COLORS =
  {
    0xffff0000, 0xffff7f00, 0xffffff00, 0xff00ff00, 0xff00ffff, 0xff007fff, 0xff7f00ff, 0xffff00ff,
    0xffff7f7f, 0xffffbf7f, 0xffffff7f, 0xff7fff7f, 0xff7fffff, 0xff7fbfff, 0xffbf7fff, 0xffff7fff,
    0xffffffff, 0xffcccccc, 0xff999999, 0xfffff8dc, 0xffffebcd, 0xffffdead, 0xffbc8f8f, 0xffdaa520
  };

  public DiscreteColorParameter(String description) {
    super(description, 0, COLORS.length);
  }

  public int getColor() {
    return COLORS[getValuei()];
  }
}
