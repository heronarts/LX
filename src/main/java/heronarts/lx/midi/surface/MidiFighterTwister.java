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
import heronarts.lx.command.LXCommand;
import heronarts.lx.midi.LXMidiEngine;
import heronarts.lx.midi.LXMidiInput;
import heronarts.lx.midi.LXMidiOutput;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameter.Polarity;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.DiscreteParameter.IncrementMode;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.utils.LXUtils;

public class MidiFighterTwister extends LXMidiSurface implements LXMidiSurface.Bidirectional {

  public static final String DEVICE_NAME = "Midi Fighter Twister";

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
  //public static final byte MANUFACTURER_ID = 0x0179;

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

  private class Config {

    private static final int PART_SIZE_BYTES = 24;

    @SuppressWarnings("unused")
    private boolean versionOK = true;       // TODO: Confirm compatible firmware version (>2016) before sending sysex commands

    private boolean initialized = false;
    private final Map<Byte, Byte> global = new LinkedHashMap<Byte, Byte>();
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
      }

      private final int encoderIndex;
      private final byte sysexTag;

      private boolean isModified = false;

      // Keep list of all settings
      private final Map<String, Setting> settings =
        new LinkedHashMap<String, Setting>();

      private final Setting has_detent;

      private Setting addSetting(String name, int address) {
        Setting setting = new Setting((byte) address);
        this.settings.put(name, setting);
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
            int size = bytesRemaining > PART_SIZE_BYTES ? PART_SIZE_BYTES : bytesRemaining;
            bytesRemaining -= PART_SIZE_BYTES;

            byte[] payload = new byte[size+11];
            payload[0] = (byte)0xf0;                    // Start sysex
            payload[1] = MIDI_MFR_ID_0;
            payload[2] = MIDI_MFR_ID_1;
            payload[3] = MIDI_MFR_ID_2;
            payload[4] = SYSEX_COMMAND_BULK_XFER;       // Command = bulk transfer
            payload[5] = 0x00;                          // 0x00 = push to MFT, 0x01 = pull from MFT
            payload[6] = sysexTag;                      // Encoder identifier
            payload[7] = (byte)part;                    // Part 'part' of 'total'
            payload[8] = (byte)total;
            payload[9] = (byte)size;                    // 24 bytes maximum size
            payload[payload.length-1] = (byte)0xf7;     // End sysex

            // Copy the data into the payload
            for (int idx=10; idx < size+10; idx++) {
              payload[idx] = configData.get(iConfig++);
            }

            // LXMidiEngine.log("MFT Encoder sysex(" + this.encoderIndex + "): " + bytesToString(payload));
            output.sendSysex(payload);
          }
        }

        // If successfully sent, mark as not modified for next round
        this.isModified = false;
        for (Setting setting : this.settings.values()) {
          setting.isModified = false;
        }
      }

      @SuppressWarnings("unused")
      private void pull() {
        // Send Pull command for this encoder only
        byte[] payload = new byte[8];
        payload[0] = (byte)0xf0;                    // Start sysex
        payload[1] = MIDI_MFR_ID_0;
        payload[2] = MIDI_MFR_ID_1;
        payload[3] = MIDI_MFR_ID_2;
        payload[4] = SYSEX_COMMAND_BULK_XFER;       // Command = bulk transfer
        payload[5] = (byte)0x01;                    // 0x00 = push to MFT, 0x01 = pull from MFT
        payload[6] = sysexTag;                      // Encoder identifier
        payload[7] = (byte)0xf7;                    // End sysex

        // LXMidiEngine.log("MFT Encoder sysex(" + this.encoderIndex + "): " + bytesToString(payload));
        output.sendSysex(payload);

        // TODO: Process response
      }

    }

    private Config() {
      for (int i=0; i<DEVICE_KNOB_NUM; i++) {
        this.encoders[i] = new Encoder(i);
      }
    }

    private void pull() {
      // TODO: Query current surface settings
      LXMidiEngine.error("Warning: MFT sysex pull not implemented");

      // TODO: Check firmware version for compatibility,
      // only send sysex if year > 2016

      //this.initialized = true;  // Only uncomment after it's working. Don't want to push a blank config!
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
      output.sendSysex(sysex);
    }

    private void initializeLXDefaults() {

      this.global.clear();
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
      this.global.put((byte)10, (byte)0);                           // 0a
      this.global.put((byte)11, (byte)0);                           // 0b CFG_ENC_MOVEMENTTYPE_DIRECT_HIGHRESOLUTION?
      this.global.put((byte)12, (byte)0);                           // 0c CFG_ENC_SWACTION_CCHOLD?
      this.global.put((byte)13, (byte)2);                           // 0d
      this.global.put((byte)14, (byte)0);                           // 0e
      this.global.put((byte)15, (byte)0);                           // 0f
      this.global.put((byte)16, (byte)1);                           // 10
      this.global.put((byte)17, (byte)0);                           // 11
      this.global.put((byte)18, CFG_ENC_MIDITYPE_SENDRELENC);       // 12
      this.global.put((byte)19, (byte)51);                          // 13
      this.global.put((byte)20, (byte)1);                           // 14
      this.global.put((byte)21, (byte)63);                          // 15
      this.global.put((byte)22, CFG_ENC_INDICATORTYPE_BLENDEDBAR);  // 16
      this.global.put((byte)23, (byte)0);                           // 17
      this.global.put((byte)24, (byte)0);                           // 18
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
        enc.set("encoder_midi_type", CFG_ENC_MIDITYPE_SENDRELENC);        // Important! must be relative type
        enc.set("active_color", (byte)51);                               // MFT default 51
        enc.set("inactive_color", (byte)1);                              // MFT default 1
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
      String s = new String();
      for (int i=0; i<bytes.length; i++) {
        s = s.concat(String.format("%02X ", bytes[i]));
      }
      return s;
    }

  };

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
      lxConfig.sendModified();  // Unfortunately config changes require a reboot to restart the display.  This adds a lag.

      // Midi commands
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
            double normalized = (parameter instanceof CompoundParameter) ?
              ((CompoundParameter) parameter).getBaseNormalized() :
              parameter.getNormalized();
            sendControlChange(CHANNEL_ROTARY_ENCODER, DEVICE_KNOB + i, (int) (normalized * 127));
            sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_BRIGHTNESS_MAX);
            if (parameter instanceof CompoundParameter && ((CompoundParameter)parameter).modulations.size()>0) {
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
          double normalized = (parameter instanceof CompoundParameter) ?
            ((CompoundParameter) parameter).getBaseNormalized() :
            this.knobs[i].getNormalized();
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

    private double[] tempValues = new double[DEVICE_KNOB_NUM];

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
          if (isPressed) {
            switch (knobClickMode.getEnum()) {
            case RESET:
              p.reset();
              break;
            case TEMPORARY:
              this.tempValues[index] = (p instanceof CompoundParameter) ?
                ((CompoundParameter) p).getBaseNormalized() :
                p.getNormalized();
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
      this.deviceListener.resend();
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

  @Override
  protected void onEnable(boolean on) {
    if (on) {
      initialize();
      this.deviceListener.register();
    } else {
      if (this.deviceListener.isRegistered) {
        this.deviceListener.unregister();
      }
    }
  }

  @Override
  protected void onReconnect() {
    if (this.enabled.isOn()) {
      this.deviceListener.resend();
    }
  }

  private void initialize() {
    initializeConfig();

    // Move MFT to current bank
    sendControlChange(CHANNEL_SYSTEM, this.currentBank.getValuei(), BANK_ON);

    for (int i = 0; i < DEVICE_KNOB_NUM; ++i) {
      sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_ANIMATION_NONE);
      sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_ANIMATION_NONE);
      sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, INDICATOR_BRIGHTNESS_MAX);
      // Set indicator (dial) to lowest level
      sendControlChange(CHANNEL_ROTARY_ENCODER, DEVICE_KNOB+i, 0);
      sendControlChange(CHANNEL_ANIMATIONS_AND_BRIGHTNESS, DEVICE_KNOB + i, RGB_BRIGHTNESS_OFF);
    }
  }

  private void initializeConfig() {
    // Pull existing config, save and re-apply at shutdown to leave MFT in previous config state.
    this.userConfig.pull();

    // Apply LX-friendly config
    this.lxConfig.initializeLXDefaults();
    this.lxConfig.sendAll();
  }

  @SuppressWarnings("unused")
  private void restoreConfig() {
    //this.userConfig.sendAll();  // Uncomment after pull is working
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
    super.dispose();
  }

}
