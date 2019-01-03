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
import heronarts.lx.transform.LXTransform;

public class StaticModel extends LXFixture {

  private final LXModel model;

  public StaticModel(LX lx, LXModel model) {
    super(lx);
    this.label.setValue(model.getClass().getSimpleName());
    this.model = model;
  }

  @Override
  protected void generatePoints(LXTransform transform) {
    for (LXPoint p : model.points) {
      transform.translate(p.x, p.y, p.z);
      addPoint(transform);
      transform.translate(-p.x, -p.y, -p.z);
    }
  }

}
