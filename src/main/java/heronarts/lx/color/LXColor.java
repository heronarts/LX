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
    DARKEST
  }

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

  public static final int ALPHA_SHIFT = 24;
  public static final int R_SHIFT = 16;
  public static final int G_SHIFT = 8;

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
    return 100.f * max / 255.f;
  }

  public static int gray(double brightness) {
    int b = 0xff & (int) (255 * (brightness / 100.));
    return
      0xff000000 |
      ((b & 0xff) << R_SHIFT) |
      ((b & 0xff) << G_SHIFT) |
      (b & 0xff);
  }

  public static int gray(float brightness) {
    int b = 0xff & (int) (255 * (brightness / 100.f));
    return
      0xff000000 |
      ((b & 0xff) << R_SHIFT) |
      ((b & 0xff) << G_SHIFT) |
      (b & 0xff);
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
  private static final float B_COEFF = 1 / 100.f;

  /**
   * Create a color from HSB
   *
   * @param h Hue from 0-360
   * @param s Saturation from 0-100
   * @param b Brightness from 0-100
   * @return rgb color value
   */
  public static int hsb(float h, float s, float b) {
    return _hsbImpl(h * H_COEFF, s * S_COEFF, b * B_COEFF);
  }

  public static int _hsbImpl(float hue, float saturation, float brightness) {
    int r = 0, g = 0, b = 0;
    if (saturation == 0) {
      r = g = b = (int) (brightness * 255.f + 0.5f);
    } else {
      float h = (hue - (float) Math.floor(hue)) * 6.0f;
      float f = h - (float) java.lang.Math.floor(h);
      float p = brightness * (1.0f - saturation);
      float q = brightness * (1.0f - saturation * f);
      float t = brightness * (1.0f - (saturation * (1.0f - f)));
      switch ((int) h) {
      case 0:
        r = (int) (brightness * 255.0f + 0.5f);
        g = (int) (t * 255.0f + 0.5f);
        b = (int) (p * 255.0f + 0.5f);
        break;
      case 1:
        r = (int) (q * 255.0f + 0.5f);
        g = (int) (brightness * 255.0f + 0.5f);
        b = (int) (p * 255.0f + 0.5f);
        break;
      case 2:
        r = (int) (p * 255.0f + 0.5f);
        g = (int) (brightness * 255.0f + 0.5f);
        b = (int) (t * 255.0f + 0.5f);
        break;
      case 3:
        r = (int) (p * 255.0f + 0.5f);
        g = (int) (q * 255.0f + 0.5f);
        b = (int) (brightness * 255.0f + 0.5f);
        break;
      case 4:
        r = (int) (t * 255.0f + 0.5f);
        g = (int) (p * 255.0f + 0.5f);
        b = (int) (brightness * 255.0f + 0.5f);
        break;
      case 5:
        r = (int) (brightness * 255.0f + 0.5f);
        g = (int) (p * 255.0f + 0.5f);
        b = (int) (q * 255.0f + 0.5f);
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
      return add(dst, src);
    case SUBTRACT:
      return subtract(dst, src);
    case MULTIPLY:
      return multiply(dst, src);
    case SCREEN:
      return screen(dst, src);
    case LIGHTEST:
      return lightest(dst, src);
    case DARKEST:
      return darkest(dst, src);
    case LERP:
      return lerp(dst, src);
    }
    throw new IllegalArgumentException("Unimplemented blend mode: " + blendMode);
  }

  public static int lerp(int dst, int src) {
    return add(dst, src, 0x100);
  }

  public static int lerp(int dst, int src, double alpha) {
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

  private static int min(int a, int b) {
    return (a < b) ? a : b;
  }

  private static int max(int a, int b) {
    return (a > b) ? a : b;
  }

}
