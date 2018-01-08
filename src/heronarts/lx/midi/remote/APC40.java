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

package heronarts.lx.midi.remote;

import heronarts.lx.LXBus;
import heronarts.lx.LXChannel;
import heronarts.lx.LXEngine;
import heronarts.lx.LXPattern;
import heronarts.lx.midi.LXMidiInput;
import heronarts.lx.midi.LXMidiOutput;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;

/**
 * Note and CC constants for all the APC40 controls
 */
public class APC40 extends LXMidiRemote {

  // CC numbers

  public final static int VOLUME = 7;
  public final static int MASTER_FADER = 14;
  public final static int CUE_LEVEL = 47;
  public final static int DEVICE_CONTROL = 16;
  public final static int DEVICE_CONTROL_LED_MODE = 24;
  public final static int TRACK_CONTROL = 48;
  public final static int TRACK_CONTROL_LED_MODE = 56;
  public final static int CROSSFADER = 15;

  // Note numbers

  public final static int CLIP_LAUNCH = 53;
  public final static int SCENE_LAUNCH = 82;

  public final static int CLIP_STOP = 52;
  public final static int STOP_ALL_CLIPS = 81;

  public final static int TRACK_SELECTION = 51;
  public final static int MASTER_TRACK = 80;

  public final static int ACTIVATOR = 50;
  public final static int SOLO_CUE = 49;
  public final static int RECORD_ARM = 48;

  public final static int PAN = 87;
  public final static int SEND_A = 88;
  public final static int SEND_B = 89;
  public final static int SEND_C = 90;

  public final static int SHIFT = 98;

  public final static int BANK_UP = 94;
  public final static int BANK_DOWN = 95;
  public final static int BANK_RIGHT = 96;
  public final static int BANK_LEFT = 97;

  public final static int TAP_TEMPO = 99;
  public final static int NUDGE_PLUS = 100;
  public final static int NUDGE_MINUS = 101;

  public final static int CLIP_TRACK = 58;
  public final static int DEVICE_ON_OFF = 59;
  public final static int LEFT_ARROW = 60;
  public final static int RIGHT_ARROW = 61;

  public final static int DETAIL_VIEW = 62;
  public final static int REC_QUANTIZATION = 63;
  public final static int MIDI_OVERDUB = 64;
  public final static int METRONOME = 65;

  public final static int PLAY = 91;
  public final static int STOP = 92;
  public final static int REC = 93;

  // LED color values

  public static final int OFF = 0;
  public static final int GREEN = 1;
  public static final int GREEN_BLINK = 2;
  public static final int RED = 3;
  public static final int RED_BLINK = 4;
  public static final int YELLOW = 5;
  public static final int YELLOW_BLINK = 6;

  // Encoder ring modes

  public final static int LED_MODE_OFF = 0;
  public final static int LED_MODE_SINGLE = 1;
  public final static int LED_MODE_VOLUME = 2;
  public final static int LED_MODE_PAN = 3;

  // APC Modes

  public final static byte GENERIC = 0x40;
  public final static byte MODE_ABLETON = 0x41;
  public final static byte MODE_ALTERNATE_ABLETON = 0x42;

  public final static int NUM_TRACK_CONTROL_KNOBS = 8;
  public final static int NUM_DEVICE_CONTROL_KNOBS = 8;

  public final static String DEVICE_NAME = "APC40";

  public APC40(LXMidiInput input) {
    this(input, null);
  }

  public APC40(LXMidiInput input, LXMidiOutput output) {
    super(input, output);
  }

  public APC40 setMode(byte mode) {
    byte[] apcModeSysex = new byte[] { (byte) 0xf0, // sysex start
        (byte) 0x47, // manufacturers id
        (byte) 0x00, // device id
        (byte) 0x73, // product model id
        (byte) 0x60, // message
        (byte) 0x00, // bytes MSB
        (byte) 0x04, // bytes LSB
        mode,
        (byte) 0x08, // version maj
        (byte) 0x01, // version min
        (byte) 0x01, // version bugfix
        (byte) 0xf7, // sysex end
    };
    sendSysex(apcModeSysex);
    return this;
  }

  private LXChannel deviceControlChannel = null;

  private final LXChannel.AbstractListener deviceControlListener = new LXChannel.AbstractListener() {
    @Override
    public void patternDidChange(LXChannel channel, LXPattern pattern) {
      bindDeviceControlKnobs(pattern);
    }
  };

  public APC40 bindDeviceControlKnobs(final LXEngine engine) {
    engine.focusedChannel.addListener(new LXParameterListener() {
      public void onParameterChanged(LXParameter parameter) {
        LXBus bus = engine.getFocusedChannel();
        if (bus instanceof LXChannel) {
          bindDeviceControlKnobs((LXChannel) bus);
        }
      }
    });
    LXBus bus = engine.getFocusedChannel();
    if (bus instanceof LXChannel) {
      bindDeviceControlKnobs((LXChannel) bus);
    }
    return this;
  }

  public APC40 bindDeviceControlKnobs(LXChannel channel) {
    if (this.deviceControlChannel != channel) {
      if (this.deviceControlChannel != null) {
        this.deviceControlChannel.removeListener(this.deviceControlListener);
      }
      this.deviceControlChannel = channel;
      this.deviceControlChannel.addListener(this.deviceControlListener);
    }
    bindDeviceControlKnobs(channel.getActivePattern());
    return this;
  }

  public APC40 bindDeviceControlKnobs(LXPattern pattern) {
    int parameterIndex = 0;
    for (LXParameter parameter : pattern.getParameters()) {
      if (parameter instanceof LXListenableNormalizedParameter) {
        bindController(parameter, 0, DEVICE_CONTROL + parameterIndex);
        if (++parameterIndex >= NUM_DEVICE_CONTROL_KNOBS) {
          break;
        }
      }
    }
    while (parameterIndex < NUM_DEVICE_CONTROL_KNOBS) {
      unbindController(0, DEVICE_CONTROL + parameterIndex);
      sendController(0, DEVICE_CONTROL + parameterIndex, 0);
      ++parameterIndex;
    }
    return this;
  }

}
