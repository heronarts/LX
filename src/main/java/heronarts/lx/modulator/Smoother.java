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
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.utils.LXUtils;

@LXModulator.Global("Smoother")
@LXModulator.Device("Smoother")
@LXCategory(LXCategory.CORE)
public class Smoother extends LXModulator implements LXOscComponent, LXNormalizedParameter {

  public final CompoundParameter input =
    new CompoundParameter("Input", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Input value to the smoother");

  public final CompoundParameter window =
    new CompoundParameter("Time", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Smoothing window time");

  public final BoundedParameter windowRangeMs =
    new BoundedParameter("Range", 1000, 100, 60000)
    .setUnits(BoundedParameter.Units.MILLISECONDS)
    .setDescription("Range of smoothing window control");

  public Smoother() {
    this("Smoother");
  }

  public Smoother(String label) {
    super(label);
    addParameter("input", this.input);
    addParameter("window", this.window);
    addParameter("windowRangeMs", this.windowRangeMs);
  }

  @Override
  protected double computeValue(double deltaMs) {
    // Hacked up remainder-lerp, ends up being a sort of weird
    // ease-out kind of thing that is slightly framerate dependent
    // but generally decent enough to start. In the future will
    // add more algorithm controls here
    return LXUtils.lerp(
      getValue(),
      this.input.getValue(),
      LXUtils.min(1, deltaMs / (this.window.getValue() * this.windowRangeMs.getValue()))
    );
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException("May not setNormalized() on Smoother");
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

}
