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

package heronarts.lx.parameter;

import heronarts.lx.LX;

/**
 * A boolean parameter which is momentary and supports instantaneous toggling
 * to true. Whenever the value is set to true the parameter listeners are fired,
 * and then the value is then immediately set back to false.
 */
public class TriggerParameter extends BooleanParameter {

  private Runnable onTrigger = null;

  public TriggerParameter(String label) {
    this(label, null);
  }

  public TriggerParameter(String label, Runnable onTrigger) {
    super(label, false);
    setMode(Mode.MOMENTARY);
    addListener(this.listener);
    onTrigger(onTrigger);
  }

  @Override
  public TriggerParameter setDescription(String description) {
    return (TriggerParameter) super.setDescription(description);
  }

  private final LXParameterListener listener = p -> {
    if (isOn()) {
      if (this.onTrigger != null) {
        this.onTrigger.run();
      }
      setValue(false);
    }
  };

  public TriggerParameter onTrigger(Runnable onTrigger) {
    if (this.onTrigger != null) {
      LX.error(new Exception(), "WARNING / SHOULDFIX: Overwriting previous onTrigger on TriggerParameter: " + getCanonicalPath());
    }
    this.onTrigger = onTrigger;
    return this;
  }

  public TriggerParameter trigger() {
    setValue(true);
    return this;
  }

  @Override
  public BooleanParameter setMode(Mode mode) {
    if (mode != Mode.MOMENTARY) {
      throw new IllegalArgumentException("TriggerParameter may only have MOMENTARY mode");
    }
    super.setMode(mode);
    return this;
  }

  @Override
  public void dispose() {
    removeListener(this.listener);
    this.onTrigger = null;
    super.dispose();
  }

}
