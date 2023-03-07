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

/**
 * A parameter that can be listened to and has normalized values. This is needed
 * for things like UI components such as a slider or a knob, which need to be
 * able to listen for changes to the parameter value and to update it in a
 * normalized range of values.
 */
public abstract class LXListenableNormalizedParameter extends
    LXListenableParameter implements LXNormalizedParameter {

  private double exponent = 1;
  private boolean mappable = true;
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
  public LXListenableNormalizedParameter setMappable(boolean mappable) {
    this.mappable = mappable;
    return this;
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

  @Override
  public boolean isMappable() {
    return this.mappable;
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

  public LXListenableNormalizedParameter incrementNormalized(double amount) {
    return incrementNormalized(amount, isWrappable());
  }

  public LXListenableNormalizedParameter incrementNormalized(double amount, boolean wrap) {
    double normalized = (this instanceof CompoundParameter) ?
      ((CompoundParameter) this).getBaseNormalized() :
      getNormalized();
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
