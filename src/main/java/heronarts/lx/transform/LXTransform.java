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

import java.util.Stack;

/**
 * A transform is a matrix stack, quite similar to the OpenGL implementation.
 * This class can be used to push a point around in 3-d space. The matrix itself
 * is not directly exposed, but the x,y,z values are.
 */
public class LXTransform {

  private final Stack<LXMatrix> matrices = new Stack<LXMatrix>();

  /**
   * Constructs a new transform with the identity matrix
   */
  public LXTransform() {
    this(new LXMatrix());
  }

  /**
   * Constructs a new transform with the given base matrix
   *
   * @param matrix Base matrix
   */
  public LXTransform(LXMatrix matrix) {
    this.matrices.push(matrix);
  }

  /**
   * Returns the current state of the transformation matrix
   *
   * @return Top of the transformation matrix stack
   */
  public LXMatrix getMatrix() {
    return this.matrices.peek();
  }

  /**
   * Returns the size of the Matrix stack
   *
   * @return Number of items in matrix stack
   */
  public int size() {
    return this.matrices.size();
  }

  /**
   * Pushes the matrix stack, future operations can be undone by pop()
   *
   * @return this, for method chaining
   */
  public LXTransform push() {
    this.matrices.push(new LXMatrix(this.matrices.peek()));
    return this;
  }

  /**
   * Pops the matrix stack, to its previous state
   *
   * @return this, for method chaining
   */
  public LXTransform pop() {
    this.matrices.pop();
    return this;
  }

  /**
   * Reset this transform to the given matrix
   * @param matrix Transform matrix
   * @return this
   */
  public LXTransform reset(LXMatrix matrix) {
    this.matrices.clear();
    this.matrices.push(new LXMatrix(matrix));
    return this;
  }

  /**
   * Resets this transform to a single identity matrix
   *
   * @return this
   */
  public LXTransform reset() {
    return reset(new LXMatrix());
  }

  /**
   * Gets the current x, y, z of the transform as a vector
   *
   * @return Vector of current position
   */
  public LXVector vector() {
    LXMatrix m = getMatrix();
    return new LXVector(m.m14, m.m24, m.m34);
  }

  /**
   * Gets the x value of the transform
   *
   * @return x value of transform
   */
  public float x() {
    return getMatrix().m14;
  }

  /**
   * Gets the y value of the transform
   *
   * @return y value of transform
   */
  public float y() {
    return getMatrix().m24;
  }

  /**
   * Gets the z value of the transform
   *
   * @return z value of transform
   */
  public float z() {
    return getMatrix().m34;
  }

  /**
   * Translates the point on the x-axis
   *
   * @param tx x translation
   * @return this
   */
  public LXTransform translateX(float tx) {
    return translate(tx, 0, 0);
  }

  /**
   * Translates the point on the y-axis
   *
   * @param ty y translation
   * @return this
   */
  public LXTransform translateY(float ty) {
    return translate(0, ty, 0);
  }

  /**
   * Translates the point on the z-axis
   *
   * @param tz z translation
   * @return this
   */
  public LXTransform translateZ(float tz) {
    return translate(0, 0, tz);
  }

  /**
   * Translates the point, default of 0 in the z-axis
   *
   * @param tx x translation
   * @param ty y translation
   * @return this
   */
  public LXTransform translate(float tx, float ty) {
    return translate(tx, ty, 0);
  }

  /**
   * Translates the point
   *
   * @param tx x translation
   * @param ty y translation
   * @param tz z translation
   * @return this
   */
  public LXTransform translate(float tx, float ty, float tz) {
    getMatrix().translate(tx, ty, tz);
    return this;
  }

  /**
   * Multiplies the transform by a transformation matrix
   *
   * @param m Matrix
   * @return this
   */
  public LXTransform multiply(LXMatrix m) {
    getMatrix().multiply(m);
    return this;
  }

  /**
   * Scales the transform by the same factor on all axes
   *
   * @param sv Scale factor
   * @return this
   */
  public LXTransform scale(float sv) {
    return scale(sv, sv, sv);
  }

  /**
   * Scales the transform on the X-axis
   *
   * @param sx Scale factor
   * @return this
   */
  public LXTransform scaleX(float sx) {
    return scale(sx, 1, 1);
  }

  /**
   * Scales the transform on the Y-axis
   *
   * @param sy Scale factor
   * @return this
   */
  public LXTransform scaleY(float sy) {
    return scale(1, sy, 1);
  }

  /**
   * Scales the transform on the Z-axis
   *
   * @param sz Scale factor
   * @return this
   */
  public LXTransform scaleZ(float sz) {
    return scale(1, 1, sz);
  }

  /**
   * Scales the transform
   *
   * @param sx Scale factor on X-axis
   * @param sy Scale factor on Y-axis
   * @param sz Scale factor on Z-axis
   * @return this
   */
  public LXTransform scale(float sx, float sy, float sz) {
    getMatrix().scale(sx, sy, sz);
    return this;
  }

  /**
   * Rotates about the x axis
   *
   * @param rx Degrees, in radians
   * @return this, for method chaining
   */
  public LXTransform rotateX(float rx) {
    getMatrix().rotateX(rx);
    return this;
  }

  /**
   * Rotates about the x axis
   *
   * @param rx Degrees, in radians
   * @return this, for method chaining
   */
  public LXTransform rotateX(double rx) {
    return rotateX((float) rx);
  }

  /**
   * Rotates about the y axis
   *
   * @param ry Degrees, in radians
   * @return this, for method chaining
   */
  public LXTransform rotateY(float ry) {
    getMatrix().rotateY(ry);
    return this;
  }

  /**
   * Rotates about the y axis
   *
   * @param ry Degrees, in radians
   * @return this, for method chaining
   */
  public LXTransform rotateY(double ry) {
    return rotateY((float) ry);
  }

  /**
   * Rotates about the z axis
   *
   * @param rz Degrees, in radians
   * @return this, for method chaining
   */
  public LXTransform rotateZ(float rz) {
    getMatrix().rotateZ(rz);
    return this;
  }

  /**
   * Rotates about the z axis
   *
   * @param rz Degrees, in radians
   * @return this, for method chaining
   */
  public LXTransform rotateZ(double rz) {
    return rotateZ((float) rz);
  }


}
