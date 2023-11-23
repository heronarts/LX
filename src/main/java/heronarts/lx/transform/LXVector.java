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

package heronarts.lx.transform;

import heronarts.lx.model.LXPoint;
import heronarts.lx.utils.LXUtils;

import java.lang.Math;

/**
 * A mutable version of an LXPoint, which has had a transformation applied to
 * it, and may have other transformations applied to it. For Processing applications,
 * this mostly conforms to the PVector API.
 */
public class LXVector {

  public float x;

  public float y;

  public float z;

  /**
   * Helper to retrieve the point this corresponds to
   */
  public final LXPoint point;

  /**
   * Index of the LXPoint this corresponds to
   */
  public final int index;

  /**
   * Construct a mutable vector
   */
  public LXVector() {
    this(0, 0, 0);
  }

  /**
   * Construct a mutable vector based on an LXPoint
   *
   * @param point Point with index reference
   */
  public LXVector(LXPoint point) {
    this.x = point.x;
    this.y = point.y;
    this.z = point.z;
    this.point = point;
    this.index = point.index;
  }

  public LXVector(LXVector that) {
    this.x = that.x;
    this.y = that.y;
    this.z = that.z;
    this.point = that.point;
    this.index = that.index;
  }

  public LXVector(float x, float y, float z) {
    this.x = x;
    this.y = y;
    this.z = z;
    this.point = null;
    this.index = -1;
  }

  public LXVector set(float x, float y) {
    this.x = x;
    this.y = y;
    return this;
  }

  public LXVector set(float x, float y, float z) {
    this.x = x;
    this.y = y;
    this.z = z;
    return this;
  }

  public LXVector set(LXVector that) {
    this.x = that.x;
    this.y = that.y;
    this.z = that.z;
    return this;
  }

  public LXVector set(LXPoint that) {
    this.x = that.x;
    this.y = that.y;
    this.z = that.z;
    return this;
  }

  public LXVector copy() {
    return new LXVector(this);
  }

  public LXVector add(float x, float y) {
    this.x += x;
    this.y += y;
    return this;
  }

  public LXVector add(float x, float y, float z) {
    this.x += x;
    this.y += y;
    this.z += z;
    return this;
  }

  public LXVector add(LXVector that) {
    this.x += that.x;
    this.y += that.y;
    this.z += that.z;
    return this;
  }

  public LXVector add(LXVector that, float amount) {
    this.x += that.x * amount;
    this.y += that.y * amount;
    this.z += that.z * amount;
    return this;
  }

  public LXVector sub(float x, float y) {
    this.x -= x;
    this.y -= y;
    return this;
  }

  public LXVector sub(float x, float y, float z) {
    this.x -= x;
    this.y -= y;
    this.z -= z;
    return this;
  }

  public LXVector sub(LXVector that) {
    this.x -= that.x;
    this.y -= that.y;
    this.z -= that.z;
    return this;
  }

  public LXVector mult(float n) {
    this.x *= n;
    this.y *= n;
    this.z *= n;
    return this;
  }

  public LXVector mult(LXVector that) {
    this.x *= that.x;
    this.y *= that.y;
    this.z *= that.z;
    return this;
  }

  public LXVector div(float n) {
    this.x /= n;
    this.y /= n;
    this.z /= n;
    return this;
  }

  public float mag() {
    return (float) Math.sqrt(this.x*this.x + this.y*this.y + this.z*this.z);
  }

  public float magSq() {
    return this.x*this.x + this.y*this.y + this.z*this.z;
  }

  public float dist(LXVector that) {
    float dx = this.x - that.x;
    float dy = this.y - that.y;
    float dz = this.z - that.z;
    return (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
  }

  public float dot(float x, float y, float z) {
    return this.x*x + this.y*y + this.z*z;
  }

  public float dot(LXVector that) {
    return this.x*that.x + this.y*that.y + this.z*that.z;
  }

  public LXVector cross(LXVector that) {
    return this.cross(that.x, that.y, that.z);
  }

  public LXVector cross(float x, float y, float z) {
    float cx = this.y*z - this.z*y;
    float cy = this.z*x - this.x*z;
    float cz = this.x*y - this.y*x;
    return set(cx, cy, cz);
  }

  public LXVector normalize() {
    float m = mag();
    if ((m != 0) && (m != 1)) {
      div(m);
    }
    return this;
  }

  public LXVector limit(float max) {
    float mag2 = magSq();
    if (mag2 > max*max) {
      mult(max/(float) Math.sqrt(mag2));
    }
    return this;
  }

  public LXVector setMag(float mag) {
    normalize();
    return mult(mag);
  }

  public LXVector lerp(LXVector that, float amt) {
    return set(
      LXUtils.lerpf(this.x, that.x, amt),
      LXUtils.lerpf(this.y, that.y, amt),
      LXUtils.lerpf(this.z, that.z, amt)
    );
  }

  public boolean isZero() {
    return
      (this.x == 0) &&
      (this.y == 0) &&
      (this.z == 0);
  }

  /**
   * Rotate in x-y plane
   *
   * @param theta Radians to rotate by
   * @return this
   */
  public LXVector rotate(float theta) {
    float xx = x;
    this.x = (float) (xx*Math.cos(theta) - this.y*Math.sin(theta));
    this.y = (float) (xx*Math.sin(theta) + this.y*Math.cos(theta));
    return this;
  }

  /**
   * Rotate about an arbitrary vector. If you are going to perform this operation
   * on many LXVectors, it is better to use the LXProjection class to avoid
   * a lot of redundant computation.
   *
   * @param theta Angle to rotate by, in radians
   * @param l vector x-value
   * @param m vector y-value
   * @param n vector z-value
   * @return this, for method chaining
   */
  public LXVector rotate(float theta, float l, float m, float n) {
    float ss = l * l + m * m + n * n;
    if (ss != 1) {
      float sr = (float) Math.sqrt(ss);
      l /= sr;
      m /= sr;
      n /= sr;
    }

    float sinv = (float) Math.sin(theta);
    float cosv = (float) Math.cos(theta);
    float a1 = l * l * (1 - cosv) + cosv;
    float a2 = l * m * (1 - cosv) - n * sinv;
    float a3 = l * n * (1 - cosv) + m * sinv;
    float b1 = l * m * (1 - cosv) + n * sinv;
    float b2 = m * m * (1 - cosv) + cosv;
    float b3 = m * n * (1 - cosv) - l * sinv;
    float c1 = l * n * (1 - cosv) - m * sinv;
    float c2 = m * n * (1 - cosv) + l * sinv;
    float c3 = n * n * (1 - cosv) + cosv;

    float xp, yp, zp;
    xp = this.x * a1 + this.y * a2 + this.z * a3;
    yp = this.x * b1 + this.y * b2 + this.z * b3;
    zp = this.x * c1 + this.y * c2 + this.z * c3;
    this.x = xp;
    this.y = yp;
    this.z = zp;

    return this;
  }

  /**
   * Calculates and returns the angle (in radians) between two vectors.
   *
   * @param v1 the x, y, and z components of an LXVector
   * @param v2 the x, y, and z components of an LXVector
   * @return angle between vectors in radians
   */
  static public float angleBetween(LXVector v1, LXVector v2) {

    // We get NaN if we pass in a zero vector which can cause problems
    // Zero seems like a reasonable angle between a (0,0,0) vector and something else
    if (v1.x == 0 && v1.y == 0 && v1.z == 0 ) return 0.0f;
    if (v2.x == 0 && v2.y == 0 && v2.z == 0 ) return 0.0f;

    double dot = v1.x * v2.x + v1.y * v2.y + v1.z * v2.z;
    double v1mag = Math.sqrt(v1.x * v1.x + v1.y * v1.y + v1.z * v1.z);
    double v2mag = Math.sqrt(v2.x * v2.x + v2.y * v2.y + v2.z * v2.z);
    // This should be a number between -1 and 1, since it's "normalized"
    double amt = dot / (v1mag * v2mag);
    // But if it's not due to rounding error, then we need to fix it
    // http://code.google.com/p/processing/issues/detail?id=340
    // Otherwise if outside the range, acos() will return NaN
    // http://www.cppreference.com/wiki/c/math/acos
    if (amt <= -1) {
      return (float) Math.PI;
    } else if (amt >= 1) {
      // http://code.google.com/p/processing/issues/detail?id=435
      return 0;
    }
    return (float) Math.acos(amt);
  }

  @Override
  public String toString() {
    return "[ " + this.x + ", " + this.y + ", " + this.z + " ]";
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof LXVector)) {
      return false;
    }
    LXVector that = (LXVector) o;
    return
      (this.x == that.x) &&
      (this.y == that.y) &&
      (this.z == that.z) &&
      (this.point == that.point) &&
      (this.index == that.index);
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = 31 * result + Float.floatToIntBits(this.x);
    result = 31 * result + Float.floatToIntBits(this.y);
    result = 31 * result + Float.floatToIntBits(this.z);
    return result;
  }
}
