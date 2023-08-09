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

package heronarts.lx.color;

import heronarts.lx.utils.LXUtils;

/**
 * Various utilities that operate on color values
 */
public class LXColor {

  /**
   * Color blending modes
   */
  public enum Blend {
    LERP,
    ADD,
    SUBTRACT,
    MULTIPLY,
    SCREEN,
    LIGHTEST,
    DARKEST,
    DODGE,
    BURN,
    HIGHLIGHT,
    SPOTLIGHT
  }

  public static final int CLEAR = 0x00000000;
  public static final int BLACK = 0xff000000;
  public static final int WHITE = 0xffffffff;
  public static final int RED = 0xffff0000;
  public static final int GREEN = 0xff00ff00;
  public static final int BLUE = 0xff0000ff;

  public static final int ALPHA_MASK = 0xff000000;
  public static final int R_MASK = 0x00ff0000;
  public static final int G_MASK = 0x0000ff00;
  public static final int B_MASK = 0x000000ff;
  public static final int RB_MASK = R_MASK | B_MASK;
  public static final int RGB_MASK = R_MASK | G_MASK | B_MASK;
  public static final int AG_MASK = ALPHA_MASK | G_MASK;

  public static final int ALPHA_SHIFT = 24;
  public static final int R_SHIFT = 16;
  public static final int G_SHIFT = 8;

  public static final int MAX_HUE = 360;

  public static byte alpha(int argb) {
    return (byte) ((argb & ALPHA_MASK) >>> ALPHA_SHIFT);
  }

  public static byte red(int argb) {
    return (byte) ((argb & R_MASK) >>> R_SHIFT);
  }

  public static byte green(int argb) {
    return (byte) ((argb & G_MASK) >>> G_SHIFT);
  }

  public static byte blue(int argb) {
    return (byte) (argb & B_MASK);
  }

  public static int toABGR(int argb) {
    return
      (argb & AG_MASK) |
      ((argb << 16) & R_MASK) |
      ((argb >> 16) & B_MASK);
  }

  /**
   * Hue of a color from 0-360
   *
   * @param rgb Color value
   * @return Hue value from 0-360
   */
  public static float h(int rgb) {
    int r = (rgb & R_MASK) >> R_SHIFT;
    int g = (rgb & G_MASK) >> G_SHIFT;
    int b = rgb & B_MASK;
    int max = (r > g) ? r : g;
    if (b > max) {
      max = b;
    }
    int min = (r < g) ? r : g;
    if (b < min) {
      min = b;
    }
    if (max == 0) {
      return 0;
    }
    float range = max - min;
    if (range == 0) {
      return 0;
    }
    float h;
    float rc = (max - r) / range;
    float gc = (max - g) / range;
    float bc = (max - b) / range;
    if (r == max) {
      h = bc - gc;
    } else if (g == max) {
      h = 2.f + rc - bc;
    } else {
      h = 4.f + gc - rc;
    }
    h /= 6.f;
    if (h < 0) {
      h += 1.f;
    }
    return 360.f * h;
  }

  /**
   * Saturation from 0-100
   *
   * @param rgb Color value
   * @return Saturation value from 0-100
   */
  public static float s(int rgb) {
    int r = (rgb & R_MASK) >> R_SHIFT;
    int g = (rgb & G_MASK) >> G_SHIFT;
    int b = rgb & B_MASK;
    int max = (r > g) ? r : g;
    if (b > max) {
      max = b;
    }
    int min = (r < g) ? r : g;
    if (b < min) {
      min = b;
    }
    return (max == 0) ? 0 : (max - min) * 100.f / max;
  }

  private static final float BRIGHTNESS_SCALE = 100f / 255f;

  /**
   * Brightness from 0-100
   *
   * @param rgb Color value
   * @return Brightness from 0-100
   */
  public static float b(int rgb) {
    int r = (rgb & R_MASK) >> R_SHIFT;
    int g = (rgb & G_MASK) >> G_SHIFT;
    int b = rgb & B_MASK;
    int max = (r > g) ? r : g;
    if (b > max) {
      max = b;
    }
    return max * BRIGHTNESS_SCALE;
  }

  /**
   * Luminosity from 0-100, using the quick approximated function:
   * Y = 0.375 R + 0.5 G + 0.125 B
   *
   * @param rgb Color value
   * @return Luminosity from 0-100
   */
  public static float luminosity(int rgb) {
    int r = (rgb & R_MASK) >> R_SHIFT;
    int g = (rgb & G_MASK) >> G_SHIFT;
    int b = rgb & B_MASK;
    return (r+r+r+b+g+g+g+g >> 3) * BRIGHTNESS_SCALE;
  }

  /**
   * Produces a grayscale color based upon value from 0-100
   *
   * @param brightness Brightness value from 0-100
   * @return Gray
   */
  public static int gray(double brightness) {
    int b = 0xff & (int) (brightness * 2.559);
    return
      LXColor.ALPHA_MASK |
      (b << R_SHIFT) |
      (b << G_SHIFT) |
      b;
  }

  /**
   * Produces a grayscale color based upon normalized value from 0-1
   *
   * @param brightness Brightness value from 0-1
   * @return Gray
   */
  public static int grayn(double brightness) {
    int b = 0xff & (int) (brightness * 255.9);
    return
      LXColor.ALPHA_MASK |
      (b << R_SHIFT) |
      (b << G_SHIFT) |
      b;
  }

  /**
   * Produces a grayscale color based upon value from 0-100
   *
   * @param brightness Brightness value from 0-100
   * @return Gray
   */
  public static int gray(float brightness) {
    int b = 0xff & (int) (brightness * 2.559f);
    return
      LXColor.ALPHA_MASK |
      (b  << R_SHIFT) |
      (b  << G_SHIFT) |
      b;
  }

  /**
   * Produces a grayscale color based upon normalized value from 0-1
   *
   * @param brightness Brightness value from 0-1
   * @return Gray
   */
  public static int grayn(float brightness) {
    int b = 0xff & (int) (brightness * 255.9f);
    return
      LXColor.ALPHA_MASK |
      (b  << R_SHIFT) |
      (b  << G_SHIFT) |
      b;
  }

  /**
   * Computes an RGB color value
   *
   * @param r Red 0-255
   * @param g Green 0-255
   * @param b Blue 0-255
   * @return Color
   */
  public static final int rgb(int r, int g, int b) {
    return rgba(r, g, b, 255);
  }

  /**
   * Computes an RGB color value from normalized floating point values
   *
   * @param r Red 0-1
   * @param g Green 0-1
   * @param b Blue 0-1
   * @return Color
   */
  public static final int rgbf(float r, float g, float b) {
    return rgba((int) (255 * r), (int) (255 * g), (int) (255 * b), 255);
  }

  /**
   * Computes an RGB color value
   *
   * @param r Red 0-255
   * @param g Green 0-255
   * @param b Blue 0-255
   * @param a Alpha 0-255
   * @return Color
   */
  public static final int rgba(int r, int g, int b, int a) {
    return
      ((a & 0xff) << ALPHA_SHIFT) |
      ((r & 0xff) << R_SHIFT) |
      ((g & 0xff) << G_SHIFT) |
      (b & 0xff);
  }

  /**
   * Utility to create a color from double values
   *
   * @param h Hue
   * @param s Saturation
   * @param b Brightness
   * @return Color value
   */
  public static final int hsb(double h, double s, double b) {
    return hsb((float) h, (float) s, (float) b);
  }

  /**
   * Utility to create a color from double values
   *
   * @param h Hue
   * @param s Saturation
   * @param b Brightness
   * @param a Alpha
   * @return Color value
   */
  public static final int hsba(double h, double s, double b, double a) {
    return hsba((float) h, (float) s, (float) b, (float) a);
  }

  private static final float H_COEFF = 1 / 360.f;
  private static final float S_COEFF = 1 / 100.f;
  private static final float B_COEFF = 255f / 100f;

  /**
   * Create a color from HSB
   *
   * @param hue Hue from 0-360
   * @param saturation Saturation from 0-100
   * @param brightness Brightness from 0-100
   * @return rgb color value
   */
  public static int hsb(float hue, float saturation, float brightness) {
    int r = 0, g = 0, b = 0;
    float brightness255 = brightness * B_COEFF;
    if (saturation == 0) {
      r = g = b = (int) (brightness255 + 0.5f);
    } else {
      float h1 = hue * H_COEFF;
      float h = (h1 - (float) Math.floor(h1)) * 6.0f;
      float f = h - (float) java.lang.Math.floor(h);
      float s1 = saturation * S_COEFF;
      float p255 = brightness255 * (1.0f - s1);
      float q255 = brightness255 * (1.0f - s1 * f);
      float t255 = brightness255 * (1.0f - (s1 * (1.0f - f)));
      switch ((int) h) {
      case 0:
        r = (int) (brightness255 + 0.5f);
        g = (int) (t255 + 0.5f);
        b = (int) (p255 + 0.5f);
        break;
      case 1:
        r = (int) (q255 + 0.5f);
        g = (int) (brightness255 + 0.5f);
        b = (int) (p255 + 0.5f);
        break;
      case 2:
        r = (int) (p255 + 0.5f);
        g = (int) (brightness255 + 0.5f);
        b = (int) (t255 + 0.5f);
        break;
      case 3:
        r = (int) (p255 + 0.5f);
        g = (int) (q255 + 0.5f);
        b = (int) (brightness255 + 0.5f);
        break;
      case 4:
        r = (int) (t255 + 0.5f);
        g = (int) (p255 + 0.5f);
        b = (int) (brightness255 + 0.5f);
        break;
      case 5:
        r = (int) (brightness255 + 0.5f);
        g = (int) (p255 + 0.5f);
        b = (int) (q255 + 0.5f);
        break;
      }
    }
    return 0xff000000 | (r << 16) | (g << 8) | (b << 0);
  }

  /**
   * Create a color from HSA, where brightness is always full
   *
   * @param h Hue from 0-360
   * @param s Saturation from 0-100
   * @param a Alpha mask from 0-1
   * @return argb color value
   */
  public static final int hsa(float h, float s, float a) {
    return hsba(h, s, 100, a);
  }

  /**
   * Create a color from HSB
   *
   * @param h Hue from 0-360
   * @param s Saturation from 0-100
   * @param b Brightness from 0-100
   * @param a Alpha from 0-1
   * @return argb color value
   */
  public static int hsba(float h, float s, float b, float a) {
    return
      (min(0xff, (int) (a * 0xff)) << ALPHA_SHIFT) |
      (hsb(h, s, b) & 0x00ffffff);
  }

  /**
   * Blends the two colors using specified blend based on the alpha channel of c2
   *
   * @param dst Background color
   * @param src Overlay color to be blended
   * @param blendMode Type of blending
   * @return Blended color
   */
  public static int blend(int dst, int src, Blend blendMode) {
    switch (blendMode) {
    case ADD:
      return add(dst, src, 0x100);
    case SUBTRACT:
      return subtract(dst, src, 0x100);
    case MULTIPLY:
      return multiply(dst, src, 0x100);
    case SCREEN:
      return screen(dst, src, 0x100);
    case LIGHTEST:
      return lightest(dst, src, 0x100);
    case DARKEST:
      return darkest(dst, src, 0x100);
    case DODGE:
      return dodge(dst, src, 0x100);
    case BURN:
      return burn(dst, src, 0x100);
    case HIGHLIGHT:
      return highlight(dst, src, 0x100);
    case SPOTLIGHT:
      return spotlight(dst, src, 0x100);
    case LERP:
      return lerp(dst, src, 0x100);
    }
    throw new IllegalArgumentException("Unimplemented blend mode: " + blendMode);
  }

  public static int lerp(int dst, int src) {
    return lerp(dst, src, 0x100);
  }

  public static int lerp(int dst, int src, double alpha) {
    return lerp(dst, src, (int) (alpha * 0x100));
  }

  public static int lerp(int dst, int src, float alpha) {
    return lerp(dst, src, (int) (alpha * 0x100));
  }

  public static int lerp(int dst, int src, int alpha) {
    int a = (((src >>> ALPHA_SHIFT) * alpha) >> 8) & 0xff;
    int srcAlpha = a + (a >= 0x7F ? 1 : 0);
    int dstAlpha = 0x100 - srcAlpha;
    return
      min((dst >>> ALPHA_SHIFT) + a, 0xff) << ALPHA_SHIFT |
      ((dst & RB_MASK) * dstAlpha + (src & RB_MASK) * srcAlpha) >>> 8 & RB_MASK |
      ((dst & G_MASK) * dstAlpha + (src & G_MASK) * srcAlpha) >>> 8 & G_MASK;
  }

  /**
   * Adds the specified colors
   *
   * @param dst Background color
   * @param src Overlay color
   * @return Summed RGB channels with 255 clip
   */
  public static int add(int dst, int src) {
    return add(dst, src, 0x100);
  }

  /**
   * Adds the specified colors
   *
   * @param dst Background color
   * @param src Overlay color
   * @param alpha Level of blending from 0-1
   * @return Summed RGB channels with 255 clip
   */
  public static int add(int dst, int src, double alpha) {
    return add(dst, src, (int) (alpha * 0x100));
  }

  /**
   * Adds the specified colors
   *
   * @param dst Background color
   * @param src Overlay color
   * @param alpha Alpha adjustment (from 0x00 - 0x100)
   * @return Summed RGB channels with 255 clip
   */
  public static int add(int dst, int src, int alpha) {
    int a = (((src >>> ALPHA_SHIFT) * alpha) >> 8) & 0xff;
    int srcAlpha = a + (a >= 0x7F ? 1 : 0);
    int rb = (dst & RB_MASK) + ((src & RB_MASK) * srcAlpha >>> 8 & RB_MASK);
    int gn = (dst & G_MASK) + ((src & G_MASK) * srcAlpha >>> 8);
    return
      min((dst >>> ALPHA_SHIFT) + a, 0xff) << ALPHA_SHIFT |
      min(rb & 0xffff0000, R_MASK) |
      min(gn & 0x00ffff00, G_MASK) |
      min(rb & 0x0000ffff, B_MASK);
  }

  public static int subtract(int dst, int src) {
    return subtract(dst, src, 0x100);
  }

  public static int subtract(int dst, int src, double alpha) {
    return subtract(dst, src, (int) (alpha * 0x100));
  }

  public static int subtract(int dst, int src, int alpha) {
    int a = (((src >>> ALPHA_SHIFT) * alpha) >> 8) & 0xff;
    int srcAlpha = a + (a >= 0x7F ? 1 : 0);
    int rb = (src & RB_MASK) * srcAlpha >>> 8;
    int gn = (src & G_MASK) * srcAlpha >>> 8;
    return
      min((dst >>> ALPHA_SHIFT) + a, 0xff) << ALPHA_SHIFT |
      max((dst & R_MASK) - (rb & R_MASK), 0) |
      max((dst & G_MASK) - (gn & G_MASK), 0) |
      max((dst & B_MASK) - (rb & B_MASK), 0);
  }

  public static int multiply(int dst, int src) {
    return multiply(dst, src, 0x100);
  }

  public static int multiply(int dst, int src, double alpha) {
    return multiply(dst, src, (int) (alpha * 0x100));
  }

  public static int multiply(int dst, int src, int alpha) {
    int a = (((src >>> ALPHA_SHIFT) * alpha) >> 8) & 0xff;
    int srcAlpha = a + (a >= 0x7F ? 1 : 0);
    int dstAlpha = 0x100 - srcAlpha;

    int dstG = (dst & G_MASK);
    int dstR = (dst & R_MASK) >> R_SHIFT;
    int dstB = (dst & B_MASK);

    int rb = ((src & R_MASK) * (dstR + 1) | (src & B_MASK) * (dstB + 1)) >>> 8 & RB_MASK;
    int g = (src & G_MASK) * (dstG + 0x100) >>> 16 & G_MASK;

    return
      min((dst >>> ALPHA_SHIFT) + a, 0xff) << ALPHA_SHIFT |
      ((dst & RB_MASK) * dstAlpha + rb * srcAlpha) >>> 8 & RB_MASK |
      (dstG * dstAlpha + g * srcAlpha) >>> 8 & G_MASK;
  }

  public static int screen(int dst, int src) {
    return screen(dst, src, 0x100);
  }

  public static int screen(int dst, int src, double alpha) {
    return screen(dst, src, (int) (alpha * 0x100));
  }

  public static int screen(int dst, int src, int alpha) {
    int a = (((src >>> ALPHA_SHIFT) * alpha) >> 8) & 0xff;
    int srcAlpha = a + (a >= 0x7F ? 1 : 0);
    int dstAlpha = 0x100 - srcAlpha;

    int dstRb = dst & RB_MASK;
    int dstGn = dst & G_MASK;
    int srcGn = src & G_MASK;
    int dstR = (dst & R_MASK) >> R_SHIFT;
    int dstB = dst & B_MASK;

    int rbSub = (
        (src & R_MASK) * (dstR + 1) |
        (src & B_MASK) * (dstB + 1)
      ) >>> 8 & RB_MASK;
    int gnSub = srcGn * (dstGn + 0x100) >> 16 & G_MASK;

    return
      min((dst >>> ALPHA_SHIFT) + a, 0xff) << ALPHA_SHIFT |
      (dstRb * dstAlpha + (dstRb + (src & RB_MASK) - rbSub) * srcAlpha) >>> 8 & RB_MASK |
      (dstGn * dstAlpha + (dstGn + srcGn - gnSub) * srcAlpha) >>> 8 & G_MASK;
  }

  public static int lightest(int dst, int src) {
    return lightest(dst, src, 0x100);
  }

  public static int lightest(int dst, int src, double alpha) {
    return lightest(dst, src, (int) (alpha * 0x100));
  }

  public static int lightest(int dst, int src, int alpha) {
    int a = (((src >>> ALPHA_SHIFT) * alpha) >> 8) & 0xff;
    int srcAlpha = a + (a >= 0x7F ? 1 : 0);
    int dstAlpha = 0x100 - srcAlpha;
    int rb =
      max(src & R_MASK, dst & R_MASK) |
      max(src & B_MASK, dst & B_MASK);
    int gn = max(src & G_MASK, dst & G_MASK);
    return
      min((dst >>> ALPHA_SHIFT) + a, 0xff) << ALPHA_SHIFT |
      (((dst & RB_MASK) * dstAlpha + rb * srcAlpha) >>> 8) & RB_MASK |
      (((dst & G_MASK) * dstAlpha + gn * srcAlpha) >>> 8) & G_MASK;
  }

  public static int darkest(int dst, int src) {
    return darkest(dst, src, 0x100);
  }

  public static int darkest(int dst, int src, double alpha) {
    return darkest(dst, src, (int) (alpha * 0x100));
  }

  public static int darkest(int dst, int src, int alpha) {
    int a = (((src >>> ALPHA_SHIFT) * alpha) >> 8) & 0xff;
    int srcAlpha = a + (a >= 0x7F ? 1 : 0);
    int dstAlpha = 0x100 - srcAlpha;
    int rb =
      min(src & R_MASK, dst & R_MASK) |
      min(src & B_MASK, dst & B_MASK);
    int gn = min(src & G_MASK, dst & G_MASK);
    return
      min((dst >>> ALPHA_SHIFT) + a, 0xff) << ALPHA_SHIFT |
      (((dst & RB_MASK) * dstAlpha + rb * srcAlpha) >>> 8) & RB_MASK |
      (((dst & G_MASK) * dstAlpha + gn * srcAlpha) >>> 8) & G_MASK;
  }

  public static int difference(int dst, int src) {
    return difference(dst, src, 0x100);
  }

  public static int difference(int dst, int src, double alpha) {
    return difference(dst, src, (int) (alpha * 0x100));
  }

  public static int difference(int dst, int src, int alpha) {
    int a = (((src >>> ALPHA_SHIFT) * alpha) >> 8) & 0xff;
    int srcAlpha = a + (a >= 0x7F ? 1 : 0);
    int dstAlpha = 0x100 - srcAlpha;
    int r = (dst & R_MASK) - (src & R_MASK);
    int g = (dst & G_MASK) - (src & G_MASK);
    int b = (dst & B_MASK) - (src & B_MASK);
    int rb = (r < 0 ? -r : r) | (b < 0 ? -b : b);
    int gn = g < 0 ? -g : g;
    return
      min((dst >>> ALPHA_SHIFT) + a, 0xff) << ALPHA_SHIFT |
      ((dst & RB_MASK) * dstAlpha + rb * srcAlpha) >>> 8 & RB_MASK |
      ((dst & G_MASK) * dstAlpha + gn * srcAlpha) >>> 8 & G_MASK;
  }

  public static int dodge(int dst, int src) {
    return dodge(dst, src, 0x100);
  }

  public static int dodge(int dst, int src, double alpha) {
    return dodge(dst, src, (int) (alpha * 0x100));
  }

  public static int dodge(int dst, int src, int alpha) {
    int a = (((src >>> ALPHA_SHIFT) * alpha) >> 8) & 0xff;

    int srcAlpha = a + (a >= 0x7f ? 1 : 0);
    int dstAlpha = 0x100 - srcAlpha;
    int r = (dst & R_MASK) / (256 - ((src & R_MASK) >> R_SHIFT));
    int g = ((dst & G_MASK) << 8) / (256 - ((src & G_MASK) >> G_SHIFT));
    int b = ((dst & B_MASK) << 8) / (256 - (src & B_MASK));
    int rb =
      (r > 0xff00 ? R_MASK : ((r << 8) & R_MASK)) |
      (b > 0x00ff ? B_MASK : b);
    int gn = (g > 0xff00 ? G_MASK : (g & G_MASK));

    return
      min((dst >>> ALPHA_SHIFT) + a, 0xff) << ALPHA_SHIFT |
      ((dst & RB_MASK) * dstAlpha + rb * srcAlpha) >>> 8 & RB_MASK |
      ((dst & G_MASK) * dstAlpha + gn * srcAlpha) >>> 8 & G_MASK;
  }

  public static int burn(int dst, int src) {
    return burn(dst, src, 0x100);
  }

  public static int burn(int dst, int src, double alpha) {
    return burn(dst, src, (int) (alpha * 0x100));
  }

  public static int burn(int dst, int src, int alpha) {
    int a = (((src >>> ALPHA_SHIFT) * alpha) >> 8) & 0xff;

    int srcAlpha = a + (a >= 0x7f ? 1 : 0);
    int dstAlpha = 0x100 - srcAlpha;

    int r = ((R_MASK - (dst & R_MASK)))  / (1 + (src & R_MASK >> R_SHIFT));
    int g = ((G_MASK - (dst & G_MASK)) << 8) / (1 + (src & G_MASK >> G_SHIFT));
    int b = ((B_MASK - (dst & B_MASK)) << 8) / (1 + (src & B_MASK));

    int rb = RB_MASK -
        (r > 0xff00 ? R_MASK : ((r << 8) & R_MASK)) -
        (b > 0x00ff ? B_MASK : b);
    int gn = G_MASK - (g > 0xff00 ? G_MASK : (g & G_MASK));

    return min((dst >>> 24) + a, 0xFF) << 24 |
        ((dst & RB_MASK) * dstAlpha + rb * srcAlpha) >>> 8 & RB_MASK |
        ((dst & G_MASK) * dstAlpha + gn * srcAlpha) >>> 8 & G_MASK;
  }

  public static int highlight(int dst, int src) {
    return highlight(dst, src, 0x100);
  }

  public static int highlight(int dst, int src, double alpha) {
    return highlight(dst, src, (int) (alpha * 0x100));
  }

  public static int highlight(int dst, int src, int alpha) {
    return add(dst, multiply(dst, src, 0x100), alpha);
  }

  public static int spotlight(int dst, int src) {
    return spotlight(dst, src, 0x100);
  }

  public static int spotlight(int dst, int src, double alpha) {
    return spotlight(dst, src, (int) (alpha * 0x100));
  }

  public static int spotlight(int dst, int src, int alpha) {
    int dstMax = LXUtils.max(
      dst & B_MASK,
      (dst & G_MASK) >> G_SHIFT,
      (dst & R_MASK) >> R_SHIFT
    );
    int dstMlt =
      (dst & ALPHA_MASK) |
      (dstMax << R_SHIFT) |
      (dstMax << G_SHIFT) |
      dstMax;
    return add(dst, multiply(dstMlt, src, 0x100), alpha);
  }

  private static int min(int a, int b) {
    return (a < b) ? a : b;
  }

  private static int max(int a, int b) {
    return (a > b) ? a : b;
  }

  /**
   * Map a pixel buffer onto a buffer of different size
   *
   * @param src Source buffer
   * @param srcNum Number of source pixels
   * @param dst Destination buffer
   * @param dstNum Number of destination pixels
   */
  public static void map(int[] src, int srcNum, int[] dst, int dstNum) {
    map(src, 0, srcNum, dst, 0, dstNum);
  }

  /**
   * Map a pixel buffer onto a buffer of different size
   *
   * @param src Source buffer
   * @param srcOffset Offset in source buffer
   * @param srcNum Number of source pixels
   * @param dst Destination buffer
   * @param dstOffset Offset in destination buffer
   * @param dstNum Number of destination pixels
   */
  public static void map(int[] src, int srcOffset, int srcNum, int[] dst, int dstOffset, int dstNum) {
    for (int i = 0; i < dstNum; ++i) {
      int srcIndex = (int) ((srcNum - 1.) * (i / (dstNum - 1.)));
      dst[dstOffset + i] = src[srcOffset + srcIndex];
    }
  }

  /**
   * Map a pixel buffer onto a buffer of different size, with color interpolation
   *
   * @param src Source buffer
   * @param srcNum Number of source pixels
   * @param dst Destination buffer
   * @param dstNum Number of destination pixels
   */
  public static void maplerp(int[] src, int srcNum, int[] dst, int dstNum) {
    maplerp(src, 0, srcNum, dst, 0, dstNum);
  }

  /**
   * Map a pixel buffer onto a buffer of different size, with color interpolation
   *
   * @param src Source buffer
   * @param srcOffset Offset in source buffer
   * @param srcNum Number of source pixels
   * @param dst Destination buffer
   * @param dstOffset Offset in destination buffer
   * @param dstNum Number of destination pixels
   */
  public static void maplerp(int[] src, int srcOffset, int srcNum, int[] dst, int dstOffset, int dstNum) {
    for (int i = 0; i < dstNum; ++i) {
      double srcIndex = (int) ((srcNum - 1.) * (i / (dstNum - 1.)));
      int srcInt = (int) srcIndex;
      double lerp = srcIndex - srcInt;
      if ((lerp > 0) && (srcInt < srcNum - 1)) {
        dst[dstOffset + i] = LXColor.lerp(
          src[srcOffset + srcInt],
          src[srcOffset + srcInt + 1],
          lerp
        );
      } else {
        dst[dstOffset + i] = src[srcOffset + srcInt];
      }
    }
  }


}
