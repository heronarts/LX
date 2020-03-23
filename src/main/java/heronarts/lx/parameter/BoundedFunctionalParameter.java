/**
 * Copyright 2018- Heron Arts LLC
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
 * @author Justin K. Belcher <jkbelcher@gmail.com>
 */

package heronarts.lx.parameter;

import heronarts.lx.parameter.BoundedParameter.Range;

/**
 * A FunctionalParameter that is bounded by a range.  This enables the calculation
 * of normalized values which enables mapping with the modulation engine.
 */
public abstract class BoundedFunctionalParameter extends FunctionalParameter implements LXNormalizedParameter {

  /**
   * Range of the parameter
   */
  public final Range range;

  /**
   * A bounded functional parameter with a range of 0-1
   *
   * @param label Label for parameter
   */
  public BoundedFunctionalParameter(String label) {
    this(label, 0, 1);
  }

  /**
   * A bounded functional parameter with a range of 0 to max
   *
   * @param label Label for parameter
   * @param max Maximum value
   */
  protected BoundedFunctionalParameter(String label, double max) {
    this(label, 0, max);
  }

  /**
   * A bounded functional parameter with a range from v0 to v1. Note that it is not necessary for
   * v0 to be less than v1, if it is desired for the knob's value to progress negatively.
   *
   * @param label Label for parameter
   * @param v0 Start of range
   * @param v1 End of range
   */
  protected BoundedFunctionalParameter(String label, double v0, double v1) {
    super(label);
    this.range = new Range(v0, v1);
  }

  @Override
  public BoundedFunctionalParameter setDescription(String description) {
    super.setDescription(description);
    return this;
  }

  /**
   * Not supported for this parameter type unless subclass overrides.
   *
   * @param value The value
   */
  @Override
  public LXParameter setValue(double value) {
    throw new UnsupportedOperationException(
        "BoundedFunctionalParameter does not support setValue()");
  }

  /**
   * Retrieves the raw value of the parameter, subclass must implement.
   * This value will be constrained to the range.
   *
   * @return Parameter value
   */
  protected abstract double computeValue();

  @Override
  public final double getValue() {
    return this.range.constrain(computeValue());
  }

  @Override
  public final float getValuef() {
    return (float) getValue();
  }

  /**
   * Sets the value of parameter using normal 0-1
   *
   * @param normalized Value from 0-1 through the parameter range
   * @return this, for method chaining
   */
  public BoundedFunctionalParameter setNormalized(double normalized) {
    throw new UnsupportedOperationException(
      "BoundedFunctionalParameter does not support setValue()");
  }

  /**
   * Gets a normalized value of the parameter from 0 to 1
   *
   * @return Normalized value, from 0 to 1
   */
  public double getNormalized() {
    return this.range.getNormalized(getValue(), this.exponent);
  }

  /**
   * Normalized value as a float
   *
   * @return Normalized value from 0-1 as a float
   */
  public float getNormalizedf() {
    return (float) getNormalized();
  }

  private double exponent = 1;

  public LXNormalizedParameter setExponent(double exponent) {
    if (exponent <= 0) {
      throw new IllegalArgumentException("May not set zero or negative exponent");
    }
    this.exponent = exponent;
    return this;
  }

  public double getExponent() {
    return this.exponent;
  }

}
