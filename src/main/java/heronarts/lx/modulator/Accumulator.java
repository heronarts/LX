/**
 * Copyright 2017- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.modulator;

import heronarts.lx.parameter.FixedParameter;
import heronarts.lx.parameter.LXParameter;

/**
 * An accumulator oscillates between increasing and decreasing value by some velocity such that it never
 * overflows or stops moving outside of the floating point range. If it hits the extremes of the floating
 * point range then it reverses direction and keeps going. This is useful in extremely long-running programs
 * that wish to seed a value for something like a noise function, without discontinuity.
 */
public class Accumulator extends LXModulator {

  private final LXParameter velocity;

  private float sign = 1;
  private int collisions = 0;

  public Accumulator() {
    this("ACCUM");
  }

  public Accumulator(double velocity) {
    this("ACCUM", velocity);
  }

  public Accumulator(LXParameter velocity) {
    this("ACCUM", velocity);
  }

  public Accumulator(String label) {
    this(label, 1);
  }

  public Accumulator(String label, double velocity) {
    this(label, new FixedParameter(velocity));
  }

  public Accumulator(String label, LXParameter velocity) {
    super(label);
    this.velocity = velocity;
  }

  @Override
  protected double computeValue(double deltaMs) {
    final float currentValue = getValuef();
    float amount = (float) (deltaMs * this.velocity.getValue());
    if (amount == 0) {
      return currentValue;
    }
    float newValue = currentValue + this.sign * amount;
    if ((currentValue == newValue) || Float.isInfinite(newValue)) {
      if (++this.collisions > 3) {
        this.collisions = 0;
        this.sign = -this.sign;
        int i = 0;
        do {
          newValue = currentValue + this.sign * amount;
          amount *= 10;
        } while ((newValue == currentValue) && (i++ < 10));
      }
    }
    return newValue;
  }
}
