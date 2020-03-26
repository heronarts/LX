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

package heronarts.lx.midi.remote;

import heronarts.lx.LXUtils;
import heronarts.lx.midi.LXMidiEngine;
import heronarts.lx.midi.LXMidiInput;
import heronarts.lx.midi.LXMidiListener;
import heronarts.lx.midi.LXMidiOutput;
import heronarts.lx.midi.MidiAftertouch;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.midi.MidiPitchBend;
import heronarts.lx.midi.MidiProgramChange;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXListenableParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;

/**
 * This has been subsumed by the LXMidiSurface system. It's being left here
 * for reference only.
 */
@Deprecated
public class LXMidiRemote implements LXMidiListener {

  public enum TakeoverMode {
    TAKEOVER, PICKUP
  };

  private final static int MIDI_RANGE = 128;
  private final static int MIDI_CHANNELS = 16;
  private final static int NUM_BINDINGS = MIDI_RANGE * MIDI_CHANNELS;

  private final static int MIDI_MAX = MIDI_RANGE - 1;

  public final static int ANY_CHANNEL = -1;
  public final static int NOTE_VELOCITY = -1;
  public final static int CC_VALUE = -1;

  private final static int OFF = -1;
  public final static int DIRECT = 1;
  public final static int TOGGLE = 2;
  private final static int DISCRETE = 3;
  private final static int DISCRETE_OFF = 4;

  // TODO(mcslee): implement LXModulator controls
  // public final static int START = 3;
  // public final static int STOP = 4;
  // public final static int START_STOP = 5;
  // public final static int TRIGGER = 6;

  private abstract class Binding implements LXParameterListener {

    protected final LXParameter parameter;
    protected final boolean isListening;

    private Binding(LXParameter parameter) {
      if (parameter == null) {
        throw new IllegalArgumentException("Cannot bind to null parameter");
      }
      this.parameter = parameter;
      if ((output != null) && (parameter instanceof LXListenableParameter)) {
        ((LXListenableParameter) parameter).addListener(this);
        this.isListening = true;
      } else {
        this.isListening = false;
      }
    }

    protected void unbind() {
      if ((output != null) && (parameter instanceof LXListenableParameter)) {
        ((LXListenableParameter) parameter).removeListener(this);
      }
    }

    protected void assertChannel(int channel) {
      if (channel < 0 || channel >= MIDI_CHANNELS) {
        throw new IllegalArgumentException("Invalid MIDI channel: " + channel);
      }
    }

    protected void assertValue(int value) {
      if (value < 0 || value >= MIDI_RANGE) {
        throw new IllegalArgumentException("Invalid MIDI value: " + value);
      }
    }
  }

  private class NoteBinding extends Binding {

    private final int channel;
    private final int number;
    private final int mode;
    private final int value;
    private final int secondary;

    private NoteBinding(LXParameter parameter, int channel, int number,
        int mode, int value) {
      this(parameter, channel, number, mode, value, 0);
    }

    private NoteBinding(LXParameter parameter, int channel, int number,
        int mode, int value, int secondary) {
      super(parameter);
      this.channel = channel;
      this.number = number;
      this.mode = mode;
      this.value = value;
      this.secondary = secondary;

      assertChannel(channel);
      assertValue(number);

      switch (this.mode) {
      case OFF:
      case DIRECT:
        break;
      case TOGGLE:
        if (!(parameter instanceof LXNormalizedParameter)) {
          throw new IllegalArgumentException(
              "TOGGLE mode requires LXNormalizedParameter");
        }
        break;
      case DISCRETE:
      case DISCRETE_OFF:
        if (!(parameter instanceof DiscreteParameter)) {
          throw new IllegalArgumentException(
              "DISCRETE mode requires DiscreteParameter");
        }
        break;
      default:
        throw new IllegalArgumentException("Invalid NoteBinding mode: " + mode);
      }

      if (this.isListening) {
        onParameterChanged(this.parameter);
      }
    }

    private void noteOnReceived(MidiNoteOn note) {
      switch (this.mode) {
      case DIRECT:
        switch (this.value) {
        case NOTE_VELOCITY:
          if (this.parameter instanceof LXNormalizedParameter) {
            double normalized = note.getVelocity() / (double) MIDI_MAX;
            ((LXNormalizedParameter) this.parameter).setNormalized(normalized);
          } else {
            this.parameter.setValue(note.getVelocity());
          }
          break;
        default:
          this.parameter.setValue(this.value);
          break;
        }
        break;

      case TOGGLE:
        LXNormalizedParameter normalized = (LXNormalizedParameter) this.parameter;
        if (this.parameter instanceof BooleanParameter) {
          ((BooleanParameter) this.parameter).toggle();
        } else {
          if (normalized.getNormalized() > 0) {
            normalized.setNormalized(0);
          } else {
            normalized.setNormalized(1);
          }
        }
        break;

      case DISCRETE:
      case DISCRETE_OFF:
        DiscreteParameter discreteParameter = (DiscreteParameter) this.parameter;
        if ((this.mode == DISCRETE_OFF) && (this.value == discreteParameter.getValuei())) {
          discreteParameter.setValue(this.secondary);
        } else {
          discreteParameter.setValue(this.value);
        }
        break;
      }
    }

    private void noteOffReceived(MidiNote note) {
      switch (this.mode) {
      case OFF:
        if (this.parameter instanceof BooleanParameter) {
          ((BooleanParameter) this.parameter).setValue(false);
        } else if (this.parameter instanceof LXNormalizedParameter) {
          ((LXNormalizedParameter) this.parameter).setNormalized(0);
        } else {
          this.parameter.setValue(0);
        }
        break;
      }
    }

    public void onParameterChanged(LXParameter parameter) {
      if (output != null) {
        double parameterValue = this.parameter.getValue();
        if (this.parameter instanceof LXNormalizedParameter) {
          parameterValue = MIDI_MAX
              * ((LXNormalizedParameter) this.parameter).getNormalized();
        }
        switch (this.mode) {
        case OFF:
          if (parameterValue == 0) {
            output.sendNoteOff(this.channel, this.number, 0);
          }
          break;
        case TOGGLE:
        case DIRECT:
          if (parameterValue == 0) {
            if (this.mode == TOGGLE) {
              output.sendNoteOff(this.channel, this.number, 0);
            }
          } else {
            if (this.value == NOTE_VELOCITY) {
              output.sendNoteOn(this.channel, this.number,
                  (int) LXUtils.constrain(parameterValue, 0, MIDI_MAX));
            } else {
              output.sendNoteOn(this.channel, this.number, this.value);
            }
          }
          break;

        case DISCRETE:
        case DISCRETE_OFF:
          DiscreteParameter discreteParameter = (DiscreteParameter) this.parameter;
          if (discreteParameter.getValuei() == this.value) {
            output.sendNoteOn(this.channel, this.number, MIDI_MAX);
          } else {
            output.sendNoteOff(this.channel, this.number, 0);
          }
          break;
        }
      }
    }
  }

  private class ControllerBinding extends Binding {
    private final int channel;
    private final int cc;
    private final int value;

    private TakeoverMode takeoverMode;
    private boolean isDirty = true;
    private double pickupDirection = 0;
    private double lastValueSet = 0;

    private ControllerBinding(LXParameter parameter, int channel, int cc,
        int value, TakeoverMode takeoverMode) {
      super(parameter);
      this.channel = channel;
      this.cc = cc;
      this.value = value;
      this.takeoverMode = takeoverMode;

      assertChannel(channel);
      assertValue(cc);

      if (this.isListening) {
        onParameterChanged(this.parameter);
      }
    }

    private double valueDelta(int controllerValue, double normalizedValue) {
      if (this.parameter instanceof LXNormalizedParameter) {
        return normalizedValue
            - ((LXNormalizedParameter) this.parameter).getNormalized();
      } else {
        return controllerValue - this.parameter.getValue();
      }
    }

    private void controlChangeReceived(MidiControlChange controller) {
      int controllerValue = controller.getValue();
      double normalizedValue = controllerValue / (double) MIDI_MAX;

      if (this.takeoverMode == TakeoverMode.PICKUP) {
        double currentDelta = valueDelta(controllerValue, normalizedValue);

        // We are waiting for a pickup
        if (this.isDirty) {
          if (this.pickupDirection == 0) {
            // Initial state, where we didn't know the controller value yet
            if (Math.abs(currentDelta) < 0.04) {
              // If it was close, benefit of the doubt
              this.isDirty = false;
            } else {
              // Otherwise, set the pickup direction
              this.pickupDirection = currentDelta;
            }
          }
          if ((currentDelta == 0)
              || ((currentDelta > 0) != (this.pickupDirection > 0))) {
            // We picked it up!
            this.isDirty = false;
          }
        } else {
          if (this.parameter.getValue() != this.lastValueSet) {
            // Uh oh, this parameter isn't what we just set it to
            this.pickupDirection = currentDelta;
            this.isDirty = true;
          }
        }

        // Are we still dirty? Don't update...
        if (this.isDirty) {
          return;
        }
      }

      if (controllerValue == 0) {
        if (this.parameter instanceof LXNormalizedParameter) {
          ((LXNormalizedParameter) this.parameter).setNormalized(0);
        } else {
          this.parameter.setValue(0);
        }
      } else {
        switch (this.value) {
        case CC_VALUE:
          if (this.parameter instanceof LXNormalizedParameter) {
            ((LXNormalizedParameter) this.parameter)
                .setNormalized(normalizedValue);
          } else {
            this.parameter.setValue(controllerValue);
          }
          break;
        default:
          this.parameter.setValue(this.value);
          break;
        }
      }

      // Remember what we last set to
      this.lastValueSet = this.parameter.getValue();
    }

    public void onParameterChanged(LXParameter parameter) {
      if (output != null) {
        double parameterValue = this.parameter.getValue();
        if (this.parameter instanceof LXNormalizedParameter) {
          double normalized = ((LXNormalizedParameter) this.parameter).getNormalized();
          parameterValue = MIDI_MAX * normalized;
        }
        if (parameterValue == 0) {
          output.sendControlChange(this.channel, this.cc, 0);
        } else {
          int sendValue;
          switch (this.value) {
          case CC_VALUE:
            sendValue = (int) LXUtils.constrain(parameterValue, 0, MIDI_MAX);
            break;
          default:
            sendValue = LXUtils.constrain(this.value, 0, MIDI_MAX);
            break;
          }
          output.sendControlChange(this.channel, this.cc, sendValue);
        }
      }
    }
  }

  private boolean logEvents = false;

  private final NoteBinding[] noteOnBindings;
  private final NoteBinding[] noteOffBindings;
  private final ControllerBinding[] controllerBindings;

  private final LXMidiInput input;
  private final LXMidiOutput output;

  public LXMidiRemote(LXMidiInput input) {
    this(input, null);
  }

  public LXMidiRemote(LXMidiOutput output) {
    this(null, output);
  }

  public LXMidiRemote(LXMidiInput input, LXMidiOutput output) {
    this.input = input;
    this.output = output;
    if (this.input != null) {
      this.input.addListener(this);
    }
    this.noteOnBindings = new NoteBinding[NUM_BINDINGS];
    this.noteOffBindings = new NoteBinding[NUM_BINDINGS];
    this.controllerBindings = new ControllerBinding[NUM_BINDINGS];
    for (int i = 0; i < NUM_BINDINGS; ++i) {
      this.noteOnBindings[i] = null;
      this.noteOffBindings[i] = null;
      this.controllerBindings[i] = null;
    }
  }

  public LXMidiInput getInput() {
    return this.input;
  }

  public LXMidiOutput getOutput() {
    return this.output;
  }

  public LXMidiRemote bindNote(LXParameter parameter, int number) {
    return bindNote(parameter, ANY_CHANNEL, number);
  }

  public LXMidiRemote bindNote(LXParameter parameter, int channel, int number) {
    return bindNote(parameter, channel, number, DIRECT);
  }

  public LXMidiRemote bindNote(LXParameter parameter, int channel, int number,
      int mode) {
    return bindNote(parameter, channel, number, mode, NOTE_VELOCITY);
  }

  public LXMidiRemote bindNote(LXParameter parameter, int channel, int number,
      int mode, int value) {
    bindNoteOn(parameter, channel, number, mode, value);
    if (mode == DIRECT) {
      bindNoteOff(parameter, channel, number);
    }
    return this;
  }

  public LXMidiRemote bindNoteOn(LXParameter parameter, int number) {
    return bindNoteOn(parameter, ANY_CHANNEL, number);
  }

  public LXMidiRemote bindNoteOn(LXParameter parameter, int channel, int number) {
    return bindNoteOn(parameter, channel, number, DIRECT);
  }

  public LXMidiRemote bindNoteOn(LXParameter parameter, int channel,
      int number, int mode) {
    return bindNoteOn(parameter, channel, number, mode, NOTE_VELOCITY);
  }

  public LXMidiRemote bindNoteOn(LXParameter parameter, int channel,
      int number, int mode, int value) {
    return bindNoteOn(parameter, channel, number, mode, value, 0);
  }

  private LXMidiRemote bindNoteOn(LXParameter parameter, int channel,
      int number, int mode, int value, int secondary) {
    if (channel == ANY_CHANNEL) {
      for (int i = 0; i < MIDI_CHANNELS; ++i) {
        bindNoteOn(parameter, i, number, mode, value, secondary);
      }
    } else {
      unbindNoteOn(channel, number);
      int i = index(channel, number);
      this.noteOnBindings[i] = new NoteBinding(parameter, channel, number,
          mode, value, secondary);
    }
    return this;
  }

  public LXMidiRemote bindNoteOff(LXParameter parameter, int number) {
    return bindNoteOff(parameter, ANY_CHANNEL, number);
  }

  public LXMidiRemote bindNoteOff(LXParameter parameter, int channel, int number) {
    if (channel == ANY_CHANNEL) {
      for (int i = 0; i < MIDI_CHANNELS; ++i) {
        bindNoteOff(parameter, i, number);
      }
    } else {
      unbindNoteOff(channel, number);
      int i = index(channel, number);
      this.noteOffBindings[i] = new NoteBinding(parameter, channel, number,
          OFF, 0);
    }
    return this;
  }

  public LXMidiRemote bindController(LXParameter parameter, int cc) {
    return bindController(parameter, ANY_CHANNEL, cc);
  }

  public LXMidiRemote bindController(LXParameter parameter, int cc,
      TakeoverMode takeoverMode) {
    return bindController(parameter, ANY_CHANNEL, cc, takeoverMode);
  }

  public LXMidiRemote bindController(LXParameter parameter, int channel, int cc) {
    return bindController(parameter, channel, cc, CC_VALUE);
  }

  public LXMidiRemote bindController(LXParameter parameter, int channel,
      int cc, TakeoverMode takeoverMode) {
    return bindController(parameter, channel, cc, CC_VALUE, takeoverMode);
  }

  public LXMidiRemote bindController(LXParameter parameter, int channel,
      int cc, int value) {
    return bindController(parameter, channel, cc, value, TakeoverMode.TAKEOVER);
  }

  public LXMidiRemote bindController(LXParameter parameter, int channel,
      int cc, int value, TakeoverMode takeoverMode) {
    if (channel == ANY_CHANNEL) {
      for (int i = 0; i < MIDI_CHANNELS; ++i) {
        bindController(parameter, i, cc, value);
      }
    } else {
      unbindController(channel, cc);
      int i = index(channel, cc);
      this.controllerBindings[i] = new ControllerBinding(parameter, channel,
          cc, value, takeoverMode);
    }
    return this;
  }

  public LXMidiRemote bindNotes(DiscreteParameter parameter, int channel,
      int[] notes) {
    for (int i = 0; i < notes.length; ++i) {
      bindNoteOn(parameter, channel, notes[i], DISCRETE,
          parameter.getMinValue() + i);
    }
    return this;
  }

  public LXMidiRemote bindNotes(DiscreteParameter parameter, int channel,
      int[] notes, int offValue) {
    for (int i = 0; i < notes.length; ++i) {
      bindNoteOn(parameter, channel, notes[i], DISCRETE_OFF,
          parameter.getMinValue() + i, offValue);
    }
    return this;
  }

  public LXMidiRemote bindNotes(DiscreteParameter parameter, int[] channels,
      int note) {
    for (int i = 0; i < channels.length; ++i) {
      bindNoteOn(parameter, channels[i], note, DISCRETE,
          parameter.getMinValue() + i);
    }
    return this;
  }

  public LXMidiRemote bindNotes(DiscreteParameter parameter, int[] channels,
      int note, int offValue) {
    for (int i = 0; i < channels.length; ++i) {
      bindNoteOn(parameter, channels[i], note, DISCRETE_OFF,
          parameter.getMinValue() + i, offValue);
    }
    return this;
  }

  public LXMidiRemote unbindNote(int number) {
    return unbindNote(ANY_CHANNEL, number);
  }

  public LXMidiRemote unbindNote(int channel, int number) {
    unbindNoteOn(channel, number);
    unbindNoteOff(channel, number);
    return this;
  }

  public LXMidiRemote unbindNoteOn(int number) {
    return unbindNoteOn(ANY_CHANNEL, number);
  }

  public LXMidiRemote unbindNoteOn(int channel, int number) {
    if (channel == ANY_CHANNEL) {
      for (int i = 0; i < MIDI_CHANNELS; ++i) {
        unbindNoteOn(channel, number);
      }
    } else {
      int i = index(channel, number);
      if (noteOnBindings[i] != null) {
        noteOnBindings[i].unbind();
        noteOnBindings[i] = null;
      }
    }
    return this;
  }

  public LXMidiRemote unbindNoteOff(int number) {
    return unbindNoteOff(ANY_CHANNEL, number);
  }

  public LXMidiRemote unbindNoteOff(int channel, int number) {
    if (channel == ANY_CHANNEL) {
      for (int i = 0; i < MIDI_CHANNELS; ++i) {
        unbindNoteOff(channel, number);
      }
    } else {
      int i = index(channel, number);
      if (noteOffBindings[i] != null) {
        noteOffBindings[i].unbind();
        noteOffBindings[i] = null;
      }
    }
    return this;
  }

  public LXMidiRemote unbindController(int cc) {
    return unbindController(ANY_CHANNEL, cc);
  }

  public LXMidiRemote unbindController(int channel, int cc) {
    if (channel == ANY_CHANNEL) {
      for (int i = 0; i < MIDI_CHANNELS; ++i) {
        unbindNoteOff(channel, cc);
      }
    } else {
      int i = index(channel, cc);
      if (controllerBindings[i] != null) {
        controllerBindings[i].unbind();
        controllerBindings[i] = null;
      }
    }
    return this;
  }

  private int index(int channel, int number) {
    return channel * MIDI_RANGE + number;
  }

  public LXMidiRemote sendNoteOn(int channel, int number, int velocity) {
    if (this.output != null) {
      this.output.sendNoteOn(channel, number, velocity);
    }
    return this;
  }

  public LXMidiRemote sendNoteOff(int channel, int number) {
    return sendNoteOff(channel, number, 0);
  }

  public LXMidiRemote sendNoteOff(int channel, int number, int velocity) {
    if (this.output != null) {
      this.output.sendNoteOff(channel, number, velocity);
    }
    return this;
  }

  public LXMidiRemote sendController(int channel, int cc, int value) {
    if (this.output != null) {
      this.output.sendControlChange(channel, cc, value);
    }
    return this;
  }

  public LXMidiRemote sendSysex(byte[] sysex) {
    if (this.output != null) {
      this.output.sendSysex(sysex);
    }
    return this;
  }

  public final LXMidiRemote logEvents(boolean logEvents) {
    this.logEvents = logEvents;
    return this;
  }

  @Override
  public final void noteOnReceived(MidiNoteOn note) {
    if (this.logEvents) {
      LXMidiEngine.log(this.input.getName() + ":noteOn:" + note.getChannel()
          + ":" + note.getPitch() + ":" + note.getVelocity());
    }
    int index = index(note.getChannel(), note.getPitch());
    if (this.noteOnBindings[index] != null) {
      this.noteOnBindings[index].noteOnReceived(note);
    }
    noteOn(note);
  }

  @Override
  public final void noteOffReceived(MidiNote note) {
    if (this.logEvents) {
      LXMidiEngine.log(this.input.getName() + ":noteOff:" + note.getChannel()
          + ":" + note.getPitch() + ":" + note.getVelocity());
    }
    int index = index(note.getChannel(), note.getPitch());
    if (this.noteOffBindings[index] != null) {
      this.noteOffBindings[index].noteOffReceived(note);
    }
    noteOff(note);
  }

  @Override
  public final void controlChangeReceived(MidiControlChange controller) {
    if (this.logEvents) {
      LXMidiEngine.log(this.input.getName() + ":controllerChange:"
          + controller.getChannel() + ":" + controller.getCC() + ":"
          + controller.getValue());
    }
    int index = index(controller.getChannel(), controller.getCC());
    if (this.controllerBindings[index] != null) {
      this.controllerBindings[index].controlChangeReceived(controller);
    }
    controlChange(controller);
  }

  @Override
  public final void programChangeReceived(MidiProgramChange programChange) {
    if (this.logEvents) {
      LXMidiEngine.log(this.input.getName() + ":programChange:"
          + programChange.getChannel() + ":" + programChange.getProgram());
    }
    programChange(programChange);
  }


  @Override
  public final void pitchBendReceived(MidiPitchBend pitchBend) {
    if (this.logEvents) {
      LXMidiEngine.log(this.input.getName() + ":pitchBend:"
          + pitchBend.getChannel() + ":" + pitchBend.getPitchBend());
    }
    pitchBend(pitchBend);
  }

  @Override
  public final void aftertouchReceived(MidiAftertouch aftertouch) {
    if (this.logEvents) {
      LXMidiEngine.log(this.input.getName() + ":aftertouch:"
          + aftertouch.getChannel() + ":" + aftertouch.getAftertouch());
    }
    aftertouch(aftertouch);
  }


  protected void noteOn(MidiNoteOn note) {
  }

  protected void noteOff(MidiNote note) {
  }

  protected void controlChange(MidiControlChange controlChange) {
  }

  protected void programChange(MidiProgramChange programChange) {
  }

  protected void pitchBend(MidiPitchBend pitchBend) {
  }

  protected void aftertouch(MidiAftertouch aftertouch) {
  }

}
