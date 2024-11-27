/**
 * Copyright 2024- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.parameter;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.Tempo;
import heronarts.lx.Tempo.Quantization;

public class QuantizedTriggerParameter extends TriggerParameter {

  private final LX lx;

  private ObjectParameter<Tempo.Quantization> quantization;

  public final BooleanParameter pending;

  public final TriggerParameter out;

  private int semaphore = 0;

  private OutputMode outputMode = OutputMode.SINGLE;

  private static final List<QuantizedTriggerParameter> instances =
    new ArrayList<QuantizedTriggerParameter>();

  private final LXParameterListener quantizationListener = p -> {
    if (getQuantization() == Tempo.Quantization.NONE) {
      resolve();
    }
  };

  public static enum OutputMode {
    /**
     * Output triggers a maximum of once per quantize event, even if multiple queues were set
     */
    SINGLE,

    /**
     * Output triggers as many times as the queue was set while pending
     */
    MULTIPLE
  };

  /**
   * Quantized parameter which uses the global launch quantization setting.
   */
  public static class Launch extends QuantizedTriggerParameter {
    public Launch(LX lx, String label) {
      this(lx, label, null);
    }

    public Launch(LX lx, String label, Runnable onTrigger) {
      super(lx, label, lx.engine.tempo.launchQuantization, onTrigger);
    }
  }

  public QuantizedTriggerParameter(LX lx, String label) {
    this(lx, label, null, null);
  }

  public QuantizedTriggerParameter(LX lx, String label, ObjectParameter<Tempo.Quantization> quantization) {
    this(lx, label, quantization, null);
  }

  public QuantizedTriggerParameter(LX lx, String label, Runnable onTrigger) {
    this(lx, label, null, onTrigger);
  }

  public QuantizedTriggerParameter(LX lx, String label, ObjectParameter<Tempo.Quantization> quantization, Runnable onTrigger) {
    super(label);
    this.lx = lx;
    this.out = new TriggerParameter(label).onTrigger(onTrigger);
    this.pending = new BooleanParameter(label + "Pending").setMode(BooleanParameter.Mode.MOMENTARY);
    setQuantization(quantization);
    instances.add(this);
  }

  public QuantizedTriggerParameter setQuantization(ObjectParameter<Tempo.Quantization> quantization) {
    if (this.quantization != null) {
      this.quantization.removeListener(this.quantizationListener);
    }
    this.quantization = quantization;
    if (this.quantization == null || !this.quantization.getObject().hasDivision()) {
      resolve();
    }
    if (this.quantization != null) {
      this.quantization.addListener(this.quantizationListener);
    }
    return this;
  }

  @Override
  public QuantizedTriggerParameter setDescription(String description) {
    super.setDescription(description);
    this.out.setDescription(description);
    return this;
  }

  @Override
  public QuantizedTriggerParameter onTrigger(Runnable onTrigger) {
    this.out.onTrigger(onTrigger);
    return this;
  }

  public QuantizedTriggerParameter setOutputMode(OutputMode outputMode) {
    this.outputMode = outputMode;
    return this;
  }

  private void increment() {
    switch (this.outputMode) {
    case MULTIPLE:
      ++this.semaphore;
      break;
    default:
    case SINGLE:
      this.semaphore = 1;
      break;
    }
  }

  private Tempo.Quantization getQuantization() {
    return (this.quantization == null) ? Tempo.Quantization.NONE : this.quantization.getObject();
  }

  @Override
  protected void _onTrigger() {
    increment();

    Quantization quantization = getQuantization();
    if (!quantization.hasDivision() || this.lx.engine.tempo.isDivisionActive(quantization.getDivision())) {
      resolve();
    } else {
      this.pending.setValue(true);
    }
  }

  /**
   * Resolves the pending state and fires the trigger
   */
  public void resolve() {
    this.pending.setValue(false);
    for (int i = 0; i < this.semaphore; ++i) {
      this.out.trigger();
    }
    this.semaphore = 0;
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.out;
  }

  @Override
  public void dispose() {
    instances.remove(this);
    setQuantization(null);
    this.pending.dispose();
    this.out.dispose();
    super.dispose();
  }

  public static void resolve(LX lx, Tempo.Division division) {
    for (QuantizedTriggerParameter trigger : instances) {
      if (trigger.lx == lx &&
          trigger.pending.isOn() &&
          trigger.getQuantization().getDivision() == division) {
        trigger.resolve();
      }
    }
  }

}
