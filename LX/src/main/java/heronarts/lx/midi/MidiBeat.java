/**
 * Copyright 2017- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.midi;

import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;

public class MidiBeat extends LXShortMessage {

  public static final long PERIOD_UNKNOWN = -1;

  private final int beat;
  private double period = PERIOD_UNKNOWN;

  MidiBeat(ShortMessage message, int beat) {
    super(message, SysexMessage.SYSTEM_EXCLUSIVE);
    this.beat = beat;
  }

  void setPeriod(double period) {
    this.period = period;
  }

  public double getPeriod() {
    return this.period;
  }

  public int getBeat() {
    return this.beat;
  }

}
