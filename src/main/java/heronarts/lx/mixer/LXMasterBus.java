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

package heronarts.lx.mixer;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClip;
import heronarts.lx.clip.LXMasterClip;

/**
 * Represents the master channel. Doesn't do anything special
 * that a normal bus does not.
 */
public class LXMasterBus extends LXBus {
  public LXMasterBus(LX lx) {
    super(lx, "Master");
  }

  @Override
  public int getIndex() {
    return lx.engine.mixer.channels.size();
  }

  @Override
  public String getPath() {
    return "master";
  }

  @Override
  protected LXClip constructClip(int index) {
    return new LXMasterClip(this.lx, index);
  }
}
