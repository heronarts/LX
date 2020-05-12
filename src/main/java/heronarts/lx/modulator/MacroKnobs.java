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

package heronarts.lx.modulator;

import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.CompoundParameter;

public class MacroKnobs extends LXModulator implements LXOscComponent {

  public final CompoundParameter macro1 = new CompoundParameter("M1")
    .setDescription("Macro control parameter");

  public final CompoundParameter macro2 = new CompoundParameter("M2")
    .setDescription("Macro control parameter");

  public final CompoundParameter macro3 = new CompoundParameter("M3")
    .setDescription("Macro control parameter");

  public final CompoundParameter macro4 = new CompoundParameter("M4")
    .setDescription("Macro control parameter");

  public final CompoundParameter macro5 = new CompoundParameter("M5")
    .setDescription("Macro control parameter");

  public MacroKnobs() {
    this("Macro");
  }

  public MacroKnobs(String label) {
    super(label);
    addParameter("macro1", this.macro1);
    addParameter("macro2", this.macro2);
    addParameter("macro3", this.macro3);
    addParameter("macro4", this.macro4);
    addParameter("macro5", this.macro5);
  }

  @Override
  protected double computeValue(double deltaMs) {
    // Not relevant
    return 0;
  }

}
