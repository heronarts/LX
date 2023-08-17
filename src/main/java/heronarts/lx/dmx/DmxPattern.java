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

package heronarts.lx.dmx;

import heronarts.lx.LX;
import heronarts.lx.LXComponentName;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.pattern.LXPattern;

@LXComponentName("DMX")
public class DmxPattern extends LXPattern {

  public final DiscreteParameter universe =
    new DiscreteParameter("Universe", 0, LXDmxEngine.MAX_UNIVERSE)
    .setDescription("Starting universe");

  public final DiscreteParameter channel =
    new DiscreteParameter("Channel", 0, 510)
    .setDescription("Starting channel");

  public final EnumParameter<LXDmxEngine.ByteOrder> byteOrder =
    new EnumParameter<LXDmxEngine.ByteOrder>("Byte Order", LXDmxEngine.ByteOrder.RGB)
    .setDescription("DMX input byte order");

  public DmxPattern(LX lx) {
    super(lx);
    addParameter("universe", this.universe);
    addParameter("channel", this.channel);
    addParameter("byteOrder", this.byteOrder);
  }

  @Override
  protected void run(double deltaMs) {
    final LXDmxEngine.ByteOrder byteOrder = this.byteOrder.getEnum();
    int universe = this.universe.getValuei();
    int channel = this.channel.getValuei();
    for (LXPoint p : model.points) {
      colors[p.index] = lx.engine.dmx.getColor(universe, channel, byteOrder);
      channel += 3;
      if (channel >= 510) {
        channel = 0;
        if (++universe >= LXDmxEngine.MAX_UNIVERSE) {
          break;
        }
      }
    }
  }

}
