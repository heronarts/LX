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

package heronarts.lx.midi.surface;

import java.util.Collections;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXSerializable;
import heronarts.lx.midi.LXMidiInput;
import heronarts.lx.midi.LXMidiListener;
import heronarts.lx.midi.LXMidiOutput;
import heronarts.lx.midi.MidiAftertouch;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.midi.MidiPitchBend;
import heronarts.lx.midi.MidiProgramChange;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXListenableParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;

public abstract class LXMidiSurface implements LXMidiListener, LXSerializable, LXParameterListener {

  /**
   * Marker interface for Midi Surface implementations which require an output
   * to be functional. Many surfaces are fine with just an input for control,
   * but those like the APC40mk2 which require 2-way communication to update
   * LEDs etc. need this marker
   */
  public interface Bidirectional {}

  protected final LX lx;

  /**
   * The midi input device for this control surface. Never null.
   */
  public final LXMidiInput input;

  /**
   * The midi output device for this control surface. May be null in cases where the
   * control surface does not implement the OutputRequired interface
   */
  public final LXMidiOutput output;

  public final BooleanParameter enabled =
    new BooleanParameter("Enabled")
    .setMappable(false)
    .setDescription("Whether the control surface is enabled");

  protected final LXParameter.Collection mutableSettings = new LXParameter.Collection();
  public final Map<String, LXParameter> settings = Collections.unmodifiableMap(this.mutableSettings);

  protected final LXParameter.Collection mutableState = new LXParameter.Collection();
  public final Map<String, LXParameter> state = Collections.unmodifiableMap(this.mutableState);

  // Internal flag for enabled state, pre/post-teardown
  private boolean _enabled = false;

  protected LXMidiSurface(LX lx, final LXMidiInput input, final LXMidiOutput output) {
    this.lx = lx;
    this.input = input;
    this.output = output;
    if ((this instanceof Bidirectional) && (output == null)) {
      throw new IllegalArgumentException("Surface " + getClass().getSimpleName() + " requires MIDI output");
    }
    this.enabled.addListener((p) -> {
      boolean on = this.enabled.isOn();
      if (on) {
        // Make sure I/O channels are enabled
        this.input.open();
        if (this.output != null) {
          this.output.open();
        }
        // Listen to the input
        this.input.addListener(this);

        // Enable sending and turn on the surface
        this._enabled = on;
        onEnable(on);
      } else {
        // Fire the onEnable() *before* deactivating _enabled, in case the surface
        // wants to turn off LED lights, etc.
        onEnable(on);
        this._enabled = on;

        // Stop listening to the input
        this.input.removeListener(this);
      }

    });

    this.output.connected.addListener((p) -> {
      if (this.output.connected.isOn()) {
        this.input.open();
        this.output.open();
        onReconnect();
      }
    });
  }

  public void onParameterChanged(LXParameter p) {}

  protected void addSetting(String key, LXListenableParameter setting) {
    if (this.mutableSettings.containsKey(key)) {
      throw new IllegalStateException("Cannot add setting twice:" + key);
    }
    this.mutableSettings.put(key, setting);
    setting.addListener(this);
  }

  protected void addState(String key, LXListenableParameter state) {
    if (this.mutableState.containsKey(key)) {
      throw new IllegalStateException("Cannot add saved state twice:" + key);
    }
    this.mutableState.put(key, state);
    state.addListener(this);
  }

  public String getName() {
    return this.input.getName();
  }

  public LXMidiInput getInput() {
    return this.input;
  }

  public LXMidiOutput getOutput() {
    return this.output;
  }

  /**
   * Subclasses may override, invoked automatically when surface is enabled/disabled
   *
   * @param isOn Whether surface is enabled
   */
  protected void onEnable(boolean isOn) {}

  /**
   * Subclasses may override, invoked when the control surface was disconnected but
   * has now reconnected. Re-initialization may be necessary.
   */
  protected void onReconnect() {}

  protected void sendNoteOn(int channel, int note, int velocity) {
    if (this._enabled) {
      this.output.sendNoteOn(channel, note, velocity);
    }
  }

  protected void sendControlChange(int channel, int cc, int value) {
    if (this._enabled) {
      this.output.sendControlChange(channel, cc, value);
    }
  }

  public int getRemoteControlStart() {
    return 0;
  }

  public int getRemoteControlLength() {
    return 0;
  }

  public boolean isRemoteControlAux() {
    return false;
  }

  public static final String KEY_CLASS = "class";
  public static final String KEY_NAME = "name";
  public static final String KEY_SETTINGS = "settings";
  public static final String KEY_STATE = "state";

  @Override
  public void load(LX lx, JsonObject obj) {
    if (obj.has(KEY_SETTINGS)) {
      final JsonElement settingsElem = obj.get(KEY_SETTINGS);
      if (settingsElem.isJsonObject()) {
        LXSerializable.Utils.loadParameters(settingsElem.getAsJsonObject(), this.mutableSettings);
      }
    }
    if (obj.has(KEY_STATE)) {
      final JsonElement stateElem = obj.get(KEY_STATE);
      if (stateElem.isJsonObject()) {
        LXSerializable.Utils.loadParameters(stateElem.getAsJsonObject(), this.mutableState);
      }
    }
  }

  @Override
  public void save(LX lx, JsonObject obj) {
    obj.addProperty(KEY_CLASS, getClass().getName());
    obj.addProperty(KEY_NAME, getName());
    if (!this.settings.isEmpty()) {
      obj.add(KEY_SETTINGS, LXSerializable.Utils.saveParameters(this.mutableSettings));
    }
    if (!this.state.isEmpty()) {
      obj.add(KEY_STATE, LXSerializable.Utils.saveParameters(this.mutableState));
    }
  }

  public boolean matches(JsonObject surface) {
    // NOTE: legacy compabitility, pre-1.0.1 didn't store the surface CLASS
    // here when there was only one surface type per device name supported
    final String surfaceClass = surface.has(KEY_CLASS) ? surface.get(KEY_CLASS).getAsString() : null;
    return getName().equals(surface.get(KEY_NAME).getAsString()) &&
      ((surfaceClass == null) || surfaceClass.equals(getClass().getName()));
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
  public void programChangeReceived(MidiProgramChange pc) {
  }

  @Override
  public void pitchBendReceived(MidiPitchBend pitchBend) {
  }

  @Override
  public void aftertouchReceived(MidiAftertouch aftertouch) {
  }

  public void dispose() {
    for (LXParameter setting : this.settings.values()) {
      ((LXListenableParameter) setting).removeListener(this);
      setting.dispose();
    }
    for (LXParameter state : this.state.values()) {
      ((LXListenableParameter) state).removeListener(this);
      state.dispose();
    }
    this.mutableSettings.clear();
  }

}
