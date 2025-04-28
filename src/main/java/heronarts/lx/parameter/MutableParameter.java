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

import heronarts.lx.utils.LXUtils;

/**
 * A MutableParameter is a parameter that has a value which can be changed to anything.
 */
public class MutableParameter extends LXListenableParameter {

  private boolean hasMinimum = false, hasMaximum = false;

  private double minimum = 0, maximum = 0;

  public MutableParameter() {
    super();
  }

  public MutableParameter(String label) {
    super(label);
  }

  public MutableParameter(String label, double value) {
    super(label, value);
  }

  public MutableParameter(double value) {
    super(value);
  }

  public MutableParameter increment() {
    return (MutableParameter) setValue(getValue() + 1);
  }

  public MutableParameter decrement() {
    return (MutableParameter) setValue(getValue() - 1);
  }

  public MutableParameter setMinimum(double minimum) {
    if (this.hasMaximum && minimum > this.maximum) {
      throw new IllegalArgumentException("Cannot set MutableParameter minimum > maximum");
    }
    this.minimum = minimum;
    this.hasMinimum = true;
    setValue(getValue());
    return this;
  }

  public MutableParameter setMaximum(double maximum) {
    if (this.hasMinimum && maximum < this.minimum) {
      throw new IllegalArgumentException("Cannot set MutableParameter maximum < minimum");
    }
    this.maximum = maximum;
    this.hasMaximum = true;
    setValue(getValue());
    return this;
  }

  @Override
  public MutableParameter setDescription(String description) {
    super.setDescription(description);
    return this;
  }

  @Override
  public MutableParameter setFormatter(Formatter formatter) {
    super.setFormatter(formatter);
    return this;
  }

  @Override
  public MutableParameter setMappable(boolean mappable) {
    super.setMappable(mappable);
    return this;
  }

  @Override
  public MutableParameter setPolarity(Polarity polarity) {
    super.setPolarity(polarity);
    return this;
  }

  @Override
  public MutableParameter setUnits(Units units) {
    super.setUnits(units);
    return this;
  }

  @Override
  protected double updateValue(double value) {
    if (this.hasMinimum) {
      value = LXUtils.max(value, this.minimum);
    }
    if (this.hasMaximum) {
      value = LXUtils.min(value, this.maximum);
    }
    return value;
  }

  public int getValuei() {
    return (int) getValue();
  }

}
