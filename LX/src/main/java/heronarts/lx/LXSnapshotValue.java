package heronarts.lx;

import com.google.gson.JsonObject;

import heronarts.lx.parameter.LXParameter;

public class LXSnapshotValue extends LXComponent {

  public Boolean isEnabled;

  public double value;

  public LXParameter parameter;

  public LXSnapshotValue(LX lx) {
    super(lx);
  }

  public LXSnapshotValue(LX lx, LXComponent scope, JsonObject obj) {
    super(lx);
    this.isEnabled = obj.get(KEY_ENABLED).getAsBoolean();
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
    obj.addProperty(KEY_ENABLED, this.isEnabled);
    obj.addProperty(KEY_VALUE, this.value);

    JsonObject parameterObj = new JsonObject();
    parameterObj.addProperty(KEY_COMPONENT_ID, this.parameter.getParent().getId());
    parameterObj.addProperty(KEY_PARAMETER_PATH, this.parameter.getPath());
    parameterObj.addProperty(KEY_PATH, LXPath.getCanonicalPath(this.parameter));
    obj.add(KEY_PARAMETER, parameterObj);
  }
}
