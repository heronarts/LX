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
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;

public interface LXSerializable {
  public void save(LX lx, JsonObject object);
  public void load(LX lx, JsonObject object);

  public static class Utils {

    public static void loadInt(DiscreteParameter parameter, JsonObject object, String key) {
      if (object.has(key)) {
        parameter.setValue(object.get(key).getAsInt());
      }
    }

    public static void loadBoolean(BooleanParameter parameter, JsonObject object, String key) {
      if (object.has(key)) {
        parameter.setValue(object.get(key).getAsBoolean());
      }
    }

    public static void loadDouble(LXParameter parameter, JsonObject object, String key) {
      if (object.has(key)) {
        parameter.setValue(object.get(key).getAsDouble());
      }
    }

    public static void loadObject(LX lx, LXSerializable serializable, JsonObject object, String key) {
      if (object.has(key)) {
        serializable.load(lx, object.getAsJsonObject(key));
      }
    }

    public static void loadArray(LX lx, LXSerializable[] serializables, JsonObject object, String key) {
      if (object.has(key)) {
        JsonArray array = object.getAsJsonArray(key);
        for (int i = 0; i < array.size(); ++i) {
          serializables[i].load(lx, array.get(i).getAsJsonObject());
        }
      }
    }

    public static JsonObject toObject(LX lx, LXSerializable serializable) {
      JsonObject obj = new JsonObject();
      serializable.save(lx,  obj);
      return obj;
    }

    public static JsonObject toObject(LX lx, Map<String, ? extends LXSerializable> serializables) {
      JsonObject map = new JsonObject();
      for (String key : serializables.keySet()) {
        JsonObject obj = new JsonObject();
        serializables.get(key).save(lx, obj);
        map.add(key, obj);
      }
      return map;
    }

    public static JsonArray toArray(LX lx, LXSerializable[] serializables) {
      JsonArray arr = new JsonArray();
      for (LXSerializable serializable : serializables) {
        arr.add(toObject(lx, serializable));
      }
      return arr;
    }

    public static JsonArray toArray(LX lx, Collection<? extends LXSerializable> serializables) {
      JsonArray arr = new JsonArray();
      for (LXSerializable serializable : serializables) {
        arr.add(toObject(lx, serializable));
      }
      return arr;
    }
  }

}
