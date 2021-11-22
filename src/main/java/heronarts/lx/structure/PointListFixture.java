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

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.transform.LXMatrix;
import heronarts.lx.transform.LXVector;

public class PointListFixture extends LXBasicFixture {

  private final List<LXVector> coordinates;

  public PointListFixture(LX lx, List<LXVector> coordinates) {
    super(lx, "Points");
    this.coordinates = new ArrayList<LXVector>(coordinates);
  }

  @Override
  protected void computePointGeometry(LXMatrix transform, List<LXPoint> points) {
    int i = 0;
    for (LXPoint p : points) {
      LXVector c = this.coordinates.get(i++);
      transform.translate(c.x, c.y, c.z);
      p.set(transform);
      transform.translate(-c.x, -c.y, -c.z);
    }
  }

  @Override
  protected int size() {
    return this.coordinates.size();
  }

  @Override
  public String[] getDefaultTags() {
    return new String[] { LXModel.Tag.POINTS };
  }
}
