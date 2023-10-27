/**
 * Copyright 2023- Justin Belcher, Mark C. Slee, Heron Arts LLC
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

/* INSTRUCTIONS
 *
 * On the mixer, under My Settings > MIDI:
 *  -Button Type MUST be set to "Toggle". Otherwise only button state is sent to midi instead of function state.
 *  -Any midi channel is usable, just set this class' midiChannel parameter to match.
 *
 * TODO:
 * -Include crossfaderAssign + crossfader + crossfaderCurve in level calculations
 * -Include CH Fader Curve in level calculations (use parameter exponent)
 */
package heronarts.lx.midi.surface;

import heronarts.lx.LX;
import heronarts.lx.midi.LXMidiEngine;
import heronarts.lx.midi.LXMidiInput;
import heronarts.lx.midi.LXMidiOutput;
import heronarts.lx.midi.MidiAftertouch;
import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOn;
import heronarts.lx.midi.MidiPitchBend;
import heronarts.lx.midi.MidiProgramChange;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.LXParameter.Polarity;
import heronarts.lx.utils.LXUtils;

public class DJMV10 extends LXMidiSurface {

  public static final String DEVICE_NAME = "DJM-V10";

  public enum MidiChannel {
    CH1(0),
    CH2(1),
    CH3(2),
    CH4(3),
    CH5(4),
    CH6(5),
    CH7(6),
    CH8(7),
    CH9(8),
    CH10(9),
    CH11(10),
    CH12(11),
    CH13(12),
    CH14(13),
    CH15(14),
    CH16(15),
    ANY(99);

    private final int index;
    private final String label;

    private MidiChannel(int index) {
      this.index = index;
      this.label = index == 99 ? "Any" : Integer.toString(this.index + 1);
    }

    @Override
    public String toString() {
      return this.label;
    }

    public int getIndex() {
      return this.index;
    }
  }

  public final EnumParameter<MidiChannel> midiChannel = new EnumParameter<MidiChannel>("MIDI Channel", MidiChannel.CH1);

  public enum Channel {
    ONE(0),
    TWO(1),
    THREE(2),
    FOUR(3),
    FIVE(4),
    SIX(5);

    private final int index;
    private final String label;

    private Channel(int index) {
      this.index = index;
      this.label = Integer.toString(this.index + 1);
    }

    @Override
    public String toString() {
      return this.label;
    }

    public int getIndex() {
      return this.index;
    }
  }

  public static final Channel[] ALL_CHANNELS = Channel.values();

  // MIDI ControlChanges

  // Three-position slide values
  public static final int SLIDE_LEFT = 0;
  public static final int SLIDE_CENTER = 64;
  public static final int SLIDE_RIGHT = 127;

  public static final int TRIM1 = 1;
  public static final int TRIM2 = 6;
  public static final int TRIM3 = 12;
  public static final int TRIM4 = 80;
  public static final int TRIM5 = 96;
  public static final int TRIM6 = 119;

  public static final int COMP1 = 70;
  public static final int COMP2 = 71;
  public static final int COMP3 = 72;
  public static final int COMP4 = 73;
  public static final int COMP5 = 74;
  public static final int COMP6 = 75;

  public static final int HIGH1 = 2;
  public static final int HIGH2 = 7;
  public static final int HIGH3 = 14;
  public static final int HIGH4 = 81;
  public static final int HIGH5 = 48;
  public static final int HIGH6 = 56;

  public static final int MID_HIGH1 = 3;
  public static final int MID_HIGH2 = 8;
  public static final int MID_HIGH3 = 15;
  public static final int MID_HIGH4 = 92;
  public static final int MID_HIGH5 = 49;
  public static final int MID_HIGH6 = 60;

  public static final int MID_LOW1 = 28;
  public static final int MID_LOW2 = 29;
  public static final int MID_LOW3 = 16;
  public static final int MID_LOW4 = 93;
  public static final int MID_LOW5 = 52;
  public static final int MID_LOW6 = 89;

  public static final int LOW1 = 4;
  public static final int LOW2 = 9;
  public static final int LOW3 = 21;
  public static final int LOW4 = 82;
  public static final int LOW5 = 63;
  public static final int LOW6 = 90;

  public static final int FILTER_RESONANCE = 44;
  public static final int FILTER1 = 32;
  public static final int FILTER2 = 33;
  public static final int FILTER3 = 34;
  public static final int FILTER4 = 35;
  public static final int FILTER5 = 36;
  public static final int FILTER6 = 37;
  public static final int FILTER_MASTER = 38;

  public static final int SEND1 = 5;
  public static final int SEND2 = 10;
  public static final int SEND3 = 22;
  public static final int SEND4 = 83;
  public static final int SEND5 = 101;
  public static final int SEND6 = 118;

  public static final int SEND_SIZE_FEEDBACK = 108;
  public static final int SEND_TIME = 105;
  public static final int SEND_TONE = 106;
  public static final int SEND_MASTER_MIX_LEVEL = 107;

  public static final int CHANNEL_FADER1 = 17;
  public static final int CHANNEL_FADER2 = 18;
  public static final int CHANNEL_FADER3 = 19;
  public static final int CHANNEL_FADER4 = 20;
  public static final int CHANNEL_FADER5 = 109;
  public static final int CHANNEL_FADER6 = 110;

  public static final int CROSSFADER = 11;
  public static final int CROSSFADER_ASSIGN1 = 65;  // three-position slide
  public static final int CROSSFADER_ASSIGN2 = 66;
  public static final int CROSSFADER_ASSIGN3 = 67;
  public static final int CROSSFADER_ASSIGN4 = 68;
  public static final int CROSSFADER_ASSIGN5 = 84;
  public static final int CROSSFADER_ASSIGN6 = 88;

  public static final int CHANNEL_FADER_CURVE = 94; // three-position slide
  public static final int CROSSFADER_CURVE = 95;    // three-position slide

  public static final int MASTER_FADER = 24;
  public static final int BOOTH_FADER = 25;
  public static final int BOOTH_EQ_HI = 111;
  public static final int BOOTH_EQ_LOW = 115;

  public static final int ISOLATOR_HI = 39;
  public static final int ISOLATOR_MID = 40;
  public static final int ISOLATOR_LOW = 41;

  public static final int MULTIIO_LEVEL = 113;

  public static final int XPAD_SLIDER = 116;
  public static final int XPAD_BEAT = 117;

  public static final int BEAT_LEFT = 76;
  public static final int BEAT_RIGHT = 77;
  public static final int BEAT_TAP_TEMPO = 78;
  public static final int TIME_MSB = 13;
  public static final int TIME_LSB = 45;

  public static final int FX_LOW = 104;
  public static final int FX_MID = 103;
  public static final int FX_HIGH = 102;

  public static final int FX_TYPE_DELAY = 42;
  public static final int FX_TYPE_ECHO = 55;
  public static final int FX_TYPE_PINGPONG = 51;
  public static final int FX_TYPE_SPIRAL = 43;
  public static final int FX_TYPE_HELIX = 62;
  public static final int FX_TYPE_REVERB = 54;
  public static final int FX_TYPE_SHIMMER = 58;
  public static final int FX_TYPE_FLANGER = 50;
  public static final int FX_TYPE_PHASER = 57;
  public static final int FX_TYPE_FILTER = 59;
  public static final int FX_TYPE_TRANS = 53;
  public static final int FX_TYPE_ROLL = 46;
  public static final int FX_TYPE_PITCH = 47;
  public static final int FX_TYPE_VINYLBRAKE = 61;

  public static final int FX_LEVEL = 91;
  public static final int FX_ONOFF = 114;

  public static final int MIC_TALKOVER = 79;  // three-position slide
  public static final int MIC_EQ_LOW = 31;
  public static final int MIC_EQ_HIGH = 30;

  public static final int PHONES_MIX_A = 27;
  public static final int PHONES_LEVEL_A = 26;
  public static final int PHONES_MIX_B = 85;
  public static final int PHONES_LEVEL_B = 86;

  // MIDI Notes

  public static final int FILTER_LPF = 13;
  public static final int FILTER_HPF = 14;

  public static final int BEAT_FX_ASSIGN1 = 1;
  public static final int BEAT_FX_ASSIGN2 = 2;
  public static final int BEAT_FX_ASSIGN3 = 3;
  public static final int BEAT_FX_ASSIGN4 = 4;
  public static final int BEAT_FX_ASSIGN5 = 5;
  public static final int BEAT_FX_ASSIGN6 = 6;
  public static final int BEAT_FX_ASSIGN_MIC = 16;
  public static final int BEAT_FX_ASSIGN_MASTER = 17;

  public static final int SEND_MASTER_MIX = 44;
  // Build-in Sends are only one at a time
  public static final int SEND_SHORT_DELAY = 19;
  public static final int SEND_LONG_DELAY = 20;
  public static final int SEND_DUB_ECHO = 47;
  public static final int SEND_REVERB = 45;
  // External Sends have full independent on/off
  public static final int SEND_EXTERNAL1 = 46;
  public static final int SEND_EXTERNAL2 = 21;

  public static final int CUE1_A = 7;
  public static final int CUE2_A = 8;
  public static final int CUE3_A = 9;
  public static final int CUE4_A = 10;
  public static final int CUE5_A = 11;
  public static final int CUE6_A = 12;
  public static final int CUE_MASTER_A = 30;
  public static final int CUE_LINK_A = 31;
  public static final int PHONES_PRE_EQ_A = 32;
  public static final int PHONES_MONO_SPLIT_A = 33;
  public static final int CUE1_B = 22;
  public static final int CUE2_B = 23;
  public static final int CUE3_B = 24;
  public static final int CUE4_B = 25;
  public static final int CUE5_B = 26;
  public static final int CUE6_B = 27;
  public static final int CUE_MASTER_B = 28;
  public static final int CUE_LINK_B = 29;

  public static final int ISOLATOR_ON = 18;

  public static final int MULTIIO_CH_SELECT_MIC = 40;
  public static final int MULTIIO_CH_SELECT_1 = 34;
  public static final int MULTIIO_CH_SELECT_2 = 35;
  public static final int MULTIIO_CH_SELECT_3 = 36;
  public static final int MULTIIO_CH_SELECT_4 = 37;
  public static final int MULTIIO_CH_SELECT_5 = 38;
  public static final int MULTIIO_CH_SELECT_6 = 39;
  public static final int MULTIIO_CH_SELECT_MASTER = 41;
  public static final int MULTIIO_MODE = 48;
  // public static final int MULTIIO_ON = ;   // ?

  public static final int AUTO_TAP = 42;
  public static final int QUANTIZE = 118;
  public static final int XPAD_TOUCH = 43;

  public static final int INPUT1_A = 53;
  public static final int INPUT1_B = 66;
  public static final int INPUT1_DIGITAL = 50;
  public static final int INPUT1_LINE = 51;
  public static final int INPUT1_PHONO = 52;
  public static final int INPUT1_BUILTIN = 81;
  public static final int INPUT1_EXT1 = 82;
  public static final int INPUT1_EXT2 = 83;

  public static final int INPUT2_A = 57;
  public static final int INPUT2_B = 68;
  public static final int INPUT2_DIGITAL = 54;
  public static final int INPUT2_LINE = 55;
  public static final int INPUT2_PHONO = 69;
  public static final int INPUT2_BUILTIN = 84;
  public static final int INPUT2_EXT1 = 85;
  public static final int INPUT2_EXT2 = 86;

  public static final int INPUT3_A = 61;
  public static final int INPUT3_B = 70;
  public static final int INPUT3_DIGITAL = 58;
  public static final int INPUT3_LINE = 59;
  public static final int INPUT3_PHONO = 60;
  public static final int INPUT3_BUILTIN = 87;
  public static final int INPUT3_EXT1 = 88;
  public static final int INPUT3_EXT2 = 89;

  public static final int INPUT4_A = 65;
  public static final int INPUT4_B = 72;
  public static final int INPUT4_DIGITAL = 62;
  public static final int INPUT4_LINE = 63;
  public static final int INPUT4_PHONO = 64;
  public static final int INPUT4_BUILTIN = 90;
  public static final int INPUT4_EXT1 = 91;
  public static final int INPUT4_EXT2 = 92;

  public static final int INPUT5_A = 95;
  public static final int INPUT5_B = 96;
  public static final int INPUT5_DIGITAL = 93;
  public static final int INPUT5_LINE = 94;
  public static final int INPUT5_PHONO = 97;
  public static final int INPUT5_BUILTIN = 98;
  public static final int INPUT5_EXT1 = 99;
  public static final int INPUT5_EXT2 = 100;

  public static final int INPUT6_A = 104;
  public static final int INPUT6_B = 105;
  public static final int INPUT6_DIGITAL = 101;
  public static final int INPUT6_LINE = 102;
  public static final int INPUT6_PHONO = 103;
  public static final int INPUT6_BUILTIN = 106;
  public static final int INPUT6_EXT1 = 107;
  public static final int INPUT6_EXT2 = 108;


  // Raw knob positions from MIDI
  public final BoundedParameter low1raw = new BoundedParameter("low1raw");
  public final BoundedParameter low2raw = new BoundedParameter("low2raw");
  public final BoundedParameter low3raw = new BoundedParameter("low3raw");
  public final BoundedParameter low4raw = new BoundedParameter("low4raw");
  public final BoundedParameter low5raw = new BoundedParameter("low5raw");
  public final BoundedParameter low6raw = new BoundedParameter("low6raw");
  public final BoundedParameter midLow1raw = new BoundedParameter("midLow1raw");
  public final BoundedParameter midLow2raw = new BoundedParameter("midLow2raw");
  public final BoundedParameter midLow3raw = new BoundedParameter("midLow3raw");
  public final BoundedParameter midLow4raw = new BoundedParameter("midLow4raw");
  public final BoundedParameter midLow5raw = new BoundedParameter("midLow5raw");
  public final BoundedParameter midLow6raw = new BoundedParameter("midLow6raw");
  public final BoundedParameter midHigh1raw = new BoundedParameter("midHigh1raw");
  public final BoundedParameter midHigh2raw = new BoundedParameter("midHigh2raw");
  public final BoundedParameter midHigh3raw = new BoundedParameter("midHigh3raw");
  public final BoundedParameter midHigh4raw = new BoundedParameter("midHigh4raw");
  public final BoundedParameter midHigh5raw = new BoundedParameter("midHigh5raw");
  public final BoundedParameter midHigh6raw = new BoundedParameter("midHigh6raw");
  public final BoundedParameter high1raw = new BoundedParameter("high1raw");
  public final BoundedParameter high2raw = new BoundedParameter("high2raw");
  public final BoundedParameter high3raw = new BoundedParameter("high3raw");
  public final BoundedParameter high4raw = new BoundedParameter("high4raw");
  public final BoundedParameter high5raw = new BoundedParameter("high5raw");
  public final BoundedParameter high6raw = new BoundedParameter("high6raw");

  public final BoundedParameter fade1 = new BoundedParameter("fade1", 1);
  public final BoundedParameter fade2 = new BoundedParameter("fade2", 1);
  public final BoundedParameter fade3 = new BoundedParameter("fade3", 1);
  public final BoundedParameter fade4 = new BoundedParameter("fade4", 1);
  public final BoundedParameter fade5 = new BoundedParameter("fade5", 1);
  public final BoundedParameter fade6 = new BoundedParameter("fade6", 1);

  public final BoundedParameter masterFader = new BoundedParameter("masterFader");
  public final BoundedParameter boothMonitor = new BoundedParameter("boothMonitor");
  public final BoundedParameter crossfader = new BoundedParameter("crossFader");

  public final BoundedParameter send1raw = new BoundedParameter("send1raw").setPolarity(Polarity.BIPOLAR);
  public final BoundedParameter send2raw = new BoundedParameter("send2raw").setPolarity(Polarity.BIPOLAR);
  public final BoundedParameter send3raw = new BoundedParameter("send3raw").setPolarity(Polarity.BIPOLAR);
  public final BoundedParameter send4raw = new BoundedParameter("send4raw").setPolarity(Polarity.BIPOLAR);
  public final BoundedParameter send5raw = new BoundedParameter("send5raw").setPolarity(Polarity.BIPOLAR);
  public final BoundedParameter send6raw = new BoundedParameter("send6raw").setPolarity(Polarity.BIPOLAR);
  public final BoundedParameter sendSizeFeedback = new BoundedParameter("sendSizeFeedback", 0.55, 0.1, 1);
  public final BoundedParameter sendSensitivity = new BoundedParameter("SendSensitivity", 2, 1, 3)
    .setDescription("Send knob sensitivity. Adjust per DJ.");

  // Normalized EQ values
  public final BoundedParameter low1 = new BoundedParameter("low1");
  public final BoundedParameter low2 = new BoundedParameter("low2");
  public final BoundedParameter low3 = new BoundedParameter("low3");
  public final BoundedParameter low4 = new BoundedParameter("low4");
  public final BoundedParameter low5 = new BoundedParameter("low5");
  public final BoundedParameter low6 = new BoundedParameter("low6");
  public final BoundedParameter midLow1 = new BoundedParameter("midLow1");
  public final BoundedParameter midLow2 = new BoundedParameter("midLow2");
  public final BoundedParameter midLow3 = new BoundedParameter("midLow3");
  public final BoundedParameter midLow4 = new BoundedParameter("midLow4");
  public final BoundedParameter midLow5 = new BoundedParameter("midLow5");
  public final BoundedParameter midLow6 = new BoundedParameter("midLow6");
  public final BoundedParameter midHigh1 = new BoundedParameter("midHigh1");
  public final BoundedParameter midHigh2 = new BoundedParameter("midHigh2");
  public final BoundedParameter midHigh3 = new BoundedParameter("midHigh3");
  public final BoundedParameter midHigh4 = new BoundedParameter("midHigh4");
  public final BoundedParameter midHigh5 = new BoundedParameter("midHigh5");
  public final BoundedParameter midHigh6 = new BoundedParameter("midHigh6");
  public final BoundedParameter high1 = new BoundedParameter("high1");
  public final BoundedParameter high2 = new BoundedParameter("high2");
  public final BoundedParameter high3 = new BoundedParameter("high3");
  public final BoundedParameter high4 = new BoundedParameter("high4");
  public final BoundedParameter high5 = new BoundedParameter("high5");
  public final BoundedParameter high6 = new BoundedParameter("high6");

  public final BoundedParameter eqRangeMax = new BoundedParameter("EQmax", 0.5)
    .setDescription("Equalizer knob value that will act as a maximum position. Adjust per DJ. Defaults to center.");

  // Normalized EQ values multiplied by faders
  public final BoundedParameter low1net = new BoundedParameter("low1net");
  public final BoundedParameter low2net = new BoundedParameter("low2net");
  public final BoundedParameter low3net = new BoundedParameter("low3net");
  public final BoundedParameter low4net = new BoundedParameter("low4net");
  public final BoundedParameter low5net = new BoundedParameter("low5net");
  public final BoundedParameter low6net = new BoundedParameter("low6net");
  public final BoundedParameter midLow1net = new BoundedParameter("midLow1net");
  public final BoundedParameter midLow2net = new BoundedParameter("midLow2net");
  public final BoundedParameter midLow3net = new BoundedParameter("midLow3net");
  public final BoundedParameter midLow4net = new BoundedParameter("midLow4net");
  public final BoundedParameter midLow5net = new BoundedParameter("midLow5net");
  public final BoundedParameter midLow6net = new BoundedParameter("midLow6net");
  public final BoundedParameter midHigh1net = new BoundedParameter("midHigh1net");
  public final BoundedParameter midHigh2net = new BoundedParameter("midHigh2net");
  public final BoundedParameter midHigh3net = new BoundedParameter("midHigh3net");
  public final BoundedParameter midHigh4net = new BoundedParameter("midHigh4net");
  public final BoundedParameter midHigh5net = new BoundedParameter("midHigh5net");
  public final BoundedParameter midHigh6net = new BoundedParameter("midHigh6net");
  public final BoundedParameter high1net = new BoundedParameter("high1net");
  public final BoundedParameter high2net = new BoundedParameter("high2net");
  public final BoundedParameter high3net = new BoundedParameter("high3net");
  public final BoundedParameter high4net = new BoundedParameter("high4net");
  public final BoundedParameter high5net = new BoundedParameter("high5net");
  public final BoundedParameter high6net = new BoundedParameter("high6net");

  // Normalized send values
  public final BoundedParameter send1 = new BoundedParameter("send1");
  public final BoundedParameter send2 = new BoundedParameter("send2");
  public final BoundedParameter send3 = new BoundedParameter("send3");
  public final BoundedParameter send4 = new BoundedParameter("send4");
  public final BoundedParameter send5 = new BoundedParameter("send5");
  public final BoundedParameter send6 = new BoundedParameter("send6");

  // Calculated net levels for each channel
  public final BoundedParameter level1net = new BoundedParameter("level1net", 0, 4); // Fade1 * Sum(low1,midLow1,midHigh1,high1)
  public final BoundedParameter level2net = new BoundedParameter("level2net", 0, 4);
  public final BoundedParameter level3net = new BoundedParameter("level3net", 0, 4);
  public final BoundedParameter level4net = new BoundedParameter("level4net", 0, 4);
  public final BoundedParameter level5net = new BoundedParameter("level5net", 0, 4);
  public final BoundedParameter level6net = new BoundedParameter("level6net", 0, 4);


  // Parameter groupings for convenient access
  private BoundedParameter[] lowraw = { low1raw, low2raw, low3raw, low4raw, low5raw, low6raw };
  private BoundedParameter[] midLowraw = { midLow1raw, midLow2raw, midLow3raw, midLow4raw, midLow5raw, midLow6raw };
  private BoundedParameter[] midHighraw = { midHigh1raw, midHigh2raw, midHigh3raw, midHigh4raw, midHigh5raw, midHigh6raw };
  private BoundedParameter[] highraw = { high1raw, high2raw, high3raw, high4raw, high5raw, high6raw };
  private BoundedParameter[] low = { low1, low2, low3, low4, low5, low6 };
  private BoundedParameter[] midLow = { midLow1, midLow2, midLow3, midLow4, midLow5, midLow6 };
  private BoundedParameter[] midHigh = { midHigh1, midHigh2, midHigh3, midHigh4, midHigh5, midHigh6 };
  private BoundedParameter[] high = { high1, high2, high3, high4, high5, high6 };
  private BoundedParameter[] lowNnet = { low1net, low2net, low3net, low4net, low5net, low6net };
  private BoundedParameter[] midLowNnet = { midLow1net, midLow2net, midLow3net, midLow4net, midLow5net, midLow6net };
  private BoundedParameter[] midHighNnet = { midHigh1net, midHigh2net, midHigh3net, midHigh4net, midHigh5net, midHigh6net };
  private BoundedParameter[] highNnet = { high1net, high2net, high3net, high4net, high5net, high6net };
  private BoundedParameter[] fade = { fade1, fade2, fade3, fade4, fade5, fade6 };
  private BoundedParameter[] levelNnet = { level1net, level2net, level3net, level4net, level5net, level6net };
  private BoundedParameter[] sendraw = { send1raw, send2raw, send3raw, send4raw, send5raw, send6raw };
  private BoundedParameter[] send = { send1, send2, send3, send4, send5, send6 };

  // A/B channel abstraction for retaining mappings when target channels are changed
  public final EnumParameter<Channel> aChannel = new EnumParameter<Channel>("A Channel", Channel.TWO);
  public final EnumParameter<Channel> bChannel = new EnumParameter<Channel>("B Channel", Channel.THREE);

  public final BoundedParameter lowA = new BoundedParameter("lowA");
  public final BoundedParameter lowB = new BoundedParameter("lowB");
  public final BoundedParameter midLowA = new BoundedParameter("midLowA");
  public final BoundedParameter midLowB = new BoundedParameter("midLowB");
  public final BoundedParameter midHighA = new BoundedParameter("midHighA");
  public final BoundedParameter midHighB = new BoundedParameter("midHighB");
  public final BoundedParameter highA = new BoundedParameter("highA");
  public final BoundedParameter highB = new BoundedParameter("highB");
  public final BoundedParameter lowAnet = new BoundedParameter("lowA");
  public final BoundedParameter lowBnet = new BoundedParameter("lowB");
  public final BoundedParameter midLowAnet = new BoundedParameter("midLowA");
  public final BoundedParameter midLowBnet = new BoundedParameter("midLowB");
  public final BoundedParameter midHighAnet = new BoundedParameter("midHighA");
  public final BoundedParameter midHighBnet = new BoundedParameter("midHighB");
  public final BoundedParameter highAnet = new BoundedParameter("highA");
  public final BoundedParameter highBnet = new BoundedParameter("highB");
  public final BoundedParameter fadeA = new BoundedParameter("fadeA");
  public final BoundedParameter fadeB = new BoundedParameter("fadeB");
  public final BoundedParameter sendA = new BoundedParameter("sendA");
  public final BoundedParameter sendB = new BoundedParameter("sendB");
  public final BoundedParameter levelAnet = new BoundedParameter("levelAnet", 0, 3);
  public final BoundedParameter levelBnet = new BoundedParameter("levelBnet", 0, 3);

  // Calculated values for A/B
  public final BoundedParameter lowNet = new BoundedParameter("lowNet");  // Max(lowA*fadeA, lowB*fadeB)
  public final BoundedParameter midLowNet = new BoundedParameter("midLowNet");
  public final BoundedParameter midHighNet = new BoundedParameter("midHighNet");
  public final BoundedParameter highNet = new BoundedParameter("highNet");

  public final BoundedParameter smartXF = new BoundedParameter("SmartXF")
    .setDescription("Crossfader position calculated using relative levels of A vs B");

  public enum XFMode {
    OFF("Off"),
    DIRECT("Direct"),
    SMART("Smart");

    private final String label;

    private XFMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final EnumParameter<XFMode> xfMode =
    new EnumParameter<XFMode>("Crossfader Sync", XFMode.OFF)
    .setDescription("Mode for following DJM-V10 crossfader with LX");

  public DJMV10(LX lx, LXMidiInput input, LXMidiOutput output) {
    super(lx, input, output);
    addSetting("midiChannel", this.midiChannel);
    addSetting("xfMode", this.xfMode);
    addSetting("aChannel", this.aChannel);
    addSetting("bChannel", this.bChannel);

    this.aChannel.addListener(this.aChannelListener);
    this.bChannel.addListener(this.bChannelListener);
    this.sendSensitivity.addListener(sendSensitivityListener);
    this.eqRangeMax.addListener(this.eqRangeMaxListener);
    this.smartXF.addListener(this.smartXFListener);
    this.xfMode.addListener(this.xfModeListener);
  }

  @Override
  protected void onEnable(boolean on) {
    if (on) {
      initialize();
    }
  }

  private void initialize() {
    // *Need MIDI sysex or other method to poll current control positions.
    // For now let's initialize to some standard values.
    this.sendSizeFeedback.setNormalized(.5);
    for (Channel channel : ALL_CHANNELS) {
      updateLow(channel, .5);
      updateMidLow(channel, .5);
      updateMidHigh(channel, .5);
      updateHigh(channel, .5);
      updateFade(channel, 0);
      updateSend(channel, 0);
    }
  }

  private final LXParameterListener aChannelListener = (p) -> {
    updateAeq();
    updateAsend();
    updateLowNet();
    updateMidLowNet();
    updateMidHighNet();
    updateHighNet();
  };

  private final LXParameterListener bChannelListener = (p) -> {
    updateBeq();
    updateBsend();
    updateLowNet();
    updateMidLowNet();
    updateMidHighNet();
    updateHighNet();
  };

  private final LXParameterListener sendSensitivityListener = (p) -> {
    recalculateAllSends();
  };

  private final LXParameterListener eqRangeMaxListener = (p) -> {
    recalculateAllEq();
  };

  private final LXParameterListener smartXFListener = (p) -> {
    if (this.xfMode.getEnum() == XFMode.SMART) {
      this.lx.engine.mixer.crossfader.setNormalized(this.smartXF.getNormalized());
    }
  };

  private final LXParameterListener xfModeListener = (p) -> {
    XFMode mode = this.xfMode.getEnum();
    if (mode == XFMode.DIRECT) {
      this.lx.engine.mixer.crossfader.setNormalized(this.crossfader.getNormalized());
    } else if (mode == XFMode.SMART) {
      this.lx.engine.mixer.crossfader.setNormalized(this.smartXF.getNormalized());
    }
  };

  protected void recalculateAllEq() {
    for (Channel channel : ALL_CHANNELS) {
      int c = channel.getIndex();
      low[c].setValue(scaleEq(lowraw[c].getValue()));
      midLow[c].setValue(scaleEq(midLowraw[c].getValue()));
      midHigh[c].setValue(scaleEq(midHighraw[c].getValue()));
      high[c].setValue(scaleEq(highraw[c].getValue()));
      lowNnet[c].setValue(low[c].getValue() * fade[c].getValue());
      midLowNnet[c].setValue(midLow[c].getValue() * fade[c].getValue());
      midHighNnet[c].setValue(midHigh[c].getValue() * fade[c].getValue());
      highNnet[c].setValue(high[c].getValue() * fade[c].getValue());
      updateLevelNet(channel);
    }
    updateAeq();
    updateBeq();
    updateLowNet();
    updateMidLowNet();
    updateMidHighNet();
    updateHighNet();
  }

  protected double scaleEq(double value) {
    return LXUtils.constrain(value / this.eqRangeMax.getValue(), 0, 1);
  }

  protected void updateLow(Channel channel, double value) {
    int c = channel.getIndex();
    lowraw[c].setValue(value);
    low[c].setValue(scaleEq(value));
    lowNnet[c].setValue(low[c].getValue() * fade[c].getValue());
    updateLevelNet(channel);
    updateABeq(channel);
    updateLowNet();
  }

  protected void updateMidLow(Channel channel, double value) {
    int c = channel.getIndex();
    midLowraw[c].setValue(value);
    midLow[c].setValue(scaleEq(value));
    midLowNnet[c].setValue(midLow[c].getValue() * fade[c].getValue());
    updateLevelNet(channel);
    updateABeq(channel);
    updateMidLowNet();
  }

  protected void updateMidHigh(Channel channel, double value) {
    int c = channel.getIndex();
    midHighraw[c].setValue(value);
    midHigh[c].setValue(scaleEq(value));
    midHighNnet[c].setValue(midHigh[c].getValue() * fade[c].getValue());
    updateLevelNet(channel);
    updateABeq(channel);
    updateMidHighNet();
  }

  protected void updateHigh(Channel channel, double value) {
    int c = channel.getIndex();
    highraw[c].setValue(value);
    high[c].setValue(scaleEq(value));
    highNnet[c].setValue(high[c].getValue() * fade[c].getValue());
    updateLevelNet(channel);
    updateABeq(channel);
    updateHighNet();
  }

  protected void updateFade(Channel channel, double value) {
    int c = channel.getIndex();
    fade[c].setValue(value);
    lowNnet[c].setValue(low[c].getValue() * value);
    midLowNnet[c].setValue(midLow[c].getValue() * value);
    midHighNnet[c].setValue(midHigh[c].getValue() * value);
    highNnet[c].setValue(high[c].getValue() * value);
    updateLevelNet(channel);
    updateABeq(channel);
  }

  protected void updateLevelNet(Channel channel) {
    int c = channel.getIndex();
    levelNnet[c].setValue(fade[c].getValue() * (low[c].getValue() + midLow[c].getValue() + midHigh[c].getValue() + high[c].getValue()));
  }

  protected void updateLowNet() {
    lowNet.setValue(Math.max(lowAnet.getValue(), lowBnet.getValue()));
  }

  protected void updateMidLowNet() {
    midLowNet.setValue(Math.max(midLowAnet.getValue(), midLowBnet.getValue()));
  }

  protected void updateMidHighNet() {
    midHighNet.setValue(Math.max(midHighAnet.getValue(), midHighBnet.getValue()));
  }

  protected void updateHighNet() {
    highNet.setValue(Math.max(highAnet.getValue(), highBnet.getValue()));
  }

  protected void updateABeq(Channel channel) {
    if (this.aChannel.getEnum().equals(channel)) {
      updateAeq(channel);
    }
    if (this.bChannel.getEnum().equals(channel)) {
      updateBeq(channel);
    }
  }

  protected void updateAeq() {
    updateAeq(this.aChannel.getEnum());
  }

  protected void updateBeq() {
    updateBeq(this.bChannel.getEnum());
  }

  protected void updateAeq(Channel channel) {
    int c = channel.getIndex();
    lowA.setValue(low[c].getValue());
    midLowA.setValue(midLow[c].getValue());
    midHighA.setValue(midHigh[c].getValue());
    highA.setValue(high[c].getValue());
    fadeA.setValue(fade[c].getValue());
    lowAnet.setValue(lowNnet[c].getValue());
    midLowAnet.setValue(midLowNnet[c].getValue());
    midHighAnet.setValue(midHighNnet[c].getValue());
    highAnet.setValue(highNnet[c].getValue());
    levelAnet.setValue(levelNnet[c].getValue());
    updateSmartXF();
  }

  protected void updateBeq(Channel channel) {
    int c = channel.getIndex();
    lowB.setValue(low[c].getValue());
    midLowB.setValue(midLow[c].getValue());
    midHighB.setValue(midHigh[c].getValue());
    highB.setValue(high[c].getValue());
    fadeB.setValue(fade[c].getValue());
    lowBnet.setValue(lowNnet[c].getValue());
    midLowBnet.setValue(midLowNnet[c].getValue());
    midHighBnet.setValue(midHighNnet[c].getValue());
    highBnet.setValue(highNnet[c].getValue());
    levelBnet.setValue(levelNnet[c].getValue());
    updateSmartXF();
  }


  protected void updateSmartXF() {
    double levA = levelAnet.getNormalized();
    double levB = levelBnet.getNormalized();
    if (levA != 0 || levB != 0) {
      smartXF.setValue( levA > levB ? levB / (levA + levB) : 1 - (levA / (levA + levB)));
    }
  }

  protected void recalculateAllSends() {
    for (Channel channel : ALL_CHANNELS) {
      int c = channel.getIndex();
      double value = sendraw[c].getValue();
      send[c].setValue(value * sendSizeFeedback.getValue() * sendSensitivity.getValue());
    }
    updateAsend();
    updateBsend();
  }

  protected void updateSend(Channel channel, double value) {
    int c = channel.getIndex();
    sendraw[c].setValue(value);
    send[c].setValue(value * sendSizeFeedback.getValue() * sendSensitivity.getValue());
    updateABsend(channel);
  }

  protected void updateABsend(Channel channel) {
    if (this.aChannel.getEnum().equals(channel)) {
      updateAsend(channel);
    }
    if (this.bChannel.getEnum().equals(channel)) {
      updateBsend(channel);
    }
  }

  protected void updateAsend() {
    updateAsend(aChannel.getEnum());
  }

  protected void updateBsend() {
    updateBsend(bChannel.getEnum());
  }

  protected void updateAsend(Channel channel) {
    sendA.setValue(send[channel.getIndex()].getValue());
  }

  protected void updateBsend(Channel channel) {
    sendB.setValue(send[channel.getIndex()].getValue());
  }

  @Override
  public void controlChangeReceived(MidiControlChange cc) {
    MidiChannel midiChannel = this.midiChannel.getEnum();
    if (cc.getChannel() == midiChannel.index || midiChannel == MidiChannel.ANY) {
      int number = cc.getCC();

      switch (number) {
      case LOW1:
        updateLow(Channel.ONE, cc.getNormalized());
        return;
      case LOW2:
        updateLow(Channel.TWO, cc.getNormalized());
        return;
      case LOW3:
        updateLow(Channel.THREE, cc.getNormalized());
        return;
      case LOW4:
        updateLow(Channel.FOUR, cc.getNormalized());
        return;
      case LOW5:
        updateLow(Channel.FIVE, cc.getNormalized());
        return;
      case LOW6:
        updateLow(Channel.SIX, cc.getNormalized());
        return;
      case MID_LOW1:
        updateMidLow(Channel.ONE, cc.getNormalized());
        return;
      case MID_LOW2:
        updateMidLow(Channel.TWO, cc.getNormalized());
        return;
      case MID_LOW3:
        updateMidLow(Channel.THREE, cc.getNormalized());
        return;
      case MID_LOW4:
        updateMidLow(Channel.FOUR, cc.getNormalized());
        return;
      case MID_LOW5:
        updateMidLow(Channel.FIVE, cc.getNormalized());
        return;
      case MID_LOW6:
        updateMidLow(Channel.SIX, cc.getNormalized());
        return;
      case MID_HIGH1:
        updateMidHigh(Channel.ONE, cc.getNormalized());
        return;
      case MID_HIGH2:
        updateMidHigh(Channel.TWO, cc.getNormalized());
        return;
      case MID_HIGH3:
        updateMidHigh(Channel.THREE, cc.getNormalized());
        return;
      case MID_HIGH4:
        updateMidHigh(Channel.FOUR, cc.getNormalized());
        return;
      case MID_HIGH5:
        updateMidHigh(Channel.FIVE, cc.getNormalized());
        return;
      case MID_HIGH6:
        updateMidHigh(Channel.SIX, cc.getNormalized());
        return;
      case HIGH1:
        updateHigh(Channel.ONE, cc.getNormalized());
        return;
      case HIGH2:
        updateHigh(Channel.TWO, cc.getNormalized());
        return;
      case HIGH3:
        updateHigh(Channel.THREE, cc.getNormalized());
        return;
      case HIGH4:
        updateHigh(Channel.FOUR, cc.getNormalized());
        return;
      case HIGH5:
        updateHigh(Channel.FIVE, cc.getNormalized());
        return;
      case HIGH6:
        updateHigh(Channel.SIX, cc.getNormalized());
        return;
      case CHANNEL_FADER1:
        updateFade(Channel.ONE, cc.getNormalized());
        return;
      case CHANNEL_FADER2:
        updateFade(Channel.TWO, cc.getNormalized());
        return;
      case CHANNEL_FADER3:
        updateFade(Channel.THREE, cc.getNormalized());
        return;
      case CHANNEL_FADER4:
        updateFade(Channel.FOUR, cc.getNormalized());
        return;
      case CHANNEL_FADER5:
        updateFade(Channel.FIVE, cc.getNormalized());
        return;
      case CHANNEL_FADER6:
        updateFade(Channel.SIX, cc.getNormalized());
        return;
      case MASTER_FADER:
        this.masterFader.setNormalized(cc.getNormalized());
        return;
      case BOOTH_FADER:
        this.boothMonitor.setNormalized(cc.getNormalized());
        return;
      case CROSSFADER:
        this.crossfader.setNormalized(cc.getNormalized());
        if (this.xfMode.getEnum() == XFMode.DIRECT) {
          this.lx.engine.mixer.crossfader.setNormalized(cc.getNormalized());
        }
        return;
      case SEND1:
        updateSend(Channel.ONE, cc.getNormalized());
        return;
      case SEND2:
        updateSend(Channel.TWO, cc.getNormalized());
        return;
      case SEND3:
        updateSend(Channel.THREE, cc.getNormalized());
        return;
      case SEND4:
        updateSend(Channel.FOUR, cc.getNormalized());
        return;
      case SEND5:
        updateSend(Channel.FIVE, cc.getNormalized());
        return;
      case SEND6:
        updateSend(Channel.SIX, cc.getNormalized());
        return;
      case SEND_SIZE_FEEDBACK:
        this.sendSizeFeedback.setNormalized(cc.getNormalized());
        recalculateAllSends();
        return;
      case TRIM1:
      case TRIM2:
      case TRIM3:
      case TRIM4:
      case TRIM5:
      case TRIM6:

      case COMP1:
      case COMP2:
      case COMP3:
      case COMP4:
      case COMP5:
      case COMP6:

      case FILTER_RESONANCE:
      case FILTER1:
      case FILTER2:
      case FILTER3:
      case FILTER4:
      case FILTER5:
      case FILTER6:
      case FILTER_MASTER:

      case SEND_TIME:
      case SEND_TONE:
      case SEND_MASTER_MIX_LEVEL:

      case CROSSFADER_ASSIGN1:  // three-position slide
      case CROSSFADER_ASSIGN2:
      case CROSSFADER_ASSIGN3:
      case CROSSFADER_ASSIGN4:
      case CROSSFADER_ASSIGN5:
      case CROSSFADER_ASSIGN6:

      case CHANNEL_FADER_CURVE: // three-position slide
      case CROSSFADER_CURVE:    // three-position slide

      case BOOTH_EQ_HI:
      case BOOTH_EQ_LOW:

      case ISOLATOR_HI:
      case ISOLATOR_MID:
      case ISOLATOR_LOW:

      case MULTIIO_LEVEL:

      case XPAD_SLIDER:
      case XPAD_BEAT:

      case BEAT_LEFT:
      case BEAT_RIGHT:
      case BEAT_TAP_TEMPO:
      case TIME_MSB:
      case TIME_LSB:

      case FX_LOW:
      case FX_MID:
      case FX_HIGH:

      case FX_TYPE_DELAY:
      case FX_TYPE_ECHO:
      case FX_TYPE_PINGPONG:
      case FX_TYPE_SPIRAL:
      case FX_TYPE_HELIX:
      case FX_TYPE_REVERB:
      case FX_TYPE_SHIMMER:
      case FX_TYPE_FLANGER:
      case FX_TYPE_PHASER:
      case FX_TYPE_FILTER:
      case FX_TYPE_TRANS:
      case FX_TYPE_ROLL:
      case FX_TYPE_PITCH:
      case FX_TYPE_VINYLBRAKE:

      case FX_LEVEL:
      case FX_ONOFF:

      case MIC_TALKOVER:  // three-position slide
      case MIC_EQ_LOW:
      case MIC_EQ_HIGH:

      case PHONES_MIX_A:
      case PHONES_LEVEL_A:
      case PHONES_MIX_B:
      case PHONES_LEVEL_B:
        // Not implemented
        return;
      }

      LXMidiEngine.error("DJM-V10 UNMAPPED CC: " + cc);
    }
  }

  private void noteReceived(MidiNote note, boolean on) {
    MidiChannel midiChannel = this.midiChannel.getEnum();
    if (note.getChannel() == midiChannel.index || midiChannel == MidiChannel.ANY) {
      int pitch = note.getPitch();

      switch (pitch) {
      case FILTER_LPF:
      case FILTER_HPF:

      case BEAT_FX_ASSIGN1:
      case BEAT_FX_ASSIGN2:
      case BEAT_FX_ASSIGN3:
      case BEAT_FX_ASSIGN4:
      case BEAT_FX_ASSIGN5:
      case BEAT_FX_ASSIGN6:
      case BEAT_FX_ASSIGN_MIC:
      case BEAT_FX_ASSIGN_MASTER:

      case SEND_MASTER_MIX:
      case SEND_SHORT_DELAY: // Build-in Sends are only one at a time
      case SEND_LONG_DELAY:
      case SEND_DUB_ECHO:
      case SEND_REVERB:
      case SEND_EXTERNAL1:   // External Sends have full independent on/off
      case SEND_EXTERNAL2:

      case CUE1_A:
      case CUE2_A:
      case CUE3_A:
      case CUE4_A:
      case CUE5_A:
      case CUE6_A:
      case CUE_MASTER_A:
      case CUE_LINK_A:
      case PHONES_PRE_EQ_A:
      case PHONES_MONO_SPLIT_A:
      case CUE1_B:
      case CUE2_B:
      case CUE3_B:
      case CUE4_B:
      case CUE5_B:
      case CUE6_B:
      case CUE_MASTER_B:
      case CUE_LINK_B:

      case ISOLATOR_ON:

      case MULTIIO_CH_SELECT_MIC:
      case MULTIIO_CH_SELECT_1:
      case MULTIIO_CH_SELECT_2:
      case MULTIIO_CH_SELECT_3:
      case MULTIIO_CH_SELECT_4:
      case MULTIIO_CH_SELECT_5:
      case MULTIIO_CH_SELECT_6:
      case MULTIIO_CH_SELECT_MASTER:
      case MULTIIO_MODE:

      case AUTO_TAP:
      case QUANTIZE:
      case XPAD_TOUCH:

      case INPUT1_A:
      case INPUT1_B:
      case INPUT1_DIGITAL:
      case INPUT1_LINE:
      case INPUT1_PHONO:
      case INPUT1_BUILTIN:
      case INPUT1_EXT1:
      case INPUT1_EXT2:
      case INPUT2_A:
      case INPUT2_B:
      case INPUT2_DIGITAL:
      case INPUT2_LINE:
      case INPUT2_PHONO:
      case INPUT2_BUILTIN:
      case INPUT2_EXT1:
      case INPUT2_EXT2:
      case INPUT3_A:
      case INPUT3_B:
      case INPUT3_DIGITAL:
      case INPUT3_LINE:
      case INPUT3_PHONO:
      case INPUT3_BUILTIN:
      case INPUT3_EXT1:
      case INPUT3_EXT2:
      case INPUT4_A:
      case INPUT4_B:
      case INPUT4_DIGITAL:
      case INPUT4_LINE:
      case INPUT4_PHONO:
      case INPUT4_BUILTIN:
      case INPUT4_EXT1:
      case INPUT4_EXT2:
      case INPUT5_A:
      case INPUT5_B:
      case INPUT5_DIGITAL:
      case INPUT5_LINE:
      case INPUT5_PHONO:
      case INPUT5_BUILTIN:
      case INPUT5_EXT1:
      case INPUT5_EXT2:
      case INPUT6_A:
      case INPUT6_B:
      case INPUT6_DIGITAL:
      case INPUT6_LINE:
      case INPUT6_PHONO:
      case INPUT6_BUILTIN:
      case INPUT6_EXT1:
      case INPUT6_EXT2:
        // Not implemented
        return;
      }

      LXMidiEngine.error("DJM-V10 UNMAPPED Note: " + note + " " + on);
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
  public void programChangeReceived(MidiProgramChange pc) {
  }

  @Override
  public void pitchBendReceived(MidiPitchBend pitchBend) {
  }

  @Override
  public void aftertouchReceived(MidiAftertouch aftertouch) {
  }

  @Override
  public void dispose() {
    this.aChannel.removeListener(this.aChannelListener);
    this.bChannel.removeListener(this.bChannelListener);
    this.sendSensitivity.removeListener(sendSensitivityListener);
    this.eqRangeMax.removeListener(this.eqRangeMaxListener);
    this.smartXF.removeListener(smartXFListener);
    this.xfMode.removeListener(xfModeListener);
    super.dispose();
  }
}
