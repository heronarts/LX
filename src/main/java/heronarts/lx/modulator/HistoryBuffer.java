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

package heronarts.lx.modulator;

import heronarts.lx.utils.LXUtils;

public abstract class HistoryBuffer extends LXModulator {

  private int cursor = 0;
  private final double[] history;

  public HistoryBuffer(int size) {
    this("BUFFER", size);
  }

  public HistoryBuffer(String label, int size) {
    super(label);
    this.history = new double[size];
  }

  @Override
  protected double computeValue(double deltaMs) {
    if (--this.cursor < 0) {
      this.cursor = this.history.length - 1;
    }
    return this.history[this.cursor] = getFrameValue();
  }

  protected abstract double getFrameValue();

  public double getHistory(float framesAgo) {
    if (framesAgo >= this.history.length - 1) {
      throw new IllegalArgumentException("Overlow frame buffer, requested " + framesAgo + " overflows buffer length of " + this.history.length);
    }
    int floor = (int) framesAgo;
    float lerp = framesAgo - floor;
    int ceil = floor + 1;
    return LXUtils.lerp(
      this.history[(this.cursor + floor) % this.history.length],
      this.history[(this.cursor + ceil) % this.history.length],
      lerp
    );
  }

  public double getHistory(int framesAgo) {
    if (framesAgo >= this.history.length) {
      throw new IllegalArgumentException("Overlow frame buffer, requested " + framesAgo + " overflows buffer length of " + this.history.length);
    }
    return this.history[(this.cursor + framesAgo) % this.history.length];
  }

  public float getHistoryf(int framesAgo) {
    return (float) getHistory(framesAgo);
  }

  public double getHistory(int framesAgo, int avgLength) {
    if (framesAgo + avgLength >= this.history.length) {
      throw new IllegalArgumentException("Overlow frame buffer, requested " + (framesAgo + avgLength) + " overflows buffer length of " + this.history.length);
    }
    double total = 0;
    for (int i = 0; i < avgLength; ++i) {
      total += this.history[(this.cursor + framesAgo + i) % this.history.length];
    }
    return total / avgLength;
  }

  public float getHistoryf(int framesAgo, int avgLength) {
    return (float) getHistory(framesAgo, avgLength);
  }

}
