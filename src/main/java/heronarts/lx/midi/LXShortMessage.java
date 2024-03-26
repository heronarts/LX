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

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;

public abstract class LXShortMessage extends ShortMessage {

  private LXMidiInput input = null;

  public static LXShortMessage fromShortMessage(ShortMessage message) {
    switch (message.getCommand()) {
    case ShortMessage.NOTE_ON:
      return new MidiNoteOn(message);
    case ShortMessage.NOTE_OFF:
      return new MidiNoteOff(message);
    case ShortMessage.CONTROL_CHANGE:
      return new MidiControlChange(message);
    case ShortMessage.PROGRAM_CHANGE:
      return new MidiProgramChange(message);
    case ShortMessage.PITCH_BEND:
      return new MidiPitchBend(message);
    case ShortMessage.CHANNEL_PRESSURE:
      return new MidiAftertouch(message);
    }
    throw new IllegalArgumentException("Unsupported LXMidi message command: " + message.getCommand());
  }

  static ShortMessage getMessage(int command, int status, int value1, int value2) throws InvalidMidiDataException {
    ShortMessage sm = new ShortMessage();
    sm.setMessage(command, status, value1, value2);
    return sm;
  }

  protected LXShortMessage(int command, int status, int value1, int value2) throws InvalidMidiDataException {
    this(getMessage(command, status, value1, value2), command);
  }

  LXShortMessage(ShortMessage message, int command) {
    super(message.getMessage());
    if (getCommand() != command) {
      throw new IllegalArgumentException(
          "LXShortMessage constructed with command " + command
              + " but has actual command " + getCommand());
    }
  }

  LXShortMessage setInput(LXMidiInput input) {
    this.input = input;
    return this;
  }

  LXMidiInput getInput() {
    return this.input;
  }

  public final void dispatch(LXMidiListener listener) {
    if (this instanceof MidiPanic) {
      listener.midiPanicReceived();
      return;
    }
    switch (getCommand()) {
    case ShortMessage.NOTE_ON:
      MidiNoteOn note = (MidiNoteOn) this;
      if (note.getVelocity() == 0) {
        listener.noteOffReceived(note);
      } else {
        listener.noteOnReceived(note);
      }
      break;
    case ShortMessage.NOTE_OFF:
      listener.noteOffReceived((MidiNoteOff) this);
      break;
    case ShortMessage.CONTROL_CHANGE:
      listener.controlChangeReceived((MidiControlChange) this);
      break;
    case ShortMessage.PROGRAM_CHANGE:
      listener.programChangeReceived((MidiProgramChange) this);
      break;
    case ShortMessage.PITCH_BEND:
      listener.pitchBendReceived((MidiPitchBend) this);
      break;
    case ShortMessage.CHANNEL_PRESSURE:
      listener.aftertouchReceived((MidiAftertouch) this);
      break;
    }
  }
}
