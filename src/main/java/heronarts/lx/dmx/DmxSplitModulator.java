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
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;

/**
 * Modulator receiving two ranges of input from one DMX channel.
 * Input     Output
 *  0        Output1 = 0    Output2 = 0
 *  1-127    Output1 = 0-1  Output2 = 0
 *  128-255  Output1 = 0    Output2 = 0-1
 *
 * @author Justin K. Belcher
 */
@LXModulator.Global("DMX Split")
@LXModulator.Device("DMX Split")
@LXCategory(LXCategory.DMX)
public class DmxSplitModulator extends DmxModulator {


  public final BooleanParameter isZero =
      new BooleanParameter("Zero", true)
      .setDescription("TRUE when DMX value is zero");

  public final BooleanParameter active1 =
      new BooleanParameter("Active1", false)
      .setDescription("TRUE when range 1 is active");

  public final BooleanParameter active2 =
      new BooleanParameter("Active2", false)
      .setDescription("TRUE when range 2 is active");

  public final CompoundParameter output1 =
      new CompoundParameter("Output1", 0)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Output for first range, moves 0-1 for DMX values 1-127");

  public final CompoundParameter output2 =
      new CompoundParameter("Output2", 0)
      .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
      .setDescription("Output for second range, moves 0-1 for DMX values 128-255");

  public DmxSplitModulator() {
    this("DMX Split");
  }

  public DmxSplitModulator(String label) {
    super(label);
    addParameter("isZero", this.isZero);
    addParameter("active1", this.active1);
    addParameter("active2", this.active2);
    addParameter("output1", this.output1);
    addParameter("output2", this.output2);
  }

  @Override
  protected double computeValue(double deltaMs) {
    final int dmx = this.lx.engine.dmx.getValuei(
      this.universe.getValuei(),
      this.channel.getValuei());

    if (dmx == 0) {
      this.isZero.setValue(true);
      this.active1.setValue(false);
      this.active2.setValue(false);
      this.output1.setValue(0);
      this.output2.setValue(0);
      return 0;
    } else if (dmx < 128) {
      double value = (dmx - 1) / 126.;
      this.isZero.setValue(false);
      this.active1.setValue(true);
      this.active2.setValue(false);
      this.output1.setValue(value);
      this.output2.setValue(0);
      return value;
    } else {
      double value = (dmx - 128) / 127.;
      this.isZero.setValue(false);
      this.active1.setValue(false);
      this.active2.setValue(true);
      this.output1.setValue(0);
      this.output2.setValue(value);
      return 0;  // modulator value is output1
    }
  }

}
