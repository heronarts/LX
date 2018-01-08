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
 * A MutableParameter is a parameter that has a value which can be changed to anything.
 */
public class MutableParameter extends LXListenableParameter {

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

  @Override
  protected double updateValue(double value) {
    return value;
  }

  public int getValuei() {
    return (int) getValue();
  }

}
