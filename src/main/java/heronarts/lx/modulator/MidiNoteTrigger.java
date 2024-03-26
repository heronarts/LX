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

package heronarts.lx.modulator;

import heronarts.lx.LXCategory;
import heronarts.lx.midi.LXMidiListener;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.NormalizedParameter;

@LXModulator.Global("MIDI Note")
@LXModulator.Device("MIDI Note")
@LXCategory(LXCategory.TRIGGER)
public class MidiNoteTrigger extends LXModulator implements LXTriggerSource, LXOscComponent, LXMidiListener {

  public final BooleanParameter legato =
    new BooleanParameter("Legato")
    .setDescription("Whether to sustain held legato notes");

  public final BooleanParameter triggerOut =
    new BooleanParameter("Trigger Out")
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Indicates whenever the MIDI trig fires");

  public final NormalizedParameter pitchOut =
    new NormalizedParameter("Pitch", 0)
    .setDescription("Normalized pitch of the note within valid range");

  public final NormalizedParameter velocityOut =
    new NormalizedParameter("Velocity", 0)
    .setDescription("Normalized velocity of the note within valid range");

  public final BooleanParameter showPitch =
    new BooleanParameter("Preview", false)
    .setDescription("Whether the note pitch meters are visible in the UI");


  public MidiNoteTrigger() {
    this("MIDI Note");
  }

  public MidiNoteTrigger(String label) {
    super(label);
    setMappingSource(false);
    addParameter("triggerOut", this.triggerOut);
    addParameter("pitchOut", this.pitchOut);
    addParameter("velocityOut", this.velocityOut);
    addParameter("showPitch", this.showPitch);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.legato) {
      this.noteCount = 0;
      this.triggerOut.setValue(false);
    }
  }

  private int noteCount = 0;

  @Override
  protected double computeValue(double deltaMs) {
    return this.pitchOut.getValue();
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.triggerOut;
  }

  @Override
  public void noteOnReceived(MidiNoteOn note) {
    ++this.noteCount;
    this.triggerOut.setValue(true);
    if (!this.legato.isOn()) {
      // Immediately clear the trigger if not legato
      this.triggerOut.setValue(false);
    }
    final int noteRange = this.midiFilter.noteRange.getValuei();
    if (noteRange > 0) {
      if (noteRange == 1) {
        this.pitchOut.setValue(.5);
      } else {
        final int minNote = this.midiFilter.minNote.getValuei();
        this.pitchOut.setValue((note.getPitch() - minNote ) / (double) noteRange);
      }
    }
    final int velocityRange = this.midiFilter.velocityRange.getValuei();
    if (velocityRange > 0) {
      if (velocityRange == 1) {
        this.velocityOut.setValue(.5);
      } else {
        final int minVelocity = this.midiFilter.minVelocity.getValuei();
        this.velocityOut.setValue((note.getVelocity() - minVelocity) / (double) velocityRange);
      }
    }
  }

  @Override
  public void noteOffReceived(MidiNote note) {
    if (this.noteCount > 0) {
      --this.noteCount;
    }
    if (!this.legato.isOn() || (this.noteCount == 0)) {
      this.triggerOut.setValue(false);
    }
  }

  @Override
  public void midiPanicReceived() {
    if (this.noteCount > 0) {
      this.noteCount = 0;
      this.triggerOut.setValue(false);
    }
  }

}
