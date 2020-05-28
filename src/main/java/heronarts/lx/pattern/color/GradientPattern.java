/**
 * Copyright 2016- Mark C. Slee, Heron Arts LLC
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
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.COLOR)
public class GradientPattern extends LXPattern {

  public enum ColorMode {
    FIXED("Fixed"),
    PRIMARY("Primary"),
    PALETTE("Palette");

    public final String label;

    private ColorMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  };

  private interface BlendFunction {
    int blend(ColorStop c1, ColorStop c2, float lerp);
  }

  public enum BlendMode {
    RGB((c1, c2, lerp) -> {
      int r = LXUtils.lerpi(c1.r, c2.r, lerp);
      int g = LXUtils.lerpi(c1.g, c2.g, lerp);
      int b = LXUtils.lerpi(c1.b, c2.b, lerp);
      return LXColor.rgba(r, g, b, 255);
    }),

    HSV((c1, c2, lerp) -> {
      return LXColor.hsb(
        LXUtils.lerpf(c1.hue, c2.hue, lerp),
        LXUtils.lerpf(c1.saturation, c2.saturation, lerp),
        LXUtils.lerpf(c1.brightness, c2.brightness, lerp)
      );
    });

    public final BlendFunction function;

    private BlendMode(BlendFunction function) {
      this.function = function;
    }

  };

  private interface CoordinateFunction {
    float getCoordinate(LXPoint p, float normalized, float offset);
  }

  public static enum CoordinateMode {

    NORMAL("Normal", (p, normalized, offset) ->  {
      return normalized - offset;
    }),

    CENTER("Center", (p, normalized, offset) -> {
      return 2 * Math.abs(normalized - (.5f + offset * .5f));
    }),

    RADIAL("Radial", (p, normalized, offset) -> {
      return p.rcn - offset;
    });

    public final String name;
    public final CoordinateFunction function;
    public final CoordinateFunction invert;

    private CoordinateMode(String name, CoordinateFunction function) {
      this.name = name;
      this.function = function;
      this.invert = (p, normalized, offset) -> { return function.getCoordinate(p, normalized, offset) - 1; };
    }

    @Override
    public String toString() {
      return this.name;
    }
  }

  public final EnumParameter<ColorMode> colorMode =
    new EnumParameter<ColorMode>("Color Mode", ColorMode.PRIMARY)
    .setDescription("Which source the gradient selects colors from");

  public final EnumParameter<BlendMode> blendMode =
    new EnumParameter<BlendMode>("Blend Mode", BlendMode.HSV)
    .setDescription("How to blend between colors in the gradient");

  public final ColorParameter fixedColor =
    new ColorParameter("Fixed", LXColor.RED)
    .setDescription("Fixed color to start the gradient from");

  public final ColorParameter secondaryColor =
    new ColorParameter("Secondary", LXColor.RED)
    .setDescription("Secondary color that the gradient blends to");

  public final DiscreteParameter paletteIndex =
    new DiscreteParameter("Index", 1, LXSwatch.MAX_COLORS + 1)
    .setDescription("Which index at the palette to start from");

  public final DiscreteParameter paletteStops =
    new DiscreteParameter("Stops", LXSwatch.MAX_COLORS, 2, LXSwatch.MAX_COLORS + 1)
    .setDescription("How many color stops to use in the palette");

  public final CompoundParameter gradient =
    new CompoundParameter("Amount", 0, -360, 360)
    .setDescription("Amount of total color gradient")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final EnumParameter<CoordinateMode> xMode =
    new EnumParameter<CoordinateMode>("X Mode", CoordinateMode.NORMAL)
    .setDescription("Which coorindate mode the X-dimension uses");

  public final EnumParameter<CoordinateMode> yMode =
    new EnumParameter<CoordinateMode>("Y Mode", CoordinateMode.NORMAL)
    .setDescription("Which coorindate mode the Y-dimension uses");

  public final EnumParameter<CoordinateMode> zMode =
    new EnumParameter<CoordinateMode>("Z Mode", CoordinateMode.NORMAL)
    .setDescription("Which coorindate mode the Z-dimension uses");

  public final CompoundParameter xAmount =
    new CompoundParameter("X-Amt", 0, -1, 1)
    .setDescription("Sets the amount of hue spread on the X axis")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter yAmount =
    new CompoundParameter("Y-Amt", 0, -1, 1)
    .setDescription("Sets the amount of hue spread on the Y axis")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter zAmount =
    new CompoundParameter("Z-Amt", 0, -1, 1)
    .setDescription("Sets the amount of hue spread on the Z axis")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter xOffset =
    new CompoundParameter("X-Off", 0, -1, 1)
    .setDescription("Sets the offset of the hue spread point on the X axis")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter yOffset =
    new CompoundParameter("Y-Off", 0, -1, 1)
    .setDescription("Sets the offset of the hue spread point on the Y axis")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter zOffset =
    new CompoundParameter("Z-Off", 0, -1, 1)
    .setDescription("Sets the offset of the hue spread point on the Z axis")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  private final ColorStop[] stops = new ColorStop[LXSwatch.MAX_COLORS + 1];
  private int numStops = 0;

  public GradientPattern(LX lx) {
    super(lx);
    addParameter("xAmount", this.xAmount);
    addParameter("yAmount", this.yAmount);
    addParameter("zAmount", this.zAmount);
    addParameter("xOffset", this.xOffset);
    addParameter("yOffset", this.yOffset);
    addParameter("zOffset", this.zOffset);
    addParameter("colorMode", this.colorMode);
    addParameter("blendMode", this.blendMode);
    addParameter("gradient", this.gradient);
    addParameter("fixedColor", this.fixedColor);
    addParameter("xMode", this.xMode);
    addParameter("yMode", this.yMode);
    addParameter("zMode", this.zMode);
    addParameter("paletteIndex", this.paletteIndex);
    addParameter("paletteStops", this.paletteStops);

    for (int i = 0; i < stops.length; ++i) {
      this.stops[i] = new ColorStop();
    }
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (this.gradient == p ||
        this.colorMode == p ||
        this.paletteIndex == p ||
        this.fixedColor.hue == p) {
      setSecondaryColor();
    }
  }

  public LXDynamicColor getPrimaryColor() {
    return this.lx.engine.palette.getSwatchColor(this.paletteIndex.getValuei() - 1);
  }

  private void setSecondaryColor() {
    switch (this.colorMode.getEnum()) {
    case FIXED:
      this.secondaryColor.setColor(LXColor.hsb(
        this.fixedColor.hue.getValue() + this.gradient.getValue(),
        this.fixedColor.saturation.getValue(),
        this.fixedColor.brightness.getValue()
      ));
      break;
    case PRIMARY:
      LXDynamicColor primary = getPrimaryColor();
      int c = primary.getColor();
      this.secondaryColor.setColor(LXColor.hsb(
        primary.getHue() + this.gradient.getValue(),
        LXColor.s(c),
        LXColor.b(c)
      ));
      break;
    default:
      break;
    }
  }

  private class ColorStop {
    private float hue;
    private float saturation;
    private float brightness;
    private int r;
    private int g;
    private int b;

    private void set(ColorParameter color) {
      set(color, 0);
    }

    private void set(ColorParameter color, float hueOffset) {
      this.hue = color.hue.getValuef() + hueOffset;
      this.saturation = color.saturation.getValuef();
      this.brightness = color.saturation.getValuef();
      setRGB(LXColor.hsb(this.hue, this.saturation, this.brightness));
    }

    private void set(LXDynamicColor color) {
      set(color, 0);
    }

    private void set(LXDynamicColor color, float hueOffset) {
      int c = color.getColor();
      this.hue = color.getHuef() + hueOffset;
      this.saturation = LXColor.s(c);
      this.brightness = LXColor.b(c);
      setRGB(LXColor.hsb(this.hue, this.saturation, this.brightness));
    }

    private void setRGB(int c) {
      this.r = (c & LXColor.R_MASK) >>> LXColor.R_SHIFT;
      this.g = (c & LXColor.G_MASK) >>> LXColor.G_SHIFT;
      this.b = (c & LXColor.B_MASK);
    }

    private void set(ColorStop that) {
      this.hue = that.hue;
      this.saturation = that.saturation;
      this.brightness = that.brightness;
      this.r = that.r;
      this.g = that.g;
      this.b = that.b;
    }
  }

  private void setColorStops() {
    switch (this.colorMode.getEnum()) {
    case FIXED:
      this.stops[0].set(this.fixedColor);
      this.stops[1].set(this.fixedColor, this.gradient.getValuef());
      this.numStops = 2;
      break;
    case PRIMARY:
      LXDynamicColor swatchColor = getPrimaryColor();
      this.stops[0].set(swatchColor);
      this.stops[1].set(swatchColor, this.gradient.getValuef());
      setSecondaryColor();
      this.numStops = 2;
      break;
    case PALETTE:
      int first = Math.min(this.paletteIndex.getValuei() - 1, this.lx.engine.palette.swatch.colors.size() - 1);
      int last = first + this.paletteStops.getValuei();
      int i = 0;
      int j = 0;
      for (LXDynamicColor color : this.lx.engine.palette.swatch.colors) {
        if (j >= first && j < last) {
          this.stops[i++].set(color);
        }
        ++j;
      }
      this.numStops = i;
      if (i > 0) {
        this.stops[i].set(this.stops[i-1]);
      }

      break;
    }
  }

  public int getGradientColor(float lerp) {
    final BlendMode blend = this.blendMode.getEnum();
    lerp *= (this.numStops - 1);
    int stop = (int) Math.floor(lerp);
    return blend.function.blend(this.stops[stop], this.stops[stop+1], lerp - stop);
  }

  @Override
  public void run(double deltaMs) {
    setColorStops();

    float xAmount = this.xAmount.getValuef();
    float yAmount = this.yAmount.getValuef();
    float zAmount = this.zAmount.getValuef();

    final float total = Math.abs(xAmount) + Math.abs(yAmount) + Math.abs(zAmount);
    if (total > 1) {
      xAmount /= total;
      yAmount /= total;
      zAmount /= total;
    }

    final float xOffset = this.xOffset.getValuef();
    final float yOffset = this.yOffset.getValuef();
    final float zOffset = this.zOffset.getValuef();

    final CoordinateMode xMode = this.xMode.getEnum();
    final CoordinateMode yMode = this.yMode.getEnum();
    final CoordinateMode zMode = this.zMode.getEnum();

    final CoordinateFunction xFunction = (xAmount < 0) ? xMode.invert : xMode.function;
    final CoordinateFunction yFunction = (yAmount < 0) ? yMode.invert : yMode.function;
    final CoordinateFunction zFunction = (zAmount < 0) ? zMode.invert : zMode.function;

    final BlendMode blend = this.blendMode.getEnum();

    for (LXPoint p : model.points) {
      float lerp = (this.numStops - 1) * LXUtils.clampf(
        xAmount * xFunction.getCoordinate(p, p.xn, xOffset) +
        yAmount * yFunction.getCoordinate(p, p.yn, yOffset) +
        zAmount * zFunction.getCoordinate(p, p.zn, zOffset),
        0, 1
      );
      int stop = (int) Math.floor(lerp);
      colors[p.index] = blend.function.blend(this.stops[stop], this.stops[stop+1], lerp - stop);
    }
  }
}
