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

/*
    final LXParameterListener checkAPC = (p) -> {
      APC40Mk2 apcSurface = (APC40Mk2) lx.engine.midi.findSurface("APC40 mkII");
      if (apcSurface != null) {
        APC40Mk2.ActiveColor activeColor = apcSurface.activeColor();
        Integer newColor = null;
        if (activeColor.color != null) {
          this.colorMode.setValue(ColorMode.FIXED);
          newColor = activeColor.color;
        } else if (activeColor.source != null) {
          LXSwatch swatch = activeColor.source.getSwatch();
          this.colorMode.setValue(ColorMode.PALETTE);
          int index = swatch.getIndex();
          this.paletteIndex.setValue(index);
          newColor = swatch.getColor(index).primary.getColor();
        }
        if (newColor != null) {
          LX.log("This is where we'd set the color to " + newColor);
          // ...except that seems to lead to a stack overflow
        }
      }
    };
    this.color.addListener(checkAPC);
    this.colorMode.addListener(checkAPC);
    this.paletteIndex.addListener(checkAPC);
    */

  }

  @Override
  public void run(double deltaMs) {
    setColors(this.color.getColor());
  }
}
