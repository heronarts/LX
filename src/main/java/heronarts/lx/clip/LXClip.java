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

package heronarts.lx.clip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXLayer;
import heronarts.lx.LXLayeredComponent;
import heronarts.lx.LXPath;
import heronarts.lx.LXRunnableComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.snapshot.LXClipSnapshot;

public abstract class LXClip extends LXRunnableComponent implements LXOscComponent, LXComponent.Renamable, LXBus.Listener {

  public interface Listener {
    public void parameterLaneAdded(LXClip clip, ParameterClipLane lane);
    public void parameterLaneRemoved(LXClip clip, ParameterClipLane lane);
  }

  private final List<Listener> listeners = new ArrayList<Listener>();

  double cursor = 0;

  public final MutableParameter length = (MutableParameter)
    new MutableParameter("Length", 0)
    .setDescription("The length of the clip")
    .setUnits(LXParameter.Units.MILLISECONDS);

  public final BooleanParameter loop = new BooleanParameter("Loop")
  .setDescription("Whether to loop the clip");

  protected final List<LXClipLane> mutableLanes = new ArrayList<LXClipLane>();
  public final List<LXClipLane> lanes = Collections.unmodifiableList(this.mutableLanes);

  public final BooleanParameter snapshotEnabled =
    new BooleanParameter("Snapshot", true)
    .setDescription("Whether snapshot recall is enabled for this clip");

  public final BooleanParameter snapshotTransitionEnabled =
    new BooleanParameter("Transition", true)
    .setDescription("Whether snapshot transition is enabled for this clip");

  public final BooleanParameter automationEnabled =
    new BooleanParameter("Automation", false)
    .setDescription("Whether automation playback is enabled for this clip");

  public final BooleanParameter customSnapshotTransition =
    new BooleanParameter("Custom Snapshot Transition")
    .setDescription("Whether to use custom snapshot transition settings for this clip");

  public final LXBus bus;
  public final LXClipSnapshot snapshot;

  private int index;
  private final boolean busListener;

  protected final LXParameterListener parameterRecorder = new LXParameterListener() {
    public void onParameterChanged(LXParameter p) {
      if (isRunning() && bus.arm.isOn()) {
        LXListenableNormalizedParameter parameter = (LXListenableNormalizedParameter) p;
        ParameterClipLane lane = getParameterLane(parameter, true);
        lane.appendEvent(new ParameterClipEvent(lane, parameter));
      }
    }
  };

  public LXClip(LX lx, LXBus bus, int index) {
    this(lx, bus, index, true);
  }

  protected LXClip(LX lx, LXBus bus, int index, boolean registerListener) {
    super(lx);
    this.label.setDescription("The name of this clip");
    this.bus = bus;
    this.index = index;
    this.busListener = registerListener;
    setParent(this.bus);
    addParameter("length", this.length);
    addParameter("loop", this.loop);
    addParameter("snapshotEnabled", this.snapshotEnabled);
    addParameter("snapshotTransitionEnabled", this.snapshotTransitionEnabled);
    addParameter("automationEnabled", this.automationEnabled);
    addParameter("customSnapshotTransition", this.customSnapshotTransition);

    addChild("snapshot", this.snapshot = new LXClipSnapshot(lx, this));

    for (LXEffect effect : bus.effects) {
      registerComponent(effect);
    }

    // This class is not always registered as a listener... in the case of LXChannelClip,
    // that parent class will take care of registering as a listener and this will avoid
    // having duplicated double-listeners
    if (registerListener) {
      bus.addListener(this);
    }
  }

  @Override
  public String getPath() {
    return "clip/" + (index + 1);
  }

  @Override
  public void onTrigger() {
    super.onTrigger();
    this.cursor = 0;
    if (this.snapshotEnabled.isOn()) {
      this.snapshot.recall();
    }
    if (this.bus.arm.isOn()) {
      this.automationEnabled.setValue(true);
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    this.snapshot.stopTransition();
  }

  @Override
  public void dispose() {
    for (LXEffect effect : bus.effects) {
      unregisterComponent(effect);
    }
    if (this.busListener) {
      this.bus.removeListener(this);
    }
    this.mutableLanes.clear();
    LX.dispose(this.snapshot);
    this.listeners.clear();
    super.dispose();
  }


  public double getLength() {
    return this.length.getValue();
  }

  private ParameterClipLane getParameterLane(LXNormalizedParameter parameter, boolean create) {
    for (LXClipLane lane : this.lanes) {
      if (lane instanceof ParameterClipLane) {
        if (((ParameterClipLane) lane).parameter == parameter) {
          return (ParameterClipLane) lane;
        }
      }
    }
    if (create) {
      ParameterClipLane lane = new ParameterClipLane(this, parameter);
      this.mutableLanes.add(lane);
      for (Listener listener : this.listeners) {
        listener.parameterLaneAdded(this, lane);
      }
      return lane;
    }
    return null;
  }

  public LXClip removeParameterLane(ParameterClipLane lane) {
    this.mutableLanes.remove(lane);
    for (Listener listener : this.listeners) {
      listener.parameterLaneRemoved(this, lane);
    }
    return this;
  }

  public LXClip addListener(Listener listener) {
    Objects.requireNonNull(listener, "May not add null LXClip.Listener");
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Already registered LXClip.Listener: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  public LXClip removeListener(Listener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot remove non-registered LXClip.Listener: " + listener);
    }
    this.listeners.remove(listener);
    return this;
  }

  public double getCursor() {
    return this.cursor;
  }

  public double getBasis() {
    double lengthValue = this.length.getValue();
    if (lengthValue > 0) {
      return this.cursor / lengthValue;
    }
    return 0;
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.running) {
      if (this.running.isOn()) {
        for (LXClip clip : this.bus.clips) {
          if (clip != null && clip != this) {
            clip.stop();
          }
        }
        if (this.bus.arm.isOn()) {
          // Start recording a new clip.
          // TODO(mcslee): toggle an overdub / replace recording mode
          this.cursor = 0;
          this.length.setValue(0);
          clearLanes();
          onStartRecording();
        }
      } else {
        // Finished recording
        if (this.bus.arm.isOn()) {
          this.length.setValue(this.cursor);
          this.bus.arm.setValue(false);
        }
      }
    }
  }

  protected void onStartRecording() {
    // Subclasses may override
  }

  private void clearLanes() {
    Iterator<LXClipLane> iter = this.mutableLanes.iterator();
    while (iter.hasNext()) {
      LXClipLane lane = iter.next();
      if (lane instanceof ParameterClipLane) {
        iter.remove();
        for (Listener listener : this.listeners) {
          listener.parameterLaneRemoved(this, (ParameterClipLane) lane);
        }
      } else {
        lane.clear();
      }
    }
  }

  protected void registerComponent(LXComponent component) {
    for (LXParameter p : component.getParameters()) {
      if (p instanceof LXListenableNormalizedParameter) {
        ((LXListenableNormalizedParameter) p).addListener(this.parameterRecorder);
      }
    }
    if (component instanceof LXLayeredComponent) {
      for (LXLayer layer : ((LXLayeredComponent) component).getLayers()) {
        registerComponent(layer);
      }
    }
  }

  protected void unregisterComponent(LXComponent component) {
    for (LXParameter p : component.getParameters()) {
      if (p instanceof LXListenableNormalizedParameter) {
        ((LXListenableNormalizedParameter) p).removeListener(this.parameterRecorder);
        ParameterClipLane lane = getParameterLane((LXNormalizedParameter) p, false);
        if (lane != null) {
          this.mutableLanes.remove(lane);
          for (Listener listener : this.listeners) {
            listener.parameterLaneRemoved(this, lane);
          }
        }
      }
    }
    if (component instanceof LXLayeredComponent) {
      for (LXLayer layer : ((LXLayeredComponent) component).getLayers()) {
        unregisterComponent(layer);
      }
    }
  }

  public int getIndex() {
    return this.index;
  }

  public LXClip setIndex(int index) {
    this.index = index;
    return this;
  }


  private void advanceCursor(double from, double to) {
    for (LXClipLane lane : this.lanes) {
      lane.advanceCursor(from, to);
    }
  }

  @Override
  protected void run(double deltaMs) {
    double nextCursor = this.cursor + deltaMs;
    if (this.bus.arm.isOn()) {
      // Recording mode... lane and event listeners will pick up and record
      // all the events. All we need to do here is update the clip length
      this.length.setValue(nextCursor);
      this.cursor = nextCursor;
    } else {
      boolean automationFinished = true;
      if (this.automationEnabled.isOn()) {
        double lengthValue = this.length.getValue();
        automationFinished = false;
        // TODO(mcslee): make this more efficient, keep track of our indices?
        advanceCursor(this.cursor, nextCursor);
        while (nextCursor > lengthValue) {
          if (!this.loop.isOn() || (lengthValue == 0)) {
            this.cursor = nextCursor = lengthValue;
            automationFinished = true;
            break;
          } else {
            nextCursor -= lengthValue;
            advanceCursor(0, nextCursor);
          }
        }
        this.cursor = nextCursor;
      }
      if (this.snapshotEnabled.isOn()) {
        this.snapshot.loop(deltaMs);
      }
      // Did we finish automation and snapshot playback, stop!
      if (automationFinished && !this.snapshot.isInTransition()) {
        stop();
      }
    }

  }

  @Override
  public void effectAdded(LXBus channel, LXEffect effect) {
    registerComponent(effect);
  }

  @Override
  public void effectRemoved(LXBus channel, LXEffect effect) {
    unregisterComponent(effect);
  }

  @Override
  public void effectMoved(LXBus channel, LXEffect effect) {}

  private static final String KEY_LANES = "parameterLanes";
  public static final String KEY_INDEX = "index";

  @Override
  public void load(LX lx, JsonObject obj) {
    clearLanes();
    if (obj.has(KEY_LANES)) {
      JsonArray lanesArr = obj.get(KEY_LANES).getAsJsonArray();
      for (JsonElement laneElement : lanesArr) {
        JsonObject laneObj = laneElement.getAsJsonObject();
        String laneType = laneObj.get(LXClipLane.KEY_LANE_TYPE).getAsString();
        loadLane(lx, laneType, laneObj);
      }
    }
    super.load(lx, obj);
  }

  protected void loadLane(LX lx, String laneType, JsonObject laneObj) {
    if (laneType.equals(LXClipLane.VALUE_LANE_TYPE_PARAMETER)) {
      LXParameter parameter;
      if (laneObj.has(LXComponent.KEY_PATH)) {
        String parameterPath = laneObj.get(KEY_PATH).getAsString();
        parameter = LXPath.getParameter(this.bus, parameterPath);
        if (parameter == null) {
          LX.error("No parameter found for saved parameter clip lane on bus " + this.bus + " at path: " + parameterPath);
          return;
        }
      } else {
        int componentId = laneObj.get(KEY_COMPONENT_ID).getAsInt();
        LXComponent component = lx.getProjectComponent(componentId);
        if (component == null) {
          LX.error("No component found for saved parameter clip lane on bus " + this.bus + " with id: " + componentId);
          return;
        }
        String parameterPath = laneObj.get(KEY_PARAMETER_PATH).getAsString();
        parameter = component.getParameter(parameterPath);
        if (parameter == null) {
          LX.error("No parameter found for saved parameter clip lane on component " + component + " at path: " + parameterPath);
          return;
        }
      }
      LXClipLane lane = getParameterLane((LXNormalizedParameter) parameter, true);
      lane.load(lx, laneObj);
    }
  }

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_INDEX, this.index);
    obj.add(KEY_LANES, LXSerializable.Utils.toArray(lx, this.lanes));
  }
}
