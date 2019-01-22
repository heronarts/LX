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
import heronarts.lx.ModelBuffer;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;

import java.awt.Color;
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
   * Buffer with colors for this output, gamma-corrected
   */
  private final ModelBuffer outputColors = new ModelBuffer(lx);

  private final ModelBuffer allWhite = new ModelBuffer(lx, LXColor.WHITE);

  private final ModelBuffer allOff = new ModelBuffer(lx, LXColor.BLACK);

  /**
   * Local array for color-conversions
   */
  private final float[] hsb = new float[3];

  /**
   * Whether the output is enabled.
   */
  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", true)
    .setDescription("Whether the output is active");

  public enum Mode {
    NORMAL,
    WHITE,
    RAW,
    OFF
  };

  /**
   * Sending mode, 0 = normal, 1 = all white, 2 = all off
   */
  public final EnumParameter<Mode> mode =
    new EnumParameter<Mode>("Mode", Mode.NORMAL)
    .setDescription("Operation mode of this output");

  /**
   * Framerate throttle
   */
  public final BoundedParameter framesPerSecond =
    new BoundedParameter("FPS", 0, 300)
    .setDescription("Maximum frames per second this output will send");

  /**
   * Gamma correction level
   */
  public final DiscreteParameter gammaCorrection =
    new DiscreteParameter("Gamma", 4)
    .setDescription("Gamma correction on the output, 0 is none");

  /**
   * Brightness of the output
   */
  public final BoundedParameter brightness =
    new BoundedParameter("Brightness", 1)
    .setDescription("Level of the output");

  /**
   * Time last frame was sent at.
   */
  private long lastFrameMillis = 0;

  protected LXOutput(LX lx) {
    this(lx, "Output");
  }

  protected LXOutput(LX lx, String label) {
    super(lx, label);
    addParameter("enabled", this.enabled);
    addParameter("mode", this.mode);
    addParameter("fps", this.framesPerSecond);
    addParameter("gamma", this.gammaCorrection);
    addParameter("brightness", this.brightness);
  }

  /**
   * Adds a child to this output, sent after color-correction
   *
   * @param child Child output
   * @return this
   */
  public LXOutput addChild(LXOutput child) {
    // TODO(mcslee): need to setParent() on the LXComponent...
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
   * Sends data to this output, after applying throttle and color correction
   *
   * @param colors Array of color values
   * @return this
   */
  public final LXOutput send(int[] colors) {
    if (!this.enabled.isOn()) {
      return this;
    }
    long now = System.currentTimeMillis();
    double fps = this.framesPerSecond.getValue();
    if ((fps == 0) || ((now - this.lastFrameMillis) > (1000. / fps))) {
      int[] colorsToSend;

      switch (this.mode.getEnum()) {
      case WHITE:
        int white = LXColor.hsb(0, 0, 100 * this.brightness.getValuef());
        int[] allWhite = this.allWhite.getArray();
        for (int i = 0; i < allWhite.length; ++i) {
          allWhite[i] = white;
        }
        colorsToSend = allWhite;
        break;

      case OFF:
        colorsToSend = this.allOff.getArray();
        break;

      case RAW:
        colorsToSend = colors;
        break;

      default:
      case NORMAL:
        colorsToSend = colors;
        int gamma = this.gammaCorrection.getValuei();
        double brt = this.brightness.getValuef();
        if (gamma > 0 || brt < 1) {
          colorsToSend = this.outputColors.getArray();
          int r, g, b, rgb;
          for (int i = 0; i < colors.length; ++i) {
            rgb = colors[i];
            r = (rgb >> 16) & 0xff;
            g = (rgb >> 8) & 0xff;
            b = rgb & 0xff;
            Color.RGBtoHSB(r, g, b, this.hsb);
            float scaleBrightness = this.hsb[2];
            for (int x = 0; x < gamma; ++x) {
              scaleBrightness *= this.hsb[2];
            }
            scaleBrightness *= brt;
            colorsToSend[i] = Color.HSBtoRGB(hsb[0], hsb[1], scaleBrightness);
          }
        }
        break;
      }

      this.onSend(colorsToSend);

      for (LXOutput child : this.mutableChildren) {
        child.send(colorsToSend);
      }
      this.lastFrameMillis = now;
    }
    return this;
  }

  /**
   * Subclasses implement this to send the data.
   *
   * @param colors Color values
   */
  protected abstract void onSend(int[] colors);
}