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

package heronarts.lx.model;

import heronarts.lx.LX;
import heronarts.lx.transform.LXMatrix;
import heronarts.lx.transform.LXTransform;
import heronarts.lx.transform.LXVector;
import heronarts.lx.utils.LXUtils;

/**
 * A point is a node with a position in space. In addition to basic
 * x/y/z coordinates, it also keeps track of some helper values that
 * are commonly useful during animation. These include normalized values
 * relative to a containing model, as well as polar versions of the
 * xyz coordinates relative to the origin.
 *
 * A point is also assumed to be a member of a larger set of points
 * for which there is an array buffer of color values. The {@link #index}
 * field refers to this points position in that buffer.
 *
 * Generally speaking, point geometry should be treated as immutable.
 * Direct modifications to the values are permitted, but will not
 * trigger updates to the geometry of a containing {@link LXModel}.
 */
public class LXPoint {

  private static int counter = 0;

  /**
   * X coordinate of this point (absolute)
   */
  public float x;

  /**
   * Y coordinate of this point (absolute)
   */
  public float y;

  /**
   * Z coordinate of this point (absolute)
   */
  public float z;

  /**
   * Radius of this point from the origin (0, 0, 0) in 3 dimensions
   */
  public float r;

  /**
   * Radius of this point from the center of the global model
   */
  public float rc;

  /**
   * Radius of this point from origin (0, 0) in the x-y plane
   */
  public float rxy;

  /**
   * Radius of this point from origin (0, 0) in the x-z plane
   */
  public float rxz;

  /**
   * Angle of this point about the origin (0, 0) in the x-y plane
   */
  public float theta;

  /**
   * Angle of this point about the origin in the x-z plane
   * (right-handed angle of rotation about the Y-axis)
   *
   * 0 is pointing straight ahead (+z axis)
   * HALF_PI is to the right (+x axis)
   * PI is backwards (-z axis)
   * 1.5*PI is to the left (-x axis)
   */
  public float azimuth;

  /**
   * Angle of this point between the y-value and the x-z plane
   *
   * 0 is flat
   * HALF_PI is upwards (+y axis)
   * -HALF_PI is downwards (-y axis)
   */
  public float elevation;

  /**
   * Normalized position of point in x-space (0-1);
   */
  public float xn = 0;

  /**
   * Normalized position of point in y-space (0-1);
   */
  public float yn = 0;

  /**
   * Normalized position of point in z-space (0-1);
   */
  public float zn = 0;

  /**
   * Normalized position of point in radial space (0-1), 0 is origin, 1 is max radius
   */
  public float rn = 0;

  /**
   * Normalized position of point in radial space from center of model,
   * 0 is center, 1 is point with max radius from center
   */
  public float rcn = 0;

  /**
   * Index of this point into color buffer
   */
  public int index;

  /**
   * Construct an empty point, value 0, 0, 0
   */
  public LXPoint() {
    this(0, 0, 0);
  }

  /**
   * Construct a point in 2-d space, z will be 0
   *
   * @param x X-coordinate
   * @param y Y-coordinate
   */
  public LXPoint(float x, float y) {
    this(x, y, 0);
  }

  /**
   * Construct a point in 3-d space
   *
   * @param x X-coordinate
   * @param y Y-coordinate
   * @param z Z-coordinate
   */
  public LXPoint(float x, float y, float z) {
    this.x = x;
    this.y = y;
    this.z = z;
    this.index = counter++;
    set();
  }

  /**
   * Construct a copy of another point
   *
   * @param that Point to copy
   */
  public LXPoint(LXPoint that) {
    set(that);
  }

  /**
   * Construct a point in 3-d space
   *
   * @param x X-coordinate
   * @param y Y-coordinate
   * @param z Z-coordinate
   */
  public LXPoint(double x, double y, double z) {
    this((float) x, (float) y, (float) z);
  }

  /**
   * Construct a point in 3-d space based upon a vector
   *
   * @param v LXVector
   */
  public LXPoint(LXVector v) {
    this(v.x, v.y, v.z);
  }

  /**
   * Construct a point from transform
   *
   * @param transform LXTransform stack
   */
  public LXPoint(LXTransform transform) {
    this(transform.x(), transform.y(), transform.z());
  }

  /**
   * Updates this point to a new x-y-z position
   *
   * @param x X-position
   * @param y Y-position
   * @param z Z-position
   * @return this
   */
  public LXPoint set(float x, float y, float z) {
    this.x = x;
    this.y = y;
    this.z = z;
    return set();
  }

  /**
   * Set the x, y, and z values based upon the position of the transform
   *
   * @param transform Transform object
   * @return this
   */
  public LXPoint set(LXTransform transform) {
    return set(transform.x(), transform.y(), transform.z());
  }

  /**
   * Set the x, y, and z values based upon the position of a transform matrix
   *
   * @param matrix Transform matrix object
   * @return this
   */
  public LXPoint set(LXMatrix matrix) {
    return set(matrix.x(), matrix.y(), matrix.z());
  }

  /**
   * Set the x, y, and z values based upon another point multiplied by a transform matrix
   *
   * @param matrix Transform matrix object
   * @param that Another point object
   * @return this
   */
  public LXPoint set(LXMatrix matrix, LXPoint that) {
    float x2 = matrix.m11 * that.x + matrix.m12 * that.y + matrix.m13 * that.z + matrix.m14;
    float y2 = matrix.m21 * that.x + matrix.m22 * that.y + matrix.m23 * that.z + matrix.m24;
    float z2 = matrix.m31 * that.x + matrix.m32 * that.y + matrix.m33 * that.z + matrix.m34;
    return set(x2, y2, z2);
  }

  /**
   * Set the x, y, and z values based upon another point multiplied by a transform matrix
   *
   * @param matrix Transform matrix object
   * @param that Another vector object
   * @return this
   */
  public LXPoint set(LXMatrix matrix, LXVector that) {
    float x2 = matrix.m11 * that.x + matrix.m12 * that.y + matrix.m13 * that.z + matrix.m14;
    float y2 = matrix.m21 * that.x + matrix.m22 * that.y + matrix.m23 * that.z + matrix.m24;
    float z2 = matrix.m31 * that.x + matrix.m32 * that.y + matrix.m33 * that.z + matrix.m34;
    return set(x2, y2, z2);
  }

  /**
   * Sets the values of this point based upon another point
   *
   * @param that Other point to copy into this point
   * @return this
   */
  public LXPoint set(LXPoint that) {
    this.x = that.x;
    this.y = that.y;
    this.z = that.z;
    this.index = that.index;

    this.r = that.r;
    this.rxy = that.rxy;
    this.rxz = that.rxz;
    this.theta = that.theta;
    this.azimuth = that.azimuth;
    this.elevation = that.elevation;

    this.xn = that.xn;
    this.yn = that.yn;
    this.zn = that.zn;
    this.rn = that.rn;

    this.rc = that.rc;
    this.rcn = that.rcn;

    return this;
  }

  /**
   * Sets the X coordinate of the point
   *
   * @param x X-coordinate
   * @return this
   */
  public LXPoint setX(float x) {
    this.x = x;
    return set();
  }

  /**
   * Sets the Y coordinate of the point
   *
   * @param y Y-coordinate
   * @return this
   */
  public LXPoint setY(float y) {
    this.y = y;
    return set();
  }

  /**
   * Sets the Z coordinate of the point
   *
   * @param z Z-coordinate
   * @return this
   */
  public LXPoint setZ(float z) {
    this.z = z;
    return set();
  }

  /**
   * Updates the point's meta-coordinates, based upon the x y z values.
   *
   * @return this
   */
  protected LXPoint set() {
    this.r = (float) Math.sqrt(x * x + y * y + z * z);
    this.rxy = (float) Math.sqrt(x * x + y * y);
    this.rxz = (float) Math.sqrt(x * x + z * z);
    this.theta = (float) ((LX.TWO_PI + Math.atan2(y, x)) % (LX.TWO_PI));
    this.azimuth = (float) ((LX.TWO_PI + Math.atan2(x, z)) % (LX.TWO_PI));
    this.elevation = (float) Math.atan2(y, rxz);
    return this;
  }

  /**
   * Multiplies the points coordinates by the given transformation matrix
   *
   * @param matrix Transformation matrix
   * @return This point, with updated coordinates
   */
  public LXPoint multiply(LXMatrix matrix) {
    float x2 = matrix.m11 * this.x + matrix.m12 * this.y + matrix.m13 * this.z + matrix.m14;
    float y2 = matrix.m21 * this.x + matrix.m22 * this.y + matrix.m23 * this.z + matrix.m24;
    float z2 = matrix.m31 * this.x + matrix.m32 * this.y + matrix.m33 * this.z + matrix.m34;
    return set(x2, y2, z2);
  }

  /**
   * Sets the normalized values on this point, relative to a model
   *
   * @param bounds Model to normalize points relative to
   */
  void normalize(LXNormalizationBounds bounds, LXModel model) {
    this.xn = xn(bounds);
    this.yn = yn(bounds);
    this.zn = zn(bounds);
    this.rn = (model.rMax == 0) ? 0f : this.r / model.rMax;
    this.rc = LXUtils.distf(this.x, this.y, this.z, bounds.cx, bounds.cy, bounds.cz);
  }

  /**
   * Gets the normalized x position of this point relative to the given bounds. If the
   * bounds has no x-dimension, the returned value is 0.5. The value is not necessarily
   * constrained in the range 0-1 if this point lies out of the given bounds.
   *
   * @param bounds Normalization bounds
   * @return Normalized x position of this point in the given bounds, 0-1 if contained
   */
  public float xn(LXNormalizationBounds bounds) {
    return (bounds.xRange == 0) ? .5f : (this.x - bounds.xMin) / bounds.xRange;
  }

  /**
   * Gets the normalized y position of this point relative to the given bounds. If the
   * bounds has no y-dimension, the returned value is 0.5. The value is not necessarily
   * constrained in the range 0-1 if this point lies out of the given bounds.
   *
   * @param bounds Normalization bounds
   * @return Normalized y position of this point in the given bounds, 0-1 if contained
   */
  public float yn(LXNormalizationBounds bounds) {
    return (bounds.yRange == 0) ? .5f : (this.y - bounds.yMin) / bounds.yRange;
  }

  /**
   * Gets the normalized z position of this point relative to the given bounds. If the
   * bounds has no z-dimension, the returned value is 0.5. The value is not necessarily
   * constrained in the range 0-1 if this point lies out of the given bounds.
   *
   * @param bounds Normalization bounds
   * @return Normalized z position of this point in the given bounds, 0-1 if contained
   */
  public float zn(LXNormalizationBounds bounds) {
    return (bounds.zRange == 0) ? .5f : (this.z - bounds.zMin) / bounds.zRange;
  }
}
