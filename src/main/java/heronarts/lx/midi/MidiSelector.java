/**
 * Copyright 2024- Mark C. Slee, Heron Arts LLC
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import heronarts.lx.parameter.AggregateParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.ObjectParameter;
import heronarts.lx.parameter.StringParameter;

public abstract class MidiSelector<T extends LXMidiTerminal> extends AggregateParameter {

  private static final List<LXMidiSource> SOURCE_CHANNEL_FILTERS = Arrays.asList(new LXMidiSource[] {
    LXMidiSource.ALL_INS,
    LXMidiSource.COMPUTER_KEYBOARD,
    LXMidiSource.OSC
  });

  private static final List<MidiSelector<?>> selectors = new ArrayList<MidiSelector<?>>();

  private static LXMidiSource[] sourceChannel = SOURCE_CHANNEL_FILTERS.toArray(new LXMidiSource[0]);
  private static LXMidiSource[] sourceDevice = new LXMidiSource[] { LXMidiSource.NONE };
  private static LXMidiDestination[] destinationDevice = new LXMidiDestination[] { LXMidiDestination.NONE };

  protected static void updateInputs(List<LXMidiInput> inputs) {
    // Device selectors use all available devices
    final ArrayList<LXMidiSource> deviceList = new ArrayList<LXMidiSource>(1 + inputs.size());
    deviceList.add(LXMidiSource.NONE);
    deviceList.addAll(inputs);
    sourceDevice = deviceList.toArray(new LXMidiSource[0]);

    // Channel filter selectors are for channel-enabled devices only
    final ArrayList<LXMidiSource> filterList = new ArrayList<LXMidiSource>(SOURCE_CHANNEL_FILTERS.size() + inputs.size());
    filterList.addAll(SOURCE_CHANNEL_FILTERS);
    for (LXMidiInput input : inputs) {
      if (input.channelEnabled.isOn()) {
        filterList.add(input);
      }
    }
    sourceChannel = filterList.toArray(new LXMidiSource[0]);

    // Update options in all the selectors
    for (MidiSelector<?> selector : selectors) {
      if (selector instanceof Source) {
        selector.update();
      }
    }
  }

  protected static void updateOutputs(List<LXMidiOutput> outputs) {
    final ArrayList<LXMidiDestination> deviceList = new ArrayList<LXMidiDestination>(1 + outputs.size());
    deviceList.add(LXMidiDestination.NONE);
    deviceList.addAll(outputs);
    destinationDevice = deviceList.toArray(new LXMidiDestination[0]);

    // Update options in all the selectors
    for (MidiSelector<?> selector : selectors) {
      if (selector instanceof Destination) {
        selector.update();
      }
    }
  }

  public final StringParameter name =
    new StringParameter("Name", null)
    .setDescription("Name of the MIDI device");

  public final DiscreteParameter index =
    new DiscreteParameter("Index", 0, 128)
    .setDescription("Index of the MIDI device, if there are multiple by the same name");

  public final ObjectParameter<T> terminal;

  private final LXParameterListener terminalListener;

  public boolean missingDevice = false;

  protected MidiSelector(String label) {
    super(label);
    setDescription("Receive MIDI messages from the given source");

    // NOTE: order important here, name will be restored *after* index when
    // re-loading, and this is handled in onSubparameterUpdate()
    addSubparameter("index", this.index);
    addSubparameter("name", this.name);

    final String midiLabel = getMidiLabel();

    this.terminal =
      new ObjectParameter<T>(midiLabel, getMidiTerminals())
      .setDescription("MIDI " + midiLabel);
    this.terminal.addListener(this.terminalListener = this::onTerminalChanged);

    selectors.add(this);
  }

  protected abstract String getMidiLabel();

  protected abstract T[] getMidiTerminals();

  private boolean flagOptionsUpdate = false;

  void update() {
    if (this.flagOptionsUpdate) {
      throw new IllegalStateException("Re-entrancy disallowed in LXMidiSource.Selector.updateOptions()");
    }
    this.flagOptionsUpdate = true;
    final T previousTerminal = this.terminal.getObject();

    final T[] availableTerminals = getMidiTerminals();
    this.terminal.setObjects(availableTerminals);

    if (this.missingDevice) {
      findMissingDevice();
    } else {
      boolean missing = true;
      for (T source : availableTerminals) {
        if (source == previousTerminal) {
          missing = false;
          this.terminal.setValue(source);
          updateNameAndIndex(source);
          break;
        }
      }
      // If The option previously set is no longer found! If it's non-null,
      // leave name/index as they were and flag that this is now a
      // non-existing MIDI device (note that if it was null, it would
      // have been found above, null is always "All Inputs" in the list
      // and wouldn't go away
      this.missingDevice = missing;
      bang();
    }
    this.flagOptionsUpdate = false;
  }

  private boolean flagNameUpdate = false;

  private void updateNameAndIndex(T terminal) {
    if (this.flagNameUpdate) {
      throw new IllegalStateException("Re-entrancy disallowed in MidiSelector.updateNameAndIndex()");
    }
    this.flagNameUpdate = true;
    if (terminal.getMidiDevice() == null) {
      // It is not a LXMidiInput source, it's a special one like All Ins
      // or Computer Keyboard
      this.index.setValue(0);
      this.name.setValue(null);
    } else {
      final String name = terminal.getName();
      int index = 0;
      for (T search : this.terminal.getObjects()) {
        if (terminal == search) {
          break;
        } else if (name.equals(search.getName())) {
          // There's another source by the same name, but not the selected one
          ++index;
        }
      }
      this.index.setValue(index);
      this.name.setValue(name);
    }
    this.flagNameUpdate = false;
  }

  private void findMissingDevice() {
    // Can be invoked either from a file reload, or from the available
    // options changing and we now want to find something set before
    final String name = this.name.getString();
    if (name == null) {
      this.missingDevice = false;
      return;
    }
    final int index = this.index.getValuei();
    int instance = 0;
    for (T source : this.terminal.getObjects()) {
      if (name.equals(source.getName())) {
        if (instance == index) {
          this.missingDevice = false;
          this.terminal.setValue(source);
          bang();
          return;
        } else {
          ++instance;
        }
      }
    }
    this.missingDevice = true;
    bang();
  }

  private void onTerminalChanged(LXParameter p) {
    if (this.flagOptionsUpdate) {
      // Don't process changes to the device parameter when it's
      // part of the routine of updating available options
      return;
    }
    this.missingDevice = false;
    updateNameAndIndex(this.terminal.getObject());
    bang();
  }

  @Override
  protected void updateSubparameters(double value) {
    // NO-OP, we don't have a meaningful internal double value
  }

  @Override
  protected void onSubparameterUpdate(LXParameter p) {
    if ((p == this.name) && !this.flagNameUpdate) {
      findMissingDevice();
    }
  }

  @Override
  public void dispose() {
    this.terminal.removeListener(this.terminalListener);
    selectors.remove(this);
    super.dispose();
  }

  public abstract static class Source extends MidiSelector<LXMidiSource> {

    protected Source(String label) {
      super(label);
    }

    public Source setInput(LXMidiInput input) {
      this.terminal.setValue((input == null) ? LXMidiSource.NONE : input);
      return this;
    }

    public LXMidiInput getInput() {
      if (this.missingDevice) {
        return null;
      }
      return (LXMidiInput) this.terminal.getObject().getMidiDevice();
    }

    /**
     * Whether the saved selector setting matches the given object
     *
     * @param that MIDI source to test for match
     * @return True
     */
    public boolean matches(LXMidiSource that) {
      if (this.missingDevice) {
        return false;
      }
      return this.terminal.getObject().matches(that);
    }

    @Override
    protected String getMidiLabel() {
      return "Source";
    }

    public static class Channel extends Source {

      public Channel(String label) {
        super(label);
      }

      @Override
      protected LXMidiSource[] getMidiTerminals() {
        return sourceChannel;
      }
    }

    public static class Device extends Source {

      public Device(String label) {
        super(label);
      }

      @Override
      protected LXMidiSource[] getMidiTerminals() {
        return sourceDevice;
      }

    }
  }

  public static abstract class Destination extends MidiSelector<LXMidiDestination> {

    protected Destination(String label) {
      super(label);
    }

    public Destination setOutput(LXMidiOutput output) {
      this.terminal.setValue((output == null) ? LXMidiDestination.NONE : output);
      return this;
    }

    public LXMidiOutput getOutput() {
      if (this.missingDevice) {
        return null;
      }
      return (LXMidiOutput) this.terminal.getObject().getMidiDevice();
    }

    @Override
    protected String getMidiLabel() {
      return "Destination";
    }

    public static class Device extends Destination {

      public Device(String label) {
        super(label);
      }

      @Override
      protected LXMidiDestination[] getMidiTerminals() {
        return destinationDevice;
      }
    }

  }
}