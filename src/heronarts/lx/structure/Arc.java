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
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.transform.LXTransform;

public class Arc extends LXFixture {

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

  public Arc(LX lx) {
    super(lx);
    addGeometryParameter("numPoints", this.numPoints);
    addGeometryParameter("radius", this.radius);
    addGeometryParameter("degrees", this.degrees);
  }

  @Override
  protected void generatePoints(LXTransform transform) {
    int numPoints = this.numPoints.getValuei();
    float radius = this.radius.getValuef();
    float degrees = this.degrees.getValuef();
    float rotation = (float) (degrees / (numPoints-1) * Math.PI / 180);
    for (int i = 0; i < numPoints; ++i) {
      addPoint(transform);
      transform.translate(0, radius, 0);
      transform.rotateZ(rotation);
      transform.translate(0, -radius, 0);
    }
  }


}
