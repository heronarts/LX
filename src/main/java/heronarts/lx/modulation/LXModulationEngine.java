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

package heronarts.lx.modulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXEngine;
import heronarts.lx.LXModulatorComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.parameter.CompoundParameter;

public class LXModulationEngine extends LXModulatorComponent implements LXOscComponent {

  public interface Listener {
    public void modulatorAdded(LXModulationEngine engine, LXModulator modulator);
    public void modulatorRemoved(LXModulationEngine engine, LXModulator modulator);
    public void modulatorMoved(LXModulationEngine engine, LXModulator modulator);

    public void modulationAdded(LXModulationEngine engine, LXCompoundModulation modulation);
    public void modulationRemoved(LXModulationEngine engine, LXCompoundModulation modulation);

    public void triggerAdded(LXModulationEngine engine, LXTriggerModulation modulation);
    public void triggerRemoved(LXModulationEngine engine, LXTriggerModulation modulation);
  }

  private final List<Listener> listeners = new ArrayList<Listener>();

  private final List<LXCompoundModulation> mutableModulations = new ArrayList<LXCompoundModulation>();
  public final List<LXCompoundModulation> modulations = Collections.unmodifiableList(this.mutableModulations);

  private final List<LXTriggerModulation> mutableTriggers = new ArrayList<LXTriggerModulation>();
  public final List<LXTriggerModulation> triggers = Collections.unmodifiableList(this.mutableTriggers);

  public LXModulationEngine(LX lx) {
    super(lx, "Modulation");
    addArray("modulation", this.modulations);
    addArray("trigger", this.triggers);
  }

  public boolean isValidTarget(CompoundParameter target) {
    LXComponent parent = getParent();
    if (parent instanceof LXEngine) {
      return true;
    }
    LXComponent targetComponent = target.getParent();
    while (targetComponent != null) {
      if (targetComponent == parent) {
        return true;
      }
      targetComponent = targetComponent.getParent();
    }
    return false;
  }

  @Override
  public boolean handleOscMessage(OscMessage message, String[] parts, int index) {
    String path = parts[index];
    for (LXModulator modulator : this.modulators) {
      if (path.equals(modulator.getOscPath())) {
        return modulator.handleOscMessage(message, parts, index+1);
      }
    }
    return super.handleOscMessage(message, parts, index);
  }

  public LXModulationEngine addListener(Listener listener) {
    Objects.requireNonNull(listener, "May not add null LXModulationEngine.Listener");
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate LXModulationEngine.Listener: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  public LXModulationEngine removeListener(Listener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot remove non-existent LXModulationEngine.Listener: " + listener);
    }
    this.listeners.remove(listener);
    return this;
  }

  public LXModulationEngine addModulation(LXCompoundModulation modulation) {
    if (this.mutableModulations.contains(modulation)) {
      throw new IllegalStateException("Cannot add duplicate LXCompoundModulation: " + modulation);
    }
    this.mutableModulations.add(modulation);
    _reindex(this.modulations);
    for (Listener listener : this.listeners) {
      listener.modulationAdded(this, modulation);
    }
    return this;
  }

  public LXModulationEngine removeModulation(LXCompoundModulation modulation) {
    if (!this.mutableModulations.contains(modulation)) {
      throw new IllegalStateException("Cannot remove non-registered LXCompoundModulation: " + modulation);
    }
    this.mutableModulations.remove(modulation);
    for (Listener listener : this.listeners) {
      listener.modulationRemoved(this, modulation);
    }
    _reindex(this.modulations);
    modulation.dispose();
    return this;
  }

  public LXModulationEngine addTrigger(LXTriggerModulation trigger) {
    if (this.mutableTriggers.contains(trigger)) {
      throw new IllegalStateException("Cannot add duplicate LXTriggerModulation: " + trigger);
    }
    this.mutableTriggers.add(trigger);
    _reindex(this.triggers);
    for (Listener listener : this.listeners) {
      listener.triggerAdded(this, trigger);
    }
    return this;
  }

  public LXModulationEngine removeTrigger(LXTriggerModulation trigger) {
    if (!this.mutableTriggers.contains(trigger)) {
      throw new IllegalStateException("Cannot remove non-registered LXTriggerModulation: " + trigger);
    }
    this.mutableTriggers.remove(trigger);
    _reindex(this.triggers);
    for (Listener listener : this.listeners) {
      listener.triggerRemoved(this, trigger);
    }
    trigger.dispose();
    return this;
  }

  private void _reindex(List<? extends LXParameterModulation> modulations) {
    int i = 0;
    for (LXParameterModulation modulation : modulations) {
      modulation.setIndex(i++);
    }
  }

  /**
   * Compiles all modulations that act upon any parameter or subcomponent of the given
   * component, whether as source or target.
   *
   * @param <T> type of parameter modulation, could be compound or trigger
   * @param component Component
   * @param modulations List of modulations that we're checking within
   * @return All modulations acting in any way upon this component or its children
   */
  public <T extends LXParameterModulation> List<T> findModulations(LXComponent component, List<T> modulations) {
    List<T> found = null;
    for (T modulation : modulations) {
      if (component.contains(modulation.source) || component.contains(modulation.target)) {
        if (found == null) {
          found = new ArrayList<T>();
        }
        found.add(modulation);
      }
    }
    return found;
  }

  public LXModulationEngine removeModulations(LXComponent component) {
    List<LXCompoundModulation> compounds = findModulations(component, this.modulations);
    if (compounds != null) {
      for (LXCompoundModulation compound : compounds) {
        removeModulation(compound);
      }
    }
    List<LXTriggerModulation> triggers = findModulations(component, this.triggers);
    if (triggers != null) {
      for (LXTriggerModulation trigger : triggers) {
        removeTrigger(trigger);
      }
    }
    return this;
  }

  @Override
  public <T extends LXModulator> T addModulator(T modulator, int index) {
    super.addModulator(modulator, index);
    for (Listener listener : this.listeners) {
      listener.modulatorAdded(this, modulator);
    }
    return modulator;
  }

  @Override
  public <T extends LXModulator> T removeModulator(T modulator) {
    // NOTE(mcslee): this may not be strictly necessary? The dispose() call in
    // super.removeModulator is probably going to do it again...
    removeModulations(modulator);
    super.removeModulator(modulator);
    for (Listener listener : this.listeners) {
      listener.modulatorRemoved(this, modulator);
    }
    return modulator;
  }

  @Override
  public <T extends LXModulator> T moveModulator(T modulator, int index) {
    super.moveModulator(modulator, index);
    for (Listener listener : this.listeners) {
      listener.modulatorMoved(this, modulator);
    }
    return modulator;
  }

  public int getModulatorCount(Class<? extends LXModulator> cls) {
    int count = 0;
    for (LXModulator modulator : this.modulators) {
      if (cls.isAssignableFrom(modulator.getClass())) {
        ++count;
      }
    }
    return count;
  }

  @Override
  public void dispose() {
    for (LXCompoundModulation modulation : this.mutableModulations) {
      modulation.dispose();
    }
    this.mutableModulations.clear();
    super.dispose();
  }

  @Override
  public String getLabel() {
    return "Modulation";
  }

  private static final String KEY_MODULATORS = "modulators";
  private static final String KEY_MODULATIONS = "modulations";
  private static final String KEY_TRIGGERS = "triggers";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_MODULATORS, LXSerializable.Utils.toArray(lx, this.modulators));
    obj.add(KEY_MODULATIONS, LXSerializable.Utils.toArray(lx, this.modulations));
    obj.add(KEY_TRIGGERS, LXSerializable.Utils.toArray(lx, this.triggers));
  }

  public void clear() {
    for (int i = this.modulators.size() - 1; i >= 0; --i) {
      removeModulator(this.modulators.get(i));
    }
    for (int i = this.modulations.size() - 1; i >= 0; --i) {
      removeModulation(this.modulations.get(i));
    }
    for (int i = this.triggers.size() - 1; i >= 0; --i) {
      removeTrigger(this.triggers.get(i));
    }
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    // Remove everything first
    clear();

    super.load(lx, obj);

    if (obj.has(KEY_MODULATORS)) {
      JsonArray modulatorArr = obj.getAsJsonArray(KEY_MODULATORS);
      for (JsonElement modulatorElement : modulatorArr) {
        JsonObject modulatorObj = modulatorElement.getAsJsonObject();
        String modulatorClass = modulatorObj.get(KEY_CLASS).getAsString();
        try {
          LXModulator modulator = this.lx.instantiateModulator(modulatorClass);
          addModulator(modulator);
          modulator.load(lx, modulatorObj);
        } catch (LX.InstantiationException x) {
          LX.error(x, "Could not instantiate modulator: " + modulatorObj.toString());
        }
      }
    }
    if (obj.has(KEY_MODULATIONS)) {
      JsonArray modulationArr = obj.getAsJsonArray(KEY_MODULATIONS);
      for (JsonElement modulationElement : modulationArr) {
        JsonObject modulationObj = modulationElement.getAsJsonObject();
        try {
          LXCompoundModulation modulation = new LXCompoundModulation(this.lx, this, modulationObj);
          addModulation(modulation);
          modulation.load(lx, modulationObj);
        } catch (Exception x) {
          LX.error(x, "Could not load modulation " + modulationObj.toString());
        }
      }
    }
    if (obj.has(KEY_TRIGGERS)) {
      JsonArray triggerArr = obj.getAsJsonArray(KEY_TRIGGERS);
      for (JsonElement triggerElement : triggerArr) {
        JsonObject triggerObj = triggerElement.getAsJsonObject();
        try {
          LXTriggerModulation trigger = new LXTriggerModulation(this.lx, this, triggerObj);
          addTrigger(trigger);
          trigger.load(lx, triggerObj);
        } catch (Exception x) {
          LX.error(x, "Could not load trigger mapping " + triggerObj.toString());
        }
      }
    }
  }

}
