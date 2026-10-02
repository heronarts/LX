/**
 * Copyright 2026- Mark C. Slee, Heron Arts LLC
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
 * @author Dan Oved
 */

package heronarts.lx.modulator;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.Tempo;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.ObjectParameter;
import heronarts.lx.parameter.QuantizedTriggerParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

@LXModulator.Global("Sample + Hold")
@LXModulator.Device("Sample + Hold")
@LXCategory(LXCategory.CORE)
public class SampleAndHold extends LXModulator implements LXNormalizedParameter, LXTriggerSource, LXOscComponent {

  public final CompoundParameter input =
    new CompoundParameter("Input", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Signal to be sampled, map a modulation source here");

  private static Tempo.Quantization[] quantizations() {
    final Tempo.Division[] divisions = Tempo.Division.values();
    final Tempo.Quantization[] q = new Tempo.Quantization[divisions.length+1];
    q[0] = Tempo.Quantization.NONE;
    int i = 1;
    for (Tempo.Division d : divisions) {
      q[i++] = d;
    }
    return q;
  }

  public final ObjectParameter<Tempo.Quantization> tempoQuantization =
    new ObjectParameter<Tempo.Quantization>("Quantization", quantizations())
    .setDescription("Tempo division when in sync mode");

  public final BooleanParameter tempoSync =
    new BooleanParameter("Sync", false)
    .setDescription("Whether the sample + hold automatically syncs at tempo intervals");

  public final QuantizedTriggerParameter sample;

  public final CompoundParameter smoothAmount =
    new CompoundParameter("Smooth Amount", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Smoothing amount");

  public final BoundedParameter smoothRangeMs =
    new BoundedParameter("Range", 1000, 100, 60000)
    .setUnits(BoundedParameter.Units.MILLISECONDS)
    .setDescription("Range of smoothing window control");

  public final TriggerParameter triggerOut =
    new TriggerParameter("Trigger Out")
    .setDescription("Fires whenever a new value is sampled");

  private double hold = 0;

  public SampleAndHold(LX lx) {
    super("Sample + Hold");

    this.sample =
      new QuantizedTriggerParameter(lx, "Sample", this.tempoQuantization, this::sample)
      .setDescription("Captures the current signal value and holds it");

    addParameter("input", this.input);
    addParameter("tempoSync", this.tempoSync);
    addParameter("tempoQuantization", this.tempoQuantization);
    addParameter("sample", this.sample);
    addParameter("smoothAmount", this.smoothAmount);
    addParameter("smoothRangeMs", this.smoothRangeMs);
    addParameter("triggerOut", this.triggerOut);
  }

  private void sample() {
    this.hold = this.input.getValue();
    this.triggerOut.trigger();
  }

  @Override
  protected double computeValue(double deltaMs) {
    if (this.tempoSync.isOn()) {
      Tempo.Quantization quantization = this.tempoQuantization.getObject();
      if (quantization.hasDivision() && quantization.getDivision().isActive()) {
        sample();
      }
    }

    return LXUtils.lerp(
      getValue(),
      this.hold,
      LXUtils.min(1, deltaMs / (this.smoothAmount.getValue() * this.smoothRangeMs.getValue()))
    );
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.triggerOut;
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException("SampleAndHold does not support setNormalized()");
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

  private static final String KEY_HOLD = "hold";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_HOLD, getValue());
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(KEY_HOLD)) {
      setValue(this.hold = obj.get(KEY_HOLD).getAsDouble());
    }

  }

}
