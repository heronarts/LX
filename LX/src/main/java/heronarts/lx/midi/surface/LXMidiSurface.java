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
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;

public abstract class LXMidiSurface implements LXMidiListener, LXSerializable {

  protected final LX lx;
  public final LXMidiInput input;
  public final LXMidiOutput output;

  public final BooleanParameter enabled =
    new BooleanParameter("Enabled")
    .setDescription("Whether the control surface is enabled");

  protected LXMidiSurface(LX lx, final LXMidiInput input, final LXMidiOutput output) {
    this.lx = lx;
    this.input = input;
    this.output = output;
    this.enabled.addListener(new LXParameterListener() {
      public void onParameterChanged(LXParameter p) {
        if (enabled.isOn()) {
          input.open();
          if (output != null) {
            output.open();
          }
          input.addListener(LXMidiSurface.this);
        } else {
          input.removeListener(LXMidiSurface.this);
        }
        onEnable(enabled.isOn());
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
