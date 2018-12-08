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
import heronarts.lx.midi.MidiNote;

/**
 * This class provides a common interface for system components to have
 * parameters that can modify their operation. Any LXComponent can have
 * parameters, such as a pattern, effect, or transition.
 */
public interface LXParameter {

  public enum Polarity {
    UNIPOLAR,
    BIPOLAR;

    @Override
    public String toString() {
      switch (this) {
      case BIPOLAR: return "↔";
      default:
      case UNIPOLAR: return "→";
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
    DECIBELS,
    HERTZ,
    MIDI_NOTE,
    DEGREES,
    RADIANS;

    @Override
    public String format(double value) {
      return Units.format(this, value);
    }

    @SuppressWarnings("fallthrough")
    public static String format(Units units, double value) {
      switch (units) {
      case INTEGER:
        return String.format("%d", (int) value);
      case SECONDS:
        value *= 1000;
        // pass through!
      case MILLISECONDS:
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
        return String.format("%d", (int) value);
      default:
      case RADIANS:
      case NONE:
        return String.format("%.2f", value);
      }
    }
  };

  /**
   * Sets the component that owns this parameter
   *
   * @param component Component
   * @param path Path name for parameter
   * @return this
   */
  public LXParameter setComponent(LXComponent component, String path);

  /**
   * Gets the component to which this parameter is registered.
   *
   * @return Component this parameter belongs to, may be null
   */
  public LXComponent getComponent();

  /**
   * Gets the path that this parameter is registered to in the component
   *
   * @return Component parameter path
   */
  public String getPath();

  /**
   * Returns a contextual help message explaining the purpose of this parameter to the user, or null if
   * none is available.
   *
   * @return Contextual help string explaining purpose of parameter.
   */
  public String getDescription();

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
  public float getValuef();

  /**
   * Gets the label for this parameter
   *
   * @return Label of parameter
   */
  public String getLabel();
}
