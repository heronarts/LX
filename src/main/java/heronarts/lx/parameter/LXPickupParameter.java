/**
 * Copyright 2024- Justin Belcher, Mark C. Slee, Heron Arts LLC
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
 * @author Justin K. Belcher <justin@jkb.studio>
 */

package heronarts.lx.parameter;

/**
 * Utility class for mapping from a one-directional source (such as a MIDI potentiometer)
 * to a normalized parameter.  When the target parameter is changed by a different source
 * this class will enter an out-of-alignment state and will require
 * a pickup (source value aligning with the parameter value) before changes are
 * once again committed to the target.  This prevents the target value from jumping.
 */
public class LXPickupParameter extends LXListenableNormalizedParameter {

  private LXListenableNormalizedParameter parameter;
  private final LXParameterListener listener = this::onParameterValueChanged;

  private boolean pickup = true;
  private boolean aligned = false;
  private boolean initialized = false;

  private double sourceValue = 0;
  private boolean internal = false;

  public LXPickupParameter() {
    this(null);
  }

  public LXPickupParameter(LXListenableNormalizedParameter target) {
    super("Pickup", 0);
    setParameter(target);
  }

  private boolean isAligned() {
    // Alignment is unknown until first source value is received
    return this.aligned && this.initialized;
  }

  public LXPickupParameter setParameter(LXListenableNormalizedParameter parameter) {
    if (this.parameter != null) {
      this.parameter.removeListener(this.listener);
    }
    this.parameter = parameter;
    if (this.parameter != null) {
      this.parameter.addListener(this.listener);
      this.aligned = this.sourceValue == this.parameter.getBaseNormalized();
    } else {
      this.aligned = true;
    }
    if (!this.pickup && this.initialized) {
      setNormalized(this.sourceValue);
    }
    return this;
  }

  public LXPickupParameter setPickup(boolean on) {
    if (this.pickup != on) {
      this.pickup = on;
      if (!this.pickup && this.initialized) {
        // Revering to Direct mode. Snap to known value.
        setNormalized(this.sourceValue);
      }
    }
    return this;
  }

  @Override
  public LXPickupParameter setNormalized(double value) {
    if (!this.initialized) {
      this.initialized = true;
      this.sourceValue = value;
      if (this.pickup) {
        // Initial input, pickup
        if (this.parameter != null) {
          this.aligned = this.sourceValue == this.parameter.getBaseNormalized();
        } else {
          this.aligned = true;
          bang();
        }
      } else {
        // Initial input, not pickup.
        this.aligned = true;
        _setParameterNormalized(value);
      }
    } else {
      // Not initial input
      if (this.pickup && !this.aligned) {
        // Out of sync with target, unless this movement achieved pickup.
        double targetValue = getNormalized();
        if (Double.compare(targetValue, sourceValue) != Double.compare(targetValue, value)) {
          // Pickup achieved. We're equal or we passed the value.
          this.aligned = true;
        }
      }
      if (!this.pickup || isAligned()) {
        _setParameterNormalized(value);
      }
      this.sourceValue = value;
      if (this.parameter == null) {
        bang();
      }
    }
    return this;
  }

  private double _getParameterNormalized() {
    if (this.parameter != null) {
      return this.parameter.getBaseNormalized();
    }
    return this.sourceValue;
  }

  private void _setParameterNormalized(double value) {
    if (this.parameter != null) {
      this.internal = true;
      this.parameter.setNormalized(value);
      this.internal = false;
    }
  }

  private void onParameterValueChanged(LXParameter parameter) {
    if (!this.internal) {
      // An external source changed the parameter value, likely knocking us out of sync.
      this.aligned = this.sourceValue == this.parameter.getBaseNormalized();
      bang();
    }
  }

  @Override
  public double getNormalized() {
    return _getParameterNormalized();
  }

  @Override
  protected double updateValue(double value) {
    return value;
  }

  @Override
  public void dispose() {
    parameter.removeListener(this.listener);
    super.dispose();
  }

}
