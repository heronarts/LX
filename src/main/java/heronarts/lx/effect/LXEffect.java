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

package heronarts.lx.effect;

import heronarts.lx.midi.MidiAftertouch;
import heronarts.lx.midi.MidiControlChange;

import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.midi.LXMidiListener;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.midi.MidiPitchBend;
import heronarts.lx.midi.MidiProgramChange;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.modulator.LinearEnvelope;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.MutableParameter;

/**
 * Class to represent an effect that may be applied to the color array. Effects
 * may be stateless or stateful, though typically they operate on a single
 * frame. Only the current frame is provided at runtime.
 */
public abstract class LXEffect extends LXDeviceComponent implements LXComponent.Renamable, LXMidiListener, LXOscComponent {

  /**
   * Placeholder pattern for when a class is missing
   */
  public static class Placeholder extends LXEffect implements LXComponent.Placeholder {

    private String placeholderClassName;
    private JsonObject effectObj = null;

    public Placeholder(LX lx) {
      super(lx);
    }

    @Override
    public String getPlaceholderTypeName() {
      return "Effect";
    }

    @Override
    public String getPlaceholderClassName() {
      return this.placeholderClassName;
    }

    @Override
    public void save(LX lx, JsonObject object) {
      super.save(lx, object);

      // Just re-save exactly what was loaded
      if (this.effectObj != null) {
        for (Map.Entry<String, JsonElement> entry : this.effectObj.entrySet()) {
          object.add(entry.getKey(), entry.getValue());
        }
      }
    }

    @Override
    public void load(LX lx, JsonObject object) {
      super.load(lx, object);
      this.placeholderClassName = object.get(LXComponent.KEY_CLASS).getAsString();
      this.effectObj = object;
    }

    @Override
    protected void run(double deltaMs, double enabledAmount) {
    }

  }

  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", true)
    .setDescription("Whether the effect is enabled");

  protected final MutableParameter enabledDampingAttack = new MutableParameter(100);
  protected final MutableParameter enabledDampingRelease =  new MutableParameter(100);
  protected final LinearEnvelope enabledDamped = new LinearEnvelope(1, 1, 0);

  private boolean initialize = true;
  private boolean onEnable = false;
  private boolean onDisable = false;

  public class Profiler {
    public long runNanos = 0;
  }

  public final Profiler profiler = new Profiler();

  private int index = -1;

  private final LXParameterListener enabledListener = (p) -> {
    if (this.enabled.isOn()) {
      this.enabledDamped.setRangeFromHereTo(1, this.enabledDampingAttack.getValue()).start();
      this.onEnable = true;
    } else {
      this.enabledDamped.setRangeFromHereTo(0, this.enabledDampingRelease.getValue()).start();
      this.onDisable = true;
    }
  };

  protected LXEffect(LX lx) {
    super(lx);
    this.label.setDescription("The name of this effect");
    this.enabled.addListener(this.enabledListener);
    addParameter("enabled", this.enabled);
    addModulator(this.enabledDamped);
  }

  @Override
  protected boolean isRemoteControl(LXListenableNormalizedParameter parameter) {
    return (parameter != this.enabled);
  }

  @Override
  public String getPath() {
    return "effect/" + (this.index+1);
  }

  /**
   * Called by the engine to assign index on this effect. Should never
   * be called otherwise.
   *
   * @param index Effect index
   * @return this
   */
  public final LXEffect setIndex(int index) {
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

  public final LXEffect setBus(LXBus bus) {
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
    if (this.initialize) {
      if (this.enabled.isOn()) {
        onEnable();
      }
      this.initialize = false;
    }

    // Fire onEnable/onDisable event
    if (this.onEnable) {
      onEnable();
      this.onEnable = false;
    } else if (this.onDisable && this.enabledDamped.finished()) {
      onDisable();
      this.onDisable = false;
    }

    long runStart = System.nanoTime();
    double enabledDamped = this.enabledDamped.getValue();
    if (enabledDamped > 0) {
      run(deltaMs, enabledDamped);
    }
    this.profiler.runNanos = System.nanoTime() - runStart;
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
  public void dispose() {
    this.enabled.removeListener(this.enabledListener);
    super.dispose();
  }

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
