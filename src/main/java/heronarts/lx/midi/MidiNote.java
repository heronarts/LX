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

public abstract class MidiNote extends LXShortMessage {

  protected MidiNote(int command, int channel, int pitch, int velocity) throws InvalidMidiDataException {
    super(command, channel, pitch, velocity);
  }

  protected MidiNote(ShortMessage message, int command) {
    super(message, command);
  }

  private final static String[] PITCHES = {
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
  };

  public static String getPitchString(int pitch) {
    // NOTE: we use the ableton/yamaha "C3" middle-C standard here... there is
    // no clear standard about what number C MIDI note 60 should be given, but
    // Ableton and "European" manufacturers seem to call it C3. Sticking with
    // that as matching Ableton is probably the most common use case.
    return PITCHES[pitch % 12] +  Integer.toString(pitch/12 - 2);
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

  /**
   * Keeps count of a stack of midi notes
   */
  public static class Stack {
    private int[] notes = new int[128];
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

    public boolean isNoteHeld() {
      return this.noteCount > 0;
    }

    public void reset() {
      for (int i = 0; i < this.notes.length; ++i) {
        this.notes[i] = 0;
      }
      this.noteCount = 0;
    }

  }

}
