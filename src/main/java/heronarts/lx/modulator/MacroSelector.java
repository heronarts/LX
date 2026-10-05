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

package heronarts.lx.modulator;

import heronarts.lx.LXCategory;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundDiscreteParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

@LXModulator.Global("Selector")
@LXModulator.Device("Selector")
@LXCategory(LXCategory.MACRO)
public class MacroSelector extends LXMacroModulator implements LXNormalizedParameter, LXTriggerSource, LXOscComponent {

  private static CompoundParameter knob(int num) {
    return new CompoundParameter("K" + num)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Input knob " + num);
  }

  private static BooleanParameter trigger(int num) {
    return
      new BooleanParameter("T" + num)
      .setMode(BooleanParameter.Mode.MOMENTARY)
      .setDescription("Input trigger " + num);
  }

  private static TriggerParameter select(int num) {
    return
      new TriggerParameter("S" + num)
      .setDescription("Select input " + num + " to be active");
  }

  public enum Mode {
    KNOBS("Knobs"),
    TRIGGERS("Trigs");

    private final String label;

    private Mode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }

  }

  public final CompoundParameter knob1 = knob(1);
  public final CompoundParameter knob2 = knob(2);
  public final CompoundParameter knob3 = knob(3);
  public final CompoundParameter knob4 = knob(4);
  public final CompoundParameter knob5 = knob(5);
  public final CompoundParameter knob6 = knob(6);
  public final CompoundParameter knob7 = knob(7);
  public final CompoundParameter knob8 = knob(8);

  public final BooleanParameter trigger1 = trigger(1);
  public final BooleanParameter trigger2 = trigger(2);
  public final BooleanParameter trigger3 = trigger(3);
  public final BooleanParameter trigger4 = trigger(4);
  public final BooleanParameter trigger5 = trigger(5);
  public final BooleanParameter trigger6 = trigger(6);
  public final BooleanParameter trigger7 = trigger(7);
  public final BooleanParameter trigger8 = trigger(8);

  public final TriggerParameter select1 = select(1);
  public final TriggerParameter select2 = select(2);
  public final TriggerParameter select3 = select(3);
  public final TriggerParameter select4 = select(4);
  public final TriggerParameter select5 = select(5);
  public final TriggerParameter select6 = select(6);
  public final TriggerParameter select7 = select(7);
  public final TriggerParameter select8 = select(8);

  public final CompoundParameter[] knobs = {
    knob1, knob2, knob3, knob4, knob5, knob6, knob7, knob8
  };

  public final BooleanParameter[] triggers = {
    trigger1, trigger2, trigger3, trigger4, trigger5, trigger6, trigger7, trigger8
  };

  public final TriggerParameter[] selects = {
    select1, select2, select3, select4, select5, select6, select7, select8
  };

  public final EnumParameter<Mode> mode =
    new EnumParameter<Mode>("Mode", Mode.KNOBS)
    .setDescription("Selector mode");

  public final DiscreteParameter maxInputs =
    new DiscreteParameter("Max Inputs", 5, 1, 6)
    .setDescription("Maximum inputs");

  public final CompoundDiscreteParameter selector =
    new CompoundDiscreteParameter("Selector", 1, 1, 6)
    .setWrappable(true)
    .setDescription("Selected input");

  public final DiscreteParameter activeIndex =
    new DiscreteParameter("Active", 0, 9)
    .setDescription("Currently active input");

  public final TriggerParameter selectPrev =
    new TriggerParameter("Prev", this::selectPrev)
    .setDescription("Select the previous input");

  public final TriggerParameter selectNext =
    new TriggerParameter("Next", this::selectNext)
    .setDescription("Select the next input");

  public final TriggerParameter selectRandom =
    new TriggerParameter("Random", this::selectRandom)
    .setDescription("Select a random input");

  public final TriggerParameter triggerOut =
    new TriggerParameter("Trigger Out")
    .setDescription("Fires whenever the input trigger fires");

  public MacroSelector() {
    super("Selector");
    addParameter("knob1", this.knob1);
    addParameter("knob2", this.knob2);
    addParameter("knob3", this.knob3);
    addParameter("knob4", this.knob4);
    addParameter("knob5", this.knob5);
    addParameter("knob6", this.knob6);
    addParameter("knob7", this.knob7);
    addParameter("knob8", this.knob8);

    addParameter("trigger1", this.trigger1);
    addParameter("trigger2", this.trigger2);
    addParameter("trigger3", this.trigger3);
    addParameter("trigger4", this.trigger4);
    addParameter("trigger5", this.trigger5);
    addParameter("trigger6", this.trigger6);
    addParameter("trigger7", this.trigger7);
    addParameter("trigger8", this.trigger8);

    addParameter("select1", this.select1);
    addParameter("select2", this.select2);
    addParameter("select3", this.select3);
    addParameter("select4", this.select4);
    addParameter("select5", this.select5);
    addParameter("select6", this.select6);
    addParameter("select7", this.select7);
    addParameter("select8", this.select8);

    addParameter("mode", this.mode);
    addParameter("maxInputs", this.maxInputs);
    addParameter("selector", this.selector);
    addParameter("active", this.activeIndex);
    addParameter("selectPrev", this.selectPrev);
    addParameter("selectNext", this.selectNext);
    addParameter("selectRandom", this.selectRandom);
    addParameter("triggerOut", this.triggerOut);

    this.showEight.addListener(this, true);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.showEight) {
      final int range = this.showEight.isOn() ? 8 : 5;
      this.maxInputs.setRange(1, range + 1);
      this.maxInputs.setValue(range);
      this.maxInputs.optionsChanged.bang();
    } else if (p == this.maxInputs) {
      this.selector.setRange(1, 1 + this.maxInputs.getValuei());
      this.selector.optionsChanged.bang();
    } else {
      int s = 0;
      for (TriggerParameter select : this.selects) {
        if (p == select) {
          if (select.isOn()) {
            this.selector.setIndex(s);
          }
          return;
        }
        ++s;
      }
      if (isRunning()) {
        final BooleanParameter input = this.triggers[this.selector.getIndex()];
        if ((p == input) && input.isOn() ) {
          this.triggerOut.trigger();
        }
      }
    }
  }

  private void selectPrev() {
    this.selector.decrement();
  }

  private void selectNext() {
    this.selector.increment();
  }

  private void selectRandom() {
    this.selector.setValue(LXUtils.randomi(this.selector.getRangei()));
  }

  @Override
  public LXParameter[] getMacroParameters() {
    return null;
  }

  @Override
  public StringParameter[] getMacroLabels() {
    return null;
  }

  @Override
  protected double computeValue(double deltaMs) {
    final int activeIndex = this.selector.getIndex();
    this.activeIndex.setValue(activeIndex);
    return this.knobs[activeIndex].getValue();
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.triggerOut;
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException("MacroSelector does not support setNormalized()");
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

  @Override
  public void dispose() {
    this.showEight.removeListener(this);
    super.dispose();
  }

}
