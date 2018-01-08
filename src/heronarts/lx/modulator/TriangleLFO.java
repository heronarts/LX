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
 * A triangular LFO is a simple linear modulator that oscillates between a low
 * and hi value over a specified time period.
 */
public class TriangleLFO extends LXRangeModulator {

  public TriangleLFO(double startValue, double endValue, double periodMs) {
    this(new FixedParameter(startValue), new FixedParameter(endValue),
        new FixedParameter(periodMs));
  }

  public TriangleLFO(LXParameter startValue, double endValue, double periodMs) {
    this(startValue, new FixedParameter(endValue), new FixedParameter(periodMs));
  }

  public TriangleLFO(double startValue, LXParameter endValue, double periodMs) {
    this(new FixedParameter(startValue), endValue, new FixedParameter(periodMs));
  }

  public TriangleLFO(double startValue, double endValue, LXParameter periodMs) {
    this(new FixedParameter(startValue), new FixedParameter(endValue), periodMs);
  }

  public TriangleLFO(LXParameter startValue, LXParameter endValue,
      double periodMs) {
    this(startValue, endValue, new FixedParameter(periodMs));
  }

  public TriangleLFO(LXParameter startValue, double endValue,
      LXParameter periodMs) {
    this(startValue, new FixedParameter(endValue), periodMs);
  }

  public TriangleLFO(double startValue, LXParameter endValue,
      LXParameter periodMs) {
    this(new FixedParameter(startValue), endValue, periodMs);
  }

  public TriangleLFO(LXParameter startValue, LXParameter endValue,
      LXParameter periodMs) {
    this("TRI", startValue, endValue, periodMs);
  }

  public TriangleLFO(String label, double startValue, double endValue,
      double periodMs) {
    this(label, new FixedParameter(startValue), new FixedParameter(endValue),
        new FixedParameter(periodMs));
  }

  public TriangleLFO(String label, LXParameter startValue, double endValue,
      double periodMs) {
    this(label, startValue, new FixedParameter(endValue), new FixedParameter(
        periodMs));
  }

  public TriangleLFO(String label, double startValue, LXParameter endValue,
      double periodMs) {
    this(label, new FixedParameter(startValue), endValue, new FixedParameter(
        periodMs));
  }

  public TriangleLFO(String label, double startValue, double endValue,
      LXParameter periodMs) {
    this(label, new FixedParameter(startValue), new FixedParameter(endValue),
        periodMs);
  }

  public TriangleLFO(String label, LXParameter startValue,
      LXParameter endValue, double periodMs) {
    this(label, startValue, endValue, new FixedParameter(periodMs));
  }

  public TriangleLFO(String label, LXParameter startValue, double endValue,
      LXParameter periodMs) {
    this(label, startValue, new FixedParameter(endValue), periodMs);
  }

  public TriangleLFO(String label, double startValue, LXParameter endValue,
      LXParameter periodMs) {
    this(label, new FixedParameter(startValue), endValue, periodMs);
  }

  public TriangleLFO(String label, LXParameter startValue,
      LXParameter endValue, LXParameter periodMs) {
    super(label, startValue, endValue, periodMs);
  }

  @Override
  protected double computeNormalizedValue(double deltaMs, double basis) {
    if (basis < 0.5) {
      return 2. * basis;
    } else {
      return 1. - 2. * (basis - 0.5);
    }
  }

  @Override
  protected double computeNormalizedBasis(double basis, double normalizedValue) {
    if (basis < 0.5) {
      return normalizedValue / 2.;
    } else {
      return 1 - (normalizedValue / 2.);
    }
  }
}