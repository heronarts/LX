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
 * An LXParameter that has a value computed by a function, which may combine the
 * values of other parameters, or call some function, etc.
 */
public abstract class FunctionalParameter implements LXParameter {

  public interface Interface {
    public double getValue();
  }

  public static FunctionalParameter create(String label, Interface iface) {
    return new FunctionalParameter(label) {
      @Override
      public double getValue() {
        return iface.getValue();
      }
    };
  }

  public static FunctionalParameter create(Interface iface) {
    return new FunctionalParameter() {
      @Override
      public double getValue() {
        return iface.getValue();
      }
    };
  }

  private final String label;
  protected String description = null;

  private LXComponent parent;
  private String path;
  private Formatter formatter = null;

  protected FunctionalParameter() {
    this("FUNC-PARAM");
  }

  protected FunctionalParameter(String label) {
    this.label = label;
  }

  @Override
  public String getDescription() {
    return this.description;
  }

  public FunctionalParameter setDescription(String description) {
    this.description = description;
    return this;
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
  public Polarity getPolarity() {
    return Polarity.UNIPOLAR;
  }

  @Override
  public Formatter getFormatter() {
    return (this.formatter != null) ? this.formatter : getUnits();
  }

  @Override
  public FunctionalParameter setFormatter(Formatter formatter) {
    this.formatter = formatter;
    return this;
  }

  @Override
  public Units getUnits() {
    return Units.NONE;
  }

  @Override
  public void dispose() {}

  /**
   * Does nothing, subclass may override.
   */
  public FunctionalParameter reset() {
    return this;
  }

  /**
   * Not supported for this parameter type unless subclass overrides.
   *
   * @param value The value
   */
  public LXParameter setValue(double value) {
    throw new UnsupportedOperationException(
        "FunctionalParameter does not support setValue()");
  }

  /**
   * Retrieves the value of the parameter, subclass must implement.
   *
   * @return Parameter value
   */
  public abstract double getValue();

  /**
   * Gets the label for this parameter
   *
   * @return Label of parameter
   */
  public final String getLabel() {
    return this.label;
  }
}
