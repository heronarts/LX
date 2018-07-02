/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.color.ColorParameter;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXListenableParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.parameter.StringParameter;

/**
 * Utility base class for objects that have parameters.
 */
public abstract class LXComponent implements LXParameterListener, LXSerializable {

  /**
   * Marker interface for components which can have their label changed
   */
  public interface Renamable {}

  private LX lx;

  public final StringParameter label =
    new StringParameter("Label")
    .setDescription("The name of this component");

  public final ColorParameter modulationColor =
    new ColorParameter("Modulation Color", LXColor.hsb(Math.random() * 360, 100, 100))
    .setDescription("The color used to indicate this modulation source");

  public final MutableParameter controlSurfaceSemaphore = (MutableParameter)
    new MutableParameter("Control-Surfaces", 0)
    .setDescription("How many control surfaces are controlling this component");

  private static final int ID_UNASSIGNED = -1;
  static final int ID_ENGINE = 1;

  static class Registry {
    private int idCounter = ID_ENGINE+1;
    private final Map<Integer, LXComponent> components = new HashMap<Integer, LXComponent>();
    private final Map<Integer, LXComponent> projectIdMap = new HashMap<Integer, LXComponent>();

    LXComponent getProjectComponent(int projectId) {
      // Check first in the project ID map, there may be another layer of
      // indirection if the engine components have changed underneath us
      LXComponent component = this.projectIdMap.get(projectId);
      if (component == null) {
        component = this.components.get(projectId);
      }
      return component;
    }

    void resetProject() {
      this.projectIdMap.clear();
    }

    void register(LXComponent component) {
      if (component.id == ID_UNASSIGNED) {
        component.id = this.idCounter++;
      } else if (component.id <= 0) {
        throw new IllegalStateException("Component has bunk ID: " + component.id + " " + component);
      }
      if (this.components.containsKey(component.id)) {
        throw new IllegalStateException("Component id already registered: " + component.id);
      }
      this.components.put(component.id, component);
    }

    int getIdCounter() {
      return this.idCounter;
    }

    void setIdCounter(int idCounter) {
      this.idCounter = idCounter;
    }

    void registerId(LXComponent component, int id) {
      if (id <= 0) {
        throw new IllegalArgumentException("Cannot setId to non-positive value: " + id + " " + component);
      }
      if (component.id == id) {
        return;
      }
      if (this.components.containsKey(id)) {
        // Check for an ID collision, which can happen if the engine
        // has new components, for instance. In that case record in a map
        // what the IDs in the project file refer to.
        this.projectIdMap.put(id, component);
      } else {
        if (component.id > 0) {
          this.components.remove(component.id);
        }
        component.id = id;
        this.components.put(id, component);
      }
    }

    void dispose(LXComponent component) {
      this.components.remove(component.id);
    }
  }

  private LXComponent parent;

  private int id;

  protected LXComponent() {
    this(null, ID_UNASSIGNED);
  }

  protected LXComponent(LX lx) {
    this(lx, ID_UNASSIGNED);
  }

  protected LXComponent(LX lx, String label) {
    this(lx, ID_UNASSIGNED, label);
  }

  protected LXComponent(LX lx, int id) {
    this(lx, id, null);
  }

  protected LXComponent(LX lx, int id, String label) {
    this.lx = lx;
    this.id = id;
    if (id != ID_UNASSIGNED && lx == null) {
      throw new IllegalArgumentException("Cannot specify id on component with no LX instance");
    }
    if (lx != null) {
      lx.componentRegistry.register(this);
    }
    if (label != null) {
      this.label.setValue(label);
    }
    addParameter("label", this.label);
  }

  public LX getLX() {
    return this.lx;
  }

  protected LXComponent addSubcomponent(LXComponent child) {
    child.setParent(this);
    return this;
  }

  protected final LXComponent setParent(LXComponent parent) {
    if (this.parent != null) {
      throw new IllegalStateException("Component already has parent set: " + this + " " + parent);
    }
    if (parent == null) {
      throw new IllegalArgumentException("Cannot set null parent on component: " + this);
    }
    if (parent.lx == null) {
      throw new IllegalStateException("Cannot set component parent with no lx instance: " + this + " " + parent);
    }
    if (parent == this) {
      throw new IllegalStateException("Component cannot be its own parent: " + parent);
    }
    this.parent = parent;
    if (this.lx == null) {
      this.lx = parent.lx;
      this.lx.componentRegistry.register(this);
    }
    return this;
  }

  public final LXComponent getParent() {
    return this.parent;
  }

  public final int getId() {
    return this.id;
  }

  public String getCanonicalPath() {
    return getCanonicalPath(this.lx.engine);
  }

  public String getCanonicalPath(LXComponent root) {
    String path = getLabel();
    if (this.parent != null && this.parent != root) {
      return this.parent.getCanonicalPath(root) + " | " + path;
    }
    return path;
  }

  public static String getCanonicalLabel(LXParameter p, LXComponent root) {
    LXComponent component = p.getComponent();
    if (component != null && component != root) {
      return component.getCanonicalPath(root) + " | " + p.getLabel();
    }
    return p.getLabel();
  }

  public static String getCanonicalLabel(LXParameter p) {
    LXComponent component = p.getComponent();
    if (component != null) {
      return component.getCanonicalPath() + " | " + p.getLabel();
    }
    return p.getLabel();
  }

  public String getLabel() {
    return label.getString();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + getCanonicalPath() + "]";
  }

  public String toString(LXComponent root) {
    return getClass().getSimpleName() + "[" + getCanonicalPath(root) + "]";
  }

  public void dispose() {
    if (this.lx == null) {
      throw new IllegalStateException("LXComponent never had lx reference set: " + this);
    }
    if (this instanceof LXModulationComponent) {
      ((LXModulationComponent) this).getModulation().dispose();
    }
    this.lx.engine.midi.removeMappings(this);
    this.lx.engine.modulation.removeModulations(this);
    for (LXParameter parameter : this.parameters.values()) {
      parameter.dispose();
    }
    this.parameters.clear();
    this.parent = null;
    this.lx.componentRegistry.dispose(this);
  }

  protected final Map<String, LXParameter> parameters = new LinkedHashMap<String, LXParameter>();

  public final LXComponent addParameter(LXParameter parameter) {
    return addParameter(parameter.getLabel(), parameter);
  }

  public LXComponent addParameter(String path, LXParameter parameter) {
    if (this.parameters.containsKey(path)) {
      throw new IllegalStateException("Cannot add parameter at existing path: " + path);
    } else if (this.parameters.containsValue(parameter)) {
      throw new IllegalStateException("Cannot add parameter twice: " + parameter);
    }
    LXComponent component = parameter.getComponent();
    if (component != null) {
      throw new IllegalStateException("Parameter " + parameter + " already owned by " + component);
    }
    parameter.setComponent(this, path);
    this.parameters.put(path, parameter);
    if (parameter instanceof LXListenableParameter) {
      ((LXListenableParameter) parameter).addListener(this);
    }
    return this;
  }

  public final LXComponent addParameters(List<LXParameter> parameters) {
    for (LXParameter parameter : parameters) {
      addParameter(parameter);
    }
    return this;
  }

  public LXComponent removeParameter(String path) {
    LXParameter parameter = this.parameters.get(path);
    if (parameter ==  null) {
      throw new IllegalStateException("No parameter at path: " + path);
    }
    return removeParameter(parameter);
  }

  public LXComponent removeParameter(LXParameter parameter) {
    if (parameter.getComponent() != this) {
      throw new IllegalStateException("Cannot remove parameter not owned by component");
    }
    this.parameters.remove(parameter.getPath());
    parameter.dispose();
    return this;
  }

  public final Collection<LXParameter> getParameters() {
    return this.parameters.values();
  }

  public final LXParameter getParameter(String path) {
    return this.parameters.get(path);
  }

  /**
   * Subclasses are free to override this, but in case they don't care a default
   * implementation is provided.
   */
  public void onParameterChanged(LXParameter parameter) {}

  protected LXComponent copyParameters(LXComponent that) {
    if (getClass() != that.getClass()) {
      throw new IllegalArgumentException("Cannot copy parameters from different class: " + that.getClass() + " -> " + getClass());
    }
    for (Map.Entry<String, LXParameter> entry: this.parameters.entrySet()) {
      LXParameter thisParameter = entry.getValue();
      LXParameter thatParameter = that.getParameter(entry.getKey());
      if (thisParameter instanceof StringParameter) {
        ((StringParameter) thisParameter).setValue(((StringParameter) thatParameter).getString());
      } else if (thisParameter instanceof ColorParameter) {
        ((ColorParameter) thisParameter).setColor(((ColorParameter) thatParameter).getColor());
      } else if (thisParameter instanceof CompoundParameter) {
        thisParameter.setValue(((CompoundParameter) thatParameter).getBaseValue());
      } else {
        thisParameter.setValue(thatParameter.getValue());
      }
    }
    return this;
  }

  protected final static String KEY_ID = "id";
  protected final static String KEY_CLASS = "class";
  protected final static String KEY_MODULATION_COLOR = "modulationColor";
  private final static String KEY_PARAMETERS = "parameters";
  public static final String KEY_COMPONENT_ID = "componentId";
  public static final String KEY_PARAMETER_PATH = "parameterPath";

  @Override
  public void save(LX lx, JsonObject obj) {
    JsonObject parameters = new JsonObject();
    for (String path : this.parameters.keySet()) {
      LXParameter parameter = this.parameters.get(path);
      if (parameter instanceof StringParameter) {
        parameters.addProperty(path, ((StringParameter) parameter).getString());
      } else if (parameter instanceof BooleanParameter) {
        parameters.addProperty(path, ((BooleanParameter) parameter).isOn());
      } else if (parameter instanceof DiscreteParameter) {
        parameters.addProperty(path, ((DiscreteParameter) parameter).getValuei());
      } else if (parameter instanceof ColorParameter) {
        // Do nothing, see ColorParameter.setComponent which adds its sub-parameters
      } else if (parameter instanceof CompoundParameter) {
        parameters.addProperty(path, ((CompoundParameter) parameter).getBaseValue());
      } else {
        parameters.addProperty(path, parameter.getValue());
      }
    }

    obj.addProperty(KEY_ID, this.id);
    obj.addProperty(KEY_CLASS, getClass().getName());
    obj.addProperty(KEY_MODULATION_COLOR, this.modulationColor.getColor());
    obj.add(KEY_PARAMETERS, parameters);
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    if (obj.has(KEY_ID)) {
      lx.componentRegistry.registerId(this, obj.get(KEY_ID).getAsInt());
    }
    if (obj.has(KEY_MODULATION_COLOR)) {
      this.modulationColor.setColor(obj.get(KEY_MODULATION_COLOR).getAsInt());
    }
    if (obj.has(KEY_PARAMETERS)) {
      JsonObject parameters = obj.getAsJsonObject(KEY_PARAMETERS);
      for (String path : this.parameters.keySet()) {
        LXParameter parameter = this.parameters.get(path);
        if (parameter == this.label && !(this instanceof LXComponent.Renamable)) {
          continue;
        }
        if (parameters.has(path)) {
          JsonElement value = parameters.get(path);
          if (parameter instanceof StringParameter) {
            ((StringParameter) parameter).setValue(value.getAsString());
          } else if (parameter instanceof BooleanParameter) {
            ((BooleanParameter) parameter).setValue(value.getAsBoolean());
          } else if (parameter instanceof DiscreteParameter) {
            parameter.setValue(value.getAsInt());
          } else if (parameter instanceof ColorParameter) {
            // Do nothing, it's stored in hue/sat/bright
          } else if (parameter instanceof CompoundParameter) {
            parameter.setValue(value.getAsDouble());
          } else {
            parameter.setValue(value.getAsDouble());
          }
        }
      }
    }

  }

}
