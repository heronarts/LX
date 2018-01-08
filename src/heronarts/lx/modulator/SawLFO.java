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
 * A sawtooth LFO oscillates from one extreme value to another. When the later
 * value is hit, the oscillator rests to its initial value.
 */
public class SawLFO extends LXRangeModulator {

  public SawLFO(double startValue, double endValue, double periodMs) {
    this(new FixedParameter(startValue), new FixedParameter(endValue),
        new FixedParameter(periodMs));
  }

  public SawLFO(LXParameter startValue, double endValue, double periodMs) {
    this(startValue, new FixedParameter(endValue), new FixedParameter(periodMs));
  }

  public SawLFO(double startValue, LXParameter endValue, double periodMs) {
    this(new FixedParameter(startValue), endValue, new FixedParameter(periodMs));
  }

  public SawLFO(double startValue, double endValue, LXParameter periodMs) {
    this(new FixedParameter(startValue), new FixedParameter(endValue), periodMs);
  }

  public SawLFO(LXParameter startValue, LXParameter endValue, double periodMs) {
    this(startValue, endValue, new FixedParameter(periodMs));
  }

  public SawLFO(LXParameter startValue, double endValue, LXParameter periodMs) {
    this(startValue, new FixedParameter(endValue), periodMs);
  }

  public SawLFO(double startValue, LXParameter endValue, LXParameter periodMs) {
    this(new FixedParameter(startValue), endValue, periodMs);
  }

  public SawLFO(LXParameter startValue, LXParameter endValue,
      LXParameter periodMs) {
    this("SAW", startValue, endValue, periodMs);
  }

  public SawLFO(String label, double startValue, double endValue,
      double periodMs) {
    this(label, new FixedParameter(startValue), new FixedParameter(endValue),
        new FixedParameter(periodMs));
  }

  public SawLFO(String label, LXParameter startValue, double endValue,
      double periodMs) {
    this(label, startValue, new FixedParameter(endValue), new FixedParameter(
        periodMs));
  }

  public SawLFO(String label, double startValue, LXParameter endValue,
      double periodMs) {
    this(label, new FixedParameter(startValue), endValue, new FixedParameter(
        periodMs));
  }

  public SawLFO(String label, double startValue, double endValue,
      LXParameter periodMs) {
    this(label, new FixedParameter(startValue), new FixedParameter(endValue),
        periodMs);
  }

  public SawLFO(String label, LXParameter startValue, LXParameter endValue,
      double periodMs) {
    this(label, startValue, endValue, new FixedParameter(periodMs));
  }

  public SawLFO(String label, LXParameter startValue, double endValue,
      LXParameter periodMs) {
    this(label, startValue, new FixedParameter(endValue), periodMs);
  }

  public SawLFO(String label, double startValue, LXParameter endValue,
      LXParameter periodMs) {
    this(label, new FixedParameter(startValue), endValue, periodMs);
  }

  public SawLFO(String label, LXParameter startValue, LXParameter endValue,
      LXParameter periodMs) {
    super(label, startValue, endValue, periodMs);
  }

  @Override
  protected final double computeNormalizedValue(double deltaMs, double basis) {
    return basis;
  }

  @Override
  protected final double computeNormalizedBasis(double basis,
      double normalizedValue) {
    return normalizedValue;
  }
}