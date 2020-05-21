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
import heronarts.lx.color.LXColor;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.COLOR)
public class ColorizeEffect extends LXEffect {

  private static final float AVG_FACTOR = 1 / (3 * 255f);
  private static final float INV_255 = 1 / 255f;

  private interface SourceFunction {
    public float getLerpFactor(int argb);
  }

  public enum Source {

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
    });

    private final String name;
    public final SourceFunction lerp;

    private Source(String name, SourceFunction function) {
      this.name = name;
      this.lerp = function;
    }

    @Override
    public String toString() {
      return this.name;
    }
  }

  public enum BlendMode {
    RGB,
    HSV;
  }

  public final EnumParameter<Source> source =
    new EnumParameter<Source>("Source", Source.BRIGHTNESS)
    .setDescription("Determines the source of the color mapping");

  public final EnumParameter<BlendMode> blendMode =
    new EnumParameter<BlendMode>("Blend Mode", BlendMode.RGB)
    .setDescription("Determines the mode of color blending");

  public final ColorParameter color1 =
    new ColorParameter("Color 1", 0xff000000)
    .setDescription("The first color that is mapped from");

  public final ColorParameter color2 =
    new ColorParameter("Color 2", 0xffffffff)
    .setDescription("The second color that is mapped to");

  public ColorizeEffect(LX lx) {
    super(lx);
    addParameter("source", this.source);
    addParameter("blendMode", this.blendMode);
    addParameter("color1", this.color1);
    addParameter("color2", this.color2);
  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    switch (this.blendMode.getEnum()) {
    case RGB:
      runRGB(deltaMs, enabledAmount);
      break;
    case HSV:
      runHSV(deltaMs, enabledAmount);
      break;
    }
  }

  private void runRGB(double deltaMs, double enabledAmount) {
    int c1 = LXColor.hsb(
      this.color1.hue.getValuef(),
      this.color1.saturation.getValuef(),
      this.color1.brightness.getValuef()
    );
    int c2 = LXColor.hsb(
      this.color2.hue.getValuef(),
      this.color2.saturation.getValuef(),
      this.color2.brightness.getValuef()
    );
    SourceFunction sf = this.source.getEnum().lerp;

    if (enabledAmount < 1) {
      for (int i = 0; i < colors.length; ++i) {
        float lerp = sf.getLerpFactor(colors[i]);
        colors[i] = LXColor.lerp(colors[i], LXColor.lerp(c1, c2, lerp), enabledAmount);
      }
    } else {
      for (int i = 0; i < colors.length; ++i) {
        float lerp = sf.getLerpFactor(colors[i]);
        colors[i] = LXColor.lerp(c1, c2, lerp);
      }
    }
  }

  private void runHSV(double deltaMs, double enabledAmount) {
    float h1 = this.color1.hue.getValuef();
    float h2 = this.color2.hue.getValuef();
    float s1 = this.color1.saturation.getValuef();
    float s2 = this.color2.saturation.getValuef();
    float b1 = this.color1.brightness.getValuef();
    float b2 = this.color2.brightness.getValuef();

    SourceFunction sf = this.source.getEnum().lerp;
    if (enabledAmount < 1) {
      for (int i = 0; i < colors.length; ++i) {
        float lerp = sf.getLerpFactor(colors[i]);
        colors[i] = LXColor.lerp(colors[i],
          LXColor.hsb(
            LXUtils.lerpf(h1, h2, lerp),
            LXUtils.lerpf(s1, s2, lerp),
            LXUtils.lerpf(b1, b2, lerp)
          ),
          enabledAmount);
      }
    } else {
      for (int i = 0; i < colors.length; ++i) {
        float lerp = sf.getLerpFactor(colors[i]);
        colors[i] = LXColor.hsb(
          LXUtils.lerpf(h1, h2, lerp),
          LXUtils.lerpf(s1, s2, lerp),
          LXUtils.lerpf(b1, b2, lerp)
        );
      }
    }

  }

}
