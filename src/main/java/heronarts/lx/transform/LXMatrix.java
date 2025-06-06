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

import java.nio.FloatBuffer;

import heronarts.lx.LX;
import heronarts.lx.model.LXPoint;
import heronarts.lx.utils.LXUtils;

/**
 * A 4x4 matrix for 3-D transformations
 */
public class LXMatrix {

  private final static float[] IDENTITY = {
    1, 0, 0, 0,
    0, 1, 0, 0,
    0, 0, 1, 0,
    0, 0, 0, 1
  };

  public float
    m11 = 1, m12 = 0, m13 = 0, m14 = 0,
    m21 = 0, m22 = 1, m23 = 0, m24 = 0,
    m31 = 0, m32 = 0, m33 = 1, m34 = 0,
    m41 = 0, m42 = 0, m43 = 0, m44 = 1;

  /**
   * Makes a new identity matrix.
   */
  public LXMatrix() {}

  public LXMatrix(float[] m) {
    set(m);
  }

  public LXMatrix(float m11, float m12, float m13, float m14,
                  float m21, float m22, float m23, float m24,
                  float m31, float m32, float m33, float m34,
                  float m41, float m42, float m43, float m44) {
    this.m11 = m11;
    this.m12 = m12;
    this.m13 = m13;
    this.m14 = m14;
    this.m21 = m21;
    this.m22 = m22;
    this.m23 = m23;
    this.m24 = m24;
    this.m31 = m31;
    this.m32 = m32;
    this.m33 = m33;
    this.m34 = m34;
    this.m41 = m41;
    this.m42 = m42;
    this.m43 = m43;
    this.m44 = m44;
  }

  /**
   * Copies the existing matrix
   *
   * @param that matrix
   */
  public LXMatrix(LXMatrix that) {
    this.m11 = that.m11;
    this.m12 = that.m12;
    this.m13 = that.m13;
    this.m14 = that.m14;
    this.m21 = that.m21;
    this.m22 = that.m22;
    this.m23 = that.m23;
    this.m24 = that.m24;
    this.m31 = that.m31;
    this.m32 = that.m32;
    this.m33 = that.m33;
    this.m34 = that.m34;
    this.m41 = that.m41;
    this.m42 = that.m42;
    this.m43 = that.m43;
    this.m44 = that.m44;
  }

  /**
   * Multiplies the matrix by another matrix
   *
   * @param m Matrix to multiply by
   * @return this
   */
  public LXMatrix multiply(LXMatrix m) {
    return multiply(
      m.m11, m.m12, m.m13, m.m14,
      m.m21, m.m22, m.m23, m.m24,
      m.m31, m.m32, m.m33, m.m34,
      m.m41, m.m42, m.m43, m.m44
    );
  }

  public LXMatrix multiply(float b11, float b12, float b13, float b14,
                           float b21, float b22, float b23, float b24,
                           float b31, float b32, float b33, float b34,
                           float b41, float b42, float b43, float b44) {

    float a11 = m11 * b11 + m12 * b21 + m13 * b31 + m14 * b41;
    float a12 = m11 * b12 + m12 * b22 + m13 * b32 + m14 * b42;
    float a13 = m11 * b13 + m12 * b23 + m13 * b33 + m14 * b43;
    float a14 = m11 * b14 + m12 * b24 + m13 * b34 + m14 * b44;

    float a21 = m21 * b11 + m22 * b21 + m23 * b31 + m24 * b41;
    float a22 = m21 * b12 + m22 * b22 + m23 * b32 + m24 * b42;
    float a23 = m21 * b13 + m22 * b23 + m23 * b33 + m24 * b43;
    float a24 = m21 * b14 + m22 * b24 + m23 * b34 + m24 * b44;

    float a31 = m31 * b11 + m32 * b21 + m33 * b31 + m34 * b41;
    float a32 = m31 * b12 + m32 * b22 + m33 * b32 + m34 * b42;
    float a33 = m31 * b13 + m32 * b23 + m33 * b33 + m34 * b43;
    float a34 = m31 * b14 + m32 * b24 + m33 * b34 + m34 * b44;

    float a41 = m41 * b11 + m42 * b21 + m43 * b31 + m44 * b41;
    float a42 = m41 * b12 + m42 * b22 + m43 * b32 + m44 * b42;
    float a43 = m41 * b13 + m42 * b23 + m43 * b33 + m44 * b43;
    float a44 = m41 * b14 + m42 * b24 + m43 * b34 + m44 * b44;

    this.m11 = a11;
    this.m12 = a12;
    this.m13 = a13;
    this.m14 = a14;
    this.m21 = a21;
    this.m22 = a22;
    this.m23 = a23;
    this.m24 = a24;
    this.m31 = a31;
    this.m32 = a32;
    this.m33 = a33;
    this.m34 = a34;
    this.m41 = a41;
    this.m42 = a42;
    this.m43 = a43;
    this.m44 = a44;

    return this;
  }

  public LXMatrix invert() {
    return setInverse(this);
  }

  public LXMatrix setInverse(LXMatrix that) {
    final float xx = that.m11;
    final float xy = that.m12;
    final float xz = that.m13;
    final float xw = that.m14;
    final float yx = that.m21;
    final float yy = that.m22;
    final float yz = that.m23;
    final float yw = that.m24;
    final float zx = that.m31;
    final float zy = that.m32;
    final float zz = that.m33;
    final float zw = that.m34;
    final float wx = that.m41;
    final float wy = that.m42;
    final float wz = that.m43;
    final float ww = that.m44;

    float determinant = 0f;

    determinant += xx * (yy*(zz*ww - zw*wz) - yz*(zy*ww - zw*wy) + yw*(zy*wz - zz*wy));
    determinant -= xy * (yx*(zz*ww - zw*wz) - yz*(zx*ww - zw*wx) + yw*(zx*wz - zz*wx));
    determinant += xz * (yx*(zy*ww - zw*wy) - yy*(zx*ww - zw*wx) + yw*(zx*wy - zy*wx));
    determinant -= xw * (yx*(zy*wz - zz*wy) - yy*(zx*wz - zz*wx) + yz*(zx*wy - zy*wx));

    final float inverseDeterminant = 1f / determinant;

    return set(
      +(yy*(zz*ww - wz*zw) - yz*(zy*ww - wy*zw) + yw*(zy*wz - wy*zz)) * inverseDeterminant,
      -(xy*(zz*ww - wz*zw) - xz*(zy*ww - wy*zw) + xw*(zy*wz - wy*zz)) * inverseDeterminant,
      +(xy*(yz*ww - wz*yw) - xz*(yy*ww - wy*yw) + xw*(yy*wz - wy*yz)) * inverseDeterminant,
      -(xy*(yz*zw - zz*yw) - xz*(yy*zw - zy*yw) + xw*(yy*zz - zy*yz)) * inverseDeterminant,

      -(yx*(zz*ww - wz*zw) - yz*(zx*ww - wx*zw) + yw*(zx*wz - wx*zz)) * inverseDeterminant,
      +(xx*(zz*ww - wz*zw) - xz*(zx*ww - wx*zw) + xw*(zx*wz - wx*zz)) * inverseDeterminant,
      -(xx*(yz*ww - wz*yw) - xz*(yx*ww - wx*yw) + xw*(yx*wz - wx*yz)) * inverseDeterminant,
      +(xx*(yz*zw - zz*yw) - xz*(yx*zw - zx*yw) + xw*(yx*zz - zx*yz)) * inverseDeterminant,

      +(yx*(zy*ww - wy*zw) - yy*(zx*ww - wx*zw) + yw*(zx*wy - wx*zy)) * inverseDeterminant,
      -(xx*(zy*ww - wy*zw) - xy*(zx*ww - wx*zw) + xw*(zx*wy - wx*zy)) * inverseDeterminant,
      +(xx*(yy*ww - wy*yw) - xy*(yx*ww - wx*yw) + xw*(yx*wy - wx*yy)) * inverseDeterminant,
      -(xx*(yy*zw - zy*yw) - xy*(yx*zw - zx*yw) + xw*(yx*zy - zx*yy)) * inverseDeterminant,

      -(yx*(zy*wz - wy*zz) - yy*(zx*wz - wx*zz) + yz*(zx*wy - wx*zy)) * inverseDeterminant,
      +(xx*(zy*wz - wy*zz) - xy*(zx*wz - wx*zz) + xz*(zx*wy - wx*zy)) * inverseDeterminant,
      -(xx*(yy*wz - wy*yz) - xy*(yx*wz - wx*yz) + xz*(yx*wy - wx*yy)) * inverseDeterminant,
      +(xx*(yy*zz - zy*yz) - xy*(yx*zz - zx*yz) + xz*(yx*zy - zx*yy)) * inverseDeterminant
    );
  }

  public float x() {
    return m14;
  }

  public float y() {
    return m24;
  }

  public float z() {
    return m34;
  }

  /**
   * Returns the x value after the given
   * point is transformed by this matrix.
   *
   * @param p Point
   * @return x value after application of matrix
   */
  public float x(LXPoint p) {
    return
      m11 * p.x +
      m12 * p.y +
      m13 * p.z +
      m14;
  }

  /**
   * Returns the y value after the given
   * point is transformed by this matrix.
   *
   * @param p Point
   * @return y value after application of matrix
   */
  public float y(LXPoint p) {
    return
      m21 * p.x +
      m22 * p.y +
      m23 * p.z +
      m24;
  }

  /**
   * Returns the z value after the given
   * point is transformed by this matrix.
   *
   * @param p Point
   * @return z value after application of matrix
   */
  public float z(LXPoint p) {
    return
      m31 * p.x +
      m32 * p.y +
      m33 * p.z +
      m34;
  }

  /**
   * Returns the normalized x value after the given
   * point is transformed by this matrix.
   *
   * @param p Point
   * @return xn value after application of matrix
   */
  public float xn(LXPoint p) {
    return
      m11 * p.xn +
      m12 * p.yn +
      m13 * p.zn +
      m14;
  }

  /**
   * Returns the normalized y value after the given
   * point is transformed by this matrix.
   *
   * @param p Point
   * @return yn value after application of matrix
   */
  public float yn(LXPoint p) {
    return
      m21 * p.xn +
      m22 * p.yn +
      m23 * p.zn +
      m24;
  }

  /**
   * Returns the normalized z value after the given
   * point is transformed by this matrix.
   *
   * @param p Point
   * @return zn value after application of matrix
   */
  public float zn(LXPoint p) {
    return
      m31 * p.xn +
      m32 * p.yn +
      m33 * p.zn +
      m34;
  }

  public LXMatrix scale(float sv) {
    return scale(sv, sv, sv);
  }

  public LXMatrix scaleX(float sx) {
    return scale(sx, 1, 1);
  }

  public LXMatrix scaleY(float sy) {
    return scale(1, sy, 1);
  }

  public LXMatrix scaleZ(float sz) {
    return scale(1, 1, sz);
  }

  public LXMatrix scale(float sx, float sy, float sz) {
    return multiply(
      sx,  0,  0,  0,
       0, sy,  0,  0,
       0,  0, sz,  0,
       0,  0,  0,  1
    );
  }

  public LXMatrix translateX(float tx) {
    return translate(tx, 0, 0);
  }

  public LXMatrix translateY(float ty) {
    return translate(0, ty, 0);
  }

  public LXMatrix translateZ(float tz) {
    return translate(0, 0, tz);
  }

  public LXMatrix translate(float tx, float ty, float tz) {
    return multiply(
      1,  0,  0, tx,
      0,  1,  0, ty,
      0,  0,  1, tz,
      0,  0,  0,  1
    );
  }

  public LXMatrix rotateX(float rx) {
    return rotateX((double) rx);
  }

  public LXMatrix rotateY(float ry) {
    return rotateY((double) ry);
  }

  public LXMatrix rotateZ(float rz) {
    return rotateZ((double) rz);
  }

  public LXMatrix rotateX(double rx) {
    float cos = LXUtils.cosf(rx);
    float sin = LXUtils.sinf(rx);
    return multiply(
      1,    0,    0,  0,
      0,  cos, -sin,  0,
      0,  sin,  cos,  0,
      0,    0,    0,  1
    );
  }

  public LXMatrix rotateY(double ry) {
    float cos = LXUtils.cosf(ry);
    float sin = LXUtils.sinf(ry);
    return multiply(
       cos,  0, sin,  0,
         0,  1,   0,  0,
      -sin,  0, cos,  0,
         0,  0,   0,  1
    );
  }

  public LXMatrix rotateZ(double rz) {
    float cos = LXUtils.cosf(rz);
    float sin = LXUtils.sinf(rz);
    return multiply(
      cos, -sin,  0,  0,
      sin,  cos,  0,  0,
        0,    0,  1,  0,
        0,    0,  0,  1
    );
  }

  public LXMatrix shearXY(float rz) {
    final float c = 1 / LXUtils.tanf(LX.HALF_PI + rz);
    return multiply(
      1, c, 0, 0,
      0, 1, 0, 0,
      0, 0, 1, 0,
      0, 0, 0, 1);
  }

  public LXMatrix shearYX(float rz) {
    final float c = 1 / LXUtils.tanf(LX.HALF_PI + rz);
    return multiply(
      1, 0, 0, 0,
      c, 1, 0, 0,
      0, 0, 1, 0,
      0, 0, 0, 1);
  }

  public LXMatrix shearYZ(float rx) {
    final float c = 1 / LXUtils.tanf(LX.HALF_PI + rx);
    return multiply(
      1, 0, 0, 0,
      0, 1, c, 0,
      0, 0, 1, 0,
      0, 0, 0, 1);
  }

  public LXMatrix shearZY(float rx) {
    final float c = 1 / LXUtils.tanf(LX.HALF_PI + rx);
    return multiply(
      1, 0, 0, 0,
      0, 1, 0, 0,
      0, c, 1, 0,
      0, 0, 0, 1);
  }

  public LXMatrix shearZX(float ry) {
    final float c = 1 / LXUtils.tanf(LX.HALF_PI + ry);
    return multiply(
      1, 0, 0, 0,
      0, 1, 0, 0,
      c, 0, 1, 0,
      0, 0, 0, 1);
  }

  public LXMatrix shearXZ(float ry) {
    final float c = 1 / LXUtils.tanf(LX.HALF_PI + ry);
    return multiply(
      1, 0, c, 0,
      0, 1, 0, 0,
      0, 0, 1, 0,
      0, 0, 0, 1);
  }

  public LXMatrix set(LXMatrix that) {
    this.m11 = that.m11;
    this.m12 = that.m12;
    this.m13 = that.m13;
    this.m14 = that.m14;
    this.m21 = that.m21;
    this.m22 = that.m22;
    this.m23 = that.m23;
    this.m24 = that.m24;
    this.m31 = that.m31;
    this.m32 = that.m32;
    this.m33 = that.m33;
    this.m34 = that.m34;
    this.m41 = that.m41;
    this.m42 = that.m42;
    this.m43 = that.m43;
    this.m44 = that.m44;
    return this;
  }

  public LXMatrix set(float ... m) {
    if (m.length != 16) {
      throw new IllegalArgumentException("LXMatrix.set() must have 16 values");
    }
    this.m11 = m[0];
    this.m12 = m[1];
    this.m13 = m[2];
    this.m14 = m[3];
    this.m21 = m[4];
    this.m22 = m[5];
    this.m23 = m[6];
    this.m24 = m[7];
    this.m31 = m[8];
    this.m32 = m[9];
    this.m33 = m[10];
    this.m34 = m[11];
    this.m41 = m[12];
    this.m42 = m[13];
    this.m43 = m[14];
    this.m44 = m[15];
    return this;
  }

  /**
   * Resets this matrix to the identity matrix
   *
   * @return this
   */
  public LXMatrix identity() {
    return set(IDENTITY);
  }

  public enum BufferOrder {
    ROW_MAJOR,
    COLUMN_MAJOR
  }

  /**
   * Puts the matrix into a FloatBuffer
   *
   * @param buffer Buffer object
   * @return Populated float buffer
   */
  public FloatBuffer put(FloatBuffer buffer) {
    return put(buffer, BufferOrder.ROW_MAJOR);
  }

  /**
   * Puts the matrix into a FloatBuffer
   *
   * @param buffer Buffer object
   * @param bufferOrder Matrix ordering in buffer
   * @return Populated float buffer
   */
  public FloatBuffer put(FloatBuffer buffer, BufferOrder bufferOrder) {
    switch (bufferOrder) {
      case COLUMN_MAJOR ->
        buffer
        .put(0,  this.m11)
        .put(1,  this.m21)
        .put(2,  this.m31)
        .put(3,  this.m41)
        .put(4,  this.m12)
        .put(5,  this.m22)
        .put(6,  this.m32)
        .put(7,  this.m42)
        .put(8,  this.m13)
        .put(9,  this.m23)
        .put(10, this.m33)
        .put(11, this.m43)
        .put(12, this.m14)
        .put(13, this.m24)
        .put(14, this.m34)
        .put(15, this.m44);

      case ROW_MAJOR ->
        buffer
        .put(0,  this.m11)
        .put(1,  this.m12)
        .put(2,  this.m13)
        .put(3,  this.m14)
        .put(4,  this.m21)
        .put(5,  this.m22)
        .put(6,  this.m23)
        .put(7,  this.m24)
        .put(8,  this.m31)
        .put(9,  this.m32)
        .put(10, this.m33)
        .put(11, this.m34)
        .put(12, this.m41)
        .put(13, this.m42)
        .put(14, this.m43)
        .put(15, this.m44);

    };
    return buffer;
  }

  @Override
  public String toString() {
    return "[" +
      "[" + this.m11 + " " + this.m12 + " " + this.m13 + " " + this.m14 + "]" +
      "[" + this.m21 + " " + this.m22 + " " + this.m23 + " " + this.m24 + "]" +
      "[" + this.m31 + " " + this.m32 + " " + this.m33 + " " + this.m34 + "]" +
      "[" + this.m41 + " " + this.m42 + " " + this.m43 + " " + this.m44 + "]" +
      "]";
  }

}