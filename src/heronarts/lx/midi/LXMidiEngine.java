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

package heronarts.lx.midi;

import heronarts.lx.LX;
import heronarts.lx.LXChannel;
import heronarts.lx.LXComponent;
import heronarts.lx.LXMappingEngine;
import heronarts.lx.LXSerializable;
import heronarts.lx.Tempo;
import heronarts.lx.midi.surface.LXMidiSurface;
import heronarts.lx.parameter.LXParameter;
import uk.co.xfactorylibrarians.coremidi4j.CoreMidiDeviceProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.ShortMessage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class LXMidiEngine implements LXSerializable {

  public enum Channel {
    CH_1, CH_2, CH_3, CH_4, CH_5, CH_6, CH_7, CH_8, CH_9, CH_10, CH_11, CH_12, CH_13, CH_14, CH_15, CH_16, OMNI;

    public boolean matches(ShortMessage message) {
      switch (this) {
      case OMNI: return true;
      default: return message.getChannel() == ordinal();
      }
    }

    public int getChannel() {
      switch (this) {
      case OMNI:
        return -1;
      default:
        return ordinal();
      }
    }

    @Override
    public String toString() {
      switch (this) {
      case OMNI:
        return "Omni";
      default:
        return "Ch." + (ordinal() + 1);
      }
    }
  }

  public interface MappingListener {
    public void mappingAdded(LXMidiEngine engine, LXMidiMapping mapping);
    public void mappingRemoved(LXMidiEngine engine, LXMidiMapping mapping);
  }

  private final List<LXMidiListener> listeners = new ArrayList<LXMidiListener>();
  private final List<MappingListener> mappingListeners = new ArrayList<MappingListener>();

  private final List<LXShortMessage> threadSafeInputQueue =
    Collections.synchronizedList(new ArrayList<LXShortMessage>());

  private final List<LXShortMessage> engineThreadInputQueue =
    new ArrayList<LXShortMessage>();

  private final List<LXMidiInput> mutableInputs = new ArrayList<LXMidiInput>();
  private final List<LXMidiOutput> mutableOutputs = new ArrayList<LXMidiOutput>();
  private final List<LXMidiSurface> mutableSurfaces = new ArrayList<LXMidiSurface>();

  public final List<LXMidiInput> inputs = Collections.unmodifiableList(this.mutableInputs);
  public final List<LXMidiOutput> outputs = Collections.unmodifiableList(this.mutableOutputs);
  public final List<LXMidiSurface> surfaces = Collections.unmodifiableList(this.mutableSurfaces);

  private final List<LXMidiMapping> mutableMappings = new ArrayList<LXMidiMapping>();
  public final List<LXMidiMapping> mappings = Collections.unmodifiableList(this.mutableMappings);

  private final LX lx;

  private class InitializationLock {
    private final List<Runnable> listeners = new ArrayList<Runnable>();
    private boolean ready = false;
  }

  private final InitializationLock initializationLock = new InitializationLock();

  public LXMidiEngine(LX lx) {
    this.lx = lx;
  }

  public void initialize() {
    new Thread() {
      @Override
      public void run() {
        // NOTE(mcslee): this can sometimes hang or be slow for unclear reasons...
        // do it in a separate thread so that we don't delay the whole application
        // starting up.
        // for (MidiDevice.Info deviceInfo : MidiSystem.getMidiDeviceInfo()) {
        try {
          for (MidiDevice.Info deviceInfo : CoreMidiDeviceProvider.getMidiDeviceInfo()) {
            try {
              MidiDevice device = MidiSystem.getMidiDevice(deviceInfo);
              if (device.getMaxTransmitters() != 0) {
                mutableInputs.add(new LXMidiInput(LXMidiEngine.this, device));
              }
              if (device.getMaxReceivers() != 0) {
                mutableOutputs.add(new LXMidiOutput(LXMidiEngine.this, device));
              }
            } catch (MidiUnavailableException mux) {
              mux.printStackTrace();
            }
          }
        } catch (Exception x) {
          System.err.println("Unexpected MIDI error, MIDI unavailable: " + x.getLocalizedMessage());
          x.printStackTrace();
        }
        for (LXMidiInput input : inputs) {
          LXMidiSurface surface = LXMidiSurface.get(lx, LXMidiEngine.this, input);
          if (surface != null) {
            mutableSurfaces.add(surface);
          }
        }

        lx.engine.addTask(new Runnable() {
          public void run() {
            synchronized (initializationLock) {
              initializationLock.ready = true;
              for (Runnable runnable : initializationLock.listeners) {
                runnable.run();
              }
              initializationLock.notifyAll();
            }
          }
        });
      }
    }.start();
  }

  public void waitUntilReady() {
    synchronized (this.initializationLock) {
      while (!this.initializationLock.ready) {
        try {
          this.initializationLock.wait();
        } catch (InterruptedException ix) {
          System.err.println(ix.getLocalizedMessage());
        }
      }
    }
  }

  public void whenReady(Runnable runnable) {
    synchronized (this.initializationLock) {
      if (this.initializationLock.ready) {
        runnable.run();
      } else {
        this.initializationLock.listeners.add(runnable);
      }
    }
  }

  public List<LXMidiInput> getInputs() {
    return this.inputs;
  }

  public List<LXMidiOutput> getOutputs() {
    return this.outputs;
  }

  public LXMidiInput matchInput(String name) {
    return matchInput(new String[] { name });
  }

  public LXMidiInput matchInput(String[] names) {
    return (LXMidiInput) matchDevice(this.mutableInputs, names);
  }

  public LXMidiOutput matchOutput(String name) {
    return matchOutput(new String[] { name });
  }

  public LXMidiOutput matchOutput(String[] names) {
    return (LXMidiOutput) matchDevice(this.mutableOutputs, names);
  }

  private LXMidiDevice matchDevice(List<? extends LXMidiDevice> devices, String[] names) {
    for (LXMidiDevice device : devices) {
      String deviceName = device.getName();
      for (String name : names) {
        if (deviceName.contains(name)) {
          return device;
        }
      }
    }
    return null;
  }

  public LXMidiEngine addListener(LXMidiListener listener) {
    this.listeners.add(listener);
    return this;
  }

  public LXMidiEngine removeListener(LXMidiListener listener) {
    this.listeners.remove(listener);
    return this;
  }

  public LXMidiEngine addMappingListener(MappingListener listener) {
    this.mappingListeners.add(listener);
    return this;
  }

  public LXMidiEngine removeMappingListener(MappingListener listener) {
    this.mappingListeners.remove(listener);
    return this;
  }

  void queueInputMessage(LXShortMessage message) {
    this.threadSafeInputQueue.add(message);
  }

  private void createMapping(LXShortMessage message) {
    // Is there a control parameter selected?
    LXParameter parameter = lx.engine.mapping.getControlTarget();
    if (parameter == null) {
      return;
    }

    // Is this a valid mapping type?
    if (!LXMidiMapping.isValidMessageType(message)) {
      return;
    }

    // Does this mapping already exist?
    for (LXMidiMapping mapping : this.mutableMappings) {
      if (mapping.parameter == parameter && mapping.matches(message)) {
        return;
      }
    }

    // Bada-boom, add it!
    addMapping(LXMidiMapping.create(this.lx, message, parameter));
  }

  private boolean applyMapping(LXShortMessage message) {
    boolean applied = false;
    for (LXMidiMapping mapping : this.mutableMappings) {
      if (mapping.matches(message)) {
        mapping.apply(message);
        applied = true;
      }
    }
    return applied;
  }

  private LXMidiEngine addMapping(LXMidiMapping mapping) {
    this.mutableMappings.add(mapping);
    for (MappingListener mappingListener : this.mappingListeners) {
      mappingListener.mappingAdded(this, mapping);
    }
    return this;
  }

  /**
   * Removes a midi mapping
   *
   * @param mapping The mapping to remove
   * @return this
   */
  public LXMidiEngine removeMapping(LXMidiMapping mapping) {
    this.mutableMappings.remove(mapping);
    for (MappingListener mappingListener : this.mappingListeners) {
      mappingListener.mappingRemoved(this, mapping);
    }
    return this;
  }

  /**
   * Called when a component is disposed. Remove any midi mappings
   * pointing to the now-nonexistent component.
   *
   * @param component Component to remove any midi mappings from
   * @return this
   */
  public LXMidiEngine removeMappings(LXComponent component) {
    Iterator<LXMidiMapping> iterator = this.mutableMappings.iterator();
    while (iterator.hasNext()) {
      LXMidiMapping mapping = iterator.next();
      if (mapping.parameter.getComponent() == component) {
        iterator.remove();
        for (MappingListener mappingListener : this.mappingListeners) {
          mappingListener.mappingRemoved(this, mapping);
        }
      }
    }
    return this;
  }

  /**
   * Invoked by the main engine to dispatch all midi messages on the
   * input queue.
   */
  public void dispatch() {
    this.engineThreadInputQueue.clear();
    synchronized (this.threadSafeInputQueue) {
      this.engineThreadInputQueue.addAll(this.threadSafeInputQueue);
      this.threadSafeInputQueue.clear();
    }
    for (LXShortMessage message : this.engineThreadInputQueue) {
      LXMidiInput input = message.getInput();
      input.dispatch(message);
      if (input.enabled.isOn()) {
        dispatch(message);
      }
    }
  }

  public void dispatch(LXShortMessage message) {
    LXMidiInput input = message.getInput();
    if (input != null) {
      if (input.controlEnabled.isOn()) {
        if (lx.engine.mapping.getMode() == LXMappingEngine.Mode.MIDI) {
          createMapping(message);
          return;
        }
        if (applyMapping(message)) {
          return;
        }
      }

      // Handle tempo sync messages
      if (message instanceof MidiBeat &&
          input.syncEnabled.isOn() &&
          this.lx.tempo.clockSource.getObject() == Tempo.ClockSource.MIDI) {
        MidiBeat beat = (MidiBeat) message;
        this.lx.tempo.trigger(((MidiBeat) message).getBeat());
        double period = beat.getPeriod();
        if (period != MidiBeat.PERIOD_UNKNOWN) {
          this.lx.tempo.setPeriod(period);
        }
      }
    }

    for (LXMidiListener listener : this.listeners) {
      message.dispatch(listener);
    }

    if (input == null || input.channelEnabled.isOn()) {
      for (LXChannel channel : this.lx.engine.getChannels()) {
        if (channel.midiMonitor.isOn() && channel.midiChannel.getEnum().matches(message)) {
          channel.midiMessage(message);
        }
      }
    }
  }

  private static final String KEY_INPUTS = "inputs";
  private static final String KEY_SURFACES = "surfaces";
  private static final String KEY_MAPPINGS = "mapping";

  private final List<JsonObject> rememberMidiInputs = new ArrayList<JsonObject>();
  private final List<JsonObject> rememberMidiSurfaces = new ArrayList<JsonObject>();

  @Override
  public void save(LX lx, JsonObject object) {
    waitUntilReady();
    JsonArray inputs = new JsonArray();
    for (LXMidiInput input : this.mutableInputs) {
      if (input.enabled.isOn()) {
        inputs.add(LXSerializable.Utils.toObject(lx, input));
      }
    }
    for (JsonObject remembered : this.rememberMidiInputs) {
      inputs.add(remembered);
    }
    JsonArray surfaces = new JsonArray();
    for (LXMidiSurface surface : this.mutableSurfaces) {
      if (surface.enabled.isOn()) {
        surfaces.add(LXSerializable.Utils.toObject(lx, surface));
      }
    }
    for (JsonObject remembered : this.rememberMidiSurfaces) {
      surfaces.add(remembered);
    }

    object.add(KEY_INPUTS, inputs);
    object.add(KEY_SURFACES, surfaces);
    object.add(KEY_MAPPINGS, LXSerializable.Utils.toArray(lx, this.mutableMappings));
  }

  @Override
  public void load(final LX lx, final JsonObject object) {
    this.rememberMidiInputs.clear();
    this.mutableMappings.clear();
    if (object.has(KEY_MAPPINGS)) {
      JsonArray mappings = object.getAsJsonArray(KEY_MAPPINGS);
      for (JsonElement element : mappings) {
        try {
          addMapping(LXMidiMapping.create(this.lx, element.getAsJsonObject()));
        } catch (Exception x) {
          System.err.println("Could not load MIDI mapping: " + element.toString());
        }
      }
    }
    whenReady(new Runnable() {
      public void run() {
        if (object.has(KEY_INPUTS)) {
          JsonArray inputs = object.getAsJsonArray(KEY_INPUTS);
          if (inputs.size() > 0) {
            for (JsonElement element : inputs) {
              JsonObject inputObj = element.getAsJsonObject();
              String inputName = inputObj.get(LXMidiInput.KEY_NAME).getAsString();
              boolean found = false;
              for (LXMidiInput input : mutableInputs) {
                if (inputName.equals(input.getName())) {
                  found = true;
                  input.load(lx, inputObj);
                  break;
                }
              }
              if (!found) {
                rememberMidiInputs.add(inputObj);
              }
            }
          }
        }
        if (object.has(KEY_SURFACES)) {
          JsonArray surfaces = object.getAsJsonArray(KEY_SURFACES);
          if (surfaces.size() > 0) {
            for (JsonElement element : surfaces) {
              JsonObject surfaceObj = element.getAsJsonObject();
              String surfaceDescription = surfaceObj.get(LXMidiSurface.KEY_DESCRIPTION).getAsString();
              boolean found = false;
              for (LXMidiSurface surface : mutableSurfaces) {
                if (surfaceDescription.equals(surface.getDescription())) {
                  found = true;
                  surface.enabled.setValue(true);
                  break;
                }
              }
              if (!found) {
                rememberMidiSurfaces.add(surfaceObj);
              }
            }
          }
        }
      }
    });
  }

}
