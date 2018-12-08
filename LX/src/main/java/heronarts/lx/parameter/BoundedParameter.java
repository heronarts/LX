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

import heronarts.lx.LXUtils;

/**
 * Simple parameter class with a double value.
 */
public class BoundedParameter extends LXListenableNormalizedParameter {

  static public class Range {

    public final double v0;
    public final double v1;
    public final double min;
    public final double max;
    public final double vRange;
    public final double range;

    public Range(double v0, double v1) {
      this.v0 = v0;
      this.v1 = v1;
      if (v0 < v1) {
        this.min = v0;
        this.max = v1;
      } else {
        this.min = v1;
        this.max = v0;
      }
      this.vRange = this.v1 - this.v0;
      this.range = this.max - this.min;
    }

    public double constrain(double value) {
      return LXUtils.constrain(value, this.min, this.max);
    }

    public double getNormalized(double value) {
      return getNormalized(value, 1.);
    }

    public double getNormalized(double value, double exponent) {
      if (this.v0 == this.v1) {
        return 0;
      }
      value = constrain(value);
      double normalized = (value - this.v0) / this.vRange;
      if (exponent != 1) {
        normalized = Math.pow(normalized, 1 / exponent);
      }
      return normalized;
    }

    public double normalizedToValue(double normalized) {
      return normalizedToValue(normalized, 1.);
    }

    public double normalizedToValue(double normalized, double exponent) {
      if (normalized < 0) {
        normalized = 0;
      } else if (normalized > 1) {
        normalized = 1;
      }
      if (exponent != 1) {
        normalized = Math.pow(normalized, exponent);
      }
      return this.v0 + (this.vRange * normalized);
    }
  }

  /**
   * Range of the parameter
   */
  public final Range range;

  /**
   * Underlying LXListenableParameter that this wraps
   */
  private final LXListenableParameter underlying;

  /**
   * Labeled parameter with value of 0 and range of 0-1
   *
   * @param label Label for parameter
   */
  public BoundedParameter(String label) {
    this(label, 0);
  }

  /**
   * A bounded parameter with label and value, initial value of 0 and a range of 0-1
   *
   * @param label Label
   * @param value value
   */
  public BoundedParameter(String label, double value) {
    this(label, value, 1);
  }

  /**
   * A bounded parameter with an initial value, and range from 0 to max
   *
   * @param label Label
   * @param value value
   * @param max Maximum value
   */
  public BoundedParameter(String label, double value, double max) {
    this(label, value, 0, max);
  }

  /**
   * A bounded parameter with initial value and range from v0 to v1. Note that it is not necessary for
   * v0 to be less than v1, if it is desired for the knob's value to progress negatively.
   *
   * @param label Label
   * @param value Initial value
   * @param v0 Start of range
   * @param v1 End of range
   */
  public BoundedParameter(String label, double value, double v0, double v1) {
    this(label, value, v0, v1, null);
  }

  /**
   * Creates a BoundedParameter which limits the value of an underlying MutableParameter to a given
   * range. Changes to the BoundedParameter are forwarded to the MutableParameter, and vice versa.
   * If the MutableParameter is set to a value outside the specified bounds, this BoundedParmaeter
   * will ignore the update and the values will be inconsistent. The typical use of this mode is
   * to create a parameter suitable for limited-range UI control of a parameter, typically a
   * MutableParameter.
   *
   * @param underlying The underlying parameter
   * @param v0 Beginning of range
   * @param v1 End of range
   */
  public BoundedParameter(LXListenableParameter underlying, double v0, double v1) {
    this(underlying.getLabel(), underlying.getValue(), v0, v1, underlying);
  }

  protected BoundedParameter(String label, double value, double v0, double v1, LXListenableParameter underlying) {
    super(label, (value < Math.min(v0, v1)) ? Math.min(v0, v1) : ((value > Math
        .max(v0, v1)) ? Math.max(v0, v1) : value));
    this.range = new Range(v0, v1);
    this.underlying = underlying;
    if (this.underlying != null) {
      this.underlying.addListener(new LXParameterListener() {
        public void onParameterChanged(LXParameter p) {
          // NOTE: if the MutableParameter is set to a value outside our range, we ignore it
          // and the values diverge.
          double v = p.getValue();
          if (v >= range.min && v <= range.max) {
            setValue(v);
          }
        }
      });
    }
  }

  @Override
  public BoundedParameter setDescription(String description) {
    return (BoundedParameter) super.setDescription(description);
  }

  public BoundedParameter incrementValue(double amount, boolean wrap) {
    double newValue = getValue() + amount;
    if (wrap) {
      if (newValue > this.range.max) {
        newValue = this.range.min + ((newValue - this.range.max) % this.range.range);
      } else if (newValue < this.range.min) {
        while (newValue < this.range.min) {
          newValue += this.range.range;
        }
      }
    }
    return (BoundedParameter) setValue(newValue);
  }

  /**
   * Sets the value of parameter using normal 0-1
   *
   * @param normalized Value from 0-1 through the parameter range
   * @return this, for method chaining
   */
  public BoundedParameter setNormalized(double normalized) {
    setValue(this.range.normalizedToValue(normalized, getExponent()));
    return this;
  }

  /**
   * Get the range of values for this parameter
   *
   * @return range from min and max
   */
  public double getRange() {
    return this.range.range;
  }

  /**
   * Gets a normalized value of the parameter from 0 to 1
   *
   * @return Normalized value, from 0 to 1
   */
  public double getNormalized() {
    return this.range.getNormalized(getValue(), getExponent());
  }

  /**
   * Normalized value as a float
   *
   * @return Normalized value from 0-1 as a float
   */
  public float getNormalizedf() {
    return (float) getNormalized();
  }

  @Override
  protected double updateValue(double value) {
    value = this.range.constrain(value);
    if (this.underlying != null) {
      this.underlying.setValue(value);
    }
    return value;
  }

}
