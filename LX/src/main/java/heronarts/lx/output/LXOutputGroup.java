/**
 * Copyright 2015- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.output;

import heronarts.lx.LX;

/**
 * Simple concrete output class which does nothing but group its children.
 */
public class LXOutputGroup extends LXOutput {

  public LXOutputGroup(LX lx) {
    this(lx, "Output");
  }

  public LXOutputGroup(LX lx, String label) {
    super(lx, label);
  }

  @Override
  protected void onSend(int[] colors, byte[] lut) {
    // Do nothing, parent class will send children
  }

}
