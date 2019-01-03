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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXModulationEngine;
import heronarts.lx.color.ColorParameter;

public abstract class LXParameterModulation extends LXComponent {

  public static class CircularDependencyException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    CircularDependencyException(String message) {
      super(message);
    };
  }

  private final LXParameter source;
  private final LXParameter target;

  public final ColorParameter color;

  // Hack so that Processing IDE can access it...
  public final ColorParameter clr;

  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", true)
    .setDescription("Whether this modulation is enabled");

  private static Map<LXParameter, List<LXParameter>> modulationGraph = new HashMap<LXParameter, List<LXParameter>>();

  private static void checkForCycles(LXParameter source, LXParameter target, List<LXParameter> targets) {
    if (targets == null) {
      return;
    }
    // Perform depth-first-search of all the dependencies of each target... if any of them wind up
    // back at source, then we've got issues...
    for (LXParameter target2 : targets) {
      if (target2 == source) {
        throw new CircularDependencyException("Mapping from " + source.getLabel() + " to " + target.getLabel() + " not allowed due to circular dependency.");
      }
      checkForCycles(source, target, modulationGraph.get(target2));
    }
  }

  private static void registerModulation(LXParameter source, LXParameter target) {
    checkForCycles(source, target, modulationGraph.get(target));
    if (!modulationGraph.containsKey(source)) {
      modulationGraph.put(source, new ArrayList<LXParameter>());
    }
    modulationGraph.get(source).add(target);
  }

  private static void unregisterModulation(LXParameter source, LXParameter target) {
    // Note: there may be multiple instances of target, this only removes one
    modulationGraph.get(source).remove(target);
  }

  protected LXParameterModulation(LXParameter source, LXParameter target) {
    if (source == null) {
      throw new IllegalArgumentException("LXParameterModulation source may not be null");
    }
    if (target == null) {
      throw new IllegalArgumentException("LXParameterdModulation target may not be null");
    }
    if (source.getComponent() == null && !(source instanceof LXComponent)) {
      throw new IllegalStateException("May not create parameter modulation from source registered to no component: " + source.toString());
    }
    if (target.getComponent() == null) {
      throw new IllegalStateException("May not create parameter modulation to target registered to no component: " + target.toString());
    }
    registerModulation(source, target);
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

  public LXParameter getTarget() {
    return this.target;
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
  public void dispose() {
    unregisterModulation(this.source, this.target);
    super.dispose();
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
