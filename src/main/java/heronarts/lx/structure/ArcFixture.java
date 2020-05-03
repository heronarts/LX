/**
 * Copyright 2018- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.structure;

import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.transform.LXMatrix;

public class ArcFixture extends LXBasicFixture {

  public enum PositionMode {
    ORIGIN("Origin"),
    CENTER("Center");

    private final String str;

    PositionMode(String str) {
      this.str = str;
    }

    @Override
    public String toString() {
      return this.str;
    }
  }

  public final DiscreteParameter numPoints = (DiscreteParameter)
    new DiscreteParameter("Num", 10, 1, 4097)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("Number of points in the arc");

  public final BoundedParameter radius =
    new BoundedParameter("Radius", 100, 0, 1000000)
    .setDescription("Radius of the arc");

  public final BoundedParameter degrees = (BoundedParameter)
    new BoundedParameter("Degrees", 90, 0, 360)
    .setUnits(LXParameter.Units.DEGREES)
    .setDescription("Number of degrees the arc covers");

  public final EnumParameter<PositionMode> positionMode =
    new EnumParameter<PositionMode>("Mode", PositionMode.ORIGIN)
    .setDescription("Whether the arc is positioned by its starting point or center");

  public ArcFixture(LX lx) {
    super(lx, "Arc");
    addMetricsParameter("numPoints", this.numPoints);
    addGeometryParameter("radius", this.radius);
    addGeometryParameter("degrees", this.degrees);
    addGeometryParameter("positionMode", this.positionMode);
  }

  @Override
  protected void computePointGeometry(LXMatrix transform, List<LXPoint> points) {
    float radius = this.radius.getValuef();
    float degrees = this.degrees.getValuef();
    float rotation = (float) (degrees / (this.points.size() - 1) * Math.PI / 180);
    switch (this.positionMode.getEnum()) {
    case CENTER:
      for (LXPoint p : this.points) {
        transform.translateY(-radius);
        p.set(transform);
        transform.translateY(radius);
        transform.rotateZ(rotation);
      }
      break;
    case ORIGIN:
      for (LXPoint p : this.points) {
        p.set(transform);
        transform.translateY(radius);
        transform.rotateZ(rotation);
        transform.translateY(-radius);
      }
      break;
    }
  }

  @Override
  protected int size() {
    return this.numPoints.getValuei();
  }

  @Override
  protected String[] getModelKeys() {
    return new String[] { LXModel.Key.STRIP, LXModel.Key.ARC};
  }

}

