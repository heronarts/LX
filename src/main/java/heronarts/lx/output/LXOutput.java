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

package heronarts.lx.output;

import java.net.InetAddress;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.BooleanParameter;

/**
 * This class represents the output stage from the LX engine to real devices.
 * Outputs may have their own brightness, be enabled/disabled, be throttled,
 * etc.
 */
public abstract class LXOutput extends LXComponent {

  public static final float MAX_FRAMES_PER_SECOND = 300;

  public interface InetOutput {

    public static final int NO_PORT = -1;

    public InetOutput setAddress(InetAddress address);
    public InetAddress getAddress();
    public InetOutput setPort(int port);
    public int getPort();
  }

  public static class GammaTable {

    public static final int NUM_STEPS = 256;
    public static final int WHITE_POINT_MAX = 255;

    public static class Curve {

      // Each of these is a length-255 array mapping input color
      // byte values to output color byte values
      public final byte[] red;
      public final byte[] green;
      public final byte[] blue;
      public final byte[] white;

      private Curve() {
        this(new byte[NUM_STEPS], new byte[NUM_STEPS], new byte[NUM_STEPS], new byte[NUM_STEPS]);
      }

      private Curve(byte[] red, byte[] green, byte[] blue, byte[] white) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.white = white;
      }

      private static final double INV_255_2 = 1. / (255. * 255.);
      private static final double INV_255_3 = 1. / (255. * 255. * 255.);

      /**
       * Generate an output curve with the given parameters
       *
       * @param output Output byte lookup table
       * @param brightness Brightness value 0-255
       * @param gamma Gamma
       * @param whitePoint White point 0-255
       */
      public static void generate(byte[] output, int brightness, double gamma, double whitePoint) {
        if (gamma == 1) {
          for (int in = 0; in < NUM_STEPS; ++in) {
            output[in] = (byte) (0xff & (int) Math.round(in * brightness * whitePoint * INV_255_2));
          }
        } else {
          for (int in = 0; in < NUM_STEPS; ++in) {
            output[in] = (byte) (0xff & (int) Math.round(Math.pow(in * brightness * whitePoint * INV_255_3, gamma) * 255.f));
          }
        }
      }
    }

    /**
     * Curve lookup tables for each of 255 precomputed brightness stages,
     * each curve contains in->out mappings for red, green, blue, and white pixels
     */
    public final Curve[] level = new Curve[NUM_STEPS];

    public GammaTable() {
      for (int i = 0; i < this.level.length; ++i) {
        this.level[i] = new Curve();
      }
    }

    /**
     * Generate the gamma curve tables for RGBW tables
     *
     * @param gamma Gamma level
     * @param whitePointRed White point for red (0-255)
     * @param whitePointGreen White point for green (0-255)
     * @param whitePointBlue White point for blue (0-255)
     * @param whitePointWhite White point for white (0-255)
     * @return this
     */
    public GammaTable generate(double gamma, double whitePointRed, double whitePointGreen, double whitePointBlue, double whitePointWhite) {
      for (int brightness = 0; brightness < GammaTable.NUM_STEPS; ++brightness) {
        Curve curve = this.level[brightness];
        Curve.generate(curve.red, brightness, gamma, whitePointRed);
        Curve.generate(curve.green, brightness, gamma, whitePointGreen);
        Curve.generate(curve.blue, brightness, gamma, whitePointBlue);
        Curve.generate(curve.white, brightness, gamma, whitePointWhite);
      }
      return this;
    }

    @Deprecated
    private GammaTable(byte[][] lut) {
      for (int i = 0; i < this.level.length; ++i) {
        this.level[i] = new Curve(lut[i], lut[i], lut[i], lut[i]);
      }
    }
  }

  public enum GammaMode {
    /**
     * Inherit gamma table from parent
     */
    INHERIT,

    /**
     * Use gamma correction setting in this specific output
     */
    DIRECT
  }

  /**
   * Whether the output is enabled.
   */
  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", true)
    .setDescription("Whether the output is active");

  /**
   * Framerate throttle
   */
  public final BoundedParameter framesPerSecond =
    new BoundedParameter("FPS", 0, MAX_FRAMES_PER_SECOND)
    .setMappable(false)
    .setDescription("Maximum frames per second this output will send (0 for no limiting)");

  /**
   * Gamma correction level
   */
  public final BoundedParameter gamma = (BoundedParameter)
    new BoundedParameter("Gamma", 1, 1, 4)
    .setMappable(false)
    .setOscMode(BoundedParameter.OscMode.ABSOLUTE)
    .setDescription("Gamma correction on the output, 1 is linear (no gamma)");

  /**
   * Gamma table mode, whether to inherit gamma
   */
  public final EnumParameter<GammaMode> gammaMode =
    new EnumParameter<GammaMode>("Gamma Mode", GammaMode.INHERIT);

  /**
   * Brightness of the output
   */
  public final CompoundParameter brightness =
    new CompoundParameter("Brightness", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Level of the output");

  /**
   * White point scaling for red pixels
   */
  public final DiscreteParameter whitePointRed =
    new DiscreteParameter("White Point Red", GammaTable.WHITE_POINT_MAX, 0, GammaTable.WHITE_POINT_MAX+1)
    .setMappable(false)
    .setDescription("White point value for red pixel output");

  /**
   * White point scaling for green pixels
   */
  public final DiscreteParameter whitePointGreen =
    new DiscreteParameter("White Point Green", GammaTable.WHITE_POINT_MAX, 0, GammaTable.WHITE_POINT_MAX+1)
    .setMappable(false)
    .setDescription("White point value for green pixel output");

  /**
   * White point scaling for blue pixels
   */
  public final DiscreteParameter whitePointBlue  =
    new DiscreteParameter("White Point Blue", GammaTable.WHITE_POINT_MAX, 0, GammaTable.WHITE_POINT_MAX+1)
    .setMappable(false)
    .setDescription("White point value for blue pixel output");

  /**
   * White point scaling for white pixels (if present)
   */
  public final DiscreteParameter whitePointWhite =
    new DiscreteParameter("White Point White", GammaTable.WHITE_POINT_MAX, 0, GammaTable.WHITE_POINT_MAX+1)
    .setMappable(false)
    .setDescription("White point value for white pixel output");

  /**
   * Time last frame was sent at.
   */
  private long lastFrameMillis = 0;

  /**
   * A lookup table that maps brightness and index byte to output byte. For high-pixel projects
   * this avoids lots of redundant brightness multiplies at the output.
   */
  private GammaTable gammaLut = null;

  private boolean hasCustomGamma = false;

  private GammaTable customGammaLut = null;

  private LXOutput gammaDelegate = null;

  private void buildGammaTable() {
    if (this.gammaMode.getEnum() == GammaMode.DIRECT) {
      if (this.gammaLut == null) {
        this.gammaLut = new GammaTable();
      }
      this.gammaLut.generate(
        this.gamma.getValue(),
        this.whitePointRed.getValue(),
        this.whitePointGreen.getValue(),
        this.whitePointBlue.getValue(),
        this.whitePointWhite.getValue()
      );
    }
  }

  protected LXOutput(LX lx) {
    this(lx, "Output");
  }

  protected LXOutput(LX lx, String label) {
    super(lx, label);
    buildGammaTable();

    addParameter("enabled", this.enabled);
    addParameter("brightness", this.brightness);
    addParameter("fps", this.framesPerSecond);
    addParameter("gamma", this.gamma);
    addParameter("gammaMode", this.gammaMode);
    addParameter("whitePointRed", this.whitePointRed);
    addParameter("whitePointGreen", this.whitePointGreen);
    addParameter("whitePointBlue", this.whitePointBlue);
    addParameter("whitePointWhite", this.whitePointWhite);
  }

  /**
   * Assigns a custom gamma table to the output
   *
   * @param gammaLut Two-dimensional array lookup of gamma curve for each precomputed brightness [0-255][0-255]
   */
  @Deprecated
  public void setGammaTable(byte[][] gammaLut) {
    setGammaTable(new GammaTable(gammaLut));
  }

  /**
   * Assigns a custom gamma table to the output
   *
   * @param gammaLut Gamma lookup tables
   */
  public void setGammaTable(GammaTable gammaLut) {
    this.customGammaLut = gammaLut;
    this.hasCustomGamma = true;
  }

  public void setGammaDelegate(LXOutput gammaDelegate) {
    this.gammaDelegate = gammaDelegate;
  }

  private GammaTable getGammaLut() {
    if (this.hasCustomGamma) {
      return this.customGammaLut;
    }
    switch (this.gammaMode.getEnum()) {
    case DIRECT:
      return this.gammaLut;
    default:
    case INHERIT:
      LXOutput gammaOutput = (this.gammaDelegate != null) ? this.gammaDelegate : (LXOutput) getParent();
      return gammaOutput.getGammaLut();
    }
  }

  public void setGroup(LXOutputGroup output) {
    super.setParent(output);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.gamma ||
        p == this.gammaMode ||
        p == this.whitePointRed ||
        p == this.whitePointGreen ||
        p == this.whitePointBlue ||
        p == this.whitePointWhite) {
      buildGammaTable();
    }
  }

  /**
   * Sends data to this output, applying throttle and color correction
   *
   * @param colors Array of color values
   * @return this
   */
  public LXOutput send(int[] colors) {
    return send(colors, 1.);
  }

  /**
   * Sends data to this output at the pre-corrected brightness
   *
   * @param colors Color buffer
   * @param brightness Brightness level from parent
   * @return this
   */
  public final LXOutput send(int[] colors, double brightness) {
    if (!this.enabled.isOn()) {
      return this;
    }
    long now = System.currentTimeMillis();
    double fps = this.framesPerSecond.getValue();
    if ((fps == 0) || ((now - this.lastFrameMillis) > (1000. / fps))) {
      // Compute effective brightness, input brightness multiplied by our own
      brightness *= this.brightness.getValue();

      // Send at the adjusted brightness level
      onSend(colors, getGammaLut(), brightness);

      this.lastFrameMillis = now;
    }
    return this;
  }

  /**
   * Subclasses implement this to send the data.
   *
   * @param colors Color values
   * @param glut Look-up table for 0-255 brightness curves
   * @param brightness Master brightness value
   */
  protected abstract void onSend(int[] colors, GammaTable glut, double brightness);

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(LXComponent.KEY_RESET)) {
      this.brightness.reset();
      this.framesPerSecond.reset();
      this.gamma.reset();
      this.whitePointRed.reset();
      this.whitePointGreen.reset();
      this.whitePointBlue.reset();
      this.whitePointWhite.reset();
    }
  }

  private static final String OUTPUT_LOG_PREFIX = "[I/O] ";

  public static final void log(String message) {
    LX.log(OUTPUT_LOG_PREFIX + message);
  }

  public static final void error(String message) {
    LX.error(OUTPUT_LOG_PREFIX + message);
  }

  public static final void error(Exception x, String message) {
    LX.error(x, OUTPUT_LOG_PREFIX + message);
  }
}