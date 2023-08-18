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
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import heronarts.lx.color.ColorParameter;
import heronarts.lx.parameter.AggregateParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;

/**
 * Interface for any object that may be stored and loaded from a serialized file using
 * Json.
 */
public interface LXSerializable {

  /**
   * Serializes this instance into the JSON object
   *
   * @param lx LX instance
   * @param object Object to serialize into
   */
  public void save(LX lx, JsonObject object);

  /**
   * Restores this instance from a JSON object
   *
   * @param lx LX instance
   * @param object Object to deserialize
   */
  public void load(LX lx, JsonObject object);

  /**
   * Static container for utility methods
   */
  public static class Utils {

    public static void saveParameters(JsonObject obj, Map<String, LXParameter> parameters) {
      for (String path : parameters.keySet()) {
        LXParameter parameter = parameters.get(path);
        if (parameter instanceof AggregateParameter) {
          // Let this store/restore from the underlying parameter values
          continue;
        }
        LXSerializable.Utils.saveParameter(parameter, obj, path);
      }
    }

    public static void saveParameter(LXParameter parameter, JsonObject obj) {
      saveParameter(parameter, obj, parameter.getPath());
    }

    public static void saveParameter(LXParameter parameter, JsonObject obj, String path) {
      if (parameter instanceof StringParameter) {
        obj.addProperty(path, ((StringParameter) parameter).getString());
      } else if (parameter instanceof BooleanParameter) {
        obj.addProperty(path, ((BooleanParameter) parameter).isOn());
      } else if (parameter instanceof DiscreteParameter) {
        obj.addProperty(path, ((DiscreteParameter) parameter).getValuei());
      } else if (parameter instanceof ColorParameter) {
        obj.addProperty(path, ((ColorParameter) parameter).getColor());
      } else if (parameter instanceof CompoundParameter) {
        obj.addProperty(path, ((CompoundParameter) parameter).getBaseValue());
      } else if (parameter instanceof FunctionalParameter) {
        // Do not write FunctionalParamters into saved files
      } else {
        obj.addProperty(path, parameter.getValue());
      }
    }

    public static void loadParameter(LXParameter parameter, JsonObject obj, String path) {
      if (obj.has(path)) {
        JsonElement value = obj.get(path);
        try {
          if (parameter instanceof StringParameter) {
            if (value instanceof JsonNull) {
              ((StringParameter) parameter).setValue(null);
            } else {
              ((StringParameter) parameter).setValue(value.getAsString());
            }
          } else if (parameter instanceof BooleanParameter) {
            ((BooleanParameter) parameter).setValue(value.getAsBoolean());
          } else if (parameter instanceof DiscreteParameter) {
            parameter.setValue(value.getAsInt());
          } else if (parameter instanceof ColorParameter) {
            ((ColorParameter) parameter).setColor(value.getAsInt());
          } else if (parameter instanceof CompoundParameter) {
            parameter.setValue(value.getAsDouble());
          } else if (parameter instanceof FunctionalParameter) {
            // Do nothing
          } else {
            parameter.setValue(value.getAsDouble());
          }
        } catch (Exception x) {
          LX.error(x, "Invalid format loading parameter " + parameter + " from JSON value: " + value);
        }
      }
    }

    /**
     * Loads an integer value into a parameter, if it is found. If the
     * key doesn't exist, this method does nothing.
     *
     * @param parameter Parameter to load
     * @param object Json object
     * @param key Key to check, if exists loaded as int
     */
    public static void loadInt(DiscreteParameter parameter, JsonObject object, String key) {
      if (object.has(key)) {
        parameter.setValue(object.get(key).getAsInt());
      }
    }

    /**
     * Loads a boolean value into a parameter, if it is found. If the
     * key doesn't exist, this method does nothing.
     *
     * @param parameter Parameter to load
     * @param object Json object
     * @param key Key to check, if exists loaded as boolean
     */
    public static void loadBoolean(BooleanParameter parameter, JsonObject object, String key) {
      if (object.has(key)) {
        parameter.setValue(object.get(key).getAsBoolean());
      }
    }

    /**
     * Loads an double value into a parameter, if it is found. If the
     * key doesn't exist, this method does nothing.
     *
     * @param parameter Parameter to load
     * @param object Json object
     * @param key Key to check, if exists loaded as double
     */
    public static void loadDouble(LXParameter parameter, JsonObject object, String key) {
      if (object.has(key)) {
        parameter.setValue(object.get(key).getAsDouble());
      }
    }

    /**
     * Loads an double value into a parameter, if it is found. If the
     * key doesn't exist, this method does nothing.
     *
     * @param parameter Parameter to load
     * @param object Json object
     * @param key Key to check, if exists loaded as string
     */
    public static void loadString(StringParameter parameter, JsonObject object, String key) {
      if (object.has(key)) {
        parameter.setValue(object.get(key).getAsString());
      }
    }

    /**
     * Loads a serializable object from a sub-key, if the key is found. If it is not found, no loading
     * will occur.
     *
     * @param lx LX instance
     * @param serializable Sub-object to load
     * @param object JSON object to load from
     * @param key Key to check for existence of
     */
    public static void loadObject(LX lx, LXSerializable serializable, JsonObject object, String key) {
      loadObject(lx, serializable, object, key, false);
    }

    /**
     * Loads a serializable object from a sub-key, if the key is found. If it is not found, loading will
     * occur with a default empty object if the final argument is <code>true</code>. The sub-object should handle that.
     *
     * @param lx LX instance
     * @param serializable Sub-object to load
     * @param object JSON object to load from
     * @param key Key to check for existence of
     * @param defaultEmptyObj Whether to load an empty JsonObject if <code>key</code> is not found
     */
    public static void loadObject(LX lx, LXSerializable serializable, JsonObject object, String key, boolean defaultEmptyObj) {
      if (object.has(key)) {
        serializable.load(lx, object.getAsJsonObject(key));
      } else if (defaultEmptyObj) {
        serializable.load(lx, new JsonObject());
      }
    }

    /**
     * Reset an object by loading an empty dictionary with the reset key
     *
     * @param lx LX instance
     * @param serializable Object to reset
     */
    public static void resetObject(LX lx, LXSerializable serializable) {
      final JsonObject reset = new JsonObject();
      reset.addProperty(LXComponent.KEY_RESET, LXComponent.KEY_RESET);
      serializable.load(lx, reset);
    }

    /**
     * Loads an array of sub-objects from the given key, if it is found
     *
     * @param lx LX instance
     * @param serializables List of child objects to load
     * @param object Object to load from
     * @param key Key to check for
     */
    public static void loadArray(LX lx, List<? extends LXSerializable> serializables, JsonObject object, String key) {
      if (object.has(key)) {
        JsonArray array = object.getAsJsonArray(key);
        for (int i = 0; i < array.size(); ++i) {
          serializables.get(i).load(lx, array.get(i).getAsJsonObject());
        }
      }
    }

    /**
     * Loads an array of sub-objects from the given key, if it is found
     *
     * @param lx LX instance
     * @param serializables array of child objects to load
     * @param object Object to load from
     * @param key Key to check for
     */
    public static void loadArray(LX lx, LXSerializable[] serializables, JsonObject object, String key) {
      if (object.has(key)) {
        JsonArray array = object.getAsJsonArray(key);
        for (int i = 0; i < array.size(); ++i) {
          serializables[i].load(lx, array.get(i).getAsJsonObject());
        }
      }
    }

    /**
     * Serializes an LXComponent to a JsonObject
     *
     * @param component Component to serialize
     * @return JsonObject representation of the component
     */
    public static JsonObject toObject(LXComponent component) {
      return toObject(component, false);
    }

    /**
     * Serializes an LXComponent to a JsonObject
     *
     * @param component Component to serialize
     * @param stripIds Whether to strip ids from the result
     * @return JsonObject representation of the component
     */
    public static JsonObject toObject(LXComponent component, boolean stripIds) {
      return toObject(component.getLX(), component, stripIds);
    }

    /**
     * Serializes any LXSerializable to a JsonObject
     *
     * @param lx LX instance
     * @param serializable Serializable object
     * @return JsonObject representation of the object
     */
    public static JsonObject toObject(LX lx, LXSerializable serializable) {
      return toObject(lx, serializable, false);
    }

    /**
     * Serializes any LXSerializable to a JsonObject
     *
     * @param lx LX instance
     * @param serializable Serializable object
     * @param stripIds Whether to strip ids from the result
     * @return JsonObject representation of the object
     */
    public static JsonObject toObject(LX lx, LXSerializable serializable, boolean stripIds) {
      JsonObject obj = new JsonObject();
      serializable.save(lx,  obj);
      if (stripIds) {
        stripIds(obj);
      }
      return obj;
    }

    /**
     * Serializes a map of subobjects into a JsonObject
     *
     * @param lx LX instance
     * @param serializables Map of serializable subobjects
     * @return JsonObject representation of all child objects
     */
    public static JsonObject toObject(LX lx, Map<String, ? extends LXSerializable> serializables) {
      JsonObject map = new JsonObject();
      for (String key : serializables.keySet()) {
        JsonObject obj = new JsonObject();
        serializables.get(key).save(lx, obj);
        map.add(key, obj);
      }
      return map;
    }

    /**
     * Serializes an array of subobjects
     *
     * @param lx LX instance
     * @param serializables Array of sub-objects
     * @return JsonArray representation of all subobjects
     */
    public static JsonArray toArray(LX lx, LXSerializable[] serializables) {
      return toArray(lx, serializables, false);
    }

    /**
     * Serializes an array of subobjects
     *
     * @param lx LX instance
     * @param serializables Array of sub-objects
     * @param stripIds Whether to strip ids in output
     * @return JsonArray representation of all subobjects
     */
    public static JsonArray toArray(LX lx, LXSerializable[] serializables, boolean stripIds) {
      JsonArray arr = new JsonArray();
      for (LXSerializable serializable : serializables) {
        arr.add(toObject(lx, serializable, stripIds));
      }
      return arr;
    }

    /**
     * Serialized a generic collection of sub-objects, not necessarily ordered
     *
     * @param lx LX instance
     * @param serializables Collection of serializable objects
     * @return JsonArray representation of collection of objects
     */
    public static JsonArray toArray(LX lx, Collection<? extends LXSerializable> serializables) {
      return toArray(lx, serializables, false);
    }

    /**
     * Serialized a generic collection of sub-objects, not necessarily ordered
     *
     * @param lx LX instance
     * @param serializables Collection of serializable objects
     * @param stripIds Whether to strip ids from output
     * @return JsonArray representation of collection of objects
     */
    public static JsonArray toArray(LX lx, Collection<? extends LXSerializable> serializables, boolean stripIds) {
      JsonArray arr = new JsonArray();
      for (LXSerializable serializable : serializables) {
        arr.add(toObject(lx, serializable, stripIds));
      }
      return arr;
    }

    /**
     * Strips all ID values out of a JsonObject. This is often helpful when copy/pasting
     * or loading objects in a context where global IDs should not be overwritten.
     *
     * @param object Object to strip all nested ID keys from
     * @return The same object, with no ID keys
     */
    public static JsonObject stripIds(JsonObject object) {
      object.remove(LXComponent.KEY_ID);
      for (Map.Entry<java.lang.String, JsonElement> entry : object.entrySet()) {
        JsonElement value = entry.getValue();
        if (value.isJsonObject()) {
          stripIds(value.getAsJsonObject());
        } else if (value.isJsonArray()) {
          for (JsonElement elem : value.getAsJsonArray()) {
            if (elem.isJsonObject()) {
              stripIds(elem.getAsJsonObject());
            }
          }
        }
      }
      return object;
    }

    public static JsonObject stripParameter(JsonObject object, String parameter) {
      if (object.has(LXComponent.KEY_PARAMETERS)) {
        object.get(LXComponent.KEY_PARAMETERS).getAsJsonObject().remove(parameter);
      }
      return object;
    }
  }

}
