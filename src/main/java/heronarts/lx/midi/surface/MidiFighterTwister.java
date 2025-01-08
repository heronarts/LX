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

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.LXEngine;
import heronarts.lx.command.LXCommand;
import heronarts.lx.midi.LXMidiEngine;
import heronarts.lx.midi.LXMidiInput;
import heronarts.lx.midi.LXMidiOutput;
import heronarts.lx.midi.LXSysexMessage;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameter.Polarity;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.DiscreteParameter.IncrementMode;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.utils.LXUtils;

import static heronarts.lx.midi.LXSysexMessage.END_SYSEX;
import static heronarts.lx.midi.LXSysexMessage.GENERAL_INFORMATION;
import static heronarts.lx.midi.LXSysexMessage.IDENTITY_REPLY;
import static heronarts.lx.midi.LXSysexMessage.NON_REALTIME;
import static heronarts.lx.midi.LXSysexMessage.START_SYSEX;

@LXMidiSurface.Name("DJTT Midi Fighter Twister")
@LXMidiSurface.DeviceName("Midi Fighter Twister")
public class MidiFighterTwister extends LXMidiSurface implements LXMidiSurface.Bidirectional {

  // MIDI Channels
  public static final int CHANNEL_ROTARY_ENCODER = 0;
  public static final int CHANNEL_SWITCH_AND_COLOR = 1;
  public static final int CHANNEL_ANIMATIONS_AND_BRIGHTNESS = 2;
  public static final int CHANNEL_SYSTEM = 3;
  public static final int CHANNEL_SHIFT = 4;
  public static final int CHANNEL_SWITCH_ANIMATION = 5;
  public static final int CHANNEL_SEQUENCER = 7;

  // MIDI ControlChanges on knob-related channels
  public static final int DEVICE_KNOB = 0;
  public static final int DEVICE_KNOB_PER_BANK = 16;
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

  public static final int RGB_PRIMARY = RGB_BLUE;
  public static final int RGB_AUX = RGB_RED;
  public static final int RGB_USER = RGB_GREEN;

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

  public enum KnobClickMode {
    RESET("Reset"),
    TEMPORARY("Temporary Edit");

    private final String label;

    private KnobClickMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public enum FocusMode {
    DEVICE("Device"),
    CHANNEL("Channel");

    private final String label;

    private FocusMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final EnumParameter<KnobClickMode> knobClickMode =
    new EnumParameter<KnobClickMode>("Knob Click", KnobClickMode.RESET)
    .setDescription("How to edit parameters when a knob is pressed");

  public final EnumParameter<FocusMode> focusMode =
    new EnumParameter<FocusMode>("Focus Buttons", FocusMode.DEVICE)
    .setDescription("How to change focus on bottom side button press");

  public final DiscreteParameter currentBank =
    new DiscreteParameter("Bank", BANK1, BANK1, BANK4 + 1)
    .setDescription("Which bank is selected on the MFT");

  public final BooleanParameter isAux =
    new BooleanParameter("Aux", false)
    .setDescription("Whether this MFT controls the primary or aux channel");

  // SYSEX Definitions

  // DJTT MIDI Constants
  public static final byte MIDI_MFR_ID_0 = 0x00;
  public static final byte MIDI_MFR_ID_1 = 0x01;
  public static final byte MIDI_MFR_ID_2 = 0x79;
  // public static final byte MANUFACTURER_ID = 0x0179;

  // DJTT SysEx Commands
  public static final byte SYSEX_COMMAND_PUSH_CONF = 0x01;
  public static final byte SYSEX_COMMAND_PULL_CONF = 0x02;
  public static final byte SYSEX_COMMAND_SYSTEM = 0x03;
  public static final byte SYSEX_COMMAND_BULK_XFER = 0x04;

  // DJTT Config Sizes
  public static final int CFG_COUNT_ENC = 15;
  public static final int CFG_COUNT_GLOBAL = 12;

  // DJTT Config Values
  public static final byte CFG_FALSE = (byte)0x00;
  public static final byte CFG_TRUE = (byte)0x01;

  public static final byte CFG_GLOBAL_SSACTION_CCHOLD = (byte)0x00;
  public static final byte CFG_GLOBAL_SSACTION_CCTOGGLE = (byte)0x01;
  public static final byte CFG_GLOBAL_SSACTION_NOTEHOLD = (byte)0x02;
  public static final byte CFG_GLOBAL_SSACTION_NOTETOGGLE = (byte)0x03;
  public static final byte CFG_GLOBAL_SSACTION_SHIFTPAGE1 = (byte)0x04;
  public static final byte CFG_GLOBAL_SSACTION_SHIFTPAGE2 = (byte)0x05;
  public static final byte CFG_GLOBAL_SSACTION_BANKUP = (byte)0x06;
  public static final byte CFG_GLOBAL_SSACTION_BANKDOWN = (byte)0x07;
  public static final byte CFG_GLOBAL_SSACTION_BANK1 = (byte)0x08;
  public static final byte CFG_GLOBAL_SSACTION_BANK2 = (byte)0x09;
  public static final byte CFG_GLOBAL_SSACTION_BANK3 = (byte)0x0a;
  public static final byte CFG_GLOBAL_SSACTION_BANK4 = (byte)0x0b;
  public static final byte CFG_GLOBAL_SSACTION_CYCLE_BANK = (byte)0x0c;

  public static final byte CFG_ENC_CONTROLTYPE_ENCODER = 0x00;
  public static final byte CFG_ENC_CONTROLTYPE_SWITCH = 0x01;
  public static final byte CFG_ENC_CONTROLTYPE_SHIFT = 0x02;
  public static final byte CFG_ENC_MOVEMENTTYPE_DIRECT_HIGHRESOLUTION = 0x00;
  public static final byte CFG_ENC_MOVEMENTTYPE_EMULATION_RESPONSIVE = 0x01;
  public static final byte CFG_ENC_MOVEMENTTYPE_VELOCITYSENSITIVE = 0x02;
  public static final byte CFG_ENC_SWACTION_CCHOLD = 0x00;
  public static final byte CFG_ENC_SWACTION_CCTOGGLE = 0x01;
  public static final byte CFG_ENC_SWACTION_NOTEHOLD = 0x02;
  public static final byte CFG_ENC_SWACTION_NOTETOGGLE = 0x03;
  public static final byte CFG_ENC_SWACTION_ENCRESETVALUE = 0x04;
  public static final byte CFG_ENC_SWACTION_ENCFINEADJUST = 0x05;
  public static final byte CFG_ENC_SWACTION_SHIFTHOLD = 0x06;
  public static final byte CFG_ENC_SWACTION_SHIFTTOGGLE = 0x07;
  public static final byte CFG_ENC_MIDITYPE_SENDNOTE = 0x00;
  public static final byte CFG_ENC_MIDITYPE_SENDCC = 0x01;
  public static final byte CFG_ENC_MIDITYPE_SENDRELENC = 0x02;
  public static final byte CFG_ENC_MIDITYPE_SENDNOTEOFF = 0x03;
  public static final byte CFG_ENC_MIDITYPE_SENDSWITCHVELCONTROL = 0x03;
  public static final byte CFG_ENC_MIDITYPE_SENDRELENCMOUSEEMUDRAG = 0x04;
  public static final byte CFG_ENC_MIDITYPE_SENDRELENCMOUSEEMUSCROLL = 0x05;
  public static final byte CFG_ENC_INDICATORTYPE_DOT = 0x00;
  public static final byte CFG_ENC_INDICATORTYPE_BAR = 0x01;
  public static final byte CFG_ENC_INDICATORTYPE_BLENDEDBAR = 0x02;
  public static final byte CFG_ENC_INDICATORTYPE_BLENDEDDOT = 0x03;

  // In the rare case of old firmware version we'll set this to false and won't push sysex config
  private boolean versionOK = true;

  private class Config {

    private static final int CONFIG_PULL_TIMEOUT_MS = 1000;
    private static final int PART_SIZE_BYTES = 24;
    private static final int PULL_STEP_NONE = -3;
    private static final int PULL_STEP_IDENTITY = -2;
    private static final int PULL_STEP_GLOBAL = -1;
    private static final int PULL_STEP_FIRST_ENCODER = 0;
    private static final int NUM_PULL_ENCODERS = DEVICE_KNOB_NUM;

    private int pullStep = PULL_STEP_NONE;
    private Encoder pullingEncoder;
    private LXEngine.Timer pullTimer;

    private boolean initialized = false;
    private final Map<Byte, Byte> global = new LinkedHashMap<Byte, Byte>();
    private final Map<Byte, Byte> encoderBulkResponse = new LinkedHashMap<Byte, Byte>();
    private final Encoder[] encoders = new Encoder[DEVICE_KNOB_NUM];

    private class Encoder {

      private class Setting {

        private final byte address;
        private byte value;
        private boolean isModified;

        private Setting(byte address) {
          this.address = address;
          this.value = (byte) 0x0;
          this.isModified = false;
        }

        private boolean setValue(byte value) {
          if (this.value != value) {
            this.value = value;
            this.isModified = true;
          }
          return this.isModified;
        }

        private void resetModified() {
          this.isModified = false;
        }
      }

      private final int encoderIndex;
      private final byte sysexTag;

      private boolean isModified = false;

      // Keep list of all settings
      private final Map<String, Setting> settings =
        new LinkedHashMap<String, Setting>();
      private final Map<Byte, Setting> settingsByAddress =
        new LinkedHashMap<Byte, Setting>();

      private final Setting has_detent;

      private Setting addSetting(String name, int address) {
        Setting setting = new Setting((byte) address);
        this.settings.put(name, setting);
        this.settingsByAddress.put(setting.address, setting);
        return setting;
      }

      private Encoder(int encoderIndex) {
        this.encoderIndex = encoderIndex;
        this.sysexTag = (byte)(encoderIndex+1);

        this.has_detent = addSetting("has_detent", 10);
        addSetting("movement", 11);
        addSetting("switch_action_type", 12);
        addSetting("switch_midi_channel", 13);
        addSetting("switch_midi_number", 14);
        addSetting("switch_midi_type", 15);
        addSetting("encoder_midi_channel", 16);
        addSetting("encoder_midi_number", 17);
        addSetting("encoder_midi_type", 18);
        addSetting("active_color", 19);
        addSetting("inactive_color", 20);
        addSetting("detent_color", 21);
        addSetting("indicator_display_type", 22);
        addSetting("is_super_knob", 23);
        addSetting("encoder_shift_midi_channel", 24);
      }

      private void setDetent(boolean value) {
        set("has_detent", value ? CFG_TRUE : CFG_FALSE);
      }

      // set method is for internal use
      private void set(String setting, byte value) {
        this.isModified = this.settings.get(setting).setValue(value) || this.isModified;
      }

      private void send(boolean forceAll) {
        if (!this.isModified && !forceAll) {
          return;
        }

        ArrayList<Byte> configData = new ArrayList<Byte>();
        for (Setting setting : this.settings.values()) {
          if (setting.isModified || forceAll) {
            configData.add(setting.address);
            configData.add(setting.value);
          }
        }

        if (!configData.isEmpty()) {
          // Use MFT sysex Bulk Transfer protocol

          // Total number of bytes to transfer
          int bytesRemaining = configData.size();

          // Total number of parts in transfer - round up
          int total = (bytesRemaining + PART_SIZE_BYTES - 1) / PART_SIZE_BYTES;
          int iConfig = 0;

          for (int part=1; part<=total; part++) {
            // Size, in bytes, of current part
            int size = LXUtils.min(bytesRemaining, PART_SIZE_BYTES);
            bytesRemaining -= PART_SIZE_BYTES;

            byte[] payload = new byte[size+11];
            payload[0] = START_SYSEX;
            payload[1] = MIDI_MFR_ID_0;
            payload[2] = MIDI_MFR_ID_1;
            payload[3] = MIDI_MFR_ID_2;
            payload[4] = SYSEX_COMMAND_BULK_XFER;       // Command = bulk transfer
            payload[5] = 0x00;                          // 0x00 = push to MFT, 0x01 = pull from MFT
            payload[6] = sysexTag;                      // Encoder identifier
            payload[7] = (byte)part;                    // Part 'part' of 'total'
            payload[8] = (byte)total;
            payload[9] = (byte)size;                    // 24 bytes maximum size
            payload[payload.length-1] = END_SYSEX;

            // Copy the data into the payload
            for (int idx=10; idx < size+10; idx++) {
              payload[idx] = configData.get(iConfig++);
            }

            // LXMidiEngine.log("MFT Encoder sysex(" + this.encoderIndex + "): " + bytesToString(payload));
            sendSysex(payload);
          }
        }

        // If successfully sent, mark as not modified for next round
        this.isModified = false;
        for (Setting setting : this.settings.values()) {
          setting.resetModified();
        }
      }

      private void pull() {
        // Send Pull command for this encoder only
        byte[] payload = new byte[8];
        payload[0] = START_SYSEX;
        payload[1] = MIDI_MFR_ID_0;
        payload[2] = MIDI_MFR_ID_1;
        payload[3] = MIDI_MFR_ID_2;
        payload[4] = SYSEX_COMMAND_BULK_XFER;       // Command = bulk transfer
        payload[5] = (byte)0x01;                    // 0x00 = push to MFT, 0x01 = pull from MFT
        payload[6] = sysexTag;                      // Encoder identifier
        payload[7] = END_SYSEX;

        // LXMidiEngine.log("MFT Encoder sysex(" + this.encoderIndex + "): " + bytesToString(payload));
        sendSysex(payload);
      }

      /**
       * Receive all settings from a Sysex Bulk Pull response
       */
      private void setBulk(Map<Byte, Byte> bulkResponse) {
        Map<Byte, Setting> settingsCopy = new LinkedHashMap<Byte, Setting>(this.settingsByAddress);
        for (Map.Entry<Byte, Byte> bulkEntry : bulkResponse.entrySet()) {
          Byte address = bulkEntry.getKey();
          Byte value = bulkEntry.getValue();
          Setting setting = settingsCopy.remove(address);
          if (setting == null) {
            LXMidiEngine.error("MFT encoder pulled unknown setting. Is this a new firmware?");
            setting = addSetting("unknown", address);
          }
          setting.setValue(value);
          setting.resetModified();
        }
        // Safety check, unlikely except for a breaking change firmware
        for (Map.Entry<Byte, Setting> entry : settingsCopy.entrySet()) {
          LXMidiEngine.error("Known MFT encoder setting was not found in bulk pull: " + entry.getKey());
          entry.getValue().resetModified();
        }
        this.isModified = false;
      }
    }

    private Config() {
      initGlobal();
      for (int i=0; i<DEVICE_KNOB_NUM; i++) {
        this.encoders[i] = new Encoder(i);
      }
    }

    private void initGlobal() {
      // Init global settings array in correct order. Some will be loaded from the first encoder.
      this.global.clear();
      for (int g = 0; g <= 24; g++) {
        this.global.put((byte)g, (byte)0x00);
      }
      // Yes this gap matches the Midi Fighter Utility sysex
      this.global.put((byte)31, (byte)0x00);
      this.global.put((byte)32, (byte)0x00);
    }

    public boolean isPulling() {
      return this.pullStep != PULL_STEP_NONE;
    }

    /**
     * Pull config from midi surface
     */
    private void pull() {
      if (connected.isOn()) {
        // If a pull is in progress, then a reconnect happened before the pull finished. Cancel it.
        if (this.pullStep != PULL_STEP_NONE) {
          pullTimer.cancel();
          this.encoderBulkResponse.clear();
          this.pullingEncoder = null;
        }
        // Query current surface settings
        this.pullStep = PULL_STEP_IDENTITY;
        pullTimer = lx.engine.addTimeout(CONFIG_PULL_TIMEOUT_MS, this::timedOut);
        LXSysexMessage sysex = LXSysexMessage.newIdentityRequest();
        sendSysex(sysex.getMessage()); // gah, can't pass the sysex. fix on one side or the other.
      } else {
        // Either the input or output is disconnected. Sysex pull is not possible.
        initializeComplete();
      }
    }

    /**
     * Timeout was reached during sysex pull operation
     */
    private void timedOut() {
      LXMidiEngine.error("MFT config pull exceeded timeout (" + CONFIG_PULL_TIMEOUT_MS + "ms)");
      this.pullTimer = null;
      this.pullStep = PULL_STEP_NONE;
      this.encoderBulkResponse.clear();
      this.pullingEncoder = null;
      initializeComplete();
    }

    private void sysexReceived(LXSysexMessage sysex) {
      if (this.pullStep == PULL_STEP_IDENTITY) {
        if (handleIdentitySysex(sysex)) {
          pullNext();
        }
      } else if (this.pullStep == PULL_STEP_GLOBAL) {
        if (handleGlobalSysex(sysex)) {
          pullNext();
        }
      }
      else if (this.pullStep >= 0) {
        if (handleEncoderSysex(sysex)) {
          pullNext();
        }
      }
      // else not for us
    }

    private void pullNext() {
      this.pullStep++;
      if (this.pullStep == PULL_STEP_GLOBAL) {
        // Pull global settings
        this.pullTimer.reset();
        pullGlobal();
      } else if (this.pullStep < NUM_PULL_ENCODERS) {
        // Pull next encoder
        this.pullTimer.reset();
        this.pullingEncoder = this.encoders[this.pullStep];
        this.pullingEncoder.pull();
      } else {
        // Finished sysex pull
        this.pullTimer.cancel();
        this.pullTimer = null;
        this.pullStep = PULL_STEP_NONE;
        this.pullingEncoder = null;
        this.initialized = true;
        initializeComplete();
      }
    }

    private void pullGlobal() {
      byte[] payload = new byte[7];
      payload[0] = START_SYSEX;
      payload[1] = MIDI_MFR_ID_0;
      payload[2] = MIDI_MFR_ID_1;
      payload[3] = MIDI_MFR_ID_2;
      payload[4] = SYSEX_COMMAND_PULL_CONF;       // Command = pull configuration
      payload[5] = (byte)0x00;                    // Request
      payload[6] = END_SYSEX;
      sendSysex(payload);
    }

    /**
     * Process global sysex or return false if invalid
     */
    private boolean handleIdentitySysex(LXSysexMessage sysex) {
      if (sysex.getLength() < 17) {
        return false;
      }
      byte[] m = sysex.getMessage();
      if (m[0] != START_SYSEX ||
        m[1] != NON_REALTIME ||
        m[3] != GENERAL_INFORMATION ||
        m[4] != IDENTITY_REPLY
      ) {
        LXMidiEngine.error("Invalid sysex header for identity response");
        return false;
      }
      // m[2] = device ID
      if (m[5] != 0x00 ||
        m[6] != 0x01 ||
        m[7] != 0x79) {
        LXMidiEngine.error("Invalid manufacturer ID in sysex identity response");
        return false;
      }
      if (m[8] != 0x05 ||
        m[9] != 0x00) {
        LXMidiEngine.error("Device ID does not match MidiFighterTwister");
        return false;
      }
      // int majorVersion = m[10];
      // int minorVersion = m[11];
      int year = byteNumber(m[12]) * 100 + byteNumber(m[13]);
      // int month = byteNumber(m[14]);
      // int day = byteNumber(m[15]);
      versionOK = year > 2016;
      // LXMidiEngine.log(String.format("Found DJTT MidiFighterTwister, firmware %d-%02d-%02d", year, month, day));
      // Too bad they commented out the serial number for the next 4 bytes.
      return true;
    }

    private boolean handleGlobalSysex(LXSysexMessage sysex) {
      int length = sysex.getLength();
      if (length < 6) {
        return false;
      }
      byte[] m = sysex.getMessage();
      if (m[0] != START_SYSEX ||
        m[1] != MIDI_MFR_ID_0 ||
        m[2] != MIDI_MFR_ID_1 ||
        m[3] != MIDI_MFR_ID_2 ||
        m[4] != SYSEX_COMMAND_PULL_CONF ||
        m[5] != 0x01) {
        LXMidiEngine.error("Invalid sysex header for global config pull");
        return false;
      }
      initGlobal();
      for (int i = 6; i < length - 1; i += 2) {
        Byte address = m[i];
        Byte value = m[i + 1];
        this.global.put(address, value);
      }
      // Special: Settings 10 (0x0A) through 24 (0x18) will come from the first encoder
      return true;
    }

    /**
     * Process a sysex bulk transfer, returning true when all parts have arrived.
     */
    private boolean handleEncoderSysex(LXSysexMessage sysex) {
      if (sysex.getLength() < 11) {
        return false;
      }
      byte[] m = sysex.getMessage();
      if (m[0] != START_SYSEX ||
        m[1] != MIDI_MFR_ID_0 ||
        m[2] != MIDI_MFR_ID_1 ||
        m[3] != MIDI_MFR_ID_2 ||
        m[4] != SYSEX_COMMAND_BULK_XFER ||
        m[5] != 0x00) {
        // Warn, but otherwise ignore. This could be a response to another software.
        LXMidiEngine.error("Invalid sysex response header for encoder pull");
        return false;
      }
      byte tagEncoder = m[6];
      if (tagEncoder != this.pullingEncoder.sysexTag) {
        // This is not the encoder we are looking for
        LXMidiEngine.error(String.format("Sysex encoder response (%02X) did not match expected (%d)", tagEncoder, pullStep + 1));
        return false;
      }
      int part = m[7];
      int totalParts = m[8];
      int payloadSize = m[9];
      // Extract all setting pairs from this payload
      for (int i = 0; i < payloadSize - 1; i += 2) {
        Byte address = m[10 + i];
        Byte value = m[11 + i];
        if (!this.encoderBulkResponse.containsKey(address)) {
          this.encoderBulkResponse.put(address, value);
        } else {
          LXMidiEngine.error("MFT sysex encoder response contained duplicate setting: " + address);
        }
      }
      if (part == totalParts) {
        // All message parts received. Pass to encoder.
        Encoder encoder = this.encoders[pullStep];
        encoder.setBulk(this.encoderBulkResponse);
        if (pullStep == PULL_STEP_FIRST_ENCODER) {
          // Special: When writing config to device, the command to write global settings needs
          // to also include the first encoder's settings. To prepare for this we'll append the
          // first encoder settings to the list of global settings here.
          for (Map.Entry<Byte, Byte> entry : this.encoderBulkResponse.entrySet()) {
            this.global.put(entry.getKey(), entry.getValue());
          }
        }
        this.encoderBulkResponse.clear();
        return true;
      }
      // Still waiting on more parts of a multi-part response
      return false;
    }

    /**
     * Parse DJTT two digit integer stored as legible hex
     */
    private int byteNumber(byte b) {
      return ((b & 0xF0) >> 4) * 10 + (b & 0x0F);
    }

    private void sendAll() {
      sendEncoders(true);
      sendGlobal();
    }

    private void sendModified() {
      if (sendEncoders(false)) {
        sendGlobal();
      }
    }

    private boolean sendEncoders(boolean forceAll) {
      if (!this.initialized) {
        LXMidiEngine.error("Cannot push empty config to MFT device");
        return false;
      }
      if (!versionOK) {
        LXMidiEngine.error("Cannot push config to MFT device running old firmware");
        return false;
      }

      boolean modified = false;

      // Encoders
      for (int i = 0; i < this.encoders.length; ++i) {
        if (this.encoders[i].isModified || forceAll) {
          this.encoders[i].send(forceAll);
          modified = true;
        }
      }

      return modified;
    }

    private void sendGlobal() {
      if (!this.initialized) {
        LXMidiEngine.error("Cannot push empty config to MFT device");
        return;
      }
      if (!versionOK) {
        LXMidiEngine.error("Cannot push config to MFT device running old firmware");
        return;
      }

      byte[] sysex = new byte[this.global.size()*2 + 6];
      sysex[0] = (byte)0xf0;
      sysex[1] = MIDI_MFR_ID_0;
      sysex[2] = MIDI_MFR_ID_1;
      sysex[3] = MIDI_MFR_ID_2;
      sysex[4] = SYSEX_COMMAND_PUSH_CONF;
      int iSys = 5;
      for (Map.Entry<Byte, Byte> g : this.global.entrySet()) {
        sysex[iSys++] = g.getKey();
        sysex[iSys++] = g.getValue();
      }
      sysex[iSys] = (byte)0xf7;

      // LXMidiEngine.log("MFT System sysex:      " + bytesToString(sysex));
      sendSysex(sysex);
    }

    private void initializeLXDefaults() {
      initGlobal();
      this.global.put((byte)0, (byte)4);                            // System MIDI channel
      this.global.put((byte)1, (byte)1);                            // Bank Side Buttons
      this.global.put((byte)2, CFG_GLOBAL_SSACTION_CCTOGGLE);       // Left Button 1 Function
      this.global.put((byte)3, CFG_GLOBAL_SSACTION_BANKDOWN);       // Left Button 2 Function
      this.global.put((byte)4, CFG_GLOBAL_SSACTION_CCTOGGLE);       // Left Button 3 Function
      this.global.put((byte)5, CFG_GLOBAL_SSACTION_CCTOGGLE);       // Right Button 1 Function
      this.global.put((byte)6, CFG_GLOBAL_SSACTION_BANKUP);         // Right Button 2 Function
      this.global.put((byte)7, CFG_GLOBAL_SSACTION_CCTOGGLE);       // Right Button 3 Function
      this.global.put((byte)8, (byte)63);                           // Super Knob Start Point
      this.global.put((byte)9, (byte)127);                          // Super Knob End Point
      this.global.put((byte)10, (byte)0);                           // 0a has detent
      this.global.put((byte)11, (byte)0);                           // 0b sensitivity
      this.global.put((byte)12, (byte)0);                           // 0c switch action type
      this.global.put((byte)13, (byte)2);                           // 0d switch midi channel
      this.global.put((byte)14, (byte)0);                           // 0e switch midi number
      this.global.put((byte)15, (byte)0);                           // 0f switch midi type
      this.global.put((byte)16, (byte)1);                           // 10 encoder midi channel
      this.global.put((byte)17, (byte)0);                           // 11 encoder midi number
      this.global.put((byte)18, CFG_ENC_MIDITYPE_SENDRELENC);       // 12 encoder midi type
      this.global.put((byte)19, (byte)51);                          // 13 active color
      this.global.put((byte)20, (byte)1);                           // 14 inactive color
      this.global.put((byte)21, (byte)63);                          // 15 detent color
      this.global.put((byte)22, CFG_ENC_INDICATORTYPE_BLENDEDBAR);  // 16 indicator type
      this.global.put((byte)23, (byte)0);                           // 17 isSuperKnob
      this.global.put((byte)24, (byte)0);                           // 18 encoder shift midi channel
      // Yes this gap matches the Midi Fighter Utility sysex
      this.global.put((byte)31, (byte)127);                         // 1f  RGB LED Brightness
      this.global.put((byte)32, (byte)127);                         // 20  Indicator Global Brightness

      for (int i = 0; i < this.encoders.length; ++i) {
        Encoder enc = this.encoders[i];
        enc.setDetent(false);
        enc.set("movement", CFG_ENC_MOVEMENTTYPE_DIRECT_HIGHRESOLUTION);
        enc.set("switch_action_type", CFG_ENC_SWACTION_CCHOLD);
        enc.set("switch_midi_channel", (byte)2);
        enc.set("switch_midi_number", (byte)enc.encoderIndex);
        enc.set("switch_midi_type", (byte)0);             // Appears no longer in use
        enc.set("encoder_midi_channel", (byte)1);
        enc.set("encoder_midi_number", (byte)enc.encoderIndex);
        enc.set("encoder_midi_type", CFG_ENC_MIDITYPE_SENDRELENC);       // Important! must be relative type
        enc.set("active_color", (byte)51);                               // MFT default 51
        enc.set("inactive_color", (byte)1);                              // MFT default 1
        enc.set("detent_color", (byte)63);                               // MFT default 63
        enc.set("indicator_display_type", CFG_ENC_INDICATORTYPE_BLENDEDBAR);
        enc.set("is_super_knob", CFG_FALSE);
        enc.set("encoder_shift_midi_channel", (byte)0);
        // Mark as modified so the full config will get sent for the first device
        enc.isModified = true;
      }

      this.initialized = true;
    }

    /**
     * A configuration friendly to LX generic midi mapping.
     * Encoders use absolute CCs, switches use notes.
     */
    @SuppressWarnings("unused")
    private void initializeUserDefaults() {
      initGlobal();
      this.global.put((byte)0, (byte)4);                            // System MIDI channel
      this.global.put((byte)1, (byte)1);                            // Bank Side Buttons
      this.global.put((byte)2, CFG_GLOBAL_SSACTION_CCTOGGLE);       // Left Button 1 Function
      this.global.put((byte)3, CFG_GLOBAL_SSACTION_BANKDOWN);       // Left Button 2 Function
      this.global.put((byte)4, CFG_GLOBAL_SSACTION_CCTOGGLE);       // Left Button 3 Function
      this.global.put((byte)5, CFG_GLOBAL_SSACTION_CCTOGGLE);       // Right Button 1 Function
      this.global.put((byte)6, CFG_GLOBAL_SSACTION_BANKUP);         // Right Button 2 Function
      this.global.put((byte)7, CFG_GLOBAL_SSACTION_CCTOGGLE);       // Right Button 3 Function
      this.global.put((byte)8, (byte)63);                           // Super Knob Start Point
      this.global.put((byte)9, (byte)127);                          // Super Knob End Point
      this.global.put((byte)10, (byte)0);                           // 0a has detent
      this.global.put((byte)11, (byte)0);                           // 0b sensitivity
      this.global.put((byte)12, (byte)0);                           // 0c switch action type
      this.global.put((byte)13, (byte)2);                           // 0d switch midi channel
      this.global.put((byte)14, (byte)0);                           // 0e switch midi number
      this.global.put((byte)15, (byte)0);                           // 0f switch midi type
      this.global.put((byte)16, (byte)1);                           // 10 encoder midi channel
      this.global.put((byte)17, (byte)0);                           // 11 encoder midi number
      this.global.put((byte)18, CFG_ENC_MIDITYPE_SENDCC);           // 12 encoder midi type
      this.global.put((byte)19, (byte)51);                          // 13 active color
      this.global.put((byte)20, (byte)1);                           // 14 inactive color
      this.global.put((byte)21, (byte)63);                          // 15 detent color
      this.global.put((byte)22, CFG_ENC_INDICATORTYPE_BLENDEDBAR);  // 16 indicator type
      this.global.put((byte)23, (byte)0);                           // 17 isSuperKnob
      this.global.put((byte)24, (byte)0);                           // 18 encoder shift midi channel
      // Yes this gap matches the Midi Fighter Utility sysex
      this.global.put((byte)31, (byte)127);                         // 1f  RGB LED Brightness
      this.global.put((byte)32, (byte)127);                         // 20  Indicator Global Brightness

      for (int i = 0; i < this.encoders.length; ++i) {
        Encoder enc = this.encoders[i];
        enc.setDetent(false);
        enc.set("movement", CFG_ENC_MOVEMENTTYPE_DIRECT_HIGHRESOLUTION);
        enc.set("switch_action_type", CFG_ENC_SWACTION_NOTEHOLD);
        enc.set("switch_midi_channel", (byte)2);
        enc.set("switch_midi_number", (byte)enc.encoderIndex);
        enc.set("switch_midi_type", (byte)0);             // Appears no longer in use
        enc.set("encoder_midi_channel", (byte)1);
        enc.set("encoder_midi_number", (byte)enc.encoderIndex);
        enc.set("encoder_midi_type", CFG_ENC_MIDITYPE_SENDCC);           // Absolute CC for LX generic mapping
        enc.set("active_color", (byte)51);                               // MFT default 51
        enc.set("inactive_color", (byte)RGB_USER);                       // MFT default 1
        enc.set("detent_color", (byte)63);                               // MFT default 63
        enc.set("indicator_display_type", CFG_ENC_INDICATORTYPE_BLENDEDBAR);
        enc.set("is_super_knob", CFG_FALSE);
        enc.set("encoder_shift_midi_channel", (byte)0);
      }

      this.initialized = true;
    }

    // Helper for debugging
    @SuppressWarnings("unused")
    private String bytesToString(byte[] bytes) {
      StringBuilder sb = new StringBuilder();
      for (byte b : bytes) {
        sb.append(String.format("%02X ", b));
      }
      return sb.toString();
    }

  }

  private final Config userConfig = new Config();
  private final Config lxConfig = new Config();

  private final DeviceListener deviceListener;

  private class DeviceListener implements FocusedDevice.Listener, LXParameterListener {

    private final FocusedDevice focusedDevice;

    private LXDeviceComponent device = null;

    private final LXListenableNormalizedParameter[] knobs =
      new LXListenableNormalizedParameter[DEVICE_KNOB_NUM];
    private final int[] knobTicks = new int[DEVICE_KNOB_NUM];
    private final int[] knobIncrementSize = new int[DEVICE_KNOB_NUM];

    private DeviceListener(LX lx) {
      for (int i = 0; i < this.knobs.length; ++i) {
        this.knobs[i] = null;
        this.knobTicks[i] = 0;
        this.knobIncrementSize[i] = 1;
      }

      this.focusedDevice = new FocusedDevice(lx, MidiFighterTwister.this, this);
      this.focusedDevice.setAuxSticky(true);
    }

    @Override
    public void onDeviceFocused(LXDeviceComponent device) {
      registerDevice(device);
    }

    private void resend() {
      resend(false);
    }

    private void resend(boolean forceAll) {
      final boolean isAux = isAux();

      // Sysex config changes require reboot therefore must happen before MIDI commands
      for (int i = 0; i < this.knobs.length; ++i) {
        LXListenableNormalizedParameter parameter = this.knobs[i];
        Config.Encoder enc = i < lxConfig.encoders.length ? lxConfig.encoders[i] : null;
        if (parameter != null && enc != null) {
          enc.setDetent(parameter.getPolarity() == Polarity.BIPOLAR);
        } else if (enc != null) {
          enc.setDetent(false);
        }
      }
      if (forceAll) {
        lxConfig.sendAll();
        // Move MFT to current bank
        sendControlChange(CHANNEL_SYSTEM, currentBank.getValuei(), BANK_ON);
      } else {
        lxConfig.sendModified();  // Unfortunately config changes require a reboot to restart the display.  This adds a lag.
      }

      // Midi commands
      for (int i = 0; i < this.knobs.length; ++i) {
        LXListenableNormalizedParameter parameter = this.knobs[i];
        if (parameter != null) {
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_ANIMATION_NONE);
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_ANIMATION_NONE);
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_BRIGHTNESS_MAX);
          double normalized = parameter.getBaseNormalized();
          sendControlChange(CHANNEL_ROTARY_ENCODER, DEVICE_KNOB + i, (int) (normalized * 127));
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_BRIGHTNESS_MAX);
          sendControlChange(CHANNEL_SWITCH_AND_COLOR, DEVICE_KNOB + i, isAux ? RGB_AUX : RGB_PRIMARY);
        } else {
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_ANIMATION_NONE);
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_ANIMATION_NONE);
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_BRIGHTNESS_25);
          if (i <= lxConfig.encoders.length && lxConfig.encoders[i].has_detent.value == CFG_TRUE) {
            sendControlChange(CHANNEL_ROTARY_ENCODER, DEVICE_KNOB+i, 63);
          } else {
            sendControlChange(CHANNEL_ROTARY_ENCODER, DEVICE_KNOB+i, 0);
          }
          sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_BRIGHTNESS_OFF);
        }
      }
    }

    private void registerDevice(LXDeviceComponent device) {
      if (this.device != device) {
        unregisterDevice();
        this.device = device;
        if (this.device != null) {
          this.device.remoteControlsChanged.addListener(this);
        }
        registerDeviceKnobs();
      }
    }

    private void registerDeviceKnobs() {

      // Sysex config changes require reboot therefore must happen before MIDI commands
      int e = 0;
      if (this.device != null) {
        for (LXListenableNormalizedParameter parameter : this.device.getRemoteControls()) {
          if (e >= this.knobs.length || e >= lxConfig.encoders.length) {
            break;
          }
          Config.Encoder enc = lxConfig.encoders[e];
          if (parameter != null) {
            enc.setDetent(parameter.getPolarity() == Polarity.BIPOLAR);
          } else {
            enc.setDetent(false);
          }
          ++e;
        }
      }
      // JKB note: Skip adjustments to unused knobs to minimize reboots.
      // Currently this leaves an artifact of center position on bipolar knobs.
      /* while (e < lxConfig.encoders.length) {
        MFTconfig.EncoderConfig enc = lxConfig.encoders[e];
         enc.setDetent(false);
        ++e;
      } */
      lxConfig.sendModified();  // Unfortunately config changes require a reboot to restart the display.  This adds a lag.

      int i = 0;
      if (this.device != null) {
        final boolean isAux = isAux();
        final List<LXParameter> uniqueParameters = new ArrayList<LXParameter>();
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
              this.knobIncrementSize[i] = LXUtils.max(1, 127/((DiscreteParameter)parameter).getRange());
            }
            if (!uniqueParameters.contains(parameter)) {
              parameter.addListener(this);
              uniqueParameters.add(parameter);
            }
            sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_ANIMATION_NONE);
            sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_BRIGHTNESS_MAX);
            double normalized = parameter.getBaseNormalized();
            sendControlChange(CHANNEL_ROTARY_ENCODER, DEVICE_KNOB + i, (int) (normalized * 127));
            sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_BRIGHTNESS_MAX);
            if (parameter instanceof LXCompoundModulation.Target && ((LXCompoundModulation.Target)parameter).getModulations().size() > 0) {
              sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_PULSE_EVERY_2_BEATS);
            } else {
              sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_ANIMATION_NONE);
            }
          } else {
            sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_ANIMATION_NONE);
            sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_ANIMATION_NONE);
            sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_BRIGHTNESS_25);
            if (i <= lxConfig.encoders.length && lxConfig.encoders[i].has_detent.value == CFG_TRUE) {
              sendControlChange(CHANNEL_ROTARY_ENCODER, DEVICE_KNOB+i, 63);
            } else {
              sendControlChange(CHANNEL_ROTARY_ENCODER, DEVICE_KNOB+i, 0);
            }
            sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_BRIGHTNESS_OFF);
          }
          sendControlChange(CHANNEL_SWITCH_AND_COLOR, DEVICE_KNOB + i, isAux ? RGB_AUX : RGB_PRIMARY);
          ++i;
        }
      }
      while (i < this.knobs.length) {
        sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_ANIMATION_NONE);
        sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_ANIMATION_NONE);
        sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_BRIGHTNESS_25);
        if (i <= lxConfig.encoders.length && lxConfig.encoders[i].has_detent.value == CFG_TRUE) {
          sendControlChange(CHANNEL_ROTARY_ENCODER, DEVICE_KNOB+i, 63);
        } else {
          sendControlChange(CHANNEL_ROTARY_ENCODER, DEVICE_KNOB+i, 0);
        }
        sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_BRIGHTNESS_OFF);
        ++i;
      }
    }

    @Override
    public void onParameterChanged(LXParameter parameter) {
      if (parameter == this.device.remoteControlsChanged) {
        unregisterDeviceKnobs();
        registerDeviceKnobs();
        return;
      }
      for (int i = 0; i < this.knobs.length; ++i) {
        if (parameter == this.knobs[i]) {
          double normalized = this.knobs[i].getBaseNormalized();
          // Normalized DiscreteParameters need artificial tracking of absolute knob location.
          // Keep local tracking in sync with changes from other source.
          if (parameter instanceof DiscreteParameter && ((DiscreteParameter)parameter).getIncrementMode() == IncrementMode.NORMALIZED) {
            this.knobTicks[i] = (int) (normalized * 127);
          }
          sendControlChange(CHANNEL_ROTARY_ENCODER, DEVICE_KNOB + i, (int) (normalized * 127));
        }
      }
    }

    private final static double KNOB_INCREMENT_AMOUNT = 1/127.;

    private void onKnobIncrement(int index, boolean isUp) {
      LXListenableNormalizedParameter knob = this.knobs[index];
      if (knob != null) {
        if (knob instanceof DiscreteParameter) {
          if (((DiscreteParameter)knob).getIncrementMode() == IncrementMode.NORMALIZED) {
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
            knob.setNormalized(value/127.);
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

    private final double[] tempValues = new double[DEVICE_KNOB_NUM];

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
        } else {
          // Set other parameter types to default value on click
          if (isPressed) {
            switch (knobClickMode.getEnum()) {
            case RESET:
              p.reset();
              break;
            case TEMPORARY:
              this.tempValues[index] = p.getBaseNormalized();
              break;
            }
          } else {
            switch (knobClickMode.getEnum()) {
            case RESET:
              break;
            case TEMPORARY:
              p.setNormalized(this.tempValues[index]);
              break;
            }
          }
        }
      }
    }

    private void unregisterDevice() {
      if (this.device != null) {
        this.device.remoteControlsChanged.removeListener(this);
        unregisterDeviceKnobs();
      }
      this.device = null;
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
          this.knobTicks[i] = 0;
          this.knobIncrementSize[i] = 1;
        }
      }
    }

    private boolean isRegistered = false;

    private void register() {
      this.isRegistered = true;
      this.focusedDevice.register();
    }

    private void unregister() {
      this.isRegistered = false;
      this.focusedDevice.unregister();
    }

    private void dispose() {
      this.focusedDevice.dispose();
    }

  }

  public MidiFighterTwister(LX lx, LXMidiInput input, LXMidiOutput output) {
    super(lx, input, output);
    this.deviceListener = new DeviceListener(lx);
    addSetting("knobClickMode", this.knobClickMode);
    addSetting("focusMode", this.focusMode);
    addSetting("isAux", this.isAux);
    addSetting("currentBank", this.currentBank);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (this.isAux == p) {
      this.deviceListener.focusedDevice.setAux(this.isAux.isOn());
      if (this.enabled.isOn()) {
        this.deviceListener.resend();
      }
    } else if (this.currentBank == p) {
      updateBank(this.currentBank.getValuei(), false);
    }
  }

  private boolean inUpdateBank = false;

  private void updateBank(int bank, boolean fromHardware) {
    if (this.inUpdateBank) {
      return;
    }

    this.inUpdateBank = true;
    if (fromHardware) {
      // Update internal value if this came from hardware
      this.lx.command.perform(new LXCommand.Parameter.SetValue(this.currentBank, bank));
    } else {
      // Tell the hardware the new state if this change was internal
      sendControlChange(CHANNEL_SYSTEM, bank, BANK_ON);
    }
    this.inUpdateBank = false;

    this.deviceListener.focusedDevice.updateRemoteControlFocus();
  }

  // True until async sysex pull is complete
  private boolean initializing = false;
  // True until async sysex pull is complete
  private boolean reconnecting = false;
  // True after async sysex pull is finished
  private boolean initialized = false;

  @Override
  protected void onEnable(boolean on) {
    if (on) {
      this.initializing = true;
      this.reconnecting = false;
      initialize();
    } else {
      if (this.deviceListener.isRegistered) {
        this.deviceListener.unregister();
      }
      restoreConfig();
    }
  }

  @Override
  protected void onReconnect() {
    // MFT may have power-cycled or lost settings, re-initialize global sysex state
    if (this.initialized) {
      // Do a simplified reconnection ONLY if first initialization finished
      this.initializing = false;
      this.reconnecting = true;
    } else {
      // Must have reconnected before first onEnable+initialize finished. Start over.
      this.initializing = true;
      this.reconnecting = false;
    }
    initialize();
  }

  private void initialize() {
    // Pull existing config, save and re-apply at shutdown to leave MFT in previous config state.
    this.userConfig.pull();
  }

  /**
   * Called after sysex config pull has been completed. Finish initialization or reconnection.
   */
  private void initializeComplete() {
    if (!initializing && !reconnecting) {
      // Initialization was cancelled
      return;
    }
    this.initialized = true;
    if (this.initializing) {
      this.initializing = false;
      // Create LX-friendly config
      this.lxConfig.initializeLXDefaults();
      // onDeviceFocused will trigger the full config being sent the first time
      this.deviceListener.register();
    } else if (this.reconnecting) {
      this.reconnecting = false;
      this.deviceListener.resend(true);
    }
  }

  private void restoreConfig() {
    // Cancel any in-progress sysex pull
    this.initializing = false;
    this.reconnecting = false;

    if (this.initialized) {
      this.initialized = false;

      // Set surface lights to clean shutdown state
      for (int i = 0; i < DEVICE_KNOB_NUM; ++i) {
        sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_ANIMATION_NONE);
        sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_BRIGHTNESS_MAX);
        sendControlChange(CHANNEL_ROTARY_ENCODER, DEVICE_KNOB + i, 0);
        sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_BRIGHTNESS_MAX);
        sendControlChange(CHANNEL_SWITCH_AND_COLOR, DEVICE_KNOB + i, RGB_USER);
      }

      // Move MFT to first bank
      sendControlChange(CHANNEL_SYSTEM, BANK1, BANK_ON);

      // Restore original config, if known
      if (this.userConfig.initialized) {
        this.userConfig.sendAll();
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
            // Knob sent value outside of expected range for relative values.  Possible causes:
            //   1. Knob is configured to send absolute values.
            //   2. Knob was rotated during config sysex / reboot.
            //   3. Knob internals are dusty.
            LXMidiEngine.error("Received value " + value + " on MFT encoder " + number + ". Confirm Encoder MIDI Type is ENC 3FH/41H and controller is clean.");
            // Assume the direction is correct, keep behavior smooth even on dusty controllers.
            if (value > KNOB_INCREMENT_VERYFAST) {
              this.deviceListener.onKnobIncrement(iKnob, true);
            } else if (value < KNOB_DECREMENT_VERYFAST) {
              this.deviceListener.onKnobIncrement(iKnob, false);
            }
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
              updateBank(number, true);
            }
            return;
          case BANK1_LEFT1:
          case BANK2_LEFT1:
          case BANK3_LEFT1:
          case BANK4_LEFT1:
            // Change scroll mode
            this.lx.command.perform(new LXCommand.Parameter.Increment(this.focusMode));
            return;
          case BANK1_RIGHT1:
          case BANK2_RIGHT1:
          case BANK3_RIGHT1:
          case BANK4_RIGHT1:
            this.lx.command.perform(new LXCommand.Parameter.Toggle(this.isAux));
            return;
          case BANK1_LEFT2:
          case BANK2_LEFT2:
          case BANK3_LEFT2:
          case BANK4_LEFT2:
            // Previous virtual bank. Handled on device.
            return;
          case BANK1_RIGHT2:
          case BANK2_RIGHT2:
          case BANK3_RIGHT2:
          case BANK4_RIGHT2:
            // Next virtual bank. Handled on device.
            return;
          case BANK1_LEFT3:
          case BANK2_LEFT3:
          case BANK3_LEFT3:
          case BANK4_LEFT3:
            if (this.focusMode.getEnum() == FocusMode.CHANNEL) {
              this.deviceListener.focusedDevice.previousChannel();
              if (!isAux()) {
                lx.engine.mixer.selectChannel(lx.engine.mixer.getFocusedChannel());
              }
            } else {
              this.deviceListener.focusedDevice.previousDevice();
            }
            return;
          case BANK1_RIGHT3:
          case BANK2_RIGHT3:
          case BANK3_RIGHT3:
          case BANK4_RIGHT3:
            if (this.focusMode.getEnum() == FocusMode.CHANNEL) {
              this.deviceListener.focusedDevice.nextChannel();
              if (!isAux()) {
                lx.engine.mixer.selectChannel(lx.engine.mixer.getFocusedChannel());
              }
            } else {
              this.deviceListener.focusedDevice.nextDevice();
            }
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
  public void sysexReceived(LXSysexMessage sysex) {
    if (this.userConfig.isPulling()) {
      this.userConfig.sysexReceived(sysex);
    }
  }

  private boolean isAux() {
    return this.isAux.isOn();
  }

  @Override
  public int getRemoteControlStart() {
    return this.currentBank.getValuei() * DEVICE_KNOB_PER_BANK;
  }

  @Override
  public int getRemoteControlLength() {
    return DEVICE_KNOB_PER_BANK;
  }

  @Override
  public boolean isRemoteControlAux() {
    return isAux();
  }

  @Override
  public void dispose() {
    this.deviceListener.dispose();
    restoreConfig();
    super.dispose();
  }

}
