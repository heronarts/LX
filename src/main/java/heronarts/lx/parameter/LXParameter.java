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
import heronarts.lx.LXPath;
import heronarts.lx.midi.MidiNote;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * This class provides a common interface for system components to have
 * parameters that can modify their operation. Any LXComponent can have
 * parameters, such as a pattern, effect, or transition.
 */
public interface LXParameter extends LXPath {

  public static class Collection extends LinkedHashMap<String, LXParameter> {
    private static final long serialVersionUID = -7473252361485696112L;

    public Collection add(String path, LXParameter parameter) {
      if (containsKey(path)) {
        throw new IllegalStateException("Cannot add duplicate parameter path to collection: " + path);
      }
      if (containsValue(parameter)) {
        throw new IllegalStateException("Cannot add parameter to same collection twice: " + parameter);
      }
      put(path, parameter);
      return this;
    }

    public Collection reset() {
      for (LXParameter p : values()) {
        p.reset();
      }
      return this;
    }
  }

  public static class Monitor {

    private final LXParameter parameter;
    private double lastValue;

    public Monitor(LXParameter parameter) {
      if (parameter == null) {
        throw new IllegalArgumentException("Cannot create LXParameter.Monitor for null");
      }
      this.parameter = parameter;
      this.lastValue = parameter.getValue();
    }

    public boolean changed() {
      double value = this.parameter.getValue();
      boolean changed = (value != this.lastValue);
      this.lastValue = value;
      return changed;
    }
  }

  public static class MultiMonitor {

    private final List<Monitor> monitors = new ArrayList<Monitor>();

    public MultiMonitor(LXParameter ... parameters) {
      for (LXParameter parameter : parameters) {
        addParameter(parameter);
      }
    }

    public MultiMonitor addParameter(LXParameter parameter) {
      this.monitors.add(new Monitor(parameter));
      return this;
    }

    public boolean changed() {
      for (Monitor monitor : this.monitors) {
        if (monitor.changed()) {
          return true;
        }
      }
      return false;
    }
  }

  public enum Polarity {
    UNIPOLAR,
    BIPOLAR;

    @Override
    public String toString() {
      switch (this) {
      case BIPOLAR: return "\u2194";
      default:
      case UNIPOLAR: return "\u2192";
      }
    }
  };

  public interface Formatter {
    public String format(double value);
  }

  public enum Units implements Formatter {
    NONE,
    INTEGER,
    SECONDS,
    MILLISECONDS,
    MILLISECONDS_RAW,
    DECIBELS,
    HERTZ,
    MIDI_NOTE,
    DEGREES,
    RADIANS,
    PERCENT,
    PERCENT_NORMALIZED,
    CLOCK;

    @Override
    @SuppressWarnings("fallthrough")
    public String format(double value) {
      switch (this) {
      case INTEGER:
        return String.format("%d", (int) value);
      case PERCENT:
        return String.format("%d%%", (int) value);
      case PERCENT_NORMALIZED:
        return String.format("%d%%", (int) (100*value));
      case SECONDS:
        value *= 1000;
        // Intentional fall-through
      case MILLISECONDS:
      case MILLISECONDS_RAW:
        if (value < 1000) {
          return String.format("%dms", (int) value);
        } else if (value < 60000) {
          return String.format("%.2fs", value / 1000);
        } else if (value < 3600000) {
          int minutes = (int) (value / 60000);
          int seconds = (int) ((value % 60000) / 1000);
          return String.format("%d:%02d", minutes, seconds);
        }
        int hours = (int) (value / 3600000);
        value = value % 3600000;
        int minutes = (int) (value / 60000);
        int seconds = (int) ((value % 60000) / 1000);
        return String.format("%d:%02d:%02d", hours, minutes, seconds);
      case HERTZ:
        if (value >= 10000) {
          return String.format("%.1fkHz", value/1000);
        } else if (value >= 1000) {
          return String.format("%.2fkHz", value/1000);
        } else if (value >= 100) {
          return String.format("%.0fHz", value);
        } else if (value >= 10) {
          return String.format("%.1fHz", value);
        }
        return String.format("%.2fHz", value);
      case DECIBELS:
        return String.format("%.1fdB", value);
      case MIDI_NOTE:
        return MidiNote.getPitchString((int) value);
      case DEGREES:
        return String.format("%d\u00B0", (int) value);
      case CLOCK:
        return String.format("%02d", (int) value);
      default:
      case RADIANS:
      case NONE:
        return String.format("%.2f", value);
      }
    }

    @SuppressWarnings("fallthrough")
    public double parseDouble(String value) throws NumberFormatException {
      double timeMultiple = 1;
      switch (this) {
      case MILLISECONDS_RAW:
      case MILLISECONDS:
        if ((value.indexOf(":") >= 0) || (this == MILLISECONDS)) {
          // NOTE: MILLISECONDS_RAW takes values in raw milliseconds, except when
          // they are specified in m:ss format. MILLISECONDS parses values expressed
          // in seconds, but always multiplies by 1000 to get the ms equivalent
          timeMultiple = 1000;
        }
        // Intentional fallthrough
      case SECONDS:
        double raw = 0;
        for (String part : value.split(":")) {
          raw = raw*60 + ((part.length() > 0) ? Double.parseDouble(part) : 0);
        }
        return timeMultiple * raw;

      case PERCENT_NORMALIZED:
        return .01 * Double.parseDouble(value);

      default:
      case NONE:
        return Double.parseDouble(value);
      }
    }
  };

  /**
   * Returns the parent aggregate parameter that this parameter belongs to
   *
   * @return Parent aggregate parameter, or null
   */
  public default AggregateParameter getParentParameter() {
    return null;
  }

  /**
   * Sets the component that owns this parameter
   *
   * @param component Component
   * @param path Path name for parameter
   * @return this
   */
  public LXParameter setComponent(LXComponent component, String path);

  /**
   * Gets the unit format that this parameter's value stores.
   *
   * @return Units
   */
  public Units getUnits();

  /**
   * Gets the formatter to be used for printing this parameter's value
   *
   * @return Formatter
   */
  public Formatter getFormatter();

  /**
   * Sets the formatter used for printing this parameter's value
   *
   * @param formatter Formatter
   * @return The parameter
   */
  public LXParameter setFormatter(Formatter formatter);

  /**
   * Gets the polarity of this parameter.
   *
   * @return polarity of this parameter
   */
  public Polarity getPolarity();

  /**
   * Invoked when the parameter is done being used and none of its resources
   * are needed anymore.
   */
  public void dispose();

  /**
   * A method to reset the value of the parameter, if a default is available.
   * Not necessarily defined for all parameters, may be ignored.
   *
   * @return this
   */
  public abstract Object reset();

  /**
   * Sets the value of the parameter.
   *
   * @param value The value
   * @return this
   */
  public LXParameter setValue(double value);

  /**
   * Retrieves the value of the parameter
   *
   * @return Parameter value
   */
  public double getValue();

  /**
   * Utility helper function to get the value of the parameter as a float.
   *
   * @return Parameter value as float
   */
  public default float getValuef() {
    return (float) getValue();
  }

  /**
   * Get the base parameter value, for modulated parameters
   * this may differ from getValue()
   *
   * @return Base parameter value
   */
  public default double getBaseValue() {
    return getValue();
  }

  /**
   * Get the base parameter value, for modulated parameters
   * this may differ from getValue()
   *
   * @return Base parameter value
   */
  public default float getBaseValuef() {
    return (float) getBaseValue();
  }

  /**
   * Gets the label for this parameter
   *
   * @return Label of parameter
   */
  public String getLabel();

  /**
   * Sets whether this parameter should be eligible for MIDI/modulation mapping
   * or not.
   *
   * @param mappable Whether parameter should be available for mapping
   * @return this
   */
  public default LXParameter setMappable(boolean mappable) {
    throw new UnsupportedOperationException(getClass() + " does not support setMappable()");
  }

  /**
   * Whether this parameter should be eligible for mapping via MIDI or
   * modulation control.
   *
   * @return <code>true</code> if mappable, false if otherwise
   */
  public default boolean isMappable() {
    return false;
  }

}
