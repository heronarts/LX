/**
 * Copyright 2023- Justin K. Belcher, Mark C. Slee, Heron Arts LLC
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
 * @author Justin K. Belcher <justin@jkb.studio>
 */

package heronarts.lx.dmx;

import heronarts.lx.LXCategory;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.modulator.LXTriggerSource;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;

/**
 * Modulator receiving a range from [min] to [max] within a DMX channel.
 * Outputs a normalized value and a boolean indicator of whether
 * the DMX value is within the range.
 */
@LXModulator.Global("DMX Range")
@LXModulator.Device("DMX Range")
@LXCategory(LXCategory.DMX)
public class DmxRangeModulator extends DmxModulator implements LXTriggerSource {

  public final DiscreteParameter min =
    new DiscreteParameter("Min", 0, 256)
    .setDescription("Minimum input value for range");

  public final DiscreteParameter max =
    new DiscreteParameter("Max", 255, 0, 256)
    .setDescription("Maximum input value for range");

  public final BooleanParameter out =
    new BooleanParameter("Out", false)
    .setDescription("TRUE when DMX value is within the range [min, max], inclusive");

  public DmxRangeModulator() {
    this("DMX Range");
  }

  public DmxRangeModulator(String label) {
    super(label);
    addParameter("min", this.min);
    addParameter("max", this.max);
    addParameter("out", this.out);
  }

  private boolean internal = false;

  @Override
  public void onParameterChanged(LXParameter p) {
    if (this.internal) {
      return;
    }

    this.internal = true;
    if (p == this.min) {
      final int min = this.min.getValuei();
      if (this.max.getValuei() < min) {
        this.max.setValue(min);
      }
    } else if (p == this.max) {
      final int max = this.max.getValuei();
      if (this.min.getValuei() > max) {
        this.min.setValue(max);
      }
    }
    this.internal = false;
  }

  @Override
  protected double computeValue(double deltaMs) {
    final int min = this.min.getValuei();
    final int max = this.max.getValuei();

    final int dmx = this.lx.engine.dmx.getValuei(
        this.universe.getValuei(),
        this.channel.getValuei()
        );

    if (dmx >= min && dmx <= max) {
      this.out.setValue(true);
      if (max == min) {
        return 1;
      }
      return ((double) dmx - min) / (max - min);
    } else {
      this.out.setValue(false);
      return 0;
    }
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.out;
  }
}
