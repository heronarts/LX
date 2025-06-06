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
import heronarts.lx.LX.InstantiationException;
import heronarts.lx.LXComponent;
import heronarts.lx.LXMappingEngine;
import heronarts.lx.LXSerializable;
import heronarts.lx.Tempo;
import heronarts.lx.command.LXCommand;
import heronarts.lx.midi.surface.APC40;
import heronarts.lx.midi.surface.APC40Mk2;
import heronarts.lx.midi.surface.APCmini;
import heronarts.lx.midi.surface.APCminiMk2;
import heronarts.lx.midi.surface.DJM900nxs2;
import heronarts.lx.midi.surface.DJMA9;
import heronarts.lx.midi.surface.DJMV10;
import heronarts.lx.midi.surface.LXMidiSurface;
import heronarts.lx.midi.surface.MidiFighterTwister;
import heronarts.lx.midi.template.LXMidiTemplate;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.ObjectParameter;
import uk.co.xfactorylibrarians.coremidi4j.CoreMidiDeviceProvider;
import uk.co.xfactorylibrarians.coremidi4j.CoreMidiException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.ShortMessage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

public class LXMidiEngine extends LXComponent implements LXOscComponent {

  public static final String TEMPLATE_PATH = "template";
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
    public default void inputAdded(LXMidiEngine engine, LXMidiInput input) {};
    public default void outputAdded(LXMidiEngine engine, LXMidiOutput output) {};
    public default void surfaceAdded(LXMidiEngine engine, LXMidiSurface surface) {};
    public default void surfaceRemoved(LXMidiEngine engine, LXMidiSurface surface) {};
  }

  public interface TemplateListener {
    public void templateAdded(LXMidiEngine engine, LXMidiTemplate template);
    public void templateRemoved(LXMidiEngine engine, LXMidiTemplate template);
    public void templateMoved(LXMidiEngine engine, LXMidiTemplate template);
  }

  public interface MappingListener {
    public void mappingAdded(LXMidiEngine engine, LXMidiMapping mapping);
    public void mappingRemoved(LXMidiEngine engine, LXMidiMapping mapping);
  }

  private final List<LXMidiListener> listeners = new ArrayList<LXMidiListener>();
  private final List<DeviceListener> deviceListeners = new ArrayList<DeviceListener>();
  private final List<TemplateListener> templateListeners = new ArrayList<TemplateListener>();
  private final List<MappingListener> mappingListeners = new ArrayList<MappingListener>();

  private final AtomicBoolean hasInputMessage = new AtomicBoolean(false);

  private final List<LXMidiMessage> threadSafeInputQueue =
    Collections.synchronizedList(new ArrayList<LXMidiMessage>());

  private final List<LXMidiMessage> engineThreadInputQueue =
    new ArrayList<LXMidiMessage>();

  private final List<LXMidiInput> mutableInputs = new CopyOnWriteArrayList<LXMidiInput>();
  private final List<LXMidiOutput> mutableOutputs = new CopyOnWriteArrayList<LXMidiOutput>();
  private final List<LXMidiSurface> mutableSurfaces = new ArrayList<LXMidiSurface>();
  private final List<LXMidiTemplate> mutableTemplates = new ArrayList<LXMidiTemplate>();

  public final List<LXMidiInput> inputs = Collections.unmodifiableList(this.mutableInputs);
  public final List<LXMidiOutput> outputs = Collections.unmodifiableList(this.mutableOutputs);
  public final List<LXMidiSurface> surfaces = Collections.unmodifiableList(this.mutableSurfaces);
  public final List<LXMidiTemplate> templates = Collections.unmodifiableList(this.mutableTemplates);

  private final List<LXMidiMapping> mutableMappings = new ArrayList<LXMidiMapping>();
  public final List<LXMidiMapping> mappings = Collections.unmodifiableList(this.mutableMappings);

  private final ConcurrentHashMap<MidiDevice.Info, LXMidiInput> midiInfoToInput =
    new ConcurrentHashMap<MidiDevice.Info, LXMidiInput>();

  private final ConcurrentHashMap<MidiDevice.Info, LXMidiOutput> midiInfoToOutput =
    new ConcurrentHashMap<MidiDevice.Info, LXMidiOutput>();

  private final List<Class<? extends LXMidiTemplate>> registeredTemplates =
    new ArrayList<Class<? extends LXMidiTemplate>>();

  private final Map<String, List<Class<? extends LXMidiSurface>>> registeredSurfaces =
    new HashMap<String, List<Class<? extends LXMidiSurface>>>();

  private class InitializationLock {

    private final List<Runnable> whenReady = new ArrayList<Runnable>();

    private volatile boolean ready = false;

    private void whenReady(Runnable runnable) {
      boolean runNow = false;
      synchronized (this) {
        if (this.ready) {
          runNow = true;
        } else {
          this.whenReady.add(runnable);
        }
      }
      if (runNow) {
        runnable.run();
      }
    }

    private void onReady() {
      synchronized (this) {
        this.ready = true;
      }
      // The whenReady array is all ours now, no one else can add to it
      this.whenReady.forEach(runnable -> runnable.run());
      this.whenReady.clear();
    }
  }

  private final InitializationLock initializationLock = new InitializationLock();

  public final BooleanParameter computerKeyboardEnabled =
    new BooleanParameter("Computer MIDI Keyboard", false)
    .setDescription("Whether the computer keyboard plays notes to MIDI tracks");

  public final DiscreteParameter computerKeyboardOctave =
    new DiscreteParameter("Computer MIDI Keyboard Octave", 5, 0, 11)
    .setFormatter(v -> {
      int octave = (int) v;
      int lowNote = octave * 12;
      int highNote = lowNote + 14;
      String lowPitch = MidiNote.getPitchString(lowNote);
      String highPitch = MidiNote.getPitchString(highNote);
      return lowPitch + " to " + highPitch + " (" + lowNote + "-" + highNote + ")";
    }, true)
    .setDescription("What octave the MIDI computer keyboard is in");

  public final ObjectParameter<Integer> computerKeyboardVelocity =
    new ObjectParameter<Integer>("Computer MIDI Keyboard Velocity", new Integer[] { 1, 20, 40, 60, 80, 100, 127 }, 100)
    .setDescription("What velocity the MIDI computer keyboard uses");

  public final DiscreteParameter computerKeyboardChannel =
    new DiscreteParameter("Computer MIDI Keyboard Channel", 0, MidiNote.NUM_CHANNELS)
    .setFormatter(v -> { return "Ch." + (int) (v + 1); }, true)
    .setDescription("What channel the MIDI computer keyboard uses");

  public LXMidiEngine(LX lx) {
    super(lx);
    _registerSurface(APC40.class);
    _registerSurface(APC40Mk2.class);
    _registerSurface(APCmini.class);
    _registerSurface(APCminiMk2.class);
    _registerSurface(DJM900nxs2.class);
    _registerSurface(DJMA9.class);
    _registerSurface(DJMV10.class);
    _registerSurface(MidiFighterTwister.class);

    _registerTemplate(heronarts.lx.midi.template.AkaiMidiMix.class);
    _registerTemplate(heronarts.lx.midi.template.AkaiMPD218.class);
    _registerTemplate(heronarts.lx.midi.template.DJTTMidiFighterTwister.class);
    _registerTemplate(heronarts.lx.midi.template.NovationLaunchkeyMk337.class);

    this.computerKeyboardEnabled.setMappable(false);
    this.computerKeyboardOctave.setMappable(false);
    this.computerKeyboardVelocity.setMappable(false);
    this.computerKeyboardVelocity.setWrappable(false);
    this.computerKeyboardChannel.setMappable(false);
    addParameter("computerKeyboardEnabled", this.computerKeyboardEnabled);
    addParameter("computerKeyboardOctave", this.computerKeyboardOctave);
    addParameter("computerKeyboardVelocity", this.computerKeyboardVelocity);
    addParameter("computerKeyboardChannel", this.computerKeyboardChannel);
    addArray(TEMPLATE_PATH, this.templates);
  }

  private void _registerTemplate(Class <? extends LXMidiTemplate> templateClass) {
    if (this.registeredTemplates.contains(templateClass)) {
      throw new IllegalStateException("Template class is already registered: " + templateClass.getName());
    }
    this.registeredTemplates.add(templateClass);
  }

  public void registerTemplate(Class <? extends LXMidiTemplate> templateClass) {
    this.lx.registry.checkRegistration();
    _registerTemplate(templateClass);
  }

  public List<Class<? extends LXMidiTemplate>> getRegisteredTemplateClasses() {
    return Collections.unmodifiableList(this.registeredTemplates);
  }

  private void _registerSurface(Class<? extends LXMidiSurface> surfaceClass) {
    _registerSurface(LXMidiSurface.getDeviceName(surfaceClass), surfaceClass);
  }

  private void _registerSurface(String deviceName, Class<? extends LXMidiSurface> surfaceClass) {
    List<Class<? extends LXMidiSurface>> surfaces;
    if (!this.registeredSurfaces.containsKey(deviceName)) {
      this.registeredSurfaces.put(deviceName, surfaces = new ArrayList<Class<? extends LXMidiSurface>>());
    } else {
      surfaces = this.registeredSurfaces.get(deviceName);
    }
    if (surfaces.contains(surfaceClass)) {
      throw new IllegalStateException("Surface class is already registered: " + deviceName + " -> " + surfaceClass.getName());
    }
    surfaces.add(surfaceClass);
  }

  @Deprecated
  /**
   * Registers a new MIDI surface implementation with the MIDI engine
   *
   * @param deviceName Device name
   * @param surfaceClass Surface class type
   * @deprecated Should use registerSurface with no deviceName parameter
   * @return midi engine
   */
  public LXMidiEngine registerSurface(String deviceName, Class<? extends LXMidiSurface> surfaceClass) {
    this.lx.registry.checkRegistration();
    _registerSurface(deviceName, surfaceClass);

    // NOTE: the MIDI initialize() thread has been kicked off at this point. We
    // might not get in until after it has already attempted to initialize MIDI surfaces
    // in which case we need to check again for this newly registered surface
    whenReady(() -> { checkForNewSurfaceClass(surfaceClass); });
    return this;
  }

  /**
   * Registers a new MIDI surface implementation with the MIDI engine
   *
   * @param surfaceClass MIDI surface class name
   * @return this
   */
  public LXMidiEngine registerSurface(Class<? extends LXMidiSurface> surfaceClass) {
    this.lx.registry.checkRegistration();
    _registerSurface(surfaceClass);

    // NOTE: the MIDI initialize() thread has been kicked off at this point. We
    // might not get in until after it has already attempted to initialize MIDI surfaces
    // in which case we need to check again for this newly registered surface
    whenReady(() -> { checkForNewSurfaceClass(surfaceClass); });
    return this;
  }

  public List<Class<? extends LXMidiSurface>> getRegisteredSurfaceClasses() {
    final List<Class<? extends LXMidiSurface>> surfaceClasses = new ArrayList<Class<? extends LXMidiSurface>>();
    for (List<Class<? extends LXMidiSurface>> surfaceList : this.registeredSurfaces.values()) {
      for (Class<? extends LXMidiSurface> surfaceClass : surfaceList) {
        if (!surfaceClasses.contains(surfaceClass)) {
          surfaceClasses.add(surfaceClass);
        }
      }
    }
    Collections.sort(surfaceClasses, new Comparator<Class<? extends LXMidiSurface>>() {
      @Override
      public int compare(Class<? extends LXMidiSurface> o1, Class<? extends LXMidiSurface> o2) {
        return LXMidiSurface.getSurfaceName(o1).compareToIgnoreCase(LXMidiSurface.getSurfaceName(o2));
      }
    });
    return surfaceClasses;
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

        final Map<MidiDevice.Info, MidiDevice> inputMap = new LinkedHashMap<>();
        final Map<MidiDevice.Info, MidiDevice> outputMap = new LinkedHashMap<>();

        try {
          // Use the CoreMidi4J version... which on Mac gets all devices in a sysex-compatible
          // wrapper, on other OS will just give the defaults
          // for (MidiDevice.Info deviceInfo : MidiSystem.getMidiDeviceInfo()) {
          for (MidiDevice.Info deviceInfo : CoreMidiDeviceProvider.getMidiDeviceInfo()) {
            try {
              MidiDevice device = MidiSystem.getMidiDevice(deviceInfo);
              if (device.getMaxTransmitters() != 0) {
                inputMap.put(deviceInfo, device);
              }
              if (device.getMaxReceivers() != 0) {
                outputMap.put(deviceInfo, device);
              }
            } catch (MidiUnavailableException mux) {
              error(mux, "MidiUnavailable on MIDI device initialization thread: " + mux.getLocalizedMessage());
            }
          }
        } catch (Exception x) {
          error(x, "Unexpected MIDI error, MIDI unavailable: " + x.getLocalizedMessage());
        }

        // Once devices have been coordinated, finish initialization on the engine thread
        lx.engine.addTask(() -> _initialize(inputMap, outputMap));
      }
    }.start();
  }

  private void _initialize(Map<MidiDevice.Info, MidiDevice> inputMap, Map<MidiDevice.Info, MidiDevice> outputMap) {
    for (Map.Entry<MidiDevice.Info, MidiDevice> pair : inputMap.entrySet()) {
      LXMidiInput input = new LXMidiInput(this, pair.getValue());
      this.midiInfoToInput.put(pair.getKey(), input);
      this.mutableInputs.add(input);
    }
    for (Map.Entry<MidiDevice.Info, MidiDevice> pair : outputMap.entrySet()) {
      LXMidiOutput output = new LXMidiOutput(this, pair.getValue());
      this.midiInfoToOutput.put(pair.getKey(), output);
      this.mutableOutputs.add(output);
    }

    MidiSelector.updateInputs(this.inputs);
    MidiSelector.updateOutputs(this.outputs);

    // Restore device settings from file, includes settings for the inputs/outputs
    // above and will also restore saved MIDI surfaces
    loadDevices();

    // Instantiate any MIDI surfaces that were NOT in the MIDI file
    for (LXMidiInput input : this.inputs) {
      instantiateSurfaces(input, false);
    }

    // Notify listeners now that *all* I/O + surfaces added
    for (DeviceListener listener : this.deviceListeners) {
      for (LXMidiInput input : this.inputs) {
        listener.inputAdded(this, input);
      }
      for (LXMidiOutput output : this.outputs) {
        listener.outputAdded(this, output);
      }
      for (LXMidiSurface surface : this.surfaces) {
        listener.surfaceAdded(this, surface);
      }
    }

    // Perform any blocked whenReady tasks
    this.initializationLock.onReady();

    // On MacOS - load the MIDI notification listener
    boolean listening = false;
    try {
      if (CoreMidiDeviceProvider.isLibraryLoaded()) {
        CoreMidiDeviceProvider.addNotificationListener(() -> {
          synchronized (deviceUpdateThread) {
            // Kick the device update thread
            deviceUpdateThread.notify();
          }
        });
        listening = true;
      }
    } catch (CoreMidiException cmx) {
      error(cmx, "Could not initialize CoreMidi notification listener: " + cmx.getMessage());
    }

    // Can't listen? We're not on Mac. Then we'll just have to poll for changes...
    if (!listening) {
      this.deviceUpdateThread.setPolling();
    }
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
    private boolean skipUpdate = false;

    private MidiDeviceUpdateThread() {
      super("LXMidiEngine Device Update");
    }

    private synchronized void setPolling() {
      this.polling = true;

      // Set this flag to avoid the first check happening immediately...
      // go back to sleep and check after 5 seconds
      this.skipUpdate = true;
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
        if (!this.skipUpdate) {
          updateMidiDevices(CoreMidiDeviceProvider.getMidiDeviceInfo());
        }
        this.skipUpdate = false;
      }
      log("LXMidiEngine Device Update Thread finished.");
    }
  };

  private final List<LXMidiInput> updateInputs = new ArrayList<>();
  private final List<LXMidiOutput> updateOutputs = new ArrayList<>();

  /**
   * NOTE: this runs on the midi update thread! We do it here because operations opening MIDI devices
   * are sometimes slow and we don't want to interrupt animation. All modification operations should be punted
   * over to the engine thread.
   */
  private void updateMidiDevices(MidiDevice.Info[] midiDeviceInfo) {
    try {
      // Take a quick snapshot on update thread of what we were starting with
      this.updateInputs.clear();
      this.updateInputs.addAll(this.inputs);
      this.updateOutputs.clear();
      this.updateOutputs.addAll(this.outputs);

      // Set up a list of devices that need to be checked for midi surfaces... we'll lazy-initialize
      // this below because this is almost always going to be empty.
      List<MidiDevice> checkForSurface = null;

      for (MidiDevice.Info deviceInfo : midiDeviceInfo) {
        // First, check if we know this MidiDevice.Info instance, if it's the exact same instance
        // then we know we've done initialization on this before and don't need further
        // checks...
        LXMidiInput existingInput = this.midiInfoToInput.get(deviceInfo);
        if (existingInput != null) {
          this.updateInputs.remove(existingInput);
          if (!existingInput.connected.isOn()) {
            MidiDevice device = MidiSystem.getMidiDevice(deviceInfo);
            this.lx.engine.addTask(() -> existingInput.setDevice(device));
          }
        }
        LXMidiOutput existingOutput = this.midiInfoToOutput.get(deviceInfo);
        if (existingOutput != null) {
          this.updateOutputs.remove(existingOutput);
          if (!existingOutput.connected.isOn()) {
            MidiDevice device = MidiSystem.getMidiDevice(deviceInfo);
            this.lx.engine.addTask(() -> existingOutput.setDevice(device));
          }
        }

        // Nothing found for this? We'll need to check again, but this time we do a lookup by
        // the contents of the info rather than exact instance comparison...
        if ((existingInput == null) && (existingOutput == null)) {
          try {
            final MidiDevice device = MidiSystem.getMidiDevice(deviceInfo);
            final String deviceName = getDeviceName(deviceInfo);
            if (device.getMaxTransmitters() != 0) {
              final LXMidiInput input = findDevice(this.updateInputs, deviceName);
              if (input != null) {
                this.updateInputs.remove(input);
                this.lx.engine.addTask(() -> input.setDevice(device));
              } else {
                // Add new midi input on the engine thread!
                this.lx.engine.addTask(() -> addInput(deviceInfo, device));

                // Add this to the list of devices that should be checked for control surface
                if (checkForSurface == null) {
                  checkForSurface = new ArrayList<MidiDevice>();
                }
                checkForSurface.add(device);
              }
            }

            if (device.getMaxReceivers() != 0) {
              final LXMidiOutput output = findDevice(this.updateOutputs, deviceName);
              if (output != null) {
                this.updateOutputs.remove(output);
                this.lx.engine.addTask(() -> output.setDevice(device));
              } else {
                // Add new midi output on the engine thread
                this.lx.engine.addTask(() -> addOutput(deviceInfo, device));
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
      for (LXMidiInput input : this.updateInputs) {
        this.lx.engine.addTask(() -> input.connected.setValue(false));
      }
      for (LXMidiOutput output : this.updateOutputs) {
        this.lx.engine.addTask(() -> output.connected.setValue(false));
      }

      // Now, in a last-pass, we've scheduled all new inputs and outputs for addition
      // and we should check for any possible new control surfaces. Note that this is done
      // after the above loop because we need to ensure that *both* the input and output
      // of the control surface have been added first.
      if (checkForSurface != null) {
        final List<MidiDevice> checkForSurface2 = checkForSurface;
        this.lx.engine.addTask(() -> {
          for (MidiDevice device : checkForSurface2) {
            checkForNewSurfaceDevice(device);
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
    final String inputName = input.getName();
    JsonObject settings = null;
    for (JsonObject remembered : this.rememberMidiInputs) {
      if (remembered.get(LXMidiInput.KEY_NAME).getAsString().equals(inputName)) {
        settings = remembered;
        break;
      }
    }
    if (settings != null) {
      input.load(this.lx, settings);
      this.rememberMidiInputs.remove(settings);
    }
    for (DeviceListener listener : this.deviceListeners) {
      listener.inputAdded(this, input);
    }
    MidiSelector.updateInputs(this.inputs);
  }

  private void addOutput(MidiDevice.Info deviceInfo, MidiDevice device) {
    LXMidiOutput output = new LXMidiOutput(this, device);
    this.mutableOutputs.add(output);
    this.midiInfoToOutput.put(deviceInfo, output);
    for (DeviceListener listener : this.deviceListeners) {
      listener.outputAdded(this, output);
    }
    MidiSelector.updateOutputs(this.outputs);
  }

  public static String getDeviceName(MidiDevice.Info deviceInfo) {
    String name = deviceInfo.getName();
    if (name.indexOf(COREMIDI4J_HEADER) == 0) {
      name = name.substring(COREMIDI4J_HEADER.length());
    }
    return name;
  }

  private void checkForNewSurfaceDevice(MidiDevice device) {
    // Is there already a control surface for this device?
    for (LXMidiSurface surface : this.surfaces) {
      LXMidiInput input = surface.getInput();
      if ((input != null) && (input.device == device)) {
        return;
      }
    }
    // Attempt a new MIDI surface for this input device
    attemptMidiSurface(findInput(device));
  }

  private void checkForNewSurfaceClass(Class<? extends LXMidiSurface> surfaceClass) {
    for (LXMidiSurface surface : this.surfaces) {
      // Bail out if it's already been instantiated
      if (surface.getClass().equals(surfaceClass)) {
        return;
      }
    }
    final String deviceName = LXMidiSurface.getDeviceName(surfaceClass);
    for (LXMidiInput input : this.inputs) {
      if (input.getName().equals(deviceName)) {
        final LXMidiOutput output = findOutput(input);
        _addSurface(surfaceClass, input, output, true);
      }
    }
  }

  private void attemptMidiSurface(LXMidiInput input) {
    if (input != null) {
      instantiateSurfaces(input, true);
    }
  }

  private void instantiateSurfaces(LXMidiInput input, boolean notifyListeners) {
    // Is there already an instantiated surface for this input? Skip it!
    for (LXMidiSurface surface : this.surfaces) {
      if (surface.getInput() == input) {
        return;
      }
    }

    // Get surface classes registered for this input, there may be multiple
    final List<Class<? extends LXMidiSurface>> surfaceClasses = this.registeredSurfaces.get(input.getName());
    if (surfaceClasses != null) {
      final LXMidiOutput output = findOutput(input);
      surfaceClasses.forEach(surfaceClass ->  _addSurface(surfaceClass, input, output, notifyListeners));
    }
  }

  public LXMidiEngine addSurface(LXMidiSurface surface) {
    this.mutableSurfaces.add(surface);
    for (DeviceListener listener : this.deviceListeners) {
      listener.surfaceAdded(this, surface);
    }
    return this;
  }

  public LXMidiEngine removeSurface(LXMidiSurface surface) {
    if (!this.mutableSurfaces.remove(surface)) {
      throw new IllegalArgumentException("Cannot remove non-existent MIDI surface: " + surface);
    }
    surface.enabled.setValue(false);
    for (DeviceListener listener : this.deviceListeners) {
      listener.surfaceRemoved(this, surface);
    }
    LX.dispose(surface);
    return this;
  }

  private LXMidiSurface _addSurface(Class<? extends LXMidiSurface> surfaceClass, LXMidiInput input, LXMidiOutput output, boolean notifyListeners) {
    LXMidiSurface surface = instantiateSurface(surfaceClass, input, output);
    if (surface != null) {
      this.mutableSurfaces.add(surface);
      if (notifyListeners) {
        for (DeviceListener listener : this.deviceListeners) {
          listener.surfaceAdded(this, surface);
        }
      }
    }
    return surface;
  }

  /**
   * Instantiate a MIDI surface of the given class
   * @param <T> MIDI surface class type
   * @param surfaceClass Class
   * @return Surface instance
   */
  public <T extends LXMidiSurface> T instantiateSurface(Class<T> surfaceClass, LXMidiInput input, LXMidiOutput output) {
    try {
      return surfaceClass.getConstructor(LX.class, LXMidiInput.class, LXMidiOutput.class).newInstance(this.lx, input, output);
    } catch (Exception x) {
      error(x, "Could not instantiate midi surface class: " + surfaceClass);
    }
    return null;
  }

  /**
   * Add an instance of the given midi surface class type
   *
   * @param surfaceClass Midi surface class
   * @return this
   */
  public LXMidiSurface addSurface(Class<? extends LXMidiSurface> surfaceClass) {
    return _addSurface(surfaceClass, null, null, true);
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
    this.initializationLock.whenReady(runnable);
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

  public LXMidiSurface findSurface(String deviceName) {
    return findSurface(deviceName, 0);
  }

  public LXMidiSurface findSurface(String deviceName, int index) {
    int i = 0;
    for (LXMidiSurface surface : this.surfaces) {
      if (surface.getDeviceName().equals(deviceName)) {
        if (i >= index) {
          return surface;
        }
        ++i;
      }
    }
    return null;
  }

  public LXMidiSurface findSurfaceClass(String className, int index) {
    int i = 0;
    for (LXMidiSurface surface : this.surfaces) {
      if (surface.getClass().getName().equals(className)) {
        if (i >= index) {
          return surface;
        }
        ++i;
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
    int index = 0;
    String inputName = input.getName();
    for (LXMidiInput that : this.mutableInputs) {
      if (that == input) {
        break;
      } else if (that.getName().equals(inputName)) {
        ++index;
      }
    }
    return findDevice(this.mutableOutputs, inputName, index);
  }

  public LXMidiOutput findOutput(String name) {
    return findDevice(this.mutableOutputs, name);
  }

  public LXMidiOutput findOutput(MidiDevice device) {
    return findDevice(this.mutableOutputs, device);
  }

  public LXMidiInput findInput(String name) {
    return findDevice(this.mutableInputs, name);
  }

  public LXMidiInput findInput(String name, int index) {
    return findDevice(this.mutableInputs, name, index);
  }

  public LXMidiInput findInput(MidiDevice device) {
    return findDevice(this.mutableInputs, device);
  }

  private <T extends LXMidiDevice> T findDevice(List<T> devices, MidiDevice device) {
    for (T that : devices) {
      if (that.device == device) {
        return that;
      }
    }
    return null;
  }

  private static <T extends LXMidiDevice> T findDevice(List<T> devices, String name) {
    return findDevice(devices, name, 0);
  }

  private static <T extends LXMidiDevice> T findDevice(List<T> devices, String name, int index) {
    int i = 0;
    for (T device : devices) {
      if (device.getName().equals(name)) {
        if (i >= index) {
          return device;
        }
        ++i;
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

  public LXMidiEngine addTemplateListener(TemplateListener listener) {
    Objects.requireNonNull(listener, "May not add null LXMidiEngine.TemplateListener");
    if (this.templateListeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate LXMidiEngine.TemplateListener: " + listener);
    }
    this.templateListeners.add(listener);
    return this;
  }

  public LXMidiEngine removeTemplateListener(TemplateListener listener) {
    if (!this.templateListeners.contains(listener)) {
      throw new IllegalStateException("Cannot remove non-registered LXMidiEngine.TemplateListener: " + listener);
    }
    this.templateListeners.remove(listener);
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

  void queueInputMessage(LXMidiMessage message) {
    this.threadSafeInputQueue.add(message);
    this.hasInputMessage.set(true);
  }

  private static final String PATH_NOTE = "note";
  private static final String PATH_CC = "cc";
  private static final String PATH_PITCHBEND = "pitchbend";

  @Override
  public boolean handleOscMessage(OscMessage message, String[] parts, int index) {
    try {
      final String path = parts[index];
      LXShortMessage oscMidiMessage = null;
      if (path.equals(PATH_NOTE)) {
        int pitch = message.getInt();
        int velocity = message.getInt();
        int channel = message.getInt();
        oscMidiMessage = new MidiNoteOn(channel, pitch, velocity);
      } else if (path.equals(PATH_CC)) {
        int value = message.getInt();
        int cc = message.getInt();
        int channel = message.getInt();
        oscMidiMessage = new MidiControlChange(channel, cc, value);
      } else if (parts[index].equals(PATH_PITCHBEND)) {
        int msb = message.getInt();
        int channel = message.getInt();
        oscMidiMessage = new MidiPitchBend(channel, msb).setSource(LXMidiSource.OSC);
      }
      if (oscMidiMessage != null) {
        oscMidiMessage.setSource(LXMidiSource.OSC);
        dispatch(oscMidiMessage);
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
    final LXNormalizedParameter parameter = lx.engine.mapping.getControlTarget();
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

    // Clear the control target now that it's mapped
    this.lx.engine.mapping.setControlTarget(null);
  }

  private boolean applyMapping(LXShortMessage message) {
    boolean applied = false;
    for (LXMidiMapping mapping : this.mutableMappings) {
      if (mapping.matches(message)) {
        mapping.apply(this.lx, message);
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

  private List<LXMidiMapping> findParameterMappings(LXParameter parameter) {
    List<LXMidiMapping> found = null;
    for (LXMidiMapping mapping : this.mappings) {
      if (parameter == mapping.parameter) {
        if (found == null) {
          found = new ArrayList<LXMidiMapping>();
        }
        found.add(mapping);
      }
    }
    return found;
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
   * Called when an individual parameter is disposed. Remove any
   * midi mappings pointing to the now-nonexistent parameter.
   *
   * @param parameter Parameter that doesn't exist anymore
   * @return this
   */
  public LXMidiEngine removeParameterMappings(LXParameter parameter) {
    List<LXMidiMapping> remove = findParameterMappings(parameter);
    if (remove != null) {
      for (LXMidiMapping mapping : remove) {
        removeMapping(mapping);
      }
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
    if (this.hasInputMessage.compareAndSet(true, false)) {
      this.engineThreadInputQueue.clear();
      synchronized (this.threadSafeInputQueue) {
        this.engineThreadInputQueue.addAll(this.threadSafeInputQueue);
        this.threadSafeInputQueue.clear();
      }
      for (LXMidiMessage message : this.engineThreadInputQueue) {
        LXMidiInput input = message.getInput();
        input.dispatch(message);
        if (input.enabled.isOn()) {
          _dispatch(message);
        }
      }
    }
  }

  private void _dispatch(LXMidiMessage message) {
    if (message instanceof LXShortMessage) {
      dispatch((LXShortMessage) message);
    } else if (message instanceof LXSysexMessage) {
      for (LXMidiListener listener : this.listeners) {
        message.dispatch(listener);
      }
    }
  }

  public void dispatch(LXShortMessage message) {
    final LXMidiInput input = message.getInput();
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
        final MidiBeat beat = (MidiBeat) message;
        if (beat.isStop()) {
          this.lx.engine.tempo.stop();
        } else {
          final double period = beat.getPeriod();
          if (period != MidiBeat.PERIOD_UNKNOWN) {
            this.lx.engine.tempo.setPeriod(period);
          }
          this.lx.engine.tempo.trigger(beat.getBeat(), beat.nanoTime);
        }
      }
    }

    for (LXMidiListener listener : this.listeners) {
      message.dispatch(listener);
    }

    if (input == null || input.channelEnabled.isOn()) {
      for (LXAbstractChannel channelBus : this.lx.engine.mixer.channels) {
        if (channelBus.midiSource.matches(message.getSource()) && channelBus.midiFilter.filter(message)) {
          channelBus.midiMessage(message);
        }
      }
      lx.engine.modulation.midiDispatch(message);
    }
  }

  public void addTemplate(LXMidiTemplate template) {
    if (this.templates.contains(template)) {
      throw new IllegalStateException("Cannot add template twice: " + template);
    }
    this.mutableTemplates.add(template);
    for (TemplateListener listener : this.templateListeners) {
      listener.templateAdded(this, template);
    }
  }

  public void removeTemplate(LXMidiTemplate template) {
    if (!this.templates.contains(template)) {
      throw new IllegalStateException("Cannot remove template that does not exist: " + template);
    }
    this.mutableTemplates.remove(template);
    for (TemplateListener listener : this.templateListeners) {
      listener.templateRemoved(this, template);
    }
    LX.dispose(template);
  }

  public void moveTemplate(LXMidiTemplate template, int index) {
    if (!this.templates.contains(template)) {
      throw new IllegalStateException("Cannot move template that does not exist: " + template);
    }
    this.mutableTemplates.remove(template);
    this.mutableTemplates.add(index, template);
    for (TemplateListener listener : this.templateListeners) {
      listener.templateMoved(this, template);
    }
  }

  private void removeTemplates() {
    for (int i = this.templates.size() - 1; i >= 0; --i) {
      removeTemplate(this.templates.get(i));
    }
  }

  public void panic() {
    try {
      dispatch(new MidiPanic());
      for (LXMidiTemplate template : this.templates) {
        template.midiPanicReceived();
      }
      lx.pushStatusMessage("Sent a MIDI panic to all devices");
    } catch (InvalidMidiDataException imdx) {
      LX.error(imdx, "Failed to generate MIDI panic");
    }
  }

  private static final String KEY_INPUTS = "inputs";
  private static final String KEY_SURFACES = "surfaces";

  private final List<JsonObject> rememberMidiInputs = new ArrayList<JsonObject>();
  private boolean inLoadDevices = false;

  public void saveDevices() {
    if (this.inLoadDevices) {
      return;
    }

    JsonArray inputs = new JsonArray();
    for (LXMidiInput input : this.mutableInputs) {
      if (input.enabled.isOn()) {
        inputs.add(LXSerializable.Utils.toObject(this.lx, input));
      }
    }
    for (JsonObject remembered : this.rememberMidiInputs) {
      inputs.add(remembered);
    }
    JsonArray surfaces = new JsonArray();
    for (LXMidiSurface surface : this.mutableSurfaces) {
      if (surface.enabled.isOn() || surface.hasRememberFlag()) {
        surfaces.add(LXSerializable.Utils.toObject(this.lx, surface, true));
      }
    }

    JsonObject object = new JsonObject();
    object.addProperty(LX.KEY_VERSION, LX.VERSION);
    object.add(KEY_INPUTS, inputs);
    object.add(KEY_SURFACES, surfaces);

    File file = this.lx.getMediaFile(DEVICES_FILE_NAME);
    try (JsonWriter writer = new JsonWriter(new FileWriter(file))) {
      writer.setIndent("  ");
      new GsonBuilder().create().toJson(object, writer);
      log("MIDI devices saved to " + file.toString());
    } catch (IOException iox) {
      error(iox, "Could not export MIDI device settings to file: " + file.toString());
    }
  }

  private void loadDevices() {
    this.inLoadDevices = true;
    this.rememberMidiInputs.clear();
    JsonObject object = loadDevicesFile();
    if (object != null) {
      try {
        _loadDevices(object);
      } catch (Exception x) {
        error(x, "Exception in loadDevices");
      }
    }
    this.inLoadDevices = false;
  }

  private void _loadDevices(JsonObject object) {
    if (object.has(KEY_INPUTS)) {
      final Map<String, Integer> inputCount = new HashMap<>();
      final JsonArray inputs = object.getAsJsonArray(KEY_INPUTS);
      for (JsonElement element : inputs) {
        final JsonObject inputObj = element.getAsJsonObject();
        final String inputName = inputObj.get(LXMidiInput.KEY_NAME).getAsString();

        int count = inputCount.containsKey(inputName) ? inputCount.get(inputName) : 0;
        LXMidiInput input = findInput(inputName, count);
        inputCount.put(inputName, ++count);

        if (input != null) {
          input.load(lx, inputObj);
        } else {
          this.rememberMidiInputs.add(inputObj);
        }

      }
    }
    if (object.has(KEY_SURFACES)) {
      final JsonArray surfaces = object.getAsJsonArray(KEY_SURFACES);
      for (JsonElement element : surfaces) {
        JsonObject surfaceObj = element.getAsJsonObject();
        String surfaceClass = surfaceObj.get(LXMidiSurface.KEY_CLASS).getAsString();

        // We have a MIDI surface remembered from a previous load
        try {
          Class<? extends LXMidiSurface> surfaceClazz = this.lx.registry.getClass(surfaceClass).asSubclass(LXMidiSurface.class);
          LXMidiSurface surface = _addSurface(surfaceClazz, null, null, false);
          if (surface != null) {
            surface.load(lx, surfaceObj);
            if (surface.connected.isOn()) {
              // Enable it immediately if it's connected
              surface.enabled.setValue(true);
            } else {
              // Otherwise flag that we want to remember it
              surface.setRememberFlag();
            }
          }
        } catch (Exception x)  {
          error(x, "Could not restore surface class type: " + surfaceClass);
        }
      }
    }
  }

  private static final String DEVICES_FILE_NAME = ".lxmidi";

  private JsonObject loadDevicesFile() {
    File file = this.lx.getMediaFile(DEVICES_FILE_NAME);
    if (file.exists()) {
      try (FileReader fr = new FileReader(file)) {
        return new Gson().fromJson(fr, JsonObject.class);
      } catch (FileNotFoundException fnfx) {
        error(fnfx, "MIDI device settings file does not exist");
      } catch (IOException iox) {
        error(iox, "Failed to load MIDI device settings");
      }
    }
    return null;
  }

  private static final String KEY_MAPPINGS = "mapping";
  private static final String KEY_TEMPLATES = "templates";

  @Override
  public void save(LX lx, JsonObject object) {
    super.save(lx, object);
    object.add(KEY_TEMPLATES, LXSerializable.Utils.toArray(lx, this.templates));
    object.add(KEY_MAPPINGS, LXSerializable.Utils.toArray(lx, this.mutableMappings));
  }

  @Override
  public void load(final LX lx, final JsonObject object) {
    removeMappings();
    removeTemplates();
    super.load(lx, object);

    if (object.has(KEY_TEMPLATES)) {
      JsonArray templates = object.getAsJsonArray(KEY_TEMPLATES);
      for (JsonElement templateElem : templates) {
        try {
          JsonObject templateObj = templateElem.getAsJsonObject();
          LXMidiTemplate template;
          template = this.lx.instantiateComponent(templateObj.get(KEY_CLASS).getAsString(), LXMidiTemplate.class);
          template.load(this.lx, templateObj);
          addTemplate(template);
        } catch (InstantiationException ix) {
          error(ix, "Could not create MidiTemplate");
        }
      }
    }
  }

  public void loadMappings(LX lx, JsonObject obj) {
    if (obj.has(KEY_MAPPINGS)) {
      JsonArray mappings = obj.getAsJsonArray(KEY_MAPPINGS);
      for (JsonElement element : mappings) {
        try {
          addMapping(LXMidiMapping.create(this.lx, element.getAsJsonObject()));
        } catch (Exception x) {
          error(x, "Could not load MIDI mapping: " + element.toString());
        }
      }
    }
  }

  public void removeMappings() {
    for (int i = this.mappings.size() - 1; i >= 0; --i) {
      removeMapping(this.mappings.get(i));
    }
  }

  public void exportMappings(File file) {
    final JsonObject obj = new JsonObject();
    obj.add(KEY_MAPPINGS, LXSerializable.Utils.toArray(this.lx, this.mappings, true));
    try (JsonWriter writer = new JsonWriter(new FileWriter(file))) {
      writer.setIndent("  ");
      new GsonBuilder().create().toJson(obj, writer);
      LX.log("Mappings saved successfully to " + file.toString());
    } catch (IOException iox) {
      LX.error(iox, "Could not export MIDI mappings to file: " + file.toString());
    }
  }

  public List<LXMidiMapping> importMappings(File file) {
    removeMappings();
    final List<LXMidiMapping> imported = new ArrayList<LXMidiMapping>();
    try (FileReader fr = new FileReader(file)) {
      JsonObject obj = new Gson().fromJson(fr, JsonObject.class);
      if (obj.has(KEY_MAPPINGS)) {
        JsonArray mappingArr = obj.get(KEY_MAPPINGS).getAsJsonArray();
        for (JsonElement mappingElem : mappingArr) {
          try {
            LXMidiMapping mapping = LXMidiMapping.create(this.lx, mappingElem.getAsJsonObject());
            addMapping(mapping);
            imported.add(mapping);
          } catch (Exception x) {
            error("Invalid mapping in " + file + ": " + mappingElem);
          }
        }
      }
    } catch (IOException iox) {
      LX.error(iox, "Could not import MIDI mappings from file: " + file.toString());
    }
    return imported;
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
