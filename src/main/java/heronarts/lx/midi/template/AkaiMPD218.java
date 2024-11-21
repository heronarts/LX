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

import java.util.Arrays;
import heronarts.lx.LX;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;

@LXMidiTemplate.Name("Akai MPD218")
@LXMidiTemplate.DeviceName("MPD218")
public class AkaiMPD218 extends LXMidiTemplate implements LXMidiTemplate.Bidirectional {

  public static final int NUM_BANKS = 3;
  public static final int NUM_PADS = 16;
  public static final int NUM_KNOBS = 6;

  private static final int[] NOTE_TO_PAD = new int[128];
  private static final int[] CC_TO_KNOB = new int[128];
  private static final int PAD_BANK_A = 36;
  private static final int PAD_BANK_B = 52;
  private static final int PAD_BANK_C = 68;

  private static final int KNOB_BANK_A1 = 3;
  private static final int KNOB_BANK_A2 = 9;
  private static final int KNOB_BANK_A3 = 12;
  private static final int KNOB_BANK_A4 = 13;
  private static final int KNOB_BANK_A5 = 14;
  private static final int KNOB_BANK_A6 = 15;

  private static final int KNOB_BANK_B = 16;
  private static final int KNOB_BANK_C = 22;

  private static final int[] KNOB_TO_CC = {
    3, 9, 12, 13, 14, 15,
    16, 17, 18, 19, 20, 21,
    22, 23, 24, 25, 26, 27
  };

  static {
    Arrays.fill(NOTE_TO_PAD, -1);
    Arrays.fill(CC_TO_KNOB, -1);

    for (int i = 0; i < NUM_PADS; ++i) {
      NOTE_TO_PAD[PAD_BANK_A+i] = i;
      NOTE_TO_PAD[PAD_BANK_B+i] = NUM_PADS + i;
      NOTE_TO_PAD[PAD_BANK_C+i] = 2*NUM_PADS +i;
    }

    for (int i = 0; i < NUM_KNOBS; ++i) {
      CC_TO_KNOB[KNOB_BANK_B+i] = NUM_KNOBS + i;
      CC_TO_KNOB[KNOB_BANK_C+i] = 2*NUM_KNOBS + i;
    }
    CC_TO_KNOB[KNOB_BANK_A1] = 0;
    CC_TO_KNOB[KNOB_BANK_A2] = 1;
    CC_TO_KNOB[KNOB_BANK_A3] = 2;
    CC_TO_KNOB[KNOB_BANK_A4] = 3;
    CC_TO_KNOB[KNOB_BANK_A5] = 4;
    CC_TO_KNOB[KNOB_BANK_A6] = 5;
  }

  public enum Bank {
    A,
    B,
    C;
  }

  public final EnumParameter<Bank> bank =
    new EnumParameter<Bank>("Bank", Bank.A)
    .setDescription("Which MPD bank is visible for editing");

  public final BooleanParameter[] pads = new BooleanParameter[NUM_BANKS * NUM_PADS];

  public final Knob[] knobs = new Knob[NUM_BANKS * NUM_KNOBS];

  private class Knob extends BoundedParameter {

    private final int cc;

    private Knob(String label, int cc) {
      super(label, 0);
      setUnits(BoundedParameter.Units.PERCENT_NORMALIZED);
      setDescription("MPD218 knob " + label);
      this.cc = cc;
    }

  }

  private class Pad extends BooleanParameter {

    private Pad(String label) {
      super(label, false);
      setMode(BooleanParameter.Mode.MOMENTARY);
      setDescription("MPD218 pad " + label);
    }

  }

  public AkaiMPD218(LX lx) {
    super(lx);
    for (Bank bank : Bank.values()) {
      for (int i = 0; i < NUM_PADS; ++i) {
        final String label = bank.toString() + (i+1);
        final int index = bank.ordinal() * NUM_PADS + i;
        this.pads[index] = new Pad("P" + label);
        addParameter("pad-" + label, this.pads[index]);
      }
      for (int i = 0; i < NUM_KNOBS; ++i) {
        final String label = bank.toString() + (i+1);
        final int index = bank.ordinal() * NUM_KNOBS + i;
        this.knobs[index] = new Knob(label, KNOB_TO_CC[index]);
        addParameter("knob-" + label, this.knobs[index]);
      }
    }
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p instanceof Knob) {
      Knob knob = (Knob) p;
      sendControlChange(0, knob.cc, (int) Math.round((127 * knob.getValue())));
    }
  }

  @Override
  protected void initializeOutput() {
    for (int cc = 0; cc < CC_TO_KNOB.length; ++cc) {
      int knob = CC_TO_KNOB[cc];
      if (knob >= 0) {
        sendControlChange(0, cc, (int) Math.round((127 * this.knobs[knob].getValue())));
      }
    }
  }

  public void noteOnReceived(MidiNoteOn note) {
    int pad = NOTE_TO_PAD[note.getPitch()];
    if (pad >= 0) {
      pads[pad].setValue(true);
    }
  }

  public void noteOffReceived(MidiNote note) {
    int pad = NOTE_TO_PAD[note.getPitch()];
    if (pad >= 0) {
      pads[pad].setValue(false);
    }
  }

  public void controlChangeReceived(MidiControlChange cc) {
    int knob = CC_TO_KNOB[cc.getCC()];
    if (knob >= 0) {
      knobs[knob].setNormalized(cc.getNormalized());
    }
  }
}