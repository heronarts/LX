/**
 * Copyright 2020- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.effect.midi;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.color.LXColor;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.modulator.ADEnvelope;
import heronarts.lx.modulator.ADSREnvelope;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.MIDI)
public class GateEffect extends LXEffect {

  public enum TriggerMode {
    GATE("Gate"),
    TRIGGER("Trig");

    public final String label;

    private TriggerMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final CompoundParameter floor =
    new CompoundParameter("Floor", 0, 0, 100)
    .setDescription("Minimum Value");

  public final CompoundParameter ceiling =
    new CompoundParameter("Ceiling", 100, 0, 100)
    .setDescription("Maximum Value");

  public final CompoundParameter attack = (CompoundParameter)
    new CompoundParameter("Attack", 100, 0, 5000)
    .setExponent(2)
    .setUnits(CompoundParameter.Units.MILLISECONDS)
    .setDescription("Attack Time");

  public final CompoundParameter decay = (CompoundParameter)
    new CompoundParameter("Decay", 1000, 0, 5000)
    .setExponent(2)
    .setUnits(CompoundParameter.Units.MILLISECONDS)
    .setDescription("Decay Time");

  public final CompoundParameter sustain =
    new CompoundParameter("Sustain", .8)
    .setDescription("Sustain Level");

  public final CompoundParameter release = (CompoundParameter)
    new CompoundParameter("Release", 1000, 0, 5000)
    .setExponent(2)
    .setUnits(CompoundParameter.Units.MILLISECONDS)
    .setDescription("Release Time");

  public final CompoundParameter shape =
    new CompoundParameter("Shape", 0, -1, 1)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription("Shape of the response curves");

  public final EnumParameter<TriggerMode> triggerMode =
    new EnumParameter<TriggerMode>("Mode", TriggerMode.GATE)
    .setDescription("Trigger Mode");

  public final BooleanParameter manualTrigger =
    new BooleanParameter("Trigger", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Manually trigger the gate");

  public final BooleanParameter midiEnabled =
    new BooleanParameter("MIDI", true)
    .setDescription("Whether to gate on MIDI notes");

  public final BoundedParameter midiVelocityResponse = (BoundedParameter)
    new BoundedParameter("Velocity", 25, 0, 100)
    .setUnits(BoundedParameter.Units.PERCENT)
    .setDescription("Degree to which MIDI velocity influences ceiling level");

  public final DiscreteParameter midiMinNote = (DiscreteParameter)
    new DiscreteParameter("Min Note", 0, 128)
    .setUnits(DiscreteParameter.Units.MIDI_NOTE)
    .setDescription("Minimum MIDI note");

  public final DiscreteParameter midiNoteRange =
    new DiscreteParameter("Range", 127, 1, 128)
    .setDescription("MIDI note range");

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

  private final ADSREnvelope adsr =
    new ADSREnvelope("ADSR", 0, 1, this.attack, this.decay, this.sustain, this.release, this.shapePow);

  private final ADEnvelope ad =
    new ADEnvelope("AD", 0, 1, this.attack, this.decay, this.shapePow);

  public GateEffect(LX lx) {
    super(lx);
    addParameter("floor", this.floor);
    addParameter("ceiling", this.ceiling);
    addParameter("attack", this.attack);
    addParameter("decay", this.decay);
    addParameter("sustain", this.sustain);
    addParameter("release", this.release);
    addParameter("shape", this.shape);
    addParameter("triggerMode", this.triggerMode);
    addParameter("manualTrigger", this.manualTrigger);
    addParameter("midiEnabled", this.midiEnabled);
    addParameter("midiVelocityResponse", this.midiVelocityResponse);
    addParameter("midiMinNote", this.midiMinNote);
    addParameter("midiNoteRange", this.midiNoteRange);

    addModulator(this.adsr);
    addModulator(this.ad);
  }

  private float velocity = 1;

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.manualTrigger) {
      this.velocity = 1;
      this.ad.engage.setValue(this.manualTrigger.isOn());
      this.adsr.engage.setValue(this.manualTrigger.isOn());
    } else if (p == this.midiEnabled) {
      if (!this.midiEnabled.isOn()) {
        this.ad.engage.setValue(false);
        this.adsr.engage.setValue(false);
      }
    }
  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    double level = LXUtils.lerp(100, LXUtils.lerp(this.floor.getValue(), this.ceiling.getValue(), this.velocity * this.ad.getValue()), enabledAmount);
    if (level < 100) {
      int mask = LXColor.gray(level);
      int alpha = 0x100;
      for (int i = 0; i < colors.length; ++i) {
        colors[i] = LXColor.multiply(colors[i], mask, alpha);
      }
    }
  }

  private boolean isValidNote(MidiNote note) {
    int pitch = note.getPitch();
    int min = this.midiMinNote.getValuei();
    int max = min + this.midiNoteRange.getValuei();
    return (pitch >= min) && (pitch < max);
  }

  @Override
  public void noteOnReceived(MidiNoteOn note) {
    if (this.midiEnabled.isOn() && isValidNote(note)) {
      this.velocity = LXUtils.lerpf(1, note.getVelocity() / 127f, this.midiVelocityResponse.getNormalizedf());
      this.ad.engage.setValue(true);
      this.adsr.engage.setValue(true);
    }
  }

  @Override
  public void noteOffReceived(MidiNote note) {
    if (this.midiEnabled.isOn() && isValidNote(note)) {
      this.ad.engage.setValue(false);
      this.adsr.engage.setValue(false);
    }
  }

}