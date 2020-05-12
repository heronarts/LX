/**
 * Copyright 2017- Mark C. Slee, Heron Arts LLC
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

import java.util.Comparator;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXSerializable;

public abstract class LXClipEvent implements Comparator<LXClipEvent>, LXSerializable {

  protected final LXClipLane lane;
  protected final LXComponent component;
  protected double cursor;

  LXClipEvent(LXClipLane lane) {
    this(lane, lane.clip.cursor, null);
  }

  LXClipEvent(LXClipLane lane, LXComponent component) {
    this(lane, lane.clip.cursor, component);
  }

  LXClipEvent(LXClipLane lane, double cursor) {
    this(lane, cursor, null);
  }

  LXClipEvent(LXClipLane lane, double cursor, LXComponent component) {
    this.lane = lane;
    this.cursor = cursor;
    this.component = component;
  }

  public double getCursor() {
    return this.cursor;
  }

  LXClipEvent setCursor(double cursor) {
    this.cursor = cursor;
    return this;
  }

  public double getBasis() {
    return this.cursor / this.lane.clip.length.getValue();
  }

  @Override
  public int compare(LXClipEvent arg0, LXClipEvent arg1) {
    if (arg0.cursor < arg1.cursor) {
      return -1;
    } else if (arg0.cursor > arg1.cursor) {
      return 1;
    }
    return 0;
  }

  public abstract void execute();

  protected static final String KEY_CURSOR = "cursor";

  @Override
  public void load(LX lx, JsonObject obj) {
    if (obj.has(KEY_CURSOR)) {
      this.cursor = obj.get(KEY_CURSOR).getAsDouble();
    }
  }

  @Override
  public void save(LX lx, JsonObject obj) {
    obj.addProperty(KEY_CURSOR, this.cursor);
  }
}
