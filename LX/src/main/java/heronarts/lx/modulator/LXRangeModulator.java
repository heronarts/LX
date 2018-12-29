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
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;

/**
 * Utility subclass for modulators which oscillate in a range of values between
 * a minimum and a maximum. Includes a set of common methods to change the
 * bounds while the modulator is running, keeping values within bounds. All
 * scaling is done by this class, subclasses can just work in a normalized space
 * between 0 and 1.
 */
public abstract class LXRangeModulator extends LXPeriodicModulator implements LXNormalizedParameter {

  private LXParameter startValue;
  private LXParameter endValue;

  private double exponent = 1;

  protected LXRangeModulator(String label, LXParameter startValue, LXParameter endValue, LXParameter periodMs) {
    super(label, periodMs);
    this.startValue = startValue;
    this.endValue = endValue;
    updateValue(startValue.getValue());
  }

  /**
   * Updates the range of the modulator.
   *
   * @param startValue New start value
   * @param endValue New final value
   * @param periodMs New period, in milliseconds
   * @return this
   */
  public final LXRangeModulator setRange(double startValue, double endValue, double periodMs) {
    this.setPeriod(periodMs);
    this.startValue = new FixedParameter(startValue);
    this.endValue = new FixedParameter(endValue);
    onRangeChanged();
    return this;
  }

  public LXRangeModulator setExponent(double exponent) {
    if (exponent <= 0) {
      throw new IllegalArgumentException("May not set zero or negative exponent");
    }
    this.exponent = exponent;
    return this;
  }

  public double getExponent() {
    return this.exponent;
  }

  /**
   * Sets the range of the modulator, maintaining the period.
   *
   * @param startValue New start value
   * @param endValue New end value
   * @return this
   */
  public final LXRangeModulator setRange(double startValue, double endValue) {
    this.startValue = new FixedParameter(startValue);
    this.endValue = new FixedParameter(endValue);
    onRangeChanged();
    return this;
  }

  /**
   * Updates the initial value
   *
   * @param startValue New start value
   * @return this
   */
  public final LXRangeModulator setStartValue(double startValue) {
    return setStartValue(new FixedParameter(startValue));
  }

  /**
   * Updates the final value
   *
   * @param endValue New final value
   * @return this
   */
  public final LXRangeModulator setEndValue(double endValue) {
    return setEndValue(new FixedParameter(endValue));
  }

  /**
   * Updates the range to proceed from the current value to a new end value.
   * Future oscillations will use the current value as the starting value.
   *
   * @param endValue New end value
   * @return this
   */
  public final LXRangeModulator setRangeFromHereTo(double endValue) {
    return setRange(getValue(), endValue);
  }

  /**
   * Updates the range to proceed from the current value to a new end value with
   * a new period. Future oscillations will use the current value as the
   * starting value.
   *
   * @param endValue New end value
   * @param periodMs New period, in milliseconds
   * @return this
   */
  public final LXRangeModulator setRangeFromHereTo(double endValue, double periodMs) {
    return setRange(getValue(), endValue, periodMs);
  }

  /**
   * After an update to the start or end value, this method is invoked to
   * confirm that the current value is still in bounds. If it is not, it is
   * pulled to the nearest boundary. If it is in bounds, we update the basis so
   * that there is not a discontinuous "jump" on the next cycle.
   */
  private void onRangeChanged() {
    double sv = this.startValue.getValue();
    double ev = this.endValue.getValue();
    double min = Math.min(sv, ev);
    double max = Math.max(sv, ev);
    double value = getValue();
    if (value < min) {
      this.setValue(min);
    } else if (value > max) {
      this.setValue(max);
    } else {
      this.updateBasis(value);
    }
  }

  /**
   * Assigns a parameter to modulate the start value of this modulator.
   *
   * @param startValue A parameter to modify the start value
   * @return this
   */
  public LXRangeModulator setStartValue(LXParameter startValue) {
    this.startValue = startValue;
    onRangeChanged();
    return this;
  }

  /**
   * Assigns a parameter to modulate the end value of this modulator.
   *
   * @param endValue A parameter to modify the start value
   * @return this
   */
  public LXRangeModulator setEndValue(LXParameter endValue) {
    this.endValue = endValue;
    onRangeChanged();
    return this;
  }

  @Override
  public void onSetValue(double value) {
    double sv = this.startValue.getValue();
    double ev = this.endValue.getValue();
    double min = Math.min(sv, ev);
    double max = Math.max(sv, ev);
    if (value < min) {
      updateValue(min);
      updateBasis(min);
    } else if (value > max) {
      updateValue(max);
      updateBasis(max);
    } else {
      updateBasis(value);
    }
  }

  public final LXRangeModulator setNormalized(double normalized) {
    double sv = this.startValue.getValue();
    double ev = this.endValue.getValue();
    setValue(sv + (ev - sv) * normalized);
    return this;
  }

  private final double getNormalized(double value) {
    double sv = this.startValue.getValue();
    double ev = this.endValue.getValue();
    if (sv == ev) {
      return 0;
    }
    return (value - sv) / (ev - sv);
  }

  public final double getNormalized() {
    return getNormalized(getValue());
  }

  public final float getNormalizedf() {
    return (float) getNormalized();
  }

  @Override
  protected final double computeValue(double deltaMs, double basis) {
    double sv = this.startValue.getValue();
    double ev = this.endValue.getValue();
    if (sv == ev) {
      return sv;
    }
    return sv + this.computeNormalizedValue(deltaMs, basis) * (ev - sv);
  }

  @Override
  protected final double computeBasis(double basis, double value) {
    double sv = this.startValue.getValue();
    double ev = this.endValue.getValue();
    if (sv == ev) {
      return 0;
    }
    return computeNormalizedBasis(basis, getNormalized(value));
  }

  /**
   * Subclasses implement this which returns their value from a 0-1 scale. This
   * class automatically takes care of scaling to the startValue/endValue range.
   *
   * @param deltaMs Milliseconds elapsed
   * @param basis Basis of modulator
   * @return Modulator value
   */
  protected abstract double computeNormalizedValue(double deltaMs, double basis);

  /**
   * Subclasses determine the basis based on a normalized value from 0 to 1.
   *
   * @param basis Modulator basis
   * @param normalizedValue A normalize value from 0 to 1
   * @return Computed normalized basis for modulator
   */
  protected abstract double computeNormalizedBasis(double basis, double normalizedValue);

}
