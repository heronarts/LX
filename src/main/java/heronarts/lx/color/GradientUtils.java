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

package heronarts.lx.color;

import heronarts.lx.utils.LXUtils;

public class GradientUtils {

  public interface GradientFunction {
    public int getGradientColor(float lerp);
  }

  public static class ColorStop {
    private float hue;
    private float saturation;
    private float brightness;
    private int r;
    private int g;
    private int b;

    public void set(ColorParameter color) {
      set(color, 0);
    }

    public void set(ColorParameter color, float hueOffset) {
      set(color, hueOffset, 0, 0);
    }

    public void set(ColorParameter color, float hueOffset, float saturationOffset, float brightnessOffset) {
      this.hue = color.hue.getValuef() + hueOffset;
      this.saturation = LXUtils.clampf(color.saturation.getValuef() + saturationOffset, 0, 100);
      this.brightness = LXUtils.clampf(color.brightness.getValuef() + brightnessOffset, 0, 100);
      setRGB(LXColor.hsb(this.hue, this.saturation, this.brightness));
    }

    public void set(LXDynamicColor color) {
      set(color, 0);
    }

    public void set(LXDynamicColor color, float hueOffset) {
      int c = color.getColor();
      this.hue = color.getHuef() + hueOffset;
      this.saturation = LXColor.s(c);
      this.brightness = LXColor.b(c);
      setRGB(LXColor.hsb(this.hue, this.saturation, this.brightness));
    }

    public void set(LXDynamicColor color, float hueOffset, float saturationOffset, float brightnessOffset) {
      int c = color.getColor();
      this.hue = color.getHuef() + hueOffset;
      this.saturation = LXUtils.clampf(LXColor.s(c) + saturationOffset, 0, 100);
      this.brightness = LXUtils.clampf(LXColor.b(c) + brightnessOffset, 0, 100);
      setRGB(LXColor.hsb(this.hue, this.saturation, this.brightness));
    }

    public void setRGB(int c) {
      this.r = (c & LXColor.R_MASK) >>> LXColor.R_SHIFT;
      this.g = (c & LXColor.G_MASK) >>> LXColor.G_SHIFT;
      this.b = (c & LXColor.B_MASK);
    }

    public void set(ColorStop that) {
      this.hue = that.hue;
      this.saturation = that.saturation;
      this.brightness = that.brightness;
      this.r = that.r;
      this.g = that.g;
      this.b = that.b;
    }

    @Override
    public String toString() {
      return String.format("rgb(%d,%d,%d) hsb(%f,%f,%f)", r, g, b, hue, saturation, brightness);
    }
  }

  public static class ColorStops {
    public final ColorStop[] stops = new ColorStop[LXSwatch.MAX_COLORS + 1];
    public int numStops = 1;

    public ColorStops() {
      for (int i = 0; i < this.stops.length; ++i) {
        this.stops[i] = new ColorStop();
      }
    }

    public void setPaletteGradient(LXPalette palette, int start, int num) {
      int first = Math.min(start, palette.swatch.colors.size() - 1);
      int last = first + num;
      int i = 0;
      int j = 0;
      for (LXDynamicColor color : palette.swatch.colors) {
        if (j >= first && j < last) {
          this.stops[i++].set(color);
        }
        ++j;
      }
      setNumStops(i);
    }

    public void setNumStops(int numStops) {
      this.numStops = numStops;
      if (this.numStops > 0) {
        this.stops[numStops].set(this.stops[numStops-1]);
      }
    }

    public int getColor(float lerp, BlendFunction blendFunction) {
      lerp *= (this.numStops - 1);
      int stop = (int) Math.floor(lerp);
      return blendFunction.blend(this.stops[stop], this.stops[stop+1], lerp - stop);
    }
  }

  public interface BlendFunction {
    public int blend(ColorStop c1, ColorStop c2, float lerp);
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
    }),

    HSV2((c1, c2, lerp) -> {
      float hue1 = c1.hue;
      float hue2 = c2.hue;
      if (hue2 - hue1 > 180) {
        hue1 += 360;
      } else if (hue1 - hue2 > 180) {
        hue2 += 360;
      }
      return LXColor.hsb(
        LXUtils.lerpf(hue1, hue2, lerp),
        LXUtils.lerpf(c1.saturation, c2.saturation, lerp),
        LXUtils.lerpf(c1.brightness, c2.brightness, lerp)
      );
    });

    public final BlendFunction function;

    private BlendMode(BlendFunction function) {
      this.function = function;
    }
  };

}
