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
  public static final int STOP = -1;

  private final int beat;

  private double periodMs = PERIOD_UNKNOWN;

  public final long nanoTime;

  MidiBeat(ShortMessage message, int beat, long nanoTime) {
    super(message, SysexMessage.SYSTEM_EXCLUSIVE);
    this.beat = beat;
    this.nanoTime = nanoTime;
  }

  void setPeriod(double period) {
    this.periodMs = period;
  }

  /**
   * Get the beat period in milliseconds
   *
   * @return Milliseconds per beat
   */
  public double getPeriod() {
    return this.periodMs;
  }

  public int getBeat() {
    return this.beat;
  }

  public boolean isStop() {
    return this.beat == STOP;
  }

}
