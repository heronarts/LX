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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.Tempo;
import heronarts.lx.Tempo.Quantization;
import heronarts.lx.utils.LXUtils;

public class QuantizedTriggerParameter extends TriggerParameter {

  private final LX lx;

  private ObjectParameter<Tempo.Quantization> quantization;

  public final BooleanParameter pending;

  public final TriggerParameter out;

  private int semaphore = 0;

  private OutputMode outputMode = OutputMode.SINGLE;

  private static final Map<Tempo.Division, List<QuantizedTriggerParameter>> pendingInstances =
    new HashMap<Tempo.Division, List<QuantizedTriggerParameter>>();

  static {
    // Initialize pending array for all valid tempo divisions
    for (Tempo.Division division : Tempo.Division.values()) {
      pendingInstances.put(division, new ArrayList<QuantizedTriggerParameter>());
    }
  }

  private final LXParameterListener quantizationListener = this::_onQuantizationChanged;

  private void _onQuantizationChanged(LXParameter p) {
    if (getQuantization() == Tempo.Quantization.NONE) {
      resolve();
    } else if (this.pending.isOn()) {
      // The quantization value has changed while the parameter is pending,
      _addPendingInstance();
    }
  };

  private void _addPendingInstance() {
    Tempo.Division division = this.quantization.getObject().getDivision();
    if (division != null) {
      // Register it in the pendingInstances array if it is not already there, avoid weird
      // case where pending division goes from A->B->A all while it hasn't yet fired, so as
      // to avoid duplicate instance in pending[A]. We are basically enforcing Set functionality
      // on the cheaper small array storage
      List<QuantizedTriggerParameter> pending = pendingInstances.get(division);
      if (!pending.contains(this)) {
        pending.add(this);
      }
    }
  }

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
      this.quantization.addListener(this.quantizationListener, true);
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
    if (this.outputMode == OutputMode.SINGLE) {
      this.semaphore = LXUtils.min(1, this.semaphore);
    }
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
    if (!quantization.hasDivision() || quantization.getDivision().isActive()) {
      resolve();
    } else {
      this.pending.setValue(true);
      _addPendingInstance();
    }
  }

  /**
   * Cancels the pending state of this trigger
   */
  public void cancel() {
    this.semaphore = 0;
    this.pending.setValue(false);
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
    cancel();
    setQuantization(null);
    this.pending.dispose();
    this.out.dispose();
    super.dispose();
  }

  /**
   * Invoked from the Tempo engine to resolve all pending parameters when a
   * particular launch quantization is fired. Should not be invoked manually.
   *
   * @param lx The LX instance
   * @param division Tempo division to resolve
   */
  public static void resolve(LX lx, Tempo.Division division) {
    final List<QuantizedTriggerParameter> instances = pendingInstances.get(division);
    if (instances.isEmpty()) {
      return;
    }

    // Make a copy of the instances we need to trigger - we do this because it is in theory
    // possible for the resolution to create a chain of modulation/trigger mappings that
    // could end up modifying the pendingInstances array. So we copy what we've got first
    // and clear it to avoid concurrent array modification exceptions
    QuantizedTriggerParameter[] copy = instances.toArray(new QuantizedTriggerParameter[0]);
    instances.clear();

    for (QuantizedTriggerParameter trigger : copy) {
      // Check that the criteria are still met... pending state or quantization may have changed
      // and we just let the cleanup happen lazily here by ignoring those that no longer match
      if (trigger.lx == lx &&
          trigger.pending.isOn() &&
          trigger.getQuantization().getDivision() == division) {
        trigger.resolve();
      }
    }
  }

}
