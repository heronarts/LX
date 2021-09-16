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
import heronarts.lx.LXComponent;
import heronarts.lx.LXMappingEngine;
import heronarts.lx.LXSerializable;
import heronarts.lx.Tempo;
import heronarts.lx.command.LXCommand;
import heronarts.lx.midi.surface.APC40;
import heronarts.lx.midi.surface.APC40Mk2;
import heronarts.lx.midi.surface.DJM900nxs2;
import heronarts.lx.midi.surface.LXMidiSurface;
import heronarts.lx.midi.surface.MidiFighterTwister;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXParameter;
import uk.co.xfactorylibrarians.coremidi4j.CoreMidiDeviceInfo;
import uk.co.xfactorylibrarians.coremidi4j.CoreMidiDeviceProvider;
import uk.co.xfactorylibrarians.coremidi4j.CoreMidiException;
import uk.co.xfactorylibrarians.coremidi4j.CoreMidiNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.ShortMessage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class LXMidiEngine extends LXComponent implements LXOscComponent {

  private static final String COREMIDI4J_HEADER = "CoreMIDI4J - ";

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

  public interface DeviceListener {
    public void inputAdded(LXMidiEngine engine, LXMidiInput input);
    public void outputAdded(LXMidiEngine engine, LXMidiOutput output);
    public void surfaceAdded(LXMidiEngine engine, LXMidiSurface surface);
  }

  public interface MappingListener {
    public void mappingAdded(LXMidiEngine engine, LXMidiMapping mapping);
    public void mappingRemoved(LXMidiEngine engine, LXMidiMapping mapping);
  }

  private final List<LXMidiListener> listeners = new ArrayList<LXMidiListener>();
  private final List<DeviceListener> deviceListeners = new ArrayList<DeviceListener>();
  private final List<MappingListener> mappingListeners = new ArrayList<MappingListener>();

  private final List<LXShortMessage> threadSafeInputQueue =
    Collections.synchronizedList(new ArrayList<LXShortMessage>());

  private final List<LXShortMessage> engineThreadInputQueue =
    new ArrayList<LXShortMessage>();

  private final List<LXMidiInput> mutableInputs = new CopyOnWriteArrayList<LXMidiInput>();
  private final List<LXMidiOutput> mutableOutputs = new CopyOnWriteArrayList<LXMidiOutput>();
  private final List<LXMidiSurface> mutableSurfaces = new CopyOnWriteArrayList<LXMidiSurface>();

  public final List<LXMidiInput> inputs = Collections.unmodifiableList(this.mutableInputs);
  public final List<LXMidiOutput> outputs = Collections.unmodifiableList(this.mutableOutputs);
  public final List<LXMidiSurface> surfaces = Collections.unmodifiableList(this.mutableSurfaces);

  private final List<LXMidiMapping> mutableMappings = new ArrayList<LXMidiMapping>();
  public final List<LXMidiMapping> mappings = Collections.unmodifiableList(this.mutableMappings);

  private final Map<MidiDevice.Info, LXMidiInput> midiInfoToInput =
    new HashMap<MidiDevice.Info, LXMidiInput>();

  private final Map<MidiDevice.Info, LXMidiOutput> midiInfoToOutput =
    new HashMap<MidiDevice.Info, LXMidiOutput>();

  private final Map<String, Class<? extends LXMidiSurface>> registeredSurfaces =
    new HashMap<String, Class<? extends LXMidiSurface>>();

  private class InitializationLock {
    private final List<Runnable> whenReady = new ArrayList<Runnable>();
    private boolean ready = false;
  }

  private final InitializationLock initializationLock = new InitializationLock();

  public final BooleanParameter computerKeyboardEnabled = (BooleanParameter)
    new BooleanParameter("Computer MIDI Keyboard", false)
    .setMappable(false)
    .setDescription("Whether the computer keyboard plays notes to MIDI tracks");

  public LXMidiEngine(LX lx) {
    super(lx);
    this.registeredSurfaces.put(APC40.DEVICE_NAME, APC40.class);
    this.registeredSurfaces.put(APC40Mk2.DEVICE_NAME, APC40Mk2.class);
    this.registeredSurfaces.put(DJM900nxs2.DEVICE_NAME, DJM900nxs2.class);
    this.registeredSurfaces.put(MidiFighterTwister.DEVICE_NAME, MidiFighterTwister.class);
    addParameter("computerKeyboardEnabled", this.computerKeyboardEnabled);
  }

  public LXMidiEngine registerSurface(String deviceName, Class<? extends LXMidiSurface> surfaceClass) {
    this.lx.registry.checkRegistration();
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
    // Get the device update thread ready in the background
    this.deviceUpdateThread.start();

    // Start an initialization thread to do first-pass device detection
    new Thread("LXMidiEngine Device Initialization") {
      @Override
      public void run() {

        // NOTE(mcslee): this can sometimes hang or be slow for unclear reasons...
        // do it in a separate thread so that we don't delay the whole application
        // starting up. On some systems this was blocking for up to 5 seconds, no clue
        // why, perhaps due to weird OS MIDI cacheing or timeouts

        try {
          // Use the CoreMidi4J version... which on Mac gets all devices in a sysex-compatible
          // wrapper, on other OS will just give the defaults
          // for (MidiDevice.Info deviceInfo : MidiSystem.getMidiDeviceInfo()) {
          for (MidiDevice.Info deviceInfo : CoreMidiDeviceProvider.getMidiDeviceInfo()) {
            try {
              MidiDevice device = MidiSystem.getMidiDevice(deviceInfo);
              if (device.getMaxTransmitters() != 0) {
                LXMidiInput input = new LXMidiInput(LXMidiEngine.this, device);
                mutableInputs.add(input);
                midiInfoToInput.put(deviceInfo, input);
              }
              if (device.getMaxReceivers() != 0) {
                LXMidiOutput output = new LXMidiOutput(LXMidiEngine.this, device);
                mutableOutputs.add(output);
                midiInfoToOutput.put(deviceInfo, output);
              }
            } catch (MidiUnavailableException mux) {
              error(mux, "MidiUnavailable on MIDI device initialization thread: " + mux.getLocalizedMessage());
            }
          }
        } catch (Exception x) {
          error(x, "Unexpected MIDI error, MIDI unavailable: " + x.getLocalizedMessage());
        }

        // Instantiate any midi surfaces
        for (LXMidiInput input : inputs) {
          instantiateSurface(input, false);
        }

        // Notify any threads blocked on waitUntilReady(), notify them to continue
        synchronized (initializationLock) {
          initializationLock.ready = true;
          initializationLock.notifyAll();
        }

        // Now, schedule the engine thread to perform any blocked whenReady tasks
        lx.engine.addTask(() -> {
          for (Runnable runnable : initializationLock.whenReady) {
            runnable.run();
          }
        });

        // On MacOS - load the MIDI notification listener
        boolean listening = false;
        try {
          if (CoreMidiDeviceProvider.isLibraryLoaded()) {
            CoreMidiDeviceProvider.addNotificationListener(new CoreMidiNotification() {
              public void midiSystemUpdated() {
                synchronized(deviceUpdateThread) {
                  deviceUpdateThread.notify();
                }
              }
            });
            listening = true;
          }
        } catch (CoreMidiException cmx) {
          error(cmx, "Could not initialize CoreMidi notification listener: " + cmx.getMessage());
        }

        // Can't listen? We're not on Mac. Then we'll just have to poll for changes...
        if (!listening) {
          deviceUpdateThread.setPolling();
        }
      }
    }.start();
  }

  public void disposeSurfaces() {
    for (LXMidiSurface surface : this.surfaces) {
      surface.dispose();
    }
    this.mutableSurfaces.clear();
  }

  @Override
  public void dispose() {
    synchronized (this.deviceUpdateThread) {
      this.deviceUpdateThread.interrupt();
      // TODO(mcslee): join that thread before disposing inputs/outputs?
    }
    for (LXMidiInput input : this.inputs) {
      input.dispose();
    }
    this.mutableInputs.clear();
    for (LXMidiOutput output : this.outputs) {
      output.dispose();
    }
    this.mutableOutputs.clear();
    super.dispose();
  }

  private final MidiDeviceUpdateThread deviceUpdateThread = new MidiDeviceUpdateThread();

  private class MidiDeviceUpdateThread extends Thread {

    private boolean polling = false;
    private boolean setPolling = false;

    private MidiDeviceUpdateThread() {
      super("LXMidiEngine Device Update");
    }

    private synchronized void setPolling() {
      this.polling = true;

      // Set this flag to avoid the first check happening immediately...
      // go back to sleep and check after 5 seconds
      this.setPolling = true;
      notify();
    }

    @Override
    public synchronized void run() {
      while (!isInterrupted()) {
        try {
          // In polling mode, check every 5 seconds, otherwise wait
          // until an observer notifies us...
          wait(this.polling ? 5000 : 0);
        } catch (InterruptedException ix) {
          break;
        }
        if (!this.setPolling) {
          updateMidiDevices();
        }
        this.setPolling = false;
      }
      log("LXMidiEngine Device Update Thread finished.");
    }
  };

  /**
   * Keep in mind that this runs on a non-engine thread!! We can read the device arrays
   * because they are using the CopyOnWrite list implementation, but any modifications
   * that will trigger listeners and parameter changes are submitted as tasks to the engine
   * thread.
   */
  private void updateMidiDevices() {
    try {
      // We'll need to check that all are alive, set this flag to false
      for (LXMidiInput input : this.mutableInputs) {
        input.keepAlive = false;
      }
      for (LXMidiOutput output : this.mutableOutputs) {
        output.keepAlive = false;
      }

      // Set up a list of devices that need to be checked for midi surfaces... we'll lazy-initialize
      // this below because this is often empty.
      List<MidiDevice.Info> checkForSurface = null;

      for (MidiDevice.Info deviceInfo : CoreMidiDeviceProvider.getMidiDeviceInfo()) {
        // First, check if we know this MidiDevice.Info instance, if it's the exact same instance
        // then we know we've done initialization on this before and don't need further
        // checks...
        LXMidiInput existingInput = this.midiInfoToInput.get(deviceInfo);
        if (existingInput != null) {
          existingInput.keepAlive = true;
          if (!existingInput.connected.isOn()) {
            MidiDevice device = MidiSystem.getMidiDevice(deviceInfo);
            lx.engine.addTask(() -> {
              existingInput.setDevice(device);
            });
          }
        }
        LXMidiOutput existingOutput = this.midiInfoToOutput.get(deviceInfo);
        if (existingOutput != null) {
          existingOutput.keepAlive = true;
          if (!existingOutput.connected.isOn()) {
            MidiDevice device = MidiSystem.getMidiDevice(deviceInfo);
            lx.engine.addTask(() -> {
              existingOutput.setDevice(device);
            });
          }
        }

        // Nothing found for this? We'll need to check again, but this time we do a lookup by
        // the contents of the info rather than exact instance comparison...
        if ((existingInput == null) && (existingOutput == null)) {
          try {
            final MidiDevice device = MidiSystem.getMidiDevice(deviceInfo);
            String deviceName = getDeviceNameUnique(deviceInfo);
            if (device.getMaxTransmitters() != 0) {
              LXMidiInput input = findInput(deviceName);
              if (input != null) {
                input.keepAlive = true;
                lx.engine.addTask(() -> {
                  input.setDevice(device);
                });
              } else {
                // Add new midi input on the engine thread!
                lx.engine.addTask(() -> {
                  addInput(deviceInfo, device);
                });

                // Add this to the list of devices that should be checked for control surface
                if (checkForSurface == null) {
                  checkForSurface = new ArrayList<MidiDevice.Info>();
                }
                checkForSurface.add(deviceInfo);
              }
            }

            if (device.getMaxReceivers() != 0) {
              LXMidiOutput output = findOutput(deviceName);
              if (output != null) {
                output.keepAlive = true;
                lx.engine.addTask(() -> {
                  output.setDevice(device);
                });
              } else {
                // Add new midi output on the engine thread
                lx.engine.addTask(() -> {
                  addOutput(deviceInfo, device);
                });
              }
            }
          } catch (MidiUnavailableException mux) {
            error(mux, "MIDI unavailable in updateMidiDevices: " + mux.getLocalizedMessage());
          }
        }
      }

      // Did any inputs or outputs disappear?? If keepAlive was not set to true
      // then it means that we've lost something... iterating over all the current
      // midi devices didn't find it, so we'll set its connected flag to false
      // and wait for it to come back later.
      for (LXMidiInput input : this.mutableInputs) {
        if (!input.keepAlive) {
          input.connected.setValue(false);
        }
      }
      for (LXMidiOutput output : this.mutableOutputs) {
        if (!output.keepAlive) {
          output.connected.setValue(false);
        }
      }

      // Now, in a last-pass, we've scheduled all new inputs and outputs for addition
      // and we should check for any possible new control surfaces. Note that this is done
      // after the above loop because we need to ensure that *both* the input and output
      // of the control surface have been added first.
      if (checkForSurface != null) {
        final List<MidiDevice.Info> checkForSurface2 = checkForSurface;
        lx.engine.addTask(() -> {
          for (MidiDevice.Info deviceInfo : checkForSurface2) {
            checkForSurface(getDeviceName(deviceInfo));
          }
        });
      }

    } catch (Exception x) {
      error(x, "Unhandled exception in midi system update: " + x.getLocalizedMessage());
    }
  }

  private void addInput(MidiDevice.Info deviceInfo, MidiDevice device) {
    LXMidiInput input = new LXMidiInput(this, device);
    this.mutableInputs.add(input);
    this.midiInfoToInput.put(deviceInfo, input);
    JsonObject unremember = null;
    for (JsonObject remembered : this.rememberMidiInputs) {
      if (remembered.get(LXMidiInput.KEY_NAME).getAsString().equals(input.getName())) {
        input.load(this.lx, unremember = remembered);
        break;
      }
    }
    if (unremember != null) {
      this.rememberMidiInputs.remove(unremember);
    }
    for (DeviceListener listener : this.deviceListeners) {
      listener.inputAdded(this, input);
    }
  }

  private void addOutput(MidiDevice.Info deviceInfo, MidiDevice device) {
    LXMidiOutput output = new LXMidiOutput(this, device);
    this.mutableOutputs.add(output);
    this.midiInfoToOutput.put(deviceInfo, output);
    for (DeviceListener listener : this.deviceListeners) {
      listener.outputAdded(this, output);
    }
  }

  public static String getDeviceName(MidiDevice.Info deviceInfo) {
    String name = deviceInfo.getName();
    if (name.indexOf(COREMIDI4J_HEADER) == 0) {
      name = name.substring(COREMIDI4J_HEADER.length());
    }
    return name;
  }

  public static String getDeviceNameUnique(MidiDevice.Info deviceInfo) {
    String name = getDeviceName(deviceInfo);
    if (deviceInfo instanceof CoreMidiDeviceInfo) {
      name += ((CoreMidiDeviceInfo) deviceInfo).getdeviceUniqueID();
    }
    return name;
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
      surface = instantiateSurface(input, true);
      if (surface != null) {
        JsonObject unremember = null;
        for (JsonObject remember : this.rememberMidiSurfaces) {
          if (remember.get(LXMidiSurface.KEY_NAME).getAsString().equals(surface.getName())) {
            unremember = remember;
            surface.enabled.setValue(true);
            break;
          }
        }
        this.rememberMidiSurfaces.remove(unremember);
      }
    }
  }

  private LXMidiSurface instantiateSurface(LXMidiInput input, boolean notifyListeners) {
    Class<? extends LXMidiSurface> surfaceClass = this.registeredSurfaces.get(input.getName());
    if (surfaceClass == null) {
      return null;
    }
    LXMidiSurface surface = null;
    try {
      surface = surfaceClass.getConstructor(LX.class, LXMidiInput.class, LXMidiOutput.class).newInstance(this.lx, input, findOutput(input));
      this.mutableSurfaces.add(surface);
      if (notifyListeners) {
        for (DeviceListener listener : this.deviceListeners) {
          listener.surfaceAdded(this, surface);
        }
      }
    } catch (Exception x) {
      error(x, "Could not instantiate midi surface class: " + surfaceClass);
    }
    return surface;
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
          error(ix, "MIDI intiialization lock was interrupted??");
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
    for (LXMidiSurface surface : this.mutableSurfaces) {
      if (surface.getInput() == input) {
        return surface;
      }
    }
    return null;
  }

  public LXMidiSurface findSurface(String name) {
    for (LXMidiSurface surface : this.mutableSurfaces) {
      if (surface.getName().equals(name)) {
        return surface;
      }
    }
    return null;
  }

  private <T extends LXMidiDevice> T matchDevice(List<T> devices, String[] names) {
    for (T device : devices) {
      String deviceName = device.getNameUnique();
      for (String name : names) {
        if (deviceName.contains(name)) {
          return device;
        }
      }
    }
    return null;
  }

  private LXMidiOutput findOutput(LXMidiInput input) {
    return findDevice(this.mutableOutputs, input.getNameUnique());
  }

  public LXMidiOutput findOutput(String name) {
    return findDevice(this.mutableOutputs, name);
  }

  public LXMidiInput findInput(String name) {
    return findDevice(this.mutableInputs, name);
  }

  private <T extends LXMidiDevice> T findDevice(List<T> devices, String name) {
    for (T device : devices) {
      if (device.getNameUnique().equals(name)) {
        return device;
      }
    }
    return null;
  }

  public LXMidiEngine addListener(LXMidiListener listener) {
    Objects.requireNonNull(listener, "May not add null LXMidiEngine.LXMidiListener");
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate LXMidiEngine.Listener: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  public LXMidiEngine removeListener(LXMidiListener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot remove non-existent LXMidiEngine.Listener: " + listener);
    }
    this.listeners.remove(listener);
    return this;
  }

  public LXMidiEngine addDeviceListener(DeviceListener listener) {
    Objects.requireNonNull(listener, "May not add null LXMidiEngine.DeviceListener");
    if (this.deviceListeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate LXMidiEngine.DeviceListener: " + listener);
    }
    this.deviceListeners.add(listener);
    return this;
  }

  public LXMidiEngine removeDeviceListener(DeviceListener listener) {
    if (!this.deviceListeners.contains(listener)) {
      throw new IllegalStateException("Cannot remove non-registered LXMidiEngine.DeviceListener: " + listener);
    }
    this.deviceListeners.remove(listener);
    return this;
  }

  public LXMidiEngine addMappingListener(MappingListener listener) {
    Objects.requireNonNull(listener, "May not add null LXMidiEngine.MappingListener");
    if (this.mappingListeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate LXMidiEngine.MappingListener: " + listener);
    }
    this.mappingListeners.add(listener);
    return this;
  }

  public LXMidiEngine removeMappingListener(MappingListener listener) {
    if (!this.mappingListeners.contains(listener)) {
      throw new IllegalStateException("Cannot remove non-registered LXMidiEngine.MappingListener: " + listener);
    }
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
      error("Invalid MIDI message via OSC: " + message);
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
      for (LXAbstractChannel channelBus : this.lx.engine.mixer.channels) {
        if (channelBus.midiMonitor.isOn() && channelBus.midiChannel.getEnum().matches(message)) {
          channelBus.midiMessage(message);
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
          error("Could not load MIDI mapping: " + element.toString());
        }
      }
    }

    // NOTE: this is performed later on the engine thread, after the MIDI engine
    // is fully initialized, because we need to make sure that we've detected
    // all the available inputs and control surfaces
    whenReady(() -> {
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
              this.rememberMidiInputs.add(inputObj);
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
              this.rememberMidiSurfaces.add(surfaceObj);
            }
          }
        }
      }
    });
  }

  private static final String MIDI_LOG_PREFIX = "[MIDI] ";

  public static final void log(String message) {
    LX.log(MIDI_LOG_PREFIX + message);
  }

  public static final void error(String message) {
    LX.error(MIDI_LOG_PREFIX + message);
  }

  public static final void error(Exception x, String message) {
    LX.error(x, MIDI_LOG_PREFIX + message);
  }

}
