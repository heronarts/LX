/**
 * Copyright 2022- Mark C. Slee, Heron Arts LLC
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
import heronarts.lx.midi.LXMidiEngine;
import heronarts.lx.midi.LXMidiListener;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

@LXModulator.Global("AHDSR")
@LXModulator.Device("AHDSR")
@LXCategory(LXCategory.CORE)
public class MultiModeEnvelope extends AHDSREnvelope implements LXOscComponent, LXNormalizedParameter, LXTriggerSource, LXMidiListener {

  private final static CompoundParameter initial() {
    return
      new CompoundParameter("Initial", 0, 0, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Initial Value");
  }

  private final static CompoundParameter peak() {
    return
      new CompoundParameter("Peak", 1, 0, 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Peak Value");
  }

  private final static CompoundParameter delay() {
    return
      new CompoundParameter("Delay", 0, 0, 5000)
      .setExponent(2)
      .setUnits(CompoundParameter.Units.MILLISECONDS)
      .setDescription("Delay Time");
  }

  private final static CompoundParameter attack() {
    return
      new CompoundParameter("Attack", 100, 0, 5000)
      .setExponent(2)
      .setUnits(CompoundParameter.Units.MILLISECONDS)
      .setDescription("Attack Time");
  }

  private final static CompoundParameter hold() {
    return
      new CompoundParameter("Hold", 0, 0, 5000)
      .setExponent(2)
      .setUnits(CompoundParameter.Units.MILLISECONDS)
      .setDescription("Hold Time");
  }

  private final static CompoundParameter decay() {
    return
      new CompoundParameter("Decay", 1000, 0, 5000)
      .setExponent(2)
      .setUnits(CompoundParameter.Units.MILLISECONDS)
      .setDescription("Decay Time");
  }

  private final static CompoundParameter sustain() {
    return
      new CompoundParameter("Sustain", 1)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Sustain Level");
  }

  private final static CompoundParameter release() {
    return
      new CompoundParameter("Release", 1000, 0, 5000)
      .setExponent(2)
      .setUnits(CompoundParameter.Units.MILLISECONDS)
      .setDescription("Release Time");
  }

  public final CompoundParameter shape =
    new CompoundParameter("Shape", 0, -1, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription("Shape of the envelope response curves");

  private final FunctionalParameter shapePow = new FunctionalParameter("Shape") {
    @Override
    public double getValue() {
      double s = shape.getValue();
      if (s > 0) {
        return LXUtils.lerp(1, 3, s);
      } else {
        return 1 / LXUtils.lerp(1, 3, -s);
      }
    }
  };

  public final BooleanParameter manualTrigger =
    new BooleanParameter("Trigger", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Manually engage the gate");

  public final TriggerParameter targetTrigger =
    new TriggerParameter("Trigger")
    .setDescription("Engage the gate from a trigger");

  public final BooleanParameter midiEnabled =
    new BooleanParameter("MIDI", false)
    .setDescription("Whether to gate on MIDI notes");

  public final BoundedParameter midiVelocityResponse =
    new BoundedParameter("Velocity", 25, -100, 100)
    .setUnits(BoundedParameter.Units.PERCENT)
    .setDescription("Degree to which MIDI velocity influences ceiling level");

  public final BoundedParameter midiNoteResponse =
    new BoundedParameter("Note Response", 0, -100, 100)
    .setUnits(BoundedParameter.Units.PERCENT)
    .setDescription("Degree to which MIDI note influences ceiling level");

  public final DiscreteParameter midiMinNote =
    new DiscreteParameter("Base Note", 0, 128)
    .setUnits(DiscreteParameter.Units.MIDI_NOTE)
    .setDescription("Base MIDI note");

  public final DiscreteParameter midiNoteRange =
    new DiscreteParameter("Range", 127, 1, 129)
    .setDescription("MIDI note range, including the base note and above");

  public final EnumParameter<LXMidiEngine.Channel> midiChannel =
    new EnumParameter<LXMidiEngine.Channel>("MIDI Channel", LXMidiEngine.Channel.OMNI)
    .setDescription("Determines which MIDI channels are responded to");

  public final BooleanParameter midiLegato =
    new BooleanParameter("Legato", false)
    .setDescription("Whether to skip retrigger on legato midi notes");

  public MultiModeEnvelope() {
    this("AHDSR");
  }

  public MultiModeEnvelope(String label) {
    super(label,
      delay(),
      attack(),
      hold(),
      decay(),
      sustain(),
      release(),
      initial(),
      peak()
    );
    setShape(this.shapePow);

    addParameter("initial", this.initial);
    addParameter("peak", this.peak);
    addParameter("delay", this.delay);
    addParameter("attack", this.attack);
    addParameter("hold", this.hold);
    addParameter("decay", this.decay);
    addParameter("sustain", this.sustain);
    addParameter("release", this.release);
    addParameter("shape", this.shape);

    addParameter("manualTrigger", this.manualTrigger);
    addParameter("targetTrigger", this.targetTrigger);

    addParameter("midiEnabled", this.midiEnabled);
    addParameter("midiVelocityResponse", this.midiVelocityResponse);
    addParameter("midiNoteResponse", this.midiNoteResponse);
    addParameter("midiMinNote", this.midiMinNote);
    addParameter("midiNoteRange", this.midiNoteRange);
    addParameter("midiChannel", this.midiChannel);
    addParameter("midiLegato", this.midiLegato);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.midiEnabled) {
      // Reset MIDI anytime this is toggled
      this.midiLegatoCount = 0;
      this.engage.setValue(false);
    } else if (p == this.manualTrigger) {
      this.peak.setValue(1);
      this.engage.setValue(this.manualTrigger.isOn());
    } else if (p == this.targetTrigger) {
      this.peak.setValue(1);
      this.engage.setValue(this.targetTrigger.isOn());
    } else if (p == this.midiEnabled) {
      if (!this.midiEnabled.isOn() && !this.manualTrigger.isOn()) {
        this.engage.setValue(false);
      }
    }
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.engage;
  }

  private boolean isActiveMidiNote(MidiNote note) {
    return
      this.midiEnabled.isOn() &&
      this.midiChannel.getEnum().matches(note) &&
      isValidMidiNoteNumber(note);
  }

  private boolean isValidMidiNoteNumber(MidiNote note) {
    int pitch = note.getPitch();
    int min = this.midiMinNote.getValuei();
    int max = min + this.midiNoteRange.getValuei();
    return (pitch >= min) && (pitch < max);
  }

  private int midiLegatoCount = 0;


  @Override
  public void noteOnReceived(MidiNoteOn note) {
    if (isActiveMidiNote(note)) {
      ++this.midiLegatoCount;
      boolean legato = this.midiLegato.isOn();
      if (legato && (this.midiLegatoCount > 1)) {
        return;
      }

      float velocity = 0;
      float velResponse = this.midiVelocityResponse.getValuef() / 100;
      if (velResponse >= 0) {
        velocity = LXUtils.lerpf(1, note.getVelocity() / 127f, velResponse);
      } else {
        velocity = LXUtils.lerpf(1, 1 - (note.getVelocity() / 127f), -velResponse);
      }

      float noteResponse = this.midiNoteResponse.getValuef() / 100;
      if (noteResponse >= 0) {
        float noteVelocity = (note.getPitch() - this.midiMinNote.getValuef() + 1) / this.midiNoteRange.getValuef();
        this.peak.setValue(velocity * LXUtils.lerpf(1, noteVelocity, noteResponse));
      } else {
        float noteVelocity = (this.midiMinNote.getValuef() + this.midiNoteRange.getValuef() + 1 - note.getPitch()) / this.midiNoteRange.getValuef();
        this.peak.setValue(velocity * LXUtils.lerpf(1, noteVelocity, -noteResponse));
      }

      if (this.engage.isOn()) {
        if (!legato) {
          if (this.resetMode.isOn()) {
            this.engage.setValue(false);
            this.engage.setValue(true);
          } else {
            this.retrig.setValue(true);
          }
        }
      } else {
        this.engage.setValue(true);
      }
    }
  }

  @Override
  public void noteOffReceived(MidiNote note) {
    if (isActiveMidiNote(note)) {
      this.midiLegatoCount = Math.max(0, this.midiLegatoCount - 1);
      if (this.midiLegatoCount == 0) {
        this.engage.setValue(false);
      }
    }
  }

}