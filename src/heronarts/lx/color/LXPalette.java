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

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXModulatorComponent;
import heronarts.lx.modulator.DampedParameter;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.modulator.SawLFO;
import heronarts.lx.modulator.SinLFO;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.parameter.LXParameter;

/**
 * A palette is an object that is used to keep track of top-level color values and
 * set modes of color computation. Though its use is not required, it is very useful for
 * creating coherent color schemes across patterns.
 */
public class LXPalette extends LXModulatorComponent implements LXOscComponent {

  public enum Mode {
    FIXED,
    OSCILLATE,
    CYCLE
  };

  public final EnumParameter<Mode> hueMode =
    new EnumParameter<Mode>("Mode", Mode.FIXED)
    .setDescription("Sets the operation mode of the palette");

  public final ColorParameter color =
    new ColorParameter("Color", 0xffff0000)
    .setDescription("The base color selection for the palette");

  /**
   * Hack... the Processing IDE doesn't let you address object.color, duplicate it to clr
   */
  public final ColorParameter clr = color;

  public final CompoundParameter range = new CompoundParameter("Range", 0, 360)
    .setDescription("Sets range in degrees (0-360) of how much spread the palette applies");

  public final CompoundParameter period = (CompoundParameter)
    new CompoundParameter("Period", 120000, 1000, 3600000)
    .setDescription("Sets how long the palette takes to complete one full oscillation")
    .setUnits(LXParameter.Units.MILLISECONDS);

  public final CompoundParameter gradient = (CompoundParameter)
    new CompoundParameter("Gradient", 0, -360, 360)
    .setDescription("Sets the amount of hue gradient")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  private final DampedParameter hueFixed = new DampedParameter(this.color.hue, 1800).setModulus(360.);

  private final SawLFO hueCycle = new SawLFO(0, 360, period);

  private final FunctionalParameter hue2 = new FunctionalParameter() {
    @Override
    public double getValue() {
      return color.hue.getValue() + range.getValue();
    }
  };

  private final SinLFO hueOscillate = new SinLFO(color.hue, hue2, period);

  private LXModulator hue = hueFixed;

  public LXPalette(LX lx) {
    super(lx);

    this.hueMode.setOptions(new String[] { "Fixed", "Oscillate", "Cycle" });
    addParameter("hueMode", this.hueMode);
    addParameter("color", this.color);
    addParameter("period", this.period);
    addParameter("range", this.range);
    addParameter("gradient", this.gradient);
    addModulator(this.hueFixed).start();
    addModulator(this.hueCycle);
    addModulator(this.hueOscillate);
  }

  public String getOscAddress() {
    return "/lx/palette";
  }

  @Override
  public String getLabel() {
    return "Palette";
  }

  @Override
  public void onParameterChanged(LXParameter parameter) {
    if (parameter == this.hueMode) {
      double hueValue = this.hue.getValue();
      this.color.hue.setValue(hueValue);
      switch (this.hueMode.getEnum()) {
        case FIXED:
          this.hue = this.hueFixed;
          this.hueFixed.setValue(hueValue).start();
          this.hueCycle.stop();
          this.hueOscillate.stop();
          break;
        case CYCLE:
          this.hue = this.hueCycle;
          this.hueFixed.stop();
          this.hueOscillate.stop();
          this.hueCycle.setValue(hueValue).start();
          break;
        case OSCILLATE:
          this.hue = this.hueOscillate;
          this.hueFixed.stop();
          this.hueCycle.stop();
          this.hueOscillate.setValue(hueValue).start();
          break;
      }
    }
  }

  public double getHue() {
    return this.hue.getValue();
  }

  public final float getHuef() {
    return (float) getHue();
  }

  public double getSaturation() {
    return this.color.saturation.getValue();
  }

  public final float getSaturationf() {
    return (float) getSaturation();
  }

  public int getColor() {
    return this.color.getColor();
  }

  public int getColor(double brightness) {
    if (brightness > 0) {
      return LXColor.hsb(getHue(), getSaturation(), brightness);
    }
    return LXColor.BLACK;
  }

  public int getColor(double saturation, double brightness) {
    if (brightness > 0) {
      return LXColor.hsb(getHue(), saturation, brightness);
    }
    return LXColor.BLACK;
  }

  @Override
  public void load(LX lx, JsonObject object) {
    super.load(lx, object);
    this.hueCycle.setValue(this.color.hue.getValue());
  }

}
