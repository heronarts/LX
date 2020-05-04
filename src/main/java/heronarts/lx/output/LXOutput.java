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

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.BooleanParameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class represents the output stage from the LX engine to real devices.
 * Outputs may have their own brightness, be enabled/disabled, be throttled,
 * etc.
 */
public abstract class LXOutput extends LXComponent {

  private final List<LXOutput> mutableChildren = new ArrayList<LXOutput>();

  public final List<LXOutput> children = Collections.unmodifiableList(this.mutableChildren);

  /**
   * Whether the output is enabled.
   */
  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", true)
    .setDescription("Whether the output is active");

  /**
   * Framerate throttle
   */
  public final BoundedParameter framesPerSecond = (BoundedParameter)
    new BoundedParameter("FPS", 0, 300)
    .setMappable(false)
    .setDescription("Maximum frames per second this output will send (0 for no limiting)");

  /**
   * Gamma correction level
   */
  public final BoundedParameter gamma = (BoundedParameter)
    new BoundedParameter("Gamma", 1, 1, 4)
    .setMappable(false)
    .setDescription("Gamma correction on the output, 1 is linear (no gamma)");

  /**
   * Brightness of the output
   */
  public final CompoundParameter brightness =
    new CompoundParameter("Brightness", 1)
    .setDescription("Level of the output");

  /**
   * Time last frame was sent at.
   */
  private long lastFrameMillis = 0;

  /**
   * A lookup table that maps brightness and index byte to output byte. For high-pixel projects
   * this avoids lots of redundant brightness multiplies at the output.
   */
  protected final byte[][] gammaLut = new byte[256][256];

  private void buildGammaTable() {
    double gamma = this.gamma.getValue();
    if (gamma == 1) {
      for (int b = 0; b < 256; ++b) {
        int bb = b + (b > 127 ? 1 : 0);
        for (int in = 0; in < 256; ++in) {
          this.gammaLut[b][in] = (byte) ((in * bb) >> 8);
        }
      }
    } else {
      double maxInv = 1. / 65025.;
      for (int b = 0; b < 256; ++b) {
        for (int in = 0; in < 256; ++in) {
          this.gammaLut[b][in] = (byte) (0xff & (int) Math.round(Math.pow(in * b * maxInv, gamma) * 255.f));
        }
      }
    }
  }

  protected LXOutput(LX lx) {
    this(lx, "Output");
  }

  protected LXOutput(LX lx, String label) {
    super(lx, label);
    buildGammaTable();

    addParameter("enabled", this.enabled);
    addParameter("fps", this.framesPerSecond);
    addParameter("gamma", this.gamma);
    addParameter("brightness", this.brightness);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.gamma) {
      buildGammaTable();
    }
  }

  /**
   * Adds a child to this output, sent after color-correction
   *
   * @param child Child output
   * @return this
   */
  public LXOutput addChild(LXOutput child) {
    child.setParent(this);
    this.mutableChildren.add(child);
    return this;
  }

  /**
   * Removes a child
   *
   * @param child Child output
   * @return this
   */
  public LXOutput removeChild(LXOutput child) {
    this.mutableChildren.remove(child);
    return this;
  }

  /**
   * Sends data to this output, applying throttle and color correction
   *
   * @param colors Array of color values
   * @return this
   */
  public final LXOutput send(int[] colors) {
    return send(colors, 1.);
  }

  /**
   * Sends data to this output at the pre-corrected brightness
   *
   * @param colors
   * @param brightness
   * @return
   */
  private final LXOutput send(int[] colors, double brightness) {
    if (!this.enabled.isOn()) {
      return this;
    }
    long now = System.currentTimeMillis();
    double fps = this.framesPerSecond.getValue();
    if ((fps == 0) || ((now - this.lastFrameMillis) > (1000. / fps))) {
      // Compute effective brightness, input brightness multiplied by our own
      brightness *= this.brightness.getValue();

      // Send at the adjusted brightness level
      onSend(colors, brightness);

      // Send to all children, with cascading brightness
      for (LXOutput child : this.mutableChildren) {
        child.send(colors, brightness);
      }
      this.lastFrameMillis = now;
    }
    return this;
  }

  protected void onSend(int[] colors, double brightness) {
    // Select the right gamma look-up table for our settings
    byte[] glut = this.gammaLut[(int) Math.round(brightness * 255.f)];
    onSend(colors, glut);
  }

  /**
   * Subclasses implement this to send the data.
   *
   * @param colors Color values
   * @param glut Look-up table scaled to appropriate brightness and gamma
   */
  protected abstract void onSend(int[] colors, byte[] glut);

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