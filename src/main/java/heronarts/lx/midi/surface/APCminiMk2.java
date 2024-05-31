/**
 * Copyright 2024- Justin Belcher, Mark C. Slee, Heron Arts LLC
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
 * @author Justin K. Belcher <justin@jkb.studio>
 */

package heronarts.lx.midi.surface;

import heronarts.lx.LX;
import heronarts.lx.midi.LXMidiInput;
import heronarts.lx.midi.LXMidiOutput;

public class APCminiMk2 extends APCminiSurface {

  public static final String DEVICE_NAME = "APC mini mk2 Control";

  // Notes in combination with Shift
  public static final int SHIFT = 122;

  public static final int CLIP_STOP = 112;
  public static final int SOLO = 113;
  public static final int MUTE = 114;
  public static final int REC_ARM = 115;
  public static final int SELECT = 116;
  public static final int DRUM_MODE = 117;
  public static final int NOTE_MODE = 118;
  public static final int STOP_ALL_CLIPS = 119;

  public static final int FADER_CTRL_VOLUME = 100;
  public static final int FADER_CTRL_PAN = 101;
  public static final int FADER_CTRL_SEND = 102;
  public static final int FADER_CTRL_DEVICE = 103;

  public static final int SELECT_UP = 104;
  public static final int SELECT_DOWN = 105;
  public static final int SELECT_LEFT = 106;
  public static final int SELECT_RIGHT = 107;

  // Multi color buttons
  // TODO: There are 127 possible colors in APCminiMk2Colors...
  public static final int LED_COLOR_OFF = 0;
  public static final int LED_GRAY_50 = 1;
  public static final int LED_GRAY_75 = 2;
  public static final int LED_WHITE = 3;
  public static final int LED_RED = 5;
  public static final int LED_YELLOW = 12;
  public static final int LED_GREEN = 21;
  public static final int LED_BLUE = 67;

  // Brightness and Behavior are set by MIDI Channel
  // Multi color (grid buttons)
  public static final int MIDI_CHANNEL_MULTI_10_PERCENT = 0;
  public static final int MIDI_CHANNEL_MULTI_25_PERCENT = 1;
  public static final int MIDI_CHANNEL_MULTI_50_PERCENT = 2;
  public static final int MIDI_CHANNEL_MULTI_65_PERCENT = 3;
  public static final int MIDI_CHANNEL_MULTI_75_PERCENT = 4;
  public static final int MIDI_CHANNEL_MULTI_90_PERCENT = 5;
  public static final int MIDI_CHANNEL_MULTI_100_PERCENT = 6;
  public static final int MIDI_CHANNEL_MULTI_PULSE_SIXTEENTH = 7;
  public static final int MIDI_CHANNEL_MULTI_PULSE_EIGTH = 8;
  public static final int MIDI_CHANNEL_MULTI_PULSE_QUARTER = 9;
  public static final int MIDI_CHANNEL_MULTI_PULSE_HALF = 10;
  public static final int MIDI_CHANNEL_MULTI_BLINK_TWENTYFOURTH = 11;
  public static final int MIDI_CHANNEL_MULTI_BLINK_SIXTEENTH = 12;
  public static final int MIDI_CHANNEL_MULTI_BLINK_EIGTH = 13;
  public static final int MIDI_CHANNEL_MULTI_BLINK_QUARTER = 14;
  public static final int MIDI_CHANNEL_MULTI_BLINK_HALF = 15;

  // Configurable color options
  public static final int LED_PATTERN_ACTIVE_BEHAVIOR = MIDI_CHANNEL_MULTI_100_PERCENT;
  public static final int LED_PATTERN_ACTIVE_COLOR = LED_RED;
  public static final int LED_PATTERN_ENABLED_BEHAVIOR = MIDI_CHANNEL_MULTI_100_PERCENT;
  public static final int LED_PATTERN_ENABLED_COLOR = LED_GREEN;
  public static final int LED_PATTERN_DISABLED_BEHAVIOR = MIDI_CHANNEL_MULTI_50_PERCENT;
  public static final int LED_PATTERN_DISABLED_COLOR = LED_WHITE;
  public static final int LED_PATTERN_TRANSITION_BEHAVIOR = MIDI_CHANNEL_MULTI_PULSE_SIXTEENTH;
  public static final int LED_PATTERN_TRANSITION_COLOR = LED_RED;
  public static final int LED_PATTERN_FOCUSED_BEHAVIOR = MIDI_CHANNEL_MULTI_100_PERCENT;
  public static final int LED_PATTERN_FOCUSED_COLOR = LED_YELLOW;
  public static final int LED_PATTERN_INACTIVE_BEHAVIOR = MIDI_CHANNEL_MULTI_50_PERCENT;
  public static final int LED_PATTERN_INACTIVE_COLOR = LED_WHITE;

  public static final int LED_CLIP_INACTIVE_BEHAVIOR = MIDI_CHANNEL_MULTI_100_PERCENT;
  public static final int LED_CLIP_INACTIVE_COLOR = LED_GRAY_50;
  public static final int LED_CLIP_PLAY_BEHAVIOR = MIDI_CHANNEL_MULTI_100_PERCENT;
  public static final int LED_CLIP_PLAY_COLOR = LED_GREEN;
  public static final int LED_CLIP_ARM_BEHAVIOR = MIDI_CHANNEL_MULTI_100_PERCENT;
  public static final int LED_CLIP_ARM_COLOR = LED_RED;
  public static final int LED_CLIP_RECORD_BEHAVIOR = MIDI_CHANNEL_MULTI_BLINK_EIGTH;
  public static final int LED_CLIP_RECORD_COLOR = LED_RED;

  public static final int LED_PARAMETER_INCREMENT_BEHAVIOR = MIDI_CHANNEL_MULTI_100_PERCENT;
  public static final int LED_PARAMETER_INCREMENT_COLOR = LED_GREEN;
  public static final int LED_PARAMETER_DECREMENT_BEHAVIOR = MIDI_CHANNEL_MULTI_100_PERCENT;
  public static final int LED_PARAMETER_DECREMENT_COLOR = LED_YELLOW;
  public static final int LED_PARAMETER_RESET_BEHAVIOR = MIDI_CHANNEL_MULTI_100_PERCENT;
  public static final int LED_PARAMETER_RESET_COLOR = LED_RED;
  public static final int LED_PARAMETER_ISDEFAULT_BEHAVIOR = MIDI_CHANNEL_MULTI_100_PERCENT;
  public static final int LED_PARAMETER_ISDEFAULT_COLOR = LED_OFF;

  public APCminiMk2(LX lx, LXMidiInput input, LXMidiOutput output) {
    super(lx, input, output);
  }

  @Override
  public String getName() {
    return "APCmini mk2";
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
        return DRUM_MODE;
      }

      @Override
      public int getNoteMode() {
        return NOTE_MODE;
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
        return FADER_CTRL_VOLUME;
      }
    };
  }


  @Override
  protected LedDefinitions getLedDefinitions() {
    return new LedDefinitions() {

      @Override
      public int getDefaultMultiBehavior() {
        return MIDI_CHANNEL_MULTI_100_PERCENT;
      }

      @Override
      public int getParameterIncrementBehavior() {
        return LED_PARAMETER_INCREMENT_BEHAVIOR;
      }

      @Override
      public int getParameterIncrementColor() {
        return LED_PARAMETER_INCREMENT_COLOR;
      }

      @Override
      public int getParameterDecrementBehavior() {
        return LED_PARAMETER_DECREMENT_BEHAVIOR;
      }

      @Override
      public int getParameterDecrementColor() {
        return LED_PARAMETER_DECREMENT_COLOR;
      }

      @Override
      public int getParameterIsDefaultBehavior() {
        return LED_PARAMETER_ISDEFAULT_BEHAVIOR;
      }

      @Override
      public int getParameterIsDefaultColor() {
        return LED_PARAMETER_ISDEFAULT_COLOR;
      }

      @Override
      public int getParameterResetBehavior() {
        return LED_PARAMETER_RESET_BEHAVIOR;
      }

      @Override
      public int getParameterResetColor() {
        return LED_PARAMETER_RESET_COLOR;
      }

      @Override
      public int getPatternActiveBehavior() {
        return LED_PATTERN_ACTIVE_BEHAVIOR;
      }

      @Override
      public int getPatternActiveColor() {
        return LED_PATTERN_ACTIVE_COLOR;
      }

      @Override
      public int getPatternEnabledBehavior() {
        return LED_PATTERN_ENABLED_BEHAVIOR;
      }

      @Override
      public int getPatternEnabledColor() {
        return LED_PATTERN_ENABLED_COLOR;
      }

      @Override
      public int getPatternDisabledBehavior() {
        return LED_PATTERN_DISABLED_BEHAVIOR;
      }

      @Override
      public int getPatternDisabledColor() {
        return LED_PATTERN_DISABLED_COLOR;
      }

      @Override
      public int getPatternDisabledFocusedBehavior() {
        return LED_PATTERN_FOCUSED_BEHAVIOR;
      }

      @Override
      public int getPatternDisabledFocusedColor() {
        return LED_PATTERN_FOCUSED_COLOR;
      }

      @Override
      public int getPatternFocusedBehavior() {
        return LED_PATTERN_FOCUSED_BEHAVIOR;
      }

      @Override
      public int getPatternFocusedColor() {
        return LED_PATTERN_FOCUSED_COLOR;
      }

      @Override
      public int getPatternInactiveBehavior() {
        return LED_PATTERN_INACTIVE_BEHAVIOR;
      }

      @Override
      public int getPatternInactiveColor() {
        return LED_PATTERN_INACTIVE_COLOR;
      }

      @Override
      public int getPatternTransitionBehavior() {
        return LED_PATTERN_TRANSITION_BEHAVIOR;
      }

      @Override
      public int getPatternTransitionColor() {
        return LED_PATTERN_TRANSITION_COLOR;
      }

      @Override
      public int getClipRecordBehavior() {
        return LED_CLIP_RECORD_BEHAVIOR;
      }

      @Override
      public int getClipRecordColor() {
        return LED_CLIP_RECORD_COLOR;
      }

      @Override
      public int getClipArmBehavior() {
        return LED_CLIP_ARM_BEHAVIOR;
      }

      @Override
      public int getClipArmColor() {
        return LED_CLIP_ARM_COLOR;
      }

      @Override
      public int getClipInactiveBehavior() {
        return LED_CLIP_INACTIVE_BEHAVIOR;
      }

      @Override
      public int getClipInactiveColor() {
        return LED_CLIP_INACTIVE_COLOR;
      }

      @Override
      public int getClipPlayBehavior() {
        return LED_CLIP_PLAY_BEHAVIOR;
      }

      @Override
      public int getClipPlayColor() {
        return LED_CLIP_PLAY_COLOR;
      }

    };
  }

}
