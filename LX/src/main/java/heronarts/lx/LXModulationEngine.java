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
 * ##library.name##
 * ##library.sentence##
 * ##library.url##
 *
 * @author      ##author##
 * @modified    ##date##
 * @version     ##library.prettyVersion## (##library.version##)
 */

package heronarts.lx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.modulator.LXModulator;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXCompoundModulation;
import heronarts.lx.parameter.LXTriggerModulation;

public class LXModulationEngine extends LXModulatorComponent implements LXOscComponent {

  private final LX lx;
  private final LXComponent component;

  public interface Listener {
    public void modulatorAdded(LXModulationEngine engine, LXModulator modulator);
    public void modulatorRemoved(LXModulationEngine engine, LXModulator modulator);

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

  public LXModulationEngine(LX lx, LXComponent component) {
    super(lx);
    this.lx = lx;
    this.component = component;
    setParent(component);
  }

  public boolean isValidTarget(CompoundParameter target) {
    if (this.component instanceof LXEngine) {
      return true;
    }
    LXComponent parent = target.getComponent();
    while (parent != null) {
      if (parent == this.component) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  public String getOscAddress() {
    return ((LXOscComponent) this.component).getOscAddress() + "/modulation";
  }

  public LXModulationEngine addListener(Listener listener) {
    this.listeners.add(listener);
    return this;
  }

  public LXModulationEngine removeListener(Listener listener) {
    this.listeners.remove(listener);
    return this;
  }

  public LXModulationEngine addModulation(LXCompoundModulation modulation) {
    if (this.mutableModulations.contains(modulation)) {
      throw new IllegalStateException("Cannot add same modulation twice");
    }
    ((LXComponent) modulation).setParent(this);
    this.mutableModulations.add(modulation);
    for (Listener listener : this.listeners) {
      listener.modulationAdded(this, modulation);
    }
    return this;
  }

  public LXModulationEngine removeModulation(LXCompoundModulation modulation) {
    this.mutableModulations.remove(modulation);
    for (Listener listener : this.listeners) {
      listener.modulationRemoved(this, modulation);
    }
    modulation.dispose();
    return this;
  }

  public LXModulationEngine addTrigger(LXTriggerModulation trigger) {
    if (this.mutableTriggers.contains(trigger)) {
      throw new IllegalStateException("Cannot add same trigger twice");
    }
    ((LXComponent) trigger).setParent(this);
    this.mutableTriggers.add(trigger);
    for (Listener listener : this.listeners) {
      listener.triggerAdded(this, trigger);
    }
    return this;
  }

  public LXModulationEngine removeTrigger(LXTriggerModulation trigger) {
    this.mutableTriggers.remove(trigger);
    for (Listener listener : this.listeners) {
      listener.triggerRemoved(this, trigger);
    }
    trigger.dispose();
    return this;
  }

  public LXModulationEngine removeModulations(LXComponent component) {
    Iterator<LXCompoundModulation> iterator = this.mutableModulations.iterator();
    while (iterator.hasNext()) {
      LXCompoundModulation modulation = iterator.next();
      if (modulation.source == component || modulation.source.getComponent() == component || modulation.target.getComponent() == component) {
        iterator.remove();
        for (Listener listener : this.listeners) {
          listener.modulationRemoved(this, modulation);
        }
        modulation.dispose();
      }
    }
    Iterator<LXTriggerModulation> triggerIterator = this.mutableTriggers.iterator();
    while (triggerIterator.hasNext()) {
      LXTriggerModulation trigger = triggerIterator.next();
      if (trigger.source.getComponent() == component || trigger.target.getComponent() == component) {
        triggerIterator.remove();
        for (Listener listener : this.listeners) {
          listener.triggerRemoved(this, trigger);
        }
        trigger.dispose();
      }
    }
    return this;
  }

  @Override
  public LXModulator addModulator(LXModulator modulator) {
    super.addModulator(modulator);
    for (Listener listener : this.listeners) {
      listener.modulatorAdded(this, modulator);
    }
    return modulator;
  }

  @Override
  public LXModulator removeModulator(LXModulator modulator) {
    removeModulations(modulator);
    super.removeModulator(modulator);
    for (Listener listener : this.listeners) {
      listener.modulatorRemoved(this, modulator);
    }
    return modulator;
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
    return "Mod";
  }

  private static final String KEY_MODULATORS = "modulators";
  private static final String KEY_MODULATIONS = "modulations";
  private static final String KEY_TRIGGERS = "triggers";

  @Override
  public void save(LX lx, JsonObject obj) {
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

    if (obj.has(KEY_MODULATORS)) {
      JsonArray modulatorArr = obj.getAsJsonArray(KEY_MODULATORS);
      for (JsonElement modulatorElement : modulatorArr) {
        JsonObject modulatorObj = modulatorElement.getAsJsonObject();
        String modulatorClass = modulatorObj.get(KEY_CLASS).getAsString();
        LXModulator modulator = this.lx.instantiateModulator(modulatorClass);
        if (modulator == null) {
          System.err.println("Could not instantiate modulator: " + modulatorClass);
        } else {
          addModulator(modulator);
          modulator.load(lx, modulatorObj);
        }
      }
    }
    if (obj.has(KEY_MODULATIONS)) {
      JsonArray modulationArr = obj.getAsJsonArray(KEY_MODULATIONS);
      for (JsonElement modulationElement : modulationArr) {
        JsonObject modulationObj = modulationElement.getAsJsonObject();
        try {
          LXCompoundModulation modulation = new LXCompoundModulation(this.lx, modulationObj);
          addModulation(modulation);
          modulation.load(lx, modulationObj);
        } catch (Exception x) {
          System.err.println(x.getLocalizedMessage());
        }
      }
    }
    if (obj.has(KEY_TRIGGERS)) {
      JsonArray triggerArr = obj.getAsJsonArray(KEY_TRIGGERS);
      for (JsonElement triggerElement : triggerArr) {
        JsonObject triggerObj = triggerElement.getAsJsonObject();
        try {
          LXTriggerModulation trigger = new LXTriggerModulation(this.lx, triggerObj);
          addTrigger(trigger);
          trigger.load(lx, triggerObj);
        } catch (Exception x) {
          System.err.println(x.getLocalizedMessage());
        }
      }
    }
  }

}
