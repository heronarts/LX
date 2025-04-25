/**
 * Copyright 2023- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.model;

import heronarts.lx.transform.LXMatrix;
import heronarts.lx.utils.LXUtils;

/**
 * Defines a normalization space
 */
public class LXNormalizationBounds {

  private LXMatrix orientation = null;

  void setOrientation(LXModel reference) {
    this.orientation = new LXMatrix().setInverse(reference.transform);
  }

  /**
   * Translates this point into the normalization orientation space, when
   * re-orientation is needed we use rounding to avoid numerical quantization
   * errors from floating point arithmetic.
   *
   * @param p Point
   * @return x coordinate of this point in reference space
   */
  float px(LXPoint p) {
    return (this.orientation == null) ? p.x : LXUtils.round2f(this.orientation.x(p));
  }

  /**
   * Translates this point into the normalization orientation space, when
   * re-orientation is needed we use rounding to avoid numerical quantization
   * errors from floating point arithmetic.
   *
   * @param p Point
   * @return y coordinate of this point in reference space
   */
  float py(LXPoint p) {
    return (this.orientation == null) ? p.y : LXUtils.round2f(this.orientation.y(p));
  }

  /**
   * Translates this point into the normalization orientation space, when
   * re-orientation is needed we use rounding to avoid numerical quantization
   * errors from floating point arithmetic.
   *
   * @param p Point
   * @return z coordinate of this point in reference space
   */
  float pz(LXPoint p) {
    return (this.orientation == null) ? p.z : LXUtils.round2f(this.orientation.z(p));
  }

  /**
   * Minimum x value
   */
  public float xMin;

  /**
   * Maximum x value
   */
  public float xMax;

  /**
   * Range of x values
   */
  public float xRange;

  /**
   * Minimum y value
   */
  public float yMin;

  /**
   * Maximum y value
   */
  public float yMax;

  /**
   * Range of y values
   */
  public float yRange;

  /**
   * Minimum z value
   */
  public float zMin;

  /**
   * Maximum z value
   */
  public float zMax;

  /**
   * Range of z values
   */
  public float zRange;

  /**
   * Center of the model in x space
   */
  public float cx;

  /**
   * Center of the model in y space
   */
  public float cy;

  /**
   * Center of the model in z space
   */
  public float cz;

}