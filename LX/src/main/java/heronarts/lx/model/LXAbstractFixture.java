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

package heronarts.lx.model;

import java.util.ArrayList;
import java.util.List;

@Deprecated
public abstract class LXAbstractFixture implements LXFixture {
  protected final List<LXPoint> points = new ArrayList<LXPoint>();

  protected LXAbstractFixture() {
  }

  public List<LXPoint> getPoints() {
    return this.points;
  }

  public LXAbstractFixture addPoint(LXPoint point) {
    this.points.add(point);
    return this;
  }

  public LXAbstractFixture addPoints(LXFixture fixture) {
    for (LXPoint point : fixture.getPoints()) {
      this.points.add(point);
    }
    return this;
  }

  public LXAbstractFixture addPoints(LXModel model) {
    for (LXPoint point : model.points) {
      this.points.add(point);
    }
    return this;
  }
}
