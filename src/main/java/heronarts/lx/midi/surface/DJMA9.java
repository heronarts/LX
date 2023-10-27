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
 * @author Justin K. Belcher <justin@jkb.studio>
 */

package heronarts.lx.midi.surface;

import heronarts.lx.LX;
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

/**
 * DJM-A9 INSTRUCTIONS
 *
 * On the mixer, under My Settings > MIDI:
 *  -Button Type MUST be set to "Toggle". Otherwise only the
 *   button state is sent to midi instead of function state.
 *  -Any midi channel is usable, just set this class' midiChannel parameter to match.
 */
public class DJMA9 extends LXMidiSurface {

  public static final String DEVICE_NAME = "DJM-A9";

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
    FOUR(3);

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

  public static final int MASTER_FADER = 24;
  public static final int BOOTH_FADER = 25;
  public static final int BOOTH_EQ_HI = 109;
  public static final int BOOTH_EQ_LOW = 110;

  public static final int MULTIIO_SENDCH_1 = 34;
  public static final int MULTIIO_SENDCH_2 = 35;
  public static final int MULTIIO_SENDCH_3 = 36;
  public static final int MULTIIO_SENDCH_4 = 37;
  public static final int MULTIIO_SENDCH_MIC = 38;
  public static final int MULTIIO_SENDCH_CFA = 39;
  public static final int MULTIIO_SENDCH_CFB = 40;
  public static final int MULTIIO_SENDCH_MASTER = 41;
  public static final int MULTIIO_LEVEL = 113;

  public static final int XPAD_SLIDER = 116;

  public static final int BEAT_LEFT = 76;
  public static final int BEAT_RIGHT = 77;
  public static final int AUTO_TAP = 69;
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
  public static final int FX_TYPE_FLANGER = 50;
  public static final int FX_TYPE_PHASER = 57;
  public static final int FX_TYPE_FILTER = 59;
  public static final int FX_TYPE_TRIPLETFILTER = 58;
  public static final int FX_TYPE_TRANS = 53;
  public static final int FX_TYPE_ROLL = 46;
  public static final int FX_TYPE_TRIPLETROLL = 47;
  public static final int FX_TYPE_MOBIUS = 61;

  public static final int FX_LEVEL = 91;
  public static final int FX_ONOFF = 114;

  // Three-position slide values
  public static final int SLIDE_LEFT = 0;
  public static final int SLIDE_CENTER = 64;
  public static final int SLIDE_RIGHT = 127;

  public static final int CROSSFADER = 11;

  public static final int EQ_CURVE = 33;
  public static final int CHANNEL_FADER_CURVE = 94;
  public static final int CROSSFADER_CURVE = 95;

  public static final int CHANNEL_FADER1 = 17;
  public static final int CHANNEL_FADER2 = 18;
  public static final int CHANNEL_FADER3 = 19;
  public static final int CHANNEL_FADER4 = 20;

  public static final int CROSSFADER_ASSIGN1 = 65;
  public static final int CROSSFADER_ASSIGN2 = 66;
  public static final int CROSSFADER_ASSIGN3 = 67;
  public static final int CROSSFADER_ASSIGN4 = 68;

  public static final int TRIM1 = 1;
  public static final int TRIM2 = 6;
  public static final int TRIM3 = 12;
  public static final int TRIM4 = 80;

  public static final int HIGH1 = 2;
  public static final int HIGH2 = 7;
  public static final int HIGH3 = 14;
  public static final int HIGH4 = 81;

  public static final int MID1 = 3;
  public static final int MID2 = 8;
  public static final int MID3 = 15;
  public static final int MID4 = 92;

  public static final int LOW1 = 4;
  public static final int LOW2 = 9;
  public static final int LOW3 = 21;
  public static final int LOW4 = 82;

  public static final int COLOR1 = 5;
  public static final int COLOR2 = 10;
  public static final int COLOR3 = 22;
  public static final int COLOR4 = 83;

  public static final int COLOR_PARAMETER = 108;

  public static final int PHONES_MIX_A = 27;
  public static final int PHONES_LEVEL_A = 26;
  public static final int PHONES_MIX_B = 85;
  public static final int PHONES_LEVEL_B = 86;

  public static final int MIC_EQ_HIGH = 30;
  public static final int MIC_EQ_LOW = 31;
  public static final int MIC_FX_ECHO = 97;
  public static final int MIC_FX_PITCH = 98;
  public static final int MIC_FX_MEGAPHONE = 99;
  public static final int MIC_FX_PARAMETER = 100;
  public static final int MIC_REVERB_PARAMETER = 101;

  // MIDI Notes
  public static final int MULTIIO_INSERT_SOURCE = 111;

  public static final int QUANTIZE = 118;

  public static final int BEAT_FX_ASSIGN1 = 1;
  public static final int BEAT_FX_ASSIGN2 = 2;
  public static final int BEAT_FX_ASSIGN3 = 3;
  public static final int BEAT_FX_ASSIGN4 = 4;
  public static final int BEAT_FX_ASSIGN_MIC = 5;
  public static final int BEAT_FX_ASSIGN_CFA = 6;
  public static final int BEAT_FX_ASSIGN_CFB = 7;
  public static final int BEAT_FX_ASSIGN_MASTER = 8;

  public static final int COLOR_FX_SPACE = 85;
  public static final int COLOR_FX_DUBECHO = 105;
  public static final int COLOR_FX_CRUSH = 106;
  public static final int COLOR_FX_SWEEP = 107;
  public static final int COLOR_FX_NOISE = 86;
  public static final int COLOR_FX_FILTER = 87;

  public static final int CUE1_A = 10;
  public static final int CUE2_A = 11;
  public static final int CUE3_A = 12;
  public static final int CUE4_A = 13;
  public static final int CUE_MASTER_A = 14;
  public static final int CUE_LINK_A = 15;
  public static final int PHONES_MONO_SPLIT_A = 33;
  public static final int CUE1_B = 16;
  public static final int CUE2_B = 17;
  public static final int CUE3_B = 18;
  public static final int CUE4_B = 19;
  public static final int CUE_MASTER_B = 20;
  public static final int CUE_LINK_B = 21;
  public static final int PHONES_MONO_SPLIT_B = 34;

  public static final int MIC_TALKOVER = 97;
  public static final int MIC_REVERB = 96;
  public static final int MIC_ON = 98;

  // Raw knob positions from MIDI
  public final BoundedParameter low1raw = new BoundedParameter("low1raw");
  public final BoundedParameter low2raw = new BoundedParameter("low2raw");
  public final BoundedParameter low3raw = new BoundedParameter("low3raw");
  public final BoundedParameter low4raw = new BoundedParameter("low4raw");
  public final BoundedParameter mid1raw = new BoundedParameter("mid1raw");
  public final BoundedParameter mid2raw = new BoundedParameter("mid2raw");
  public final BoundedParameter mid3raw = new BoundedParameter("mid3raw");
  public final BoundedParameter mid4raw = new BoundedParameter("mid4raw");
  public final BoundedParameter high1raw = new BoundedParameter("high1raw");
  public final BoundedParameter high2raw = new BoundedParameter("high2raw");
  public final BoundedParameter high3raw = new BoundedParameter("high3raw");
  public final BoundedParameter high4raw = new BoundedParameter("high4raw");

  public final BoundedParameter fade1 = new BoundedParameter("fade1", 1);
  public final BoundedParameter fade2 = new BoundedParameter("fade2", 1);
  public final BoundedParameter fade3 = new BoundedParameter("fade3", 1);
  public final BoundedParameter fade4 = new BoundedParameter("fade4", 1);

  public final BoundedParameter masterFader = new BoundedParameter("masterFader");
  public final BoundedParameter boothMonitor = new BoundedParameter("boothMonitor");
  public final BoundedParameter crossfader = new BoundedParameter("crossFader");

  public final BoundedParameter color1raw = new BoundedParameter("color1raw").setPolarity(Polarity.BIPOLAR);
  public final BoundedParameter color2raw = new BoundedParameter("color2raw").setPolarity(Polarity.BIPOLAR);
  public final BoundedParameter color3raw = new BoundedParameter("color3raw").setPolarity(Polarity.BIPOLAR);
  public final BoundedParameter color4raw = new BoundedParameter("color4raw").setPolarity(Polarity.BIPOLAR);
  public final BoundedParameter colorParameter = new BoundedParameter("colorParam", 0.55, 0.1, 1);
  public final BoundedParameter colorSensitivity = new BoundedParameter("ColorSensitivity", 2, 1, 3)
    .setDescription("Color knob sensitivity. Adjust per DJ.");

  // Normalized EQ values
  public final BoundedParameter low1 = new BoundedParameter("low1");
  public final BoundedParameter low2 = new BoundedParameter("low2");
  public final BoundedParameter low3 = new BoundedParameter("low3");
  public final BoundedParameter low4 = new BoundedParameter("low4");
  public final BoundedParameter mid1 = new BoundedParameter("mid1");
  public final BoundedParameter mid2 = new BoundedParameter("mid2");
  public final BoundedParameter mid3 = new BoundedParameter("mid3");
  public final BoundedParameter mid4 = new BoundedParameter("mid4");
  public final BoundedParameter high1 = new BoundedParameter("high1");
  public final BoundedParameter high2 = new BoundedParameter("high2");
  public final BoundedParameter high3 = new BoundedParameter("high3");
  public final BoundedParameter high4 = new BoundedParameter("high4");

  public final BoundedParameter eqRangeMax = new BoundedParameter("EQmax", 0.5)
    .setDescription("Equalizer knob value that will act as a maximum position. Adjust per DJ. Defaults to center.");

  // Normalized EQ values multiplied by faders
  public final BoundedParameter low1net = new BoundedParameter("low1net");
  public final BoundedParameter low2net = new BoundedParameter("low2net");
  public final BoundedParameter low3net = new BoundedParameter("low3net");
  public final BoundedParameter low4net = new BoundedParameter("low4net");
  public final BoundedParameter mid1net = new BoundedParameter("mid1net");
  public final BoundedParameter mid2net = new BoundedParameter("mid2net");
  public final BoundedParameter mid3net = new BoundedParameter("mid3net");
  public final BoundedParameter mid4net = new BoundedParameter("mid4net");
  public final BoundedParameter high1net = new BoundedParameter("high1net");
  public final BoundedParameter high2net = new BoundedParameter("high2net");
  public final BoundedParameter high3net = new BoundedParameter("high3net");
  public final BoundedParameter high4net = new BoundedParameter("high4net");

  // Normalized color values (center knob position will return zero)
  public final BoundedParameter color1 = new BoundedParameter("color1");
  public final BoundedParameter color2 = new BoundedParameter("color2");
  public final BoundedParameter color3 = new BoundedParameter("color3");
  public final BoundedParameter color4 = new BoundedParameter("color4");

  // Calculated net levels for each channel
  public final BoundedParameter level1net = new BoundedParameter("level1net", 0, 3); // Fade1 * Sum(low1,mid1,high1)
  public final BoundedParameter level2net = new BoundedParameter("level2net", 0, 3);
  public final BoundedParameter level3net = new BoundedParameter("level3net", 0, 3);
  public final BoundedParameter level4net = new BoundedParameter("level4net", 0, 3);


  // Parameter groupings for convenient access
  private BoundedParameter[] lowraw = { low1raw, low2raw, low3raw, low4raw };
  private BoundedParameter[] midraw = { mid1raw, mid2raw, mid3raw, mid4raw };
  private BoundedParameter[] highraw = { high1raw, high2raw, high3raw, high4raw };
  private BoundedParameter[] low = { low1, low2, low3, low4 };
  private BoundedParameter[] mid = { mid1, mid2, mid3, mid4 };
  private BoundedParameter[] high = { high1, high2, high3, high4 };
  private BoundedParameter[] lowNnet = { low1net, low2net, low3net, low4net };
  private BoundedParameter[] midNnet = { mid1net, mid2net, mid3net, mid4net };
  private BoundedParameter[] highNnet = { high1net, high2net, high3net, high4net };
  private BoundedParameter[] fade = { fade1, fade2, fade3, fade4 };
  private BoundedParameter[] levelNnet = { level1net, level2net, level3net, level4net };
  private BoundedParameter[] colorraw = { color1raw, color2raw, color3raw, color4raw };
  private BoundedParameter[] color = { color1, color2, color3, color4 };

  // A/B channel abstraction for retaining mappings when target channels are changed
  public final EnumParameter<Channel> aChannel = new EnumParameter<Channel>("A Channel", Channel.TWO);
  public final EnumParameter<Channel> bChannel = new EnumParameter<Channel>("B Channel", Channel.THREE);

  public final BoundedParameter lowA = new BoundedParameter("lowA");
  public final BoundedParameter lowB = new BoundedParameter("lowB");
  public final BoundedParameter midA = new BoundedParameter("midA");
  public final BoundedParameter midB = new BoundedParameter("midB");
  public final BoundedParameter highA = new BoundedParameter("highA");
  public final BoundedParameter highB = new BoundedParameter("highB");
  public final BoundedParameter lowAnet = new BoundedParameter("lowA");
  public final BoundedParameter lowBnet = new BoundedParameter("lowB");
  public final BoundedParameter midAnet = new BoundedParameter("midA");
  public final BoundedParameter midBnet = new BoundedParameter("midB");
  public final BoundedParameter highAnet = new BoundedParameter("highA");
  public final BoundedParameter highBnet = new BoundedParameter("highB");
  public final BoundedParameter fadeA = new BoundedParameter("fadeA");
  public final BoundedParameter fadeB = new BoundedParameter("fadeB");
  public final BoundedParameter colorA = new BoundedParameter("colorA");
  public final BoundedParameter colorB = new BoundedParameter("colorB");
  public final BoundedParameter levelAnet = new BoundedParameter("levelAnet", 0, 3);
  public final BoundedParameter levelBnet = new BoundedParameter("levelBnet", 0, 3);

  // Calculated values for A/B
  public final BoundedParameter lowNet = new BoundedParameter("lowNet");  // Max(lowA*fadeA, lowB*fadeB)
  public final BoundedParameter midNet = new BoundedParameter("midNet");
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
    .setDescription("Mode for following DJM-A9 crossfader with LX");

  public DJMA9(LX lx, LXMidiInput input, LXMidiOutput output) {
    super(lx, input, output);
    addSetting("midiChannel", this.midiChannel);
    addSetting("xfMode", this.xfMode);
    addSetting("aChannel", this.aChannel);
    addSetting("bChannel", this.bChannel);

    this.aChannel.addListener(this.aChannelListener);
    this.bChannel.addListener(this.bChannelListener);
    this.colorSensitivity.addListener(colorSensitivityListener);
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
    this.colorParameter.setNormalized(.5);
    for (Channel channel : ALL_CHANNELS) {
      updateLow(channel, .5);
      updateMid(channel, .5);
      updateHigh(channel, .5);
      updateFade(channel, 1);
      updateColor(channel, .5);
    }
  }

  private final LXParameterListener aChannelListener = (p) -> {
    updateAeq();
    updateAcolor();
    updateLowNet();
    updateMidNet();
    updateHighNet();
  };

  private final LXParameterListener bChannelListener = (p) -> {
    updateBeq();
    updateBcolor();
    updateLowNet();
    updateMidNet();
    updateHighNet();
  };

  private final LXParameterListener colorSensitivityListener = (p) -> {
    recalculateAllColors();
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
      mid[c].setValue(scaleEq(midraw[c].getValue()));
      high[c].setValue(scaleEq(highraw[c].getValue()));
      lowNnet[c].setValue(low[c].getValue() * fade[c].getValue());
      midNnet[c].setValue(mid[c].getValue() * fade[c].getValue());
      highNnet[c].setValue(high[c].getValue() * fade[c].getValue());
      updateLevelNet(channel);
    }
    updateAeq();
    updateBeq();
    updateLowNet();
    updateMidNet();
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

  protected void updateMid(Channel channel, double value) {
    int c = channel.getIndex();
    midraw[c].setValue(value);
    mid[c].setValue(scaleEq(value));
    midNnet[c].setValue(mid[c].getValue() * fade[c].getValue());
    updateLevelNet(channel);
    updateABeq(channel);
    updateMidNet();
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
    midNnet[c].setValue(mid[c].getValue() * value);
    highNnet[c].setValue(high[c].getValue() * value);
    updateLevelNet(channel);
    updateABeq(channel);
  }

  protected void updateLevelNet(Channel channel) {
    int c = channel.getIndex();
    levelNnet[c].setValue(fade[c].getValue() * (low[c].getValue() + mid[c].getValue() + high[c].getValue()));
  }

  protected void updateLowNet() {
    lowNet.setValue(Math.max(lowAnet.getValue(), lowBnet.getValue()));
  }

  protected void updateMidNet() {
    midNet.setValue(Math.max(midAnet.getValue(), midBnet.getValue()));
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
    midA.setValue(mid[c].getValue());
    highA.setValue(high[c].getValue());
    fadeA.setValue(fade[c].getValue());
    lowAnet.setValue(lowNnet[c].getValue());
    midAnet.setValue(midNnet[c].getValue());
    highAnet.setValue(highNnet[c].getValue());
    levelAnet.setValue(levelNnet[c].getValue());
    updateSmartXF();
  }

  protected void updateBeq(Channel channel) {
    int c = channel.getIndex();
    lowB.setValue(low[c].getValue());
    midB.setValue(mid[c].getValue());
    highB.setValue(high[c].getValue());
    fadeB.setValue(fade[c].getValue());
    lowBnet.setValue(lowNnet[c].getValue());
    midBnet.setValue(midNnet[c].getValue());
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

  protected void recalculateAllColors() {
    for (Channel channel : ALL_CHANNELS) {
      int c = channel.getIndex();
      double value = colorraw[c].getValue();
      color[c].setValue((value < .5 ? (.5-value) * 2 : (value-.5) * 2) * colorParameter.getValue() * colorSensitivity.getValue());
    }
    updateAcolor();
    updateBcolor();
  }

  protected void updateColor(Channel channel, double value) {
    int c = channel.getIndex();
    colorraw[c].setValue(value);
    color[c].setValue((value < .5 ? (.5-value) * 2 : (value-.5) * 2) * colorParameter.getValue() * colorSensitivity.getValue());
    updateABcolor(channel);
  }

  protected void updateABcolor(Channel channel) {
    if (this.aChannel.getEnum().equals(channel)) {
      updateAcolor(channel);
    }
    if (this.bChannel.getEnum().equals(channel)) {
      updateBcolor(channel);
    }
  }

  protected void updateAcolor() {
    updateAcolor(aChannel.getEnum());
  }

  protected void updateBcolor() {
    updateBcolor(bChannel.getEnum());
  }

  protected void updateAcolor(Channel channel) {
    colorA.setValue(color[channel.getIndex()].getValue());
  }

  protected void updateBcolor(Channel channel) {
    colorB.setValue(color[channel.getIndex()].getValue());
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
      case MID1:
        updateMid(Channel.ONE, cc.getNormalized());
        return;
      case MID2:
        updateMid(Channel.TWO, cc.getNormalized());
        return;
      case MID3:
        updateMid(Channel.THREE, cc.getNormalized());
        return;
      case MID4:
        updateMid(Channel.FOUR, cc.getNormalized());
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
      case COLOR1:
        updateColor(Channel.ONE, cc.getNormalized());
        return;
      case COLOR2:
        updateColor(Channel.TWO, cc.getNormalized());
        return;
      case COLOR3:
        updateColor(Channel.THREE, cc.getNormalized());
        return;
      case COLOR4:
        updateColor(Channel.FOUR, cc.getNormalized());
        return;
      case COLOR_PARAMETER:
        this.colorParameter.setNormalized(cc.getNormalized());
        recalculateAllColors();
        return;
      }

      // LXMidiEngine.error("DJM-A9 UNMAPPED CC: " + cc);
    }
  }

  private void noteReceived(MidiNote note, boolean on) {
    MidiChannel midiChannel = this.midiChannel.getEnum();
    if (note.getChannel() == midiChannel.index || midiChannel == MidiChannel.ANY) {
      int pitch = note.getPitch();

      switch (pitch) {
      case QUANTIZE:
        return;
      }

      // LXMidiEngine.error("DJM-A9 UNMAPPED Note: " + note + " " + on);
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
    this.colorSensitivity.removeListener(colorSensitivityListener);
    this.eqRangeMax.removeListener(this.eqRangeMaxListener);
    this.smartXF.removeListener(smartXFListener);
    this.xfMode.removeListener(xfModeListener);
    super.dispose();
  }
}