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
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.parameter.TriggerParameter;

/**
 * Modulator that provides randomized triggering on an interval
 */
@LXModulator.Global("Interval")
@LXModulator.Device("Interval")
@LXCategory(LXCategory.TRIGGER)
public class Interval extends LXPeriodicModulator implements LXTriggerSource, LXOscComponent {

  public final CompoundParameter periodMs =
    new CompoundParameter("Interval", 1000, 10, 1000*60)
    .setExponent(3)
    .setUnits(CompoundParameter.Units.MILLISECONDS)
    .setDescription("Base interval for random trigger updates");

  public final CompoundParameter randomMs =
    new CompoundParameter("Random", 0, 0, 1000*60)
    .setExponent(3)
    .setUnits(CompoundParameter.Units.MILLISECONDS)
    .setDescription("Range of random time added to each interval");

  public final CompoundParameter chance =
    new CompoundParameter("Chance", 100, 0, 100)
    .setUnits(CompoundParameter.Units.PERCENT)
    .setDescription("Chance that the trigger fires on each interval");

  private double randomInterval = 0;

  private final FunctionalParameter totalMs = new FunctionalParameter() {
    @Override
    public double getValue() {
      return periodMs.getValue() + randomInterval * randomMs.getValue();
    }
  };

  public final TriggerParameter triggerOut =
    new TriggerParameter("Trigger Out")
    .setDescription("Engages when the interval triggers");

  public Interval() {
    this("Interval");
  }

  private Interval(String label) {
    super(label, null);
    setPeriod(this.totalMs);

    addParameter("periodMs", this.periodMs);
    addParameter("randomMs", this.randomMs);
    addParameter("chance", this.chance);
    addParameter("triggerOut", this.triggerOut);

    setDescription("Trigger that fires on randomized interval");
    setMappingSource(false);
  }

  @Override
  protected double computeValue(double deltaMs, double basis) {
    if (loop() || finished()) {
      double d = Math.random();
      if (d*100 < this.chance.getValue()) {
        this.randomInterval = Math.random();
        this.triggerOut.trigger();
        return 1;
      }
    }
    return 0;
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.triggerOut;
  }

  @Override
  protected double computeBasis(double basis, double value) {
    return 0;
  }

}
