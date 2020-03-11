package heronarts.lx.snapshot;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXPath;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXParameter;

public class LXSnapshotValue extends LXComponent {

  public final BooleanParameter isEnabled =
    new BooleanParameter("isEnabled")
    .setDescription("If TRUE value will be applied to parameter when snapshot is loaded.");

  private double value;

  public LXParameter parameter;

  public LXSnapshotValue(LX lx) {
    super(lx);
    addParameter(this.isEnabled);
  }

  public LXSnapshotValue(LX lx, LXParameter parameter, double value) {
    this(lx);
    this.parameter = parameter;
    this.value = value;
}

  public LXSnapshotValue(LX lx, LXComponent scope, JsonObject obj) {
    this(lx);
    this.value = obj.get(KEY_VALUE).getAsDouble();
    this.parameter = getParameter(lx, scope, obj.getAsJsonObject(KEY_PARAMETER));
  }

  public static LXParameter getParameter(LX lx, LXComponent scope, JsonObject obj) {
    if (obj.has(KEY_PATH)) {
      LXParameter parameter = (LXParameter) LXPath.get(lx, obj.get(KEY_PATH).getAsString());
      if (parameter != null) {
        return parameter;
      }
      System.err.println("Failed to locate parameter at " + obj.get(KEY_PATH).getAsString());
    }
    if (obj.has(KEY_ID)) {
      return (LXParameter) lx.getProjectComponent(obj.get(KEY_ID).getAsInt());
    }
    LXComponent component = lx.getProjectComponent(obj.get(KEY_COMPONENT_ID).getAsInt());
    String path = obj.get(KEY_PARAMETER_PATH).getAsString();
    return component.getParameter(path);
  }

  public void applyValue() {
    if (this.parameter != null) {
      this.parameter.setValue(this.value);
    }
  }

  @Override
  public void dispose() {
    this.parameter = null;
    super.dispose();
  }

  protected static final String KEY_ENABLED = "enabled";
  protected static final String KEY_VALUE = "value";
  protected static final String KEY_PARAMETER = "parameter";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_VALUE, this.value);

    JsonObject parameterObj = new JsonObject();
    parameterObj.addProperty(KEY_COMPONENT_ID, this.parameter.getParent().getId());
    parameterObj.addProperty(KEY_PARAMETER_PATH, this.parameter.getPath());
    parameterObj.addProperty(KEY_PATH, LXPath.getCanonicalPath(this.parameter));
    obj.add(KEY_PARAMETER, parameterObj);
  }
}
