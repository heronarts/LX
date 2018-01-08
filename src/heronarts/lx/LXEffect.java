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

import heronarts.lx.midi.MidiAftertouch;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.midi.LXMidiListener;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.midi.MidiPitchBend;
import heronarts.lx.midi.MidiProgramChange;
import heronarts.lx.modulator.LinearEnvelope;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.MutableParameter;

/**
 * Class to represent an effect that may be applied to the color array. Effects
 * may be stateless or stateful, though typically they operate on a single
 * frame. Only the current frame is provided at runtime.
 */
public abstract class LXEffect extends LXDeviceComponent implements LXComponent.Renamable, LXMidiListener, LXOscComponent {

  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", false)
    .setDescription("Whether the effect is enabled");

  protected final MutableParameter enabledDampingAttack = new MutableParameter(100);
  protected final MutableParameter enabledDampingRelease = new MutableParameter(100);
  protected final LinearEnvelope enabledDamped = new LinearEnvelope(0, 0, 0);

  public class Timer {
    public long runNanos = 0;
  }

  public final Timer timer = new Timer();

  private int index = -1;

  protected LXEffect(LX lx) {
    super(lx);
    this.label.setDescription("The name of this effect");
    String simple = getClass().getSimpleName();
    if (simple.endsWith("Effect")) {
      simple = simple.substring(0, simple.length() - "Effect".length());
    }
    this.label.setValue(simple);

    this.enabled.addListener(new LXParameterListener() {
      public void onParameterChanged(LXParameter parameter) {
        if (LXEffect.this.enabled.isOn()) {
          enabledDamped.setRangeFromHereTo(1, enabledDampingAttack.getValue()).start();
          onEnable();
        } else {
          enabledDamped.setRangeFromHereTo(0, enabledDampingRelease.getValue()).start();
          onDisable();
        }
      }
    });

    addParameter("enabled", this.enabled);
    addModulator(this.enabledDamped);
  }

  public String getOscAddress() {
    LXBus bus = getBus();
    if (bus != null) {
      return bus.getOscAddress() + "/effect/" + (this.index+1);
    }
    return null;
  }

  /**
   * Called by the engine to assign index on this effect. Should never
   * be called otherwise.
   *
   * @param index
   * @return
   */
  final LXEffect setIndex(int index) {
    this.index = index;
    return this;
  }

  /**
   * Gets the index of this effect in the channel FX bus.
   *
   * @return index of this effect in the channel FX bus
   */
  public final int getIndex() {
    return this.index;
  }

  public LXEffect setBus(LXBus bus) {
    setParent(bus);
    return this;
  }

  public LXBus getBus() {
    return (LXBus) getParent();
  }

  /**
   * @return whether the effect is currently enabled
   */
  public final boolean isEnabled() {
    return this.enabled.isOn();
  }

  /**
   * Toggles the effect.
   *
   * @return this
   */
  public final LXEffect toggle() {
    this.enabled.toggle();
    return this;
  }

  /**
   * Enables the effect.
   *
   * @return this
   */
  public final LXEffect enable() {
    this.enabled.setValue(true);
    return this;
  }

  /**
   * Disables the effect.
   *
   * @return this
   */
  public final LXEffect disable() {
    this.enabled.setValue(false);
    return this;
  }

  protected/* abstract */void onEnable() {
  }

  protected/* abstract */void onDisable() {
  }

  /**
   * Applies this effect to the current frame
   *
   * @param deltaMs Milliseconds since last frame
   */
  @Override
  public final void onLoop(double deltaMs) {
    long runStart = System.nanoTime();
    double enabledDamped = this.enabledDamped.getValue();
    if (enabledDamped > 0) {
      run(deltaMs, enabledDamped);
    }
    this.timer.runNanos = System.nanoTime() - runStart;
  }

  /**
   * Implementation of the effect. Subclasses need to override this to implement
   * their functionality.
   *
   * @param deltaMs Number of milliseconds elapsed since last invocation
   * @param enabledAmount The amount of the effect to apply, scaled from 0-1
   */
  protected abstract void run(double deltaMs, double enabledAmount);

  @Override
  public void noteOnReceived(MidiNoteOn note) {

  }

  @Override
  public void noteOffReceived(MidiNote note) {

  }

  @Override
  public void controlChangeReceived(MidiControlChange cc) {

  }

  @Override
  public void programChangeReceived(MidiProgramChange cc) {

  }

  @Override
  public void pitchBendReceived(MidiPitchBend pitchBend) {

  }

  @Override
  public void aftertouchReceived(MidiAftertouch aftertouch) {

  }

}
