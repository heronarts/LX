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

import com.google.gson.JsonObject;
import heronarts.lx.LX;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXMasterBus;
import heronarts.lx.parameter.StringParameter;

/**
 * A composition lane representing a mixer channel
 */
public class BusClipLane extends LXClipLane<NullClipEvent> {

  public final LXComposition composition;
  public final LXBus bus;

  BusClipLane(LXComposition composition, LXBus bus) {
    super(composition, bus);
    this.composition = composition;
    this.bus = bus;
  }

  public StringParameter getLabelParameter() {
    return (this.bus instanceof LXMasterBus) ? null : this.bus.label;
  }

  @Override
  public String getLabel() {
    return this.bus.getLabel();
  }

  @Override
  public String getPath() {
    return "lane/" + getIndex();
  }

  @Override
  void overdubCursor(Cursor from, Cursor to, boolean inclusive) {}

  public static final String KEY_BUS_ID = "busId";
  public static final String KEY_BUS = "bus";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_BUS, this.bus.getCanonicalPath());
    obj.addProperty(KEY_BUS_ID, this.bus.getId());
  }

  @Override
  protected NullClipEvent loadEvent(LX lx, JsonObject eventObj) {
    throw new UnsupportedOperationException("No events on BusClipLane");
  }

}
