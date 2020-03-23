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

package heronarts.lx.modulator;

import heronarts.lx.parameter.FixedParameter;
import heronarts.lx.parameter.LXParameter;

/**
 * A click is a simple modulator that fires a value of 1 every time its period
 * has passed. Otherwise it always returns 0.
 */
public class Click extends LXPeriodicModulator {

  private boolean fired = false;

  public Click(double periodMs) {
    this(new FixedParameter(periodMs));
  }

  public Click(LXParameter periodMs) {
    this("CLICK", periodMs);
  }

  public Click(String label, double periodMs) {
    this(label, new FixedParameter(periodMs));
  }

  public Click(String label, LXParameter periodMs) {
    super(label, periodMs);
  }

  @Override
  public void loop(double deltaMs) {
    if (!this.isRunning()) {
      setValue(0, false);
    }
    super.loop(deltaMs);
    this.fired = false;
  }

  /**
   * Sets the value of the click to 1, so that code querying it in this frame of
   * execution sees it as active. On the next iteration of the run loop it will
   * be off again.
   *
   * @return this
   */
  public LXModulator fire() {
    setValue(1, false);
    setBasis(0);
    start();
    this.fired = true;
    return this;
  }

  /**
   * Helper to conditionalize logic based on the click. Typical use is to query
   * as follows:
   *
   * <pre>
   * if (clickInstance.click()) {
   *   // perform periodic operation
   * }
   * </pre>
   *
   * @return true if the value is 1, otherwise false
   */
  public boolean click() {
    return this.getValue() == 1;
  }

  @Override
  protected double computeValue(double deltaMs, double basis) {
    return this.fired || loop() || finished() ? 1 : 0;
  }

  @Override
  protected double computeBasis(double basis, double value) {
    // The basis is indeterminate for this modulator, it can only be
    // specifically known when the value is 1.
    return value < 1 ? 0 : 1;
  }
}