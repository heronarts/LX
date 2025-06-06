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
 * Parameter type with a discrete set of possible integer values.
 */
public class DiscreteParameter extends LXListenableNormalizedParameter {

  protected int minValue;

  protected int maxValue; // inclusive

  protected long range;

  private String[] options = null;

  public enum IncrementMode {
    NORMALIZED,
    RELATIVE
  }

  private IncrementMode incrementMode = IncrementMode.NORMALIZED;

  public final MutableParameter optionsChanged = new MutableParameter();

  /**
   * Parameter with values from [0, range-1], 0 by default
   *
   * @param label Name of parameter
   * @param range range of values
   */
  public DiscreteParameter(String label, int range) {
    this(label, 0, range);
  }

  /**
   * Parameter with values from [min, max-1], min by default
   *
   * @param label Label
   * @param min Minimum value
   * @param max Maximum value is 1 less than this
   */
  public DiscreteParameter(String label, int min, int max) {
    this(label, min, min, max);
  }

  /**
   * Parameter with values from [min, max-1], value by default
   *
   * @param label Label
   * @param value Default value
   * @param min Minimum value (inclusive)
   * @param max Maximum value (exclusive)
   */
  public DiscreteParameter(String label, int value, int min, int max) {
    super(label, value);
    setRange(min, max);
    setUnits(LXParameter.Units.INTEGER);
    setOscMode(OscMode.ABSOLUTE);
  }

  /**
   * Parameter with set of String label values
   *
   * @param label Label
   * @param options Values
   */
  public DiscreteParameter(String label, String[] options) {
    this(label, options.length);
    this.options = options;
  }

  /**
   * Parameter with set of String label values, and a default
   *
   * @param label Label
   * @param options Values
   * @param value Default index
   */
  public DiscreteParameter(String label, String[] options, int value) {
    this(label, value, 0, options.length);
    this.options = options;
  }

  public DiscreteParameter setFormatter(Formatter formatter, boolean setOptions) {
    setFormatter(formatter);
    if (setOptions) {
      setOptions(formatter);
    }
    return this;
  }

  @Override
  public DiscreteParameter setFormatter(Formatter formatter) {
    super.setFormatter(formatter);
    return this;
  }

  @Override
  public DiscreteParameter setUnits(DiscreteParameter.Units units) {
    super.setUnits(units);
    return this;
  }

  @Override
  public DiscreteParameter setWrappable(boolean wrappable) {
    super.setWrappable(wrappable);
    return this;
  }

  @Override
  public DiscreteParameter setMappable(boolean mappable) {
    super.setMappable(mappable);
    return this;
  }

  @Override
  public DiscreteParameter setDescription(String description) {
    super.setDescription(description);
    return this;
  }

  @Override
  protected double updateValue(double value) {
    if (value < this.minValue) {
      return this.minValue
          + (this.range - ((int) (this.minValue - value) % this.range))
          % this.range;
    }
    return this.minValue + ((int) (value - this.minValue) % this.range);
  }

  /**
   * Returns the minimum value (inclusive) this parameter can hold
   *
   * @return Min value, inclusive
   */
  public final int getMinValue() {
    return this.minValue;
  }

  /**
   * Returns the maximum value (inclusive) this parameter can hold
   *
   * @return Maximum value, inclusive
   */
  public final int getMaxValue() {
    return this.maxValue;
  }

  /**
   * Gets the number of discrete values this parameter can hold as an integer. Will
   * throw an exception if the range is too large to be represented as an integer.
   *
   * @return Number of distinct values this parameter can hold
   */
  public final int getRangei() {
    if (this.range > Integer.MAX_VALUE) {
      throw new IllegalStateException("Cannot provide integer range for DiscreteParameter exceeding integer bounds: " + this + " -> " + this.range);
    }
    return (int) this.range;
  }

  /**
   * Returns the number of discrete values this parameter can hold, the size of the
   * inclusive range [getMinValue(), getMaxValue()]
   *
   * @return Number of distinct values this parameter can hold
   */
  public final long getRange() {
    return this.range;
  }

  /**
   * The set of string labels for these parameters
   *
   * @return Strings, may be null
   */
  public String[] getOptions() {
    return this.options;
  }

  /**
   * The currently selected option
   *
   * @return String description, or numerical value
   */
  public String getBaseOption() {
    return (this.options != null) ? this.options[getBaseIndex()] : getFormatter().format(getBaseValuei());
  }

  /**
   * The currently selected option
   *
   * @return String description, or numerical value
   */
  public String getOption() {
    return (this.options != null) ? this.options[getIndex()] : getFormatter().format(getValuei());
  }

  public String getOption(double value) {
    return (this.options != null) ? this.options[(int) value - this.minValue] : getFormatter().format(value);
  }

  /**
   * Set the range and option strings for the parameter
   *
   * @param options Array of string labels
   * @return this
   */
  public DiscreteParameter setOptions(String[] options) {
    return setOptions(options, true);
  }

  /**
   * Set the options to the strings generated by the formatter
   *
   * @param formatter Formatter
   * @return Parameter with options updated
   */
  public DiscreteParameter setOptions(Formatter formatter) {
    if (this.range > Integer.MAX_VALUE) {
      throw new IllegalStateException("Cannot setOptions() on a DiscreteParameter outside of Integer size range:"  + this.range);
    }
    String[] options = new String[(int) this.range];
    int i = 0;
    for (int v = this.minValue; v <= this.maxValue; ++v) {
      options[i++] = formatter.format(v);
    }
    setOptions(options);
    return this;
  }

  /**
   * Set the range and option strings for the parameter
   *
   * @param options Array of string labels
   * @param updateRange Also update the range
   * @return this
   */
  public DiscreteParameter setOptions(String[] options, boolean updateRange) {
    if (this.options != options) {
      this.options = options;
      if (options != null) {
        if (updateRange) {
          setRange(options.length);
        } else if (options.length != this.range) {
          throw new IllegalArgumentException("Cannot set options array with length different from range: " + this.range + " != " + options.length);
        }
      }
      this.optionsChanged.bang();
    }
    return this;
  }

  /**
   * Sets the range from [minValue, maxValue-1] inclusive
   *
   * @param minValue Minimum value
   * @param maxValue Maximum value, exclusive
   * @return this
   */
  public final DiscreteParameter setRange(int minValue, int maxValue) {
    return setRange(minValue, (long) maxValue);
  }

  /**
   * Sets the range from [minValue, maxValue-1] inclusive
   *
   * @param minValue Minimum value
   * @param maxValue Maximum value, exclusive
   * @return this
   */
  public DiscreteParameter setRange(int minValue, long maxValue) {
    if (maxValue > 1 + (long) Integer.MAX_VALUE) {
      throw new IllegalArgumentException("DiscreteParameter maxValue may not exceed Integer.MAX_VALUE, given " + maxValue);
    }
    if (this.options != null && (this.options.length != maxValue - minValue)) {
      throw new UnsupportedOperationException("May not call setRange on a DiscreteParameter with String options of different length");
    }
    if (maxValue <= minValue) {
      throw new IllegalArgumentException("DiscreteParameter must have range of at least 1");
    }
    this.minValue = minValue;
    this.maxValue = (int) (maxValue - 1);
    this.range = maxValue - minValue;
    setValue(LXUtils.constrain(getBaseValuei(), this.minValue, this.maxValue));
    return this;
  }

  /**
   * Sets range from [0, range-1] inclusive
   *
   * @param range Number of discrete values
   * @return this
   */
  public final DiscreteParameter setRange(int range) {
    return setRange(0, (long) range);
  }

  /**
   * Set the value by index
   *
   * @param index Index in all eligible values, 0 corresponds to minimum value
   * @return this
   */
  public DiscreteParameter setIndex(int index) {
    setValue(index + this.minValue);
    return this;
  }

  public DiscreteParameter increment() {
    return increment(1, isWrappable());
  }

  public DiscreteParameter increment(boolean wrap) {
    return increment(1, wrap);
  }

  public DiscreteParameter increment(int amt) {
    return increment(amt, isWrappable());
  }

  public DiscreteParameter increment(int amt, boolean wrap) {
    if (wrap) {
      setValue(getBaseValuei() + amt);
    } else {
      setValue(Math.min(this.minValue + this.range - 1, getBaseValuei() + amt));
    }
    return this;
  }

  public DiscreteParameter decrement() {
    return decrement(1, isWrappable());
  }

  public DiscreteParameter decrement(boolean wrap) {
    return decrement(1, wrap);
  }

  public DiscreteParameter decrement(int amt) {
    return decrement(amt, isWrappable());
  }

  public DiscreteParameter decrement(int amt, boolean wrap) {
    if (wrap) {
      setValue(getValuei() - amt);
    } else {
      setValue(Math.max(this.minValue, getBaseValuei() - amt));
    }
    return this;
  }

  public int getValuei() {
    return (int) getValue();
  }

  public int getBaseValuei() {
    return getValuei();
  }

  public int getIndex() {
    return getValuei() - this.minValue;
  }

  public int getBaseIndex() {
    return getBaseValuei() - this.minValue;
  }

  public double getNormalized() {
    if (this.range == 1) {
      return 0;
    }
    return (getValue() - this.minValue) / (this.range - 1);
  }

  @Override
  public double getValueFromNormalized(double normalized) {
    return normalizedToValue(normalized);
  }

  protected int normalizedToValue(double normalized) {
    long increment = LXUtils.constrainl((long) (normalized * this.range), 0, this.range-1);
    return (int) (this.minValue + increment);
  }

  public DiscreteParameter setNormalized(double normalized) {
    setValue(normalizedToValue(normalized));
    return this;
  }

  public IncrementMode getIncrementMode() {
    return this.incrementMode;
  }

  public DiscreteParameter setIncrementMode(IncrementMode incrementMode) {
    this.incrementMode = incrementMode;
    return this;
  }

}
