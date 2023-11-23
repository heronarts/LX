/**
 * Copyright 2020- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.effect.color;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.color.ColorParameter;
import heronarts.lx.color.GradientUtils.BlendFunction;
import heronarts.lx.color.GradientUtils.BlendMode;
import heronarts.lx.color.GradientUtils.ColorStops;
import heronarts.lx.color.GradientUtils.GradientFunction;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXDynamicColor;
import heronarts.lx.color.LXPalette;
import heronarts.lx.color.LXSwatch;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;

@LXCategory(LXCategory.COLOR)
public class ColorizeEffect extends LXEffect implements GradientFunction {

  private static final float AVG_FACTOR = 1 / (3 * 255f);
  private static final float INV_255 = 1 / 255f;

  private interface SourceFunction {
    public float getLerpFactor(int argb);
  }

  public enum SourceMode {

    BRIGHTNESS("Brightness", (argb) -> {
      return LXColor.b(argb) * .01f; }),

    LUMINOSITY("Luminosity", (argb) -> {
      return LXColor.luminosity(argb) * .01f;
    }),

    RED("Red", (argb) -> { return INV_255 * ((argb & LXColor.R_MASK) >> LXColor.R_SHIFT); }),
    GREEN("Green", (argb) -> { return INV_255 * ((argb & LXColor.G_MASK) >> LXColor.G_SHIFT); }),
    BLUE("Blue", (argb) -> { return INV_255 * (argb & LXColor.B_MASK); }),

    MIN("Min", (argb) -> {
      int r = (argb & LXColor.R_MASK) >> LXColor.R_SHIFT;
      int g = (argb & LXColor.G_MASK) >> LXColor.G_SHIFT;
      int b = (argb & LXColor.B_MASK);
      int rg = (r < g) ? r : g;
      return INV_255 * ((b < rg) ? b : rg);
    }),

    AVERAGE("Average", (argb) -> {
      int r = (argb & LXColor.R_MASK) >> LXColor.R_SHIFT;
      int g = (argb & LXColor.G_MASK) >> LXColor.G_SHIFT;
      int b = (argb & LXColor.B_MASK);
      return AVG_FACTOR * (r + g + b);
    }),

    ALPHA("Alpha", (argb) -> {
      return INV_255 * (argb >>> LXColor.ALPHA_SHIFT);
    });

    private final String name;
    public final SourceFunction lerp;

    private SourceMode(String name, SourceFunction function) {
      this.name = name;
      this.lerp = function;
    }

    @Override
    public String toString() {
      return this.name;
    }
  }

  public enum ColorMode {
    FIXED("Fixed"),
    RELATIVE("Relative"),
    LINKED("Linked"),
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

  public final EnumParameter<SourceMode> source =
    new EnumParameter<SourceMode>("Source", SourceMode.BRIGHTNESS)
    .setDescription("Determines the source of the color mapping");

  public final EnumParameter<BlendMode> blendMode =
    new EnumParameter<BlendMode>("Blend Mode", BlendMode.RGB)
    .setDescription("Determines the mode of color blending");

  public final EnumParameter<ColorMode> colorMode =
    new EnumParameter<ColorMode>("Color Mode", ColorMode.FIXED)
    .setDescription("Which source the colors come from");

  public final ColorParameter color1 =
    new ColorParameter("Color 1", 0xff000000)
    .setDescription("The first color that is mapped from");

  public final ColorParameter color2 =
    new ColorParameter("Color 2", 0xffffffff)
    .setDescription("The second color that is mapped to");

  public final CompoundParameter gradientHue =
    new CompoundParameter("H-Offset", 0, -360, 360)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("Amount of hue gradient")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter gradientSaturation =
    new CompoundParameter("S-Offset", 0, -100, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Amount of saturation gradient")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter gradientBrightness =
    new CompoundParameter("B-Offset", 0, -100, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Amount of brightness gradient")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter linkedHue =
    new CompoundParameter("H-Linked", 0, -360, 360)
    .setUnits(CompoundParameter.Units.DEGREES)
    .setDescription("Amount of hue gradient")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter linkedSaturation =
    new CompoundParameter("S-Linked", 0, -100, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Amount of saturation gradient")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final CompoundParameter linkedBrightness =
    new CompoundParameter("B-Linked", 0, -100, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Amount of brightness gradient")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final DiscreteParameter paletteIndex =
    new LXPalette.IndexSelector("Index")
    .setDescription("Which index at the palette to start from");

  public final DiscreteParameter paletteStops =
    new DiscreteParameter("Stops", LXSwatch.MAX_COLORS, 2, LXSwatch.MAX_COLORS + 1)
    .setDescription("How many color stops to use in the palette");

  public ColorizeEffect(LX lx) {
    super(lx);
    addParameter("source", this.source);
    addParameter("gradientHue", this.gradientHue);
    addParameter("gradientSaturation", this.gradientSaturation);
    addParameter("gradientBrightness", this.gradientBrightness);
    addParameter("colorMode", this.colorMode);
    addParameter("blendMode", this.blendMode);
    addParameter("color1", this.color1);
    addParameter("color2", this.color2);
    addParameter("paletteIndex", this.paletteIndex);
    addParameter("paletteStops", this.paletteStops);
    addParameter("primaryHue", this.linkedHue);
    addParameter("primarySaturation", this.linkedSaturation);
    addParameter("primaryBrightness", this.linkedBrightness);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.colorMode ||
        p == this.color1 ||
        p == this.gradientHue ||
        p == this.gradientSaturation ||
        p == this.gradientBrightness ||
        p == this.linkedHue ||
        p == this.linkedSaturation ||
        p == this.linkedBrightness) {
      // We want to do this onParameterChanged so that the UI indicator
      // updates properly, whether or not this pattern is actually running
      setGradientColor();
    }
  }

  private final ColorStops colorStops = new ColorStops();

  private void setGradientColor() {
    if (this.colorMode.getEnum() == ColorMode.RELATIVE) {
      this.color2.brightness.setValue(this.color1.brightness.getValue() + this.gradientBrightness.getValue());
      this.color2.saturation.setValue(this.color1.saturation.getValue() + this.gradientSaturation.getValue());
      this.color2.hue.setValue((360 + this.color1.hue.getValue() + this.gradientHue.getValue()) % 360);
    } else if (this.colorMode.getEnum() == ColorMode.LINKED) {
      LXDynamicColor swatchColor = getSwatchColor();
      this.color1.brightness.setValue(swatchColor.getBrightness() + this.linkedBrightness.getValue());
      this.color1.saturation.setValue(swatchColor.getSaturation() + this.linkedSaturation.getValue());
      this.color1.hue.setValue((360 + swatchColor.getHue() + this.linkedHue.getValue()) % 360);

      this.color2.brightness.setValue(swatchColor.getBrightness() + this.gradientBrightness.getValue());
      this.color2.saturation.setValue(swatchColor.getSaturation() + this.gradientSaturation.getValue());
      this.color2.hue.setValue((360 + swatchColor.getHue() + this.gradientHue.getValue()) % 360);
    }
  }

  public LXDynamicColor getSwatchColor() {
    return this.lx.engine.palette.getSwatchColor(this.paletteIndex.getValuei() - 1);
  }

  private void setColorStops() {
    switch (this.colorMode.getEnum()) {
    default:
    case FIXED:
      this.colorStops.stops[0].set(this.color1);
      this.colorStops.stops[1].set(this.color2);
      this.colorStops.setNumStops(2);
      break;

    case LINKED:
      LXDynamicColor swatchColor = getSwatchColor();
      this.colorStops.stops[0].set(swatchColor,
        this.linkedHue.getValuef(),
        this.linkedSaturation.getValuef(),
        this.linkedBrightness.getValuef()
      );
      this.colorStops.stops[1].set(swatchColor,
        this.gradientHue.getValuef(),
        this.gradientSaturation.getValuef(),
        this.gradientBrightness.getValuef()
      );
      this.colorStops.setNumStops(2);
      break;

    case RELATIVE:
      this.colorStops.stops[0].set(this.color1);
      this.colorStops.stops[1].set(this.color1,
        this.gradientHue.getValuef(),
        this.gradientSaturation.getValuef(),
        this.gradientBrightness.getValuef()
      );
      this.colorStops.setNumStops(2);
      break;

    case PALETTE:
      this.colorStops.setPaletteGradient(this.lx.engine.palette, this.paletteIndex.getValuei() - 1, this.paletteStops.getValuei());
      break;
    }
  }

  @Override
  public int getGradientColor(float lerp) {
    return this.colorStops.getColor(lerp, this.blendMode.getEnum().function);
  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    setGradientColor();
    setColorStops();

    final SourceFunction sourceFunction = this.source.getEnum().lerp;
    final BlendFunction blendFunction = this.blendMode.getEnum().function;

    if (enabledAmount < 1) {
      for (LXPoint p : model.points) {
        int i = p.index;
        int c2 = this.colorStops.getColor(sourceFunction.getLerpFactor(colors[i]), blendFunction);
        colors[i] = LXColor.lerp(
          colors[i],
          (colors[i] & LXColor.ALPHA_MASK) | (c2 & LXColor.RGB_MASK),
          enabledAmount
        );
      }
    } else {
      for (LXPoint p : model.points) {
        int i = p.index;
        int c2 = this.colorStops.getColor(sourceFunction.getLerpFactor(colors[i]), blendFunction);
        colors[i] = (colors[i] & LXColor.ALPHA_MASK) | (c2 & LXColor.RGB_MASK);
      }
    }
  }


}
