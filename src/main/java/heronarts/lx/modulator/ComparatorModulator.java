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
import heronarts.lx.parameter.EnumParameter;

@LXModulator.Global("Comparator")
@LXModulator.Device("Comparator")
@LXCategory(LXCategory.TRIGGER)
public class ComparatorModulator extends LXModulator implements LXTriggerSource, LXOscComponent {

  public enum Comparison {
    GREATER(">"),
    GREATER_EQUAL(">="),
    LESS("<"),
    LESS_EQUAL("<="),
    EQUAL("=="),
    NOT_EQUAL("!=");

    public final String label;

    private Comparison(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }

    public boolean compare(double left, double right) {
      switch (this) {
      case EQUAL:
        return left == right;
      case GREATER:
        return left > right;
      case GREATER_EQUAL:
        return left >= right;
      case LESS:
        return left < right;
      case LESS_EQUAL:
        return left <= right;
      case NOT_EQUAL:
        return left != right;
      }
      return false;
    }
  }

  public final CompoundParameter in1 =
    new CompoundParameter("In-1", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("First input value");

  public final CompoundParameter in2 =
    new CompoundParameter("In-2", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Second input value");

  public final EnumParameter<Comparison> comparison =
    new EnumParameter<Comparison>("Comparison", Comparison.LESS)
    .setDescription("Comparison to make between inputs");

  public final BooleanParameter out =
    new BooleanParameter("Out", false)
    .setDescription("Output value");

  public ComparatorModulator() {
    this("Comparator");
  }

  public ComparatorModulator(String label) {
    super(label);
    addParameter("in1", this.in1);
    addParameter("in2", this.in2);
    addParameter("comparison", this.comparison);
    addParameter("out", this.out);
    setMappingSource(false);
  }

  @Override
  protected double computeValue(double deltaMs) {
    final double in1 = this.in1.getValue();
    final double in2 = this.in2.getValue();
    final boolean result = this.comparison.getEnum().compare(in1, in2);
    this.out.setValue(result);
    return result ? 1 : 0;
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.out;
  }


}
