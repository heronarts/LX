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
    return PITCHES[pitch % 12] +  Integer.toString(pitch/12);
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

  public boolean isNoteOn() {
    return (this.getCommand() == ShortMessage.NOTE_ON) && (getVelocity() > 0);
  }

}
