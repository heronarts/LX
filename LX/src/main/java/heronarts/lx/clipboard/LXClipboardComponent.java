/**
 * Copyright 2019- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.clipboard;

import java.io.StringWriter;
import java.util.Map;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

import heronarts.lx.LX;
import heronarts.lx.LXChannel;
import heronarts.lx.LXComponent;
import heronarts.lx.LXEffect;
import heronarts.lx.LXPattern;
import heronarts.lx.LXSerializable;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.structure.LXFixture;

public class LXClipboardComponent<T extends LXComponent> implements LXClipboardItem {

  // Helpers to make code and casting less ugly

  public static class Channel extends LXClipboardComponent<LXChannel> {
    public Channel(LXChannel channel) {
      super(LXChannel.class, channel);
    }
  }

  public static class Pattern extends LXClipboardComponent<LXPattern> {
    public Pattern(LXPattern pattern) {
      super(LXPattern.class, pattern);
    }
  }

  public static class Effect extends LXClipboardComponent<LXEffect> {
    public Effect(LXEffect effect) {
      super(LXEffect.class, effect);
    }
  }

  public static class Modulator extends LXClipboardComponent<LXModulator> {
    public Modulator(LXModulator modulator) {
      super(LXModulator.class, modulator);
    }
  }

  public static class Fixture extends LXClipboardComponent<LXFixture> {
    public Fixture(LXFixture fixture) {
      super(LXFixture.class, fixture);
    }
  }

  private final Class<T> componentClass;
  private final Class<? extends T> instanceClass;
  private final JsonObject componentObj;

  private LXClipboardComponent(Class<T> cls, T component) {
    this.componentClass = cls;
    this.instanceClass = component.getClass().asSubclass(cls);
    this.componentObj = LXSerializable.Utils.toObject(component);

    // TODO(mcslee): is this the best way to handle this? alternate solution would be
    // having flags or state for the load() methods to know about restoring from a
    // file vs duplicating things...
    stripIds(this.componentObj);
  }

  private void stripIds(JsonObject object) {
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
  }

  @Override
  public String getSystemClipboardString() {
    try {
      StringWriter io = new StringWriter();
      JsonWriter writer = new JsonWriter(io);
      writer.setIndent("  ");
      new GsonBuilder().create().toJson(this.componentObj, writer);
      writer.close();
      return io.toString();
    } catch (Exception x) {
      System.err.println("Error serializing LXComponent for system clipboard");
      x.printStackTrace();
    }
    return null;
  }

  @Override
  public Class<? extends LXComponent> getComponentClass() {
    return this.componentClass;
  }

  public Class<? extends T> getInstanceClass() {
    return this.instanceClass.asSubclass(this.componentClass);
  }

  public JsonObject getComponentObject() {
    return this.componentObj;
  }

  public T duplicate(LX lx) {
    try {
      T instance = lx.instantiateComponent(this.instanceClass.asSubclass(this.componentClass), this.componentClass);
      instance.load(lx, this.componentObj);
      return instance;
    } catch (LX.InstantiationException x) {
      lx.command.pushError("Cannot duplicate component, class is missing: " + this.componentClass + ". Check that content files have not been removed?", x);
    }
    return null;
  }


}
