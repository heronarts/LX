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
import heronarts.lx.model.LXPoint;
import heronarts.lx.modulator.AHDSREnvelope;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.MIDI)
public class GateEffect extends LXEffect implements LXEffect.Midi {

  public enum EnvelopeMode {
    GATE("Gate", AHDSREnvelope.StageMode.AHDSR, false),
    TRIGGER("1-Shot", AHDSREnvelope.StageMode.AHD, true);

    public final String label;
    public final AHDSREnvelope.StageMode stageMode;
    public final boolean oneshot;

    private EnvelopeMode(String label, AHDSREnvelope.StageMode stageMode, boolean oneshot) {
      this.label = label;
      this.stageMode = stageMode;
      this.oneshot = oneshot;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public enum TriggerMode {
    RETRIG("Retrig"),
    LEGATO("Legato"),
    RESET("Reset");

    public final String label;

    private TriggerMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final CompoundParameter initial =
    new CompoundParameter("Initial", 0, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Initial Value");

  public final CompoundParameter peak =
    new CompoundParameter("Peak", 1, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Peak Value");

  public final CompoundParameter delay =
    new CompoundParameter("Delay", 0, 0, 5000)
    .setExponent(2)
    .setUnits(CompoundParameter.Units.MILLISECONDS)
    .setDescription("Delay Time");

  public final CompoundParameter attack =
    new CompoundParameter("Attack", 100, 0, 5000)
    .setExponent(2)
    .setUnits(CompoundParameter.Units.MILLISECONDS)
    .setDescription("Attack Time");

  public final CompoundParameter hold =
    new CompoundParameter("Hold", 0, 0, 5000)
    .setExponent(2)
    .setUnits(CompoundParameter.Units.MILLISECONDS)
    .setDescription("Hold Time");

  public final CompoundParameter decay =
    new CompoundParameter("Decay", 1000, 0, 5000)
    .setExponent(2)
    .setUnits(CompoundParameter.Units.MILLISECONDS)
    .setDescription("Decay Time");

  public final CompoundParameter sustain =
    new CompoundParameter("Sustain", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Sustain Level");

  public final CompoundParameter release =
    new CompoundParameter("Release", 1000, 0, 5000)
    .setExponent(2)
    .setUnits(CompoundParameter.Units.MILLISECONDS)
    .setDescription("Release Time");

  public final CompoundParameter shape =
    new CompoundParameter("Shape", 0, -1, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription("Shape of the envelope response curves");

  public final EnumParameter<EnvelopeMode> envelopeMode =
    new EnumParameter<EnvelopeMode>("Mode", EnvelopeMode.GATE)
    .setDescription("Envelope Mode");

  public final EnumParameter<TriggerMode> triggerMode =
    new EnumParameter<TriggerMode>("Trigger Mode", TriggerMode.RETRIG)
    .setDescription("Trigger Mode");

  public final BooleanParameter manualTrigger =
    new BooleanParameter("Trigger", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Manually engage the gate");

  public final TriggerParameter targetTrigger =
    new TriggerParameter("Trigger")
    .setDescription("Engage the gate from a trigger");

  public final BoundedParameter midiVelocityResponse =
    new BoundedParameter("Velocity", 25, -100, 100)
    .setUnits(BoundedParameter.Units.PERCENT)
    .setDescription("Degree to which MIDI velocity influences ceiling level");

  public final BoundedParameter midiNoteResponse =
    new BoundedParameter("Note Response", 0, -100, 100)
    .setUnits(BoundedParameter.Units.PERCENT)
    .setDescription("Degree to which MIDI note influences ceiling level");

  private final FunctionalParameter effectivePeak = new FunctionalParameter("Peak") {
    @Override
    public double getValue() {
      return LXUtils.lerp(initial.getValue(), peak.getValue(), amount);
    }
  };

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

  public final AHDSREnvelope env =
    new AHDSREnvelope("ADSR", this.delay, this.attack, this.hold, this.decay, this.sustain, this.release, this.initial, this.effectivePeak)
    .setShape(this.shapePow);

  public GateEffect(LX lx) {
    super(lx);
    addParameter("initial", this.initial);
    addParameter("peak", this.peak);
    addParameter("delay", this.delay);
    addParameter("attack", this.attack);
    addParameter("hold", this.hold);
    addParameter("decay", this.decay);
    addParameter("sustain", this.sustain);
    addParameter("release", this.release);
    addParameter("shape", this.shape);
    addParameter("envelopeMode", this.envelopeMode);
    addParameter("triggerMode", this.triggerMode);
    addParameter("manualTrigger", this.manualTrigger);
    addParameter("targetTrigger", this.targetTrigger);
    addLegacyInternalParameter("midiEnabled", this.midiFilter.enabled);
    addParameter("midiVelocityResponse", this.midiVelocityResponse);
    addParameter("midiNoteResponse", this.midiNoteResponse);
    addLegacyInternalParameter("minNote", this.midiFilter.minNote);
    addLegacyInternalParameter("noteRange", this.midiFilter.noteRange);

    startModulator(this.env);

    onParameterChanged(this.envelopeMode);
    onParameterChanged(this.triggerMode);

    this.midiFilter.enabled.addListener(this);
  }

  private float amount = 1;

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.midiFilter.enabled) {
      // Reset MIDI anytime this is toggled
      this.midiLegatoCount = 0;
      if (!this.midiFilter.enabled.isOn() && !this.manualTrigger.isOn()) {
        this.env.engage.setValue(false);
      }
    } else if (p == this.manualTrigger) {
      this.amount = 1;
      this.env.engage.setValue(this.manualTrigger.isOn());
    } else if (p == this.targetTrigger) {
      this.env.engage.setValue(this.targetTrigger.isOn());
    } else if (p == this.envelopeMode) {
      EnvelopeMode triggerMode = this.envelopeMode.getEnum();
      this.env.stageMode.setValue(triggerMode.stageMode);
      this.env.oneshot.setValue(triggerMode.oneshot);
    } else if (p == this.triggerMode) {
      this.env.resetMode.setValue(this.triggerMode.getEnum() == TriggerMode.RESET);
    }
  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    // double level = LXUtils.lerp(100, LXUtils.lerp(this.floor.getValue(), this.ceiling.getValue(), this.amount * this.env.getValue()), enabledAmount);
    double level = LXUtils.lerp(100, 100 * this.env.getValue(), enabledAmount);
    if (level == 0) {
      setColors(LXColor.BLACK);
    } else if (level < 100) {
      int mask = LXColor.gray(level);
      int alpha = 0x100;
      for (LXPoint p : model.points) {
        colors[p.index] = LXColor.multiply(colors[p.index], mask, alpha);
      }
    }
  }

  private int midiLegatoCount = 0;

  @Override
  public void noteOnReceived(MidiNoteOn note) {
    ++this.midiLegatoCount;
    TriggerMode triggerMode = this.triggerMode.getEnum();
    if ((triggerMode == TriggerMode.LEGATO) && (this.midiLegatoCount > 1)) {
      return;
    }

    float velocity = 0;
    float velResponse = this.midiVelocityResponse.getValuef() / 100;
    float scaleVelocity = (note.getVelocity() - this.midiFilter.minVelocity.getValuef() + 1) / this.midiFilter.velocityRange.getValuef();
    if (velResponse >= 0) {
      velocity = LXUtils.lerpf(1, scaleVelocity, velResponse);
    } else {
      velocity = LXUtils.lerpf(1, 1 - scaleVelocity, -velResponse);
    }

    float noteResponse = this.midiNoteResponse.getValuef() / 100;
    if (noteResponse >= 0) {
      float noteVelocity = (note.getPitch() - this.midiFilter.minNote.getValuef() + 1) / this.midiFilter.noteRange.getValuef();
      this.amount = velocity * LXUtils.lerpf(1, noteVelocity, noteResponse);
    } else {
      float noteVelocity = (this.midiFilter.minNote.getValuef() + this.midiFilter.noteRange.getValuef() + 1 - note.getPitch()) / this.midiFilter.noteRange.getValuef();
      this.amount = velocity * LXUtils.lerpf(1, noteVelocity, -noteResponse);
    }

    if (this.env.engage.isOn()) {
      switch (triggerMode) {
      case RETRIG:
        this.env.retrig.setValue(true);
        break;
      case RESET:
        this.env.engage.setValue(false);
        this.env.engage.setValue(true);
        break;
      case LEGATO:
        break;
      }
    } else {
      this.env.engage.setValue(true);
    }
  }

  @Override
  public void noteOffReceived(MidiNote note) {
    this.midiLegatoCount = Math.max(0, this.midiLegatoCount - 1);
    if (this.midiLegatoCount == 0) {
      this.env.engage.setValue(false);
    }
  }

  @Override
  public void dispose() {
    this.midiFilter.enabled.removeListener(this);
    super.dispose();
  }

}