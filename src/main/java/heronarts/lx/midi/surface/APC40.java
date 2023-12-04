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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.clip.LXClip;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.midi.LXMidiEngine;
import heronarts.lx.midi.LXMidiInput;
import heronarts.lx.midi.LXMidiOutput;
import heronarts.lx.midi.LXShortMessage;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXGroup;
import heronarts.lx.mixer.LXMixerEngine;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.pattern.LXPattern;

public class APC40 extends LXMidiSurface implements LXMidiSurface.Bidirectional {

  public static final String DEVICE_NAME = "Akai APC40";

  public static final byte GENERIC_MODE = 0x40;
  public static final byte ABLETON_MODE = 0x41;
  public static final byte ABLETON_ALTERNATE_MODE = 0x42;

  protected static final int LED_STYLE_OFF = 0;
  protected static final int LED_STYLE_SINGLE = 1;
  protected static final int LED_STYLE_UNIPOLAR = 2;
  protected static final int LED_STYLE_BIPOLAR = 3;

  public static final int NUM_CHANNELS = 8;

  // CCs
  public static final int CHANNEL_FADER = 7;
  public static final int MASTER_FADER = 14;
  public static final int CROSSFADER = 15;
  public static final int CUE_LEVEL = 47;

  public static final int DEVICE_KNOB = 16;
  public static final int DEVICE_KNOB_NUM = 8;
  public static final int DEVICE_KNOB_MAX = DEVICE_KNOB + DEVICE_KNOB_NUM;

  public static final int DEVICE_KNOB_STYLE = 24;
  public static final int DEVICE_KNOB_STYLE_MAX = DEVICE_KNOB_STYLE + DEVICE_KNOB_NUM;

  public static final int TRACK_KNOB = 48;
  public static final int TRACK_KNOB_NUM = 8;
  public static final int TRACK_KNOB_MAX = TRACK_KNOB + TRACK_KNOB_NUM - 1;

  public static final int TRACK_KNOB_STYLE = 56;
  public static final int TRACK_KNOB_STYLE_MAX = TRACK_KNOB_STYLE + TRACK_KNOB_NUM;

  // Notes
  public static final int CLIP_LAUNCH = 53;
  public static final int CLIP_LAUNCH_ROWS = 5;
  public static final int CLIP_LAUNCH_MAX = CLIP_LAUNCH + CLIP_LAUNCH_ROWS - 1;
  public static final int CLIP_LAUNCH_COLUMNS = NUM_CHANNELS;

  public static final int CHANNEL_ARM = 48;
  public static final int CHANNEL_SOLO = 49;
  public static final int CHANNEL_ACTIVE = 50;
  public static final int CHANNEL_FOCUS = 51;
  public static final int CLIP_STOP = 52;

  public static final int CHANNEL_CROSSFADE_GROUP = 66;
  public static final int MASTER_FOCUS = 80;

  public static final int STOP_ALL_CLIPS = 81;
  public static final int SCENE_LAUNCH = 82;
  public static final int SCENE_LAUNCH_NUM = 5;
  public static final int SCENE_LAUNCH_MAX = SCENE_LAUNCH + SCENE_LAUNCH_NUM - 1;

  public static final int PAN = 87;
  public static final int SEND_A = 88;
  public static final int SEND_B = 89;
  public static final int SEND_C = 90;

  public static final int PLAY = 91;
  public static final int STOP = 92;
  public static final int RECORD = 93;

  public static final int BANK_SELECT_UP = 94;
  public static final int BANK_SELECT_DOWN = 95;
  public static final int BANK_SELECT_RIGHT = 96;
  public static final int BANK_SELECT_LEFT = 97;

  public static final int SHIFT = 98;

  public static final int TAP_TEMPO = 99;
  public static final int NUDGE_PLUS = 100;
  public static final int NUDGE_MINUS = 101;

  public static final int CLIP_TRACK = 58;
  public static final int DEVICE_ON_OFF = 59;
  public static final int DEVICE_LEFT = 60;
  public static final int DEVICE_RIGHT = 61;

  public static final int DETAIL_VIEW = 62;
  public static final int REC_QUANTIZE = 63;
  public static final int MIDI_OVERDUB = 64;
  public static final int METRONOME = 65;

  // LED color + mode definitions
  public static final int LED_OFF = 0;
  public static final int LED_ON = 1;
  public static final int LED_BLINK = 2;

  public static final int LED_GREEN = 1;
  public static final int LED_GREEN_BLINK = 2;
  public static final int LED_RED = 3;
  public static final int LED_RED_BLINK = 4;
  public static final int LED_YELLOW = 5;
  public static final int LED_YELLOW_BLINK = 6;

  public enum GridMode {
    PATTERN,
    CLIP;
  };

  private GridMode gridMode = GridMode.PATTERN;

  private boolean shiftOn = false;

  private final Map<LXAbstractChannel, ChannelListener> channelListeners = new HashMap<LXAbstractChannel, ChannelListener>();

  private final DeviceListener deviceListener = new DeviceListener();

  private class DeviceListener implements FocusedDevice.Listener, LXParameterListener {

    private final FocusedDevice focusedDevice;
    private LXDeviceComponent device = null;

    private final LXListenableNormalizedParameter[] knobs =
      new LXListenableNormalizedParameter[DEVICE_KNOB_NUM];

    DeviceListener() {
      Arrays.fill(this.knobs, null);
      this.focusedDevice = new FocusedDevice(lx, APC40.this, this);
    }

    @Override
    public void onDeviceFocused(LXDeviceComponent device) {
      registerDevice(device);
    }

    void resend() {
      for (int i = 0; i < this.knobs.length; ++i) {
        LXListenableNormalizedParameter parameter = this.knobs[i];
        if (parameter != null) {
          sendControlChange(0, DEVICE_KNOB_STYLE + i, parameter.getPolarity() == LXParameter.Polarity.BIPOLAR ? LED_STYLE_BIPOLAR : LED_STYLE_UNIPOLAR);
          double normalized = parameter.getBaseNormalized();
          sendControlChange(0, DEVICE_KNOB + i, (int) (normalized * 127));
        } else {
          sendControlChange(0, DEVICE_KNOB_STYLE + i, LED_STYLE_OFF);
        }
      }
      boolean isEnabled = false;
      if (this.device instanceof LXEffect) {
        LXEffect effect = (LXEffect) this.device;
        isEnabled = effect.enabled.isOn();
      } else if (this.device instanceof LXPattern) {
        LXPattern pattern = (LXPattern) this.device;
        isEnabled = isPatternEnabled(pattern);
      }
      sendNoteOn(0, DEVICE_ON_OFF, isEnabled ? LED_ON : LED_OFF);
    }

    private boolean isPatternEnabled(LXPattern pattern) {
      switch (pattern.getChannel().compositeMode.getEnum()) {
      case BLEND:
        return pattern.enabled.isOn();
      default:
      case PLAYLIST:
        return pattern == pattern.getChannel().getTargetPattern();
      }
    }

    private void clearKnobsAfter(int i) {
      while (i < this.knobs.length) {
        sendControlChange(0, DEVICE_KNOB_STYLE + i, LED_STYLE_OFF);
        ++i;
      }
    }

    private void registerDevice(LXDeviceComponent device) {
      if (this.device == device) {
        return;
      }
      unregisterDevice();
      this.device = device;

      boolean isEnabled = false;
      if (this.device instanceof LXEffect) {
        LXEffect effect = (LXEffect) this.device;
        effect.enabled.addListener(this);
        isEnabled = effect.isEnabled();
      } else if (this.device instanceof LXPattern) {
        LXPattern pattern = (LXPattern) this.device;
        pattern.enabled.addListener(this);
        isEnabled = isPatternEnabled(pattern);
      }
      if (this.device != null) {
        this.device.remoteControlsChanged.addListener(this);
      }

      sendNoteOn(0, DEVICE_ON_OFF, isEnabled ? LED_ON : LED_OFF);
      if (this.device == null) {
        clearKnobsAfter(0);
        return;
      }

      registerDeviceKnobs();
    }

    private void registerDeviceKnobs() {
      int i = 0;
      final List<LXParameter> uniqueParameters = new ArrayList<LXParameter>();
      for (LXListenableNormalizedParameter parameter : this.device.getRemoteControls()) {
        if (i >= this.knobs.length) {
          break;
        }
        this.knobs[i] = parameter;
        if (parameter != null) {
          if (!uniqueParameters.contains(parameter)) {
            parameter.addListener(this);
            uniqueParameters.add(parameter);
          }
          sendControlChange(0, DEVICE_KNOB_STYLE + i, parameter.getPolarity() == LXParameter.Polarity.BIPOLAR ? LED_STYLE_BIPOLAR : LED_STYLE_UNIPOLAR);
          sendKnobValue(this.knobs[i], i);
        } else {
          sendControlChange(0, DEVICE_KNOB_STYLE + i, LED_STYLE_OFF);
        }
        ++i;
      }
      clearKnobsAfter(i);
    }

    @Override
    public void onParameterChanged(LXParameter parameter) {
      LXEffect effect = (this.device instanceof LXEffect) ? (LXEffect) this.device : null;
      LXPattern pattern = (this.device instanceof LXPattern) ? (LXPattern) this.device : null;
      if (parameter == this.device.remoteControlsChanged) {
        unregisterDeviceKnobs();
        registerDeviceKnobs();
      } else if ((effect != null) && (parameter == effect.enabled)) {
        sendNoteOn(0, DEVICE_ON_OFF, effect.enabled.isOn() ? LED_ON : LED_OFF);
      } else if ((pattern != null) && (parameter == pattern.enabled)) {
        sendNoteOn(0, DEVICE_ON_OFF, isPatternEnabled(pattern) ? LED_ON : LED_OFF);
        sendChannelPatterns(pattern.getChannel().getIndex(), pattern.getChannel());
      } else {
        for (int i = 0; i < this.knobs.length; ++i) {
          if (parameter == this.knobs[i]) {
            sendKnobValue(this.knobs[i], i);
          }
        }
      }
    }

    private void sendKnobValue(LXListenableNormalizedParameter knobParam, int i) {
      double normalized = knobParam.getBaseNormalized();

      // Wrappable discrete parameters need to inset the values a bit to avoid fiddly jumping at 0/1
      if ((knobParam instanceof DiscreteParameter) && knobParam.isWrappable()) {
        DiscreteParameter discrete = (DiscreteParameter) knobParam;
        normalized = (discrete.getBaseValuei() - discrete.getMinValue() + 0.5f) / discrete.getRange();
      }
      sendControlChange(0, DEVICE_KNOB + i, (int) (normalized * 127));
    }

    void onDeviceOnOff() {
      if (this.device instanceof LXPattern) {
        final LXPattern pattern = (LXPattern) this.device;
        final LXChannel channel = pattern.getChannel();
        if (channel.compositeMode.getEnum() == LXChannel.CompositeMode.BLEND) {
          pattern.enabled.toggle();
        } else {
          pattern.getChannel().goPatternIndex(pattern.getIndex());
        }
        sendNoteOn(0, DEVICE_ON_OFF, isPatternEnabled(pattern) ? LED_ON : LED_OFF);
      } else if (this.device instanceof LXEffect) {
        LXEffect effect = (LXEffect) this.device;
        effect.enabled.toggle();
      }
    }

    void onKnob(int index, double normalized) {
      if (this.knobs[index] != null) {
        // If it's a wrappable parameter, let it wrap
        if (this.knobs[index].isWrappable()) {
          if (normalized == 0.0) {
            normalized = 1.0;
          } else if (normalized == 1.0) {
            normalized = 0.0;
          }
        }
        this.knobs[index].setNormalized(normalized);
      }
    }

    private void unregisterDevice() {
      if (this.device != null) {
        if (this.device instanceof LXEffect) {
          LXEffect effect = (LXEffect) this.device;
          effect.enabled.removeListener(this);
        }
        if (this.device instanceof LXPattern) {
          LXPattern pattern = (LXPattern) this.device;
          pattern.enabled.removeListener(this);
        }
        this.device.remoteControlsChanged.removeListener(this);
        unregisterDeviceKnobs();
        this.device = null;
      }
    }

    private void unregisterDeviceKnobs() {
      final List<LXParameter> uniqueParameters = new ArrayList<LXParameter>();
      for (int i = 0; i < this.knobs.length; ++i) {
        if (this.knobs[i] != null) {
          if (!uniqueParameters.contains(this.knobs[i])) {
            uniqueParameters.add(this.knobs[i]);
            this.knobs[i].removeListener(this);
          }
          this.knobs[i] = null;
        }
      }
    }

    private void dispose() {
      unregisterDevice();
    }

  }

  private class ChannelListener implements LXChannel.Listener, LXBus.ClipListener, LXParameterListener {

    private final LXAbstractChannel channel;
    private final LXParameterListener onCompositeModeChanged = this::onCompositeModeChanged;

    ChannelListener(LXAbstractChannel channel) {
      this.channel = channel;
      if (channel instanceof LXChannel) {
        ((LXChannel) channel).addListener(this);
        ((LXChannel) channel).compositeMode.addListener(this.onCompositeModeChanged);
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

    public void dispose() {
      if (this.channel instanceof LXChannel) {
        ((LXChannel) this.channel).removeListener(this);
        ((LXChannel) this.channel).compositeMode.removeListener(this.onCompositeModeChanged);
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

    private void onCompositeModeChanged(LXParameter p) {
      final int index = this.channel.getIndex();
      if (index >= CLIP_LAUNCH_COLUMNS) {
        return;
      }
      sendChannelPatterns(index, this.channel);
    }

    public void onParameterChanged(LXParameter p) {
      int index = this.channel.getIndex();
      if (index >= CLIP_LAUNCH_COLUMNS) {
        return;
      }

      if (p == this.channel.cueActive) {
        sendNoteOn(index, CHANNEL_SOLO, this.channel.cueActive.isOn() ? LED_ON : LED_OFF);
      } else if (p == this.channel.enabled) {
        sendNoteOn(index, CHANNEL_ACTIVE, this.channel.enabled.isOn() ? LED_ON : LED_OFF);
      } else if (p == this.channel.crossfadeGroup) {
        sendNoteOn(index, CHANNEL_CROSSFADE_GROUP, this.channel.crossfadeGroup.getValuei());
      } else if (p == this.channel.arm) {
        sendNoteOn(index, CHANNEL_ARM, this.channel.arm.isOn() ? LED_ON : LED_OFF);
        sendChannelClips(this.channel.getIndex(), this.channel);
      } else if (p.getParent() instanceof LXClip) {
        // TODO(mcslee): could be more efficient...
        sendChannelClips(index, this.channel);
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

    @Override
    public void effectAdded(LXBus channel, LXEffect effect) {
    }

    @Override
    public void effectRemoved(LXBus channel, LXEffect effect) {
    }

    @Override
    public void effectMoved(LXBus channel, LXEffect effect) {
      // TODO(mcslee): update device focus??
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
      sendChannelPatterns(channel.getIndex(), channel);
    }

    @Override
    public void patternRemoved(LXChannel channel, LXPattern pattern) {
      sendChannelPatterns(channel.getIndex(), channel);
    }

    @Override
    public void patternMoved(LXChannel channel, LXPattern pattern) {
      sendChannelPatterns(channel.getIndex(), channel);
    }

    @Override
    public void patternWillChange(LXChannel channel, LXPattern pattern, LXPattern nextPattern) {
      sendChannelPatterns(channel.getIndex(), channel);
    }

    @Override
    public void patternDidChange(LXChannel channel, LXPattern pattern) {
      sendChannelPatterns(channel.getIndex(), channel);
    }

    @Override
    public void clipAdded(LXBus bus, LXClip clip) {
      clip.running.addListener(this);
      sendChannelClips(this.channel.getIndex(), this.channel);
    }

    @Override
    public void clipRemoved(LXBus bus, LXClip clip) {
      clip.running.removeListener(this);
      sendChannelClips(this.channel.getIndex(), this.channel);
    }

  }

  public final BooleanParameter masterFaderEnabled =
    new BooleanParameter("Master Fader", true)
    .setDescription("Whether the master fader is enabled");

  public final BooleanParameter crossfaderEnabled =
    new BooleanParameter("Crossfader", true)
    .setDescription("Whether the A/B crossfader is enabled");

  public final BooleanParameter clipLaunchEnabled =
    new BooleanParameter("Clip Launch", true)
    .setDescription("Whether the clip launch buttons are enabled");

  public APC40(LX lx, LXMidiInput input, LXMidiOutput output) {
    super(lx, input, output);
    addSetting("masterFaderEnabled", this.masterFaderEnabled);
    addSetting("crossfaderEnabled", this.crossfaderEnabled);
    addSetting("clipLaunchEnabled", this.clipLaunchEnabled);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.clipLaunchEnabled) {
      if (this.clipLaunchEnabled.isOn()) {
        sendChannels();
      } else {
        clearClipLaunch();
      }
    }
  }

  @Override
  protected void onEnable(boolean on) {
    if (on) {
      setApcMode(ABLETON_ALTERNATE_MODE);
      initialize(false);
      register();
    } else {
      this.deviceListener.registerDevice(null);
      for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
        if (channel instanceof LXChannel) {
          ((LXChannel)channel).controlSurfaceFocusLength.setValue(0);
        }
      }
      if (this.isRegistered) {
        unregister();
      }
      setApcMode(GENERIC_MODE);
    }
  }

  @Override
  protected void onReconnect() {
    if (this.enabled.isOn()) {
      setApcMode(ABLETON_ALTERNATE_MODE);
      initialize(true);
      this.deviceListener.resend();
    }
  }

  private void setApcMode(byte mode) {
    this.output.sendSysex(new byte[] {
      (byte) 0xf0, // sysex start
      0x47, // manufacturers id
      0x00, // device id
      0x73, // product model id
      0x60, // message
      0x00, // bytes MSB
      0x04, // bytes LSB
      mode,
      0x09, // version maj
      0x03, // version min
      0x01, // version bugfix
      (byte) 0xf7, // sysex end
    });
  }

  private void initialize(boolean reconnect) {
    for (int i = 0; i < DEVICE_KNOB_NUM; ++i) {
      sendControlChange(0, DEVICE_KNOB_STYLE+i, LED_STYLE_OFF);
    }
    for (int i = 0; i < TRACK_KNOB_NUM; ++i) {
      // Initialize channel knobs for generic control, but don't
      // reset their values if we're in a reconnect situation
      sendControlChange(0, TRACK_KNOB_STYLE+i, LED_STYLE_SINGLE);
      if (!reconnect) {
        sendControlChange(0, TRACK_KNOB+i, 64);
      }
    }
    sendChannels();
  }

  private void sendChannels() {
    for (int i = 0; i < NUM_CHANNELS; ++i) {
      sendChannel(i, getChannel(i));
    }
    sendChannelFocus();
  }

  private void sendChannelGrid() {
    for (int i = 0; i < NUM_CHANNELS; ++i) {
      LXAbstractChannel channel = getChannel(i);
      sendChannelPatterns(i, channel);
      sendChannelClips(i, channel);
    }
  }

  private void clearChannelGrid() {
    for (int i = 0; i < NUM_CHANNELS; ++i) {
      sendChannel(i, null);
    }
  }

  private void clearClipLaunch() {
    for (int i = 0; i < NUM_CHANNELS; ++i) {
      for (int j = CLIP_LAUNCH; j <= CLIP_LAUNCH_MAX; ++j) {
        sendNoteOn(i, j, LED_OFF);
      }
      sendNoteOn(i, CLIP_STOP, LED_OFF);
    }
    for (int j = SCENE_LAUNCH; j < SCENE_LAUNCH_MAX; ++j) {
      sendNoteOn(0, j, LED_OFF);
    }
  }

  private void sendChannel(int index, LXAbstractChannel channel) {
    if (channel != null) {
      sendNoteOn(index, CHANNEL_ACTIVE, channel.enabled.isOn() ? LED_ON : LED_OFF);
      sendNoteOn(index, CHANNEL_CROSSFADE_GROUP, channel.crossfadeGroup.getValuei());
      sendNoteOn(index, CHANNEL_SOLO, channel.cueActive.isOn() ? LED_ON : LED_OFF);
      sendNoteOn(index, CHANNEL_ARM, channel.arm.isOn() ? LED_ON : LED_OFF);
    } else {
      sendNoteOn(index, CHANNEL_ACTIVE, LED_OFF);
      sendNoteOn(index, CHANNEL_CROSSFADE_GROUP, LED_OFF);
      sendNoteOn(index, CHANNEL_SOLO, LED_OFF);
      sendNoteOn(index, CHANNEL_ARM, LED_OFF);
    }
    sendChannelPatterns(index, channel);
    sendChannelClips(index, channel);
  }

  private void sendChannelPatterns(int index, LXAbstractChannel channelBus) {
    if (!this.clipLaunchEnabled.isOn()) {
      return;
    }
    if (index >= CLIP_LAUNCH_COLUMNS || (this.gridMode != GridMode.PATTERN)) {
      return;
    }
    if (channelBus instanceof LXChannel) {
      final LXChannel channel = (LXChannel) channelBus;
      final boolean blendMode = channel.compositeMode.getEnum() == LXChannel.CompositeMode.BLEND;
      final int baseIndex = channel.controlSurfaceFocusIndex.getValuei();
      final int endIndex = channel.patterns.size() - baseIndex;
      final int activeIndex = channel.getActivePatternIndex() - baseIndex;
      final int nextIndex = channel.getNextPatternIndex() - baseIndex;
      final int focusedIndex = (channel.patterns.size() == 0) ? -1 : channel.focusedPattern.getValuei() - baseIndex;

      for (int y = 0; y < CLIP_LAUNCH_ROWS; ++y) {
        int pitch = CLIP_LAUNCH + y;
        int color = LED_OFF;
        if (blendMode) {
          if (y < endIndex) {
            if (channel.patterns.get(baseIndex + y).enabled.isOn()) {
              // Pattern is enabled!
              color = LED_GREEN;
            } else {
              // Pattern is present but off
              color = LED_RED;
            }
          }
        } else {
          if (y == activeIndex) {
            // This pattern is active (may also be focused)
            color = LED_GREEN;
          } else if (y == nextIndex) {
            // This pattern is being transitioned to
            color = LED_GREEN_BLINK;
          } else if (y == focusedIndex) {
            // This pattern is not active, but it is focused
            color = LED_RED;
          } else if (y < endIndex) {
            // There is a pattern present
            color = LED_YELLOW;
          }
        }

        sendNoteOn(index, pitch, color);
      }
    } else {
      for (int y = 0; y < CLIP_LAUNCH_ROWS; ++y) {
        sendNoteOn(index, CLIP_LAUNCH + y, LED_OFF);
      }
    }
  }

  private void sendChannelClips(int index, LXAbstractChannel channel) {
    if (!this.clipLaunchEnabled.isOn()) {
      return;
    }
    if (index >= CLIP_LAUNCH_COLUMNS || (this.gridMode != GridMode.CLIP)) {
      return;
    }
    for (int i = 0; i < CLIP_LAUNCH_ROWS; ++i) {
      int pitch = CLIP_LAUNCH + i;
      int color = LED_OFF;
      if (channel != null) {
        LXClip clip = channel.getClip(i);
        if (clip != null) {
          color = clip.isRunning() ? LED_GREEN : (channel.arm.isOn() ? LED_RED : LED_YELLOW);
        }
      }
      sendNoteOn(index, pitch, color);
    }
  }

  private final LXMixerEngine.Listener mixerEngineListener = new LXMixerEngine.Listener() {
    @Override
    public void channelRemoved(LXMixerEngine mixer, LXAbstractChannel channel) {
      unregisterChannel(channel);
      sendChannels();
    }

    @Override
    public void channelMoved(LXMixerEngine mixer, LXAbstractChannel channel) {
      sendChannels();
    }

    @Override
    public void channelAdded(LXMixerEngine mixer, LXAbstractChannel channel) {
      sendChannels();
      registerChannel(channel);
    }
  };

  private final LXParameterListener cueAListener = p -> {
    sendNoteOn(0, DETAIL_VIEW, this.lx.engine.mixer.cueA.isOn() ? 1 : 0);
  };

  private final LXParameterListener cueBListener = p -> {
    sendNoteOn(0, REC_QUANTIZE, this.lx.engine.mixer.cueB.isOn() ? 1 : 0);
  };

  private final LXParameterListener tempoListener = p -> {
    sendNoteOn(0, METRONOME, this.lx.engine.tempo.enabled.isOn() ? LED_ON : LED_OFF);
  };

  private final LXParameterListener focusedChannelListener = p -> { sendChannelFocus(); };

  private void sendChannelFocus() {
    int focusedChannel = this.lx.engine.mixer.focusedChannel.getValuei();
    boolean masterFocused = (focusedChannel == this.lx.engine.mixer.channels.size());
    for (int i = 0; i < NUM_CHANNELS; ++i) {
      sendNoteOn(i, CHANNEL_FOCUS, (!masterFocused && (i == focusedChannel)) ? LED_ON : LED_OFF);
    }
    sendNoteOn(0, MASTER_FOCUS, masterFocused ? LED_ON : LED_OFF);
  }

  private boolean isRegistered = false;

  private void register() {

    this.isRegistered = true;

    this.deviceListener.focusedDevice.register();

    for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
      registerChannel(channel);
    }

    this.lx.engine.mixer.addListener(this.mixerEngineListener);
    this.lx.engine.mixer.focusedChannel.addListener(this.focusedChannelListener);
    this.lx.engine.mixer.cueA.addListener(this.cueAListener, true);
    this.lx.engine.mixer.cueB.addListener(this.cueBListener, true);
    this.lx.engine.tempo.enabled.addListener(this.tempoListener, true);
    sendGridMode();
  }

  private void unregister() {
    this.isRegistered = false;

    this.deviceListener.focusedDevice.unregister();

    for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
      unregisterChannel(channel);
    }

    this.lx.engine.mixer.removeListener(this.mixerEngineListener);
    this.lx.engine.mixer.focusedChannel.removeListener(this.focusedChannelListener);
    this.lx.engine.mixer.cueA.removeListener(this.cueAListener);
    this.lx.engine.mixer.cueB.removeListener(this.cueBListener);
    this.lx.engine.tempo.enabled.removeListener(this.tempoListener);

    clearChannelGrid();
  }

  private void registerChannel(LXAbstractChannel channel) {
    this.channelListeners.put(channel, new ChannelListener(channel));
  }

  private void unregisterChannel(LXAbstractChannel channel) {
    ChannelListener channelListener = this.channelListeners.remove(channel);
    if (channelListener != null) {
      channelListener.dispose();
    }
  }

  private LXAbstractChannel getChannel(int index) {
    if (index < this.lx.engine.mixer.channels.size()) {
      return this.lx.engine.mixer.channels.get(index);
    }
    return null;
  }

  private LXAbstractChannel getChannel(LXShortMessage message) {
    return getChannel(message.getChannel());
  }

  private void noteReceived(MidiNote note, boolean on) {
    int pitch = note.getPitch();

    // Global toggle messages
    switch (pitch) {
    case SHIFT:
      this.shiftOn = on;
      return;
    case METRONOME:
      if (on) {
        lx.engine.tempo.enabled.toggle();
      }
      return;
    case TAP_TEMPO:
      lx.engine.tempo.tap.setValue(on);
      return;
    case NUDGE_MINUS:
      lx.engine.tempo.nudgeDown.setValue(on);
      return;
    case NUDGE_PLUS:
      lx.engine.tempo.nudgeUp.setValue(on);
      return;
    }

    // Global momentary light-up buttons
    switch (pitch) {
    case CLIP_STOP:
    case SCENE_LAUNCH:
    case DEVICE_LEFT:
    case DEVICE_RIGHT:
    case PAN:
    case SEND_A:
    case SEND_B:
    case SEND_C:
      sendNoteOn(note.getChannel(), pitch, on ? LED_ON : LED_OFF);
      break;
    }
    if (pitch >= SCENE_LAUNCH && pitch <= SCENE_LAUNCH_MAX) {
      sendNoteOn(note.getChannel(), pitch, on ? LED_GREEN : LED_OFF);
    }
    if (!this.clipLaunchEnabled.isOn() && pitch >= CLIP_LAUNCH && pitch <= CLIP_LAUNCH_MAX ) {
      sendNoteOn(note.getChannel(), pitch, on ? LED_GREEN : LED_OFF);
    }

    // Global momentary
    if (on) {
      LXBus bus;
      switch (pitch) {
      case MASTER_FOCUS:
        lx.engine.mixer.selectChannel(lx.engine.mixer.masterBus);
        lx.engine.mixer.focusedChannel.setValue(lx.engine.mixer.channels.size());
        return;
      case BANK_SELECT_LEFT:
        this.lx.engine.mixer.focusedChannel.decrement(false);
        lx.engine.mixer.selectChannel(lx.engine.mixer.getFocusedChannel());
        return;
      case BANK_SELECT_RIGHT:
        this.lx.engine.mixer.focusedChannel.increment(false);
        lx.engine.mixer.selectChannel(lx.engine.mixer.getFocusedChannel());
        return;
      case BANK_SELECT_UP:
        bus = this.lx.engine.mixer.getFocusedChannel();
        if (bus instanceof LXChannel) {
          ((LXChannel) bus).focusedPattern.decrement(this.shiftOn ? CLIP_LAUNCH_ROWS : 1 , false);
        }
        return;
      case BANK_SELECT_DOWN:
        bus = this.lx.engine.mixer.getFocusedChannel();
        if (bus instanceof LXChannel) {
          ((LXChannel) bus).focusedPattern.increment(this.shiftOn ? CLIP_LAUNCH_ROWS : 1 , false);
        }
        return;
      case DETAIL_VIEW:
        this.lx.engine.mixer.cueA.toggle();
        return;
      case REC_QUANTIZE:
        this.lx.engine.mixer.cueB.toggle();
        return;
      case STOP_ALL_CLIPS:
        if (this.clipLaunchEnabled.isOn()) {
          this.lx.engine.clips.stopClips();
        }
        return;
      }

      if (pitch >= SCENE_LAUNCH && pitch <= SCENE_LAUNCH_MAX) {
        if (this.clipLaunchEnabled.isOn()) {
          this.lx.engine.clips.launchScene(pitch - SCENE_LAUNCH);
        }
        return;
      }

      if (pitch >= CLIP_LAUNCH && pitch <= CLIP_LAUNCH_MAX) {
        if (!this.clipLaunchEnabled.isOn()) {
          return;
        }

        int channelIndex = note.getChannel();
        int index = pitch - CLIP_LAUNCH;
        LXAbstractChannel channel = getChannel(channelIndex);
        if (channel != null) {
          if (this.gridMode == GridMode.PATTERN) {
            if (channel instanceof LXChannel) {
              LXChannel c = (LXChannel) channel;
              index += c.controlSurfaceFocusIndex.getValuei();
              if (index < c.getPatterns().size()) {
                c.focusedPattern.setValue(index);
                if (!this.shiftOn) {
                  if (c.compositeMode.getEnum() == LXChannel.CompositeMode.BLEND) {
                    c.patterns.get(index).enabled.toggle();
                  } else {
                    c.goPatternIndex(index);
                  }
                }
              }
            }
          } else if (this.gridMode == GridMode.CLIP) {
            LXClip clip = channel.getClip(index);
            if (clip == null) {
              clip = channel.addClip(index);
            } else {
              if (clip.isRunning()) {
                clip.stop();
              } else {
                clip.trigger();
                this.lx.engine.clips.setFocusedClip(clip);
              }
            }
          }
        }
        return;
      }
    }

    // Channel messages
    LXAbstractChannel channel = getChannel(note);
    if (channel == null) {
      // No channel, bail out
      return;
    }

    // From here on, we only handle button *presses* - not releases
    if (!on) {
      return;
    }

    switch (note.getPitch()) {
    case CHANNEL_ARM:
      channel.arm.toggle();
      return;
    case CHANNEL_ACTIVE:
      channel.enabled.toggle();
      return;
    case CHANNEL_SOLO:
      channel.cueActive.toggle();
      return;
    case CHANNEL_CROSSFADE_GROUP:
      if (this.shiftOn) {
        channel.blendMode.increment();
      } else {
        channel.crossfadeGroup.increment();
      }
      return;
    case CLIP_STOP:
      channel.stopClips();
      return;
    case CHANNEL_FOCUS:
      if (this.shiftOn) {
        if (channel instanceof LXChannel) {
          ((LXChannel) channel).autoCycleEnabled.toggle();
        }
      } else {
        this.lx.engine.mixer.focusedChannel.setValue(channel.getIndex());
        lx.engine.mixer.selectChannel(lx.engine.mixer.getFocusedChannel());
      }
      return;
    case CLIP_TRACK:
      toggleGridMode();
      return;
    case DEVICE_ON_OFF:
      this.deviceListener.onDeviceOnOff();
      return;
    case DEVICE_LEFT:
      this.deviceListener.focusedDevice.previousDevice();
      return;
    case DEVICE_RIGHT:
      this.deviceListener.focusedDevice.nextDevice();
      return;

    case PLAY:
      LXBus focusedChannel = this.lx.engine.mixer.getFocusedChannel();
      if (focusedChannel instanceof LXChannel) {
        LXChannel patternChannel = (LXChannel) focusedChannel;
        patternChannel.goPattern(patternChannel.getFocusedPattern());
      }
      return;

    case PAN:
    case SEND_A:
    case SEND_B:
    case SEND_C:
    case STOP:
    case RECORD:
      return;
    }

    LXMidiEngine.error("APC40 received unmapped note: " + note);
  }

  private void toggleGridMode() {
    if (this.gridMode == GridMode.PATTERN) {
      this.gridMode = GridMode.CLIP;
      sendChannelGrid();
    } else {
      this.gridMode = GridMode.PATTERN;
      sendChannelGrid();
    }
    sendGridMode();
  }

  private void sendGridMode() {
    sendNoteOn(0, CLIP_TRACK, this.gridMode == GridMode.CLIP ? LED_ON : LED_OFF);
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
    case CUE_LEVEL:
      if (this.shiftOn) {
        this.lx.engine.palette.color.primary.saturation.incrementValue(cc.getRelative());
      } else {
        this.lx.engine.palette.color.primary.hue.incrementValue(cc.getRelative(), true);
      }
      return;
    case CHANNEL_FADER:
      int channel = cc.getChannel();
      if (channel < this.lx.engine.mixer.channels.size()) {
        this.lx.engine.mixer.channels.get(channel).fader.setNormalized(cc.getNormalized());
      }
      return;
    case MASTER_FADER:
      if (this.masterFaderEnabled.isOn()) {
        this.lx.engine.mixer.masterBus.fader.setNormalized(cc.getNormalized());
      }
      return;
    case CROSSFADER:
      if (this.crossfaderEnabled.isOn()) {
        this.lx.engine.mixer.crossfader.setNormalized(cc.getNormalized());
      }
      return;
    }

    if (number >= DEVICE_KNOB && number <= DEVICE_KNOB_MAX) {
      this.deviceListener.onKnob(number - DEVICE_KNOB, cc.getNormalized());
      return;
    }

    if (number >= TRACK_KNOB && number <= TRACK_KNOB_MAX) {
      sendControlChange(cc.getChannel(), cc.getCC(), cc.getValue());
      return;
    }

    LXMidiEngine.error("APC40 unmapped CC: " + cc);
  }

  @Override
  public void dispose() {
    if (this.isRegistered) {
      unregister();
    }
    if (this.enabled.isOn()) {
      setApcMode(GENERIC_MODE);
    }
    this.deviceListener.dispose();
    super.dispose();
  }

}
