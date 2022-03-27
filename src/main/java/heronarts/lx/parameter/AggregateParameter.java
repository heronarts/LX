/**
 * Copyright 2022- Mark C. Slee, Heron Arts LLC
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An aggregate parameter is a parameter that may be directly monitored for changes, but whose
 * value is constructed from a number of underlying parameters. Changes to the underlying parameters
 * are automatically monitored and will trigger an update of the parameter's direct value if necessary.
 *
 * There is also a mechanism to update the sub-parameter values if the aggregate value is set
 * directly (it is not required that this be supported/implemented based upon the nature of the parameter).
 *
 * The sub-parameters are automatically registered with the parent component at OSC paths under the
 * path of the aggregate parameter itself.
 *
 * The canonical example of an AggregateParameter is the {@link heronarts.lx.color.ColorParameter}
 * which is comprised of an underlying hue, saturation, and brightness value that generate a single
 * resulting color value.
 */
public abstract class AggregateParameter extends LXListenableParameter {

  private final Map<String, LXListenableParameter> _subparameters = new LinkedHashMap<String, LXListenableParameter>();

  public final Map<String, LXListenableParameter> subparameters = Collections.unmodifiableMap(this._subparameters);

  protected AggregateParameter(String label) {
    this(label, 0);
  }

  protected AggregateParameter(String label, double value) {
    super(label, value);
  }

  protected void addSubparameter(String path, LXListenableParameter parameter) {
    if (this._subparameters.containsKey(path)) {
      throw new IllegalStateException("Cannot add sub-parameter at path " + path
        + ", sub-parameter already exists");
    }
    this._subparameters.put(path, parameter);
    parameter.setParentParameter(this);
    parameter.addListener(this.subparameterListener);
  }

  private boolean inSubparameterUpdate = false;
  private boolean inValueUpdate = false;

  @Override
  protected final double updateValue(double value) {
    this.inValueUpdate = true;
    value = onUpdateValue(value);
    if (!this.inSubparameterUpdate) {
      updateSubparameters(value);
    }
    this.inValueUpdate = false;
    return value;
  }

  private final LXParameterListener subparameterListener = (p) -> {
    if (!this.inValueUpdate) {
      boolean push = this.inSubparameterUpdate;
      this.inSubparameterUpdate = true;
      onSubparameterUpdate(p);
      this.inSubparameterUpdate = push;
    }
  };

  /**
   * Subclasses may optionally override to take action based upon directly updated value
   *
   * @param value Updated value
   * @return Value to store
   */
  protected double onUpdateValue(double value) {
    return value;
  }

  /**
   * Subclasses should update the subparameter values based upon the raw parameter value
   * if it has been set directly.
   */
  protected abstract void updateSubparameters(double value);

  /**
   * Subclasses should update the main parameter value when a sub-parameter has changed
   * @param p Subparameter that has changed
   */
  protected abstract void onSubparameterUpdate(LXParameter p);

  /**
   * Subclasses may override. By default an AggregateParameter returns its first subparameter
   * that is an LXListenableNormalizedParameter for remote control surface.
   *
   * @return Subparameter to be used by a remote control surface
   */
  public LXListenableNormalizedParameter getRemoteControl() {
    for (LXListenableParameter subparam : this.subparameters.values()) {
      if (subparam instanceof LXListenableNormalizedParameter) {
        return (LXListenableNormalizedParameter) subparam;
      }
    }
    return null;
  }

  @Override
  public void dispose() {
    for (LXListenableParameter parameter : this._subparameters.values()) {
      parameter.removeListener(this.subparameterListener);
      // NOTE: do not dispose parameters here! the parent LXComponent will handle that
    }
    super.dispose();
  }

}
