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
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.utils.LXUtils;

@LXModulator.Global("Operator")
@LXModulator.Device("Operator")
@LXCategory(LXCategory.CORE)
public class OperatorModulator extends LXModulator implements LXNormalizedParameter, LXOscComponent {

  public enum Operation {
    LERP("Lerp"),
    ADD("Add"),
    SUBTRACT("Subtract"),
    DIFFERENCE("Difference"),
    MULTIPLY("Multiply"),
    DIVIDE("Divide"),
    RATIO("Ratio"),
    MIN("Min"),
    MAX("Max"),
    INVERT("Invert");

    public final String label;

    private Operation(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }

    public double operate(double left, double right, double amount) {
      switch (this) {
      case LERP:
        return LXUtils.lerp(left, right, amount);
      case MULTIPLY:
        return left * LXUtils.lerp(1, right, amount);
      case ADD:
        return LXUtils.min(1, left + right * amount);
      case SUBTRACT:
        return LXUtils.max(0, left - right * amount);
      case DIFFERENCE:
        return LXUtils.lerp(left, Math.abs(left - right), amount);
      case MIN:
        return LXUtils.lerp(left, Math.min(left, right), amount);
      case MAX:
        return LXUtils.lerp(left, Math.max(left, right), amount);
      case DIVIDE:
        return LXUtils.min(1, LXUtils.lerp(left, left / right, amount));
      case RATIO:
        double ratio = (left < right) ? left / right : right / left;
        return LXUtils.lerp(left, LXUtils.constrain(ratio, 0, 1), amount);
      case INVERT:
        return LXUtils.lerp(left, 1 - left, amount);
      }
      return 0;
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

  public final EnumParameter<Operation> operation =
    new EnumParameter<Operation>("Operation", Operation.LERP)
    .setDescription("Operation to apply between inputs");

  public final CompoundParameter amount =
    new CompoundParameter("Amount", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Amount to blend from input 1 to the result of the operation");

  public OperatorModulator() {
    this("Operator");
  }

  public OperatorModulator(String label) {
    super(label);
    addParameter("in1", this.in1);
    addParameter("in2", this.in2);
    addParameter("amount", this.amount);
    addParameter("operation", this.operation);
  }

  @Override
  protected double computeValue(double deltaMs) {
    return this.operation.getEnum().operate(
      this.in1.getValue(),
      this.in2.getValue(),
      this.amount.getValue()
    );
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException("Cannot setNormalized() on OperatorModulator");
  }

  @Override
  public double getNormalized() {
    return getValue();
  }


}
