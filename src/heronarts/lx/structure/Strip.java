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

public class Strip extends LXFixture {

  public final DiscreteParameter numPoints = (DiscreteParameter)
    new DiscreteParameter("Num", 30, 1, 4097)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("Number of points in the strip");

  public final BoundedParameter spacing =
    new BoundedParameter("Spacing", 10, 0, 1000000)
    .setDescription("Spacing between points in the strip");

  public Strip(LX lx) {
    super(lx);
    addGeometryParameter("numPoints", this.numPoints);
    addGeometryParameter("spacing", this.spacing);
  }

  @Override
  protected void generatePoints(LXTransform transform) {
    int numPoints = this.numPoints.getValuei();
    float spacing = this.spacing.getValuef();
    for (int i = 0; i < numPoints; ++i) {
      addPoint(transform);
      transform.translate(spacing, 0);
    }
  }

}
