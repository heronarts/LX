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
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.StringParameter;

@LXModulator.Global("Knobs")
@LXCategory(LXCategory.MACRO)
public class MacroKnobs extends LXModulator implements LXOscComponent {

  public final CompoundParameter macro1 =
    new CompoundParameter("K1")
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Macro control parameter");

  public final CompoundParameter macro2 =
    new CompoundParameter("K2")
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Macro control parameter");

  public final CompoundParameter macro3 =
    new CompoundParameter("K3")
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Macro control parameter");

  public final CompoundParameter macro4 =
    new CompoundParameter("K4")
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Macro control parameter");

  public final CompoundParameter macro5 =
    new CompoundParameter("K5")
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Macro control parameter");

  public final StringParameter label1 =
    new StringParameter("Label-1", "-")
    .setDescription("Label for knob 1");

  public final StringParameter label2 =
    new StringParameter("Label-2", "-")
    .setDescription("Label for knob 2");

  public final StringParameter label3 =
    new StringParameter("Label-3", "-")
    .setDescription("Label for knob 3");

  public final StringParameter label4 =
    new StringParameter("Label-4", "-")
    .setDescription("Label for knob 4");

  public final StringParameter label5 =
    new StringParameter("Label-5", "-")
    .setDescription("Label for knob 5");

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
    addParameter("label1", this.label1);
    addParameter("label2", this.label2);
    addParameter("label3", this.label3);
    addParameter("label4", this.label4);
    addParameter("label5", this.label5);
  }

  @Override
  protected double computeValue(double deltaMs) {
    // Not relevant
    return 0;
  }

}
