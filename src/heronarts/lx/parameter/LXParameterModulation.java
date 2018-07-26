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

package heronarts.lx.parameter;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXModulationEngine;
import heronarts.lx.color.ColorParameter;

public abstract class LXParameterModulation extends LXComponent {

  private final LXParameter source;
  private final LXParameter target;

  public final ColorParameter color;

  // Hack so that Processing IDE can access it...
  public final ColorParameter clr;

  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", true)
    .setDescription("Whether this modulation is enabled");

  protected LXParameterModulation(LXParameter source, LXParameter target) {
    if (source == null) {
      throw new IllegalArgumentException("LXParameterModulation source may not be null");
    }
    if (target == null) {
      throw new IllegalArgumentException("LXParameterdModulation target may not be null");
    }
    this.source = source;
    this.target = target;
    LXComponent component = source.getComponent();
    if (component instanceof LXModulationEngine) {
      this.color = ((LXComponent) source).modulationColor;
    } else {
      this.color = component.modulationColor;
    }
    this.clr = this.color;
    addParameter("enabled", this.enabled);
  }

  @Override
  public String getLabel() {
    return this.source.getLabel() + " > " + this.target.getLabel();
  }

  protected static final String KEY_SOURCE = "source";
  protected static final String KEY_TARGET = "target";

  protected static LXParameter getParameter(LX lx, JsonObject obj) {
    if (obj.has(KEY_ID)) {
      return (LXParameter) lx.getProjectComponent(obj.get(KEY_ID).getAsInt());
    }
    LXComponent component = lx.getProjectComponent(obj.get(KEY_COMPONENT_ID).getAsInt());
    String path = obj.get(KEY_PARAMETER_PATH).getAsString();
    return component.getParameter(path);
  }

  @Override
  public void save(LX lx, JsonObject obj) {
    JsonObject sourceObj = new JsonObject();
    if (this.source instanceof LXComponent) {
      LXComponent sourceComponent = (LXComponent) this.source;
      sourceObj.addProperty(KEY_ID, sourceComponent.getId());
    } else {
      sourceObj.addProperty(KEY_COMPONENT_ID, this.source.getComponent().getId());
      sourceObj.addProperty(KEY_PARAMETER_PATH, this.source.getPath());
    }
    obj.add(KEY_SOURCE, sourceObj);
    JsonObject targetObj = new JsonObject();
    targetObj.addProperty(KEY_COMPONENT_ID, this.target.getComponent().getId());
    targetObj.addProperty(KEY_PARAMETER_PATH, this.target.getPath());
    obj.add(KEY_TARGET, targetObj);
    super.save(lx, obj);
  }

}
