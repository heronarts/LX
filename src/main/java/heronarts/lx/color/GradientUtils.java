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

import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.utils.LXUtils;

public class GradientUtils {

  public static class GrayTable {

    private final static int SIZE = 256;

    private final LXNormalizedParameter invert;
    private final LXNormalizedParameter level;

    private double previousInvert = -1;
    private double previousLevel = -1;
    private boolean dirty = true;

    /**
     * Lookup table of gray values
     */
    public final int[] lut;

    public GrayTable(LXNormalizedParameter invert) {
      this(invert, 0);
    }

    public GrayTable(LXNormalizedParameter invert, int padding) {
      this(invert, null, padding);
    }

    public GrayTable(LXNormalizedParameter invert, LXNormalizedParameter level) {
      this(invert, level, 0);
    }

    public GrayTable(LXNormalizedParameter invert, LXNormalizedParameter level, int padding) {
      this.invert = invert;
      this.level = level;
      this.lut = new int[SIZE + padding];
    }

    public void update() {
      final double invert = this.invert.getNormalized();
      final double level = (this.level != null) ? this.level.getNormalized() : 1;
      if ((invert != this.previousInvert) || (level != this.previousLevel)) {
        this.dirty = true;
        this.previousInvert = invert;
        this.previousLevel = level;
      }
      if (this.dirty) {
        for (int i = 0; i < SIZE; ++i) {
          int b = (int) (level * LXUtils.lerp(i, 255.9 - i, invert));
          this.lut[i] = 0xff000000 | (b << 16) | (b << 8) | b;
        }
        for (int i = SIZE; i < this.lut.length; ++i) {
          this.lut[i] = this.lut[SIZE-1];
        }
        this.dirty = false;
      }
    }

    /**
     * Gets the LUT grayscale color for this brightness value
     *
     * @param b Brightness from 0-100
     * @return Invert table color
     */
    public int get(float b) {
      return this.lut[(int) (2.559f * b)];
    }
  }

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
      final int c = color.getColor();
      this.hue = color.getHuef() + hueOffset;
      this.saturation = color.getSaturation();
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

    public boolean isBlack() {
      return this.brightness == 0;
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

  /**
   * Hue interpolation modes. Since the hues form a color wheel, there are various
   * strategies for moving from hue1 to hue2.
   */
  public interface HueInterpolation {

    /**
     * Interpolate between two values
     *
     * @param hue1 Source hue
     * @param hue2 Destination hue
     * @param lerp Interpolation amount
     * @return A hue on a path between these two values
     */
    public float lerp(float hue1, float hue2, float lerp);

    /**
     * HSV path always stays within the color wheel of raw values, never crossing
     * the 360-degree boundary
     */
    public static final HueInterpolation HSV = (hue1, hue2, lerp) -> {
      return LXUtils.lerpf(hue1, hue2, lerp);
    };

    /**
     * HSVM takes the minimum path from hue1 to hue2, wrapping around the
     * 360-degree boundary if it makes for a shorter path
     */
    public static final HueInterpolation HSVM = (hue1, hue2, lerp) -> {
      if (hue2 - hue1 > 180) {
        hue1 += 360f;
      } else if (hue1 - hue2 > 180) {
        hue2 += 360f;
      }
      return LXUtils.lerpf(hue1, hue2, lerp);
    };

    /**
     * HSVCW takes a clockwise path always, even if it means a longer interpolation
     * from hue1 to hue2, e.g. [350->340] will go [350->360],[0->340]
     */
    public static final HueInterpolation HSVCW = (hue1, hue2, lerp) -> {
      if (hue2 < hue1) {
        hue2 += 360f;
      }
      return LXUtils.lerpf(hue1, hue2, lerp);
    };

    /**
     * HSVCCW takes a counter-clockwise path always, even if it means a longer interpolation
     * from hue1 to hue2, e.g. [340->350] will go [340->0],[360->350]
     */
    public static final HueInterpolation HSVCCW = (hue1, hue2, lerp) -> {
      if (hue1 < hue2) {
        hue1 += 360f;
      }
      return LXUtils.lerpf(hue1, hue2, lerp);
    };
  }

  /**
   * A blend function interpolates between two colors
   */
  public interface BlendFunction {

    /**
     * Blend between colors specified as gradient color stops, not necessarily
     * backed by any parameters
     *
     * @param c1 Source color
     * @param c2 Destination color
     * @param lerp Blend amount
     * @return Blended color
     */
    public int blend(ColorStop c1, ColorStop c2, float lerp);

    /**
     * Blend between colors specified by color parameters.
     *
     * @param c1 Source color
     * @param c2 Destination color
     * @param lerp Blend amount
     * @return Blended color
     */
    public int blend(ColorParameter c1, ColorParameter c2, float lerp);

    public static final BlendFunction RGB = new BlendFunction() {

      public int blend(ColorStop c1, ColorStop c2, float lerp) {
        int r = LXUtils.lerpi(c1.r, c2.r, lerp);
        int g = LXUtils.lerpi(c1.g, c2.g, lerp);
        int b = LXUtils.lerpi(c1.b, c2.b, lerp);
        return LXColor.rgba(r, g, b, 255);
      }

      @Override
      public int blend(ColorParameter c1, ColorParameter c2, float lerp) {
        return LXColor.lerp(c1.getColor(), c2.getColor(), lerp);
      }

    };

    static BlendFunction _HSV(HueInterpolation hueLerp) {
      return new BlendFunction() {
        public int blend(ColorStop c1, ColorStop c2, float lerp) {
          float hue1 = c1.hue;
          float hue2 = c2.hue;
          float sat1 = c1.saturation;
          float sat2 = c2.saturation;
          if (c1.isBlack()) {
            hue1 = hue2;
            sat1 = sat2;
          } else if (c2.isBlack()) {
            hue2 = hue1;
            sat2 = sat1;
          }
          return LXColor.hsb(
            hueLerp.lerp(hue1, hue2, lerp),
            LXUtils.lerpf(sat1, sat2, lerp),
            LXUtils.lerpf(c1.brightness, c2.brightness, lerp)
          );
        }

        @Override
        public int blend(ColorParameter c1, ColorParameter c2, float lerp) {
          float hue1, hue2, sat1, sat2;
          if (c1.isBlack()) {
            hue1 = hue2 = c2.hue.getValuef();
            sat1 = sat2 = c2.saturation.getValuef();
          } else if (c2.isBlack()) {
            hue2 = hue1 = c1.hue.getValuef();
            sat2 = sat1 = c1.saturation.getValuef();;
          } else {
            hue1 = c1.hue.getValuef();
            hue2 = c2.hue.getValuef();
            sat1 = c1.saturation.getValuef();
            sat2 = c2.saturation.getValuef();
          }
          return LXColor.hsb(
            hueLerp.lerp(hue1, hue2, lerp),
            LXUtils.lerpf(sat1, sat2, lerp),
            LXUtils.lerpf(c1.brightness.getValuef(), c2.brightness.getValuef(), lerp)
          );
        }
      };
    }

    public static final BlendFunction HSV = _HSV(HueInterpolation.HSV);
    public static final BlendFunction HSVM = _HSV(HueInterpolation.HSVM);
    public static final BlendFunction HSVCW = _HSV(HueInterpolation.HSVCW);
    public static final BlendFunction HSVCCW = _HSV(HueInterpolation.HSVCCW);
  }

  public enum BlendMode {

    RGB("RGB", null, BlendFunction.RGB),
    HSV("HSV", HueInterpolation.HSV, BlendFunction.HSV),
    HSVM("HSV-Min", HueInterpolation.HSVM, BlendFunction.HSVM),
    HSVCW("HSV-CW", HueInterpolation.HSVCW, BlendFunction.HSVCW),
    HSVCCW("HSV-CCW", HueInterpolation.HSVCCW, BlendFunction.HSVCCW);

    public final String label;
    public final HueInterpolation hueInterpolation;
    public final BlendFunction function;

    private BlendMode(String label, HueInterpolation hueInterpolation, BlendFunction function) {
      this.label = label;
      this.hueInterpolation = hueInterpolation;
      this.function = function;
    }

    @Override
    public String toString() {
      return this.label;
    }
  };

}
