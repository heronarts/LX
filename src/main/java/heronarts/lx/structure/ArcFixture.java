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

import heronarts.lx.LX;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.transform.LXTransform;

public class ArcFixture extends LXBasicFixture {

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

  public ArcFixture(LX lx) {
    super(lx);
    addMetricsParameter("numPoints", this.numPoints);
    addGeometryParameter("radius", this.radius);
    addGeometryParameter("degrees", this.degrees);
  }

  @Override
  protected void computePointGeometry(LXTransform transform) {
    int numPoints = size();
    float radius = this.radius.getValuef();
    float degrees = this.degrees.getValuef();
    float rotation = (float) (degrees / (numPoints-1) * Math.PI / 180);
    for (LXPoint p : this.points) {
      p.set(transform);
      transform.translate(0, radius, 0);
      transform.rotateZ(rotation);
      transform.translate(0, -radius, 0);
    }
  }

  @Override
  protected int size() {
    return this.numPoints.getValuei();
  }

  @Override
  protected String getModelKey() {
    return LXModel.Key.ARC;
  }

}

