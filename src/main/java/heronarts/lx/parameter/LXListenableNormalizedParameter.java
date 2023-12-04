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

import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.utils.LXUtils;

import java.util.List;

/**
 * A parameter that can be listened to and has normalized values. This is needed
 * for things like UI components such as a slider or a knob, which need to be
 * able to listen for changes to the parameter value and to update it in a
 * normalized range of values.
 */
public abstract class LXListenableNormalizedParameter extends LXListenableParameter implements LXNormalizedParameter {

  private double exponent = 1;
  private boolean wrappable = false;
  private OscMode oscMode = OscMode.NORMALIZED;

  protected LXListenableNormalizedParameter(String label, double value) {
    super(label, value);
  }

  public LXListenableNormalizedParameter setExponent(double exponent) {
    if (exponent <= 0) {
      throw new IllegalArgumentException("May not set zero or negative exponent");
    }
    this.exponent = exponent;
    return this;
  }

  public double getExponent() {
    return this.exponent;
  }

  @Override
  public LXListenableNormalizedParameter setUnits(Units units) {
    super.setUnits(units);
    switch (units) {
    case INTEGER:
    case SECONDS:
    case MILLISECONDS:
    case MILLISECONDS_RAW:
    case MIDI_NOTE:
    case CLOCK:
      setOscMode(OscMode.ABSOLUTE);
      break;
    default:
      break;
    }
    return this;
  }

  public LXListenableNormalizedParameter setOscMode(OscMode oscMode) {
    this.oscMode = oscMode;
    return this;
  }

  @Override
  public OscMode getOscMode() {
    return this.oscMode;
  }

  public LXListenableNormalizedParameter setWrappable(boolean wrappable) {
    this.wrappable = wrappable;
    return this;
  }

  @Override
  public boolean isWrappable() {
    return this.wrappable;
  }

  protected double getNormalizedWithModulation(double normalized, List<? extends LXCompoundModulation> modulations) {
    for (LXCompoundModulation modulation : modulations) {
      normalized += modulation.getModulationAmount();
    }
    if (isWrappable()) {
      if (normalized < 0) {
        return 1. + (normalized % 1.);
      } else if (normalized > 1) {
        return normalized % 1.;
      }
      // NOTE: don't want to mod exactly 1. to 0, leave it at 1.
      return normalized;
    }
    return LXUtils.constrain(normalized, 0, 1);
  }

  public LXListenableNormalizedParameter incrementNormalized(double amount) {
    return incrementNormalized(amount, isWrappable());
  }

  public LXListenableNormalizedParameter incrementNormalized(double amount, boolean wrap) {
    double normalized = getBaseNormalized();
    normalized += amount;
    if (wrap) {
      if (normalized > 1) {
        normalized = normalized % 1;
      } else if (normalized < 0) {
        normalized = 1 + (normalized % 1);
      }
    }
    return (LXListenableNormalizedParameter) setNormalized(normalized);
  }

}
