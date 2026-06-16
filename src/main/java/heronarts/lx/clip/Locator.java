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
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.TriggerParameter;

/**
 * A position marker on a composition
 */
public class Locator extends LXComponent implements LXOscComponent, LXComponent.Renamable {

  private final LXComposition composition;
  public final Cursor.Parameter position;

  private int index;

  public final TriggerParameter launch =
    new TriggerParameter("Launch", this::onLaunch)
    .setDescription("Launches playback from this locator");

  public Locator(LXComposition composition, Cursor cursor) {
    super(composition.getLX(), Integer.toString(composition.locators.size() + 1));
    setParent(this.composition = composition);
    this.position = new Cursor.Parameter(composition, "Position");
    this.position.set(cursor);
    addParameter("position", this.position);
    addParameter("launch", this.launch);
  }

  public Locator setCursor(Cursor cursor) {
    this.position.set(cursor);
    return this;
  }

  private void onLaunch() {
    this.composition.launchAutomationFrom(this.position.cursor);
  }

  // Package-only method for LXSnapshotEngine to update indices
  void setIndex(int index) {
    this.index = index;
  }

  /**
   * Public accessor for the index of this snapshot in the list
   *
   * @return This snapshot's position in the global list
   */
  public int getIndex() {
    return this.index;
  }

  @Override
  public String getPath() {
    return "locator/" + (this.index+1);
  }
}
