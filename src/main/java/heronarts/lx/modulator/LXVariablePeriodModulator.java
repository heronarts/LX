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

package heronarts.lx.modulator;

import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;

public abstract class LXVariablePeriodModulator extends LXRangeModulator {

  public static enum ClockMode {
    FAST,
    SLOW,
    SYNC;

    @Override
    public String toString() {
      switch (this) {
      case FAST: return "Fast";
      case SLOW: return "Slow";
      default:
      case SYNC: return "Sync";
      }
    }
  };

  public final EnumParameter<ClockMode> clockMode =
    new EnumParameter<ClockMode>("Clock Mode", ClockMode.FAST)
    .setDescription("Clock mode of the modulator");

  public final CompoundParameter periodFast =
    new CompoundParameter("Period", 1000, 100, 60000)
    .setDescription("Sets the period of the modulator in msecs")
    .setExponent(4)
    .setUnits(LXParameter.Units.MILLISECONDS);

  public final CompoundParameter periodSlow =
    new CompoundParameter("Period", 10000, 1000, 900000)
    .setDescription("Sets the period of the modulator in msecs")
    .setExponent(4)
    .setUnits(LXParameter.Units.MILLISECONDS);

  protected LXVariablePeriodModulator(String label, LXParameter startValue, LXParameter endValue, LXParameter periodMs) {
    super(label, startValue, endValue, periodMs);
    addParameter("clockMode", this.clockMode);
    addParameter("periodFast", this.periodFast);
    addParameter("periodSlow", this.periodSlow);
  }

  private boolean inClockUpdate = false;

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.clockMode) {
      this.inClockUpdate = true;
      switch (this.clockMode.getEnum()) {
      case FAST:
        setPeriod(this.periodFast);
        this.tempoSync.setValue(false);
        break;
      case SLOW:
        setPeriod(this.periodSlow);
        this.tempoSync.setValue(false);
        break;
      case SYNC:
        this.tempoSync.setValue(true);
        break;
      }
      this.inClockUpdate = false;
    } else if (p == this.tempoSync) {
      if (this.tempoSync.isOn()) {
        this.clockMode.setValue(ClockMode.SYNC);
      } else if (!this.inClockUpdate) {
        this.clockMode.setValue(ClockMode.FAST);
      }
    }
  }

}
