/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.pattern.test;

import heronarts.lx.LXCategory;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXModel;
import heronarts.lx.LX;
import heronarts.lx.modulator.Click;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.utils.LXUtils;

/**
 * Braindead simple test pattern that iterates through all the nodes turning
 * them on one by one in fixed order.
 */
@LXCategory(LXCategory.TEST)
public class TestPattern extends LXPattern {

  public enum Mode {
    ITERATE("Iterate Points"),
    FIXED("Fixed Index"),
    SUBKEY("Model Key");

    private final String string;

    private Mode(String string) {
      this.string = string;
    }

    @Override
    public String toString() {
      return this.string;
    }
  }

  public final EnumParameter<Mode> mode =
    new EnumParameter<Mode>("Mode", Mode.ITERATE)
    .setDescription("Which mode of test operation to use");

  public final CompoundParameter rate = (CompoundParameter)
    new CompoundParameter("Rate", 50, 10, 10000)
    .setExponent(2)
    .setUnits(LXParameter.Units.MILLISECONDS)
    .setDescription("Iteration speed through points in the model");

  public final DiscreteParameter fixedIndex = new DiscreteParameter("Fixed", 0, LXUtils.max(1, model.size))
  .setDescription("Fixed LED point to turn on");

  public final StringParameter subkey =
    new StringParameter("Subkey", LXModel.Key.STRIP)
    .setDescription("Sets the type of model object to query for");

  private final Click increment = new Click(rate);

  private int active;

  public TestPattern(LX lx) {
    super(lx);
    addParameter("mode", this.mode);
    addParameter("rate", this.rate);
    addParameter("fixedIndex", this.fixedIndex);
    addParameter("subkey", this.subkey);
    startModulator(this.increment);
    setAutoCycleEligible(false);
  }

  @Override
  protected void onModelChanged(LXModel model) {
    this.fixedIndex.setRange(0, LXUtils.max(1, model.size));
  }

  @Override
  public void run(double deltaMs) {
    if (model.size == 0) {
      return;
    }

    setColors(LXColor.BLACK);
    switch (this.mode.getEnum()) {
    case ITERATE:
      if (this.increment.click()) {
        ++this.active;
      }
      this.active = this.active % model.points.length;
      this.colors[this.active] = LXColor.WHITE;
      break;
    case FIXED:
      this.colors[this.fixedIndex.getValuei()] = LXColor.WHITE;
      break;
    case SUBKEY:
      for (LXModel sub : model.sub(this.subkey.getString())) {
        setColor(sub, LXColor.WHITE);
      }
      break;
    }
  }
}
