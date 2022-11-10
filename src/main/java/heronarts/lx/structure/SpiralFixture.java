/**
 * Copyright 2022- Mark C. Slee, Heron Arts LLC
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
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.transform.LXMatrix;

@LXCategory(LXCategory.CORE)
public class SpiralFixture extends LXBasicFixture {

  public static final int MAX_POINTS = 4096;

  public final DiscreteParameter numPoints =
    new DiscreteParameter("Num", 170, 1, MAX_POINTS + 1)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("Number of points in the spiral");

  public final BoundedParameter numTurns =
    new BoundedParameter("Turns", 10, 0, 100)
    .setDescription("How many turns of the spiral");

  public final BoundedParameter radius =
    new BoundedParameter("Radius", 100, 0, 1000000)
    .setDescription("Radius of the spiral");

  public final BoundedParameter length =
    new BoundedParameter("Length", 800, 0, 1000000)
    .setDescription("Length of the spiral");

  public SpiralFixture(LX lx) {
    super(lx, "Spiral");
    addMetricsParameter("numPoints", this.numPoints);
    addMetricsParameter("numTurns", this.numTurns);
    addGeometryParameter("radius", this.radius);
    addGeometryParameter("length", this.length);
  }

  @Override
  protected void computePointGeometry(LXMatrix transform, List<LXPoint> points) {
    final float radius = this.radius.getValuef();
    final float length = this.length.getValuef();
    final float numTurns = this.numTurns.getValuef();

    final float rotation = numTurns * LX.TWO_PIf / this.points.size();
    final float translate = length / this.points.size();

    for (LXPoint p : this.points) {
      transform.translateY(-radius);
      p.set(transform);
      transform.translateY(radius);
      transform.rotateZ(rotation);
      transform.translateZ(translate);
    }
  }

  @Override
  protected int size() {
    return this.numPoints.getValuei();
  }

  @Override
  protected String[] getDefaultTags() {
    return new String[] { LXModel.Tag.STRIP, LXModel.Tag.SPIRAL};
  }

  @Override
  public void addModelMetaData(Map<String, String> metaData) {
    metaData.put("numPoints", String.valueOf(this.numPoints.getValuei()));
    metaData.put("numTurns", String.valueOf(this.numTurns.getValue()));
    metaData.put("radius", String.valueOf(this.radius.getValue()));
    metaData.put("length", String.valueOf(this.length.getValue()));
  }

}

