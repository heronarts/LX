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
import heronarts.lx.modulator.LXTriggerSource;
import heronarts.lx.modulator.MultiTrig;
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
    checkForCyclesDirect(source, target, candidate);

    // Are we modifying a property of a modulation itself? e.g. modulation depth?
    // Whoa-nellie, better check that we don't make a loop from the source that modulation
    // belongs to, e.g. if candidate is the depth of modulation from an LFO to a knob,
    // we need to check everything that the LFO itself modulates!
    final LXComponent candidateParent = candidate.getParent();
    if (candidateParent instanceof LXParameterModulation parameterModulation) {
      checkForCycles(source, target, parameterModulation.source);
    }

    // What if we're modulating to a trigger source, see if its output trigger is an issue
    if (candidateParent instanceof LXTriggerSource triggerSource) {
      final BooleanParameter trigger = triggerSource.getTriggerSource();
      if (trigger != null) {
        checkForCyclesDependent(source, target, trigger);
      }
    }

    // Special case for multi-trig... n.b. there are other contingencies like this,
    // not a totally fool-proof solution here
    if (candidateParent instanceof MultiTrig multiTrig) {
      checkForCyclesDependent(source, target, multiTrig.out1);
      checkForCyclesDependent(source, target, multiTrig.out2);
      checkForCyclesDependent(source, target, multiTrig.out3);
      checkForCyclesDependent(source, target, multiTrig.out4);
      checkForCyclesDependent(source, target, multiTrig.out5);
    }

    // Next, depth-first-search of all the dependencies of this candidate, if any of them wind up
    // back at source, then we've got issues...
    checkForCyclesDepth(source, target, candidate);

  }

  private static void checkForCyclesDependent(LXParameter source, LXParameter target, LXParameter candidate) throws CircularDependencyException {
    checkForCyclesDirect(source, target, candidate);
    checkForCyclesDepth(source, target, candidate);
  }

  private static void checkForCyclesDirect(LXParameter source, LXParameter target, LXParameter candidate) throws CircularDependencyException {
    if (source == candidate) {
      LX.error("Mapping from " + source.getCanonicalPath() + " to " + target.getCanonicalPath() + " is not allowed because it would create a circular dependency.");
      throw new CircularDependencyException("Mapping from " + source.getLabel() + " to " + target.getLabel() + " is not allowed because it would create a circular dependency.");
    }
  }

  private static void checkForCyclesDepth(LXParameter source, LXParameter target, LXParameter candidate) throws CircularDependencyException {

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
    final List<LXParameter> targets = modulationGraph.get(source);
    targets.remove(target);
    if (targets.isEmpty()) {
      modulationGraph.remove(source);
    }
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
      LX.error("LXParameterModulation.getParameter() failed for path " + obj.get(KEY_PATH).getAsString() + " in scope " + scope.getParent());
    }
    if (obj.has(KEY_ID)) {
      return (LXParameter) lx.getProjectComponent(obj.get(KEY_ID).getAsInt());
    }
    if (obj.has(KEY_COMPONENT_ID)) {
      LXComponent component = lx.getProjectComponent(obj.get(KEY_COMPONENT_ID).getAsInt());
      String path = obj.get(KEY_PARAMETER_PATH).getAsString();
      return component.getParameter(path);
    }
    return null;
  }

  @Override
  public void dispose() {
    unregisterModulation(this.source, this.target);
    super.dispose();
  }

  @Override
  public void save(LX lx, JsonObject obj) {
    JsonObject sourceObj = new JsonObject();
    if (this.source instanceof LXComponent sourceComponent) {
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

  public static JsonObject move(JsonObject obj, LXModulationEngine scope, Map<String, String> pathChanges, LXComponent moved) {
    if ((moved != null) && !moved.isDescendant(scope.getParent())) {
      LX.debug("Modulation cannot be restored, component (" + moved.getCanonicalPath() + ") moved out of modulation scope (" + scope.getCanonicalPath() + ")");
      return null;
    }

    final String prefix = scope.getParent().getCanonicalPath();

    final JsonObject move = obj.deepCopy();
    final JsonObject source = move.getAsJsonObject(KEY_SOURCE);
    final JsonObject target = move.getAsJsonObject(KEY_TARGET);

    String sourcePath = source.get(KEY_PATH).getAsString();
    String targetPath = target.get(KEY_PATH).getAsString();
    boolean checkSource = true;
    boolean checkTarget = true;

    for (Map.Entry<String, String> entry : pathChanges.entrySet()) {
      String fromPath = entry.getKey();
      String toPath = entry.getValue();
      if (prefix != null) {
        fromPath = LXPath.stripPrefix(fromPath, prefix);
        toPath = LXPath.stripPrefix(toPath, prefix);
      }
      if (checkSource && sourcePath.startsWith(fromPath)) {
        sourcePath = toPath + sourcePath.substring(fromPath.length());
        checkSource = false;
      }
      if (checkTarget && targetPath.startsWith(fromPath)) {
        targetPath = toPath + targetPath.substring(fromPath.length());
        checkTarget = false;
      }
    }

    source.addProperty(KEY_PATH, sourcePath);
    target.addProperty(KEY_PATH, targetPath);
    return move;
  }

}
