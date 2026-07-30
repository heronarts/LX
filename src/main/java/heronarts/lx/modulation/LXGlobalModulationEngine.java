/**
 * Copyright 2026- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.modulation;

import heronarts.lx.LX;
import heronarts.lx.clip.LXClipBus;
import heronarts.lx.parameter.BooleanParameter;

public class LXGlobalModulationEngine extends LXModulationEngine implements LXClipBus {

  public final BooleanParameter arm =
    new BooleanParameter("Arm", false)
    .setDescription("Arms the modulation engine for composition recording");

  public LXGlobalModulationEngine(LX lx) {
    super(lx);
    addParameter("arm", this.arm);
  }

  @Override
  public BooleanParameter getArmParameter() {
    return this.arm;
  }

}
