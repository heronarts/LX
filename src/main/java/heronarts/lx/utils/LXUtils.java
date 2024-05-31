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

package heronarts.lx.utils;

import heronarts.lx.LX;

/**
 * Helper class of useful utilities, many just mirror Processing built-ins but
 * reduce the awkwardness of calling through applet in the library code.
 */
public class LXUtils {

  /**
   * Only used statically, need not be instantiated.
   */
  private LXUtils() {
  }

  public static int min(int a, int b) {
    return a < b ? a : b;
  }

  public static int min(int a, int b, int c) {
    return (a < b) ? ((a < c) ? a : c) : ((b < c) ? b : c);
  }

  public static int max(int a, int b) {
    return a < b ? b : a;
  }

  public static int max(int a, int b, int c) {
    return (a > b) ? ((a > c) ? a : c) : ((b > c) ? b : c);
  }

  public static float minf(float a, float b) {
    return a < b ? a : b;
  }

  public static float minf(float a, float b, float c) {
    return (a < b) ? ((a < c) ? a : c) : ((b < c) ? b : c);
  }

  public static float maxf(float a, float b) {
    return a < b ? b : a;
  }

  public static float maxf(float a, float b, float c) {
    return (a > b) ? ((a > c) ? a : c) : ((b > c) ? b : c);
  }

  public static double min(double a, double b) {
    return a < b ? a : b;
  }

  public static double min(double a, double b, double c) {
    return (a < b) ? ((a < c) ? a : c) : ((b < c) ? b : c);
  }

  public static double max(double a, double b) {
    return a < b ? b : a;
  }

  public static double max(double a, double b, double c) {
    return (a > b) ? ((a > c) ? a : c) : ((b > c) ? b : c);
  }

  public static double clamp(double value, double min, double max) {
    return value < min ? min : (value > max ? max : value);
  }

  public static float clampf(float value, float min, float max) {
    return value < min ? min : (value > max ? max : value);
  }

  public static int clamp(int value, int min, int max) {
    return value < min ? min : (value > max ? max : value);
  }

  public static double constrain(double value, double min, double max) {
    return value < min ? min : (value > max ? max : value);
  }

  public static float constrainf(float value, float min, float max) {
    return value < min ? min : (value > max ? max : value);
  }

  public static int constrain(int value, int min, int max) {
    return value < min ? min : (value > max ? max : value);
  }

  public static double wrapn(double value) {
    if (value > 1.) {
      return value % 1.;
    } else if (value < 0.) {
      return 1. + (value % 1.);
    }
    return value;
  }

  public static float wrapnf(float value) {
    if (value > 1f) {
      return value % 1f;
    } else if (value < 0f) {
      return 1f + (value % 1f);
    }
    return value;
  }

  public static int wrap(int value, int min, int max) {
    if (value > max) {
      return min + (value - max) % (max - min);
    } else if (value < min) {
      return max + (value - min) % (max - min);
    }
    return value;
  }

  public static double wrap(double value, double min, double max) {
    if (value > max) {
      return min + (value - max) % (max - min);
    } else if (value < min) {
      return max + (value - min) % (max - min);
    }
    return value;
  }

  public static float wrapf(float value, float min, float max) {
    if (value > max) {
      return min + (value - max) % (max - min);
    } else if (value < min) {
      return max + (value - min) % (max - min);
    }
    return value;
  }

  public static int randomi(int max) {
    return randomi(0, max);
  }

  public static int randomi(int min, int max) {
    return (int) constrain(random(min, max+1), min, max);
  }

  public static double random(double max) {
    return random(0, max);
  }

  public static double random(double min, double max) {
    return min + Math.random() * (max - min);
  }

  public static float randomf(float max) {
    return randomf(0, max);
  }

  public static float randomf(float min, float max) {
    return min + (float) Math.random() * (max-min);
  }

  public static double distance(double x1, double y1, double x2, double y2) {
    return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
  }

  public static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
    return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2) + (z1 - z2) * (z1 - z2));
  }

  public static double lerp(double v1, double v2, double amt) {
    return v1 + (v2 - v1) * amt;
  }

  public static float lerpf(float v1, float v2, float amt) {
    return v1 + (v2 - v1) * amt;
  }

  public static int lerpi(int v1, int v2, float amt) {
    return Math.round(v1 + (v2 - v1) * amt);
  }

  /**
   * Returns true if value is between [min, max] inclusive
   *
   * @param val Value
   * @param min Min value
   * @param max Max value
   * @return True if contained in range
   */
  public static boolean inRange(int val, int min, int max) {
    return (val >= min) && (val <= max);
  }

  /**
   * Returns true if value is between [min, max] inclusive
   *
   * @param val Value
   * @param min Min value
   * @param max Max value
   * @return True if contained in range
   */
  public static boolean inRange(float val, float min, float max) {
    return (val >= min) && (val <= max);
  }

  /**
   * Returns true if value is between [min, max] inclusive
   *
   * @param val Value
   * @param min Min value
   * @param max Max value
   * @return True if contained in range
   */
  public static boolean inRange(double val, double min, double max) {
    return (val >= min) && (val <= max);
  }

  /**
   * Inverse linear interpolation, normalizes a value relative to bounds
   *
   * @param amt Interpolated value
   * @param v1 Start value
   * @param v2 End value
   * @return Normalized value in range 0-1 for amt in [v1,v2]
   */
  public static float ilerpf(float amt, float v1, float v2) {
    return (amt - v1) / (v2 - v1);
  }

  /**
   * Inverse linear interpolation, normalizes a value relative to bounds
   *
   * @param amt Interpolated value
   * @param v1 Start value
   * @param v2 End value
   * @return Normalized value in range 0-1 for amt in [v1,v2]
   */
  public static double ilerp(double amt, double v1, double v2) {
    return (amt - v1) / (v2 - v1);
  }

  public static double tri(double t) {
    t = t - Math.floor(t);
    if (t < 0.25) {
      return t * 4;
    } else if (t < 0.75) {
      return 1 - 4 * (t - 0.25);
    } else {
      return -1 + 4 * (t - 0.75);
    }
  }

  public static float trif(float t) {
    return (float) LXUtils.tri(t);
  }

  public static double avg(double v1, double v2) {
    return (v1 + v2) / 2.;
  }

  public static float avgf(float v1, float v2) {
    return (v1 + v2) / 2.f;
  }

  public static double dist(double x1, double y1, double x2, double y2) {
    return Math.sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1));
  }

  public static float distf(float x1, float y1, float x2, float y2) {
    return (float) Math.sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1));
  }

  public static double dist(double x1, double y1, double z1, double x2, double y2, double z2) {
    return Math.sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1)+(z2-z1)*(z2-z1));
  }

  public static float distf(float x1, float y1, float z1, float x2, float y2, float z2) {
    return (float) Math.sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1)+(z2-z1)*(z2-z1));
  }

  /**
   * Computes the distance between v1 and v2 with a wrap-around at the modulus.
   * Both v1 and v2 must be in the range [0, modulus]. For example, if v1=1, v2=11,
   * and modulus = 12, then the distance is 2, not 10.
   *
   * @param v1 First value
   * @param v2 Second value
   * @param modulus Modulus to wrap around
   * @return shortest distance between v1-v2 wrapping around the modulus
   */
  public static double wrapdist(double v1, double v2, double modulus) {
    if (v1 < v2) {
      return Math.min(v2 - v1, v1 + modulus - v2);
    } else {
      return Math.min(v1 - v2, v2 + modulus - v1);
    }
  }

  public static float wrapdistf(float v1, float v2, float modulus) {
    if (v1 < v2) {
      return Math.min(v2 - v1, v1 + modulus - v2);
    } else {
      return Math.min(v1 - v2, v2 + modulus - v1);
    }
  }

  /**
   * Returns a floating-point rounded value of the sin function to 8 decimal places.
   * This is often useful because Math.sin(Math.PI) is NOT actually 0.
   *
   * @param radians Radians to take sin of
   * @return Result rounded to 8 decimal places
   */
  public static float sinf(double radians) {
    return Math.round(Math.sin(radians) * 1e8) / 1e8f;
  }

  /**
   * Returns a floating-point rounded value of the cos function to 8 decimal places.
   * This is often useful because Math.sin(Math.PI) is NOT actually 0.
   *
   * @param radians Radians to take cos of
   * @return Result rounded to 8 decimal places
   */
  public static float cosf(double radians) {
    return Math.round(Math.cos(radians) * 1e8) / 1e8f;
  }

  /**
   * Returns the result of Math.atan2 pushed into the positive space [0-TWO_PI]
   *
   * @param y Y
   * @param x X
   * @return Result of atan2, in the range 0-TWO_PI
   */
  public static float atan2pf(float y, float x) {
    return (float) ((LX.TWO_PI + Math.atan2(y, x)) % LX.TWO_PI);
  }

  /**
   * Returns a floating-point rounded value of the tan function to 8 decimal places.
   * This is often useful because Math.sin(Math.PI) is NOT actually 0.
   *
   * @param radians Radians to take cos of
   * @return Result rounded to 8 decimal places
   */
  public static float tanf(double radians) {
    return Math.round(Math.tan(radians) * 1e8) / 1e8f;
  }

  /**
   * This function computes a random value at the coordinate (x,y,z).
   * Adjacent random values are continuous but the noise fluctuates
   * its randomness with period 1, i.e. takes on wholly unrelated values
   * at integer points. Specifically, this implements Ken Perlin's
   * revised noise function from 2002.
   *
   * @param x X coordinate
   * @param y Y coordinate
   * @param z Z coordinate
   * @return Noise value
   */
  public static float noise(float x, float y, float z) {
    return Noise.stb_perlin_noise3(x, y, z, 0, 0, 0);
  }

  /**
   * This function computes a random value at the coordinate (x,y,z).
   * Adjacent random values are continuous but the noise fluctuates
   * its randomness with period 1, i.e. takes on wholly unrelated values
   * at integer points. Specifically, this implements Ken Perlin's
   * revised noise function from 2002.
   *
   * @param x X coordinate
   * @param y Y coordinate
   * @param z Z coordinate
   * @param seed Seed
   * @return Noise value
   */
  public static float noise(float x, float y, float z, int seed) {
    return Noise.stb_perlin_noise3_seed(x, y, z, 0, 0, 0, seed);
  }


  /**
   * Common fractal noise functions are included, which produce
   * a wide variety of nice effects depending on the parameters
   * provided. Note that each function will call stb_perlin_noise3
   * 'octaves' times, so this parameter will affect runtime.
   *
   * @param x X coordinate
   * @param y Y coordinate
   * @param z Z coordinate
   * @param lacunarity spacing between successive octaves (use exactly 2.0 for wrapping output)
   * @param gain relative weighting applied to each successive octave
   * @param offset used to invert the ridges, may need to be larger, not sure
   * @param octaves number of "octaves" of noise3() to sum
   * @return Noise value
   */
  public static float noiseRidge(float x, float y, float z, float lacunarity, float gain, float offset, int octaves) {
    return Noise.stb_perlin_ridge_noise3(x, y, z, lacunarity, gain, offset, octaves);
  }

  /**
   * Common fractal noise functions are included, which produce
   * a wide variety of nice effects depending on the parameters
   * provided. Note that each function will call stb_perlin_noise3
   * 'octaves' times, so this parameter will affect runtime.
   *
   * @param x X coordinate
   * @param y Y coordinate
   * @param z Z coordinate
   * @param lacunarity spacing between successive octaves (use exactly 2.0 for wrapping output)
   * @param gain relative weighting applied to each successive octave
   * @param octaves number of "octaves" of noise3() to sum
   * @return Noise value
   */
  public static float noiseFBM(float x, float y, float z, float lacunarity, float gain, int octaves) {
    return Noise.stb_perlin_fbm_noise3(x, y, z, lacunarity, gain, octaves);
  }

  /**
   * Common fractal noise functions are included, which produce
   * a wide variety of nice effects depending on the parameters
   * provided. Note that each function will call stb_perlin_noise3
   * 'octaves' times, so this parameter will affect runtime.
   *
   * @param x X coordinate
   * @param y Y coordinate
   * @param z Z coordinate
   * @param lacunarity spacing between successive octaves (use exactly 2.0 for wrapping output)
   * @param gain relative weighting applied to each successive octave
   * @param octaves number of "octaves" of noise3() to sum
   * @return Noise value
   */
  public static float noiseTurbulence(float x, float y, float z, float lacunarity, float gain, int octaves) {
    return Noise.stb_perlin_turbulence_noise3(x, y, z, lacunarity, gain, octaves);
  }

  public static class LookupTable {

    public interface Function {

      public float compute(int i, int tableSize);

      public static Function SIN = (i, tableSize) -> {
        return (float) Math.sin(i * LX.TWO_PI / tableSize);
      };

      static Function COS = (i, tableSize) -> {
        return (float) Math.cos(i * LX.TWO_PI / tableSize);
      };

      static Function TAN = (i, tableSize) -> {
        return (float) Math.tan(i * LX.TWO_PI / tableSize);
      };

    }

    public static class Sin extends LookupTable {
      public Sin(int tableSize) {
        super(tableSize, Function.SIN);
      }

      public float sin(float radians) {
        int index = (int) Math.round(Math.abs(radians) / LX.TWO_PI * this.tableSize);
        float val = this.values[index % this.values.length];
        return (radians > 0) ? val : -val;
      }
    }

    public static class Cos extends LookupTable {
      public Cos(int tableSize) {
        super(tableSize, Function.COS);
      }

      public float cos(float radians) {
        int index = (int) Math.round(Math.abs(radians) / LX.TWO_PI * this.tableSize);
        return this.values[index % this.values.length];
      }
    }

    public static class Tan extends LookupTable {
      public Tan(int tableSize) {
        super(tableSize, Function.TAN);
      }

      public float tan(float radians) {
        int index = (int) Math.round(Math.abs(radians) / LX.TWO_PI * this.tableSize);
        float val = this.values[index % this.values.length];
        return (radians > 0) ? val : -val;
      }
    }

    protected final int tableSize;
    protected float[] values;

    public LookupTable(int tableSize, Function function) {
      this.tableSize = tableSize;
      this.values = new float[tableSize + 1];
      for (int i = 0; i <= tableSize; ++i) {
        this.values[i] = function.compute(i, tableSize);
      }
    }

    /**
     * Looks up the value in the table
     *
     * @param basis Basis
     * @return value nearest to the basis
     */
    public float get(float basis) {
      return this.values[Math.round(basis * this.tableSize)];
    }

    public float get(double basis) {
      return this.values[(int) Math.round(basis * this.tableSize)];
    }
  }

  public static boolean isEmpty(String s) {
    return s == null || s.trim().isEmpty();
  }
}
