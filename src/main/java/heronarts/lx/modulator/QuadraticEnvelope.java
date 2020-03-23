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
 * A quadratic envelope moves from one value to another along a quadratic curve.
 */
public class QuadraticEnvelope extends LXRangeModulator {

  /**
   * Different modes of quadratic easing.
   */
  public enum Ease {
    /**
     * The quadratic curve accelerates towards the final value
     */
    IN,

    /**
     * The quadratic curve decelerates towards the final value
     */
    OUT,

    /**
     * The curve slops on both the start and end values
     */
    BOTH
  };

  private Ease ease = Ease.IN;

  public QuadraticEnvelope(double startValue, double endValue, double periodMs) {
    this(new FixedParameter(startValue), new FixedParameter(endValue), new FixedParameter(periodMs));
  }

  public QuadraticEnvelope(LXParameter startValue, double endValue, double periodMs) {
    this(startValue, new FixedParameter(endValue), new FixedParameter(periodMs));
  }

  public QuadraticEnvelope(double startValue, LXParameter endValue, double periodMs) {
    this(new FixedParameter(startValue), endValue, new FixedParameter(periodMs));
  }

  public QuadraticEnvelope(double startValue, double endValue, LXParameter periodMs) {
    this(new FixedParameter(startValue), new FixedParameter(endValue), periodMs);
  }

  public QuadraticEnvelope(LXParameter startValue, LXParameter endValue, double periodMs) {
    this(startValue, endValue, new FixedParameter(periodMs));
  }

  public QuadraticEnvelope(LXParameter startValue, double endValue, LXParameter periodMs) {
    this(startValue, new FixedParameter(endValue), periodMs);
  }

  public QuadraticEnvelope(double startValue, LXParameter endValue, LXParameter periodMs) {
    this(new FixedParameter(startValue), endValue, periodMs);
  }

  public QuadraticEnvelope(LXParameter startValue, LXParameter endValue, LXParameter periodMs) {
    this("QENV", startValue, endValue, periodMs);
  }

  public QuadraticEnvelope(String label, double startValue, double endValue, double periodMs) {
    this(label, new FixedParameter(startValue), new FixedParameter(endValue), new FixedParameter(periodMs));
  }

  public QuadraticEnvelope(String label, LXParameter startValue, double endValue, double periodMs) {
    this(label, startValue, new FixedParameter(endValue), new FixedParameter(periodMs));
  }

  public QuadraticEnvelope(String label, double startValue, LXParameter endValue, double periodMs) {
    this(label, new FixedParameter(startValue), endValue, new FixedParameter(periodMs));
  }

  public QuadraticEnvelope(String label, double startValue, double endValue, LXParameter periodMs) {
    this(label, new FixedParameter(startValue), new FixedParameter(endValue), periodMs);
  }

  public QuadraticEnvelope(String label, LXParameter startValue, LXParameter endValue, double periodMs) {
    this(label, startValue, endValue, new FixedParameter(periodMs));
  }

  public QuadraticEnvelope(String label, LXParameter startValue, double endValue, LXParameter periodMs) {
    this(label, startValue, new FixedParameter(endValue), periodMs);
  }

  public QuadraticEnvelope(String label, double startValue, LXParameter endValue, LXParameter periodMs) {
    this(label, new FixedParameter(startValue), endValue, periodMs);
  }

  public QuadraticEnvelope(String label, LXParameter startValue, LXParameter endValue, LXParameter periodMs) {
    super(label, startValue, endValue, periodMs);
    setExponent(2);
    setLooping(false);
  }

  /**
   * Sets the easing type
   *
   * @param ease easing type
   * @return this
   */
  public QuadraticEnvelope setEase(Ease ease) {
    this.ease = ease;
    return this;
  }

  @Override
  protected double computeNormalizedValue(double deltaMs, double basis) {
    double exponent = getExponent();
    switch (this.ease) {
    case IN:
      return Math.pow(basis, exponent);
    case OUT:
      return 1 - Math.pow(1 - basis, exponent);
    case BOTH:
      if (basis < 0.5) {
        return .5 * Math.pow(2*basis, exponent);
      } else {
        return .5 + .5 * (1 - Math.pow(1 - 2 * (basis - 0.5), exponent));
      }
    }
    return 0;
  }

  @Override
  protected double computeNormalizedBasis(double basis, double normalizedValue) {
    switch (this.ease) {
    case IN:
      return Math.sqrt(normalizedValue);
    case OUT:
      return 1 - Math.sqrt(1 - normalizedValue);
    case BOTH:
      if (normalizedValue < 0.5) {
        normalizedValue = normalizedValue * 2;
        return Math.sqrt(normalizedValue) / 2.;
      } else {
        normalizedValue = (normalizedValue - 0.5) * 2;
        return 0.5 + (1 - Math.sqrt(1 - normalizedValue)) / 2.;
      }
    }
    return 0;
  }

}
