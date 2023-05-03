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
import heronarts.lx.color.GradientUtils.BlendMode;
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

  private static final int DEFAULT_COLOR = LXColor.RED;

  public enum Mode {
    FIXED("Fixed"),
    OSCILLATE("Osc"),
    CYCLE("Cycle");

    public final String label;

    private Mode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final EnumParameter<Mode> mode =
    new EnumParameter<Mode>("Mode", Mode.FIXED)
    .setDescription("The mode of this color");

  public final EnumParameter<BlendMode> blendMode =
    new EnumParameter<BlendMode>("Blend", BlendMode.RGB)
    .setDescription("The blend mode when in oscillation");

  public final BoundedParameter period =
    new BoundedParameter("Period", 30, 1, 60*60)
    .setUnits(BoundedParameter.Units.SECONDS)
    .setDescription("The period of oscillation or rotation in this color");

  public final ColorParameter primary =
    new ColorParameter("Color", DEFAULT_COLOR)
    .setDescription("The base color setting");

  public final ColorParameter secondary =
    new ColorParameter("Secondary", LXColor.GREEN)
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
    this(swatch, DEFAULT_COLOR);
  }

  protected LXDynamicColor(LXSwatch swatch, int initialColor) {
    super(swatch.getLX());
    setParent(swatch);
    addParameter("mode", this.mode);
    addParameter("period", this.period);
    addParameter("primary", this.color.setColor(initialColor));
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
      final BlendMode blendMode = this.blendMode.getEnum();
      switch (blendMode) {
      case HSV:
      case HSVM:
      case HSVCW:
      case HSVCCW:
        float hue1 = this.primary.hue.getValuef();
        float hue2 = this.secondary.hue.getValuef();
        if (this.primary.isBlack()) {
          return hue2;
        } else if (this.secondary.isBlack()) {
          return hue1;
        }
        return blendMode.hueInterpolation.lerp(hue1, hue2, (float) getBasis());

      default:
      case RGB:
        int c = getColor();
        float b = LXColor.b(c);
        if (b > 0) {
          return LXColor.h(c);
        } else {
          // 0 brightness! This means one of primary or secondary is black...
          if (getBasis() > 0.5) {
            return this.secondary.hue.getValuef();
          } else {
            return this.primary.hue.getValuef();
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

  /**
   * Gets the saturation of the current dynamic color. This will return a valid
   * value even if brightness is all the way down and the color is black.
   */
  public float getSaturation() {
    switch (this.mode.getEnum()) {
    case OSCILLATE:
      switch (this.blendMode.getEnum()) {
      case HSV:
      case HSVM:
      case HSVCW:
      case HSVCCW:
        double sat1 = this.primary.saturation.getValue();
        double sat2 = this.secondary.saturation.getValue();
        if (this.primary.isBlack()) {
          return (float) sat2;
        } else if (this.secondary.isBlack()) {
          return (float) sat1;
        }
        return (float) LXUtils.lerp(sat1, sat2, getBasis());

      default:
      case RGB:
        int c = getColor();
        float b = LXColor.b(c);
        if (b > 0) {
          return LXColor.s(c);
        } else {
          // 0 brightness! This means one of primary or secondary is black...
          if (getBasis() > 0.5) {
            return this.secondary.saturation.getValuef();
          } else {
            return this.primary.saturation.getValuef();
          }
        }
      }


    case CYCLE:
      return this.color.saturation.getValuef();

    default:
    case FIXED:
      return this.color.saturation.getValuef();
    }
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
      return this.blendMode.getEnum().function.blend(this.primary, this.secondary, (float) getBasis());

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
