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
import java.util.Collections;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.model.LXPoint;
import heronarts.lx.transform.LXTransform;

public class Group extends LXFixture {

  private final List<LXFixture> mutableFixtures = new ArrayList<LXFixture>();
  public final List<LXFixture> fixtures = Collections.unmodifiableList(this.mutableFixtures);

  public Group(LX lx) {
    super(lx);
  }

  public Group addFixture(LXFixture fixture) {
    if (this.mutableFixtures.contains(fixture)) {
      throw new IllegalStateException("Group may not contain two copies of same fixture");
    }
    this.mutableFixtures.add(fixture);
    regenerate();
    return this;
  }

  public Group removeFixture(LXFixture fixture) {
    if (!this.mutableFixtures.contains(fixture)) {
      throw new IllegalStateException("Group does not contain fixture: " + fixture);
    }
    this.mutableFixtures.remove(fixture);
    regenerate();
    return this;
  }

  @Override
  void regenerate() {
    super.regenerate();
    for (LXFixture fixture : this.fixtures) {
      fixture.setParentTransformMatrix(getTransformMatrix());
      fixture.regenerate();
      for (LXPoint p : fixture.points) {
        addPoint(p);
      }
    }
  }

  @Override
  protected void generatePoints(LXTransform transform) {
    // No need to do anything here!
  }


}
