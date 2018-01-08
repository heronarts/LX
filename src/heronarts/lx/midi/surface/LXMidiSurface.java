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
import heronarts.lx.midi.LXMidiEngine;
import heronarts.lx.midi.LXMidiInput;
import heronarts.lx.midi.LXMidiListener;
import heronarts.lx.midi.LXMidiOutput;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;

public abstract class LXMidiSurface implements LXMidiListener, LXSerializable {

  public static final String APC40_MK2 = "APC40 mkII";

  private static LXMidiOutput findOutput(LXMidiEngine engine, String description) {
    for (LXMidiOutput output : engine.outputs) {
      if (output.getDescription().equals(description)) {
        return output;
      }
    }
    return null;
  }

  public static LXMidiSurface get(LX lx, LXMidiEngine engine, LXMidiInput input) {
    String description = input.getDescription();
    if (description.equals(APC40_MK2)) {
      return new APC40Mk2(lx, input, findOutput(engine, APC40_MK2));
    }
    return null;
  }

  protected final LX lx;
  protected final LXMidiInput input;
  protected final LXMidiOutput output;

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
          output.open();
          input.addListener(LXMidiSurface.this);
        } else {
          input.removeListener(LXMidiSurface.this);
        }
        onEnable(enabled.isOn());
      }
    });

  }

  public String getDescription() {
    return this.input.getDescription();
  }

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

  public static final String KEY_DESCRIPTION = "description";

  @Override
  public void load(LX lx, JsonObject object) {

  }

  @Override
  public void save(LX lx, JsonObject object) {
    object.addProperty(KEY_DESCRIPTION, this.input.getDescription());
  }

}
