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

import heronarts.lx.parameter.AggregateParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.LXParameter;

public class ColorParameter extends AggregateParameter {

  public final CompoundParameter hue;
  public final CompoundParameter saturation;
  public final CompoundParameter brightness;

  public static final String PATH_BRIGHTNESS = "brightness";
  public static final String PATH_SATURATION = "saturation";
  public static final String PATH_HUE = "hue";

  protected int color;

  public ColorParameter(String label) {
    this(label, 0xff000000);
  }

  public ColorParameter(String label, int color) {
    super(label, Double.longBitsToDouble(color));

    final double h = LXColor.h(color);

    this.hue =
      new CompoundParameter("Hue", Double.isNaN(h) ? 0 : h, 0, 359)
      .setWrappable(true)
      .setDescription("Hue component of the color");

    this.saturation =
      new CompoundParameter("Saturation", LXColor.s(color), 0, 100)
      .setUnits(CompoundParameter.Units.PERCENT)
      .setDescription("Saturation component of the color");

    this.brightness =
      new CompoundParameter("Brightness", LXColor.b(color), 0, 100)
      .setUnits(CompoundParameter.Units.PERCENT)
      .setDescription("Brightness component of the color");

    // NOTE: register brightness and saturation first for load/save, otherwise if they
    // are 0 the hue information could possibly be overwritten
    addSubparameter(PATH_BRIGHTNESS, this.brightness);
    addSubparameter(PATH_SATURATION, this.saturation);
    addSubparameter(PATH_HUE, this.hue);

    this.color = color;
  }

  @Override
  public LXListenableNormalizedParameter getRemoteControl() {
    return this.hue;
  }

  @Override
  public ColorParameter setDescription(String description) {
    return (ColorParameter) super.setDescription(description);
  }

  /**
   * Returns the fixed color defined by this parameter. Note that this does not
   * take into account any modulation applied to the hue/saturation/brightness
   * values.
   *
   * @return fixed color specified by the parameter
   */
  public int getColor() {
    return this.color;
  }

  public int getBaseColor() {
    return getColor();
  }

  /**
   * Calculates the potentially modulated value of the color parameter
   * based upon the parameter values at this precise instance.
   *
   * @return Generated color value
   */
  public int calcColor() {
    // There may be modulators applied to the h/s/b values!
    return LXColor.hsb(
      this.hue.getValue(),
      this.saturation.getValue(),
      this.brightness.getValue()
    );
  }

  public ColorParameter setColor(int color) {
    setValue(Double.longBitsToDouble(color));
    return this;
  }

  public boolean isBlack() {
    return this.brightness.getValue() == 0;
  }

  public String getHexString() {
    return String.format("0x%08x", this.color);
  }

  @Override
  protected double onUpdateValue(double value) {
    this.color = (int) Double.doubleToRawLongBits(value);
    return value;
  }

  @Override
  protected void updateSubparameters(double value) {
    double b = LXColor.b(this.color);
    this.brightness.setValue(b);
    if (b > 0) {
      double s = LXColor.s(this.color);
      this.saturation.setValue(s);
      if (s > 0) {
        this.hue.setValue(LXColor.h(this.color));
      }
    }
  }

  @Override
  protected void onSubparameterUpdate(LXParameter p) {
    setColor(LXColor.hsb(
      this.hue.getBaseValue(),
      this.saturation.getBaseValue(),
      this.brightness.getBaseValue())
    );
  }

}
