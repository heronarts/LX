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

/**
 * A time parameter is a utility for specifying a time of day in discrete
 * hours, minutes, and seconds components.
 */
public class TimeParameter extends AggregateParameter {

  public static final int HOURS_PER_DAY = 24;
  public static final int MINUTES_PER_HOUR = 60;
  public static final int SECONDS_PER_MINUTE = 60;
  public static final int SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR;
  public static final int SECONDS_PER_DAY = SECONDS_PER_HOUR * HOURS_PER_DAY;

  public final DiscreteParameter hours =
    new DiscreteParameter("Hours", 0, 24)
    .setDescription("Hours of the day");

  public final DiscreteParameter minutes =
    new DiscreteParameter("Minutes", 0, 60)
    .setDescription("Minutes of the hours");

  public final DiscreteParameter seconds =
    new DiscreteParameter("Seconds", 0, 60)
    .setDescription("Seconds of the hour");

  protected TimeParameter(String label) {
    super(label);
    addSubparameter("hours", this.hours);
    addSubparameter("minutes", this.minutes);
    addSubparameter("seconds", this.seconds);
  }

  /**
   * Sets the value of the time parameter
   *
   * @param hours Hours of day (0-23)
   * @param minutes Minutes of the hour (0-59)
   * @param seconds Seconds of the hour (0-59)
   * @return this
   */
  public TimeParameter setTime(int hours, int minutes, int seconds) {
    if (hours < 0) {
      throw new IllegalArgumentException("TimeParameter hours may not be < 0 (" + hours + ")");
    } else if (hours >= HOURS_PER_DAY) {
      throw new IllegalArgumentException("TimeParameter hours may not be > 23 (" + hours + ")");
    }
    if (minutes < 0) {
      throw new IllegalArgumentException("TimeParameter minutes may not be < 0 (" + minutes + ")");
    } else if (minutes >= MINUTES_PER_HOUR) {
      throw new IllegalArgumentException("TimeParameter minutes may not be > 59 (" + minutes + ")");
    }
    if (seconds < 0) {
      throw new IllegalArgumentException("TimeParameter seconds may not be < 0 (" + seconds + ")");
    } else if (seconds > SECONDS_PER_HOUR) {
      throw new IllegalArgumentException("TimeParameter seconds may not be > 59 (" + seconds + ")");
    }
    return setTime(hours * SECONDS_PER_HOUR + minutes * SECONDS_PER_MINUTE + seconds);
  }

  /**
   * Sets the time parameter to the given number of seconds in the day
   *
   * @param secondsOfDay Seconds in the day (0 - TimeParameter.SECONDS_PER_DAY - 1)
   * @return this
   */
  public TimeParameter setTime(int secondsOfDay) {
    if (secondsOfDay < 0) {
      throw new IllegalArgumentException("TimeParameter may not be < 0 (" + hours + ")");
    } else if (secondsOfDay >= SECONDS_PER_DAY) {
      throw new IllegalArgumentException("TimeParameter hours may not be > " + (SECONDS_PER_DAY-1) + "(" + secondsOfDay + ")");
    }
    setValue(secondsOfDay);
    return this;
  }

  /**
   * Returns the stored time value as a number of seconds elapsed in the day
   *
   * @return Number of seconds in the day this time represents
   */
  public int getSecondsOfDay() {
    return (int) getValue();
  }

  @Override
  protected void updateSubparameters(double value) {
    int secondsOfDay = (int) value;
    this.hours.setValue(secondsOfDay / SECONDS_PER_HOUR);
    this.minutes.setValue((secondsOfDay % SECONDS_PER_HOUR) / MINUTES_PER_HOUR);
    this.seconds.setValue(secondsOfDay % SECONDS_PER_MINUTE);
  }

  @Override
  protected void onSubparameterUpdate(LXParameter p) {
    setTime(this.hours.getValuei(), this.minutes.getValuei(), this.seconds.getValuei());
  }

}
