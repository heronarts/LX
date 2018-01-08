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
 * ##library.name##
 * ##library.sentence##
 * ##library.url##
 *
 * @author      ##author##
 * @modified    ##date##
 * @version     ##library.prettyVersion## (##library.version##)
 */

package heronarts.lx.modulator;

import heronarts.lx.parameter.BoundedParameter;

public class MacroKnobs extends LXModulator {

  public final BoundedParameter macro1 = new BoundedParameter("M1")
    .setDescription("Macro control parameter");

  public final BoundedParameter macro2 = new BoundedParameter("M2")
    .setDescription("Macro control parameter");

  public final BoundedParameter macro3 = new BoundedParameter("M3")
    .setDescription("Macro control parameter");

  public final BoundedParameter macro4 = new BoundedParameter("M4")
    .setDescription("Macro control parameter");

  public final BoundedParameter macro5 = new BoundedParameter("M5")
    .setDescription("Macro control parameter");

  public MacroKnobs() {
    this("MACRO");
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
