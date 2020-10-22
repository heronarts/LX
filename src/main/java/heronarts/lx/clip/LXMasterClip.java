/**
 * Copyright 2017- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.clip;

import heronarts.lx.LX;

public class LXMasterClip extends LXClip {
  public LXMasterClip(LX lx, int index) {
    super(lx, lx.engine.mixer.masterBus, index);
    lx.engine.mixer.crossfader.addListener(this.parameterRecorder);
    registerComponent(lx.engine.palette);
  }

  @Override
  public void dispose() {
    lx.engine.mixer.crossfader.removeListener(this.parameterRecorder);
    super.dispose();
  }
}
