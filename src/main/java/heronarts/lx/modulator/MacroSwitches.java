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
import heronarts.lx.midi.LXMidiListener;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;

@LXModulator.Global("Switches")
@LXModulator.Device("Switches")
@LXCategory(LXCategory.MACRO)
public class MacroSwitches extends LXMacroModulator implements LXTriggerSource, LXMidiListener {

  private static BooleanParameter macro(int num) {
    return new BooleanParameter("B" + num)
      .setDescription("Macro control switch " + num);
  }

  private static StringParameter label(int num) {
    return new StringParameter("Label-" + num, DEFAULT_LABEL)
    .setDescription("Label for switch " + num);
  }

  public final BooleanParameter macro1 = macro(1);
  public final BooleanParameter macro2 = macro(2);
  public final BooleanParameter macro3 = macro(3);
  public final BooleanParameter macro4 = macro(4);
  public final BooleanParameter macro5 = macro(5);
  public final BooleanParameter macro6 = macro(6);
  public final BooleanParameter macro7 = macro(7);
  public final BooleanParameter macro8 = macro(8);

  public final StringParameter label1 = label(1);
  public final StringParameter label2 = label(2);
  public final StringParameter label3 = label(3);
  public final StringParameter label4 = label(4);
  public final StringParameter label5 = label(5);
  public final StringParameter label6 = label(6);
  public final StringParameter label7 = label(7);
  public final StringParameter label8 = label(8);

  public final BooleanParameter exclusive =
    new BooleanParameter("Exclusive", false)
    .setDescription("Determines whether only one switch may be active at a time");

  public final BooleanParameter[] switches = {
    macro1, macro2, macro3, macro4, macro5, macro6, macro7, macro8
  };

  public final StringParameter[] labels = {
    label1, label2, label3, label4, label5, label6, label7, label8
  };

  public MacroSwitches() {
    this("Switches");
  }

  public MacroSwitches(String label) {
    super(label);
    this.midiFilter.enabled.setValue(false);
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
    addParameter("exclusive", this.exclusive);
    setMappingSource(false);
  }

  private BooleanParameter getSwitch(LXParameter p) {
    for (BooleanParameter s : this.switches) {
      if (s == p) {
        return s;
      }
    }
    return null;
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (this.exclusive.isOn()) {
      final BooleanParameter s = getSwitch(p);
      if ((s != null) && s.isOn()) {
        for (BooleanParameter other : this.switches) {
          if (other != s) {
            other.setValue(false);
          }
        }
      }
    }
  }

  @Override
  protected double computeValue(double deltaMs) {
    // Not relevant
    return 0;
  }

  @Override
  public LXParameter[] getMacroParameters() {
    return this.switches;
  }

  @Override
  public StringParameter[] getMacroLabels() {
    return this.labels;
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return null;
  }

  @Override
  public void noteOnReceived(MidiNoteOn note) {
    final int idx = note.getPitch() - this.midiFilter.minNote.getValuei();
    if (idx < this.switches.length) {
      this.switches[idx].toggle();
    }
  }

}
