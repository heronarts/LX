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

package heronarts.lx.parameter;

/**
 * A simple parameter that has a binary value of off or on
 */
public class BooleanParameter extends LXListenableNormalizedParameter {

  public enum Mode {
    TOGGLE,
    MOMENTARY;
  }

  private Mode mode = Mode.TOGGLE;

  public BooleanParameter(String label) {
    this(label, false);
  }

  public BooleanParameter(String label, boolean on) {
    super(label, on ? 1. : 0.);
  }

  @Override
  public BooleanParameter setDescription(String description) {
    return (BooleanParameter) super.setDescription(description);
  }

  public BooleanParameter setMode(Mode mode) {
    this.mode = mode;
    return this;
  }

  public Mode getMode() {
    return this.mode;
  }

  public boolean isOn() {
    return getValueb();
  }

  public boolean getValueb() {
    return this.getValue() > 0.;
  }

  public BooleanParameter setValue(boolean value) {
    setValue(value ? 1. : 0.);
    return this;
  }

  public BooleanParameter toggle() {
    setValue(!isOn());
    return this;
  }

  @Override
  protected double updateValue(double value) {
    return (value > 0) ? 1. : 0.;
  }

  public double getNormalized() {
    return (getValue() > 0) ? 1. : 0.;
  }

  public float getNormalizedf() {
    return (float) getNormalized();
  }

  public BooleanParameter setNormalized(double normalized) {
    setValue(normalized >= 0.5);
    return this;
  }

}
