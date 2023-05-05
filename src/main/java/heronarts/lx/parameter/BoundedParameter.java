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

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.utils.LXUtils;

/**
 * Simple parameter class with a double value.
 */
public class BoundedParameter extends LXListenableNormalizedParameter {

  public enum NormalizationCurve {
    /**
     * Normal exponential curve from v0 to v1 (0 to 1) maps to x^2
     */
    NORMAL,

    /**
     * Normal exponential curve from v1 to v0 (1 to 0) maps to 1 - x^2
     */
    REVERSE,

    /**
     * Exponential curve biased from center, slope is 0 at vCenter
     */
    BIAS_CENTER,

    /**
     * Exponential curve biased at center, slope is steepest at vCenter
     */
    BIAS_OUTER
  };

  private NormalizationCurve curve = NormalizationCurve.NORMAL;

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
      return getNormalized(value, exponent, NormalizationCurve.NORMAL);
    }

    public double getNormalized(double value, double exponent, NormalizationCurve curve) {
      if (this.v0 == this.v1) {
        return 0;
      }
      value = constrain(value);
      double normalized = (value - this.v0) / this.vRange;
      if (exponent != 1) {
        final double expInv = 1./exponent;
        switch (curve) {
        case NORMAL:
          normalized = Math.pow(normalized, expInv);
          break;
        case REVERSE:
          normalized = 1 - Math.pow(1 - normalized, expInv);
          break;
        case BIAS_CENTER:
          if (normalized < 0.5) {
            normalized = 0.5 - 0.5 * Math.pow(2 * (0.5 - normalized), expInv);
          } else {
            normalized = 0.5 + 0.5 * Math.pow(2 * (normalized - 0.5), expInv);
          }
          break;
        case BIAS_OUTER:
          if (normalized < 0.5) {
            normalized = 0.5 * Math.pow(2*normalized, expInv);
          } else {
            normalized = 1 - 0.5 * Math.pow(2*(1-normalized), expInv);
          }
          break;
        }
      }
      return normalized;
    }

    public double normalizedToValue(double normalized) {
      return normalizedToValue(normalized, 1.);
    }

    public double normalizedToValue(double normalized, double exponent) {
      return normalizedToValue(normalized, exponent, NormalizationCurve.NORMAL);
    }

    public double normalizedToValue(double normalized, double exponent, NormalizationCurve curve) {
      if (normalized < 0) {
        normalized = 0;
      } else if (normalized > 1) {
        normalized = 1;
      }
      if (exponent != 1) {
        switch (curve) {
        case NORMAL:
          normalized = Math.pow(normalized, exponent);
          break;
        case REVERSE:
          normalized = 1 - Math.pow(1 - normalized, exponent);
          break;
        case BIAS_CENTER:
          if (normalized < 0.5) {
            normalized = 0.5 - 0.5 * Math.pow(2*(0.5 - normalized), exponent);
          } else {
            normalized = 0.5 + 0.5 * Math.pow(2*(normalized - 0.5), exponent);
          }
          break;
        case BIAS_OUTER:
          if (normalized < 0.5) {
            normalized = 0.5 * Math.pow(2 * normalized, exponent);
          } else {
            normalized = 1 - 0.5 * Math.pow(2 * (1 - normalized), exponent);
          }
          break;
        }
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
      this.underlying.addListener((p) -> {
        // NOTE: if the MutableParameter is set to a value outside our range, we ignore it
        // and the values diverge.
        double v = p.getValue();
        if (v >= this.range.min && v <= this.range.max) {
          setValue(v);
        }
      });
    }
  }

  @Override
  public BoundedParameter setWrappable(boolean wrappable) {
    super.setWrappable(wrappable);
    return this;
  }

  @Override
  public BoundedParameter setMappable(boolean mappable) {
    super.setMappable(mappable);
    return this;
  }

  @Override
  public BoundedParameter setUnits(BoundedParameter.Units units) {
    super.setUnits(units);
    return this;
  }

  @Override
  public BoundedParameter setDescription(String description) {
    super.setDescription(description);
    return this;
  }

  @Override
  public BoundedParameter setPolarity(LXParameter.Polarity polarity) {
    super.setPolarity(polarity);
    return this;
  }

  @Override
  public BoundedParameter setExponent(double exponent) {
    super.setExponent(exponent);
    return this;
  }

  public BoundedParameter setNormalizationCurve(NormalizationCurve curve) {
    this.curve = curve;
    return this;
  }

  public NormalizationCurve getNormalizationCurve() {
    return this.curve;
  }

  private boolean detentOn = false;
  private double[] detents = null;
  private double[] detentsNormalized = null;
  private int iDetent = -1;
  private static final int TICKS_PER_DETENT = 12;
  private int ticks = 0;
  private boolean currentIsDetent = false;

  /**
   * Subclasses can override this method to customize the Detent On state,
   * for example to make it dependent on another parameter.
   *
   * When this method returns True, calls to incrementNormalized()
   * accumulate ticks rather than modifying the underlying value immediately.
   * After a certain number of ticks have accumulated the parameter value
   * will be moved to the next detent in the list.
   */
  public boolean getDetentOn() {
    return this.detentOn;
  }

  /**
   * By default this method allows manual control of Detent On state.
   */
  public BoundedParameter setDetentOn(boolean on) {
    this.detentOn = on;
    return this;
  }

  public final BoundedParameter setDetents(double[] values) {
    this.detents = sanitizeDetents(values);
    this.detentsNormalized = null;
    this.iDetent = -1;
    this.currentIsDetent = false;
    return this;
  }

  public final BoundedParameter setDetentsNormalized(double[] normalized) {
    this.detentsNormalized = sanitizeDetents(normalized, 0, 1);
    this.detents = null;
    this.iDetent = -1;
    this.currentIsDetent = false;
    return this;
  }

  private double[] sanitizeDetents(double[] values) {
    return sanitizeDetents(values, this.range.min, this.range.max);
  }

  private double[] sanitizeDetents(double[] values, double min, double max) {
    List<Double> filtered = new ArrayList<Double>();

    for (double value : values) {
      if (min <= value && value <= max) {
        filtered.add(value);
      } else {
        LX.error("Detent out of range: " + Double.toString(value));
      }
    }

    double[] r = new double[filtered.size()];
    for (int i = 0; i < filtered.size(); i++) {
      r[i] = filtered.get(i);
    }
    return r;
  }

  @Override
  public BoundedParameter incrementValue(double amount) {
    return incrementValue(amount, isWrappable());
  }

  public BoundedParameter incrementValue(double amount, boolean wrap) {
    double newValue = getValue() + amount;
    if (wrap) {
      if (newValue > this.range.max) {
        newValue = this.range.min + ((newValue - this.range.max) % this.range.range);
      } else if (newValue < this.range.min) {
        newValue = this.range.max + ((newValue - this.range.min) % this.range.range);
      }
    }
    this.currentIsDetent = false;   // Would prefer to intercept all calls to setValue() but it is final
    return (BoundedParameter) setValue(newValue);
  }

  // Another example of a place this flag needs to be reset,
  // which could be eliminated if this was done in setValue().
  @Override
  public LXParameter reset() {
    this.currentIsDetent = false;
    return super.reset();
  }


  public LXListenableNormalizedParameter incrementDetentTicks(boolean increase) {
    return incrementDetentTicks(increase, isWrappable());
  }

  public LXListenableNormalizedParameter incrementDetentTicks(boolean increase, boolean wrap) {
    return incrementNormalized(increase ? 1 : -1, wrap, true);
  }

  @Override
  public LXListenableNormalizedParameter incrementNormalized(double amount, boolean wrap) {
    return incrementNormalized(amount, wrap, getDetentOn());
  }

  public LXListenableNormalizedParameter incrementNormalized(double amount, boolean wrap, boolean detent) {
    if (detent) {
      // Swallow increments and convert to ticks
      if (amount > 0) {
        ticks = LXUtils.max(this.ticks, 0) + 1;
        if (this.ticks == TICKS_PER_DETENT) {
          nextDetent(wrap);
        }
      } else if (amount < 0) {
        this.ticks = LXUtils.min(this.ticks, 0) - 1;
        if (this.ticks == 0-TICKS_PER_DETENT) {
          previousDetent(wrap);
        }
      }
      return this;
    } else {
      this.ticks = 0;
      this.currentIsDetent = false;
      return super.incrementNormalized(amount, wrap);
    }
  }

  public BoundedParameter nextDetent() {
    return nextDetent(isWrappable());
  }

  public final BoundedParameter nextDetent(boolean wrap) {
    this.ticks = 0;
    if (this.currentIsDetent) {
      if (this.detents != null && this.detents.length > 0) {
        if (this.iDetent == this.detents.length-1) {
          // Wrap if allowed
          if (wrap) {
            this.iDetent = 0;
            super.setValue(this.detents[this.iDetent]);
          }
        } else {
          this.iDetent++;
          super.setValue(this.detents[this.iDetent]);
        }
      } else if (this.detentsNormalized != null && this.detentsNormalized.length > 0) {
        if (this.iDetent == this.detentsNormalized.length-1) {
          // Wrap if allowed
          if (wrap) {
            this.iDetent = 0;
            super.setValue(this.detentsNormalized[this.iDetent]);
          }
        } else {
          this.iDetent++;
          super.setValue(this.detentsNormalized[this.iDetent]);
        }
      }
    } else {
      if (this.detents != null) {
        // Detents are values
        double value = getValue();
        // Locate next detent above current value
        for (int i=0; i<this.detents.length; i++) {
          if (this.detents[i] > value) {
            this.currentIsDetent = true;
            this.iDetent = i;
            super.setValue(this.detents[this.iDetent]);
            return this;
          }
        }
        // All detents were less than current value. Wrap if allowed.
        if (wrap) {
          if (this.detents.length > 0) {
            this.currentIsDetent = true;
            this.iDetent = 0;
            super.setValue(this.detents[this.iDetent]);
          }
        }
      } else if (this.detentsNormalized != null) {
        // Detents are normalized values
        double normalizedValue = getNormalized();
        // Locate next detent above current value
        for (int i=0; i<this.detentsNormalized.length; i++) {
          if (this.detentsNormalized[i] > normalizedValue) {
            this.currentIsDetent = true;
            this.iDetent = i;
            super.setValue(this.detentsNormalized[this.iDetent]);
            return this;
          }
        }
        // All detents were less than current value. Wrap if allowed.
        if (wrap) {
          if (this.detentsNormalized.length > 0) {
            this.currentIsDetent = true;
            this.iDetent = 0;
            super.setValue(this.detentsNormalized[this.iDetent]);
          }
        }
      }
    }
    return this;
  }

  public BoundedParameter previousDetent() {
    return previousDetent(isWrappable());
  }

  public final BoundedParameter previousDetent(boolean wrap) {
    this.ticks = 0;
    if (this.currentIsDetent) {
      if (this.detents != null && this.detents.length > 0) {
        if (this.iDetent <= 0) {
          // Wrap if allowed
          if (wrap) {
            this.iDetent = this.detents.length - 1;
            super.setValue(this.detents[this.iDetent]);
          }
        } else {
          this.iDetent--;
          super.setValue(this.detents[this.iDetent]);
        }
      } else if (this.detentsNormalized != null && this.detentsNormalized.length > 0) {
        if (this.iDetent <= 0) {
          // Wrap if allowed
          if (wrap) {
            this.iDetent = this.detentsNormalized.length - 1;
            super.setValue(this.detentsNormalized[this.iDetent]);
          }
        } else {
          this.iDetent--;
          super.setValue(this.detentsNormalized[this.iDetent]);
        }
      }
    } else {
      if (this.detents != null) {
        // Detents are values
        double value = getValue();
        // Locate next detent below current value
        for (int i=this.detents.length-1; i>=0; i--) {
          if (this.detents[i] < value) {
            this.currentIsDetent = true;
            this.iDetent = i;
            super.setValue(this.detents[this.iDetent]);
            return this;
          }
        }
        // All detents were above current value. Wrap if allowed.
        if (wrap) {
          if (this.detents.length > 0) {
            this.currentIsDetent = true;
            this.iDetent = this.detents.length - 1;
            super.setValue(this.detents[this.iDetent]);
          }
        }
      } else if (this.detentsNormalized != null) {
        // Detents are normalized values
        double normalizedValue = getNormalized();
        // Locate next detent below current value
        for (int i=this.detentsNormalized.length-1; i>=0; i--) {
          if (this.detentsNormalized[i] < normalizedValue) {
            this.currentIsDetent = true;
            this.iDetent = i;
            super.setValue(this.detentsNormalized[this.iDetent]);
            return this;
          }
        }
        // All detents were above current value. Wrap if allowed.
        if (wrap) {
          if (this.detentsNormalized.length > 0) {
            this.currentIsDetent = true;
            this.iDetent = this.detentsNormalized.length - 1;
            super.setValue(this.detentsNormalized[this.iDetent]);
          }
        }
      }
    }
    return this;
  }

  /**
   * Sets the value of parameter using normal 0-1
   *
   * @param normalized Value from 0-1 through the parameter range
   * @return this, for method chaining
   */
  public BoundedParameter setNormalized(double normalized) {
    setValue(this.range.normalizedToValue(normalized, getExponent(), getNormalizationCurve()));
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
    return this.range.getNormalized(getValue(), getExponent(), getNormalizationCurve());
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
