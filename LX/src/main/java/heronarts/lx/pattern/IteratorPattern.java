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

package heronarts.lx.pattern;

import heronarts.lx.LXCategory;
import heronarts.lx.LX;
import heronarts.lx.LXPattern;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.modulator.SawLFO;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.FunctionalParameter;

/**
 * Braindead simple test pattern that iterates through all the nodes turning
 * them on one by one in fixed order.
 */
@LXCategory(LXCategory.TEST)
public class IteratorPattern extends LXPattern {

  public final CompoundParameter speed =
    new CompoundParameter("Speed", 10, 1, 100)
    .setDescription("Iteration speed through points in the model");

  private final LXModulator index = startModulator(new SawLFO(0, 1, new FunctionalParameter() {
    @Override
    public double getValue() {
      return (1000 / speed.getValue()) * model.size;
    }
  }));

  public IteratorPattern(LX lx) {
    super(lx);
    addParameter("speed", this.speed);
    setAutoCycleEligible(false);
  }

  @Override
  public void run(double deltaMs) {
    int active = (int) (this.index.getValue() * model.size);
    for (int i = 0; i < colors.length; ++i) {
      this.colors[i] = (i == active) ? 0xFFFFFFFF : 0xFF000000;
    }
  }
}
