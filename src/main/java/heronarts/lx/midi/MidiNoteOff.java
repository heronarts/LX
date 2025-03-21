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

public class MidiNoteOff extends MidiNote {

  MidiNoteOff(byte[] data) {
    super(data);
  }

  MidiNoteOff(ShortMessage message) {
    super(message, ShortMessage.NOTE_OFF);
  }

  public MidiNoteOff(int channel, int pitch) throws InvalidMidiDataException {
    super(ShortMessage.NOTE_OFF, channel, pitch, 0);
  }

  @Override
  public String toString() {
    return "MidiNoteOff:" + getChannel() + ":Pitch:" + getPitch() + ":Vel:" + getVelocity();
  }

}
