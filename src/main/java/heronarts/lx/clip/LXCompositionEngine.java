/**
 * Copyright 2025- Justin K. Belcher, Heron Arts LLC
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
 * @author Justin K. Belcher <justin@jkb.studio>
 */

package heronarts.lx.clip;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.MutableParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LXCompositionEngine extends LXComponent implements LXOscComponent, LXClipContainer {

  public interface Listener {
    public void compositionChanged(LXComposition composition);
  }

  private final List<Listener> listeners = new ArrayList<>();

  // TODO: allow a list of compositions, not just one
  private LXComposition composition;

  public final BooleanParameter clipExpanded =
    new BooleanParameter("Clip", true)
      .setMode(BooleanParameter.Mode.TOGGLE)
      .setDescription("Toggle Clip visibility in the alt window");

  public final BooleanParameter arm =
    new BooleanParameter("Arm")
      .setDescription("Arms the composition for recording. If the Start Transport With Record preference is enabled, recording will start immediately.");

  public final FocusedClipParameter focusedClip = new FocusedClipParameter();

  public LXCompositionEngine(LX lx) {
    super(lx, "Composition");

    addParameter("clipExpanded", this.clipExpanded);
    addParameter("focusedClip", this.focusedClip);

    this.arm.addListener(this::armChanged);
  }

  public void initialize() {
    this.composition = new LXComposition(this.lx, this);
  }

  public LXComposition getComposition() {
    return this.composition;
  }

  private void armChanged(LXParameter p) {
    // If "Start Transport With Record" preference is enabled, then start recording when arm is pressed.
    // TODO: update preference to project-specific location?
    // if (lx.preferences.startTransportWithRecord.isOn()) {
      if (this.arm.isOn() && !this.composition.isRunning()) {
        if (this.composition.hasContent()) {
          // Existing composition: start recording from current cursor position
          this.composition.launchAutomationFromCursor();
        } else {
          // New/empty composition: start from the beginning
          this.composition.launch();
        }
      }
    // }
  }

  public void loop(double deltaMs) {
    if (this.composition != null) {
      this.composition.loop(deltaMs);
    }
  }

  public LXClip getFocusedClip() {
    return this.focusedClip.getClip();
  }

  public LXCompositionEngine setFocusedClip(LXClip clip) {
    this.focusedClip.setClip(clip);
    return this;
  }

  // LXClipContainer

  @Override
  public BooleanParameter getArmParameter() {
    return this.arm;
  }

  // Listeners

  public final void addListener(Listener listener) {
    Objects.requireNonNull(listener, "May not add null LXCompositionEngine.Listener");
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("May not add duplicate LXCompositionEngine.Listener: " + listener);
    }
    this.listeners.add(listener);
  }

  public final void removeListener(Listener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("May not remove non-registered LXCompositionEngine.Listener: " + listener);
    }
    this.listeners.remove(listener);
  }

  private void notifyCompositionChanged() {
    for (Listener listener : this.listeners) {
      listener.compositionChanged(this.composition);
    }
    lx.engine.audio.timeline.onCompositionChanged(this.composition);
  }

  public void clear() {
    // This may reference a list eventually...
    if (this.composition != null) {
      final LXComposition prior = this.composition;
      this.composition = null;
      notifyCompositionChanged();
      LX.dispose(prior);
    }
  }

  // Disposal

  @Override
  public void dispose() {
    clear();
    this.listeners.forEach(listener -> LX.warning("Stranded LXCompositionEngine.Listener: " + listener));
    this.listeners.clear();
    super.dispose();
  }

  // Serialization

  private static final String KEY_COMPOSITIONS = "compositions";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);;
    JsonArray clipsArr = new JsonArray();
    // There's only one composition for now...
    clipsArr.add(LXSerializable.Utils.toObject(lx, this.composition));
    obj.add(KEY_COMPOSITIONS, clipsArr);
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    clear();

    super.load(lx, obj);

    if (obj.has(KEY_COMPOSITIONS)) {
      JsonArray compArr = obj.get(KEY_COMPOSITIONS).getAsJsonArray();
      for (JsonElement compElem : compArr) {
        JsonObject compObj = compElem.getAsJsonObject();
        LXComposition composition = new LXComposition(lx, this);
        composition.load(lx, compObj);
        // There's only one composition for now...
        this.composition = composition;
        break;
      }
    }

    if (this.composition == null) {
      // New project or no composition was saved
      this.composition = new LXComposition(lx, this);
    }

    notifyCompositionChanged();
  }

  public static class FocusedClipParameter extends MutableParameter {

    private LXClip clip = null;

    private FocusedClipParameter() {
      super("Focused Clip");
      setDescription("Parameter which indicate the globally focused clip");
    }

    public FocusedClipParameter setClip(LXClip clip) {
      if (this.clip != clip) {
        this.clip = clip;
        bang();
      }
      return this;
    }

    public LXClip getClip() {
      return this.clip;
    }

  }
}
