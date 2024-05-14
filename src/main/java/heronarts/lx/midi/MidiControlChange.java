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

package heronarts.lx.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;

public class MidiControlChange extends LXShortMessage {

  public final static int BANK_SELECT = 0x00;
  public final static int MOD_WHEEL = 0x01;
  public final static int BREATH_CONTROLLER = 0x02;
  public final static int FOOT_CONTROLLER = 0x04;
  public final static int PORTAMENTO_TIME = 0x05;
  public final static int DATA_ENTRY_MSB = 0x06;
  public final static int MAIN_VOLUME = 0x07;
  public final static int BALANCE = 0x08;
  public final static int PAN = 0x0a;
  public final static int DAMPER_PEDAL = 0x40;
  public final static int PORTAMENTO = 0x41;
  public final static int SOSTENUTO = 0x42;
  public final static int SOFT_PEDAL = 0x43;
  public final static int LEGATO_FOOTWSWITCH = 0x44;
  public final static int HOLD_2 = 0x45;
  public final static int PORTAMENTO_CONTROL = 0x54;
  public final static int DATA_INCREMENT = 0x60;
  public final static int NRPN_LSB = 0x62;
  public final static int NRPN_MSB = 0x63;
  public final static int RPN_LSB = 0x64;
  public final static int RPN_MSB = 0x65;

  public final static int RESET_ALL_CONTROLLERS = 0x79;
  public final static int LOCAL_CONTROL = 0x7a;
  public final static int ALL_NOTES_OFF = 0x7b;
  public final static int OMNI_OFF = 0x7c;
  public final static int OMNI_ON = 0x7d;
  public final static int POLY_OFF = 0x7e;
  public final static int POLY_ON = 0x7f;

  public final static int MAX_CC_VALUE = 127;

  public MidiControlChange(int channel, int cc, int value) throws InvalidMidiDataException {
    super(ShortMessage.CONTROL_CHANGE, channel, cc, value);
  }

  MidiControlChange(ShortMessage message) {
    super(message, ShortMessage.CONTROL_CHANGE);
  }

  public int getCC() {
    return getData1();
  }

  public int getValue() {
    return getData2();
  }

  public double getNormalized() {
    return getValue() / 127.;
  }

  public int getRelative() {
    int value = getValue();
    if (value >= 64) {
      return value - 128;
    }
    return value;
  }


  @Override
  public String toString() {
    return "MidiControlChange:" + getChannel() + ":CC:" + getCC() + ":Value: " + getValue();
  }
}
