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
 * Simple square wave LFO. Not damped. Oscillates between a low and high value.
 */
public class SquareLFO extends LXRangeModulator {

  public SquareLFO(double startValue, double endValue, double periodMs) {
    this(new FixedParameter(startValue), new FixedParameter(endValue),
        new FixedParameter(periodMs));
  }

  public SquareLFO(LXParameter startValue, double endValue, double periodMs) {
    this(startValue, new FixedParameter(endValue), new FixedParameter(periodMs));
  }

  public SquareLFO(double startValue, LXParameter endValue, double periodMs) {
    this(new FixedParameter(startValue), endValue, new FixedParameter(periodMs));
  }

  public SquareLFO(double startValue, double endValue, LXParameter periodMs) {
    this(new FixedParameter(startValue), new FixedParameter(endValue), periodMs);
  }

  public SquareLFO(LXParameter startValue, LXParameter endValue, double periodMs) {
    this(startValue, endValue, new FixedParameter(periodMs));
  }

  public SquareLFO(LXParameter startValue, double endValue, LXParameter periodMs) {
    this(startValue, new FixedParameter(endValue), periodMs);
  }

  public SquareLFO(double startValue, LXParameter endValue, LXParameter periodMs) {
    this(new FixedParameter(startValue), endValue, periodMs);
  }

  public SquareLFO(LXParameter startValue, LXParameter endValue,
      LXParameter periodMs) {
    this("SQUARE", startValue, endValue, periodMs);
  }

  public SquareLFO(String label, double startValue, double endValue,
      double periodMs) {
    this(label, new FixedParameter(startValue), new FixedParameter(endValue),
        new FixedParameter(periodMs));
  }

  public SquareLFO(String label, LXParameter startValue, double endValue,
      double periodMs) {
    this(label, startValue, new FixedParameter(endValue), new FixedParameter(
        periodMs));
  }

  public SquareLFO(String label, double startValue, LXParameter endValue,
      double periodMs) {
    this(label, new FixedParameter(startValue), endValue, new FixedParameter(
        periodMs));
  }

  public SquareLFO(String label, double startValue, double endValue,
      LXParameter periodMs) {
    this(label, new FixedParameter(startValue), new FixedParameter(endValue),
        periodMs);
  }

  public SquareLFO(String label, LXParameter startValue, LXParameter endValue,
      double periodMs) {
    this(label, startValue, endValue, new FixedParameter(periodMs));
  }

  public SquareLFO(String label, LXParameter startValue, double endValue,
      LXParameter periodMs) {
    this(label, startValue, new FixedParameter(endValue), periodMs);
  }

  public SquareLFO(String label, double startValue, LXParameter endValue,
      LXParameter periodMs) {
    this(label, new FixedParameter(startValue), endValue, periodMs);
  }

  public SquareLFO(String label, LXParameter startValue, LXParameter endValue,
      LXParameter periodMs) {
    super(label, startValue, endValue, periodMs);
  }

  @Override
  protected double computeNormalizedValue(double deltaMs, double basis) {
    return (basis < 0.5) ? 0 : 1;
  }

  @Override
  protected double computeNormalizedBasis(double basis, double normalizedValue) {
    return (normalizedValue == 0) ? 0 : 0.5;
  }

}
