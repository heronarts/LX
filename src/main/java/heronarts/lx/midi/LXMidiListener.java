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

public interface LXMidiListener {

  public default void noteOnReceived(MidiNoteOn note) {}

  public default void noteOffReceived(MidiNote note) {}

  public default void controlChangeReceived(MidiControlChange cc) {}

  public default void programChangeReceived(MidiProgramChange pc) {}

  public default void pitchBendReceived(MidiPitchBend pitchBend) {}

  public default void aftertouchReceived(MidiAftertouch aftertouch) {}

  public default void midiPanicReceived() {}

}
