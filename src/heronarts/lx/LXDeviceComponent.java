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

package heronarts.lx;

import com.google.gson.JsonObject;

import heronarts.lx.osc.LXOscComponent;

/**
 * A component which may have its own scoped user-level modulators. The concrete subclasses
 * of this are Patterns and Effects.
 */
public abstract class LXDeviceComponent extends LXLayeredComponent implements LXModulationComponent, LXOscComponent {

  public final LXModulationEngine modulation;

  protected LXDeviceComponent(LX lx) {
    super(lx);
    this.modulation = new LXModulationEngine(lx, this);
  }

  @Override
  public void loop(double deltaMs) {
    super.loop(deltaMs);
    this.modulation.loop(deltaMs);
  }

  public LXModulationEngine getModulation() {
    return this.modulation;
  }

  private static final String KEY_MODULATION = "modulation";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_MODULATION, LXSerializable.Utils.toObject(lx, this.modulation));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(KEY_MODULATION)) {
      this.modulation.load(lx,  obj.getAsJsonObject(KEY_MODULATION));
    }
  }

}
