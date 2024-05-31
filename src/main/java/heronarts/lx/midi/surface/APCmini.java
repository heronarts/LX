/**
 * Copyright 2022- Justin Belcher, Mark C. Slee, Heron Arts LLC
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

import heronarts.lx.LX;
import heronarts.lx.midi.LXMidiInput;
import heronarts.lx.midi.LXMidiOutput;

public class APCmini extends APCminiSurface {

  public static final String DEVICE_NAME = "APC MINI";

  public static final int SHIFT = 98;

  public static final int SELECT_UP = 64;
  public static final int SELECT_DOWN = 65;
  public static final int SELECT_LEFT = 66;
  public static final int SELECT_RIGHT = 67;

  public static final int FADER_CTRL_VOLUME = 68;
  public static final int FADER_CTRL_PAN = 69;
  public static final int FADER_CTRL_SEND = 70;
  public static final int FADER_CTRL_DEVICE = 71;

  public static final int CLIP_STOP = 82;
  public static final int SOLO = 83;
  public static final int REC_ARM = 84;
  public static final int MUTE = 85;
  public static final int SELECT = 86;
  public static final int EXTRA1 = 87;
  public static final int EXTRA2 = 88;
  public static final int STOP_ALL_CLIPS = 89;

  // LED color definitions

  // Multi color buttons
  public static final int LED_GREEN = 1;
  public static final int LED_GREEN_BLINK = 2;
  public static final int LED_RED = 3;
  public static final int LED_RED_BLINK = 4;
  public static final int LED_YELLOW = 5;
  public static final int LED_YELLOW_BLINK = 6;

  // Configurable color options
  public static final int LED_PATTERN_ACTIVE = LED_RED;
  public static final int LED_PATTERN_INACTIVE = LED_YELLOW;
  public static final int LED_PATTERN_FOCUSED = LED_YELLOW_BLINK;
  public static final int LED_PATTERN_ENABLED = LED_GREEN;
  public static final int LED_PATTERN_DISABLED = LED_RED;
  public static final int LED_PATTERN_DISABLED_FOCUSED = LED_RED_BLINK;
  public static final int LED_PATTERN_TRANSITION = LED_RED_BLINK;

  public static final int LED_CLIP_INACTIVE = LED_YELLOW;
  public static final int LED_CLIP_PLAY = LED_GREEN;
  public static final int LED_CLIP_ARM = LED_RED;
  public static final int LED_CLIP_RECORD = LED_RED_BLINK;

  public static final int LED_PARAMETER_INCREMENT = LED_GREEN;
  public static final int LED_PARAMETER_DECREMENT = LED_YELLOW;
  public static final int LED_PARAMETER_RESET = LED_RED;
  public static final int LED_PARAMETER_ISDEFAULT = LED_OFF;

  public APCmini(LX lx, LXMidiInput input, LXMidiOutput output) {
    super(lx, input, output);
  }

  @Override
  public String getName() {
    return "APCmini";
  }

  @Override
  protected NoteDefinitions getNoteDefinitions() {
    return new NoteDefinitions() {

      @Override
      public int getShift() {
        return SHIFT;
      }

      @Override
      public int getClipStop() {
        return CLIP_STOP;
      }

      @Override
      public int getSolo() {
        return SOLO;
      }

      @Override
      public int getMute() {
        return MUTE;
      }

      @Override
      public int getRecArm() {
        return REC_ARM;
      }

      @Override
      public int getSelect() {
        return SELECT;
      }

      @Override
      public int getDrumMode() {
        return EXTRA1;
      }

      @Override
      public int getNoteMode() {
        return EXTRA2;
      }

      @Override
      public int getStopAllClips() {
        return STOP_ALL_CLIPS;
      }

      @Override
      public int getFaderCtrlVolume() {
        return FADER_CTRL_VOLUME;
      }

      @Override
      public int getFaderCtrlPan() {
        return FADER_CTRL_PAN;
      }

      @Override
      public int getFaderCtrlSend() {
        return FADER_CTRL_SEND;
      }

      @Override
      public int getFaderCtrlDevice() {
        return FADER_CTRL_DEVICE;
      }

      @Override
      public int getSelectUp() {
        return SELECT_UP;
      }

      @Override
      public int getSelectDown() {
        return SELECT_DOWN;
      }

      @Override
      public int getSelectLeft() {
        return SELECT_LEFT;
      }

      @Override
      public int getSelectRight() {
        return SELECT_RIGHT;
      }

      @Override
      public int getChannelButton() {
        return SELECT_UP;
      }
    };
  }

  @Override
  protected LedDefinitions getLedDefinitions() {
    return new LedDefinitions() {

      @Override
      public int getDefaultMultiBehavior() {
        return MIDI_CHANNEL_SINGLE;
      }

      @Override
      public int getParameterIncrementBehavior() {
        return MIDI_CHANNEL_SINGLE;
      }

      @Override
      public int getParameterIncrementColor() {
        return LED_PARAMETER_INCREMENT;
      }

      @Override
      public int getParameterDecrementBehavior() {
        return MIDI_CHANNEL_SINGLE;
      }

      @Override
      public int getParameterDecrementColor() {
        return LED_PARAMETER_DECREMENT;
      }

      @Override
      public int getParameterIsDefaultBehavior() {
        return MIDI_CHANNEL_SINGLE;
      }

      @Override
      public int getParameterIsDefaultColor() {
        return LED_PARAMETER_ISDEFAULT;
      }

      @Override
      public int getParameterResetBehavior() {
        return MIDI_CHANNEL_SINGLE;
      }

      @Override
      public int getParameterResetColor() {
        return LED_PARAMETER_RESET;
      }

      @Override
      public int getPatternActiveBehavior() {
        return MIDI_CHANNEL_SINGLE;
      }

      @Override
      public int getPatternActiveColor() {
        return LED_PATTERN_ACTIVE;
      }

      @Override
      public int getPatternEnabledBehavior() {
        return MIDI_CHANNEL_SINGLE;
      }

      @Override
      public int getPatternEnabledColor() {
        return LED_PATTERN_ENABLED;
      }

      @Override
      public int getPatternDisabledBehavior() {
        return MIDI_CHANNEL_SINGLE;
      }

      @Override
      public int getPatternDisabledColor() {
        return LED_PATTERN_DISABLED;
      }

      @Override
      public int getPatternDisabledFocusedBehavior() {
        return MIDI_CHANNEL_SINGLE;
      }

      @Override
      public int getPatternDisabledFocusedColor() {
        return LED_PATTERN_DISABLED_FOCUSED;
      }

      @Override
      public int getPatternFocusedBehavior() {
        return MIDI_CHANNEL_SINGLE;
      }

      @Override
      public int getPatternFocusedColor() {
        return LED_PATTERN_FOCUSED;
      }

      @Override
      public int getPatternInactiveBehavior() {
        return MIDI_CHANNEL_SINGLE;
      }

      @Override
      public int getPatternInactiveColor() {
        return LED_PATTERN_INACTIVE;
      }

      @Override
      public int getPatternTransitionBehavior() {
        return MIDI_CHANNEL_SINGLE;
      }

      @Override
      public int getPatternTransitionColor() {
        return LED_PATTERN_TRANSITION;
      }

      @Override
      public int getClipRecordBehavior() {
        return MIDI_CHANNEL_SINGLE;
      }

      @Override
      public int getClipRecordColor() {
        return LED_CLIP_RECORD;
      }

      @Override
      public int getClipArmBehavior() {
        return MIDI_CHANNEL_SINGLE;
      }

      @Override
      public int getClipArmColor() {
        return LED_CLIP_ARM;
      }

      @Override
      public int getClipInactiveBehavior() {
        return MIDI_CHANNEL_SINGLE;
      }

      @Override
      public int getClipInactiveColor() {
        return LED_CLIP_INACTIVE;
      }

      @Override
      public int getClipPlayBehavior() {
        return MIDI_CHANNEL_SINGLE;
      }

      @Override
      public int getClipPlayColor() {
        return LED_CLIP_PLAY;
      }

    };
  }
}

