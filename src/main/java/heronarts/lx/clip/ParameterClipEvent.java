package heronarts.lx.clip;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.utils.LXUtils;

public class ParameterClipEvent extends LXClipEvent<ParameterClipEvent> {

  public final ParameterClipLane lane;
  private double normalized;

  ParameterClipEvent(ParameterClipLane lane) {
    this(lane, lane.parameter.getBaseNormalized());
  }

  ParameterClipEvent(ParameterClipLane lane, double normalized) {
    super(lane, lane.parameter.getParent());
    this.lane = lane;
    this.normalized = normalizeEventValue(normalized);
  }

  ParameterClipEvent(ParameterClipLane lane, Cursor cursor, double normalized) {
    this(lane, normalized);
    setCursor(cursor);
  }

  private double normalizeEventValue(double normalized) {
    if (this.lane instanceof ParameterClipLane.Boolean) {
      normalized = (normalized > .5f) ? 1 : 0;
    } else {
      normalized = LXUtils.constrain(normalized, 0, 1);
    }
    return normalized;
  }

  boolean _setNormalized(double normalized) {
    normalized = normalizeEventValue(normalized);
    if (this.normalized != normalized) {
      this.normalized = normalized;
      return true;
    }
    return false;
  }

  public ParameterClipEvent setNormalized(double normalized) {
    if (_setNormalized(normalized)) {
      this.lane.onChange.bang();
    }
    return this;
  }

  public double getNormalized() {
    return this.normalized;
  }

  public float getNormalizedf() {
    return (float) this.normalized;
  }

  @Override
  public void execute() {
    this.lane.parameter.setNormalized(this.normalized);
  }

  @Override
  public String toString() {
    return this.cursor.toString() + " -> " + getNormalized();
  }

  protected static final String KEY_NORMALIZED = "normalized";

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(KEY_NORMALIZED)) {
      this.normalized = obj.get(KEY_NORMALIZED).getAsDouble();
    }
  }

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_NORMALIZED, this.normalized);
  }
}
