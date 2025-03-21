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

import java.util.Arrays;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;

import heronarts.lx.utils.LXUtils;

public abstract class MidiNote extends LXShortMessage {

  private boolean isMutable = false;

  protected MidiNote(int command, int channel, int pitch, int velocity) throws InvalidMidiDataException {
    super(command, channel, pitch, velocity);
  }

  protected MidiNote(ShortMessage message, int command) {
    super(message, command);
  }

  MidiNote(byte[] data) {
    super(data);
  }

  public static MidiNote constructMutable(int command, int channel, int pitch, int velocity) throws InvalidMidiDataException {
    final byte[] data = {
      (byte) ((command & 0xf0) | (channel & 0x0f)),
      (byte) (pitch & 0xff),
      (byte) (velocity & 0xff)
    };
    MidiNote note;
    switch (command) {
      case ShortMessage.NOTE_ON: note = new MidiNoteOn(data); break;
      case ShortMessage.NOTE_OFF: note = new MidiNoteOff(data); break;
      default: throw new InvalidMidiDataException("MidiNote.constructMutable must take NOTE_ON or NOTE_OFF command");
    }
    note.isMutable = true;
    return note;
  }

  public MidiNote mutableCopy() {
    try {
      return constructMutable(getCommand(), getChannel(), getPitch(), getVelocity());
    } catch (InvalidMidiDataException imdx) {
      throw new IllegalStateException("MidiNote.mutableCopy can't already hold bad data??? " + this);
    }
  }

  public static final int NUM_PITCHES = 128;
  public static final int NUM_CHANNELS = 16;
  public static final int MAX_VELOCITY = 127;

  private final static String[] PITCH_STRINGS = {
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
  };

  public static String getPitchString(int pitch) {
    // NOTE: we use the ableton/yamaha "C3" middle-C standard here... there is
    // no clear standard about what number C MIDI note 60 should be given, but
    // Ableton and "European" manufacturers seem to call it C3. Sticking with
    // that as matching Ableton is probably the most common use case.
    return PITCH_STRINGS[pitch % 12] +  Integer.toString(pitch/12 - 2);
  }

  public String getPitchString() {
    return getPitchString(getPitch());
  }

  public int getPitch() {
    return getData1();
  }

  public int getVelocity() {
    return getData2();
  }

  public double getVelocityNormalized() {
    return getVelocity() / 127.;
  }

  public boolean isNoteOn() {
    return (this.getCommand() == ShortMessage.NOTE_ON) && (getVelocity() > 0);
  }

  public boolean isNoteOff() {
    return !isNoteOn();
  }

  public void setChannel(int channel) {
    if (!this.isMutable) {
      throw new IllegalStateException("May not setPitch() on non-mutable MIDI note");
    }
    if (!LXUtils.inRange(channel, 0, NUM_CHANNELS - 1)) {
      throw new IllegalArgumentException("Channel must fall in range [0-" + (NUM_CHANNELS-1) + "]");
    }
    this.data[0] = (byte) (getCommand() | (channel & 0x0F));
  }

  public void setPitch(int pitch) {
    if (!this.isMutable) {
      throw new IllegalStateException("May not setPitch() on non-mutable MIDI note");
    }
    if (!LXUtils.inRange(pitch, 0, NUM_PITCHES - 1)) {
      throw new IllegalArgumentException("Pitch must fall in range [0-" + (NUM_PITCHES-1) + "]");
    }
    this.data[1] = (byte) (pitch & 0xFF);
  }

  public void setVelocity(int velocity) {
    if (!this.isMutable) {
      throw new IllegalStateException("May not setVelocity() on non-mutable MIDI note");
    }
    if (!LXUtils.inRange(velocity, 1, MAX_VELOCITY)) {
      throw new IllegalArgumentException("Velocity must fall in range [1-" + MAX_VELOCITY + "]");
    }
    this.data[2] = (byte) (velocity & 0xFF);
  }

  /**
   * Keeps count of a stack of midi notes
   */
  public static class Stack {
    private int[] notes = new int[NUM_PITCHES];
    private int noteCount = 0;

    public void onMidiNote(MidiNote note) {
      final int pitch = note.getPitch();
      if (note.isNoteOn()) {
        ++this.notes[pitch];
        ++this.noteCount;
      } else {
        if (this.notes[pitch] > 0) {
          --this.notes[pitch];
          --this.noteCount;
        }
      }
    }

    public int getNoteCount() {
      return this.noteCount;
    }

    public int getNoteCount(int pitch) {
      return this.notes[pitch];
    }

    public boolean isNoteHeld() {
      return this.noteCount > 0;
    }

    public boolean isNoteHeld(int pitch) {
      return this.notes[pitch] > 0;
    }

    public void reset() {
      Arrays.fill(this.notes, 0);
      this.noteCount = 0;
    }

  }

}
