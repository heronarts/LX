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
 * @author Mike Schiraldi
 */

package heronarts.lx.pattern.color;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LinkedColorParameter;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.pattern.LXPattern;

@LXCategory(LXCategory.COLOR)
public class AlternatingPattern extends LXPattern {
  public final LinkedColorParameter firstColor =
          new LinkedColorParameter("First Color")
                  .setDescription("First color to use");

  public final LinkedColorParameter secondColor =
          new LinkedColorParameter("Second Color")
                  .setDescription("Second color to use");

  public final DiscreteParameter stripeLength =
          new DiscreteParameter("Length", 1, 10)
                  .setDescription("Number of pixels to go before switching colors");

  public AlternatingPattern(LX lx) {
    // These colors will only be seen when the user switches modes:
    this(lx, LXColor.WHITE, LXColor.BLUE);

    this.firstColor.mode.setValue(LinkedColorParameter.Mode.PALETTE);
    this.firstColor.index.setValue(1);

    this.secondColor.mode.setValue(LinkedColorParameter.Mode.PALETTE);
    this.secondColor.index.setValue(2);
  }

  public AlternatingPattern(LX lx, int firstColor, int secondColor) {
    super(lx);
    this.firstColor.setColor(firstColor);
    this.secondColor.setColor(secondColor);
    addParameter("firstColor", this.firstColor);
    addParameter("secondColor", this.secondColor);
    addParameter("stripeLength", this.stripeLength);
  }

  @Override
  public void run(double deltaMs) {
    int stripeLength = this.stripeLength.getValuei();
    int color1 = this.firstColor.calcColor();
    int color2 = this.secondColor.calcColor();
    int i = 0;
    for (LXPoint p : model.points) {
      int phase = (i / stripeLength) % 2;
      this.colors[p.index] = (phase == 0) ? color1 : color2;
      ++i;
    }
  }
}
