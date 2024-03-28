/**
 * Copyright 2024- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.modulator;

import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;

public abstract class LXMacroModulator extends LXModulator implements LXOscComponent {

  public final BooleanParameter showEight =
    new BooleanParameter("Show Eight")
    .setDescription("Eight controls mode");

  protected LXMacroModulator(String label) {
    super(label);
    addInternalParameter("showEight", this.showEight);
  }

}
