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

package heronarts.lx.modulator;

import java.util.Calendar;

import heronarts.lx.LXCategory;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.TimeParameter;

@LXModulator.Global("Timer")
@LXCategory(LXCategory.TRIGGER)
public class Timer extends LXModulator implements LXNormalizedParameter, LXTriggerSource, LXOscComponent {

  public final TimeParameter time =
    new TimeParameter("Time")
    .setDescription("What time of day the timer fires");

  public final BooleanParameter sunday =
    new BooleanParameter("Sunday", true)
    .setDescription("Whether the timer fires on Sunday");

  public final BooleanParameter monday =
    new BooleanParameter("Monday", true)
    .setDescription("Whether the timer fires on Monday");

  public final BooleanParameter tuesday =
    new BooleanParameter("Tuesday", true)
    .setDescription("Whether the timer fires on Tuesday");

  public final BooleanParameter wednesday =
    new BooleanParameter("Wednesday", true)
    .setDescription("Whether the timer fires on Wednesday");

  public final BooleanParameter thursday =
    new BooleanParameter("Thursday", true)
    .setDescription("Whether the timer fires on Thursday");

  public final BooleanParameter friday =
    new BooleanParameter("Friday", true)
    .setDescription("Whether the timer fires on Friday");

  public final BooleanParameter saturday =
    new BooleanParameter("Saturday", true)
    .setDescription("Whether the timer fires on Saturday");

  private final BooleanParameter[] days = { sunday, monday, tuesday, wednesday, thursday, friday, saturday };

  private final Calendar calendar = Calendar.getInstance();

  public final BooleanParameter triggerOut =
    new BooleanParameter("Trigger Out")
    .setDescription("Indicates when the timer fires")
    .setMode(BooleanParameter.Mode.MOMENTARY);

  public Timer() {
    this("Timer");
  }

  public Timer(String label) {
    super(label);
    addParameter("time", this.time);

    addParameter("sunday", this.sunday);
    addParameter("monday", this.monday);
    addParameter("tuesday", this.tuesday);
    addParameter("wednesday", this.wednesday);
    addParameter("thursday", this.thursday);
    addParameter("friday", this.friday);
    addParameter("saturday", this.saturday);

    addParameter("triggerOut", this.triggerOut);

    setMappingSource(false);
  }

  @Override
  protected double computeValue(double deltaMs) {
    this.calendar.setTimeInMillis(System.currentTimeMillis());
    boolean active = false;
    int day = this.calendar.get(Calendar.DAY_OF_WEEK);
    if (this.days[day-1].isOn()) {
      int thisSeconds = TimeParameter.getSecondsOfDay(this.calendar);
      int timerSeconds = this.time.getSecondsOfDay();
      active = (thisSeconds == timerSeconds);
    }
    this.triggerOut.setValue(active);
    return active ? 1 : 0;
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.triggerOut;
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException("Timer does not support setNormalized");
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

}
