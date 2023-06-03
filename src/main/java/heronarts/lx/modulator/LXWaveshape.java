/**
 * Copyright 2017- Mark C. Slee, Heron Arts LLC
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

import heronarts.lx.LX;

/**
 * Waveshapes compute a function in the range 0-1 over a basis 0-1.
 */
public interface LXWaveshape {

  public double compute(double basis);
  public double invert(double value, double basisHint);

  public static LXWaveshape SIN = new LXWaveshape() {
    @Override
    public double compute(double basis) {
      return .5 * (1 + Math.sin(basis * LX.TWO_PI - LX.HALF_PI));
    }

    @Override
    public double invert(double value, double basisHint) {
      double sinValue = -1 + 2. * value;
      double angle = Math.asin(sinValue);
      if (basisHint > 0.5) {
        angle = Math.PI - angle;
      }
      return (angle + LX.HALF_PI) / LX.TWO_PI;
    }

    @Override
    public String toString() {
      return "Sine";
    }
  };

  public static LXWaveshape TRI = new LXWaveshape() {
    @Override
    public double compute(double basis) {
      return (basis < 0.5) ? (2. * basis) : (1. - 2. * (basis-0.5));
    }

    @Override
    public double invert(double value, double basisHint) {
      return (basisHint < 0.5) ? (value / 2.) : (1. - (value / 2.));
    }

    @Override
    public String toString() {
      return "Triangle";
    }
  };

  public static LXWaveshape UP = new LXWaveshape() {

    @Override
    public double compute(double basis) {
      return basis;
    }

    @Override
    public double invert(double value, double basisHint) {
      return value;
    }

    @Override
    public String toString() {
      return "Up";
    }
  };

  public static LXWaveshape DOWN = new LXWaveshape() {

    @Override
    public double compute(double basis) {
      return 1. - basis;
    }

    @Override
    public double invert(double value, double basisHint) {
      return 1. - value;
    }

    @Override
    public String toString() {
      return "Down";
    }
  };

  public static LXWaveshape SQUARE = new LXWaveshape() {

    @Override
    public double compute(double basis) {
      return (basis < 0.5) ? 0 : 1;
    }

    @Override
    public double invert(double value, double basisHint) {
      return (value == 0) ? 0 : 0.5;
    }

    @Override
    public String toString() {
      return "Square";
    }
  };


}
