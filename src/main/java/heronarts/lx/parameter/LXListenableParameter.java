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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import heronarts.lx.LX;
import heronarts.lx.LXComponent;

/**
 * This is a parameter instance that can be listened to, meaning we are able to
 * deterministically know when the value has changed. This means that all
 * modifications *must* come through setValue().
 */
public abstract class LXListenableParameter implements LXParameter {

  private final String label;

  private double defaultValue, value;

  private final List<LXParameterListener> listeners = new ArrayList<LXParameterListener>();

  private AggregateParameter parentParameter = null;

  private LXComponent parent;

  private String path;

  private Units units = Units.NONE;

  private Formatter formatter = null;

  private Polarity polarity = Polarity.UNIPOLAR;

  protected String description = null;

  private boolean mappable = true;

  protected LXListenableParameter() {
    this(null, 0);
  }

  protected LXListenableParameter(String label) {
    this(label, 0);
  }

  protected LXListenableParameter(double value) {
    this(null, value);
  }

  protected LXListenableParameter(String label, double value) {
    this.label = label;
    this.defaultValue = this.value = value;
  }

  public Formatter getFormatter() {
    return (this.formatter != null) ? this.formatter : getUnits();
  }

  public LXListenableParameter setFormatter(Formatter formatter) {
    this.formatter = formatter;
    return this;
  }

  public Units getUnits() {
    return this.units;
  }

  public LXListenableParameter setUnits(Units units) {
    this.units = units;
    return this;
  }

  public Polarity getPolarity() {
    return this.polarity;
  }

  public LXListenableParameter setPolarity(Polarity polarity) {
    this.polarity = polarity;
    return this;
  }

  public LXListenableParameter addListener(LXParameterListener listener) {
    return addListener(listener, false);
  }

  public LXListenableParameter addListener(LXParameterListener listener, boolean fireImmediately) {
    Objects.requireNonNull(listener, "May not add null LXParameterListener: " + this);
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate LXParameterListener " + getCanonicalPath() + " " + listener.getClass().getName());
    }
    this.listeners.add(listener);
    if (fireImmediately) {
      listener.onParameterChanged(this);
    }
    return this;
  }

  public final LXListenableParameter removeListener(LXParameterListener listener) {
    if (!this.listeners.contains(listener)) {
      LX.error(new Exception(), "Trying to remove unregistered LXParameterListener " + getCanonicalPath() + " " + listener.getClass().getName());
    }
    this.listeners.remove(listener);
    return this;
  }

  public LXListenableParameter setDescription(String description) {
    this.description = description;
    return this;
  }

  @Override
  public String getDescription() {
    return this.description;
  }

  @Override
  public LXParameter setComponent(LXComponent component, String path) {
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

  void setParentParameter(AggregateParameter parentParameter) {
    this.parentParameter = parentParameter;
  }

  @Override
  public AggregateParameter getParentParameter() {
    return this.parentParameter;
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
  public void dispose() {
    for (LXParameterListener listener : this.listeners) {
      String className = listener.getClass().getName();
      if (className.contains(".ui.")) {
        LX.warning("Stranded UI listener on parameter: " + getCanonicalPath() + " - " + className);
      } else {
        LX.error(new Exception(), "WARNING / SHOULDFIX: Stranded listener on parameter: " + getCanonicalPath() + " - " + className);
      }
    }
    this.listeners.clear();
  }

  public LXParameter reset() {
    return setValue(this.defaultValue);
  }

  /**
   * Resets the value of the parameter, giving it a new default. Future calls to
   * reset() with no parameter will use this value.
   *
   * @param value New default value
   * @return this
   */
  public LXParameter reset(double value) {
    this.defaultValue = value;
    return setValue(this.defaultValue);
  }

  public LXParameter incrementValue(double amount) {
    return setValue(this.value + amount);
  }

  private boolean inListener = false;
  private final Queue<Double> setValues = new ArrayDeque<Double>();

  public final LXParameter setValue(double value) {
    return setValue(value, true);
  }

  public final LXParameter setValue(double value, boolean notifyListeners) {
    if (this.inListener) {
      // setValue() was called recursively from a parameter listener.
      // This is okay, but we need to call all the listeners with the
      // first value before we make this next update.
      this.setValues.add(value);
    } else {
      if (this.value != value) {
        value = updateValue(value);
        if (this.value != value) {
          this.value = value;
          if (notifyListeners) {
            this.inListener = true;
            for (LXParameterListener l : listeners) {
              l.onParameterChanged(this);
            }
            this.inListener = false;
          }
          while (!this.setValues.isEmpty()) {
            setValue(this.setValues.poll());
          }
        }
      }
    }
    return this;
  }

  public double getValue() {
    return this.value;
  }

  public final boolean isDefault() {
    // NOTE: check this.value directly here, don't use getValue() because a
    // CompoundParameter with modulation will return something different...
    return this.value == this.defaultValue;
  }

  public String getLabel() {
    return this.label;
  }

  /**
   * Manually notify all listeners of this parameter's current value.
   * Useful in some situations to force state reset.
   *
   * @return this
   */
  public LXListenableParameter bang() {
    for (LXParameterListener l : listeners) {
      l.onParameterChanged(this);
    }
    return this;
  }

  @Override
  public LXListenableParameter setMappable(boolean mappable) {
    this.mappable = mappable;
    return this;
  }

  @Override
  public boolean isMappable() {
    return this.mappable;
  }

  /**
   * Invoked when the value has changed. Subclasses should update any special
   * internal state according to this new value.
   *
   * @param value New value
   * @return this
   */
  protected abstract double updateValue(double value);

}
