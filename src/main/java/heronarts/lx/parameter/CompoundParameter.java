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

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Collections;
import java.util.List;

import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.utils.LXUtils;

public class CompoundParameter extends BoundedParameter {

  // Note that the thread-safe CopyOnWriteArrayList is used here because the UI
  // thread may also need to access these modulations to draw animated knobs and controls
  // while the engine thread may make modifications
  private final List<LXCompoundModulation> mutableModulations =
    new CopyOnWriteArrayList<LXCompoundModulation>();

  public final List<LXCompoundModulation> modulations =
    Collections.unmodifiableList(this.mutableModulations);

  /**
   * Labeled parameter with value of 0 and range of 0-1
   *
   * @param label Label for parameter
   */
  public CompoundParameter(String label) {
    super(label, 0);
  }

  /**
   * A bounded parameter with label and value, initial value of 0 and a range of 0-1
   *
   * @param label Label
   * @param value value
   */
  public CompoundParameter(String label, double value) {
    super(label, value, 1);
  }

  /**
   * A bounded parameter with an initial value, and range from 0 to max
   *
   * @param label Label
   * @param value value
   * @param max Maximum value
   */
  public CompoundParameter(String label, double value, double max) {
    super(label, value, 0, max);
  }

  /**
   * A bounded parameter with initial value and range from v0 to v1. Note that it is not necessary for
   * v0 to be less than v1, if it is desired for the knob's value to progress negatively.
   *
   * @param label Label
   * @param value Initial value
   * @param v0 Start of range
   * @param v1 End of range
   */
  public CompoundParameter(String label, double value, double v0, double v1) {
    super(label, value, v0, v1, null);
  }

  /**
   * Creates a CompoundParameter which limits the value of an underlying MutableParameter to a given
   * range. Changes to the CompoundParameter are forwarded to the MutableParameter, and vice versa.
   * If the MutableParameter is set to a value outside the specified bounds, this BoundedParmaeter
   * will ignore the update and the values will be inconsistent. The typical use of this mode is
   * to create a parameter suitable for limited-range UI control of a parameter, typically a
   * MutableParameter.
   *
   * @param underlying The underlying parameter
   * @param v0 Beginning of range
   * @param v1 End of range
   */
  public CompoundParameter(LXListenableParameter underlying, double v0, double v1) {
    super(underlying.getLabel(), underlying.getValue(), v0, v1, underlying);
  }

  @Override
  public CompoundParameter setPolarity(LXParameter.Polarity polarity) {
    super.setPolarity(polarity);
    return this;
  }

  @Override
  public CompoundParameter setExponent(double exponent) {
    super.setExponent(exponent);
    return this;
  }

  @Override
  public CompoundParameter setDescription(String description) {
    super.setDescription(description);
    return this;
  }

  /**
   * Adds a modulation to this parameter
   *
   * @param modulation Modulation mapping to add to this parameter
   * @return this
   */
  public CompoundParameter addModulation(LXCompoundModulation modulation) {
    if (this.mutableModulations.contains(modulation)) {
      throw new IllegalStateException("Cannot add same modulation twice");
    }
    this.mutableModulations.add(modulation);
    bang();
    return this;
  }

  /**
   * Removes a modulation from this parameter
   *
   * @param modulation Modulation mapping to remove
   * @return this
   */
  public CompoundParameter removeModulation(LXCompoundModulation modulation) {
    this.mutableModulations.remove(modulation);
    bang();
    return this;
  }

  public double getBaseValue() {
    return super.getValue();
  }

  public float getBaseValuef() {
    return (float) getBaseValue();
  }

  public double getBaseNormalized() {
    return this.range.getNormalized(getBaseValue(), getExponent());
  }

  public float getBaseNormalizedf() {
    return (float) getBaseNormalized();
  }

  @Override
  public double getNormalized() {
    double normalized = this.range.getNormalized(getBaseValue(), getExponent());
    for (LXCompoundModulation modulation : this.mutableModulations) {
      if (modulation.enabled.isOn()) {
        if (modulation.getPolarity() == Polarity.UNIPOLAR) {
          normalized += modulation.source.getNormalized() * modulation.range.getValue();
        } else {
          normalized += 2.*(modulation.source.getNormalized()-.5) * modulation.range.getValue();
        }
      }
    }
    return LXUtils.constrain(normalized, 0, 1);
  }

  @Override
  public double getValue() {
    if (this.mutableModulations.size() == 0) {
      return super.getValue();
    }
    return this.range.normalizedToValue(getNormalized(), getExponent());
  }

}
