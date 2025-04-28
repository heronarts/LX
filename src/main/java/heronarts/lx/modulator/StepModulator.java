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

import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.TriggerParameter;

public abstract class StepModulator extends LXPeriodicModulator {

  public static final int MAX_STEPS = 16;

  public enum TriggerMode {
    INTERNAL("Internal"),
    EXTERNAL("External");

    public final String label;

    private TriggerMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final DiscreteParameter numSteps =
    new DiscreteParameter("Num Steps", MAX_STEPS, 1, MAX_STEPS+1)
    .setDescription("Number of active steps");

  public final EnumParameter<TriggerMode> triggerMode =
    new EnumParameter<TriggerMode>("Trig Mode", TriggerMode.INTERNAL)
    .setDescription("Whether triggers are internally or externally generated");

  public final CompoundParameter stepTimeMs =
    new CompoundParameter("Step Time", 500, 50, 5000)
    .setUnits(CompoundParameter.Units.MILLISECONDS)
    .setDescription("Manual Step Time");

  public final TriggerParameter triggerIn =
    new TriggerParameter("Trigger In", this::onTriggerIn)
    .setDescription("Advances the step sequencer manually");

  public final TriggerParameter triggerOut =
    new TriggerParameter("Trig Out")
    .setDescription("Fires on an active step");

  private final FunctionalParameter totalMs = new FunctionalParameter() {
    @Override
    public double getValue() {
      return triggerMode.getEnum() == TriggerMode.EXTERNAL ?
        Double.POSITIVE_INFINITY :
        stepTimeMs.getValue();
    }
  };

  public final BooleanParameter[] activeStep = new BooleanParameter[MAX_STEPS];

  protected int step = 0;

  protected StepModulator(String label) {
    super(label, null);
    setPeriod(this.totalMs);
    this.tempoSync.setValue(true);

    addParameter("numSteps", this.numSteps);
    addParameter("triggerMode", this.triggerMode);
    addParameter("triggerIn", this.triggerIn);
    addParameter("triggerOut", this.triggerOut);

    for (int i = 0; i < this.activeStep.length; ++i) {
      this.activeStep[i] =
        new BooleanParameter("Trig-" + (i+1))
        .setMode(BooleanParameter.Mode.MOMENTARY)
        .setDescription("Fires on step " + (i+1));
      addParameter("activeStep-" + (i+1), this.activeStep[i]);
    }
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.triggerMode) {
      if (this.triggerMode.getEnum() == TriggerMode.EXTERNAL) {
        this.tempoSync.setValue(false);
        setBasis(0);
      }
    } else if (p == this.numSteps) {
      nextStep(0, false);
    }
  }

  @Override
  protected final double computeValue(double deltaMs, double basis) {
    if (loop() || finished()) {
      nextStep(1, true);
    }
    return getStepValue(deltaMs, basis);
  }

  protected double getStepValue(double deltaMs, double basis) {
    return 0;
  }

  @Override
  protected double computeBasis(double basis, double value) {
    throw new UnsupportedOperationException("StepSequencer does not support computeBasis");
  }

  private void nextStep(int increment, boolean trigger) {
    if (this.tempoSync.isOn() && this.tempoLock.isOn()) {
      this.step = this.lx.engine.tempo.getCycleCount(this.tempoDivision.getEnum()) % this.numSteps.getValuei();
    } else {
      this.step = (this.step + increment) % this.numSteps.getValuei();
    }
    for (int i = 0; i < this.activeStep.length; ++i) {
      this.activeStep[i].setValue(i == this.step);
    }
    onStep(trigger);
  }

  protected void onStep(boolean trigger) {

  }

  private void onTriggerIn() {
    if (this.running.isOn() && (this.triggerMode.getEnum() == TriggerMode.EXTERNAL)) {
      nextStep(1, true);
    }
  }


}
