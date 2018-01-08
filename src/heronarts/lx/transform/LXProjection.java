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

import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Class to compute projections of an entire model. These are applied cheaply by
 * using direct manipulation rather than matrix multiplication. No push or pop
 * is available.
 */
public class LXProjection implements Iterable<LXVector> {

  private final LXVector[] vectors;

  private final LXModel model;

  public Iterator<LXVector> iterator() {
    return Arrays.asList(this.vectors).iterator();
  }

  /**
   * Constructs a projection view of the given model
   *
   * @param model Model
   */
  public LXProjection(LXModel model) {
    this.vectors = new LXVector[model.points.length];
    int i = 0;
    for (LXPoint point : model.points) {
      this.vectors[i++] = new LXVector(point);
    }
    this.model = model;
  }

  /**
   * Reset all points in the projection to the model
   *
   * @return this, for method chaining
   */
  public LXProjection reset() {
    int i = 0;
    for (LXPoint point : this.model.points) {
      this.vectors[i].x = point.x;
      this.vectors[i].y = point.y;
      this.vectors[i].z = point.z;
      ++i;
    }
    return this;
  }

  /**
   * Scales the projection
   *
   * @param sx x-factor
   * @param sy y-factor
   * @param sz z-factor
   * @return this, for method chaining
   */
  public LXProjection scale(float sx, float sy, float sz) {
    for (LXVector v : vectors) {
      v.x *= sx;
      v.y *= sy;
      v.z *= sz;
    }
    return this;
  }

  /**
   * Translates the projection
   *
   * @param tx x-translation
   * @param ty y-translation
   * @param tz z-translation
   * @return this, for method chaining
   */
  public LXProjection translate(float tx, float ty, float tz) {
    for (LXVector v : vectors) {
      v.x += tx;
      v.y += ty;
      v.z += tz;
    }
    return this;
  }

  /**
   * Centers the projection, by translating it such that the origin (0, 0, 0)
   * becomes the center of the model
   *
   * @return this, for method chaining
   */
  public LXProjection center() {
    return translate(-this.model.cx, -this.model.cy, -this.model.cz);
  }

  /**
   * Translates the model from its center, so (0, 0, 0) becomes (tx, ty, tz)
   *
   * @param tx x-translation
   * @param ty y-translation
   * @param tz z-translation
   * @return this, for method chaining
   */
  public LXProjection translateCenter(float tx, float ty, float tz) {
    return translate(-this.model.cx + tx, -this.model.cy + ty, -this.model.cz
        + tz);
  }

  /**
   * Reflects the projection about the x-axis
   *
   * @return this, for method chaining
   */
  public LXProjection reflectX() {
    for (LXVector v : this.vectors) {
      v.x = -v.x;
    }
    return this;
  }

  /**
   * Reflects the projection about the y-axis
   *
   * @return this, for method chaining
   */
  public LXProjection reflectY() {
    for (LXVector v : this.vectors) {
      v.y = -v.y;
    }
    return this;
  }

  /**
   * Reflects the projection about the z-axis
   *
   * @return this, for method chaining
   */
  public LXProjection reflectZ() {
    for (LXVector v : this.vectors) {
      v.z = -v.z;
    }
    return this;
  }

  /**
   * Rotates the projection about a vector
   *
   * @param angle Angle to rotate by, in radians
   * @param l vector x-value
   * @param m vector y-value
   * @param n vector z-value
   * @return this, for method chaining
   */
  public LXProjection rotate(float angle, float l, float m, float n) {
    float ss = l * l + m * m + n * n;
    if (ss != 1) {
      float sr = (float) Math.sqrt(ss);
      l /= sr;
      m /= sr;
      n /= sr;
    }

    float sinv = (float) Math.sin(angle);
    float cosv = (float) Math.cos(angle);
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

    for (LXVector v : this.vectors) {
      xp = v.x * a1 + v.y * a2 + v.z * a3;
      yp = v.x * b1 + v.y * b2 + v.z * b3;
      zp = v.x * c1 + v.y * c2 + v.z * c3;
      v.x = xp;
      v.y = yp;
      v.z = zp;
    }

    return this;
  }

  /**
   * Rotate about the x-axis
   *
   * @param angle Angle in radians
   * @return this
   */
  public LXProjection rotateX(float angle) {
    return rotate(angle, 1, 0, 0);
  }

  /**
   * Rotate about the x-axis
   *
   * @param angle Angle in radians
   * @return this
   */
  public LXProjection rotateY(float angle) {
    return rotate(angle, 0, 1, 0);
  }

  /**
   * Rotate about the x-axis
   *
   * @param angle Angle in radians
   * @return this
   */
  public LXProjection rotateZ(float angle) {
    return rotate(angle, 0, 0, 1);
  }
}
