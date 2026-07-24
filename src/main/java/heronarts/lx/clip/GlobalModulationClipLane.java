/**
 * Copyright 2026- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.clip;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.modulation.LXGlobalModulationEngine;

public class GlobalModulationClipLane extends LXClipLane<NullClipEvent> {

  public final LXComposition composition;

  protected GlobalModulationClipLane(LXComposition composition, LXGlobalModulationEngine engine) {
    super(composition, engine);
    this.composition = composition;
  }

  @Override
  public String getLabel() {
    return "Modulation";
  }

  @Override
  void overdubCursor(Cursor from, Cursor to, boolean inclusive) {}

  @Override
  protected NullClipEvent loadEvent(LX lx, JsonObject eventObj) {
    throw new UnsupportedOperationException("No events on GlobalModulationClipLane");
  }

}
