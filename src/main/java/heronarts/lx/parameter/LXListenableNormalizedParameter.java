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
 * A parameter that can be listened to and has normalized values. This is needed
 * for things like UI components such as a slider or a knob, which need to be
 * able to listen for changes to the parameter value and to update it in a
 * normalized range of values.
 */
public abstract class LXListenableNormalizedParameter extends
    LXListenableParameter implements LXNormalizedParameter {

  private double exponent = 1;
  private boolean mappable = true;

  protected LXListenableNormalizedParameter(String label, double value) {
    super(label, value);
  }

  public LXListenableNormalizedParameter setExponent(double exponent) {
    if (exponent <= 0) {
      throw new IllegalArgumentException("May not set zero or negative exponent");
    }
    this.exponent = exponent;
    return this;
  }

  public double getExponent() {
    return this.exponent;
  }

  @Override
  public LXListenableNormalizedParameter setMappable(boolean mappable) {
    this.mappable = mappable;
    return this;
  }

  @Override
  public boolean isMappable() {
    return this.mappable;
  }

}
