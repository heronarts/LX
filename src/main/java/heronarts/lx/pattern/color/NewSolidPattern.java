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

package heronarts.lx.pattern.color;

import heronarts.lx.LXCategory;
import heronarts.lx.LX;
import heronarts.lx.color.*;
import heronarts.lx.pattern.LXPattern;

@LXCategory(LXCategory.COLOR)
public class NewSolidPattern extends LXPattern {
  public final LinkableColorParameter color;

  public NewSolidPattern(LX lx) {
    this(lx, LXColor.RED);
  }

  public NewSolidPattern(LX lx, int color) {
    super(lx);
    this.color = new LinkableColorParameter(lx, "Color", color);
    addParameter("color", this.color);
  }

  @Override
  public void run(double deltaMs) {
    setColors(this.color.getColor());
  }
}
