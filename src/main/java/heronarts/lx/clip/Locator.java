/**
 * Copyright 2025- Justin K. Belcher, Heron Arts LLC
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
 * @author Justin K. Belcher <justin@jkb.studio>
 */

package heronarts.lx.clip;

import heronarts.lx.LXComponent;

/**
 * A position marker on a composition
 */
public class Locator extends LXComponent implements LXComponent.Renamable {

  public final Cursor.Parameter position;

  public Locator(LXComposition composition, Cursor cursor) {
    super(composition.getLX(), Integer.toString(composition.locators.size() + 1));
    setParent(composition);
    this.position = new Cursor.Parameter(composition, "Position");
    this.position.set(cursor);
    addParameter("position", this.position);
  }

  public Locator setCursor(Cursor cursor) {
    this.position.set(cursor);
    return this;
  }
}
