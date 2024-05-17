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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.clip.LXClip;
import heronarts.lx.clip.LXClipEngine;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.midi.LXMidiEngine;
import heronarts.lx.midi.LXMidiInput;
import heronarts.lx.midi.LXMidiOutput;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXGroup;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.pattern.LXPattern;

public class APCminiMk2 extends LXMidiSurface implements LXMidiSurface.Bidirectional {

  public static final String DEVICE_NAME = "APC mini mk2 Control";

  public static final int NUM_CHANNELS = 8;
  public static final double PARAMETER_INCREMENT_AMOUNT = 0.1;

  // CCs
  public static final int CHANNEL_FADER = 48;
  public static final int CHANNEL_FADER_MAX = CHANNEL_FADER + NUM_CHANNELS - 1;
  public static final int MASTER_FADER = 56;

  // Notes
  public static final int CLIP_LAUNCH = 0;
  public static final int CLIP_LAUNCH_ROWS = 8;
  public static final int CLIP_LAUNCH_COLUMNS = NUM_CHANNELS;
  public static final int CLIP_LAUNCH_NUM = CLIP_LAUNCH_ROWS * CLIP_LAUNCH_COLUMNS;
  public static final int CLIP_LAUNCH_MAX = CLIP_LAUNCH + CLIP_LAUNCH_NUM - 1;

  public static final int FADER_CTRL_VOLUME = 100;
  public static final int FADER_CTRL_PAN = 101;
  public static final int FADER_CTRL_SEND = 102;
  public static final int FADER_CTRL_DEVICE = 103;

  public static final int SELECT_UP = 104;
  public static final int SELECT_DOWN = 105;
  public static final int SELECT_LEFT = 106;
  public static final int SELECT_RIGHT = 107;

  public static final int CHANNEL_BUTTON = FADER_CTRL_VOLUME;
  public static final int CHANNEL_BUTTON_MAX = CHANNEL_BUTTON + NUM_CHANNELS - 1;

  public static final int CLIP_STOP = 112;
  public static final int SOLO = 113;
  public static final int MUTE = 114;
  public static final int REC_ARM = 115;
  public static final int SELECT = 116;
  public static final int DRUM = 117;
  public static final int NOTE = 118;
  public static final int STOP_ALL_CLIPS = 119;

  public static final int SCENE_LAUNCH = CLIP_STOP;
  public static final int SCENE_LAUNCH_NUM = 8;
  public static final int SCENE_LAUNCH_MAX = SCENE_LAUNCH + SCENE_LAUNCH_NUM - 1;

  // The FADER CTRL buttons are used with shift to set the grid mode
  public static final int GRID_MODE_PATTERNS = FADER_CTRL_VOLUME;
  public static final int GRID_MODE_CLIPS = FADER_CTRL_PAN;
  public static final int GRID_MODE_PARAMETERS = FADER_CTRL_DEVICE;

  public static final int PARAMETER_COLUMNS = 8;
  public static final int PARAMETER_COLUMN_STRIDE = 1;
  public static final int PARAMETER_ROWS = 2;
  public static final int PARAMETER_ROW_STRIDE = -4;
  public static final int PARAMETER_NUM = PARAMETER_COLUMNS * PARAMETER_ROWS;
  public static final int PARAMETER_START = (CLIP_LAUNCH_ROWS - 1) * CLIP_LAUNCH_COLUMNS + CLIP_LAUNCH;

  // Notes in combination with Shift
  public static final int SHIFT = 122;

  // The SOFT KEYS buttons are used with shift to set the channel mode
  public static final int CHANNEL_BUTTON_MODE_FOCUS = SELECT;
  public static final int CHANNEL_BUTTON_MODE_ENABLED = MUTE;
  public static final int CHANNEL_BUTTON_MODE_CUE = SOLO;
  public static final int CHANNEL_BUTTON_MODE_ARM = REC_ARM;
  public static final int CHANNEL_BUTTON_MODE_CLIP_STOP = CLIP_STOP;

  // LEDs

  // Brightness and Behavior are set by MIDI Channel
  // Single color (perimeter buttons)
  public static final int MIDI_CHANNEL_SINGLE = 0;
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

  // Single AND multi color buttons
  public static final int LED_OFF = 0;

  // Single color buttons
  public static final int LED_ON = 1;
  public static final int LED_BLINK = 2;

  // Multi color buttons
  // TODO: There are 127 possible colors
  public static final int LED_COLOR_OFF = 0;
  public static final int LED_GRAY_50 = 1;
  public static final int LED_GRAY_75 = 2;
  public static final int LED_WHITE = 3;
  public static final int LED_RED = 5;
  public static final int LED_YELLOW = 12;
  public static final int LED_GREEN = 21;
  public static final int LED_BLUE = 67;

  // Configurable color options
  public static final int LED_PATTERN_ACTIVE_BEHAVIOR = MIDI_CHANNEL_MULTI_100_PERCENT;
  public static final int LED_PATTERN_ACTIVE_COLOR = LED_RED;
  public static final int LED_PATTERN_ENABLED_BEHAVIOR = MIDI_CHANNEL_MULTI_100_PERCENT;
  public static final int LED_PATTERN_ENABLED_COLOR = LED_GREEN;
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

  private static int LED_ON(boolean condition) {
    return condition ? LED_ON : LED_OFF;
  }

  public enum ChannelButtonMode {
    ARM,
    CLIP_STOP,
    CUE,
    ENABLED,
    FOCUS
  };

  private ChannelButtonMode channelButtonMode = ChannelButtonMode.FOCUS;

  public enum GridMode {
    PATTERNS(LXClipEngine.GridMode.PATTERNS),
    CLIPS(LXClipEngine.GridMode.CLIPS),
    PARAMETERS(null);

    public final LXClipEngine.GridMode engineGridMode;

    private GridMode(LXClipEngine.GridMode engineGridMode) {
      this.engineGridMode = engineGridMode;
    }

  };

  private GridMode gridMode = GridMode.PATTERNS;

  private boolean shiftOn = false;

  private final Map<LXAbstractChannel, ChannelListener> channelListeners = new HashMap<LXAbstractChannel, ChannelListener>();

  private final DeviceListener deviceListener = new DeviceListener();

  private class DeviceListener implements FocusedDevice.Listener, LXParameterListener {

    private final FocusedDevice focusedDevice;
    private LXDeviceComponent device = null;

    private final LXListenableNormalizedParameter[] knobs =
      new LXListenableNormalizedParameter[PARAMETER_NUM];

    private DeviceListener() {
      Arrays.fill(this.knobs, null);
      this.focusedDevice = new FocusedDevice(lx, APCminiMk2.this, this);
    }

    @Override
    public void onDeviceFocused(LXDeviceComponent device) {
      registerDevice(device);
    }

    private void registerDevice(LXDeviceComponent device) {
      if (this.device != device) {
        unregisterDevice(false);
        this.device = device;

        int i = 0;
        if (this.device != null) {
          for (LXListenableNormalizedParameter parameter : getDeviceRemoteControls()) {
            if (i >= this.knobs.length) {
              break;
            }
            this.knobs[i] = parameter;
            int patternButton = getParameterButton(i);
            if (parameter != null) {
              parameter.addListener(this);
              if (isGridModeParameters()) {
                sendNoteOn(LED_PARAMETER_INCREMENT_BEHAVIOR, patternButton, LED_PARAMETER_INCREMENT_COLOR);
                sendNoteOn(LED_PARAMETER_DECREMENT_BEHAVIOR, patternButton - CLIP_LAUNCH_COLUMNS, LED_PARAMETER_DECREMENT_COLOR);
                if (parameter.isDefault()) {
                  sendNoteOn(LED_PARAMETER_ISDEFAULT_BEHAVIOR, patternButton - (CLIP_LAUNCH_COLUMNS * 2), LED_PARAMETER_ISDEFAULT_COLOR);
                } else {
                  sendNoteOn(LED_PARAMETER_RESET_BEHAVIOR, patternButton - (CLIP_LAUNCH_COLUMNS * 2), LED_PARAMETER_RESET_COLOR);
                }
                sendNoteOn(MIDI_CHANNEL_MULTI_100_PERCENT, patternButton - (CLIP_LAUNCH_COLUMNS * 3), LED_OFF);
              }
            } else {
              // JKB: Added IF clause.  Why wasn't it here?
              if (isGridModeParameters()) {
                sendNoteOn(MIDI_CHANNEL_MULTI_100_PERCENT, patternButton, LED_OFF);
                sendNoteOn(MIDI_CHANNEL_MULTI_100_PERCENT, patternButton - CLIP_LAUNCH_COLUMNS, LED_OFF);
                sendNoteOn(MIDI_CHANNEL_MULTI_100_PERCENT, patternButton - (CLIP_LAUNCH_COLUMNS * 2), LED_OFF);
                sendNoteOn(MIDI_CHANNEL_MULTI_100_PERCENT, patternButton - (CLIP_LAUNCH_COLUMNS * 3), LED_OFF);
              }
            }
            ++i;
          }
          this.device.controlSurfaceSemaphore.increment();
        }
        if (isGridModeParameters()) {
          while (i < this.knobs.length) {
            int patternButton = getParameterButton(i);
            sendNoteOn(MIDI_CHANNEL_MULTI_100_PERCENT, patternButton, LED_OFF);
            sendNoteOn(MIDI_CHANNEL_MULTI_100_PERCENT, patternButton - CLIP_LAUNCH_COLUMNS, LED_OFF);
            sendNoteOn(MIDI_CHANNEL_MULTI_100_PERCENT, patternButton - (CLIP_LAUNCH_COLUMNS * 2), LED_OFF);
            sendNoteOn(MIDI_CHANNEL_MULTI_100_PERCENT, patternButton - (CLIP_LAUNCH_COLUMNS * 3), LED_OFF);
            ++i;
          }
        }
      }
    }

    private LXListenableNormalizedParameter[] getDeviceRemoteControls() {
      return this.device.getRemoteControls();
    }

    @Override
    public void onParameterChanged(LXParameter parameter) {
      if (isGridModeParameters()) {
        for (int i = 0; i < this.knobs.length; ++i) {
          if (parameter == this.knobs[i]) {
            int patternButton = getParameterButton(i);
            if (((LXListenableNormalizedParameter)parameter).isDefault()) {
              sendNoteOn(LED_PARAMETER_ISDEFAULT_BEHAVIOR, patternButton - (CLIP_LAUNCH_COLUMNS * 2), LED_PARAMETER_ISDEFAULT_COLOR);
            } else {
              sendNoteOn(LED_PARAMETER_RESET_BEHAVIOR, patternButton - (CLIP_LAUNCH_COLUMNS * 2), LED_PARAMETER_RESET_COLOR);
            }
            break;
          }
        }
      }
    }

    private void resend() {
      if (isGridModeParameters()) {
        for (int i = 0; i < this.knobs.length; ++i) {
          LXListenableNormalizedParameter parameter = this.knobs[i];
          int patternButton = getParameterButton(i);
          if (parameter != null) {
            sendNoteOn(LED_PARAMETER_INCREMENT_BEHAVIOR, patternButton, LED_PARAMETER_INCREMENT_COLOR);
            sendNoteOn(LED_PARAMETER_DECREMENT_BEHAVIOR, patternButton - CLIP_LAUNCH_COLUMNS, LED_PARAMETER_DECREMENT_COLOR);
            if (parameter.isDefault()) {
              sendNoteOn(LED_PARAMETER_ISDEFAULT_BEHAVIOR, patternButton - (CLIP_LAUNCH_COLUMNS * 2), LED_PARAMETER_ISDEFAULT_COLOR);
            } else {
              sendNoteOn(LED_PARAMETER_RESET_BEHAVIOR, patternButton - (CLIP_LAUNCH_COLUMNS * 2), LED_PARAMETER_RESET_COLOR);
            }
          } else {
            sendNoteOn(MIDI_CHANNEL_MULTI_100_PERCENT, patternButton, LED_OFF);
            sendNoteOn(MIDI_CHANNEL_MULTI_100_PERCENT, patternButton - CLIP_LAUNCH_COLUMNS, LED_OFF);
            sendNoteOn(MIDI_CHANNEL_MULTI_100_PERCENT, patternButton - (CLIP_LAUNCH_COLUMNS * 2), LED_OFF);
          }
          sendNoteOn(MIDI_CHANNEL_MULTI_100_PERCENT, patternButton - (CLIP_LAUNCH_COLUMNS * 3), LED_OFF);
        }
      }
    }

    private int getParameterButton(int index) {
      int row = index / PARAMETER_COLUMNS;
      int column = index % PARAMETER_COLUMNS;
      return PARAMETER_START + (row * CLIP_LAUNCH_COLUMNS * PARAMETER_ROW_STRIDE) + (column * PARAMETER_COLUMN_STRIDE);
    }

    private void onParameterButton(int columnIndex, int rowIndex) {
      int paramIndex = 0;
      int button = rowIndex;
      while (button > 3) {
        paramIndex += PARAMETER_COLUMNS;
        button -= 4;
      }
      paramIndex += columnIndex;

      LXListenableNormalizedParameter param = this.knobs[paramIndex];
      if (param != null) {
        switch (button) {
          case 0:
            if (param instanceof BooleanParameter) {
              ((BooleanParameter)param).setValue(true);
            } else if (param instanceof DiscreteParameter) {
              ((DiscreteParameter)param).increment();
            } else {
              param.setNormalized(param.getNormalized() + PARAMETER_INCREMENT_AMOUNT);
            }
            break;
          case 1:
            if (param instanceof BooleanParameter) {
              ((BooleanParameter)param).setValue(false);
            } else if (param instanceof DiscreteParameter) {
              ((DiscreteParameter)param).decrement();
            } else {
              param.setNormalized(param.getNormalized() - PARAMETER_INCREMENT_AMOUNT);
            }
            break;
          case 2:
            param.reset();
            break;
        }
      }
    }

    private void unregisterDevice(boolean clearParams) {
      if (this.device != null) {
        for (int i = 0; i < this.knobs.length; ++i) {
          if (this.knobs[i] != null) {
            this.knobs[i].removeListener(this);
            this.knobs[i] = null;
            if (isGridModeParameters() && clearParams) {
              final int patternButton = getParameterButton(i);
              sendNoteOn(MIDI_CHANNEL_SINGLE, patternButton, LED_OFF);
              sendNoteOn(MIDI_CHANNEL_SINGLE, patternButton - CLIP_LAUNCH_COLUMNS, LED_OFF);
              sendNoteOn(MIDI_CHANNEL_SINGLE, patternButton - (CLIP_LAUNCH_COLUMNS * 2), LED_OFF);
              sendNoteOn(MIDI_CHANNEL_SINGLE, patternButton - (CLIP_LAUNCH_COLUMNS * 3), LED_OFF);
            }
          }
        }
        this.device.controlSurfaceSemaphore.decrement();
      }
      this.device = null;
    }

    private void dispose() {
      unregisterDevice(true);
    }

  }

  protected final MixerSurface mixerSurface;

  private final MixerSurface.Listener mixerSurfaceListener = new MixerSurface.Listener() {
    @Override
    public void onChannelChanged(int index, LXAbstractChannel channel, LXAbstractChannel previousChannel) {
      if (previousChannel != null && !mixerSurface.contains(previousChannel)) {
        unregisterChannel(previousChannel);
      }
      if (channel != null) {
        registerChannel(channel);
        channelFaders[index].setTarget(channel.fader);
      } else {
        channelFaders[index].setTarget(null);
      }

      if (isGridModePatterns()) {
        sendChannelPatterns(index, channel);
      } else if (isGridModeClips()) {
        sendChannelClips(index, channel);
      }

      sendChannelButton(index, channel);
    }

    @Override
    public void onGridOffsetChanged() {
      sendGrid();
    }

  };

  private class ChannelListener implements LXChannel.Listener, LXBus.ClipListener, LXParameterListener {

    private final LXAbstractChannel channel;

    ChannelListener(LXAbstractChannel channel) {
      this.channel = channel;
      if (channel instanceof LXChannel) {
        ((LXChannel) channel).addListener(this);
      } else {
        channel.addListener(this);
      }
      channel.addClipListener(this);
      channel.cueActive.addListener(this);
      channel.enabled.addListener(this);
      channel.crossfadeGroup.addListener(this);
      channel.arm.addListener(this);
      if (channel instanceof LXChannel) {
        LXChannel c = (LXChannel) channel;
        c.focusedPattern.addListener(this);
        c.controlSurfaceFocusLength.setValue(CLIP_LAUNCH_ROWS);
        int focusedPatternIndex = c.getFocusedPatternIndex();
        c.controlSurfaceFocusIndex.setValue(focusedPatternIndex < CLIP_LAUNCH_ROWS ? 0 : (focusedPatternIndex - CLIP_LAUNCH_ROWS + 1));
      }
      for (LXClip clip : this.channel.clips) {
        if (clip != null) {
          clip.running.addListener(this);
        }
      }
    }

    public void onParameterChanged(LXParameter p) {
      final int index = mixerSurface.getIndex(this.channel);

      if (p == this.channel.cueActive) {
        if (channelButtonMode == ChannelButtonMode.CUE) {
          sendNoteOn(MIDI_CHANNEL_SINGLE, CHANNEL_BUTTON + index, LED_ON(this.channel.cueActive.isOn()));
        }
      } else if (p == this.channel.enabled) {
        if (channelButtonMode == ChannelButtonMode.ENABLED) {
          sendNoteOn(MIDI_CHANNEL_SINGLE, CHANNEL_BUTTON + index, LED_ON(this.channel.enabled.isOn()));
        }
      } else if (p == this.channel.crossfadeGroup) {
        // Button press toggles through the 3 modes. Button does not stay lit.
        sendNoteOn(MIDI_CHANNEL_SINGLE, CHANNEL_BUTTON + index, LED_OFF);
      } else if (p == this.channel.arm) {
        sendNoteOn(MIDI_CHANNEL_SINGLE, CHANNEL_BUTTON + index, LED_ON(channel.arm.isOn()));
        sendChannelClips(index, this.channel);
      } else if (p.getParent() instanceof LXClip) {
        LXClip clip = (LXClip)p.getParent();
        sendClip(index, this.channel, clip.getIndex(), clip);
      }
      if (this.channel instanceof LXChannel) {
        LXChannel c = (LXChannel) this.channel;
        if (p == c.focusedPattern) {
          int focusedPatternIndex = c.getFocusedPatternIndex();
          int channelSurfaceIndex = c.controlSurfaceFocusIndex.getValuei();
          if (focusedPatternIndex < channelSurfaceIndex) {
            c.controlSurfaceFocusIndex.setValue(focusedPatternIndex);
          } else if (focusedPatternIndex >= channelSurfaceIndex + CLIP_LAUNCH_ROWS) {
            c.controlSurfaceFocusIndex.setValue(focusedPatternIndex - CLIP_LAUNCH_ROWS + 1);
          }
          sendChannelPatterns(index, c);
        }
      }
    }

    public void dispose() {
      if (this.channel instanceof LXChannel) {
        ((LXChannel) this.channel).removeListener(this);
      } else {
        this.channel.removeListener(this);
      }
      this.channel.removeClipListener(this);
      this.channel.cueActive.removeListener(this);
      this.channel.enabled.removeListener(this);
      this.channel.crossfadeGroup.removeListener(this);
      this.channel.arm.removeListener(this);
      if (this.channel instanceof LXChannel) {
        LXChannel c = (LXChannel) this.channel;
        c.focusedPattern.removeListener(this);
        c.controlSurfaceFocusLength.setValue(0);
        c.controlSurfaceFocusIndex.setValue(0);
      }
      for (LXClip clip : this.channel.clips) {
        if (clip != null) {
          clip.running.removeListener(this);
        }
      }
    }

    @Override
    public void effectAdded(LXBus channel, LXEffect effect) {
    }

    @Override
    public void effectRemoved(LXBus channel, LXEffect effect) {
    }

    @Override
    public void effectMoved(LXBus channel, LXEffect effect) {
      // TODO(mcslee): update device focus??  *JKB: Note retained from APC40mkII
    }

    @Override
    public void indexChanged(LXAbstractChannel channel) {
      // Handled by the engine channelMoved listener.
    }

    @Override
    public void groupChanged(LXChannel channel, LXGroup group) {

    }

    @Override
    public void patternAdded(LXChannel channel, LXPattern pattern) {
      if (isGridModePatterns()) {
        sendChannelPatterns(mixerSurface.getIndex(channel), channel);
      }
    }

    @Override
    public void patternRemoved(LXChannel channel, LXPattern pattern) {
      if (isGridModePatterns()) {
        sendChannelPatterns(mixerSurface.getIndex(channel), channel);
      }
    }

    @Override
    public void patternMoved(LXChannel channel, LXPattern pattern) {
      if (isGridModePatterns()) {
        sendChannelPatterns(mixerSurface.getIndex(channel), channel);
      }
    }

    @Override
    public void patternWillChange(LXChannel channel, LXPattern pattern, LXPattern nextPattern) {
      if (isGridModePatterns()) {
        sendChannelPatterns(mixerSurface.getIndex(channel), channel);
      }
    }

    @Override
    public void patternDidChange(LXChannel channel, LXPattern pattern) {
      if (isGridModePatterns()) {
        sendChannelPatterns(mixerSurface.getIndex(channel), channel);
      }
    }

    @Override
    public void patternEnabled(LXChannel channel, LXPattern pattern) {
      if (isGridModePatterns() && channel.isComposite()) {
        sendChannelPatterns(mixerSurface.getIndex(channel), channel);
      }
    }

    @Override
    public void clipAdded(LXBus bus, LXClip clip) {
      clip.running.addListener(this);
      sendClip(mixerSurface.getIndex(channel), this.channel, clip.getIndex(), clip);
    }

    @Override
    public void clipRemoved(LXBus bus, LXClip clip) {
      clip.running.removeListener(this);
      sendChannelClips(mixerSurface.getIndex(channel), this.channel);
    }

  }

  public final BooleanParameter masterFaderEnabled =
    new BooleanParameter("Master Fader", true)
    .setDescription("Whether the master fader is enabled");

  public final EnumParameter<LXMidiParameterControl.Mode> faderMode =
    new EnumParameter<LXMidiParameterControl.Mode>("Fader Mode", LXMidiParameterControl.Mode.PICKUP)
    .setDescription("Parameter control mode for faders");

  private final LXMidiParameterControl masterFader;
  private final LXMidiParameterControl[] channelFaders;

  public APCminiMk2(LX lx, LXMidiInput input, LXMidiOutput output) {
    super(lx, input, output);

    this.masterFader = new LXMidiParameterControl(this.lx.engine.mixer.masterBus.fader);
    this.channelFaders = new LXMidiParameterControl[NUM_CHANNELS];
    for (int i = 0; i < NUM_CHANNELS; i++) {
      this.channelFaders[i] = new LXMidiParameterControl();
    }
    updateFaderMode();

    this.mixerSurface =
      new MixerSurface(lx, this.mixerSurfaceListener, NUM_CHANNELS, CLIP_LAUNCH_ROWS)
      .setGridMode(this.gridMode.engineGridMode);

    addSetting("masterFaderEnabled", this.masterFaderEnabled);
    addSetting("faderMode", this.faderMode);
    addState("channelNumber", this.mixerSurface.channelNumber);
    addState("gridClipOffset", this.mixerSurface.gridClipOffset);
    addState("gridPatternOffset", this.mixerSurface.gridPatternOffset);
  }

  @Override
  public String getName() {
    return "APC Mini mk2";
  }

  private boolean isGridModePatterns() {
    return this.gridMode == GridMode.PATTERNS;
  }

  private boolean isGridModeClips() {
    return this.gridMode == GridMode.CLIPS;
  }

  private boolean isGridModeParameters() {
    return this.gridMode == GridMode.PARAMETERS;
  }

  private void setGridMode(GridMode gridMode) {
    if (this.gridMode != gridMode) {
      this.gridMode = gridMode;
      this.mixerSurface.setGridMode(gridMode.engineGridMode);
      if (gridMode.engineGridMode != null) {
        lx.engine.clips.gridMode.setValue(gridMode.engineGridMode);
      }
      sendGridModeButtons();
      sendGrid();
    }
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.faderMode) {
      updateFaderMode();
    }
  }

  private void updateFaderMode() {
    final LXMidiParameterControl.Mode mode = this.faderMode.getEnum();
    this.masterFader.setMode(mode);
    for (LXMidiParameterControl channelFader : this.channelFaders) {
      channelFader.setMode(mode);
    }
  }

  @Override
  protected void onEnable(boolean on) {
    if (on) {
      initialize(false);
      register();
    } else {
      for (int i = 0; i < this.mixerSurface.bankWidth; ++i) {
        LXAbstractChannel channel = this.mixerSurface.getChannel(i);
        if (channel instanceof LXChannel) {
          ((LXChannel)channel).controlSurfaceFocusLength.setValue(0);
        }
      }
      if (this.isRegistered) {
        unregister();
      }
    }
  }

  @Override
  protected void onReconnect() {
    if (this.enabled.isOn()) {
      initialize(true);
      this.deviceListener.resend();
    }
  }

  private void initialize(boolean reconnect) {
    sendGrid();
    sendChannelButtonRow();
    sendSceneLaunchButtons();
  }

  private void sendGrid() {
    if (isGridModeParameters()) {
      this.deviceListener.resend();
    } else {
      for (int i = 0; i < NUM_CHANNELS; ++i) {
        LXAbstractChannel channel = getChannel(i);
        switch (this.gridMode) {
          case PATTERNS:
            sendChannelPatterns(i, channel);
            break;
          case CLIPS:
            sendChannelClips(i, channel);
            break;
          case PARAMETERS:
            break;
        }
      }
    }
  }

  private void clearGrid() {
    sendNoteOn(MIDI_CHANNEL_SINGLE, FADER_CTRL_VOLUME, LED_OFF);
    sendNoteOn(MIDI_CHANNEL_SINGLE, FADER_CTRL_PAN, LED_OFF);
    sendNoteOn(MIDI_CHANNEL_SINGLE, FADER_CTRL_SEND, LED_OFF);
    sendNoteOn(MIDI_CHANNEL_SINGLE, FADER_CTRL_DEVICE, LED_OFF);
    for (int i = 0; i < NUM_CHANNELS; ++i) {
      sendChannelPatterns(i, null);
    }
  }

  private void sendChannelPatterns(int index, LXAbstractChannel bus) {
    if (index < 0 || index >= CLIP_LAUNCH_COLUMNS) {
      return;
    }
    if (bus instanceof LXChannel) {
      final LXChannel channel = (LXChannel) bus;

      final int baseIndex = this.mixerSurface.getGridPatternOffset();
      final int endIndex = channel.patterns.size() - baseIndex;
      final int focusedIndex = (channel.patterns.size() == 0) ? -1 : channel.focusedPattern.getValuei() - baseIndex;

      final int activeIndex = channel.getActivePatternIndex() - baseIndex;
      final int nextIndex = channel.getNextPatternIndex() - baseIndex;

      final boolean isPlaylist = channel.isPlaylist();

      for (int y = 0; y < CLIP_LAUNCH_ROWS; ++y) {
        int behavior = MIDI_CHANNEL_MULTI_100_PERCENT;
        int note = CLIP_LAUNCH + CLIP_LAUNCH_COLUMNS * (CLIP_LAUNCH_ROWS - 1 - y) + index;
        int color = LED_OFF;
        if (isPlaylist) {
          if (y == activeIndex) {
            // This pattern is active (may also be focused)
            behavior = LED_PATTERN_ACTIVE_BEHAVIOR;
            color = LED_PATTERN_ACTIVE_COLOR;
          } else if (y == nextIndex) {
            // This pattern is being transitioned to
            behavior = LED_PATTERN_TRANSITION_BEHAVIOR;
            color = LED_PATTERN_TRANSITION_COLOR;
          } else if (y == focusedIndex) {
            // This pattern is not active, but it is focused
            behavior = LED_PATTERN_FOCUSED_BEHAVIOR;
            color = LED_PATTERN_FOCUSED_COLOR;
          } else if (y < endIndex) {
            // There is a pattern present
            behavior = LED_PATTERN_INACTIVE_BEHAVIOR;
            color = LED_PATTERN_INACTIVE_COLOR;
          }
        } else {
          if (y < endIndex) {
            if (channel.patterns.get(baseIndex + y).enabled.isOn()) {
              behavior = LED_PATTERN_ENABLED_BEHAVIOR;
              color = LED_PATTERN_ENABLED_COLOR;
            } else if (y == focusedIndex) {
              // This pattern is not enabled, but it is focused
              behavior = LED_PATTERN_FOCUSED_BEHAVIOR;
              color = LED_PATTERN_FOCUSED_COLOR;
            } else {
              behavior = LED_PATTERN_INACTIVE_BEHAVIOR;
              color = LED_PATTERN_INACTIVE_COLOR;
            }
          }
        }
        sendNoteOn(behavior, note, color);
      }
    } else {
      for (int y = 0; y < CLIP_LAUNCH_ROWS; ++y) {
        sendNoteOn(
          MIDI_CHANNEL_MULTI_100_PERCENT,
          CLIP_LAUNCH + CLIP_LAUNCH_COLUMNS * (CLIP_LAUNCH_ROWS - 1 - y) + index,
          LED_OFF
        );
      }
    }
  }

  private void sendChannelClips(int index, LXAbstractChannel channel) {
    if (index < 0 || index >= CLIP_LAUNCH_COLUMNS) {
      return;
    }
    for (int i = 0; i < CLIP_LAUNCH_ROWS; ++i) {
      LXClip clip = null;
      if (channel != null) {
        clip = channel.getClip(i + this.mixerSurface.getGridClipOffset());
      }
      sendClip(index, channel, i, clip);
    }
  }

  private void sendClip(int channelIndex, LXAbstractChannel channel, int clipIndex, LXClip clip) {
    if (!isGridModeClips() || channelIndex < 0 || channelIndex >= CLIP_LAUNCH_COLUMNS || clipIndex < 0 || clipIndex >= CLIP_LAUNCH_ROWS) {
      return;
    }
    int behavior = MIDI_CHANNEL_MULTI_100_PERCENT;
    int color = LED_OFF;
    int pitch = CLIP_LAUNCH + channelIndex + CLIP_LAUNCH_COLUMNS * (CLIP_LAUNCH_ROWS - 1 - clipIndex);
    if (channel != null && clip != null) {
      if (channel.arm.isOn()) {
        if (clip.isRunning()) {
          behavior = LED_CLIP_RECORD_BEHAVIOR;
          color =  LED_CLIP_RECORD_COLOR;
        } else {
          behavior = LED_CLIP_ARM_BEHAVIOR;
          color =  LED_CLIP_ARM_COLOR;
        }
      } else {
        if (clip.isRunning()) {
          behavior = LED_CLIP_PLAY_BEHAVIOR;
          color =  LED_CLIP_PLAY_COLOR;
        } else {
          behavior = LED_CLIP_INACTIVE_BEHAVIOR;
          color =  LED_CLIP_INACTIVE_COLOR;
        }
      }
    }
    sendNoteOn(behavior, pitch, color);
  }

  private void sendChannelFocus() {
    if ((this.channelButtonMode == ChannelButtonMode.FOCUS) && !this.shiftOn) {
      sendChannelButtonRow();
    }
  }

  private void setChannelButtonMode(ChannelButtonMode mode) {
    this.channelButtonMode = mode;
    sendSceneLaunchButtons();
  }

  private void sendChannelButtonRow() {
    for (int i = 0; i < NUM_CHANNELS; ++i) {
      sendChannelButton(i, getChannel(i));
    }
  }

  private void clearChannelButtonRow() {
    for (int i = 0; i < NUM_CHANNELS; ++i) {
      sendNoteOn(MIDI_CHANNEL_SINGLE, CHANNEL_BUTTON + i, LED_OFF);
    }
  }

  private void sendSceneLaunchButtons() {
    if (this.shiftOn) {
      sendNoteOn(MIDI_CHANNEL_SINGLE, CHANNEL_BUTTON_MODE_FOCUS, LED_ON(this.channelButtonMode == ChannelButtonMode.FOCUS));
      sendNoteOn(MIDI_CHANNEL_SINGLE, CHANNEL_BUTTON_MODE_ENABLED, LED_ON(this.channelButtonMode == ChannelButtonMode.ENABLED));
      sendNoteOn(MIDI_CHANNEL_SINGLE, CHANNEL_BUTTON_MODE_CUE, LED_ON(this.channelButtonMode == ChannelButtonMode.CUE));
      sendNoteOn(MIDI_CHANNEL_SINGLE, CHANNEL_BUTTON_MODE_ARM, LED_ON(this.channelButtonMode == ChannelButtonMode.ARM));
      sendNoteOn(MIDI_CHANNEL_SINGLE, CHANNEL_BUTTON_MODE_CLIP_STOP, LED_ON(this.channelButtonMode == ChannelButtonMode.CLIP_STOP));
      sendNoteOn(MIDI_CHANNEL_SINGLE, DRUM, LED_OFF);
      sendNoteOn(MIDI_CHANNEL_SINGLE, NOTE, LED_OFF);
      sendNoteOn(MIDI_CHANNEL_SINGLE, STOP_ALL_CLIPS, LED_OFF);
    } else {
      clearSceneLaunchButtons();
    }
  }

  private void clearSceneLaunchButtons() {
    sendNoteOn(MIDI_CHANNEL_SINGLE, CLIP_STOP, LED_OFF);
    sendNoteOn(MIDI_CHANNEL_SINGLE, SOLO, LED_OFF);
    sendNoteOn(MIDI_CHANNEL_SINGLE, MUTE, LED_OFF);
    sendNoteOn(MIDI_CHANNEL_SINGLE, REC_ARM, LED_OFF);
    sendNoteOn(MIDI_CHANNEL_SINGLE, SELECT, LED_OFF);
    sendNoteOn(MIDI_CHANNEL_SINGLE, DRUM, LED_OFF);
    sendNoteOn(MIDI_CHANNEL_SINGLE, NOTE, LED_OFF);
    sendNoteOn(MIDI_CHANNEL_SINGLE, STOP_ALL_CLIPS, LED_OFF);
  }

  private void sendGridModeButtons() {
    if (this.shiftOn) {
      sendNoteOn(MIDI_CHANNEL_SINGLE, GRID_MODE_PATTERNS, LED_ON(this.gridMode == GridMode.PATTERNS));
      sendNoteOn(MIDI_CHANNEL_SINGLE, GRID_MODE_CLIPS, LED_ON(this.gridMode == GridMode.CLIPS));
      sendNoteOn(MIDI_CHANNEL_SINGLE, FADER_CTRL_SEND, LED_OFF);
      sendNoteOn(MIDI_CHANNEL_SINGLE, GRID_MODE_PARAMETERS, LED_ON(this.gridMode == GridMode.PARAMETERS));
    }
  }

  private void sendChannelButton(int index, LXAbstractChannel channel) {
    final int noteNumber = index + CHANNEL_BUTTON;
    int color = LED_OFF;

    if (this.shiftOn) {
      // Shift - FADER CTRL buttons set the grid mode
      // Arrow buttons move the grid on keypress, otherwise LED_OFF
      switch (noteNumber) {
      case GRID_MODE_PATTERNS:
        color = LED_ON(this.gridMode == GridMode.PATTERNS);
        break;
      case GRID_MODE_CLIPS:
        color = LED_ON(this.gridMode == GridMode.CLIPS);
        break;
      case GRID_MODE_PARAMETERS:
        color = LED_ON(this.gridMode == GridMode.PARAMETERS);
        break;
      }

    } else {

      // Not shift - indicate channel status based upon state
      if (channel != null) {
        switch (this.channelButtonMode) {
        case FOCUS:
          color = LED_ON(channel == this.lx.engine.mixer.getFocusedChannel());
          break;
        case ENABLED:
          color = LED_ON(channel.enabled.isOn());
          break;
        case CUE:
          color = LED_ON(channel.cueActive.isOn());
          break;
        case ARM:
          color = LED_ON(channel.arm.isOn());
          break;
        case CLIP_STOP:
          // Action button, on only when pressed
          color = LED_OFF;
          break;
        }
      }
    }

    sendNoteOn(MIDI_CHANNEL_SINGLE, CHANNEL_BUTTON + index, color);
  }

  private final LXParameterListener focusedChannelListener = p -> {
    sendChannelFocus();
  };

  private boolean isRegistered = false;

  private void register() {

    this.isRegistered = true;

    this.mixerSurface.register();
    this.lx.engine.mixer.focusedChannel.addListener(this.focusedChannelListener);
    this.deviceListener.focusedDevice.register();
  }

  private void unregister() {
    this.isRegistered = false;

    this.mixerSurface.unregister();
    this.deviceListener.focusedDevice.unregister();
    this.lx.engine.mixer.focusedChannel.removeListener(this.focusedChannelListener);

    clearGrid();
    clearChannelButtonRow();
    clearSceneLaunchButtons();
  }

  private void registerChannel(LXAbstractChannel channel) {
    if (!this.channelListeners.containsKey(channel)) {
      this.channelListeners.put(channel, new ChannelListener(channel));
    }
  }

  private void unregisterChannel(LXAbstractChannel channel) {
    ChannelListener channelListener = this.channelListeners.remove(channel);
    if (channelListener != null) {
      channelListener.dispose();
    }
  }

  private LXAbstractChannel getChannel(int index) {
    return this.mixerSurface.getChannel(index);
  }

  private void noteReceived(MidiNote note, boolean on) {
    final int pitch = note.getPitch();

    // Global momentary
    if (pitch == SHIFT) {
      // Shift doesn't have an LED, odd.
      this.shiftOn = on;
      sendChannelButtonRow();
      sendSceneLaunchButtons();
      return;
    }

    // Clip grid buttons
    if (pitch >= CLIP_LAUNCH && pitch <= CLIP_LAUNCH_MAX) {
      if (on) {
        gridNoteOnReceived(note);
      }
      return;
    }

    // Scene launch buttons
    if (pitch >= SCENE_LAUNCH && pitch <= SCENE_LAUNCH_MAX) {
      sceneLaunchNoteReceived(note, on);
      return;
    }

    if (pitch >= CHANNEL_BUTTON && pitch <= CHANNEL_BUTTON_MAX) {
      channelButtonNoteReceived(note, on);
      return;
    }

    LXMidiEngine.error("APCminiMk2 received unmapped note: " + note);
  }

  private void gridNoteOnReceived(MidiNote note) {
    final int pitch = note.getPitch();
    final int channelIndex = (pitch - CLIP_LAUNCH) % CLIP_LAUNCH_COLUMNS;
    final int index = CLIP_LAUNCH_ROWS - 1 - ((pitch - CLIP_LAUNCH) / CLIP_LAUNCH_COLUMNS);
    if (isGridModeParameters()) {
      // Grid button: Parameter
      if (!this.shiftOn) {
        this.deviceListener.onParameterButton(channelIndex, index);
      }
    } else {
      LXAbstractChannel channel = getChannel(channelIndex);
      if (channel != null) {
        if (isGridModeClips()) {
          // Grid button: Clip
          final int clipIndex = index + mixerSurface.getGridClipOffset();
          LXClip clip = channel.getClip(clipIndex);
          if (clip == null) {
            clip = channel.addClip(clipIndex);
          } else {
            if (this.shiftOn) {
              this.lx.engine.clips.setFocusedClip(clip);
            } else {
              if (clip.isRunning()) {
                clip.stop();
              } else {
                clip.trigger();
                this.lx.engine.clips.setFocusedClip(clip);
              }
            }
          }
        } else if (isGridModePatterns()) {
          // Grid button: Pattern
          if (channel instanceof LXChannel) {
            final LXChannel c = (LXChannel) channel;
            int target = index + mixerSurface.getGridPatternOffset();
            if (target < c.getPatterns().size()) {
              c.focusedPattern.setValue(target);
              if (!this.shiftOn) {
                if (channel.isPlaylist()) {
                  c.goPatternIndex(target);
                } else {
                  c.patterns.get(target).enabled.toggle();
                }
              }
            }
          }
        }
      }
    }
  }

  private void sceneLaunchNoteReceived(MidiNote note, boolean on) {
    final int pitch = note.getPitch();
    if (this.shiftOn) {

      if (pitch == STOP_ALL_CLIPS) {
        sendNoteOn(MIDI_CHANNEL_SINGLE, pitch, LED_ON(on));
      }

      if (on) {
        // Buttons control ChannelButtonMode
        // SHIFT + SOFT KEYS sets the ChannelButtonMode
        switch (pitch) {
        case CHANNEL_BUTTON_MODE_FOCUS:
          setChannelButtonMode(ChannelButtonMode.FOCUS);
          break;
        case CHANNEL_BUTTON_MODE_ENABLED:
          setChannelButtonMode(ChannelButtonMode.ENABLED);
          break;
        case CHANNEL_BUTTON_MODE_CUE:
          setChannelButtonMode(ChannelButtonMode.CUE);
          break;
        case CHANNEL_BUTTON_MODE_ARM:
          setChannelButtonMode(ChannelButtonMode.ARM);
          break;
        case CHANNEL_BUTTON_MODE_CLIP_STOP:
          setChannelButtonMode(ChannelButtonMode.CLIP_STOP);
          break;

        case DRUM:
        case NOTE:
          // These are not implemented because the APC doesn't even send them... it
          // puts the APC into its own custom DRUM / NOTE mode
          LXMidiEngine.error("APCminiMk2 received unexpected SHIFT + DRUM/NOTE: " + note);
          break;

        case STOP_ALL_CLIPS:
          // Global stop/trigger action
          if (isGridModePatterns()) {
            this.lx.engine.clips.launchPatternCycle();
          } else if (isGridModeClips()) {
            this.lx.engine.clips.stopClips();
          }
          break;
        }
      }
    } else {
      // Global momentary mode
      sendNoteOn(MIDI_CHANNEL_SINGLE, pitch, LED_ON(on));
      if (isGridModeClips()) {
        this.lx.engine.clips.launchScene(pitch - SCENE_LAUNCH + this.mixerSurface.getGridClipOffset());
      } else if (isGridModePatterns()) {
        this.lx.engine.clips.launchPatternScene(pitch - SCENE_LAUNCH + this.mixerSurface.getGridPatternOffset());
      }
    }
  }

  private void channelButtonNoteReceived(MidiNote note, boolean on) {
    final int pitch = note.getPitch();
    if (this.shiftOn) {

      // Momentary buttons
      switch (pitch) {
      case SELECT_LEFT:
      case SELECT_RIGHT:
      case SELECT_UP:
      case SELECT_DOWN:
        sendNoteOn(MIDI_CHANNEL_SINGLE, pitch, LED_ON(on));
      }

      // Shift+Button press
      if (on) {
        switch (note.getPitch()) {
        case SELECT_LEFT:
          if (isGridModeParameters()) {
            this.deviceListener.focusedDevice.previousDevice();
          } else {
            this.mixerSurface.channelNumber.decrement();
          }
          break;

        case SELECT_RIGHT:
          if (isGridModeParameters()) {
            this.deviceListener.focusedDevice.nextDevice();
          } else {
            this.mixerSurface.channelNumber.increment();
          }
          break;

        case SELECT_UP:
          if (isGridModeParameters()) {
            LXBus bus = this.lx.engine.mixer.getFocusedChannel();
            if (bus instanceof LXChannel) {
              ((LXChannel) bus).focusedPattern.decrement(1, false);
            }
          } else {
            this.mixerSurface.decrementGridOffset();
          }
          break;

        case SELECT_DOWN:
          if (isGridModeParameters()) {
            LXBus bus = this.lx.engine.mixer.getFocusedChannel();
            if (bus instanceof LXChannel) {
              ((LXChannel) bus).focusedPattern.increment(1, false);
            }
          } else {
            this.mixerSurface.incrementGridOffset();
          }
          break;

        // SHIFT + FADER CTRL sets the GridMode
        case GRID_MODE_PATTERNS:
          setGridMode(GridMode.PATTERNS);
          break;
        case GRID_MODE_CLIPS:
          setGridMode(GridMode.CLIPS);
          break;
        case GRID_MODE_PARAMETERS:
          setGridMode(GridMode.PARAMETERS);
          break;
        }
      }

    } else {

      // Momentary buttons when firing clip trigger
      if (this.channelButtonMode == ChannelButtonMode.CLIP_STOP) {
        sendNoteOn(MIDI_CHANNEL_SINGLE, pitch, LED_ON(on));
      }

      if (on) {
        LXAbstractChannel channel = getChannel(pitch - CHANNEL_BUTTON);
        if (channel != null) {
          switch (this.channelButtonMode) {
          case FOCUS:
            this.lx.engine.mixer.focusedChannel.setValue(channel.getIndex());
            lx.engine.mixer.selectChannel(lx.engine.mixer.getFocusedChannel());
            break;
          case ENABLED:
            channel.enabled.toggle();
            break;
          case CUE:
            channel.cueActive.toggle();
            break;
          case ARM:
            channel.arm.toggle();
            break;
          case CLIP_STOP:
            if (isGridModePatterns()) {
              if (channel.isPlaylist()) {
                ((LXChannel) channel).triggerPatternCycle.trigger();
              }
            } else if (isGridModeClips()) {
              channel.stopClips();
            }
            break;
          }
        }
      }
    }
  }

  @Override
  public void noteOnReceived(MidiNoteOn note) {
    noteReceived(note, true);
  }

  @Override
  public void noteOffReceived(MidiNote note) {
    noteReceived(note, false);
  }

  @Override
  public void controlChangeReceived(MidiControlChange cc) {
    int number = cc.getCC();

    switch (number) {
    case MASTER_FADER:
      if (this.masterFaderEnabled.isOn()) {
        this.masterFader.setValue(cc);
      }
      return;
    }

    if (number >= CHANNEL_FADER && number <= CHANNEL_FADER_MAX) {
      int channel = number - CHANNEL_FADER;
      this.channelFaders[channel].setValue(cc);
      return;
    }

    LXMidiEngine.error("APC MINI unmapped control change: " + cc);
  }

  @Override
  public void dispose() {
    if (this.isRegistered) {
      unregister();
    }
    this.masterFader.dispose();
    for (LXMidiParameterControl fader : this.channelFaders) {
      fader.dispose();
    }
    this.deviceListener.dispose();
    this.mixerSurface.dispose();
    super.dispose();
  }

}
