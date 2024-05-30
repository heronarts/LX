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
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import heronarts.lx.midi.LXMidiListener;
import heronarts.lx.midi.LXShortMessage;
import heronarts.lx.midi.MidiFilterParameter;
import heronarts.lx.midi.MidiPanic;
import heronarts.lx.midi.surface.LXMidiSurface;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXMasterBus;
import heronarts.lx.model.LXModel;
import heronarts.lx.modulation.LXModulationContainer;
import heronarts.lx.modulation.LXModulationEngine;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.AggregateParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.structure.view.LXViewDefinition;
import heronarts.lx.structure.view.LXViewEngine;

/**
 * A component which may have its own scoped user-level modulators. The concrete subclasses
 * of this are Patterns and Effects.
 */
public abstract class LXDeviceComponent extends LXLayeredComponent implements LXPresetComponent, LXModulationContainer, LXOscComponent, LXMidiListener {

  /**
   * Marker interface that indicates this device implements MIDI functionality
   */
  public interface Midi {}

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

  private LXListenableNormalizedParameter[] defaultRemoteControls = null;
  private LXListenableNormalizedParameter[] customRemoteControls = null;

  public MutableParameter remoteControlsChanged = new MutableParameter("Remote Control Monitor");

  private Throwable crash = null;

  public final BooleanParameter crashed =
    new BooleanParameter("Crashed", false)
    .setDescription("Set to true by the engine if this component fails in an unexpected way");

  public final BooleanParameter controlsExpanded =
    new BooleanParameter("Expanded", true)
    .setDescription("Whether the device controls are expanded");

  public final BooleanParameter controlsExpandedCue =
    new BooleanParameter("Expanded Cue", true)
    .setDescription("Whether the device controls are expanded in cue view");

  public final BooleanParameter controlsExpandedAux =
    new BooleanParameter("Expanded Aux", true)
    .setDescription("Whether the device controls are expanded in aux view");

  public final BooleanParameter modulationExpanded =
    new BooleanParameter("Modulation Expanded", false)
    .setDescription("Whether the device modulation section is expanded");

  public final MidiFilterParameter midiFilter =
    new MidiFilterParameter("MIDI Filter", true)
    .setDescription("MIDI filter settings for this device");

  /**
   * View selector for this device
   */
  public final LXViewEngine.Selector view;
  public final LXViewEngine.Selector viewPriority;

  private final LXParameterListener viewListener;
  private final LXParameterListener viewPriorityListener;

  /**
   * A semaphore used to keep count of how many remote control surfaces may be
   * controlling this component. This may be used by UI implementations to indicate
   * to the user that this component is under remote control.
   */
  public final MutableParameter controlSurfaceSemaphore = (MutableParameter)
    new MutableParameter("Control-Surfaces", 0)
    .setDescription("How many control surfaces are controlling this component");

  // Must use a thread-safe set here because it's also accessed from the UI thread!
  private final CopyOnWriteArraySet<LXMidiSurface> controlSurfaces =
    new CopyOnWriteArraySet<LXMidiSurface>();

  protected LXDeviceComponent(LX lx) {
    this(lx, null);
  }

  protected LXDeviceComponent(LX lx, String label) {
    super(lx, label);
    addChild("modulation", this.modulation = new LXModulationEngine(lx));
    addInternalParameter("expanded", this.controlsExpanded);
    addInternalParameter("expandedCue", this.controlsExpandedCue);
    addInternalParameter("expandedAux", this.controlsExpandedAux);
    addInternalParameter("modulationExpanded", this.modulationExpanded);

    addParameter("midiFilter", this.midiFilter);
    addParameter("view", this.view = lx.structure.views.newViewSelector("View", "Model view selector for this device"));
    addParameter("viewPriority", this.viewPriority = lx.structure.views.newViewSelectorPriority("View", "Priority model view selector for this device"));

    this.view.addListener(this.viewListener = p -> {
      LXViewDefinition view = this.view.getObject();
      if ((view == null) || view.priority.isOn()) {
        this.viewPriority.setValue(view);
      }
    });

    this.viewPriority.addListener(this.viewPriorityListener = p -> {
      this.view.setValue(this.viewPriority.getObject());
    });
  }

  public LXModel getModelView() {
    LXViewDefinition view = this.view.getObject();
    if (view != null) {
      return view.getModelView();
    }
    LXComponent parent = getParent();
    if (parent instanceof LXAbstractChannel) {
      return ((LXAbstractChannel) parent).getModelView();
    } else if (parent instanceof LXMasterBus) {
      return lx.model;
    }
    return getModel();
  }

  private void validateRemoteControls(LXListenableNormalizedParameter ... remoteControls) {
    for (LXListenableNormalizedParameter control : remoteControls) {
      if ((control != null) && !control.isDescendant(this)) {
        throw new IllegalArgumentException("Cannot add remote control that does not belong to this component: " + control);
      }
    }
  }

  protected void setRemoteControls(LXListenableNormalizedParameter ... remoteControls) {
    validateRemoteControls(remoteControls);
    this.defaultRemoteControls = remoteControls;
  }

  public void clearCustomRemoteControls() {
    if (this.customRemoteControls != null) {
      this.customRemoteControls = null;
      this.remoteControlsChanged.bang();
    }
  }

  public void setCustomRemoteControls(LXListenableNormalizedParameter ... remoteControls) {
    validateRemoteControls(remoteControls);
    this.customRemoteControls = remoteControls;
    this.remoteControlsChanged.bang();
  }

  protected LXComponent removeCustomRemoteControl(LXParameter parameter) {
    // Clear this parameter from custom remote controls
    if (this.customRemoteControls != null) {
      for (int i = 0; i < this.customRemoteControls.length; ++i) {
        if (this.customRemoteControls[i] == parameter) {
          this.customRemoteControls[i] = null;
        }
      }
    }
    return this;
  }

  public LXListenableNormalizedParameter[] getCustomRemoteControls() {
    return this.customRemoteControls;
  }

  /**
   * Returns whether this parameter is stored along with snapshots
   *
   * @param parameter Parameter
   * @return true if this can be included in snapshots
   */
  public boolean isSnapshotControl(LXParameter parameter) {
    return !(
      (parameter == this.label) ||
      (parameter == this.midiFilter) ||
      (parameter == this.viewPriority)
    );

  }

  /**
   * Returns whether this parameter is visible in default remote control
   * or device control UIs
   *
   * @param parameter Parameter to check
   * @return true if this should be hidden by default
   */
  public boolean isHiddenControl(LXParameter parameter) {
    return
      (parameter == this.midiFilter) ||
      (parameter == this.view) ||
      (parameter == this.viewPriority);
  }

  protected LXDeviceComponent resetRemoteControls() {
    this.defaultRemoteControls = null;
    this.remoteControlsChanged.bang();
    return this;
  }

  /**
   * Subclasses may override this. The method returns an array of parameters in order
   * that can be addressed by a remote control surface
   *
   * @return Array of parameters for a remote control surface to address
   */
  public final LXListenableNormalizedParameter[] getRemoteControls() {
    if (this.customRemoteControls != null) {
      return this.customRemoteControls;
    }
    if (this.defaultRemoteControls == null) {
      List<LXListenableNormalizedParameter> remoteControls = new ArrayList<LXListenableNormalizedParameter>();
      for (LXParameter parameter : getParameters()) {
        // No hidden controls please!
        if (isHiddenControl(parameter)) {
          continue;
        }
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
          remoteControls.add((LXListenableNormalizedParameter) parameter);
        }
      }
      this.defaultRemoteControls = remoteControls.toArray(new LXListenableNormalizedParameter[0]);
    }
    return this.defaultRemoteControls;
  }

  public LXComponent addControlSurface(LXMidiSurface surface) {
    if (this.controlSurfaces.contains(surface)) {
      throw new IllegalStateException("Cannot add same control surface to device twice: " + surface);
    }
    this.controlSurfaces.add(surface);
    this.controlSurfaceSemaphore.increment();
    return this;
  }

  public LXComponent removeControlSurface(LXMidiSurface surface) {
    if (!this.controlSurfaces.contains(surface)) {
      throw new IllegalStateException("Cannot remove control surface that is not added: " + surface);
    }
    this.controlSurfaces.remove(surface);
    this.controlSurfaceSemaphore.decrement();
    return this;
  }

  public Set<LXMidiSurface> getControlSurfaces() {
    return this.controlSurfaces;
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
        this.modulation.loop(deltaMs);
        super.loop(deltaMs);
      } catch (Throwable x) {
        LX.error(x, "Unexpected error in device loop " + getClass().getName() + ": " + x.getLocalizedMessage());
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

  @Override
  public LXModulationEngine getModulationEngine() {
    return this.modulation;
  }

  @Override
  public BooleanParameter getModulationExpanded() {
    return this.modulationExpanded;
  }

  /**
   * Dispatch a MIDI message to this device, and any of its modulators which should receive that
   *
   * @param message Message
   */
  public void midiDispatch(LXShortMessage message) {
    if (message instanceof MidiPanic) {
      this.midiFilter.midiPanic();
      message.dispatch(this);
    } else if (this.midiFilter.filter(message)) {
      message.dispatch(this);
    }
    getModulationEngine().midiDispatch(message);
  }

  @Override
  public void dispose() {
    this.view.removeListener(this.viewListener);
    this.viewPriority.removeListener(this.viewPriorityListener);
    super.dispose();
  }

  protected static final String KEY_DEVICE_VERSION = "deviceVersion";

  private static final String KEY_REMOTE_CONTROLS = "remoteControls";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_DEVICE_VERSION, getDeviceVersion());
    if (this.customRemoteControls != null) {
      JsonArray remoteControls = new JsonArray();
      for (LXListenableNormalizedParameter control : this.customRemoteControls) {
        remoteControls.add((control != null) ? control.getCanonicalPath(this) : null);
      }
      obj.add(KEY_REMOTE_CONTROLS, remoteControls);
    }
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(KEY_REMOTE_CONTROLS)) {
      JsonArray remoteControls = obj.get(KEY_REMOTE_CONTROLS).getAsJsonArray();
      LXListenableNormalizedParameter[] customRemoteControls = new LXListenableNormalizedParameter[remoteControls.size()];
      for (int i = 0; i < remoteControls.size(); ++i) {
        JsonElement elem = remoteControls.get(i);
        if (!elem.isJsonNull()) {
          LXParameter parameter = LXPath.getParameter(this, remoteControls.get(i).getAsString());
          if (parameter instanceof LXListenableNormalizedParameter) {
            customRemoteControls[i] = (LXListenableNormalizedParameter) parameter;
          }
        }
      }
      setCustomRemoteControls(customRemoteControls);
    }
  }

}
