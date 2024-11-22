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
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.LXParameter;

@LXMidiTemplate.Name("DJTT Midi Fighter Twister")
@LXMidiTemplate.DeviceName("Midi Fighter Twister")
public class DJTTMidiFighterTwister extends LXMidiTemplate implements LXMidiTemplate.Bidirectional {

  public static final int NUM_KNOBS = 16;

  public static final int KNOB_CHANNEL = 0;
  public static final int SWITCH_CHANNEL = 1;

  public class Knob extends BoundedParameter {

    public final int index;

    private Knob(int index) {
      super("K" + (index+1));
      this.index = index;
      setUnits(BoundedParameter.Units.PERCENT_NORMALIZED);
      setDescription("Knob " + (index+1));
      addParameter("knob-" + (index+1), this);
    }
  }

  public class Switch extends BooleanParameter {
    public final int index;

    private Switch(int index) {
      super("S" + (index+1));
      this.index = index;
      setMode(BooleanParameter.Mode.MOMENTARY);
      setDescription("Switch " + (index+1));
      addParameter("Switch-" + (index+1), this);
    }
  }

  public final Knob[] knobs = new Knob[NUM_KNOBS];
  public final Switch[] switches = new Switch[NUM_KNOBS];

  public DJTTMidiFighterTwister(LX lx) {
    super(lx);
    for (int i = 0; i < NUM_KNOBS; ++i) {
      this.knobs[i] = new Knob(i);
      this.switches[i] = new Switch(i);
    }
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p instanceof Knob) {
      Knob knob = (Knob) p;
      sendControlChange(KNOB_CHANNEL, knob.index, (int) Math.round(knob.getNormalized() * 127.));
    } else if (p instanceof Switch) {
      Switch s = (Switch) p;
      sendControlChange(SWITCH_CHANNEL, s.index, s.isOn() ? 127 : 0);
    }
  }

  @Override
  protected void initializeOutput() {
    for (Knob knob : this.knobs) {
      sendControlChange(KNOB_CHANNEL, knob.index, (int) Math.round(knob.getNormalized() * 127.));
    }
    for (Switch s : this.switches) {
      sendControlChange(SWITCH_CHANNEL, s.index, s.isOn() ? 127 : 0);
    }
  }

  @Override
  public void controlChangeReceived(MidiControlChange cc) {
    int knobIndex = cc.getCC();
    if (knobIndex < NUM_KNOBS) {
      switch (cc.getChannel()) {
      case KNOB_CHANNEL:
        this.knobs[knobIndex].setNormalized(cc.getNormalized());
        break;
      case SWITCH_CHANNEL:
        this.switches[knobIndex].setValue(cc.getValue() > 0);
        break;
      }
    }
  }

}
