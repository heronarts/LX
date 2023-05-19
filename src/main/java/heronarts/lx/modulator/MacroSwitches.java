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
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.StringParameter;

@LXModulator.Global("Switches")
@LXCategory(LXCategory.MACRO)
public class MacroSwitches extends LXModulator implements LXOscComponent, LXTriggerSource {

  public final BooleanParameter macro1 =
    new BooleanParameter("B1")
    .setDescription("Macro control switch");

  public final BooleanParameter macro2 =
    new BooleanParameter("B2")
    .setDescription("Macro control switch");

  public final BooleanParameter macro3 =
    new BooleanParameter("B3")
    .setDescription("Macro control switch");

  public final BooleanParameter macro4 =
    new BooleanParameter("B4")
    .setDescription("Macro control switch");

  public final BooleanParameter macro5 =
    new BooleanParameter("B5")
    .setDescription("Macro control switch");

  public final StringParameter label1 =
    new StringParameter("Label-1", "-")
    .setDescription("Label for switch 1");

  public final StringParameter label2 =
    new StringParameter("Label-2", "-")
    .setDescription("Label for switch 2");

  public final StringParameter label3 =
    new StringParameter("Label-3", "-")
    .setDescription("Label for switch 3");

  public final StringParameter label4 =
    new StringParameter("Label-4", "-")
    .setDescription("Label for switch 4");

  public final StringParameter label5 =
    new StringParameter("Label-5", "-")
    .setDescription("Label for switch 5");

  public final BooleanParameter[] switches = {
    macro1, macro2, macro3, macro4, macro5
  };

  public final StringParameter[] labels = {
    label1, label2, label3, label4, label5
  };

  public MacroSwitches() {
    this("Switches");
  }

  public MacroSwitches(String label) {
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
    setMappingSource(false);
  }

  @Override
  protected double computeValue(double deltaMs) {
    // Not relevant
    return 0;
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return null;
  }

}
