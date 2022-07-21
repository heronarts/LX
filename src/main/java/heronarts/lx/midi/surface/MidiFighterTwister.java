/**
 * Copyright 2021- Justin Belcher, Mark C. Slee, Heron Arts LLC
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
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.midi.LXMidiEngine;
import heronarts.lx.midi.LXMidiInput;
import heronarts.lx.midi.LXMidiOutput;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.DiscreteParameter.IncrementMode;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.utils.LXUtils;

public class MidiFighterTwister extends LXMidiSurface implements LXMidiSurface.Bidirectional {

  /* Use the Midi Fighter Utility from DJ Tech Tools to apply
   * these recommended settings to your midi controller:
   *
   * Global Settings:
   *   System MIDI Channel = 4
   *   Side Key Functions:
   *     Left Button 1 Function: CC Toggle
   *     Left Button 2 Function: Previous Bank
   *     Left Button 3 Function: CC Toggle
   *     Right Button 1 Function: CC Toggle
   *     Right Button 2 Function: Next Bank
   *     Right Button 3 Function: CC Toggle
   * Encoder Settings (click Multiple, select all encoders):
   *   Sensitivity: High Resolution
   *   Switch Action Type: CC Hold
   *   Encoder MIDI Type: ENC 3FH/41H
   *   Encoder Switch MIDI Settings:
   *     Switch MIDI Channel: 2
   *   Encoder Rotary MIDI Settings:
   *     Encoder MIDI Channel: 1
   */

  public static final String DEVICE_NAME = "Midi Fighter Twister";

  public enum MFTTarget {
    FOCUSCHANNEL,
    FOCUSCHANNELAUX
  }

  public final EnumParameter<MFTTarget> target =
    new EnumParameter<MFTTarget>("Target", MFTTarget.FOCUSCHANNEL)
    .setDescription("Target focusedChannel or focusedChannelAux. FocusedChannelAux is only visible in performance mode.");

  // MIDI Channels
  public static final int CHANNEL_ROTARY_ENCODER = 0;
  public static final int CHANNEL_SWITCH_AND_COLOR = 1;
  public static final int CHANNEL_ANIMATIONS_AND_BRIGHTNESS = 2;
  public static final int CHANNEL_SYSTEM = 3;
  public static final int CHANNEL_SHIFT = 4;
  public static final int CHANNEL_SEQUENCER = 7;

  // MIDI ControlChanges on knob-related channels
  public static final int DEVICE_KNOB = 0;
  public static final int DEVICE_KNOB_NUM = 64;
  public static final int DEVICE_KNOB_MAX = DEVICE_KNOB + DEVICE_KNOB_NUM;
  public static final int KNOB_DECREMENT_VERYFAST = 61;
  public static final int KNOB_DECREMENT_FAST = 62;
  public static final int KNOB_DECREMENT = 63;
  public static final int KNOB_INCREMENT = 65;
  public static final int KNOB_INCREMENT_FAST = 66;
  public static final int KNOB_INCREMENT_VERYFAST = 67;
  public static final int KNOB_TICKS_PER_DISCRETE_INCREMENT = 8;

  // MIDI ControlChanges on System channel
  public static final int BANK1 = 0;
  public static final int BANK2 = 1;
  public static final int BANK3 = 2;
  public static final int BANK4 = 3;

  public static final int BANK1_LEFT1 = 8;
  public static final int BANK1_LEFT2 = 9;
  public static final int BANK1_LEFT3 = 10;
  public static final int BANK1_RIGHT1 = 11;
  public static final int BANK1_RIGHT2 = 12;
  public static final int BANK1_RIGHT3 = 13;

  public static final int BANK2_LEFT1 = 14;
  public static final int BANK2_LEFT2 = 15;
  public static final int BANK2_LEFT3 = 16;
  public static final int BANK2_RIGHT1 = 17;
  public static final int BANK2_RIGHT2 = 18;
  public static final int BANK2_RIGHT3 = 19;

  public static final int BANK3_LEFT1 = 20;
  public static final int BANK3_LEFT2 = 21;
  public static final int BANK3_LEFT3 = 22;
  public static final int BANK3_RIGHT1 = 23;
  public static final int BANK3_RIGHT2 = 24;
  public static final int BANK3_RIGHT3 = 25;

  public static final int BANK4_LEFT1 = 26;
  public static final int BANK4_LEFT2 = 27;
  public static final int BANK4_LEFT3 = 28;
  public static final int BANK4_RIGHT1 = 29;
  public static final int BANK4_RIGHT2 = 30;
  public static final int BANK4_RIGHT3 = 31;

  // MIDI Notes on Color channel
  public static final int RGB_INACTIVE_COLOR = 0;
  public static final int RGB_ACTIVE_COLOR = 127;
  // To set RGB to any color use values 1-126
  public static final int RGB_BLUE = 1;
  public static final int RGB_GREEN = 50;
  public static final int RGB_RED = 80;

  // MIDI Notes on Animation channel
  public static final int RGB_ANIMATION_NONE = 0;
  public static final int RGB_TOGGLE_EVERY_8_BEATS = 1;
  public static final int RGB_TOGGLE_EVERY_4_BEATS = 2;
  public static final int RGB_TOGGLE_EVERY_2_BEATS = 3;
  public static final int RGB_TOGGLE_EVERY_BEAT = 4;
  public static final int RGB_TOGGLE_EVERY_HALF_BEAT = 5;
  public static final int RGB_TOGGLE_EVERY_QUARTER_BEAT = 6;
  public static final int RGB_TOGGLE_EVERY_EIGTH_BEAT = 7;
  public static final int RGB_TOGGLE_EVERY_SIXTEENTH_BEAT = 8;
  public static final int RGB_PULSE_EVERY_8_BEATS = 10;
  public static final int RGB_PULSE_EVERY_4_BEATS = 11;
  public static final int RGB_PULSE_EVERY_2_BEATS = 12;
  public static final int RGB_PULSE_EVERY_BEAT = 13;
  public static final int RGB_PULSE_EVERY_HALF_BEAT = 14;
  public static final int RGB_PULSE_EVERY_QUARTER_BEAT = 15;
  public static final int RGB_PULSE_EVERY_EIGTH_BEAT = 16;
  public static final int RGB_BRIGHTNESS_OFF = 17;
  public static final int RGB_BRIGHTNESS_MID = 32;
  public static final int RGB_BRIGHTNESS_MAX = 47;
  // Note: All values between 17 and 47 can be used for RGB brightness
  public static final int INDICATOR_ANIMATION_NONE = 48;
  public static final int INDICATOR_TOGGLE_EVERY_8_BEATS = 49;
  public static final int INDICATOR_TOGGLE_EVERY_4_BEATS = 50;
  public static final int INDICATOR_TOGGLE_EVERY_2_BEATS = 51;
  public static final int INDICATOR_TOGGLE_EVERY_BEAT = 52;
  public static final int INDICATOR_TOGGLE_EVERY_HALF_BEAT = 53;
  public static final int INDICATOR_TOGGLE_EVERY_QUARTER_BEAT = 54;
  public static final int INDICATOR_TOGGLE_EVERY_EIGTH_BEAT = 55;
  public static final int INDICATOR_TOGGLE_EVERY_SIXTEENTH_BEAT = 56;
  public static final int INDICATOR_PULSE_EVERY_8_BEATS = 57;
  public static final int INDICATOR_PULSE_EVERY_4_BEATS = 58;
  public static final int INDICATOR_PULSE_EVERY_2_BEATS = 59;
  public static final int INDICATOR_PULSE_EVERY_BEAT = 60;
  public static final int INDICATOR_PULSE_EVERY_HALF_BEAT = 61;
  public static final int INDICATOR_PULSE_EVERY_QUARTER_BEAT = 62;
  public static final int INDICATOR_PULSE_EVERY_EIGTH_BEAT = 63;
  public static final int INDICATOR_PULSE_EVERY_SIXTEENTH_BEAT = 64;
  public static final int INDICATOR_BRIGHTNESS_OFF = 65;
  public static final int INDICATOR_BRIGHTNESS_25 = 72;
  public static final int INDICATOR_BRIGHTNESS_MID = 80;
  public static final int INDICATOR_BRIGHTNESS_MAX = 95;
  // Note: All values between 65 and 95 can be used for Indicator brightness
  public static final int RAINBOW_CYCLE = 127;

  // MIDI Notes on System Channel
  public static final int BANK_OFF = 0;
  public static final int BANK_ON = 127;

  public final DiscreteParameter currentBank = new DiscreteParameter("Bank", BANK1, BANK1, BANK4 + 1);

  private final DeviceListener deviceListener = new DeviceListener();

  private class DeviceListener implements LXParameterListener {

    private LXDeviceComponent device = null;
    private LXEffect effect = null;
    private LXPattern pattern = null;
    private LXBus channel = null;

    private final LXListenableNormalizedParameter[] knobs =
      new LXListenableNormalizedParameter[DEVICE_KNOB_NUM];
    private final int[] knobTicks = new int[DEVICE_KNOB_NUM];
    private final int[] knobIncrementSize = new int[DEVICE_KNOB_NUM];

    DeviceListener() {
      for (int i = 0; i < this.knobs.length; ++i) {
        this.knobs[i] = null;
        this.knobTicks[i] = 0;
        this.knobIncrementSize[i] = 1;
      }
    }

    private void resend() {
      int knobColor = getKnobColor();
      for (int i = 0; i < this.knobs.length; ++i) {
        LXListenableNormalizedParameter parameter = this.knobs[i];
        if (parameter != null) {
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_ANIMATION_NONE);
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_ANIMATION_NONE);
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_BRIGHTNESS_MAX);
          double normalized = (parameter instanceof CompoundParameter) ?
            ((CompoundParameter) parameter).getBaseNormalized() :
            parameter.getNormalized();
          sendControlChange(CHANNEL_ROTARY_ENCODER, DEVICE_KNOB + i, (int) (normalized * 127));
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_BRIGHTNESS_MAX);
          sendControlChange(CHANNEL_SWITCH_AND_COLOR, DEVICE_KNOB + i, knobColor);
        } else {
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_ANIMATION_NONE);
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_ANIMATION_NONE);
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_BRIGHTNESS_25);
          sendControlChange(CHANNEL_ROTARY_ENCODER, DEVICE_KNOB+i, 0);
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_BRIGHTNESS_OFF);
        }
      }
    }

    private void registerChannel(LXBus channel) {
      unregisterChannel();
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

    private void registerPrevious() {
      if (this.effect != null) {
        int effectIndex = this.effect.getIndex();
        if (effectIndex > 0) {
          register(this.effect.getBus().getEffect(effectIndex - 1));
        } else if (this.channel instanceof LXChannel) {
          register(((LXChannel) this.channel).getFocusedPattern());
        }
      }
    }

    private void registerNext() {
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

    private int getKnobColor() {
      switch (target.getEnum()) {
        case FOCUSCHANNELAUX:
          return RGB_RED;
        case FOCUSCHANNEL:
        default:
          return RGB_BLUE;
      }
    }

    private void register(LXDeviceComponent device) {
      if (this.device != device) {
        unregister();
        this.device = device;
        if (this.device instanceof LXEffect) {
          this.effect = (LXEffect) this.device;
          this.effect.enabled.addListener(this);
        } else if (this.device instanceof LXPattern) {
          this.pattern = (LXPattern) this.device;
        }

        int i = 0;
        if (this.device != null) {
          int knobColor = getKnobColor();
          for (LXListenableNormalizedParameter parameter : this.device.getRemoteControls()) {
            if (i >= this.knobs.length) {
              break;
            }
            this.knobs[i] = parameter;
            this.knobTicks[i] = 0;
            if (parameter != null) {
              // We will track an absolute knob value for Normalized DiscreteParameters even though MFT sends relative CCs.
              if (parameter instanceof DiscreteParameter && ((DiscreteParameter)parameter).getIncrementMode() == IncrementMode.NORMALIZED) {
                this.knobTicks[i] = (int) (parameter.getNormalized() * 127);
                this.knobIncrementSize[i] = LXUtils.max(1, 128/((DiscreteParameter)parameter).getRange());
              }
              parameter.addListener(this);
              sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_ANIMATION_NONE);
              sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_BRIGHTNESS_MAX);
              double normalized = (parameter instanceof CompoundParameter) ?
                ((CompoundParameter) parameter).getBaseNormalized() :
                parameter.getNormalized();
              sendControlChange(CHANNEL_ROTARY_ENCODER, DEVICE_KNOB + i, (int) (normalized * 127));
              sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_BRIGHTNESS_MAX);
              if (parameter instanceof CompoundParameter && ((CompoundParameter)parameter).modulations.size()>0) {
                sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_PULSE_EVERY_2_BEATS);
              } else {
                sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_ANIMATION_NONE);
                //sendControlChange(CHANNEL_SWITCH_AND_COLOR, DEVICE_KNOB + i, 50);
              }
            } else {
              sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_ANIMATION_NONE);
              sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_ANIMATION_NONE);
              sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_BRIGHTNESS_25);
              sendControlChange(CHANNEL_ROTARY_ENCODER, DEVICE_KNOB+i, 0);
              sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_BRIGHTNESS_OFF);
            }
            sendControlChange(CHANNEL_SWITCH_AND_COLOR, DEVICE_KNOB + i, knobColor);
            ++i;
          }
          this.device.controlSurfaceSemaphore.increment();
        }
        while (i < this.knobs.length) {
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_ANIMATION_NONE);
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_ANIMATION_NONE);
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_BRIGHTNESS_25);
          sendControlChange(CHANNEL_ROTARY_ENCODER, DEVICE_KNOB+i, 0);
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_BRIGHTNESS_OFF);
          ++i;
        }

      }
    }

    @Override
    public void onParameterChanged(LXParameter parameter) {
      if ((this.channel != null) &&
          (this.channel instanceof LXChannel) &&
          (parameter == ((LXChannel)this.channel).focusedPattern)) {
        if ((this.device == null) || (this.device instanceof LXPattern)) {
          register(((LXChannel) this.channel).getFocusedPattern());
        }
      } else if ((this.effect != null) && (parameter == this.effect.enabled)) {
        // No device on/off light on MFT
      } else {
        for (int i = 0; i < this.knobs.length; ++i) {
          if (parameter == this.knobs[i]) {
            double normalized = (parameter instanceof CompoundParameter) ?
              ((CompoundParameter) parameter).getBaseNormalized() :
              this.knobs[i].getNormalized();
            // Normalized DiscreteParameters need artificial tracking of absolute knob location.
            // Keep local tracking in sync with changes from other source.
            if (parameter instanceof DiscreteParameter && ((DiscreteParameter)parameter).getIncrementMode() == IncrementMode.NORMALIZED) {
              this.knobTicks[i] = (int) (normalized * 127);
            }
            sendControlChange(CHANNEL_ROTARY_ENCODER, DEVICE_KNOB + i, (int) (normalized * 127));
            break;
          }
        }
      }
    }

    private void onKnob(int index, double normalized) {
      if (this.knobs[index] != null) {
        this.knobs[index].setNormalized(normalized);
      }
    }

    private final static double KNOB_INCREMENT_AMOUNT = 1/128.;

    private void onKnobIncrement(int index, boolean isUp) {
      LXListenableNormalizedParameter knob = this.knobs[index];
      if (knob != null) {
        if (knob instanceof DiscreteParameter) {
          if (((DiscreteParameter)knob).getIncrementMode() ==  IncrementMode.NORMALIZED) {
            int value = this.knobTicks[index] + (isUp ? 1 : -1);
            if (knob.isWrappable()) {
              // Make the length of the wrap space the same as the length between other values on this parameter
              if (value < 0-this.knobIncrementSize[index] || value > 127+this.knobIncrementSize[index]) {
                value = value < 0 ? 127 : 0;
              }
              this.knobTicks[index] = value;
              value = LXUtils.constrain(value, 0, 127);
            } else {
              value = LXUtils.constrain(value, 0, 127);
              this.knobTicks[index] = value;
            }
            knob.setNormalized(value/128.);
          } else {
            // IncrementMode == RELATIVE
            // Move after a set number of ticks in the same direction
            if (isUp) {
              this.knobTicks[index] = LXUtils.max(this.knobTicks[index], 0) + 1;
            } else {
              this.knobTicks[index] = LXUtils.min(this.knobTicks[index], 0) - 1;
            }
            if (this.knobTicks[index] == KNOB_TICKS_PER_DISCRETE_INCREMENT * (isUp ? 1 : -1)) {
              this.knobTicks[index] = 0;
              if (isUp) {
                ((DiscreteParameter)knob).increment();
              } else {
                ((DiscreteParameter)knob).decrement();
              }
            }
          }
        } else {
          knob.incrementNormalized(KNOB_INCREMENT_AMOUNT * (isUp ? 1 : -1));
        }
      }
    }

    private void onSwitch(int index, boolean isPressed) {
      if (this.knobs[index] != null) {
        LXListenableNormalizedParameter p = this.knobs[index];
        if (p instanceof BooleanParameter) {
          // Toggle or momentary press boolean as appropriate
          BooleanParameter bp = (BooleanParameter)p;
          if (bp.getMode() == BooleanParameter.Mode.MOMENTARY) {
            bp.setValue(isPressed);
          } else {
            if (isPressed) {
              bp.toggle();
            }
          }
        } else if (p instanceof DiscreteParameter) {
          // Enum,Integer,etc will be incremented on click
          if (isPressed) {
            ((DiscreteParameter)p).increment();
          }
        } else {
          // Set other parameter types to default value on click
          p.reset();
        }
      }
    }

    private void unregister() {
      if (this.effect != null) {
        this.effect.enabled.removeListener(this);
      }
      if (this.device != null) {
        for (int i = 0; i < this.knobs.length; ++i) {
          if (this.knobs[i] != null) {
            this.knobs[i].removeListener(this);
            this.knobs[i] = null;
            this.knobTicks[i] = 0;
            this.knobIncrementSize[i] = 1;
          }
        }
        this.device.controlSurfaceSemaphore.decrement();
      }
      this.pattern = null;
      this.effect = null;
      this.device = null;
    }

    private void unregisterChannel() {
      if (this.channel != null) {
        if (this.channel instanceof LXChannel) {
          ((LXChannel) this.channel).focusedPattern.removeListener(this);
        }
      }
      this.channel = null;
    }

    private void dispose() {
      unregister();
      unregisterChannel();
    }
  }

  private void updateBank(int bank) {
    this.currentBank.setValue(bank);
  }

  private void incrementTarget() {
    this.target.increment();
  }

  private void decrementTarget() {
    this.target.decrement();
  }

  public MidiFighterTwister(LX lx, LXMidiInput input, LXMidiOutput output) {
      super(lx, input, output);

      this.target.addListener(new LXParameterListener() {
        @Override
        public void onParameterChanged(LXParameter parameter) {
          onTargetChanged(target.getEnum());
        }
      });
  }

  private void onTargetChanged(MFTTarget target) {
    System.out.println("MFT Target: " + target);
    registerChannelByTarget();
    this.deviceListener.resend();
  }

  @Override
  protected void onEnable(boolean on) {
    if (on) {
      initialize(false);
      register();
    } else {
      this.deviceListener.register(null);
      for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
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
    if (!reconnect) {
      // Move MFT to first bank
      sendControlChange(CHANNEL_SYSTEM, BANK1, BANK_ON);

      for (int i = 0; i < DEVICE_KNOB_NUM; ++i) {
        sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_ANIMATION_NONE);
        sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_ANIMATION_NONE);
        sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_BRIGHTNESS_MAX);
        // Set indicator (dial) to lowest level
        sendControlChange(CHANNEL_ROTARY_ENCODER, DEVICE_KNOB+i, 0);
        sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_BRIGHTNESS_OFF);
      }
    }
  }

  private final LXParameterListener focusedChannelListener = (p) -> {
    if (this.target.getEnum() == MFTTarget.FOCUSCHANNEL ) {
      this.deviceListener.registerChannel(this.lx.engine.mixer.getFocusedChannel());
    }
    };

  private final LXParameterListener focusedChannelAuxListener = (p) -> {
    if (this.target.getEnum() == MFTTarget.FOCUSCHANNELAUX ) {
      this.deviceListener.registerChannel(this.lx.engine.mixer.getFocusedChannelAux());
    }
    };

  private boolean isRegistered = false;

  private void register() {
    isRegistered = true;

    this.lx.engine.mixer.focusedChannel.addListener(this.focusedChannelListener);
    this.lx.engine.mixer.focusedChannelAux.addListener(this.focusedChannelAuxListener);

    registerChannelByTarget();
  }

  private void registerChannelByTarget() {
    switch (this.target.getEnum()) {
      case FOCUSCHANNELAUX:
        this.deviceListener.registerChannel(this.lx.engine.mixer.getFocusedChannelAux());
        return;
      case FOCUSCHANNEL:
      default:
        this.deviceListener.registerChannel(this.lx.engine.mixer.getFocusedChannel());
        return;
    }
  }

  private void unregister() {
    isRegistered = false;

    this.lx.engine.mixer.focusedChannel.removeListener(this.focusedChannelListener);
    this.lx.engine.mixer.focusedChannelAux.removeListener(this.focusedChannelAuxListener);
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
    int channel = cc.getChannel();
    int number = cc.getCC();
    int value = cc.getValue();

    switch (channel) {
      case CHANNEL_ROTARY_ENCODER:
        if (number >= DEVICE_KNOB && number <= DEVICE_KNOB_MAX) {
          int iKnob = number - DEVICE_KNOB;
          if (value == KNOB_INCREMENT || value == KNOB_INCREMENT_FAST || value == KNOB_INCREMENT_VERYFAST) {
            this.deviceListener.onKnobIncrement(iKnob, true);
          } else if (value == KNOB_DECREMENT || value == KNOB_DECREMENT_FAST || value == KNOB_DECREMENT_VERYFAST) {
            this.deviceListener.onKnobIncrement(iKnob, false);
          } else {
            // Knob sent absolute values but software is expecting relative values
            LXMidiEngine.error("MFT Encoder MIDI Type should be ENC 3FH/41H for encoder " + number + ". Received value " + value);
            // Let it through just to be nice
            this.deviceListener.onKnob(iKnob, cc.getNormalized());
          }
          return;
        }
        LXMidiEngine.error("MFT Unknown Knob: " + number);
        break;
      case CHANNEL_SWITCH_AND_COLOR:
        if (number >= DEVICE_KNOB && number <= DEVICE_KNOB_MAX) {
            this.deviceListener.onSwitch(number - DEVICE_KNOB, cc.getNormalized() > 0);
            return;
          }
        LXMidiEngine.error("MFT Unknown Switch: " + number);
        break;
      case CHANNEL_SYSTEM:
        switch (number) {
          case BANK1:
          case BANK2:
          case BANK3:
          case BANK4:
            if (value == BANK_ON) {
              updateBank(number);
            }
            return;
          case BANK1_LEFT1:
          case BANK2_LEFT1:
          case BANK3_LEFT1:
          case BANK4_LEFT1:
            this.decrementTarget();
            return;
          case BANK1_RIGHT1:
          case BANK2_RIGHT1:
          case BANK3_RIGHT1:
          case BANK4_RIGHT1:
            this.incrementTarget();
            return;
          case BANK1_LEFT2:
          case BANK2_LEFT2:
          case BANK3_LEFT2:
          case BANK4_LEFT2:
          case BANK1_LEFT3:
          case BANK2_LEFT3:
          case BANK3_LEFT3:
          case BANK4_LEFT3:
            this.deviceListener.registerPrevious();
            return;
          case BANK1_RIGHT2:
          case BANK2_RIGHT2:
          case BANK3_RIGHT2:
          case BANK4_RIGHT2:
          case BANK1_RIGHT3:
          case BANK2_RIGHT3:
          case BANK3_RIGHT3:
          case BANK4_RIGHT3:
            this.deviceListener.registerNext();
            return;
          default:
            LXMidiEngine.error("Unrecognized midi number " + number + " on system channel from MFT. Check your configuration with Midifighter Utility.");
            return;
        }
      default:
          LXMidiEngine.error("Unrecognized midi channel " + channel + " from MFT. Check your configuration with Midifighter Utility.");
          break;
    }
  }

  private void noteReceived(MidiNote note, boolean on) {
    LXMidiEngine.error("MFT UNMAPPED Note: " + note + " " + on);
  }

  @Override
  public void dispose() {
    if (this.isRegistered) {
      unregister();
    }
    this.deviceListener.dispose();
    super.dispose();
  }

}
