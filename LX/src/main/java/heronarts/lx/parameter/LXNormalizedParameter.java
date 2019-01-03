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

/**
 * A parameter that supports values in a normalized form, from 0 to 1. This only
 * makes sense for parameters with fixed, finite ranges. The calls to
 * setNormalized() and getNormalized() operate in this space, while getValue()
 * respects the actual given value range.
 */
public interface LXNormalizedParameter extends LXParameter {

  /**
   * Sets the value or the parameter in normalized space from 0 to 1
   *
   * @param value The normalized value, from 0 to 1
   * @return this, for method chaining
   */
  public LXNormalizedParameter setNormalized(double value);

  /**
   * Gets the value of the parameter in a normalized space from 0 to 1
   *
   * @return Value of parameter, normalized to range from 0 to 1
   */
  public double getNormalized();

  /**
   * Gets the value of the parameter in a normalized space as a float
   *
   * @return Normalized value of parameter, in range from 0 to 1
   */
  public float getNormalizedf();

  /**
   * Gets the exponent used for scaling this parameter across its normalized range.
   * Default is 1 which means linear scaling.
   *
   * @return scaling exponent
   */
  public double getExponent();

}
