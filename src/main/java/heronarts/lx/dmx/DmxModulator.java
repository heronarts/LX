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

import heronarts.lx.LXCategory;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXNormalizedParameter;

@LXModulator.Global("DMX")
@LXModulator.Device("DMX")
@LXCategory(LXCategory.CORE)
public class DmxModulator extends LXModulator implements LXOscComponent, LXNormalizedParameter {

  public final DiscreteParameter universe =
    new DiscreteParameter("Universe", 0, LXDmxEngine.MAX_UNIVERSE)
    .setDescription("DMX universe");

  public final DiscreteParameter channel =
    new DiscreteParameter("Channel", 0, LXDmxEngine.MAX_CHANNEL)
    .setDescription("DMX channel");

  public DmxModulator() {
    this("DMX");
  }

  public DmxModulator(String label) {
    super(label);
    addParameter("universe", this.universe);
    addParameter("channel", this.channel);
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException("May not setNormalized on DmxModulator");
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

  @Override
  protected double computeValue(double deltaMs) {
    return lx.engine.dmx.getNormalized(
      this.universe.getValuei(),
      this.channel.getValuei()
    );
  }

}
