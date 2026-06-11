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
import heronarts.lx.LXComponent;
import heronarts.lx.LXSerializable;

/**
 * A position marker on a composition
 */
public class Locator extends LXComponent {

  // TODO: change to CursorParameter
  public final Cursor cursor;

  public Locator(LXComposition composition, Cursor cursor) {
    super();
    setParent(composition);
    this.cursor = cursor.clone();
  }

  public Locator setCursor(Cursor cursor) {
    this.cursor.set(cursor);
    return this;
  }

  private static final String KEY_CURSOR = "cursor";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_CURSOR, LXSerializable.Utils.toObject(lx, this.cursor));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(KEY_CURSOR)) {
      this.cursor.load(lx, obj.getAsJsonObject(KEY_CURSOR));
    }
  }
}
