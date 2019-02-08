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
import heronarts.lx.LXChannelBus;
import heronarts.lx.LXComponent;
import heronarts.lx.LXMappingEngine;
import heronarts.lx.LXSerializable;
import heronarts.lx.Tempo;
import heronarts.lx.command.LXCommand;
import heronarts.lx.midi.surface.APC40Mk2;
import heronarts.lx.midi.surface.LXMidiSurface;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXParameter;
import uk.co.xfactorylibrarians.coremidi4j.CoreMidiDeviceProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.ShortMessage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class LXMidiEngine extends LXComponent implements LXOscComponent {

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

  private final Map<String, Class<? extends LXMidiSurface>> registeredSurfaces =
    new HashMap<String, Class<? extends LXMidiSurface>>();

  private class InitializationLock {
    private final List<Runnable> whenReady = new ArrayList<Runnable>();
    private boolean ready = false;
  }

  private final InitializationLock initializationLock = new InitializationLock();

  public final BooleanParameter computerKeyboardEnabled =
    new BooleanParameter("Computer MIDI Keyboard", false)
    .setDescription("Whether the computer keyboard plays notes to MIDI tracks");

  public LXMidiEngine(LX lx) {
    super(lx);
    this.registeredSurfaces.put(APC40Mk2.DEVICE_NAME, APC40Mk2.class);
    addParameter("computerKeyboardEnabled", this.computerKeyboardEnabled);
  }

  public LXMidiEngine registerSurface(String deviceName, Class<? extends LXMidiSurface> surfaceClass) {
    this.lx.checkRegistration();
    if (this.registeredSurfaces.containsKey(deviceName)) {
      throw new IllegalStateException("Existing midi device name " + deviceName + " cannot be remapped to " + surfaceClass);
    }
    this.registeredSurfaces.put(deviceName, surfaceClass);

    // NOTE: the MIDI initialize() thread has been kicked off at this point. We
    // might not get in until after it has already attempted to initialize MIDI surfaces
    // in which case we need to check again for this newly registered surface
    whenReady(new Runnable() {
      public void run() {
        checkForSurface(deviceName);
      }
    });
    return this;
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

        // Instantiate midi surfaces
        for (LXMidiInput input : inputs) {
          instantiateSurface(input);
        }

        // Notify anything blocked on waitUntilReady()
        synchronized (initializationLock) {
          initializationLock.ready = true;
          initializationLock.notifyAll();
        }

        // Schedule engine thread to perform any blocked whenReady tasks
        lx.engine.addTask(new Runnable() {
          public void run() {
            for (Runnable runnable : initializationLock.whenReady) {
              runnable.run();
            }
          }
        });
      }
    }.start();
  }

  private void checkForSurface(String deviceName) {
    // Has it already been made? Then we're good
    LXMidiSurface surface = findSurface(deviceName);
    if (surface != null) {
      return;
    }
    // See if this input even exists
    LXMidiInput input = findInput(deviceName);
    if (input != null) {
      instantiateSurface(input);
    }
  }

  private LXMidiSurface instantiateSurface(LXMidiInput input) {
    Class<? extends LXMidiSurface> surfaceClass = this.registeredSurfaces.get(input.getName());
    if (surfaceClass == null) {
      return null;
    }
    try {
      LXMidiSurface surface = surfaceClass.getConstructor(LX.class, LXMidiInput.class, LXMidiOutput.class).newInstance(this.lx, input, findOutput(input));
      this.mutableSurfaces.add(surface);
    } catch (Exception x) {
      System.err.println("Could not instantiate midi surface class: " + surfaceClass);
      x.printStackTrace();
    }
    return null;
  }

  /**
   * This code blocks the current thread until the MIDI engine is totally ready. This
   * should only really be called from the engine thread and is currently private because
   * it is dangerous. It's currently used in save() and nowhere else.
   */
  private void waitUntilReady() {
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

  /**
   * Executes the given code only after the MIDI engine initialization is done.
   * If it's done, then this will be run immediately on the thread that called it.
   * If it is not done, then this will be run later on the LX engine thread in the
   * sequential order of all calls made to whenReady
   *
   * @param runnable Code to run when MIDI engine is ready
   */
  public void whenReady(Runnable runnable) {
    synchronized (this.initializationLock) {
      if (this.initializationLock.ready) {
        runnable.run();
      } else {
        this.initializationLock.whenReady.add(runnable);
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
    return matchDevice(this.mutableInputs, names);
  }

  public LXMidiOutput matchOutput(String name) {
    return matchOutput(new String[] { name });
  }

  public LXMidiOutput matchOutput(String[] names) {
    return matchDevice(this.mutableOutputs, names);
  }

  public LXMidiSurface findSurface(LXMidiInput input) {
    for (LXMidiSurface surface : this.surfaces) {
      if (surface.getInput() == input) {
        return surface;
      }
    }
    return null;
  }

  public LXMidiSurface findSurface(String name) {
    for (LXMidiSurface surface : this.surfaces) {
      if (surface.getName().equals(name)) {
        return surface;
      }
    }
    return null;
  }

  private <T extends LXMidiDevice> T matchDevice(List<T> devices, String[] names) {
    for (T device : devices) {
      String deviceName = device.getName();
      for (String name : names) {
        if (deviceName.contains(name)) {
          return device;
        }
      }
    }
    return null;
  }

  private LXMidiOutput findOutput(LXMidiInput input) {
    return findDevice(this.outputs, input.getName());
  }

  public LXMidiOutput findOutput(String name) {
    return findDevice(this.outputs, name);
  }

  public LXMidiInput findInput(String name) {
    return findDevice(this.inputs, name);
  }

  private <T extends LXMidiDevice> T findDevice(List<T> devices, String name) {
    for (T device : devices) {
      if (device.getName().equals(name)) {
        return device;
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

  private static final String PATH_NOTE = "note";
  private static final String PATH_CC = "cc";
  private static final String PATH_PITCHBEND = "pitchbend";

  @Override
  public boolean handleOscMessage(OscMessage message, String[] parts, int index) {
    try {
      String path = parts[index];
      if (path.equals(PATH_NOTE)) {
        int pitch = message.getInt();
        int velocity = message.getInt();
        int channel = message.getInt();
        dispatch(new MidiNoteOn(channel, pitch, velocity));
        return true;
      }
      if (path.equals(PATH_CC)) {
        int value = message.getInt();
        int cc = message.getInt();
        int channel = message.getInt();
        dispatch(new MidiControlChange(channel, cc, value));
        return true;
      }
      if (parts[index].equals(PATH_PITCHBEND)) {
        int msb = message.getInt();
        int channel = message.getInt();
        dispatch(new MidiPitchBend(channel, msb));
        return true;
      }
    } catch (InvalidMidiDataException imdx) {
      System.err.println("[OSC] Invalid MIDI message: " + imdx.getLocalizedMessage());
      return false;
    }
    return super.handleOscMessage(message, parts, index);
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
    this.lx.command.perform(new LXCommand.Midi.AddMapping(message, parameter));
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

  public LXMidiEngine addMapping(LXMidiMapping mapping) {
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

  public List<LXMidiMapping> findMappings(LXComponent component) {
    List<LXMidiMapping> found = null;
    for (LXMidiMapping mapping : this.mappings) {
      if (component.contains(mapping.parameter)) {
        if (found == null) {
          found = new ArrayList<LXMidiMapping>();
        }
        found.add(mapping);
      }
    }
    return found;
  }

  /**
   * Called when a component is disposed. Remove any midi mappings
   * pointing to the now-nonexistent component.
   *
   * @param component Component to remove any midi mappings from
   * @return this
   */
  public LXMidiEngine removeMappings(LXComponent component) {
    List<LXMidiMapping> remove = findMappings(component);
    if (remove != null) {
      for (LXMidiMapping mapping : remove) {
        removeMapping(mapping);
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
          this.lx.engine.tempo.clockSource.getObject() == Tempo.ClockSource.MIDI) {
        MidiBeat beat = (MidiBeat) message;
        this.lx.engine.tempo.trigger(((MidiBeat) message).getBeat());
        double period = beat.getPeriod();
        if (period != MidiBeat.PERIOD_UNKNOWN) {
          this.lx.engine.tempo.setPeriod(period);
        }
      }
    }

    for (LXMidiListener listener : this.listeners) {
      message.dispatch(listener);
    }

    if (input == null || input.channelEnabled.isOn()) {
      for (LXChannelBus channelBus : this.lx.engine.channels) {
        if (channelBus instanceof LXChannel) {
          LXChannel channel = (LXChannel) channelBus;
          if (channel.midiMonitor.isOn() && channel.midiChannel.getEnum().matches(message)) {
            channel.midiMessage(message);
          }
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
    super.save(lx, object);
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
    this.rememberMidiSurfaces.clear();
    this.mutableMappings.clear();
    super.load(lx, object);

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
              LXMidiInput input = findInput(inputName);
              if (input != null) {
                input.load(lx, inputObj);
              } else {
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
              String surfaceName = surfaceObj.get(LXMidiSurface.KEY_NAME).getAsString();
              LXMidiSurface surface = findSurface(surfaceName);
              if (surface != null) {
                surface.enabled.setValue(true);
              } else {
                rememberMidiSurfaces.add(surfaceObj);
              }
            }
          }
        }
      }
    });
  }

}
