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

public class MidiPitchBend extends LXShortMessage {

  public MidiPitchBend(int channel, int msb) throws InvalidMidiDataException {
    this(channel, 0, msb);
  }

  public MidiPitchBend(int channel, int lsb, int msb) throws InvalidMidiDataException {
    super(ShortMessage.PITCH_BEND, channel, lsb, msb);
  }

  MidiPitchBend(ShortMessage message) {
    super(message, ShortMessage.PITCH_BEND);
  }

  /**
   * Returns the pitch bend value, signed from [-8192, +8191]
   *
   * @return Pitch bend value
   */
  public int getPitchBend() {
    return (getData1() + (getData2() << 7)) - 0x2000;
  }

  /**
   * Returns the pitch bend value normalized space from [-1, +1]
   *
   * @return Normalized pitch bend amount
   */
  public double getNormalized() {
    int pitchBend = getPitchBend();
    return (pitchBend > 0) ? (pitchBend / 8191.) : (pitchBend / 8192.);
  }

  @Override
  public String toString() {
    return "MidiPitchBend:" + getChannel() + ":Bend:" + getPitchBend();
  }
}
