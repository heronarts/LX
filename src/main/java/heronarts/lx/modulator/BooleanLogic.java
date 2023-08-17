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
import heronarts.lx.parameter.EnumParameter;

@LXModulator.Global("Logic")
@LXModulator.Device("Logic")
@LXCategory(LXCategory.TRIGGER)
public class BooleanLogic extends LXModulator implements LXTriggerSource, LXOscComponent {

  public enum Operator {
    AND("AND"),
    OR("OR"),
    XOR("XOR");

    public final String label;

    private Operator(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }

    public boolean operate(boolean left, boolean right) {
      switch (this) {
      case AND:
        return left && right;
      case OR:
        return left || right;
      case XOR:
        return left ^ right;
      }
      return false;
    }
  }

  public final BooleanParameter in1 =
    new BooleanParameter("In-1", false)
    .setDescription("Input value 1");

  public final BooleanParameter in2 =
    new BooleanParameter("In-2", false)
    .setDescription("Input value 2");

  public final BooleanParameter in3 =
    new BooleanParameter("In-3", false)
    .setDescription("Input value 3");

  public final BooleanParameter in4 =
    new BooleanParameter("In-4", false)
    .setDescription("Input value 4");

  public final BooleanParameter not1 =
    new BooleanParameter("Not-1", false)
    .setDescription("Inverts input 1");

  public final BooleanParameter not2 =
    new BooleanParameter("Not-2", false)
    .setDescription("Inverts input 2");

  public final BooleanParameter not3 =
    new BooleanParameter("Not-3", false)
    .setDescription("Inverts input 3");

  public final BooleanParameter not4 =
    new BooleanParameter("Not-4", false)
    .setDescription("Inverts input 4");

  public final EnumParameter<Operator> op1 =
    new EnumParameter<Operator>("Op-1", Operator.OR)
    .setDescription("Logical operator 1");

  public final EnumParameter<Operator> op2 =
    new EnumParameter<Operator>("Op-2", Operator.OR)
    .setDescription("Logical operator 2");

  public final EnumParameter<Operator> op3 =
    new EnumParameter<Operator>("Op-3", Operator.OR)
    .setDescription("Logical operator 3");

  public final BooleanParameter out =
    new BooleanParameter("Out", false)
    .setDescription("Output value");

  public BooleanLogic() {
    this("Logic");
  }

  public BooleanLogic(String label) {
    super(label);

    addParameter("in1", this.in1);
    addParameter("in2", this.in2);
    addParameter("in3", this.in3);
    addParameter("in4", this.in4);
    addParameter("not1", this.not1);
    addParameter("not2", this.not2);
    addParameter("not3", this.not3);
    addParameter("not4", this.not4);
    addParameter("op1", this.op1);
    addParameter("op2", this.op2);
    addParameter("op3", this.op3);
    addParameter("out", this.out);

    setMappingSource(false);
  }

  private boolean operand(BooleanParameter toggle, BooleanParameter invert) {
    return invert.isOn() ? !toggle.isOn() : toggle.isOn();
  }

  @Override
  protected double computeValue(double deltaMs) {
    boolean result = operand(this.in1, this.not1);
    result = this.op1.getEnum().operate(result, operand(this.in2, this.not2));
    result = this.op2.getEnum().operate(result, operand(this.in3, this.not3));
    result = this.op3.getEnum().operate(result, operand(this.in4, this.not4));
    this.out.setValue(result);
    return result ? 1 : 0;
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.out;
  }

}
