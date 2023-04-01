/**
 * Copyright 2023- Justin Belcher, Mark C. Slee, Heron Arts LLC
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

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BooleanParameter.Mode;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.pattern.LXPattern;

@LXCategory("CoreExample")
public class PresetExamplePattern extends LXPattern {

  public final BooleanParameter preset =
    new BooleanParameter("PRESET", false)
    .setDescription("When On, linked parameters will stop only at preset values")
    .setMode(Mode.MOMENTARY);

  public final CompoundParameter x =
    (CompoundParameter) new CompoundParameter("X") {
      @Override
      public boolean getDetentOn() {
        return preset.isOn();
      }
    }
    .setDetents(new double[] { 0, .25, .5, .75, 1 })
    .setWrappable(true);

  public PresetExamplePattern(LX lx) {
    super(lx);

    addParameter("preset", this.preset);
    addParameter("x", this.x);
  }

  private final double range = 0.05;

  @Override
  protected void run(double deltaMs) {
    clearColors();

    double x = this.x.getValue();

    for (LXPoint p : this.getModel().getPoints()) {
      if (x-range < p.xn && p.xn < x+range) {
        this.colors[p.index] = LXColor.BLUE;
      }
    }
  }
}
