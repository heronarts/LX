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
import heronarts.lx.model.LXPoint;
import heronarts.lx.LX;
import heronarts.lx.modulator.Click;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
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
    TAG("Tag");

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

  public final CompoundParameter rate =
    new CompoundParameter("Rate", 50, 10, 10000)
    .setExponent(2)
    .setUnits(LXParameter.Units.MILLISECONDS)
    .setDescription("Iteration speed through points in the model");

  public final DiscreteParameter fixedIndex =
    new DiscreteParameter("Fixed", 0, LXUtils.max(1, model.size))
    .setDescription("Fixed LED point to turn on");

  public final StringParameter tag =
    new StringParameter("Tag", LXModel.Tag.STRIP)
    .setDescription("Sets the fixture tag to query for");

  public final BooleanParameter tagAll =
    new BooleanParameter("All", true)
    .setDescription("Light up all points in the tag");

  public final DiscreteParameter tagIndex =
    new DiscreteParameter("Fixed", 0, LXUtils.max(1, model.size))
    .setDescription("Fixed LED point to turn on");

  public final BoundedParameter cpuTest =
    new BoundedParameter("CPU Test", 0, 1000)
    .setDescription("How many thousands of extra multiplications to perform per frame");

  private final Click increment = new Click(rate);

  private int active;

  public TestPattern(LX lx) {
    super(lx);
    addParameter("mode", this.mode);
    addParameter("rate", this.rate);
    addParameter("fixedIndex", this.fixedIndex);
    addParameter("tag", this.tag);
    addParameter("tagAll", this.tagAll);
    addParameter("tagIndex", this.tagIndex);
    addParameter("cpuTest", this.cpuTest);
    startModulator(this.increment);
    setAutoCycleEligible(false);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.tag) {
      updateTagIndex();
    }
  }

  @Override
  protected void onModelChanged(LXModel model) {
    this.fixedIndex.setRange(0, LXUtils.max(1, model.size));
    updateTagIndex();
  }

  private void updateTagIndex() {
    int count = 0;
    for (LXModel sub : model.sub(this.tag.getString())) {
      count += sub.points.length;
    }
    this.tagIndex.setRange(0, LXUtils.max(1, count));
  }

  @Override
  public void run(double deltaMs) {
    final int cpuTest = 1000 * (int) (this.cpuTest.getValue());
    for (int i = 0; i < cpuTest; ++i) {
      double d1 = Math.random();
      double d2 = Math.random();
      double d3 = Math.random();
      LXUtils.lerp(d1, d2, d3);
    }

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
      this.colors[model.points[this.active].index] = LXColor.WHITE;
      break;
    case FIXED:
      this.colors[model.points[this.fixedIndex.getValuei()].index] = LXColor.WHITE;
      break;
    case TAG:
      final boolean tagAll = this.tagAll.isOn();
      final int tagIndex = this.tagIndex.getValuei();
      int i = 0;
      for (LXModel sub : model.sub(this.tag.getString())) {
        if (tagAll) {
          setColor(sub, LXColor.WHITE);
        } else {
          for (LXPoint p : sub.points) {
            if (i++ == tagIndex) {
              colors[p.index] = LXColor.WHITE;
              return;
            }
          }
        }
      }
      break;
    }


  }
}
