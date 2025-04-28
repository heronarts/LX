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

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.utils.LXUtils;

@LXMidiTemplate.Name("Novation Launchkey MK3 37")
@LXMidiTemplate.DeviceName("Launchkey MK3 37 LKMK3 MIDI Out")
public class NovationLaunchkeyMk337 extends LXMidiTemplate {

  private static final int NUM_KNOBS = 8;
  private static final int KNOB_CHANNEL = 0;
  private static final int KNOB_1 = 21;

  private static final int NUM_PADS = 16;
  private static final int PAD_CHANNEL = 9;
  private static final int PAD_1 = 36;

  private static final int BUTTON_CHANNEL = 15;

  public final BoundedParameter[] knobs = new BoundedParameter[NUM_KNOBS];

  public final BooleanParameter[] pads = new BooleanParameter[NUM_PADS];

  private final BooleanParameter[] buttonMap = new BooleanParameter[128];

  private final List<BooleanParameter> buttons = new ArrayList<BooleanParameter>();

  private BooleanParameter button(String path, String label, int cc) {
    BooleanParameter button =
      new BooleanParameter(label)
      .setMode(BooleanParameter.Mode.MOMENTARY)
      .setDescription(label + " button");
    addParameter(path, button);
    this.buttonMap[cc] = button;
    this.buttons.add(button);
    return button;
  }

  public final BooleanParameter capture = button("capture", "Capture", 74);
  public final BooleanParameter quantise = button("quantise", "Quantise", 75);
  public final BooleanParameter click = button("click", "Click", 76);
  public final BooleanParameter undo = button("undo", "Undo", 77);
  public final BooleanParameter play = button("play", "Play", 115);
  public final BooleanParameter stop = button("stop", "Stop", 116);
  public final BooleanParameter rec = button("Rec", "Rec", 117);
  public final BooleanParameter loop = button("loop", "Loop", 118);

  public final BooleanParameter up = button("up", "▲", 106);
  public final BooleanParameter down = button("down", "▼", 107);
  public final BooleanParameter right = button("right", ">", 104);
  public final BooleanParameter ssm = button("ssm", "S", 105);

  public NovationLaunchkeyMk337(LX lx) {
    super(lx);
    for (int i = 0; i < NUM_KNOBS; ++i) {
      String label = "K" + (i+1);
      addParameter(label, this.knobs[i] =
        new BoundedParameter(label)
        .setUnits(BoundedParameter.Units.PERCENT_NORMALIZED)
        .setDescription("Knob " + (i+1))
      );
    }

    for (int i = 0; i < NUM_PADS; ++i) {
      String label = "P" + (i+1);
      addParameter(label, this.pads[i] =
        new BooleanParameter(label)
        .setMode(BooleanParameter.Mode.MOMENTARY)
        .setDescription("Pad " + (i+1))
      );
    }
  }

  private final int[] PAD_MAP = {
    8, 9, 10, 11, 0, 1, 2, 3, 12, 13, 14, 15, 4, 5, 6, 7
  };

  private void setPad(MidiNote note, boolean value) {
    if (note.getChannel() == PAD_CHANNEL) {
      final int padIndex = note.getPitch() - PAD_1;
      if (LXUtils.inRange(padIndex, 0, NUM_PADS - 1)) {
        this.pads[PAD_MAP[padIndex]].setValue(value);
      }
    }
  }

  @Override
  public void noteOnReceived(MidiNoteOn note) {
    setPad(note, true);
  }

  @Override
  public void noteOffReceived(MidiNote note) {
    setPad(note, false);
  }

  @Override
  public void controlChangeReceived(MidiControlChange cc) {
    switch (cc.getChannel()) {
    case KNOB_CHANNEL:
      final int knobIndex = cc.getCC() - KNOB_1;
      if (LXUtils.inRange(knobIndex, 0, NUM_KNOBS - 1)) {
        this.knobs[knobIndex].setNormalized(cc.getNormalized());
      }
      break;
    case BUTTON_CHANNEL:
      final BooleanParameter button = this.buttonMap[cc.getCC()];
      if (button != null) {
        button.setNormalized(cc.getNormalized());
      }
      break;
    }
  }

  @Override
  public void midiPanicReceived() {
    for (BooleanParameter pad : this.pads) {
      pad.setValue(false);
    }
    for (BooleanParameter button : this.buttons) {
      button.setValue(false);
    }
  }

}
