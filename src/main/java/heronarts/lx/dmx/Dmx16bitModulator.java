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

/**
 * A modulator using two channels of DMX data for high resolution
 */
@LXModulator.Global("DMX 16-bit")
@LXModulator.Device("DMX 16-bit")
@LXCategory(LXCategory.DMX)
public class Dmx16bitModulator extends DmxModulator {

  public Dmx16bitModulator() {
    this("DMX 16-bit");
  }

  public Dmx16bitModulator(String label) {
    super(label);
    this.channel.setRange(0, LXDmxEngine.MAX_CHANNEL - 1);
  }

  @Override
  protected double computeValue(double deltaMs) {
    final int universe = this.universe.getValuei();
    final int channel = this.channel.getValuei();

    final byte byte1 = this.lx.engine.dmx.getByte(universe, channel);
    final byte byte2 = this.lx.engine.dmx.getByte(universe, channel + 1);

    return (((byte1 & 0xff) << 8) | (byte2 & 0xff)) / 65535.;
  }
}
