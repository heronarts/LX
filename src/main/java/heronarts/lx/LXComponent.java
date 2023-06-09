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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import heronarts.lx.color.ColorParameter;
import heronarts.lx.color.DiscreteColorParameter;
import heronarts.lx.modulation.LXModulationContainer;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.osc.LXOscEngine;
import heronarts.lx.osc.OscArgument;
import heronarts.lx.osc.OscInt;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.parameter.AggregateParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXListenableParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.StringParameter;

/**
 * Core base class for any component in the LX tree. This class supports the
 * generic encapsulation of an object with an abstract path, which may
 * have a variety of parameters. Changes to the parameters can be sent and
 * received via OSC, and the component can also be serialized and loaded
 * from JSON files.
 */
public abstract class LXComponent implements LXPath, LXParameterListener, LXSerializable {

  @Documented
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Hidden {
    String value() default "";
  }

  /**
   * Marker interface for components which can have their label changed. Any LXComponent
   * class that has a user-editable label in the UI must have this marker interface
   * attached to it for those edits to be saved and loaded.
   */
  public interface Renamable {}

  /**
   * A market interface for a Placeholder component that is used when an LXComponent
   * class implementation is missing at runtime.
   */
  public interface Placeholder {
    public String getPlaceholderTypeName();
    public String getPlaceholderClassName();
    public LX.InstantiationException getInstantiationException();
  }

  /**
   * The LX instance that this component belongs to.
   */
  protected LX lx;

  /**
   * The parent component, this may be null if the component is newly created and has
   * not been added to the hierarchy yet.
   */
  private LXComponent parent;

  /**
   * Path of this component relative to its parent.
   */
  private String path;

  /**
   * An ordered map of direct descendants of this component.
   */
  private final LinkedHashMap<String, LXComponent> mutableChildren =
    new LinkedHashMap<String, LXComponent>();

  /**
   * An immutable view of the map of child components.
   */
  public final Map<String, LXComponent> children =
    Collections.unmodifiableMap(this.mutableChildren);

  /**
   * An ordered map of array descendants of this component. Rather than a single
   * component, the keys in this map are each a list of components of the same type.
   */
  private final LinkedHashMap<String, List<? extends LXComponent>> childArrays =
    new LinkedHashMap<String, List<? extends LXComponent>>();

  /**
   * A globally unique identifier for this component. May hold the value
   * @{link #ID_UNASSIGNED} if the component has not been registered with
   * the LX hierarchy yet.
   */
  private int id;

  private String description = null;

  /**
   * The user-facing label of this component. May be editable if this LXComponent
   * class implements the {@link Renamable} interface.
   */
  public final StringParameter label =
    new StringParameter("Label")
    .setDescription("The name of this component");

  /**
   * A color used to identify this component when it or one of its parameters
   * is used as a modulation source.
   */
  public final DiscreteColorParameter modulationColor = (DiscreteColorParameter)
    new DiscreteColorParameter("Modulation Color")
    .setDescription("The color used to indicate this modulation source");

  public final BooleanParameter modulationControlsExpanded =
    new BooleanParameter("Expanded", true)
    .setDescription("Whether the modulation controls are expanded");

  public final BooleanParameter modulationsExpanded =
    new BooleanParameter("Show Modulations", true)
    .setDescription("Whether the modulations are visible");

  // Prefix for internal implementation-only parameters
  private static final String INTERNAL_PREFIX = "internal/";

  // Sentinel value for a component with no id assigned yet
  private static final int ID_UNASSIGNED = -1;

  // Reserved ID for the root LXEngine node
  static final int ID_ENGINE = 1;

  // Internal helper class which manages the registration of globally unique IDs
  static class Registry {

    // Keep a dummy-counter for new ID assignments
    private int idCounter = ID_ENGINE + 1;

    // Flags that keep track of special loading states in which ID-collisions may occur
    boolean projectLoading = false;
    boolean modelImporting = false;
    boolean scheduleLoading = false;

    // Global map of ID to component
    private final Map<Integer, LXComponent> components = new HashMap<Integer, LXComponent>();

    // Utility map that is used to manage ID collisions. If we load an old project file, the
    // IDs that is specifies for its objects may collide with existing IDs due to changes in the
    // core components of the LX framework. In this case, we reassign new IDs to those components
    // loaded from the project file, and keep track of a mapping between the ID from the project
    // file to their new globally unique ID in the LX id-space
    private final Map<Integer, LXComponent> projectIdMap = new HashMap<Integer, LXComponent>();

    /**
     * Retrieves the component with this globally unique id
     *
     * @param componentId Component ID
     * @return Component, or <code>null</code> if none exists
     */
    LXComponent getComponent(int componentId) {
      return this.components.get(componentId);
    }

    /**
     * Gets the component referenced by ID in project file. Note that the component returned
     * may not have this same ID if there was a collision when the project file was loaded.
     * In that case, the correct object that the project file was referring to will be
     * returned.
     *
     * @param projectId Component ID from project file
     * @return Matching component, which may have a different ID now, or <code>null</code> if none exists
     */
    LXComponent getProjectComponent(int projectId) {
      // Check first in the project ID map, there may be another layer of
      // indirection if the engine components have changed underneath us
      LXComponent component = this.projectIdMap.get(projectId);
      if (component == null) {
        component = this.components.get(projectId);
      }
      return component;
    }

    /**
     * Resets the registry when a new project is loaded.
     */
    void resetProject() {
      this.projectIdMap.clear();
    }

    int getIdCounter() {
      return this.idCounter;
    }

    void setIdCounter(int idCounter) {
      this.idCounter = idCounter;
    }

    /**
     * Registers a new component. If it is awaiting ID assignment, one is generated. Error-checking
     * is also performed here if the component has already been registered. It may not be
     * registered a second time.
     *
     * @param component Component to globally register
     */
    private void register(LXComponent component) {
      if (component.id == ID_UNASSIGNED) {
        component.id = this.idCounter++;
      } else if (component.id <= 0) {
        throw new IllegalStateException("Component has illegal non-positive ID: " + component.id + " " + component);
      }
      if (this.components.containsKey(component.id)) {
        throw new IllegalStateException("Component id " + component.id + " already registered: " + component + " to " + this.components.get(component.id));
      }
      this.components.put(component.id, component);
    }

    /**
     * Registers a new fixed ID for the given component. Error-checking is performed in case
     * this ID will create a collision.
     *
     * @param component Component to register
     * @param id Fixed ID to give this component, which may create a collision
     */
    private void registerId(LXComponent component, int id) {
      if (id <= 0) {
        throw new IllegalArgumentException("Cannot registerId to non-positive value: " + id + " " + component);
      }
      // This component already has that ID, nothing to do here
      if (component.id == id) {
        return;
      }
      if (this.components.containsKey(id)) {
        if (this.projectLoading) {
          // Check for an ID collision, which can happen if the engine
          // has new components, for instance. In that case record in a map
          // what the IDs in the project file refer to.
          this.projectIdMap.put(id, component);
        } else if (this.modelImporting || this.scheduleLoading) {
          // We ignore ID assignment collisions from external model files.
          // Sticking with the newly generated ID is fine.
        } else {
          // This can't happen, there should be no reason that we're requesting a component
          // to re-use an existing component ID when we are outside of loading a file
          throw new IllegalStateException("ID collision outside of project/schedule load or model import: " + component + " trying to clobber " + this.components.get(id));
        }
      } else {
        if (component.id > 0) {
          // Does the component already have any ID? If so, remove that from the registry
          this.components.remove(component.id);
        }
        // Update the component's ID and store it in the global map
        component.id = id;
        this.components.put(id, component);

        // If the restored ID was ahead of our counter, we need to bump the counter
        // to avoid causing future collisions
        if (id >= this.idCounter) {
          this.idCounter = id + 1;
        }
      }
    }

    // Get rid of this component
    private void dispose(LXComponent component) {
      this.components.remove(component.id);
    }
  }

  /**
   * Gets the name of a component class, with a suffix removed
   *
   * @param component Component class
   * @param suffix Suffix to remove
   * @return Name of component type
   */
  public static String getComponentName(Class<? extends LXComponent> component, String suffix) {
    LXComponentName annotation = component.getAnnotation(LXComponentName.class);
    if (annotation != null) {
      return annotation.value();
    }
    String simple = component.getSimpleName();
    if (simple.endsWith(suffix)) {
      simple = simple.substring(0, simple.length() - suffix.length());
    }
    return simple;
  }

  /**
   * Gets the name of a component class, automatically removing the suffix of
   * a generic LX superclass, if one is found
   *
   * @param cls Component class
   * @return Name of component class
   */
  public static String getComponentName(Class<? extends LXComponent> cls) {
    LXComponentName annotation = cls.getAnnotation(LXComponentName.class);
    if (annotation != null) {
      return annotation.value();
    }
    String suffix = "";
    Class<? extends LXComponent> generic = cls;
    while (generic != null) {
      if (generic.getSimpleName().startsWith("LX")) {
        suffix = generic.getSimpleName().substring(2);
        break;
      }
      generic = generic.getSuperclass().asSubclass(LXComponent.class);
    }
    return getComponentName(cls, suffix);
  }

  /**
   * Gets the name of an LXComponent object with suffix removed
   *
   * @param component Component instance
   * @param suffix Suffix to remove
   * @return Name of component type
   */
  public static String getComponentName(LXComponent component, String suffix) {
    return getComponentName(component.getClass(), suffix);
  }

  /**
   * Creates a new component with no ID and not part of the LX hierarchy. This
   * should very rarely be used, except when creating components that will have to
   * be dynamically loaded later, or may never be part of the hierarchy.
   */
  protected LXComponent() {
    this(null, ID_UNASSIGNED);
  }

  /**
   * Creates a new component as part of an LX hierarchy. An ID will be automatically
   * assigned to this component.
   *
   * @param lx LX instance
   */
  protected LXComponent(LX lx) {
    this(lx, ID_UNASSIGNED);
  }

  /**
   * Creates a new component as part of the LX hierarchy. An ID will be automatically
   * assigned. This component's label will be set to the provided initial value.
   *
   * @param lx LX instance
   * @param label Initial label for the component
   */
  protected LXComponent(LX lx, String label) {
    this(lx, ID_UNASSIGNED, label);
  }

  /**
   * Creates a new component as part of the LX hierarchy. It will be explicitly registered
   * with the the given pre-existing ID.
   *
   * @param lx LX instance
   * @param id Fixed ID value to give this component
   */
  protected LXComponent(LX lx, int id) {
    this(lx, id, null);
  }

  /**
   * Creates a new component as part of the LX hierarchy. It will be explicitly registered
   * with the the given pre-existing ID and a given label value.
   *
   * @param lx LX instance
   * @param id Fixed ID value to give this component
   * @param label Initial label for the component
   */
  protected LXComponent(LX lx, int id, String label) {
    this.lx = lx;
    this.id = id;
    if ((id != ID_UNASSIGNED) && (lx == null)) {
      throw new IllegalArgumentException("Cannot specify id on component with no LX instance");
    }
    if (lx != null) {
      lx.componentRegistry.register(this);
    }
    this.label.setValue((label != null) ? label : LXComponent.getComponentName(getClass()));
    addParameter("label", this.label);
    addInternalParameter("modulationColor", this.modulationColor);
    addInternalParameter("modulationControlsExpanded", this.modulationControlsExpanded);
    addInternalParameter("modulationsExpanded", this.modulationsExpanded);
  }

  /**
   * Accessor to the LX instance that this component is part of. Note that this may
   * be <code>null</code> if this is a dynamic component that has not been registered yet.
   *
   * @return LX instance, or <code>null</code> if unregistered
   */
  public LX getLX() {
    return this.lx;
  }

  public static String getCategory(Class<? extends LXComponent> clazz) {
    LXCategory annotation = clazz.getAnnotation(LXCategory.class);
    return (annotation != null) ? annotation.value() : LXCategory.OTHER;
  }

  // Helper to check that a path is valid, no collisions allowed between parameters,
  // children, and child arrays, otherwise we'll have OSC conflicts.
  private void _checkPath(String path, String type) {
    if (this.parameters.containsKey(path)) {
      throw new IllegalStateException("Cannot add " + type + " at path " + path
        + ", parameter already exists");
    }
    if (this.mutableChildren.containsKey(path)) {
      throw new IllegalStateException(
        "Cannot add " + type + " at path " + path + ", child already exists");
    }
    if (this.childArrays.containsKey(path)) {
      throw new IllegalStateException(
        "Cannot add " + type + " at path " + path + ", array already exists");
    }
  }

  /**
   * Registers an array of subcomponents with this component. They will be accessible
   * via path and OSC queries at the given path from this component.
   *
   * @param path Path to register the array at
   * @param childArray Child objects
   * @return this
   */
  protected LXComponent addArray(String path, List<? extends LXComponent> childArray) {
    if (childArray == null) {
      throw new IllegalStateException("Cannot add null LXComponent.addArray()");
    }
    _checkPath(path, "array");
    this.childArrays.put(path, childArray);
    return this;
  }

  /**
   * Registers a child component with this component. It will be accessible via
   * path and OSC queries relative to this component.
   *
   * @param path Path relative to this
   * @param child The child component
   * @return this
   */
  protected LXComponent addChild(String path, LXComponent child) {
    if (child == null) {
      throw new IllegalStateException("Cannot add null LXComponent.addChild()");
    }
    _checkPath(path, "child");
    child.setParent(this, path);
    this.mutableChildren.put(path, child);
    return this;
  }

  /**
   * Accesses the child component object at a given path. This method can only be used
   * for direct descendants. It will not return elements out of a child array.
   *
   * @param path Child path
   * @return Child object if exists, or <code>null</code> if not found
   */
  public LXComponent getChild(String path) {
    return this.mutableChildren.get(path);
  }

  /**
   * Registers this component with a parent object in the hierarchy.
   * If this component has not been registered an ID with LX yet but
   * the parent object is,
   *
   * @param parent Parent component
   * @return this
   */
  protected final LXComponent setParent(LXComponent parent) {
    return setParent(parent, null);
  }

  // Internal helper for parent assignment with error-checks
  private final LXComponent setParent(LXComponent parent, String path) {
    if (this.parent != null) {
      throw new IllegalStateException("Cannot LXComponent.setParent() when parent already set: " + this + " " + this.parent + " " + parent);
    }
    if (parent == null) {
      throw new IllegalArgumentException("Cannot LXComponent.setParent(null): " + this);
    }
    if (parent.lx == null) {
      throw new IllegalStateException("Cannot LXComponent.setParent() with unregistered parent: " + this + " " + parent);
    }
    if (parent == this) {
      throw new IllegalStateException("LXComponent cannot be its own parent: " + parent);
    }
    this.parent = parent;
    this.path = path;

    if (this.lx == null) {
      this.lx = parent.lx;
      this.lx.componentRegistry.register(this);
    }
    return this;
  }

  /**
   * Accessor for the parent component. May be <code>null</code> if this component has
   * not been registered with any parent.
   *
   * @return Parent component, or <code>null</code> if none exists.
   */
  public final LXComponent getParent() {
    return this.parent;
  }

  /**
   * Accessor for the global id of this component. May be @{link #ID_UNASSIGNED} if this
   * has not been assigned yet.
   *
   * @return Global ID or {@link #ID_UNASSIGNED}
   */
  public final int getId() {
    return this.id;
  }

  /**
   * Accessor for this component's OSC path relative to its parent. This by default
   * is no different from {@link #getPath()}, but certain subclasses may modify this
   * to support different types of OSC paths that aren't required to match the LX
   * hierarchy.
   *
   * @return Path that this object can be accessed via OSC
   */
  public String getOscPath() {
    return this.path;
  }

  /**
   * Determines whether the given LX object is contained by this
   * parent, at any depth in the tree of child components and parameters.
   *
   * @param that Potential child object
   * @return <code>true</code> if a child component or parameter, <code>false</code> otherwise
   */
  public final boolean contains(LXPath that) {
    while (that != null) {
      if (that == this) {
        return true;
      }
      that = that.getParent();
    }
    return false;
  }

  /**
   * Gets the OSC-friendly label for this object
   *
   * @return This component's label, sanitized to be OSC-compatible
   */
  public String getOscLabel() {
    return getLabel().trim().replaceAll("[\\s#*,/\\\\?\\[\\]{}]+", "-");
  }

  /**
   * Gets the OSC address for this object
   *
   * @return Full OSC address for this component
   */
  public String getOscAddress() {
    return getCanonicalPath();
  }

  private static final String PATH_OSC_QUERY = "osc-query";

  /**
   * Handles an OSC message sent to this component. By default this method handles
   * registered components and parameters, but subclasses may override this method
   * to handle different types of OSC messages.
   *
   * @param message Full OSC message object
   * @param parts The OSC address pattern, broken into an array of parts
   * @param index Which index into the parts array corresponds to this component's children
   * @return <code>true</code> if the OSC message was handled and should be considered consumed, <code>false</code> otherwise
   */
  public boolean handleOscMessage(OscMessage message, String[] parts, int index) {
    String path = parts[index];

    // The special OSC query message prompts us to send out the values of all
    // our child objects via OSC
    if (path.equals(PATH_OSC_QUERY)) {
      oscQuery();
      return true;
    }

    // First check for a child component at the given path
    LXComponent child = getChild(path);
    if (child != null) {
      return child.handleOscMessage(message, parts, index + 1);
    }

    // Then check for a child array
    List<? extends LXComponent> array = this.childArrays.get(path);
    if (array != null) {
      String arrayId = parts[index+1];
      if (arrayId.matches("\\d+")) {
        int arrayIndex = Integer.parseInt(arrayId) - 1;
        if (arrayIndex >= 0 && arrayIndex < array.size()) {
          return array.get(arrayIndex).handleOscMessage(message, parts, index + 2);
        }
      }
      LXOscEngine.error("Invalid array index in OSC message: " + parts[index+1] + " (" + message + ")");
      return false;
    }

    // Next check for a parameter at the given path
    LXParameter parameter = getParameter(path);
    if (parameter == null) {
      LXOscEngine.error("Component " + this + " did not find anything at OSC path: " + path + " (" + message + ")");
      return false;
    }

    return handleOscParameter(message, parameter, parts, index);
  }

  private boolean handleOscParameter(OscMessage message, LXParameter parameter, String[] parts, int index) {
    // Handle OSC messages for different parameter types
    if (parameter instanceof BooleanParameter) {
      ((BooleanParameter) parameter).setValue(message.getBoolean());
    } else if (parameter instanceof StringParameter) {
      ((StringParameter) parameter).setValue(message.getString());
    } else if (parameter instanceof AggregateParameter) {
      if (parts.length >= index + 1) {
        LXParameter subparameter = ((AggregateParameter) parameter).subparameters.get(parts[index+1]);
        if (subparameter != null) {
          return handleOscParameter(message, subparameter, parts, index+1);
        } else {
          LXOscEngine.error("Component " + this + " did not find anything at OSC path: " + path + " (" + message + ")");
          return false;
        }
      } else {
        ((ColorParameter) parameter).setColor(message.getInt());
      }
    } else if (parameter instanceof DiscreteParameter) {
      OscArgument arg = message.get();
      if ((arg instanceof OscInt) || ((DiscreteParameter) parameter).getOscMode() == LXNormalizedParameter.OscMode.ABSOLUTE) {
        parameter.setValue(arg.toInt());
      } else {
        ((DiscreteParameter) parameter).setNormalized(arg.toFloat());
      }
    } else if (parameter instanceof LXNormalizedParameter) {
      LXNormalizedParameter normalizedParameter = (LXNormalizedParameter) parameter;
      if (normalizedParameter.getOscMode() == LXNormalizedParameter.OscMode.ABSOLUTE) {
        normalizedParameter.setValue(message.getFloat());
      } else {
        normalizedParameter.setNormalized(message.getFloat());
      }
    } else {
      parameter.setValue(message.getFloat());
    }
    return true;
  }

  // Send out the values of all our children by OSC
  private void oscQuery() {
    if (this instanceof LXOscComponent) {
      for (LXParameter p : this.parameters.values()) {
        this.lx.engine.osc.sendParameter(p);
      }
      final Collection<LXComponent> children = this.children.values();
      for (LXComponent child : children) {
        child.oscQuery();
      }
      for (List<? extends LXComponent> array : this.childArrays.values()) {
        for (LXComponent component : array) {
          if ((component != null) && !children.contains(component)) {
            component.oscQuery();
          }
        }
      }
    }
  }

  public JsonObject toOscQuery() {
    JsonObject obj = new JsonObject();
    obj.addProperty("FULL_PATH", getCanonicalPath());
    String description = getDescription();
    if (description == null) {
      description = getClass().getName();
    }
    obj.addProperty("DESCRIPTION", description);
    JsonObject contents = new JsonObject();
    for (Map.Entry<String, LXParameter> parameterEntry : this.parameters.entrySet()) {
      LXParameter parameter = parameterEntry.getValue();
      if ((this.label == parameter) && !(this instanceof Renamable)) {
        continue;
      }
      contents.add(parameterEntry.getKey(), toOscQuery(parameter));
    }
    for (Map.Entry<String, LXComponent> childEntry : this.mutableChildren.entrySet()) {
      LXComponent child = childEntry.getValue();
      if (child instanceof LXOscComponent) {
        contents.add(childEntry.getKey(), child.toOscQuery());
      }
    }
    for (Map.Entry<String, List<? extends LXComponent>> childArrayEntry : this.childArrays.entrySet()) {
      JsonObject arrObj = new JsonObject();
      arrObj.addProperty("FULL_PATH", getCanonicalPath() + "/" + childArrayEntry.getKey());
      arrObj.addProperty("DESCRIPTION", "Container element");
      JsonObject arrContents = new JsonObject();
      List<? extends LXComponent> childArr = childArrayEntry.getValue();
      for (int i = 0; i < childArr.size(); ++i) {
        LXComponent child = childArr.get(i);
        if (child instanceof LXOscComponent) {
          arrContents.add("" + (i+1), child.toOscQuery());
        }
      }
      arrObj.add("CONTENTS", arrContents);
      contents.add(childArrayEntry.getKey(), arrObj);
    }

    obj.add("CONTENTS", contents);
    return obj;
  }

  public JsonObject toOscQuery(LXParameter parameter) {
    if (!(this instanceof LXOscComponent)) {
      return null;
    }
    JsonObject obj = new JsonObject();
    obj.addProperty("FULL_PATH", parameter.getCanonicalPath());
    obj.addProperty("DESCRIPTION", parameter.getDescription());

    JsonObject range = null;

    // TODO(mcslee): handle aggregate parameter in here
    if (parameter instanceof BooleanParameter) {
      boolean isOn = ((BooleanParameter) parameter).isOn();
      obj.addProperty("VALUE", isOn);
      obj.addProperty("TYPE", isOn ? "T" : "F");
    } else if (parameter instanceof StringParameter) {
      obj.addProperty("VALUE", ((StringParameter)parameter).getString());
      obj.addProperty("TYPE", "s");
    } else if (parameter instanceof ColorParameter) {
      obj.addProperty("VALUE", ((ColorParameter)parameter).getColor());
      obj.addProperty("TYPE", "r");
    } else if (parameter instanceof DiscreteParameter) {
      obj.addProperty("VALUE", ((DiscreteParameter) parameter).getValuei());
      obj.addProperty("TYPE", "i");
      range = new JsonObject();
      range.addProperty("MIN", ((DiscreteParameter) parameter).getMinValue());
      range.addProperty("MAX", ((DiscreteParameter) parameter).getMaxValue());
    } else if (parameter instanceof CompoundParameter) {
      obj.addProperty("VALUE", ((CompoundParameter) parameter).getBaseNormalizedf());
      obj.addProperty("TYPE", "f");
      range = new JsonObject();
      range.addProperty("MIN", 0f);
      range.addProperty("MAX", 1f);
    } else if (parameter instanceof LXNormalizedParameter) {
      obj.addProperty("VALUE", ((LXNormalizedParameter) parameter).getNormalizedf());
      obj.addProperty("TYPE", "f");
      range = new JsonObject();
      range.addProperty("MIN", 0f);
      range.addProperty("MAX", 1f);
    } else {
      obj.addProperty("VALUE", parameter.getValuef());
      obj.addProperty("TYPE", "f");
    }

    if (range != null) {
      JsonArray rangeArr = new JsonArray();
      rangeArr.add(range);
      obj.add("RANGE", rangeArr);
    }

    return obj;
  }

  /**
   * Finds the child parameter or component at the specified path
   *
   * @param parts A path, already broken into parts
   * @param index Index to start looking at
   * @return Child parameter, subcomponent, or subcomponent array member
   */
  final LXPath path(String[] parts, int index) {
    if (index < 0 || index >= parts.length) {
      throw new IllegalArgumentException("Illegal index to path method: " + index + " parts.length=" + parts.length);
    }
    final String key = parts[index];
    LXParameter parameter = this.parameters.get(key);
    if (parameter != null) {
      if (parameter instanceof AggregateParameter) {
        if (index < parts.length - 1) {
          String subparam = parts[index] + "/" + parts[index+1];
          return this.parameters.get(subparam);
        }
      }
      return parameter;
    }
    LXComponent child = this.mutableChildren.get(key);
    if (child != null) {
      if (index == parts.length - 1) {
        return child;
      }
      return child.path(parts, index + 1);
    }
    List<? extends LXComponent> array = this.childArrays.get(key);
    if (array != null) {
      ++index;
      if (index < parts.length) {
        try {
          // NOTE: path indices are 1-indexed because they are used in OSC and shown to
          // the end-user... correct them back to 0-indexing for us computer scientists...
          int arrIndex = Integer.parseInt(parts[index]) - 1;
          if (arrIndex >= 0 && arrIndex < array.size()) {
            child = array.get(arrIndex);
            if (index == parts.length - 1) {
              return child;
            }
            return (child != null) ? child.path(parts, index + 1) : null;
          }
        } catch (NumberFormatException nfx) {
          return null;
        }
      }
    }
    return null;
  }

  /**
   * Accessor for the path of this object. Returns the path this component
   * was registered with. Some subclasses may override this if path structure
   * is dynamic.
   *
   * @return path of this component relative to its parent
   */
  public String getPath() {
    return this.path;
  }

  /**
   * Accessor for the user-facing label of this component. Objects that implement
   * the {@link Renamable} interface may allow the user to change this value.
   *
   * @return Label for this component
   */
  public String getLabel() {
    return this.label.getString();
  }

  public String getDescription() {
    return this.description;
  }

  protected LXComponent setDescription(String description) {
    this.description = description;
    return this;
  }

  /**
   * Returns a useful debug string for the component, indicating the class name
   * along with the ID number and the canonical path
   *
   * @return Debug string identifying this component
   */
  @Override
  public String toString() {
    return getClass().getSimpleName() + "[#" + this.id + "][" + getCanonicalPath() + "]";
  }

  /**
   * Returns a useful debug string for the component, indicating the class name
   * along with the ID number and the canonical path
   *
   * @param root Component to simplify path relative to
   * @return Debug string identifying this component
   */
  public String toString(LXComponent root) {
    return getClass().getSimpleName() + "[#" + this.id + "][" + getCanonicalPath(root) + "]";
  }

  private boolean disposed = false;

  /**
   * Invoked when a component is being removed from the system and will no longer be used at all.
   * This unregisters the component and should free up any resources and parameter listeners.
   * Ideally after this method is called the object should be eligible for garbage collection.
   *
   * Subclasses are generally expected to override this method to handle their particular
   * cleanup work. They should also generally call <code>super.dispose()</code> at the appropriate
   * time to perform the basic cleanup, which may need to happen either before or after cleaning
   * up other objects.
   */
  public void dispose() {
    if (this.lx == null) {
      throw new IllegalStateException("LXComponent cannot dispose(), never had lx reference set: " + this);
    }
    if (this.disposed) {
      throw new IllegalStateException("Cannot dispose LXComponent twice: " + this);
    }
    this.disposed = true;

    // NOTE: we do not dispose of all children or child arrays automatically here. It is better
    // to leave this to explicit subclass implementations. Ordering can matter and child
    // arrays are typically dynamic, not fixed

    // Remove the modulation engine for any component that has one
    if ((this != this.lx.engine) && (this instanceof LXModulationContainer)) {
      ((LXModulationContainer) this).getModulationEngine().dispose();
    }

    // Remove modulations from any containers up the chain
    LXComponent parent = getParent();
    while ((parent != null) && (parent != this.lx.engine)) {
      if (parent instanceof LXModulationContainer) {
        ((LXModulationContainer) parent).getModulationEngine().removeModulations(this);
      }
      parent = parent.getParent();
    }

    // The global midi, modulation, and snapshot engines need to know we're gone
    this.lx.engine.midi.removeMappings(this);
    this.lx.engine.modulation.removeModulations(this);
    this.lx.engine.snapshots.removeSnapshotViews(this);

    // Remove all of the parameters
    for (LXParameter parameter : new ArrayList<LXParameter>(this.parameters.values())) {
      removeParameter(parameter);
    }
    this.parameters.clear();

    // Unset our parent reference and dispose via registry
    this.parent = null;
    this.lx.componentRegistry.dispose(this);
  }

  // Map of String key to parameter
  protected final Map<String, LXParameter> parameters = new LinkedHashMap<String, LXParameter>();

  // Map of String key to internal-only parameters
  protected final Map<String, LXParameter> internalParameters = new LinkedHashMap<String, LXParameter>();

  protected final Map<String, LXParameter> legacyParameters = new LinkedHashMap<String, LXParameter>();

  protected final Map<String, LXParameter> legacyInternalParameters = new LinkedHashMap<String, LXParameter>();

  /**
   * Adds a parameter to this component, using its label as the path by default. This method
   * is deprecated and heavily discouraged, it is best always to provide a specific path
   * using {@link #addParameter(String, LXParameter)} instead.
   *
   * @param parameter Parameter to add
   * @return this
   */
  @Deprecated
  protected final LXComponent addParameter(LXParameter parameter) {
    return addParameter(parameter.getLabel(), parameter);
  }

  /**
   * Internal implementation parameters. These won't be automatically exposed to the UI or to OSC etc. and will
   * not show up in getParameters() or the general parameter list. They will be saved and loaded however. Subclasses
   * should use this for parameters which manage internal state but should never show up to the end user and
   * will not be available via OSC.
   *
   * @param path Path to internal parameter
   * @param parameter Parameter
   * @return this
   */
  protected final LXComponent addInternalParameter(String path, LXParameter parameter) {
    if (this.internalParameters.containsKey(path)) {
      throw new IllegalStateException("Cannot add duplicate internal parameter at: " + path + ", component: " + this);
    }
    parameter.setComponent(this, INTERNAL_PREFIX + path);
    this.internalParameters.put(path, parameter);
    return this;
  }

  /**
   * Adds a parameter to the component at a fixed path. The parameter will be registered in the
   * LX hierarchy, and if it is of a listenable type will also send and receive OSC messages.
   * Listenable parameters will also be automatically registered with their parent component as
   * a listener for notifications upon any change of value.
   *
   * @param path String key path to the parameter, must be unique
   * @param parameter Parameter to add to the component
   * @return this
   */
  protected LXComponent addParameter(String path, LXParameter parameter) {
    _checkPath(path, "parameter");
    if (this.parameters.containsValue(parameter)) {
      throw new IllegalStateException(
        "Cannot add parameter twice: " + path + " / " + parameter);
    }
    LXComponent component = parameter.getParent();
    if (component != null) {
      throw new IllegalStateException(
        "Parameter " + path + " / " + parameter + " already owned by " + component);
    }
    parameter.setComponent(this, path);
    this.parameters.put(path, parameter);
    if (parameter instanceof LXListenableParameter) {
      ((LXListenableParameter) parameter).addListener(this);
      if (this instanceof LXOscComponent) {
        ((LXListenableParameter) parameter).addListener(this.oscListener);
      }
    }
    if (parameter instanceof AggregateParameter) {
      for (Map.Entry<String, LXListenableParameter> entry : ((AggregateParameter) parameter).subparameters.entrySet()) {
        addParameter(path + "/" + entry.getKey(), entry.getValue());
      }
    }
    return this;
  }

  /**
   * Adds a redundant legacy parameter path. If a stored file refers to this path which is no longer active, it
   * will load to the new parameter position.
   *
   * @param legacyPath Legacy parameter path, not used anymore
   * @param parameter Parameter that should be loaded
   * @return this
   */
  protected LXComponent addLegacyParameter(String legacyPath, LXParameter parameter) {
    if (this.legacyParameters.containsKey(legacyPath)) {
      throw new IllegalStateException("Cannot register duplicate parameter to legacy path: " + legacyPath);
    }
    this.legacyParameters.put(legacyPath, parameter);
    return this;
  }

  /**
   * Adds a redundant internal legacy parameter path. If a stored file refers to this path which is
   * no longer active, it will load to the new parameter position.
   *
   * @param legacyPath Legacy internal parameter path, not used anymore
   * @param parameter Parameter that should be loaded
   * @return this
   */
  protected LXComponent addLegacyInternalParameter(String legacyPath, LXParameter parameter) {
    if (this.legacyInternalParameters.containsKey(legacyPath)) {
      throw new IllegalStateException("Cannot register duplicate parameter to internal legacy path: " + legacyPath);
    }
    this.legacyInternalParameters.put(legacyPath, parameter);
    return this;
  }

  /**
   * Removes a parameter from the component. The parameter will be automatically disposed
   * and may never be used again.
   *
   * @param path Parameter path
   * @return this
   */
  protected LXComponent removeParameter(String path) {
    return removeParameter(path, false);
  }

  /**
   * Removes a parameter from the component. The parameter will be automatically disposed
   * and may never be used again.
   *
   * @param path Parameter path
   * @param removeModulations Whether to also explicitly remove modulations to this parameter
   * @return this
   */
  protected LXComponent removeParameter(String path, boolean removeModulations) {
    LXParameter parameter = this.parameters.get(path);
    if (parameter == null) {
      throw new IllegalStateException("Cannot remove parameter at non-existent path: " + path + " " + this);
    }
    return removeParameter(parameter, removeModulations);
  }

  /**
   * Removes a parameter from the component. The parameter will be automatically disposed
   * and may never be used again.
   *
   * @param parameter Parameter
   * @return this
   */
  protected LXComponent removeParameter(LXParameter parameter) {
    return removeParameter(parameter, false);
  }

  protected LXComponent removeParameter(LXParameter parameter, boolean disposeModulations) {
    if (parameter.getParent() != this) {
      throw new IllegalStateException("Cannot remove parameter not owned by component");
    }
    if (parameter instanceof LXListenableParameter) {
      ((LXListenableParameter) parameter).removeListener(this);
      if (this instanceof LXOscComponent) {
        ((LXListenableParameter) parameter).removeListener(this.oscListener);
      }
    }

    // Explicitly dispose of modulations for this one off parameter
    if (disposeModulations) {
      // Remove modulations from any containers up the chain
      LXComponent parent = getParent();
      while ((parent != null) && (parent != this.lx.engine)) {
        if (parent instanceof LXModulationContainer) {
          ((LXModulationContainer) parent).getModulationEngine().removeParameterModulations(parameter);
        }
        parent = parent.getParent();
      }

      // The global midi, modulation, and snapshot engines need to know we're gone
      this.lx.engine.midi.removeParameterMappings(parameter);
      this.lx.engine.modulation.removeParameterModulations(parameter);
      this.lx.engine.snapshots.removeSnapshotParameterViews(parameter);
    }


    this.parameters.remove(parameter.getPath());
    parameter.dispose();
    return this;
  }

  /**
   * Returns a read-only view of all the parameters in this component.
   *
   * @return Unmodifiable collection view of all the parameters
   */
  public final Collection<LXParameter> getParameters() {
    return Collections.unmodifiableCollection(this.parameters.values());
  }

  /**
   * Whether this component has a parameter at the given path
   *
   * @param path Parameter path
   * @return whether that parameter path is used
   */
  public final boolean hasParameter(String path) {
    return this.parameters.containsKey(path);
  }

  /**
   * Accessor for parameter at a given path
   *
   * @param path Path to parameter
   * @return Parameter if it exists, otherwise <code>null</code>
   */
  public final LXParameter getParameter(String path) {
    if (path.startsWith(INTERNAL_PREFIX)) {
      return this.internalParameters.get(path.substring(INTERNAL_PREFIX.length()));
    }
    return this.parameters.get(path);
  }

  // OSC internal implementation, catches parameter value changes and sends OSC messages
  private final LXParameterListener oscListener = (p) -> {
    // These checks are necessary for bootstrapping, before the OSC engine is spun up
    if ((this.lx != null) && (this.lx.engine != null) && (this.lx.engine.osc != null)) {
      this.lx.engine.osc.sendParameter(p);
    }
  };

  /**
   * Subclasses are free to override this if desired. It will automatically fire for
   * any listenable parameter that is registered with this component.
   *
   * @param parameter Parameter that has a value change
   */
  @Override
  public void onParameterChanged(LXParameter parameter) {
  }

  /**
   * Utility method to copy all parameter values from another component.
   * The other component is expected to be of the same type or a super-type
   * as this object.
   *
   * @param that Other component
   * @return this
   */
  protected LXComponent copyParameters(LXComponent that) {
    if (!that.getClass().isInstance(this)) {
      throw new IllegalArgumentException(
        "Cannot copy parameters from non-assignable class: " + that.getClass()
          + " -> " + getClass());
    }
    for (Map.Entry<String, LXParameter> entry : that.parameters.entrySet()) {
      LXParameter thisParameter = getParameter(entry.getKey());
      LXParameter thatParameter = entry.getValue();
      if (thisParameter instanceof StringParameter) {
        ((StringParameter) thisParameter).setValue(((StringParameter) thatParameter).getString());
      } else if (thisParameter instanceof AggregateParameter) {
        // NOTE(mcslee): do nothing! Let the sub-parameters copy over in this instance
      } else if (thisParameter instanceof CompoundParameter) {
        thisParameter.setValue(((CompoundParameter) thatParameter).getBaseValue());
      } else {
        thisParameter.setValue(thatParameter.getValue());
      }
    }
    return this;
  }

  public final static String KEY_ID = "id";
  public final static String KEY_CLASS = "class";

  public final static String KEY_RESET = "_reset_";
  public final static String KEY_PARAMETERS = "parameters";
  public final static String KEY_INTERNAL = "internal";
  public final static String KEY_CHILDREN = "children";
  public static final String KEY_COMPONENT_ID = "componentId";
  public static final String KEY_PARAMETER_PATH = "parameterPath";
  public static final String KEY_PATH = "path";

  /**
   * Utility function to serialize a set of parameters
   *
   * @param component Component that owns the parameters
   * @param obj JsonObject to serialize to
   * @param parameters Map of parameters to serialize
   */
  protected static void saveParameters(LXComponent component, JsonObject obj, Map<String, LXParameter> parameters) {
    LXSerializable.Utils.saveParameters(obj, parameters);
  }

  /**
   * Utility function to load a set of parameters
   *
   * @param component Component that owns the parameters
   * @param obj JsonObject to serialize to
   * @param parameters Map of parameters to unserialize
   */
  protected static void loadParameters(LXComponent component, JsonObject obj, Map<String, LXParameter> parameters) {
    for (String path : parameters.keySet()) {
      LXParameter parameter = parameters.get(path);
      if ((parameter == component.label) && !(component instanceof LXComponent.Renamable)) {
        continue;
      }
      if (parameter instanceof AggregateParameter) {
        // Let this store/restore from the underlying parameter values
        continue;
      }
      LXSerializable.Utils.loadParameter(parameter, obj, path);
    }
  }

  /**
   * Serializes the LX component. By default, all internal and user-facing parameters
   * are serialized, as well as any explicitly registered child components. Note that
   * child arrays are not serialized, or any other dynamic components. Subclasses may
   * override to perform more saving, and are expected to call <code>super.save(lx, obj)</code>
   * at the appropriate time.
   */
  @Override
  public void save(LX lx, JsonObject obj) {
    // Serialize parameters
    JsonObject internal = new JsonObject();
    saveParameters(this, internal, this.internalParameters);
    JsonObject parameters = new JsonObject();
    saveParameters(this, parameters, this.parameters);

    // Serialize children
    JsonObject children = LXSerializable.Utils.toObject(lx, this.mutableChildren);
    obj.addProperty(KEY_ID, this.id);
    obj.addProperty(KEY_CLASS, getClass().getName());
    obj.add(KEY_INTERNAL, internal);
    obj.add(KEY_PARAMETERS, parameters);
    obj.add(KEY_CHILDREN, children);
  }

  /**
   * Loads the LX component. Restores the ID of the component, as well as its
   * internal and user-facing parameters. Any explicitly registered children
   * will be automatically loaded, so long as they are direct descendants.
   * Dynamic arrays will not be automatically loaded, this is left to subclasses
   * to implement.
   */
  @Override
  public void load(LX lx, JsonObject obj) {
    if (obj.has(KEY_ID)) {
      lx.componentRegistry.registerId(this, obj.get(KEY_ID).getAsInt());
      this.lx = lx;
    }

    // Load parameters
    if (obj.has(KEY_INTERNAL)) {
      final JsonObject parametersObj = obj.getAsJsonObject(KEY_INTERNAL);
      loadParameters(this, parametersObj, this.legacyInternalParameters);
      loadParameters(this, parametersObj, this.internalParameters);
    }
    if (obj.has(KEY_PARAMETERS)) {
      final JsonObject parametersObj = obj.getAsJsonObject(KEY_PARAMETERS);
      loadParameters(this, parametersObj, this.legacyParameters);
      loadParameters(this, parametersObj, this.parameters);
    }

    // Load child components
    JsonObject children = obj.has(KEY_CHILDREN) ? obj.getAsJsonObject(KEY_CHILDREN) : new JsonObject();
    for (String path : this.mutableChildren.keySet()) {
      LXComponent child = this.mutableChildren.get(path);
      if (children.has(path)) {
        child.load(lx, children.getAsJsonObject(path));
      } else {
        final JsonObject reset = new JsonObject();
        reset.addProperty(KEY_RESET, KEY_RESET);
        child.load(lx, reset);
      }
    }
  }
}
