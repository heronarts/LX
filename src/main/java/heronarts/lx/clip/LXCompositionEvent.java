package heronarts.lx.clip;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXSerializable;

/**
 * Abstract base for composition events that have a duration (start + length + end),
 * as opposed to point events like {@link ParameterClipEvent}.
 */
public abstract class LXCompositionEvent<T extends LXCompositionEvent<T>> extends LXClipEvent<T> {

  // TODO: convert length & end to CursorParameters
  public final Cursor length = new Cursor();
  public final Cursor end = new Cursor();

  LXCompositionEvent(LXClipLane<T> lane) {
    super(lane);
  }

  LXCompositionEvent(LXClipLane<T> lane, Cursor cursor) {
    super(lane, cursor);
  }

  protected Cursor.Operator CursorOp() {
    return this.lane.CursorOp();
  }

  // Length

  @Override
  LXClipEvent<T> setCursor(Cursor cursor) {
    super.setCursor(cursor);
    refreshEnd();
    return this;
  }

  protected void setLength(Cursor length) {
    this.length.set(length);
    refreshEnd();
  }

  /**
   * Recalculate end cursor as start cursor plus length
   */
  protected void refreshEnd() {
    this.end.set(this.cursor.add(this.length));
  }

  /**
   * Move event start without moving the end.
   * Adjusts length to keep end fixed.
   * Subclasses may override for additional behavior (e.g. playback offset, loop wrapping).
   *
   * @param start new value for the start cursor
   */
  public void setEventStart(Cursor start) {
    final Cursor.Operator c = CursorOp();
    c.constrain(start, Cursor.ZERO, this.end.subtract(Cursor.MIN_LOOP));
    Cursor newLength = this.end.subtract(start);
    setCursor(start);
    setLength(newLength);
  }

  /**
   * Move event end without moving the start.
   * Subclasses may override for additional constraints (e.g. original length, loop behavior).
   *
   * @param endCursor new value for the end cursor
   */
  public void setEventEnd(Cursor endCursor) {
    final Cursor.Operator c = CursorOp();
    if (c.isBeforeOrEqual(endCursor, this.cursor.add(Cursor.MIN_LOOP))) {
      endCursor = this.cursor.add(Cursor.MIN_LOOP);
    }
    setLength(endCursor.subtract(this.cursor));
  }

  // Serialization

  private static final String KEY_LENGTH = "length";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_LENGTH, LXSerializable.Utils.toObject(lx, this.length));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(KEY_LENGTH)) {
      this.length.load(lx, obj.getAsJsonObject(KEY_LENGTH));
    }
    refreshEnd();
  }

}
