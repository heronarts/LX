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

import java.util.HashMap;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.LXBus;
import heronarts.lx.LXChannel;
import heronarts.lx.LXComponent;
import heronarts.lx.LXEffect;
import heronarts.lx.LXEngine;
import heronarts.lx.LXPattern;
import heronarts.lx.clip.LXClip;
import heronarts.lx.midi.LXMidiInput;
import heronarts.lx.midi.LXMidiOutput;
import heronarts.lx.midi.LXShortMessage;
import heronarts.lx.midi.MidiAftertouch;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.midi.MidiPitchBend;
import heronarts.lx.midi.MidiProgramChange;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;

public class APC40Mk2 extends LXMidiSurface {

  public static final byte GENERIC_MODE = 0x40;
  public static final byte ABLETON_MODE = 0x41;
  public static final byte ABLETON_ALTERNATE_MODE = 0x42;

  protected static final int LED_STYLE_OFF = 0;
  protected static final int LED_STYLE_SINGLE = 1;
  protected static final int LED_STYLE_UNIPOLAR = 2;
  protected static final int LED_STYLE_BIPOLAR = 3;

  public static final int NUM_CHANNELS = 8;
  public static final int CLIP_LAUNCH_ROWS = 5;
  public static final int CLIP_LAUNCH_COLUMNS = NUM_CHANNELS;

  // CCs
  public static final int CHANNEL_FADER = 7;
  public static final int TEMPO = 13;
  public static final int MASTER_FADER = 14;
  public static final int CROSSFADER = 15;
  public static final int CUE_LEVEL = 47;

  public static final int DEVICE_KNOB = 16;
  public static final int DEVICE_KNOB_NUM = 8;
  public static final int DEVICE_KNOB_MAX = DEVICE_KNOB + DEVICE_KNOB_NUM;

  public static final int DEVICE_KNOB_STYLE = 24;
  public static final int DEVICE_KNOB_STYLE_MAX = DEVICE_KNOB_STYLE + DEVICE_KNOB_NUM;

  public static final int CHANNEL_KNOB = 48;
  public static final int CHANNEL_KNOB_NUM = 8;
  public static final int CHANNEL_KNOB_MAX = CHANNEL_KNOB + CHANNEL_KNOB_NUM - 1;

  public static final int CHANNEL_KNOB_STYLE = 56;
  public static final int CHANNEL_KNOB_STYLE_MAX = CHANNEL_KNOB_STYLE + CHANNEL_KNOB_NUM;

  // Notes
  public static final int CLIP_LAUNCH = 0;
  public static final int CLIP_LAUNCH_NUM = 40;
  public static final int CLIP_LAUNCH_MAX = CLIP_LAUNCH + CLIP_LAUNCH_NUM - 1;

  public static final int CHANNEL_ARM = 48;
  public static final int CHANNEL_SOLO = 49;
  public static final int CHANNEL_ACTIVE = 50;
  public static final int CHANNEL_FOCUS = 51;
  public static final int CLIP_STOP = 52;

  public static final int DEVICE_LEFT = 58;
  public static final int DEVICE_RIGHT = 59;
  public static final int BANK_LEFT = 60;
  public static final int BANK_RIGHT = 61;

  public static final int DEVICE_ON_OFF = 62;
  public static final int DEVICE_LOCK = 63;
  public static final int CLIP_DEVICE_VIEW = 64;
  public static final int DETAIL_VIEW = 65;

  public static final int CHANNEL_CROSSFADE_GROUP = 66;
  public static final int MASTER_FOCUS = 80;

  public static final int STOP_ALL_CLIPS = 81;
  public static final int SCENE_LAUNCH = 82;
  public static final int SCENE_LAUNCH_NUM = 5;
  public static final int SCENE_LAUNCH_MAX = SCENE_LAUNCH + SCENE_LAUNCH_NUM - 1;

  public static final int PAN = 87;
  public static final int SENDS = 88;
  public static final int USER = 89;

  public static final int PLAY = 91;
  public static final int RECORD = 93;
  public static final int SESSION = 102;

  public static final int BANK_SELECT_UP = 94;
  public static final int BANK_SELECT_DOWN = 95;
  public static final int BANK_SELECT_RIGHT = 96;
  public static final int BANK_SELECT_LEFT = 97;

  public static final int SHIFT = 98;

  public static final int METRONOME = 90;
  public static final int TAP_TEMPO = 99;
  public static final int NUDGE_MINUS = 100;
  public static final int NUDGE_PLUS = 101;

  public static final int BANK = 103;

  // LED color + mode definitions
  public static final int LED_OFF = 0;
  public static final int LED_ON = 1;
  public static final int LED_GRAY = 2;
  public static final int LED_RED = 120;
  public static final int LED_RED_HALF = 121;
  public static final int LED_GREEN = 122;
  public static final int LED_GREEN_HALF = 123;
  public static final int LED_YELLOW = 124;
  public static final int LED_AMBER = 126;

  public static final int LED_MODE_PRIMARY = 0;
  public static final int LED_MODE_PULSE = 10;
  public static final int LED_MODE_BLINK = 15;

  private boolean shiftOn = false;
  private boolean bankOn = true;

  private final Map<LXChannel, ChannelListener> channelListeners = new HashMap<LXChannel, ChannelListener>();

  private final DeviceListener deviceListener = new DeviceListener();

  private class DeviceListener implements LXParameterListener {

    private LXComponent device = null;
    private LXEffect effect = null;
    private LXPattern pattern = null;
    private LXBus channel = null;

    private final LXListenableNormalizedParameter[] knobs =
      new LXListenableNormalizedParameter[DEVICE_KNOB_NUM];

    DeviceListener() {
      for (int i = 0; i < this.knobs.length; ++i) {
        this.knobs[i] = null;
      }
    }

    void registerChannel(LXBus channel) {
      if (this.channel != null) {
        if (this.channel instanceof LXChannel) {
          ((LXChannel) this.channel).focusedPattern.removeListener(this);
        }
      }
      this.channel = channel;
      if (channel instanceof LXChannel) {
        ((LXChannel) channel).focusedPattern.addListener(this);
        register(((LXChannel) channel).getFocusedPattern());
      } else if (channel.effects.size() > 0) {
        register(channel.getEffect(0));
      } else {
        register(null);
      }
    }

    void registerPrevious() {
      if (this.effect != null) {
        int effectIndex = this.effect.getIndex();
        if (effectIndex > 0) {
          register(this.effect.getBus().getEffect(effectIndex - 1));
        } else if (this.channel instanceof LXChannel) {
          register(((LXChannel) this.channel).getFocusedPattern());
        }
      }
    }

    void registerNext() {
      if (this.effect != null) {
        int effectIndex = this.effect.getIndex();
        if (effectIndex < this.effect.getBus().effects.size() - 1) {
          register(this.effect.getBus().getEffect(effectIndex + 1));
        }
      } else if (this.pattern != null) {
        if (channel.effects.size() > 0) {
          register(channel.getEffect(0));
        }
      }
    }

    void register(LXComponent device) {
      if (this.device != device) {
        if (this.effect != null) {
          this.effect.enabled.removeListener(this);
        }
        if (this.device != null) {
          for (int i = 0; i < this.knobs.length; ++i) {
            if (this.knobs[i] != null) {
              this.knobs[i].removeListener(this);
            }
          }
          this.device.controlSurfaceSemaphore.decrement();
        }
        this.pattern = null;
        this.effect = null;
        this.device = device;
        if (this.device instanceof LXEffect) {
          this.effect = (LXEffect) this.device;
          this.effect.enabled.addListener(this);
        } else if (this.device instanceof LXPattern) {
          this.pattern = (LXPattern) this.device;
        }

        int i = 0;
        boolean isEnabled = false;
        if (this.device != null) {
          if (this.effect != null) {
            isEnabled = this.effect.isEnabled();
          } else if (this.pattern != null) {
            isEnabled = this.pattern == ((LXChannel) this.channel).getActivePattern();
          }
          for (LXParameter p : this.device.getParameters()) {
            if (i >= this.knobs.length) {
              break;
            }
            if (p instanceof BoundedParameter || p instanceof DiscreteParameter) {
              LXListenableNormalizedParameter parameter = (LXListenableNormalizedParameter) p;
              this.knobs[i] = parameter;
              parameter.addListener(this);
              sendControlChange(0, DEVICE_KNOB_STYLE + i, p.getPolarity() == LXParameter.Polarity.BIPOLAR ? LED_STYLE_BIPOLAR : LED_STYLE_UNIPOLAR);
              double normalized = (parameter instanceof CompoundParameter) ?
                ((CompoundParameter) parameter).getBaseNormalized() :
                parameter.getNormalized();
              sendControlChange(0, DEVICE_KNOB + i, (int) (normalized * 127));
              ++i;
            }
          }
          this.device.controlSurfaceSemaphore.increment();
        }
        sendNoteOn(0, DEVICE_ON_OFF, isEnabled ? LED_ON : LED_OFF);
        while (i < this.knobs.length) {
          sendControlChange(0, DEVICE_KNOB_STYLE + i, LED_STYLE_OFF);
          ++i;
        }
      }
    }

    @Override
    public void onParameterChanged(LXParameter parameter) {
      if (this.channel != null && this.channel instanceof LXChannel && parameter == ((LXChannel)this.channel).focusedPattern) {
        if (this.device instanceof LXPattern) {
          register(((LXChannel)this.channel).getFocusedPattern());
        }
      } else if (this.effect != null && parameter == this.effect.enabled) {
        sendNoteOn(0, DEVICE_ON_OFF, this.effect.enabled.isOn() ? LED_ON : LED_OFF);
      } else {
        for (int i = 0; i < this.knobs.length; ++i) {
          if (parameter == this.knobs[i]) {
            double normalized = (parameter instanceof CompoundParameter) ?
              ((CompoundParameter) parameter).getBaseNormalized() :
              this.knobs[i].getNormalized();
            sendControlChange(0, DEVICE_KNOB + i, (int) (normalized * 127));
            break;
          }
        }
      }
    }

    void onDeviceOnOff() {
      if (this.pattern != null) {
        this.pattern.getChannel().goIndex(this.pattern.getIndex());
        sendNoteOn(0, DEVICE_ON_OFF, 1);
      } else if (this.effect != null) {
        this.effect.enabled.toggle();
      }
    }

    void onKnob(int index, double normalized) {
      if (this.knobs[index] != null) {
        this.knobs[index].setNormalized(normalized);
      }
    }


  }

  private class ChannelListener implements LXChannel.Listener, LXBus.ClipListener, LXParameterListener {

    private final LXChannel channel;

    ChannelListener(LXChannel channel) {
      this.channel = channel;
      this.channel.addListener(this);
      this.channel.addClipListener(this);
      this.channel.cueActive.addListener(this);
      this.channel.enabled.addListener(this);
      this.channel.crossfadeGroup.addListener(this);
      this.channel.arm.addListener(this);
      this.channel.focusedPattern.addListener(this);

      this.channel.controlSurfaceFocusLength.setValue(CLIP_LAUNCH_ROWS);
      int focusedPatternIndex = this.channel.getFocusedPatternIndex();
      this.channel.controlSurfaceFocusIndex.setValue(focusedPatternIndex < CLIP_LAUNCH_ROWS ? 0 : (focusedPatternIndex - CLIP_LAUNCH_ROWS + 1));
    }

    public void dispose() {
      this.channel.removeListener(this);
      this.channel.removeClipListener(this);
      this.channel.cueActive.removeListener(this);
      this.channel.enabled.removeListener(this);
      this.channel.crossfadeGroup.removeListener(this);
      this.channel.arm.removeListener(this);
      this.channel.focusedPattern.removeListener(this);
      for (LXClip clip : this.channel.clips) {
        if (clip != null) {
          clip.running.removeListener(this);
        }
      }
      this.channel.controlSurfaceFocusLength.setValue(0);
      this.channel.controlSurfaceFocusIndex.setValue(0);
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
      } else if (p == this.channel.focusedPattern) {
        int focusedPatternIndex = this.channel.getFocusedPatternIndex();
        int channelSurfaceIndex = this.channel.controlSurfaceFocusIndex.getValuei();
        if (focusedPatternIndex < channelSurfaceIndex) {
          this.channel.controlSurfaceFocusIndex.setValue(focusedPatternIndex);
        } else if (focusedPatternIndex >= channelSurfaceIndex + CLIP_LAUNCH_ROWS) {
          this.channel.controlSurfaceFocusIndex.setValue(focusedPatternIndex - CLIP_LAUNCH_ROWS + 1);
        }
        sendChannelPatterns(index, this.channel);
      } else if (p.getComponent() instanceof LXClip) {
        // TODO(mcslee): could be more efficient...
        sendChannelClips(index, this.channel);
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
    public void indexChanged(LXChannel channel) {
      // Handled by the engine channelMoved listener.
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

  public APC40Mk2(LX lx, LXMidiInput input, LXMidiOutput output) {
    super(lx, input, output);
  }

  @Override
  protected void onEnable(boolean on) {
    setApcMode(on ? ABLETON_ALTERNATE_MODE : GENERIC_MODE);
    if (on) {
      initialize();
      register();
    } else {
      this.deviceListener.register(null);
      for (LXChannel channel : this.lx.engine.channels) {
        channel.controlSurfaceFocusLength.setValue(0);
      }
    }
  }

  private void setApcMode(byte mode) {
    this.output.sendSysex(new byte[] {
      (byte) 0xf0, // sysex start
      0x47, // manufacturers id
      0x00, // device id
      0x29, // product model id
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

  private void initialize() {
    this.output.sendNoteOn(0, BANK, this.bankOn ? LED_ON : LED_OFF);
    for (int i = 0; i < CHANNEL_KNOB_NUM; ++i) {
      sendControlChange(0, CHANNEL_KNOB_STYLE+i, LED_STYLE_OFF);
    }
    for (int i = 0; i < DEVICE_KNOB_NUM; ++i) {
      sendControlChange(0, DEVICE_KNOB_STYLE+i, LED_STYLE_OFF);
    }
    for (int i = 0; i < CHANNEL_KNOB_NUM; ++i) {
      sendControlChange(0, CHANNEL_KNOB+i, 64);
      sendControlChange(0, CHANNEL_KNOB_STYLE+i, LED_STYLE_SINGLE);
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
      LXChannel channel = getChannel(i);
      sendChannelPatterns(i, channel);
      sendChannelClips(i, channel);
    }
  }

  private void sendChannel(int index, LXChannel channel) {
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

  private void sendChannelPatterns(int index, LXChannel channel) {
    if (index >= CLIP_LAUNCH_COLUMNS || !this.bankOn) {
      return;
    }
    int endIndex = -1, activeIndex = -1, nextIndex = -1, focusedIndex = -1;
    if (channel != null) {
      int baseIndex = channel.controlSurfaceFocusIndex.getValuei();
      endIndex = channel.patterns.size() - baseIndex;
      activeIndex = channel.getActivePatternIndex() - baseIndex;
      nextIndex = channel.getNextPatternIndex() - baseIndex;
      focusedIndex = channel.focusedPattern.getValuei() - baseIndex;
    }
    for (int y = 0; y < CLIP_LAUNCH_ROWS; ++y) {
      int note = CLIP_LAUNCH + CLIP_LAUNCH_COLUMNS * (CLIP_LAUNCH_ROWS - 1 - y) + index;
      int midiChannel = LED_MODE_PRIMARY;
      int color = LED_OFF;
      if (y == activeIndex) {
        color = 60;
      } else if (y == nextIndex) {
        sendNoteOn(LED_MODE_PRIMARY, note, 60);
        midiChannel = LED_MODE_PULSE;
        color = 9;
      } else if (y == focusedIndex) {
        color = 10;
      } else if (y < endIndex) {
        color = 117;
      }
      sendNoteOn(midiChannel, note, color);
    }
  }

  private void sendChannelClips(int index, LXChannel channel) {
    if (index >= CLIP_LAUNCH_COLUMNS || this.bankOn) {
      return;
    }
    for (int i = 0; i < CLIP_LAUNCH_ROWS; ++i) {
      int color = LED_OFF;
      int mode = LED_MODE_PRIMARY;
      if (channel != null) {
        int pitch = CLIP_LAUNCH + index + CLIP_LAUNCH_COLUMNS * (CLIP_LAUNCH_ROWS - 1 - i);
        LXClip clip = channel.getClip(i);
        if (clip != null) {
          color = channel.arm.isOn() ? LED_RED_HALF : LED_GRAY;
          if (clip.isRunning()) {
            color = channel.arm.isOn() ? LED_RED : LED_GREEN;
            sendNoteOn(LED_MODE_PRIMARY, pitch, color);
            mode = LED_MODE_PULSE;
            color = channel.arm.isOn() ? LED_RED_HALF : LED_GREEN_HALF;
          }
        }
        sendNoteOn(mode, pitch, color);
      }
    }
  }

  private void sendChannelFocus() {
    int focusedChannel = this.lx.engine.focusedChannel.getValuei();
    boolean masterFocused = (focusedChannel == this.lx.engine.channels.size());
    for (int i = 0; i < NUM_CHANNELS; ++i) {
      sendNoteOn(i, CHANNEL_FOCUS, (!masterFocused && (i == focusedChannel)) ? LED_ON : LED_OFF);
    }
    sendNoteOn(0, MASTER_FOCUS, masterFocused ? LED_ON : LED_OFF);
  }

  private boolean registered = false;

  private void register() {
    if (this.registered) {
      for (LXChannel channel : this.lx.engine.channels) {
        channel.controlSurfaceFocusLength.setValue(CLIP_LAUNCH_ROWS);
      }
      return;
    }
    for (LXChannel channel : this.lx.engine.channels) {
      registerChannel(channel);
    }
    this.lx.engine.addListener(new LXEngine.Listener() {

      @Override
      public void channelRemoved(LXEngine engine, LXChannel channel) {
        unregisterChannel(channel);
      }

      @Override
      public void channelMoved(LXEngine engine, LXChannel channel) {
        sendChannels();
      }

      @Override
      public void channelAdded(LXEngine engine, LXChannel channel) {
        sendChannels();
        registerChannel(channel);
      }
    });

    this.lx.engine.focusedChannel.addListener(new LXParameterListener() {
      public void onParameterChanged(LXParameter p) {
        sendChannelFocus();
        deviceListener.registerChannel(lx.engine.getFocusedChannel());
      }
    });

    deviceListener.registerChannel(this.lx.engine.getFocusedChannel());

    this.lx.engine.cueA.addListener(new LXParameterListener() {
      @Override
      public void onParameterChanged(LXParameter parameter) {
        sendNoteOn(0, CLIP_DEVICE_VIEW, lx.engine.cueA.isOn() ? 1 : 0);
      }
    });
    sendNoteOn(0, CLIP_DEVICE_VIEW, lx.engine.cueA.isOn() ? 1 : 0);

    this.lx.engine.cueB.addListener(new LXParameterListener() {
      @Override
      public void onParameterChanged(LXParameter parameter) {
        sendNoteOn(0, DETAIL_VIEW, lx.engine.cueB.isOn() ? 1 : 0);
      }
    });
    sendNoteOn(0, DETAIL_VIEW, lx.engine.cueB.isOn() ? 1 : 0);

    this.lx.tempo.enabled.addListener(new LXParameterListener() {
      public void onParameterChanged(LXParameter parameter) {
        sendNoteOn(0, METRONOME, lx.tempo.enabled.isOn() ? LED_ON : LED_OFF);
      }
    });
    sendNoteOn(0, METRONOME, lx.tempo.enabled.isOn() ? LED_ON : LED_OFF);

  }

  private void registerChannel(LXChannel channel) {
    ChannelListener channelListener = new ChannelListener(channel);
    this.channelListeners.put(channel, channelListener);
  }

  private void unregisterChannel(LXChannel channel) {
    ChannelListener channelListener = this.channelListeners.remove(channel);
    if (channelListener != null) {
      channelListener.dispose();
    }
  }

  private LXChannel getChannel(int index) {
    if (index < this.lx.engine.channels.size()) {
      return this.lx.engine.channels.get(index);
    }
    return null;
  }

  private LXChannel getChannel(LXShortMessage message) {
    return getChannel(message.getChannel());
  }

  private void noteReceived(MidiNote note, boolean on) {
    int pitch = note.getPitch();

    // Global toggle messages
    switch (pitch) {
    case SHIFT:
      this.shiftOn = on;
      return;
    case BANK:
      if (on) {
        this.bankOn = !this.bankOn;
        sendNoteOn(note.getChannel(), pitch, this.bankOn ? LED_ON : LED_OFF);
        sendChannelGrid();
      }
      return;
    case METRONOME:
      if (on) {
        lx.tempo.enabled.toggle();
      }
      return;
    case TAP_TEMPO:
      lx.tempo.tap.setValue(on);
      return;
    case NUDGE_MINUS:
      lx.tempo.nudgeDown.setValue(on);
      return;
    case NUDGE_PLUS:
      lx.tempo.nudgeUp.setValue(on);
      return;
    }

    // Global momentary light-up buttons
    switch (pitch) {
    case CLIP_STOP:
    case SCENE_LAUNCH:
      sendNoteOn(note.getChannel(), pitch, on ? LED_ON : LED_OFF);
      break;
    }
    if (pitch >= SCENE_LAUNCH && pitch <= SCENE_LAUNCH_MAX) {
      sendNoteOn(note.getChannel(), pitch, on ? LED_GREEN : LED_OFF);
    }

    // Global momentary
    if (on) {
      LXBus bus;
      switch (pitch) {
      case MASTER_FOCUS:
        lx.engine.focusedChannel.setValue(lx.engine.channels.size());
        return;
      case BANK_SELECT_LEFT:
        this.lx.engine.focusedChannel.decrement(false);
        return;
      case BANK_SELECT_RIGHT:
        this.lx.engine.focusedChannel.increment(false);
        return;
      case BANK_SELECT_UP:
        bus = this.lx.engine.getFocusedChannel();
        if (bus instanceof LXChannel) {
          ((LXChannel) bus).focusedPattern.decrement(this.shiftOn ? CLIP_LAUNCH_ROWS : 1 , false);
        }
        return;
      case BANK_SELECT_DOWN:
        bus = this.lx.engine.getFocusedChannel();
        if (bus instanceof LXChannel) {
          ((LXChannel) bus).focusedPattern.increment(this.shiftOn ? CLIP_LAUNCH_ROWS : 1 , false);
        }
        return;
      case CLIP_DEVICE_VIEW:
        this.lx.engine.cueA.toggle();
        return;
      case DETAIL_VIEW:
        this.lx.engine.cueB.toggle();
        return;
      case BANK:
        this.lx.engine.crossfaderBlendMode.increment();
        return;
      case STOP_ALL_CLIPS:
        this.lx.engine.stopClips();
        return;
      }

      if (pitch >= SCENE_LAUNCH && pitch <= SCENE_LAUNCH_MAX) {
        this.lx.engine.launchScene(pitch - SCENE_LAUNCH);
        return;
      }

      if (pitch >= CLIP_LAUNCH && pitch <= CLIP_LAUNCH_MAX) {
        int channelIndex = (pitch - CLIP_LAUNCH) % CLIP_LAUNCH_COLUMNS;
        int index = CLIP_LAUNCH_ROWS - 1 - ((pitch - CLIP_LAUNCH) / CLIP_LAUNCH_COLUMNS);
        LXChannel channel = getChannel(channelIndex);
        if (channel != null) {
          index += channel.controlSurfaceFocusIndex.getValuei();
          if (this.bankOn) {
            if (index < channel.getPatterns().size()) {
              channel.focusedPattern.setValue(index);
              if (!this.shiftOn) {
                channel.goIndex(index);
              }
            }
          } else {
            LXClip clip = channel.getClip(index);
            if (clip == null) {
              clip = channel.addClip(index);
            } else {
              if (clip.isRunning()) {
                clip.stop();
              } else {
                clip.trigger();
                this.lx.engine.focusedClip.setClip(clip);
              }
            }
          }
        }
        return;
      }
    }

    // Channel messages
    LXChannel channel = getChannel(note);
    if (channel != null) {
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
          channel.autoCycleEnabled.toggle();
        } else {
          this.lx.engine.focusedChannel.setValue(channel.getIndex());
        }
        return;
      case DEVICE_ON_OFF:
        this.deviceListener.onDeviceOnOff();
        return;
      case DEVICE_LEFT:
        this.deviceListener.registerPrevious();
        return;
      case DEVICE_RIGHT:
        this.deviceListener.registerNext();
        return;
      case BANK_LEFT:
        this.deviceListener.registerPrevious();
        return;
      case BANK_RIGHT:
        this.deviceListener.registerNext();
        return;
      }
    }

    System.out.println("APC40mk2 UNMAPPED: " + note);
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
    case TEMPO:
      if (this.shiftOn) {
        this.lx.tempo.adjustBpm(.1 * cc.getRelative());
      } else {
        this.lx.tempo.adjustBpm(cc.getRelative());
      }
      return;
    case CUE_LEVEL:
      if (this.shiftOn) {
        this.lx.palette.color.saturation.incrementValue(cc.getRelative());
      } else {
        this.lx.palette.color.hue.incrementValue(cc.getRelative(), true);
      }
      return;
    case CHANNEL_FADER:
      int channel = cc.getChannel();
      if (channel < this.lx.engine.channels.size()) {
        this.lx.engine.channels.get(channel).fader.setNormalized(cc.getNormalized());
      }
      return;
    case MASTER_FADER:
      this.lx.engine.output.brightness.setNormalized(cc.getNormalized());
      return;
    case CROSSFADER:
      this.lx.engine.crossfader.setNormalized(cc.getNormalized());
      return;
    }

    if (number >= DEVICE_KNOB && number <= DEVICE_KNOB_MAX) {
      this.deviceListener.onKnob(number - DEVICE_KNOB, cc.getNormalized());
      return;
    }

    if (number >= CHANNEL_KNOB && number <= CHANNEL_KNOB_MAX) {
      sendControlChange(cc.getChannel(), cc.getCC(), cc.getValue());
      return;
    }

    // System.out.println("APC40mk2 UNMAPPED: " + cc);
  }

  @Override
  public void programChangeReceived(MidiProgramChange pc) {
  }

  @Override
  public void pitchBendReceived(MidiPitchBend pitchBend) {
  }

  @Override
  public void aftertouchReceived(MidiAftertouch aftertouch) {
  }

}
