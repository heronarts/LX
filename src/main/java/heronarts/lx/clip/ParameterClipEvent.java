package heronarts.lx.clip;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.utils.LXUtils;

public class ParameterClipEvent extends LXClipEvent<ParameterClipEvent> {

  public final LXNormalizedParameter parameter;
  private double normalized;

  ParameterClipEvent(ParameterClipLane lane, LXNormalizedParameter parameter) {
    this(lane, parameter, parameter.getBaseNormalized());
  }

  ParameterClipEvent(ParameterClipLane lane, LXNormalizedParameter parameter, double normalized) {
    super(lane, parameter.getParent());
    this.parameter = parameter;
    this.normalized = normalizeEventValue(normalized);
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
    this.parameter.setNormalized(this.normalized);
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
