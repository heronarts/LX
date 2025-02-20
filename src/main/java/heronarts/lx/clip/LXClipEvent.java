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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXSerializable;

public abstract class LXClipEvent<T extends LXClipEvent<?>> implements Comparator<T>, LXSerializable {

  protected final LXClipLane<T> lane;
  protected final LXComponent component;
  public final Cursor cursor;

  LXClipEvent(LXClipLane<T> lane) {
    this(lane, lane.clip.cursor, null);
  }

  LXClipEvent(LXClipLane<T> lane, LXComponent component) {
    this(lane, lane.clip.cursor, component);
  }

  LXClipEvent(LXClipLane<T> lane, Cursor cursor) {
    this(lane, cursor, null);
  }

  LXClipEvent(LXClipLane<T> lane, Cursor cursor, LXComponent component) {
    this.lane = lane;
    this.cursor = cursor.clone();
    this.component = component;
  }

  public Cursor getCursor() {
    return this.cursor;
  }

  LXClipEvent<T> setCursor(Cursor cursor) {
    this.cursor.set(cursor);
    return this;
  }

  @Override
  public int compare(T arg0, T arg1) {
    return this.lane.clip.timeBase.getEnum().operator.compare(arg0.cursor, arg1.cursor);
  }

  public abstract void execute();

  protected static final String KEY_CURSOR = "cursor";

  @Override
  public void load(LX lx, JsonObject obj) {
    if (obj.has(KEY_CURSOR)) {
      JsonElement cursorElem = obj.get(KEY_CURSOR);
      if (cursorElem.isJsonObject()) {
        this.cursor.load(lx, cursorElem.getAsJsonObject());
      } else {
        // Legacy-load... these were from old projects when cursors were only in raw double,
        // we'll need to infer the tempo-values from the clip's reference BPM
        this.cursor.set(this.lane.clip.constructAbsoluteCursor(cursorElem.getAsDouble()));
      }
    }
  }

  @Override
  public void save(LX lx, JsonObject obj) {
    obj.add(KEY_CURSOR, LXSerializable.Utils.toObject(lx, this.cursor));
  }
}
