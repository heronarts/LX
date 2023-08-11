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
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.utils.LXUtils;

@LXModulator.Global("Spring")
@LXModulator.Device("Spring")
@LXCategory(LXCategory.CORE)
public class Spring extends LXModulator implements LXOscComponent, LXNormalizedParameter {

  public final CompoundParameter position =
    new CompoundParameter("Position", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Position of the spring");

  public final CompoundParameter tension =
    new CompoundParameter("Tension", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Tension of the spring");

  public final CompoundParameter friction =
    new CompoundParameter("Friction", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Friction of the spring");

  public final CompoundParameter bounce =
    new CompoundParameter("Bounce", .5)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Bounce coefficiant if the spring strikes the end of its bounds");

  public Spring() {
    this("Spring");
  }

  public Spring(String label) {
    super(label);
    addParameter("position", this.position);
    addParameter("tension", this.tension);
    addParameter("friction", this.friction);
    addParameter("bounce", this.bounce);
  }

  private double velocity = 0;

  @Override
  protected double computeValue(double deltaMs) {
    final double timeStep = deltaMs / 1000.;
    double position = getValue();
    final double distance = position - this.position.getValue();
    final double tension = this.tension.getValue() * 100;
    final double friction = this.friction.getValue() * 10;

    double accel = -tension * distance - friction * this.velocity;
    position += timeStep * (this.velocity + .5 * accel * timeStep);
    this.velocity += accel * timeStep;
    if (position < 0 || position > 1) {
      position = LXUtils.constrain(position, 0, 1);
      this.velocity = -this.velocity * this.bounce.getValue();;
    }
    return position;
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException("May not setNormalized() on Spring");
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

}
