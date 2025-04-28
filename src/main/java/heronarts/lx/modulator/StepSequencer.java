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
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;

@LXModulator.Global("Step Sequencer")
@LXModulator.Device("Step Sequencer")
@LXCategory(LXCategory.TRIGGER)
public class StepSequencer extends StepModulator implements LXTriggerSource, LXOscComponent {

  public final BooleanParameter[] steps = new BooleanParameter[MAX_STEPS];

  public StepSequencer() {
    super("Step Sequencer");
    setMappingSource(false);

    for (int i = 0; i < this.steps.length; ++i) {
      this.steps[i] =
        new BooleanParameter("Step-" + (i+1), false)
        .setDescription("Whether the sequencer triggers on step " + (i+1));
      addParameter("step-" + (i+1), this.steps[i]);

    }

    setDescription("Step sequencer that triggers on active steps");
  }

  @Override
  protected void onStep(boolean trigger) {
    if (trigger && this.steps[this.step].isOn()) {
      this.triggerOut.trigger();
    }
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.triggerOut;
  }

}
