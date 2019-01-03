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

package heronarts.lx.modulator;

import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.FixedParameter;
import heronarts.lx.parameter.LXParameter;

/**
 * A modulator that tracks the value of a parameter but damps motion over time according
 * to rules.
 */
public class DampedParameter extends LXModulator {

  private final LXParameter parameter;

  private final LXParameter velocity;

  private final LXParameter acceleration;

  private final LXParameter deceleration;

  private double currentVelocity = 0;

  private boolean hasModulus = false;

  private double modulus = 0;

  public DampedParameter(String label, double velocity) {
    this(new BoundedParameter(label, 0, Double.MIN_VALUE, Double.MAX_VALUE), velocity, 0);
  }

  public DampedParameter(LXParameter parameter, double velocity) {
    this(parameter, velocity, 0);
  }

  public DampedParameter(LXParameter parameter, double velocity, double acceleration) {
    this("DAMPED-" + parameter.getLabel(), parameter, velocity, acceleration, acceleration);
  }

  public DampedParameter(LXParameter parameter, double velocity, double acceleration, double deceleration) {
    this("DAMPED-" + parameter.getLabel(), parameter, velocity, acceleration, deceleration);
  }

  public DampedParameter(LXParameter parameter, LXParameter velocity) {
    this("DAMPED-" + parameter.getLabel(), parameter, velocity, 0);
  }

  public DampedParameter(LXParameter parameter, LXParameter velocity, LXParameter acceleration) {
    this("DAMPED-" + parameter.getLabel(), parameter, velocity, acceleration);
  }

  public DampedParameter(LXParameter parameter, LXParameter velocity, LXParameter acceleration, LXParameter deceleration) {
    this("DAMPED-" + parameter.getLabel(), parameter, velocity, acceleration, deceleration);
  }

  public DampedParameter(String label, LXParameter parameter, LXParameter velocity) {
    this(label, parameter, velocity, 0);
  }

  public DampedParameter(String label, LXParameter parameter, double velocity, double acceleration) {
    this(label, parameter, velocity, acceleration, acceleration);
  }

  public DampedParameter(String label, LXParameter parameter, double velocity, double acceleration, double deceleration) {
    this(label, parameter, new FixedParameter(velocity), new FixedParameter(acceleration), new FixedParameter(deceleration));
  }

  public DampedParameter(String label, LXParameter parameter, LXParameter velocity, double acceleration) {
    this(label, parameter, velocity, acceleration, acceleration);
  }

  public DampedParameter(String label, LXParameter parameter, LXParameter velocity, double acceleration, double deceleration) {
    this(label, parameter, velocity, new FixedParameter(acceleration), new FixedParameter(deceleration));
  }

  public DampedParameter(String label, LXParameter parameter, LXParameter velocity, LXParameter acceleration) {
    this(label, parameter, velocity, acceleration, acceleration);
  }

  public DampedParameter(String label, LXParameter parameter, LXParameter velocity, LXParameter acceleration, LXParameter deceleration) {
    super(label);
    this.parameter = parameter;
    this.velocity = velocity;
    this.acceleration = acceleration;
    this.deceleration = deceleration;
    updateValue(parameter.getValue());
  }

  /**
   * Sets a modulus at which values wrap around
   *
   * @param modulus Modulus value
   * @return this
   */
  public DampedParameter setModulus(double modulus) {
    this.modulus = modulus;
    this.hasModulus = (modulus > 0);
    return this;
  }

  /**
   * Sets whether a modulus value is used.
   *
   * @param hasModulus Whether to use modulus
   * @return this
   */
  public DampedParameter setModulus(boolean hasModulus) {
    this.hasModulus = hasModulus && (this.modulus > 0);
    return this;
  }

  @Override
  protected double computeValue(double deltaMs) {
    double value = getValue();

    double target = this.parameter.getValue();
    if (value == target) {
      this.currentVelocity = 0;
      return value;
    }

    if (this.hasModulus) {
      if (target < value) {
        double wrapTarget = target + this.modulus;
        if (Math.abs(value - wrapTarget) < Math.abs(value - target)) {
          target = wrapTarget;
        }
      } else {
        double wrapValue = value + this.modulus;
        if (Math.abs(wrapValue - target) < Math.abs(value - target)) {
          value = wrapValue;
        }
      }
    }

    double av = Math.abs(this.acceleration.getValue());
    double dv = Math.abs(this.deceleration.getValue());
    double vv = Math.abs(this.velocity.getValue());

    double deltaS = deltaMs / 1000.;
    if (av > 0 || dv > 0) {
      double position = value;
      if (target < value) {
        av = -av;
        dv = -dv;
      }
      double decelTime = Math.abs(this.currentVelocity / dv);
      double decelPosition = position + this.currentVelocity * decelTime - .5 * dv * decelTime * decelTime;
      if (target > value) {
        // Moving positively
        if ((this.currentVelocity > 0) && (decelPosition > target)) {
          // Decelerating
          position = Math.min(target, value + this.currentVelocity * deltaS + .5 * -dv * deltaS * deltaS);
          this.currentVelocity = Math.max(0, this.currentVelocity - dv * deltaS);
        } else {
          // Accelerating
          position = Math.min(target, value + this.currentVelocity * deltaS + .5 * av * deltaS * deltaS);
          this.currentVelocity = Math.min(vv, this.currentVelocity + av * deltaS);
        }
      } else {
        // Moving negatively
        if ((this.currentVelocity < 0) && (decelPosition < target)) {
          // Decelerating
          position = Math.max(target, value + this.currentVelocity * deltaS + .5 * -dv * deltaS * deltaS);
          this.currentVelocity = Math.min(0, this.currentVelocity - dv * deltaS);
        } else {
          // Accelerating
          position = Math.max(target, value + this.currentVelocity * deltaS + .5 * av * deltaS * deltaS);
          this.currentVelocity = Math.max(-vv, this.currentVelocity + av * deltaS);
        }
      }

      if (this.hasModulus) {
        position = position % this.modulus;
      }

      return position;
    }

    // No acceleration mode
    double range = vv * deltaS;
    double after;
    if (target > value) {
      after = Math.min(value + range, target);
    } else {
      after = Math.max(value - range, target);
    }

    if (this.hasModulus) {
      after = after % this.modulus;
    }
    return after;

  }

  public LXParameter getParameter() {
    return this.parameter;
  }

}
