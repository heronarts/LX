/**
 * Copyright 2022- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.transform;

import heronarts.lx.parameter.LXParameter;

public class LXParameterizedMatrix extends LXMatrix {

  public interface UpdateFunction {
    public void updateMatrix(LXMatrix matrix);
  }

  private final LXParameter.MultiMonitor monitor = new LXParameter.MultiMonitor();

  private boolean dirty = true;

  public LXParameterizedMatrix addParameter(LXParameter parameter) {
    this.monitor.addParameter(parameter);
    return this;
  }

  public void update(UpdateFunction update) {
    if (this.monitor.changed()) {
      this.dirty = true;
    }
    if (this.dirty) {
      update.updateMatrix(identity());
      this.dirty = false;
    }
  }

}
