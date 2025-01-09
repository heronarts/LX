/**
 * Copyright 2025- Mark C. Slee, Heron Arts LLC
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

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.Tempo;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.ObjectParameter;
import heronarts.lx.parameter.QuantizedTriggerParameter;
import heronarts.lx.parameter.TriggerParameter;

@LXModulator.Global("Quantizer")
@LXModulator.Device("Quantizer")
@LXCategory(LXCategory.TRIGGER)
public class Quantizer extends LXModulator implements LXTriggerSource, LXOscComponent {

  public final ObjectParameter<Tempo.Quantization> quantization =
    Tempo.newQuantizationParameter(
      "Quantization",
      "Division to use when quantizing the trigger"
    );

  public final QuantizedTriggerParameter engage;

  public final TriggerParameter triggerOut =
    new TriggerParameter("Trigger Out")
    .setDescription("Fires when the quantization period has elapsed");

  public Quantizer(LX lx) {
    this(lx, "Quantizer");
  }

  public Quantizer(LX lx, String label) {
    super(label);
    this.engage =
      new QuantizedTriggerParameter(lx, "Trigger", this.quantization, () -> {
        this.triggerOut.trigger();
      })
      .setDescription("Engages the quantizer");

    addParameter("triggerIn", this.engage);
    addParameter("quantization", this.quantization);
    addParameter("triggerOut", this.triggerOut);
    setDescription("Trigger that is quantied to a tempo division");
    setMappingSource(false);
  }

  @Override
  protected double computeValue(double deltaMs) {
    return this.triggerOut.getValue();
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.triggerOut;
  }

}
