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

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.Tempo;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.FixedParameter;
import heronarts.lx.parameter.LXParameter;

/**
 * An LXPeriodicModulator is a modulator that moves through a cycle in a given
 * amount of time. It may then repeat the cycle, or perform it once. The values
 * are computed based upon a position in the cycle, internally referred to as a
 * basis, which moves from 0 to 1. This can be thought of as equivalent to an
 * angle moving from 0 to two times pi. The period itself is a parameter which
 * may be a modulator or otherwise.
 */
public abstract class LXPeriodicModulator extends LXModulator {

  /**
   * Whether this modulator runs continuously looping.
   */
  public final BooleanParameter looping =
    new BooleanParameter("Loop", true)
    .setDescription("Whether this modulator loops at the end of its cycle");

  public final BooleanParameter tempoSync =
    new BooleanParameter("Sync", false)
    .setDescription("Whether this modulator syncs to a tempo");

  public final EnumParameter<Tempo.Division> tempoDivision =
    new EnumParameter<Tempo.Division>("Division", Tempo.Division.QUARTER)
    .setDescription("Tempo division when in sync mode");

  public final BooleanParameter tempoLock =
    new BooleanParameter("Lock", true)
    .setDescription("Whether this modulator is locked to the beat grid or free-running");

  /**
   * Whether the modulator finished on this cycle.
   */
  private boolean finished = false;

  private boolean needsReset = false;

  private boolean disableAutoReset = false;

  /**
   * Whether the modulator looped on this cycle.
   */
  private boolean looped = false;

  /**
   * The number of times the modulator looped on this cycle; should be
   * 0 or 1 unless the period's extremely short and/or the machine is overworked.
   */
  private int numLoops = 0;

  /**
   * Flag set when we are in a reset operation
   */
  private boolean reset = false;

  /**
   * Flag set when we are restarted
   */
  private boolean restarted = false;

  /**
   * The basis is a value that moves from 0 to 1 through the period
   */
  private double basis = 0;

  /**
   * The number of milliseconds in the period of this modulator.
   */
  private LXParameter period;

  /**
   * Utility constructor with period
   *
   * @param label Label
   * @param period Parameter for period
   */
  protected LXPeriodicModulator(String label, LXParameter period) {
    super(label);
    addParameter("loop", this.looping);
    addParameter("tempoSync", this.tempoSync);
    addParameter("tempoMultiplier", this.tempoDivision);
    addParameter("tempoLock", this.tempoLock);
    this.tempoDivision.setWrappable(false);
    this.period = period;
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.running) {
      if (this.running.isOn()) {
        this.restarted = true;
        if (this.needsReset && !this.disableAutoReset) {
          this.setBasis(0);
        }
      }
    } else if (p == this.tempoLock) {
      if (this.tempoLock.isOn()) {
        this.restarted = true;
      }
    }
  }

  /**
   * Sets whether the modulator should loop after it completes a cycle or halt
   * at the end position.
   *
   * @param looping Whether to loop
   * @return this, for method chaining
   */
  public LXPeriodicModulator setLooping(boolean looping) {
    this.looping.setValue(looping);
    return this;
  }

  public LXPeriodicModulator disableAutoReset() {
    this.disableAutoReset = true;
    return this;
  }

  /**
   * Accessor for the current basis
   *
   * @return The basis of the modulator
   */
  public final double getBasis() {
    return this.basis;
  }

  /**
   * Accessor for basis as a float
   *
   * @return basis as float
   */
  public final float getBasisf() {
    return (float) getBasis();
  }

  @Override
  protected void onReset() {
    this.reset = true;
    setBasis(0);
    this.reset = false;
    this.needsReset = false;
  }

  /**
   * Sets the basis to a random position
   *
   * @return this
   */
  public final LXPeriodicModulator randomBasis() {
    setBasis(Math.random());
    return this;
  }

  /**
   * Set the modulator to a certain basis position in its cycle.
   *
   * @param basis Basis of modulator, from 0-1
   * @return this
   */
  public final LXPeriodicModulator setBasis(double basis) {
    if (basis < 0) {
      basis = 0;
    } else if (basis > 1) {
      basis = 1;
    }
    this.basis = basis;
    updateValue(this.computeValue(0));
    return this;
  }

  /**
   * Set the modulator to a certain value in its cycle.
   *
   * @param value The value to apply
   */
  @Override
  public void onSetValue(double value) {
    this.updateBasis(value);
  }

  /**
   * Updates the basis of the modulator based on present values.
   *
   * @param value New value of the modulator
   */
  protected final void updateBasis(double value) {
    this.basis = computeBasis(this.basis, value);
  }

  /**
   * Modify the period of this modulator
   *
   * @param periodMs New period, in milliseconds
   * @return Modulator, for method chaining;
   */
  public final LXPeriodicModulator setPeriod(double periodMs) {
    this.period = new FixedParameter(periodMs);
    return this;
  }

  /**
   * @return The period of this modulator
   */
  public final double getPeriod() {
    return this.period.getValue();
  }

  /**
   * @return The period of this modulator as a floating point
   */
  public final float getPeriodf() {
    return (float) this.getPeriod();
  }

  /**
   * Sets a parameter to the period of this modulator
   *
   * @param period Parameter for period value
   * @return This modulator, for method chaining
   */
  final public LXPeriodicModulator setPeriod(LXParameter period) {
    this.period = period;
    return this;
  }

  @Override
  public void loop(double deltaMs) {
    this.finished = false;
    this.looped = false;
    this.numLoops = 0;
    super.loop(deltaMs);
  }

  private int previousCycle = 0;

  @Override
  protected final double computeValue(double deltaMs) {
    this.finished = false;
    this.looped = false;
    this.numLoops = 0;
    this.needsReset = false;
    final double periodv = this.period.getValue();
    if (this.tempoSync.isOn()) {
      if (this.tempoLock.isOn()) {
        final Tempo.Division division = this.tempoDivision.getEnum();
        final int cycle = this.lx.engine.tempo.getCycleCount(division);
        this.basis = this.lx.engine.tempo.getBasis(division);
        if (this.reset) {
          // No-op, just let the lastMeasure value be updated beneath, resets
          // of tempo synced modulators shouldn't be treated as having finished
          // their loop... the tempo is always running continuously in the background
        } else if (this.restarted) {
          this.restarted = false;
        } else if (cycle != this.previousCycle) {
          if (this.looping.isOn()) {
            this.looped = true;
            this.numLoops = (cycle > this.previousCycle) ? (cycle - this.previousCycle) : 1;
          } else {
            this.basis = 1.;
            // NOTE - code path below for basis >= 1 will set finished/needsReset and stop()
          }
        }
        this.previousCycle = cycle;
      } else {
        this.basis += deltaMs / this.lx.engine.tempo.period.getValue() * this.tempoDivision.getEnum().multiplier;
      }
    } else if (periodv == 0) {
      this.basis = 1;
    } else {
      this.basis += deltaMs / periodv;
    }

    if (this.basis >= 1.) {
      if (this.looping.isOn()) {
        this.looped = true;
        this.numLoops = (int) this.basis;
        this.basis = this.basis % 1;
      } else {
        this.basis = 1.;
        this.finished = true;
        this.needsReset = true;
        stop();
      }
    }
    return computeValue(deltaMs, this.basis);
  }

  /**
   * Returns true once each time this modulator loops through its starting position.
   *
   * @return true if the modulator just looped
   */
  public final boolean loop() {
    return this.looped;
  }

  /**
   * Returns the number of times the modulator looped on the last cycle; should be
   * 0 or 1 unless the period's extremely short and/or the machine is overworked.
   *
   * @return number of loops on the last cycle
   */
  public final int numLoops() {
    return this.numLoops;
  }

  /**
   * For envelope modulators, which are not looping, this returns true if they
   * finished on this frame.
   *
   * @return true if the modulator just finished its operation on this frame
   */
  public final boolean finished() {
    return this.finished;
  }

  /**
   * Implementation method to compute the value of a modulator given its basis.
   *
   * @param deltaMs Milliseconds elapsed
   * @param basis Basis of the modulator
   * @return Value of modulator
   */
  abstract protected double computeValue(double deltaMs, double basis);

  /**
   * Implementation method to compute the appropriate basis for a modulator
   * given its current basis and value.
   *
   * @param basis Last basis of modulator
   * @param value Current value of modulator
   * @return Basis of modulator
   */
  abstract protected double computeBasis(double basis, double value);

  private static final String KEY_BASIS = "basis";

  @Override
  public void save(LX lx, JsonObject object) {
    super.save(lx, object);
    object.addProperty(KEY_BASIS, this.basis);
  }

  @Override
  public void load(LX lx, JsonObject object) {
    super.load(lx,  object);
    if (object.has(KEY_BASIS)) {
      setBasis(object.get(KEY_BASIS).getAsDouble());
    }
  }
}
