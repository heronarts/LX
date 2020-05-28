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

package heronarts.lx.color;

import heronarts.lx.LXModulatorComponent;
import heronarts.lx.modulator.SawLFO;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.utils.LXUtils;

/**
 * A dynamic color is a color that has a few different settings that allows it to either
 * be fixed or to change its value over time.
 */
public class LXDynamicColor extends LXModulatorComponent implements LXOscComponent {

  public enum Mode {
    FIXED("Fixed Color"),
    OSCILLATE("Oscillate"),
    CYCLE("Hue Cycle");

    public final String label;

    private Mode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public enum BlendMode {
    HSV,
    RGB;
  }

  public final EnumParameter<Mode> mode =
    new EnumParameter<Mode>("Mode", Mode.FIXED)
    .setDescription("The mode of this color");

  public final EnumParameter<BlendMode> blendMode =
    new EnumParameter<BlendMode>("Blend", BlendMode.HSV)
    .setDescription("The blend mode when in oscillation");

  public final BoundedParameter period = (BoundedParameter)
    new BoundedParameter("Period", 30, 1, 60*60)
    .setUnits(BoundedParameter.Units.SECONDS)
    .setDescription("The period of oscillation or rotation in this color");

  public final ColorParameter primary =
    new ColorParameter("Color", 0xffff0000)
    .setDescription("The base color setting");

  public final ColorParameter secondary =
    new ColorParameter("Secondary", 0xff00ff00)
    .setDescription("The secondary color setting");

  public final ColorParameter color = this.primary;

  private final SawLFO basis = startModulator(new SawLFO(0, 1, new FunctionalParameter() {
    @Override
    public double getValue() {
      return period.getValue() * 1000;
    }
  }));

  private int index = 0;

  public LXDynamicColor(LXSwatch swatch) {
    super(swatch.getLX());
    setParent(swatch);
    addParameter("mode", this.mode);
    addParameter("period", this.period);
    addParameter("primary", this.color);
    addParameter("secondary", this.secondary);
  }

  public LXSwatch getSwatch() {
    return (LXSwatch) getParent();
  }

  @Override
  public String getPath() {
    return "color/" + (this.index + 1);
  }

  void setIndex(int index) {
    this.index = index;
  }

  public int getIndex() {
    return this.index;
  }

  public double getBasis() {
    switch (this.mode.getEnum()) {
    case OSCILLATE:
      return 1 - 2 * Math.abs(this.basis.getValue() - .5);
    case CYCLE:
      return this.basis.getValue();
    default:
      return 0;
    }
  }

  void trigger() {
    this.basis.trigger();
  }

  /**
   * Gets the hue of the current dynamic color. This will return a valid
   * value even if brightness is all the way down and the color is black.
   *
   * @return Hue
   */
  public double getHue() {
    switch (this.mode.getEnum()) {
    case OSCILLATE:
      switch (this.blendMode.getEnum()) {
      case HSV:
        double hue1 = this.primary.hue.getValue();
        double hue2 = this.secondary.hue.getValue();
        if (hue2 < hue1) {
          hue2 += 360.;
        }
        return LXUtils.lerp(hue1, hue2, getBasis());

      default:
      case RGB:
        int c = getColor();
        float b = LXColor.b(c);
        if (b > 0) {
          return LXColor.h(c);
        } else {
          // 0 brightness! This means one of primary or secondary is black...
          if (getBasis() > 0.5) {
            return this.secondary.hue.getValue();
          } else {
            return this.primary.hue.getValue();
          }
        }
      }

    case CYCLE:
      return this.color.hue.getValue() + this.basis.getValue() * 360;

    default:
    case FIXED:
      return this.color.hue.getValue();
    }
  }

  /**
   * Gets the hue of the current dynamic color. This will return a valid
   * value even if brightness is all the way down and the color is black.
   *
   * @return Hue
   */
  public float getHuef() {
    return (float) getHue();
  }

  public float getSaturation() {
    return LXColor.s(getColor());
  }

  public float getBrightness() {
    return LXColor.b(getColor());
  }

  /**
   * Gets the current value of this dynamic color
   *
   * @return Color, which may be blending or oscillating different options
   */
  public int getColor() {
    switch (this.mode.getEnum()) {
    case OSCILLATE:
      double lerp = getBasis();
      switch (this.blendMode.getEnum()) {
      case HSV:
        double hue1 = this.primary.hue.getValue();
        double hue2 = this.secondary.hue.getValue();
        if (hue2 < hue1) {
          hue2 += 360.;
        }
        return LXColor.hsb(
          LXUtils.lerp(hue1, hue2, lerp),
          LXUtils.lerp(this.primary.saturation.getValue(), this.secondary.saturation.getValue(), lerp),
          LXUtils.lerp(this.primary.brightness.getValue(), this.secondary.brightness.getValue(), lerp)
        );
      default:
      case RGB:
        return LXColor.lerp(this.primary.getColor(), this.secondary.getColor(), lerp);
      }

    case CYCLE:
      return LXColor.hsb(
        this.color.hue.getValue() + this.basis.getValue() * 360,
        this.color.saturation.getValue(),
        this.color.brightness.getValue()
      );

    default:
    case FIXED:
      return this.color.getColor();
    }
  }

}
