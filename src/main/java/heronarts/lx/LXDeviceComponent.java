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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

import heronarts.lx.effect.LXEffect;
import heronarts.lx.midi.LXMidiListener;
import heronarts.lx.modulation.LXModulationContainer;
import heronarts.lx.modulation.LXModulationEngine;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.AggregateParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.pattern.LXPattern;

/**
 * A component which may have its own scoped user-level modulators. The concrete subclasses
 * of this are Patterns and Effects.
 */
public abstract class LXDeviceComponent extends LXLayeredComponent implements LXModulationContainer, LXOscComponent, LXMidiListener {

  public static final Comparator<Class<? extends LXDeviceComponent>> DEVICE_CATEGORY_NAME_SORT =
    new Comparator<Class<? extends LXDeviceComponent>>() {
      public int compare(Class<? extends LXDeviceComponent> cls1, Class<? extends LXDeviceComponent> cls2) {
        String category1 = getCategory(cls1);
        String category2 = getCategory(cls2);
        if (category1.equals(category2)) {
          return LXComponent.getComponentName(cls1).compareToIgnoreCase(LXComponent.getComponentName(cls2));
        } else if (category1.equals(LXCategory.TEST)) {
          return 1;
        } else if (category2.equals(LXCategory.TEST)) {
          return -1;
        }
        return category1.compareToIgnoreCase(category2);
      }
  };

  protected static final int DEVICE_VERSION_UNSPECIFIED = -1;

  public final LXModulationEngine modulation;

  private LXListenableNormalizedParameter[] remoteControls = null;

  private Throwable crash = null;

  public final BooleanParameter crashed =
    new BooleanParameter("Crashed", false)
    .setDescription("Set to true by the engine if this component fails in an unexpected way");

  public final BooleanParameter controlsExpanded =
    new BooleanParameter("Expanded", true)
    .setDescription("Whether the device controls are expanded");

  public final BooleanParameter modulationExpanded =
    new BooleanParameter("Modulation Expanded", false)
    .setDescription("Whether the device modulation section is expanded");

  public final StringParameter presetFile =
    new StringParameter("Preset", null)
    .setDescription("Name of last preset file that has been loaded/saved");

  protected LXDeviceComponent(LX lx) {
    this(lx, null);
  }

  protected LXDeviceComponent(LX lx, String label) {
    super(lx, label);
    addChild("modulation", this.modulation = new LXModulationEngine(lx));
    addInternalParameter("expanded", this.controlsExpanded);
    addInternalParameter("modulationExpanded", this.modulationExpanded);
    addInternalParameter("presetFile", this.presetFile);
  }

  public static String getCategory(Class<? extends LXDeviceComponent> clazz) {
    LXCategory annotation = clazz.getAnnotation(LXCategory.class);
    return (annotation != null) ? annotation.value() : LXCategory.OTHER;
  }

  protected void setRemoteControls(LXListenableNormalizedParameter ... remoteControls) {
    this.remoteControls = remoteControls;
  }

  /**
   * Subclasses may override this. The method returns an array of parameters in order
   * that can be addressed by a remote control surface
   *
   * @return Array of parameters for a remote control surface to address
   */
  public LXListenableNormalizedParameter[] getRemoteControls() {
    if (this.remoteControls == null) {
      List<LXListenableNormalizedParameter> remoteControls = new ArrayList<LXListenableNormalizedParameter>();
      for (LXParameter parameter : getParameters()) {
        // Do not include subparams of AggregateParameter
        if (parameter.getParentParameter() != null) {
          continue;
        }
        if (parameter instanceof AggregateParameter) {
          // For aggregate parameters, include the specified sub-param
          LXListenableNormalizedParameter subparameter = ((AggregateParameter) parameter).getRemoteControl();
          if (subparameter != null) {
            remoteControls.add(subparameter);
          }
        } else if (parameter instanceof LXListenableNormalizedParameter) {
          // Otherwise include any parameter of a knob-able type
          boolean valid = true;
          if (this instanceof LXPattern) {
            valid = parameter != ((LXPattern) this).recall;
          } else if (this instanceof LXEffect) {
            valid = parameter != ((LXEffect) this).enabled;
          }
          if (valid) {
            remoteControls.add((LXListenableNormalizedParameter) parameter);
          }
        }
      }
      this.remoteControls = remoteControls.toArray(new LXListenableNormalizedParameter[0]);
    }
    return this.remoteControls;
  }

  /**
   * Subclasses may override this and provide a version identifier. This will be written
   * to project files when this device is saved. The version should be incremented when
   * change to the code makes old parameter values incompatible, and the implementation
   * should handle loading old values if possible.
   *
   * @return Version number of this device
   */
  public int getDeviceVersion() {
    return DEVICE_VERSION_UNSPECIFIED;
  }

  @Override
  public void loop(double deltaMs) {
    if (!this.crashed.isOn()) {
      try {
        super.loop(deltaMs);
        this.modulation.loop(deltaMs);
      } catch (Exception x) {
        LX.error(x, "Unexpected exception in device loop " + getClass().getName() + ": " + x.getLocalizedMessage());
        this.lx.pushError(x, "Device " + LXComponent.getComponentName(getClass()) + " crashed due to an unexpected error.\n" + x.getLocalizedMessage());
        this.crash = x;
        this.crashed.setValue(true);
      }
    }
  }

  public Throwable getCrash() {
    return this.crash;
  }

  public String getCrashStackTrace() {
    try (StringWriter sw = new StringWriter()){
      this.crash.printStackTrace(new PrintWriter(sw));
      return sw.toString();
    } catch (IOException iox) {
      // This is fucked
      return null;
    }
  }

  public LXModulationEngine getModulationEngine() {
    return this.modulation;
  }

  protected static final String KEY_DEVICE_VERSION = "deviceVersion";
  private final static String KEY_VERSION = "version";
  private final static String KEY_TIMESTAMP = "timestamp";

  public void loadPreset(File file) {
    try (FileReader fr = new FileReader(file)) {
      JsonObject obj = new Gson().fromJson(fr, JsonObject.class);
      this.lx.componentRegistry.projectLoading = true;
      this.lx.componentRegistry.setIdCounter(this.lx.getMaxId(obj, this.lx.componentRegistry.getIdCounter()) + 1);
      load(this.lx, obj);
      this.lx.componentRegistry.projectLoading = false;
      this.presetFile.setValue(file.getName());
      LX.log("Device preset loaded successfully from " + file.toString());
    } catch (IOException iox) {
      LX.error("Could not load device preset file: " + iox.getLocalizedMessage());
      this.lx.pushError(iox, "Could not load device preset file: " + iox.getLocalizedMessage());
    } catch (Exception x) {
      LX.error(x, "Exception in loadPreset: " + x.getLocalizedMessage());
      this.lx.pushError(x, "Exception in loadPreset: " + x.getLocalizedMessage());
    } finally {
      this.lx.componentRegistry.projectLoading = false;
    }
  }

  public void savePreset(File file) {
    JsonObject obj = new JsonObject();
    obj.addProperty(KEY_VERSION, LX.VERSION);
    obj.addProperty(KEY_TIMESTAMP, System.currentTimeMillis());
    save(this.lx, obj);
    try (JsonWriter writer = new JsonWriter(new FileWriter(file))) {
      writer.setIndent("  ");
      new GsonBuilder().create().toJson(obj, writer);
      this.presetFile.setValue(file.getName());
      LX.log("Device preset saved successfully to " + file.toString());
    } catch (IOException iox) {
      LX.error(iox, "Could not write device preset to output file: " + file.toString());
    }
  }

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_DEVICE_VERSION, getDeviceVersion());
  }

}
