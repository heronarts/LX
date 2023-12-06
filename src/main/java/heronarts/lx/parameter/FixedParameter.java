/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
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

import heronarts.lx.LXComponent;

/**
 * A FixedParameter is an immutable parameter. It will throw a RuntimeException
 * if setValue() is attempted. Useful for anonymous placeholder values in places
 * that expect to use LXParameters.
 */
public class FixedParameter implements LXParameter {

  private final double value;

  private LXComponent parent;
  private String path;

  private Formatter formatter;

  public FixedParameter(double value) {
    this.value = value;
  }

  @Override
  public String getDescription() {
    return null;
  }

  @Override
  public LXParameter setComponent(LXComponent parent, String path) {
    if (parent == null || path == null) {
      throw new IllegalArgumentException("May not set null component or path");
    }
    if (this.parent != null || this.path != null) {
      throw new IllegalStateException("Component already set on this modulator: " + this);
    }
    this.parent = parent;
    this.path = path;
    return this;
  }

  @Override
  public LXComponent getParent() {
    return this.parent;
  }

  @Override
  public String getPath() {
    return this.path;
  }

  @Override
  public Formatter getFormatter() {
    return (this.formatter != null) ? this.formatter : getUnits();
  }

  @Override
  public FixedParameter setFormatter(Formatter formatter) {
    this.formatter = formatter;
    return this;
  }

  public Units getUnits() {
    return Units.NONE;
  }

  public Polarity getPolarity() {
    return Polarity.UNIPOLAR;
  }

  @Override
  public void dispose() {

  }

  @Override
  public LXParameter reset() {
    return this;
  }

  @Override
  public LXParameter setValue(double value) {
    throw new RuntimeException("Cannot invoke setValue on a FixedParameter");
  }

  @Override
  public double getValue() {
    return this.value;
  }

  @Override
  public String getLabel() {
    return null;
  }

}
