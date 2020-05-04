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

public abstract class LXMidiSurface implements LXMidiListener, LXSerializable {

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

  public final BooleanParameter enabled = (BooleanParameter)
    new BooleanParameter("Enabled")
    .setMappable(false)
    .setDescription("Whether the control surface is enabled");

  protected LXMidiSurface(LX lx, final LXMidiInput input, final LXMidiOutput output) {
    this.lx = lx;
    this.input = input;
    this.output = output;
    if ((this instanceof Bidirectional) && (output == null)) {
      throw new IllegalArgumentException("Surface " + getClass().getSimpleName() + " requires MIDI output");
    }
    this.enabled.addListener((p) -> {
      if (this.enabled.isOn()) {
        // Make sure I/O channels are enabled
        this.input.open();
        if (this.output != null) {
          this.output.open();
        }
        // Listen to the input
        this.input.addListener(this);
      } else {
        // Stop listening to the input
        this.input.removeListener(this);
      }
      onEnable(this.enabled.isOn());
    });

    this.output.connected.addListener((p) -> {
      if (this.output.connected.isOn()) {
        this.input.open();
        this.output.open();
        onReconnect();
      }
    });
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
    if (this.enabled.isOn()) {
      this.output.sendNoteOn(channel, note, velocity);
    }
  }

  protected void sendControlChange(int channel, int cc, int value) {
    if (this.enabled.isOn()) {
      this.output.sendControlChange(channel, cc, value);
    }
  }

  public static final String KEY_NAME = "name";

  @Override
  public void load(LX lx, JsonObject object) {

  }

  @Override
  public void save(LX lx, JsonObject object) {
    object.addProperty(KEY_NAME, this.input.getName());
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

}
