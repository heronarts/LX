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

package heronarts.lx.modulation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXPath;
import heronarts.lx.color.DiscreteColorParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXParameter;

public abstract class LXParameterModulation extends LXComponent {

  public static class ModulationException extends Exception {

    private static final long serialVersionUID = 1L;

    protected ModulationException(String message) {
      super(message);
    }
  }

  public static class CircularDependencyException extends ModulationException {

    private static final long serialVersionUID = 1L;

    private CircularDependencyException(String message) {
      super(message);
    };
  }

  public static class InvalidScopeException extends ModulationException {

    private static final long serialVersionUID = 1L;

    private InvalidScopeException(LXModulationEngine scope, LXParameter parameter) {
      super("Parameter " + parameter + " is not in valid scope for modulation engine " + scope.getParent());
    };
  }

  protected int index = -1;

  public final LXModulationEngine scope;
  public final LXParameter source;
  public final LXParameter target;

  public final DiscreteColorParameter color;

  // Hack so that Processing IDE can access it...
  public final DiscreteColorParameter clr;

  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", true)
    .setDescription("Whether this modulation is enabled");

  // Forward-connectivity map from parameter to list of all parameters that it directly modulates
  private static Map<LXParameter, List<LXParameter>> modulationGraph =
    new HashMap<LXParameter, List<LXParameter>>();

  private static void checkForCycles(LXParameter source, LXParameter target, LXParameter candidate) throws CircularDependencyException {
    if (source == candidate) {
      throw new CircularDependencyException("Mapping from " + source.getLabel() + " to " + target.getLabel() + " is not allowed because it would create a circular dependency.");
    }

    // Are we modifying a property of a modulation itself? e.g. modulation depth?
    // Whoa-nellie, better check that we don't make a loop from the source that modulation
    // belongs to, e.g. if candidate is the depth of modulation from an LFO to a knob,
    // we need to check everything that the LFO itself modulates!
    final LXComponent candidateParent = candidate.getParent();
    if (candidateParent instanceof LXParameterModulation) {
      checkForCycles(source, target, ((LXParameterModulation) candidateParent).source);
    }

    // Next, depth-first-search of all the dependencies of this candidate, if any of them wind up
    // back at source, then we've got issues...
    final List<LXParameter> candidates = modulationGraph.get(candidate);
    if (candidates != null) {
      for (LXParameter candidate2 : candidates) {
        checkForCycles(source, target, candidate2);
      }
    }
  }

  private static void registerModulation(LXParameter source, LXParameter target) throws CircularDependencyException {
    checkForCycles(source, target, target);
    if (!modulationGraph.containsKey(source)) {
      modulationGraph.put(source, new ArrayList<LXParameter>());
    }
    modulationGraph.get(source).add(target);
  }

  private static void unregisterModulation(LXParameter source, LXParameter target) {
    // Note: there may be multiple instances of target, this only removes one.
    // That's by design, since we do need to keep a count of the number of mappings from
    // source->target.
    modulationGraph.get(source).remove(target);
  }

  private void checkScope(LXModulationEngine scope, LXParameter parameter) throws InvalidScopeException {
    LXComponent domain = scope.getParent();
    LXComponent component = parameter.getParent();
    while (component != null) {
      if (component == domain) {
        return;
      }
      component = component.getParent();
    }
    throw new InvalidScopeException(scope, parameter);
  }

  protected LXParameterModulation(LXModulationEngine scope, LXParameter source, LXParameter target) throws ModulationException {
    if (source == null) {
      throw new IllegalArgumentException("LXParameterModulation source may not be null");
    }
    if (target == null) {
      throw new IllegalArgumentException("LXParameterModulation target may not be null");
    }
    if (source.getParent() == null && !(source instanceof LXComponent)) {
      throw new IllegalStateException("May not create parameter modulation from source registered to no component: " + source.toString());
    }
    if (target.getParent() == null) {
      throw new IllegalStateException("May not create parameter modulation to target registered to no component: " + target.toString());
    }
    registerModulation(source, target);
    checkScope(scope, source);
    checkScope(scope, target);

    this.scope = scope;
    this.source = source;
    this.target = target;
    this.color = (source instanceof LXComponent) ? ((LXComponent) source).modulationColor : source.getParent().modulationColor;
    this.clr = this.color;
    addParameter("enabled", this.enabled);
  }

  public LXParameterModulation setIndex(int index) {
    this.index = index;
    return this;
  }

  public int getIndex() {
    return this.index;
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

  protected static LXParameter getParameter(LX lx, LXModulationEngine scope, JsonObject obj) {
    if (obj.has(KEY_PATH)) {
      LXParameter parameter = LXPath.getParameter(scope.getParent(), obj.get(KEY_PATH).getAsString());
      if (parameter != null) {
        return parameter;
      }
      LX.error("Failed to locate parameter at " + obj.get(KEY_PATH).getAsString() + " in scope " + scope.getParent());
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
      sourceObj.addProperty(KEY_COMPONENT_ID, this.source.getParent().getId());
      sourceObj.addProperty(KEY_PARAMETER_PATH, this.source.getPath());
    }
    sourceObj.addProperty(KEY_PATH, this.source.getCanonicalPath(this.scope.getParent()));

    obj.add(KEY_SOURCE, sourceObj);
    JsonObject targetObj = new JsonObject();
    targetObj.addProperty(KEY_COMPONENT_ID, this.target.getParent().getId());
    targetObj.addProperty(KEY_PARAMETER_PATH, this.target.getPath());
    targetObj.addProperty(KEY_PATH, this.target.getCanonicalPath(this.scope.getParent()));
    obj.add(KEY_TARGET, targetObj);
    super.save(lx, obj);
  }

}
