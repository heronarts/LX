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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.google.gson.JsonObject;

import heronarts.lx.midi.LXMidiListener;
import heronarts.lx.modulation.LXModulationContainer;
import heronarts.lx.modulation.LXModulationEngine;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.LXParameter;

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

  protected LXDeviceComponent(LX lx) {
    this(lx, null);
  }

  protected LXDeviceComponent(LX lx, String label) {
    super(lx, label);
    addChild("modulation", this.modulation = new LXModulationEngine(lx));
    addInternalParameter("expanded", this.controlsExpanded);
    addInternalParameter("modulationExpanded", this.modulationExpanded);
  }

  public static String getCategory(Class<? extends LXDeviceComponent> clazz) {
    LXCategory annotation = clazz.getAnnotation(LXCategory.class);
    return (annotation != null) ? annotation.value() : LXCategory.OTHER;
  }

  /**
   * Subclasses may override this to filter out parameters that should not
   * be controlled by a remote surface
   *
   * @param parameter Parameter to check
   * @return Whether parameter is eligible for remote control
   */
  protected boolean isRemoteControl(LXListenableNormalizedParameter parameter) {
    return true;
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
        if (parameter instanceof LXListenableNormalizedParameter) {
          remoteControls.add((LXListenableNormalizedParameter) parameter);
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

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_DEVICE_VERSION, getDeviceVersion());
  }

}
