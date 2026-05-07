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

  public Locator(Composition composition, Cursor cursor) {
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
