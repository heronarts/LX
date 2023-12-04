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

package heronarts.lx.parameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import heronarts.lx.modulation.LXCompoundModulation;

public class CompoundDiscreteParameter extends DiscreteParameter implements LXCompoundModulation.Target {

  // Note that the thread-safe CopyOnWriteArrayList is used here because the UI
  // thread may also need to access these modulations to draw animated knobs and controls
  // while the engine thread may make modifications
  private final List<LXCompoundModulation> mutableModulations =
    new CopyOnWriteArrayList<LXCompoundModulation>();

  public final List<LXCompoundModulation> modulations =
    Collections.unmodifiableList(this.mutableModulations);

  private final List<LXCompoundModulation.Listener> modulationListeners =
    new ArrayList<LXCompoundModulation.Listener>();

  @Override
  public final CompoundDiscreteParameter addModulationListener(LXCompoundModulation.Listener listener) {
    Objects.requireNonNull(listener, "May not add null CompoundDiscreteParameter.ModulationListener");
    if (this.modulationListeners.contains(listener)) {
      throw new IllegalStateException("Cannod add CompoundDiscreteParameter.ModulationListener listener twice: " + listener);
     }
    this.modulationListeners.add(listener);
    return this;
  }

  @Override
  public final CompoundDiscreteParameter removeModulationListener(LXCompoundModulation.Listener listener) {
    this.modulationListeners.remove(listener);
    return this;
  }

  /**
   * Parameter with values from [0, range-1], 0 by default
   *
   * @param label Name of parameter
   * @param range range of values
   */
  public CompoundDiscreteParameter(String label, int range) {
    super(label, 0, range);
  }

  /**
   * Parameter with values from [min, max-1], min by default
   *
   * @param label Label
   * @param min Minimum value
   * @param max Maximum value is 1 less than this
   */
  public CompoundDiscreteParameter(String label, int min, int max) {
    super(label, min, min, max);
  }

  /**
   * Parameter with values from [min, max-1], value by default
   *
   * @param label Label
   * @param value Default value
   * @param min Minimum value (inclusive)
   * @param max Maximum value (exclusive)
   */
  public CompoundDiscreteParameter(String label, int value, int min, int max) {
    super(label, value, min, max);
  }

  /**
   * Parameter with set of String label values
   *
   * @param label Label
   * @param options Values
   */
  public CompoundDiscreteParameter(String label, String[] options) {
    super(label, options.length);
  }

  /**
   * Parameter with set of String label values, and a default
   *
   * @param label Label
   * @param options Values
   * @param value Default index
   */
  public CompoundDiscreteParameter(String label, String[] options, int value) {
    super(label, value, 0, options.length);
  }

  @Override
  public List<LXCompoundModulation> getModulations() {
    return this.modulations;
  }

  /**
   * Adds a modulation to this parameter
   *
   * @param modulation Modulation mapping to add to this parameter
   * @return this
   */
  @Override
  public CompoundDiscreteParameter addModulation(LXCompoundModulation modulation) {
    if (this.mutableModulations.contains(modulation)) {
      throw new IllegalStateException("Cannot add the same modulation twice");
    }
    this.mutableModulations.add(modulation);
    for (LXCompoundModulation.Listener listener : this.modulationListeners) {
      listener.modulationAdded(this, modulation);
    }
    bang();
    return this;
  }

  /**
   * Removes a modulation from this parameter
   *
   * @param modulation Modulation mapping to remove
   * @return this
   */
  @Override
  public CompoundDiscreteParameter removeModulation(LXCompoundModulation modulation) {
    this.mutableModulations.remove(modulation);
    for (LXCompoundModulation.Listener listener : this.modulationListeners) {
      listener.modulationRemoved(this, modulation);
    }
    bang();
    return this;
  }

  @Override
  public CompoundDiscreteParameter setUnits(CompoundDiscreteParameter.Units units) {
    super.setUnits(units);
    return this;
  }

  @Override
  public CompoundDiscreteParameter setWrappable(boolean wrappable) {
    super.setWrappable(wrappable);
    return this;
  }

  @Override
  public CompoundDiscreteParameter setMappable(boolean mappable) {
    super.setMappable(mappable);
    return this;
  }

  @Override
  public CompoundDiscreteParameter setDescription(String description) {
    super.setDescription(description);
    return this;
  }

  @Override
  public double getBaseValue() {
    return super.getValue();
  }

  @Override
  public double getBaseNormalized() {
    if (this.range <= 1) {
      return 0;
    }
    return (getBaseValue() - this.minValue) / (this.range - 1);
  }

  @Override
  public int getBaseValuei() {
    return (int) getBaseValue();
  }

  @Override
  public int getBaseIndex() {
    return super.getIndex();
  }

  @Override
  public double getNormalized() {
    if (this.range <= 1) {
      return 0;
    }
    double normalized = getNormalizedWithModulation(getBaseNormalized(), this.modulations);

    // Quantize that normalized value so that knobs/sliders "snap" appropriately, it's not possible
    // for a discrete control to take values "between" integers and the getNormalized() result
    // should not represent it as such.
    double quantize = normalizedToValue(normalized);
    return (quantize - this.minValue) / (this.range - 1);
  }

  @Override
  public double getValue() {
    if (this.range <= 1) {
      return 0;
    }
    if (this.mutableModulations.size() == 0) {
      return super.getValue();
    }
    return normalizedToValue(getNormalized());
  }
}
