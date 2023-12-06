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

import heronarts.lx.LXComponent;
import heronarts.lx.utils.LXUtils;

/**
 * Simple normalized parameter which is not listenable.
 */
public class NormalizedParameter implements LXNormalizedParameter {

  private LXComponent parent = null;
  private String path = null;
  private final String label;
  private String description = null;
  private double value = 0;
  private boolean mappable = true;
  private Formatter formatter = null;

  public NormalizedParameter(String label) {
    this(label, 0);
  }

  public NormalizedParameter(String label, double value) {
    this.label = label;
    this.value = value;
  }

  @Override
  public NormalizedParameter setComponent(LXComponent component, String path) {
    if (component == null || path == null) {
      throw new IllegalArgumentException("May not set null component or path");
    }
    if (this.parent != null || this.path != null) {
      throw new IllegalStateException("Component already set on this modulator: " + this);
    }
    this.parent = component;
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

  public NormalizedParameter setDescription(String description) {
    this.description = description;
    return this;
  }

  @Override
  public String getDescription() {
    return this.description;
  }

  @Override
  public Formatter getFormatter() {
    return (this.formatter != null) ? this.formatter : getUnits();
  }

  @Override
  public NormalizedParameter setFormatter(Formatter formatter) {
    this.formatter = formatter;
    return this;
  }

  @Override
  public Units getUnits() {
    return Units.NONE;
  }

  @Override
  public Polarity getPolarity() {
    return Polarity.UNIPOLAR;
  }

  @Override
  public void dispose() {
  }

  @Override
  public NormalizedParameter reset() {
    this.value = 0;
    return this;
  }

  @Override
  public NormalizedParameter setValue(double value) {
    this.value = LXUtils.constrain(value, 0, 1);;
    return this;
  }

  @Override
  public double getValue() {
    return this.value;
  }

  @Override
  public String getLabel() {
    return this.label;
  }

  @Override
  public NormalizedParameter setNormalized(double value) {
    return setValue(value);
  }

  @Override
  public double getNormalized() {
    return this.value;
  }

  @Override
  public float getNormalizedf() {
    return (float) getNormalized();
  }

  @Override
  public double getExponent() {
    return 1;
  }

  @Override
  public boolean isMappable() {
    return this.mappable;
  }

}
