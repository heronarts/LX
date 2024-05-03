/**
 * Copyright 2017- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.parameter;

/**
 * Parameter which contains a mutable String value.
 */
public class StringParameter extends LXListenableParameter {

  private String defaultString, string;

  public StringParameter(String label) {
    this(label, "");
  }

  public StringParameter(String label, String string) {
    super(label);
    this.defaultString = this.string = string;
  }

  @Override
  public StringParameter setDescription(String description) {
    super.setDescription(description);
    return this;
  }

  @Override
  public LXParameter reset() {
    this.string = this.defaultString;
    super.reset();
    return this;
  }

  @Override
  public LXParameter reset(double value) {
    throw new UnsupportedOperationException("StringParameter cannot be reset to a numeric value");
  }

  public StringParameter reset(String string) {
    this.defaultString = string;
    return setValue(this.defaultString);
  }

  public StringParameter setValue(String string) {
    return setValue(string, false);
  }

  public StringParameter setValue(String string, boolean update) {
    if (this.string == null) {
      if (string != null) {
        this.string = string;
        update = true;
      }
    } else if (!this.string.equals(string)) {
      this.string = string;
      update = true;
    }
    if (update) {
      incrementValue(1);
    }
    return this;
  }

  @Override
  protected double updateValue(double value) {
    return value;
  }

  public String getString() {
    return this.string;
  }

}
