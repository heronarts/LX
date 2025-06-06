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

public class MidiNoteOn extends MidiNote {

  public MidiNoteOn(int channel, int pitch, int velocity) throws InvalidMidiDataException {
    super(ShortMessage.NOTE_ON, channel, pitch, velocity);
  }

  MidiNoteOn(ShortMessage message) {
    super(message, ShortMessage.NOTE_ON);
  }

  MidiNoteOn(byte[] data) {
    super(data);
  }

  @Override
  public String toString() {
    return "MidiNoteOn:" + getChannel() + ":Pitch:" + getPitch() + ":Vel:" + getVelocity();
  }
}
