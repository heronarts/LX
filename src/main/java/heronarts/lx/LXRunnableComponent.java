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

package heronarts.lx;

import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.TriggerParameter;

public abstract class LXRunnableComponent extends LXComponent implements LXLoopTask {

  protected double runMs = 0;

  /**
   * Whether this modulator is currently running.
   */
  public final BooleanParameter running =
    new BooleanParameter("Running", false)
    .setDescription("Sets whether the component is running");

  public final TriggerParameter trigger =
    new TriggerParameter("Trigger", this::_trigger)
    .setDescription("Resets the cycle and starts running");

  protected LXRunnableComponent() {
    this(null, null);
  }

  protected LXRunnableComponent(String label) {
    this(null, label);
  }

  protected LXRunnableComponent(LX lx) {
    this(lx, null);
  }

  protected LXRunnableComponent(LX lx, String label) {
    super(lx, label);
    addParameter("running", this.running);
    addParameter("trigger", this.trigger);
  }

  @Override
  public void onParameterChanged(LXParameter parameter) {
    super.onParameterChanged(parameter);
    if (parameter == this.running) {
      if (this.running.isOn()) {
        onStart();
      } else {
        onStop();
      }
    }
  }

  private void _trigger() {
    onTrigger();
    onReset();
    start();
  }

  public final LXRunnableComponent toggle() {
    this.running.toggle();
    return this;
  }

  /**
   * Sets the runnable in motion
   *
   * @return this
   */
  public final LXRunnableComponent start() {
    this.running.setValue(true);
    return this;
  }

  /**
   * Pauses the runnable wherever it is. Internal state should be maintained. A
   * subsequent call to start() should result in the runnable continuing as it
   * was running before.
   *
   * @return this
   */
  public final LXRunnableComponent stop() {
    this.running.setValue(false);
    return this;
  }

  /**
   * Indicates whether this runnable is running.
   *
   * @return Whether running
   */
  public final boolean isRunning() {
    return this.running.isOn();
  }

  /**
   * Invoking the trigger() method restarts a runnable from its initial state,
   * and should also start the runnable if it is not already running.
   *
   * @return this
   */
  public final LXRunnableComponent trigger() {
    this.trigger.trigger();
    return this;
  }

  /**
   * Resets the runnable to its default condition and stops it.
   *
   * @return this
   */
  public final LXRunnableComponent reset() {
    stop();
    onReset();
    return this;
  }

  /**
   * Optional subclass method when start happens.
   */
  protected/* abstract */void onStart() {

  }

  /**
   * Optional subclass method when stop happens.
   */
  protected/* abstract */void onStop() {

  }

  /**
   * Optional subclass method when reset happens.
   */
  protected/* abstract */void onReset() {
  }

  /**
   * Optional subclass method when trigger is fired, called before onReset
   * and onStart
   */
  protected/* abstract */void onTrigger() {

  }


  @Override
  public void loop(double deltaMs) {
    if (this.running.isOn()) {
      this.runMs += deltaMs;
      run(deltaMs);
    }
    postRun(deltaMs);
  }

  protected abstract void run(double deltaMs);

  protected void postRun(double deltaMs) {}

}
