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

import heronarts.lx.LXCategory;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.StringParameter;

@LXModulator.Global("Knobs")
@LXModulator.Device("Knobs")
@LXCategory(LXCategory.MACRO)
public class MacroKnobs extends LXMacroModulator {

  private static CompoundParameter macro(int num) {
    return new CompoundParameter("K" + num)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Macro control parameter " + num);
  }

  private static StringParameter label(int num) {
    return new StringParameter("Label-" + num, "-")
      .setDescription("Label for knob " + num);
  }

  public final CompoundParameter macro1 = macro(1);
  public final CompoundParameter macro2 = macro(2);
  public final CompoundParameter macro3 = macro(3);
  public final CompoundParameter macro4 = macro(4);
  public final CompoundParameter macro5 = macro(5);
  public final CompoundParameter macro6 = macro(6);
  public final CompoundParameter macro7 = macro(7);
  public final CompoundParameter macro8 = macro(8);

  public final StringParameter label1 = label(1);
  public final StringParameter label2 = label(2);
  public final StringParameter label3 = label(3);
  public final StringParameter label4 = label(4);
  public final StringParameter label5 = label(5);
  public final StringParameter label6 = label(6);
  public final StringParameter label7 = label(7);
  public final StringParameter label8 = label(8);

  public final CompoundParameter[] knobs = {
    macro1, macro2, macro3, macro4, macro5, macro6, macro7, macro8
  };

  public final StringParameter[] labels = {
    label1, label2, label3, label4, label5, label6, label7, label8
  };

  public MacroKnobs() {
    this("Knobs");
  }

  public MacroKnobs(String label) {
    super(label);
    addParameter("macro1", this.macro1);
    addParameter("macro2", this.macro2);
    addParameter("macro3", this.macro3);
    addParameter("macro4", this.macro4);
    addParameter("macro5", this.macro5);
    addParameter("macro6", this.macro6);
    addParameter("macro7", this.macro7);
    addParameter("macro8", this.macro8);
    addParameter("label1", this.label1);
    addParameter("label2", this.label2);
    addParameter("label3", this.label3);
    addParameter("label4", this.label4);
    addParameter("label5", this.label5);
    addParameter("label6", this.label6);
    addParameter("label7", this.label7);
    addParameter("label8", this.label8);
  }

  @Override
  protected double computeValue(double deltaMs) {
    // Not relevant
    return 0;
  }

}
