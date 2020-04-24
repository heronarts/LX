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

package heronarts.lx.structure;

/**
 * Interface for classes which may contain LXFixtures. They are to be notified
 * when things change in their child fixtures.
 */
public interface LXFixtureContainer {

  /**
   * The generation of this fixture has changed, its metrics
   * or hierarchy are now different. The container will need to
   * take this into account.
   *
   * @param fixture Fixture that has changed
   */
  public void fixtureGenerationChanged(LXFixture fixture);

  /**
   * The geometry of this fixture has changed, its metrics
   * and hierarchy are consistent but the point locations
   * may have changed.
   *
   * @param fixture Fixture that has changed
   */
  public void fixtureGeometryChanged(LXFixture fixture);

}
