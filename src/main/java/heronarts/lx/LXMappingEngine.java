/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx;

import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXNormalizedParameter;

public class LXMappingEngine {

  public enum Mode {
    OFF,
    MIDI,
    MODULATION_SOURCE,
    MODULATION_TARGET,
    TRIGGER_SOURCE,
    TRIGGER_TARGET
  };

  private LXNormalizedParameter controlTarget = null;

  public final EnumParameter<Mode> mode = new EnumParameter<Mode>("Mode", Mode.OFF);

  LXMappingEngine() {
    this.mode.addListener((p) -> {
      this.controlTarget = null;
    });
  }

  public LXMappingEngine setMode(Mode mode) {
    this.mode.setValue(mode);
    return this;
  }

  public Mode getMode() {
    return this.mode.getEnum();
  }

  public LXMappingEngine setControlTarget(LXNormalizedParameter controlTarget) {
    this.controlTarget = controlTarget;
    return this;
  }

  public LXNormalizedParameter getControlTarget() {
    return this.controlTarget;
  }

}
