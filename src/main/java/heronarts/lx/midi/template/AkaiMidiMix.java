/**
 * Copyright 2024- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.midi.template;

import heronarts.lx.LX;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.LXParameter;

@LXMidiTemplate.Name("Akai MIDIMIX")
@LXMidiTemplate.DeviceName("MIDI Mix")
public class AkaiMidiMix extends LXMidiTemplate implements LXMidiTemplate.Bidirectional {

  public static final int NUM_CHANNELS = 8;
  public static final int KNOBS_PER_CHANNEL = 3;
  public static final int NUM_KNOBS = NUM_CHANNELS * KNOBS_PER_CHANNEL;
  public static final int NUM_FADERS = NUM_CHANNELS + 1;
  public static final int MASTER_FADER = NUM_CHANNELS;

  private class Toggle extends BooleanParameter {

    private final int note;

    private Toggle(String label, boolean def, int note) {
      super(label, def);
      this.note = note;
    }

  }

  public final BoundedParameter[] knobs = new BoundedParameter[NUM_KNOBS];
  public final BoundedParameter[] faders = new BoundedParameter[NUM_FADERS];
  public final Toggle[] mute = new Toggle[NUM_CHANNELS];
  public final Toggle[] solo = new Toggle[NUM_CHANNELS];
  public final Toggle[] arm = new Toggle[NUM_CHANNELS];

  private final BoundedParameter[] controlMap = new BoundedParameter[128];
  private final BooleanParameter[] noteMap = new BooleanParameter[128];

  public AkaiMidiMix(LX lx) {
    super(lx);
    for (int i = 0; i < NUM_KNOBS; ++i) {
      String knobId = (1+(i / 3)) + "-" + (1 + i%3);
      this.knobs[i] =
        new BoundedParameter("Knob " + knobId, 0)
        .setUnits(BoundedParameter.Units.PERCENT_NORMALIZED)
        .setDescription("MIDI Mix knob " + (i+1));
      addParameter("knob-" + knobId, this.knobs[i]);

      int col = (i / 3);
      int row = i % 3;
      if (col < 4) {
        this.controlMap[16 + 4*col + row] = this.knobs[i];
      } else {
        this.controlMap[46+ 4*(col-4) + row] = this.knobs[i];
      }
    }
    for (int i = 0; i < NUM_FADERS; ++i) {
      this.faders[i] =
        new BoundedParameter("Fader " + ((i == MASTER_FADER) ? "M" : (i+1)), 0)
        .setUnits(BoundedParameter.Units.PERCENT_NORMALIZED)
        .setDescription("MIDI Mix fader " + (i+1));
      addParameter("fader-" + (i+1), this.faders[i]);
      if (i < 4) {
        this.controlMap[19+4*i] = this.faders[i];
      } else if (i < NUM_CHANNELS) {
        this.controlMap[49+4*(i-4)] = this.faders[i];
      } else {
        this.controlMap[62] = this.faders[i];
      }
    }
    for (int i = 0; i < NUM_CHANNELS; ++i) {
      int note = 1 + 3*i;
      this.mute[i] = new Toggle("Mute " + (i+1), true, note);
      addParameter("mute-" + (i+1), this.mute[i]);
      this.noteMap[note] = this.mute[i];
    }
    for (int i = 0; i < NUM_CHANNELS; ++i) {
      int note = 2 + 3*i;
      this.solo[i] = new Toggle("Solo " + (i+1), false, note);
      addParameter("solo-" + (i+1), this.solo[i]);
      this.noteMap[note] = this.solo[i];
    }
    for (int i = 0; i < NUM_CHANNELS; ++i) {
      int note = 3 + 3*i;
      this.arm[i] = new Toggle("Arm " + (i+1), false, note);
      addParameter("arm-" + (i+1), this.arm[i]);
      this.noteMap[note] = this.arm[i];
    }
  }

  @Override
  protected void initializeOutput() {
    for (int i = 0; i < this.noteMap.length; ++i) {
      if (this.noteMap[i] != null) {
        sendNoteOn(0, i, this.noteMap[i].isOn() ? 127 : 0);
      }
    }
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p instanceof Toggle) {
      Toggle toggle = (Toggle) p;
      sendNoteOn(0, toggle.note, toggle.isOn() ? 127 : 0);
    }
  }

  @Override
  public void noteOnReceived(MidiNoteOn note) {
    final BooleanParameter button = this.noteMap[note.getPitch()];
    if (button != null) {
      button.toggle();
    }
  }

  @Override
  public void controlChangeReceived(MidiControlChange cc) {
    final BoundedParameter control = this.controlMap[cc.getCC()];
    if (control != null) {
      control.setValue(cc.getNormalized());
    }
  }

}
