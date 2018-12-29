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

import heronarts.lx.parameter.FixedParameter;
import heronarts.lx.parameter.LXParameter;

/**
 * An accelerator is a free-running modulator that changes its value based on
 * velocity and acceleration, measured in units/second and units/second^2,
 * respectively.
 */
public class Accelerator extends LXModulator {

  private double initValue;
  private double velocity;

  private LXParameter initVelocity;
  private LXParameter acceleration;

  public Accelerator(double initValue, double initVelocity, double acceleration) {
    this(initValue, initVelocity, new FixedParameter(acceleration));
  }

  public Accelerator(double initValue, double initVelocity, LXParameter acceleration) {
    this("ACCEL", initValue, initVelocity, acceleration);
  }

  public Accelerator(double initValue, LXParameter initVelocity, LXParameter acceleration) {
    this("ACCEL", initValue, initVelocity, acceleration);
  }

  public Accelerator(String label, double initValue, double initVelocity, double acceleration) {
    this(label, initValue, new FixedParameter(initVelocity), new FixedParameter(acceleration));
  }

  public Accelerator(String label, double initValue, double initVelocity, LXParameter acceleration) {
    this(label, initValue, new FixedParameter(initVelocity), acceleration);
  }

  public Accelerator(String label, double initValue, LXParameter initVelocity, LXParameter acceleration) {
    super(label);
    setValue(this.initValue = initValue);
    setSpeed(initVelocity, acceleration);
  }

  @Override
  protected void onReset() {
    setVelocity(this.initVelocity.getValue());
    setValue(this.initValue);
  }

  /**
   * @return the current velocity
   */
  public double getVelocity() {
    return this.velocity;
  }

  /**
   * @return the current velocity as a floating point
   */
  public float getVelocityf() {
    return (float) this.getVelocity();
  }

  /**
   * @return The current acceleration
   */
  public double getAcceleration() {
    return this.acceleration.getValue();
  }

  /**
   * @return The current acceleration, as a float
   */
  public float getAccelerationf() {
    return (float) this.getAcceleration();
  }

  public Accelerator setSpeed(double initVelocity, double acceleration) {
    return setSpeed(new FixedParameter(initVelocity), new FixedParameter(acceleration));
  }

  /**
   * Sets both the velocity and acceleration of the modulator. Updates the
   * default values so that a future call to trigger() will reset to this
   * velocity.
   *
   * @param initVelocity New velocity
   * @param acceleration Acceleration
   * @return this
   */
  public Accelerator setSpeed(LXParameter initVelocity, LXParameter acceleration) {
    this.initVelocity = initVelocity;
    this.velocity = this.initVelocity.getValue();
    this.acceleration = acceleration;
    return this;
  }

  /**
   * Updates the current velocity. Does not reset the default.
   *
   * @param velocity New velocity
   * @return this
   */
  public Accelerator setVelocity(double velocity) {
    this.velocity = velocity;
    return this;
  }

  /**
   * Sets the initial velocity to a fixed value
   *
   * @param initVelocity Fixed initial velocity value
   * @return this
   */
  public Accelerator setInitVelocity(double initVelocity) {
    return setInitVelocity(new FixedParameter(initVelocity));
  }

  /**
   * Sets initial velocity of the Accelerator
   *
   * @param initVelocity Initial velocity parameter
   * @return this
   */
  public Accelerator setInitVelocity(LXParameter initVelocity) {
    this.initVelocity = initVelocity;
    return this;
  }

  public Accelerator setAcceleration(double acceleration) {
    return setAcceleration(new FixedParameter(acceleration));
  }

  /**
   * Updates the acceleration.
   *
   * @param acceleration New acceleration
   * @return this
   */
  public Accelerator setAcceleration(LXParameter acceleration) {
    this.acceleration = acceleration;
    return this;
  }

  @Override
  protected double computeValue(double deltaMs) {
    double a = getAcceleration();
    double dt = deltaMs / 1000.;
    // s(t) = s(0) + v*t + (1/2)a*t^2
    double s = this.getValue() + this.velocity * dt + 0.5 * a * dt * dt;
    // v(t) = v(0) + a*t
    this.velocity += a * dt;
    return s;
  }
}