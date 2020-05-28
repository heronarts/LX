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
import heronarts.lx.color.ColorParameter;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXDynamicColor;
import heronarts.lx.color.LXSwatch;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.pattern.LXPattern;

@LXCategory(LXCategory.COLOR)
public class SolidPattern extends LXPattern {

  public enum ColorMode {
    FIXED("Fixed"),
    PALETTE("Palette");

    public final String label;

    private ColorMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final EnumParameter<ColorMode> colorMode =
    new EnumParameter<ColorMode>("Color Mode", ColorMode.FIXED)
    .setDescription("Which source the gradient selects colors from");

  public final ColorParameter color =
    new ColorParameter("Color")
    .setDescription("Color of the pattern");

  public final DiscreteParameter paletteIndex =
    new DiscreteParameter("Index", 1, LXSwatch.MAX_COLORS + 1)
    .setDescription("Which index at the palette to start from");

  public SolidPattern(LX lx) {
    this(lx, LXColor.RED);
  }

  public SolidPattern(LX lx, int color) {
    super(lx);
    this.color.setColor(color);
    addParameter("color", this.color);
    addParameter("colorMode", this.colorMode);
    addParameter("paletteIndex", this.paletteIndex);
  }

  public LXDynamicColor getPaletteColor() {
    return this.lx.engine.palette.getSwatchColor(this.paletteIndex.getValuei() - 1);
  }

  @Override
  public void run(double deltaMs) {
    switch (this.colorMode.getEnum()) {
    case PALETTE:
      setColors(getPaletteColor().getColor());
      break;
    default:
    case FIXED:
      // There may be modulators applied to the h/s/b values!
      setColors(LXColor.hsb(
        this.color.hue.getValue(),
        this.color.saturation.getValue(),
        this.color.brightness.getValue()
      ));
      break;
    }
  }
}
