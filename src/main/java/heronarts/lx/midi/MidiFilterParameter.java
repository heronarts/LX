/**
 * Copyright 2023- Mark C. Slee, Heron Arts LLC
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

import heronarts.lx.parameter.AggregateParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;

public class MidiFilterParameter extends AggregateParameter {

  public final BooleanParameter enabled =
    new BooleanParameter("MIDI Enabled", false)
    .setDescription("Whether MIDI is enabled");

  public final EnumParameter<LXMidiEngine.Channel> channel =
    new EnumParameter<LXMidiEngine.Channel>("MIDI Channel", LXMidiEngine.Channel.OMNI)
    .setDescription("Determines which MIDI channel is responded to");

  public final DiscreteParameter minNote =
    new DiscreteParameter("Min Note", 0, 128)
    .setUnits(DiscreteParameter.Units.MIDI_NOTE)
    .setDescription("Minimum MIDI Note Num");

  public final DiscreteParameter noteRange =
    new DiscreteParameter("Note Range", 128, 0, 129)
    .setDescription("MIDI Note Range");

  public final DiscreteParameter minVelocity =
    new DiscreteParameter("Min Velocity", 1, 1, 128)
    .setDescription("Minimum MIDI Velocity");

  public final DiscreteParameter velocityRange =
    new DiscreteParameter("Velocity Range", 127, 0, 128)
    .setDescription("MIDI Note Range");

  private final byte[] filteredNoteOnCount = new byte[128];

  public MidiFilterParameter(String label) {
    this(label, false);
  }

  public MidiFilterParameter(String label, boolean enabled) {
    super(label);
    this.enabled.setValue(enabled);
    addSubparameter("enabled", this.enabled);
    addSubparameter("channel", this.channel);
    addSubparameter("minNote", this.minNote);
    addSubparameter("noteRange", this.noteRange);
    addSubparameter("minVelocity", this.minVelocity);
    addSubparameter("velocityRange", this.velocityRange);
  }

  @Override
  public MidiFilterParameter setDescription(String description) {
    return (MidiFilterParameter) super.setDescription(description);
  }

  @Override
  protected void updateSubparameters(double value) {
    final long bits = Double.doubleToRawLongBits(value);
    this.enabled.setValue(bits & 0x1);
    this.channel.setValue((bits >>> 1) & 0xff);
    this.minNote.setValue((bits >>> 8) & 0xff);
    this.noteRange.setValue((bits >>> 16) & 0xff);
    this.minVelocity.setValue((bits >>> 24) & 0xff);
    this.velocityRange.setValue((bits >>> 32) & 0xff);
  }

  @Override
  protected void onSubparameterUpdate(LXParameter p) {
    final long bits =
      (this.enabled.isOn() ? 0x1 : 0x0) |
      (this.channel.getValuei() << 1) |
      (this.minNote.getValuei() << 8) |
      (this.noteRange.getValuei() << 16) |
      (this.minVelocity.getValuei() << 24) |
      (this.velocityRange.getValuei() << 32);
    setValue(Double.longBitsToDouble(bits));
  }

  public MidiFilterParameter resetNoteStack() {
    Arrays.fill(this.filteredNoteOnCount, (byte) 0);
    return this;
  }

  /**
   * Check whether this MIDI note passes through the filter
   *
   * @param message MIDI message
   * @return True if this MIDI message passes the filter
   */
  public boolean filter(LXShortMessage message) {
    if (!this.enabled.isOn()) {
      return false;
    }

    if (!this.channel.getEnum().matches(message)) {
      return false;
    }

    if (message instanceof MidiNote) {
      final MidiNote note = (MidiNote) message;
      final int pitch = note.getPitch();

      // Filter on pitch
      final int minNote = this.minNote.getValuei();
      final int maxNote = minNote + this.noteRange.getValuei();
      if (pitch < minNote || pitch >= maxNote) {
        return false;
      }

      if (note.isNoteOn()) {
        final int velocity = note.getVelocity();
        final int minVelocity = this.minVelocity.getValuei();
        final int maxVelocity = minVelocity + this.velocityRange.getValuei();
        if (velocity < minVelocity || velocity >= maxVelocity) {
          // God help us if over 127 of the same note stack up, we'll just overflow!
          ++this.filteredNoteOnCount[pitch];
          return false;
        }
      } else {
        // Have we filtered out a note on message from this pitch due to
        // an ignored velocity level? Then filter out this note off message
        // as well, assuming FIFO on note-on/off ordering
        if (this.filteredNoteOnCount[pitch] > 0) {
          --this.filteredNoteOnCount[pitch];
          return false;
        }

        // Otherwise let note off messages through
        return true;
      }
    }

    // The message passed!
    return true;

  }

}
