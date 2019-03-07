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
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.BoundedParameter.Range;
import heronarts.lx.parameter.LXParameter.Polarity;

public class DJM900nxs2 extends LXMidiSurface {

  public static final String DEVICE_NAME = "DJM-900NXS2";

  // MIDI ControlChanges
  public static final int MASTER_FADER = 24;
  public static final int BALANCE = 23;
  public static final int BOOTH_FADER = 25;
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
  public static final int COLOR_FX_SPACE = 105;
  public static final int COLOR_FX_DUBECHO = 107;
  public static final int COLOR_FX_SWEEP = 106;
  public static final int COLOR_FX_NOISE = 85;
  public static final int COLOR_FX_CRUSH = 86;
  public static final int COLOR_FX_FILTER = 87;


  public static final int CUE1 = 70;
  public static final int CUE2 = 71;
  public static final int CUE3 = 72;
  public static final int CUE4 = 73;
  public static final int CUE_MASTER = 74;
  public static final int CUE_LINK = 115;

  public static final int PHONES_MIXING = 27;
  public static final int PHONES_LEVEL = 26;


  public static final int SENDRETURN_SOURCE = 111;
  public static final int SENDRETURN_TYPE = 112;
  public static final int SENDRETURN_LEVEL = 113;
  public static final int SENDRETURN_ONOFF = 64;


  public static final int PITCH_SOMETHING1 = 13;
  public static final int PITCH_SOMETHING2 = 45;
  public static final int BEAT_LOWER = 76;
  public static final int BEAT_HIGHER = 77;
  public static final int AUTO_TAP = 69;
  public static final int TAP_TEMPO = 78;


  public static final int FX_LOW = 104;
  public static final int FX_MID = 103;
  public static final int FX_HIGH = 102;

  public static final int FX_TYPE_DELAY = 42;
  public static final int FX_TYPE_ECHO = 55;
  public static final int FX_TYPE_PINGPONG = 51;
  public static final int FX_TYPE_SPIRAL = 43;
  public static final int FX_TYPE_REVERB = 45;
  public static final int FX_TYPE_TRANS = 54; //?
  public static final int FX_TYPE_FILTER = 59;
  public static final int FX_TYPE_FLANGER = 50;
  public static final int FX_TYPE_PHASER = 57;
  public static final int FX_TYPE_PITCH = 47; //?
  public static final int FX_TYPE_SLIPROLL = 58; //?
  public static final int FX_TYPE_ROLL = 46; //?
  public static final int FX_TYPE_VINYLBRAKE = 61; //?
  public static final int FX_TYPE_HELIX = 62; //?

  public static final int FX_TARGET_CROSSFADER_B = 40;
  public static final int FX_TARGET_CROSSFADER_A = 39;
  public static final int FX_TARGET_MIC = 38;
  public static final int FX_TARGET_CHANNEL1 = 34;
  public static final int FX_TARGET_CHANNEL2 = 35;
  public static final int FX_TARGET_CHANNEL3 = 36;
  public static final int FX_TARGET_CHANNEL4 = 37;
  public static final int FX_TARGET_MASTER = 41;

  public static final int FX_LEVEL = 91;
  public static final int FX_ONOFF = 114;

  // MIDI Notes
  public static final int QUANTIZE = 118;


  // Raw knob positions from MIDI
  public final CompoundParameter low1raw = new CompoundParameter("low1raw");
  public final CompoundParameter low2raw = new CompoundParameter("low2raw");
  public final CompoundParameter low3raw = new CompoundParameter("low3raw");
  public final CompoundParameter low4raw = new CompoundParameter("low4raw");
  public final CompoundParameter mid1raw = new CompoundParameter("mid1raw");
  public final CompoundParameter mid2raw = new CompoundParameter("mid2raw");
  public final CompoundParameter mid3raw = new CompoundParameter("mid3raw");
  public final CompoundParameter mid4raw = new CompoundParameter("mid4raw");
  public final CompoundParameter high1raw = new CompoundParameter("high1raw");
  public final CompoundParameter high2raw = new CompoundParameter("high2raw");
  public final CompoundParameter high3raw = new CompoundParameter("high3raw");
  public final CompoundParameter high4raw = new CompoundParameter("high4raw");

  public final CompoundParameter fade1 = new CompoundParameter("fade1", 1);
  public final CompoundParameter fade2 = new CompoundParameter("fade2", 1);
  public final CompoundParameter fade3 = new CompoundParameter("fade3", 1);
  public final CompoundParameter fade4 = new CompoundParameter("fade4", 1);

  public final CompoundParameter masterFader = new CompoundParameter("masterFader");
  public final CompoundParameter boothMonitor = new CompoundParameter("boothMonitor");
  public final CompoundParameter crossfader = new CompoundParameter("crossFader");

  public final CompoundParameter color1raw = (CompoundParameter)new CompoundParameter("color1raw").setPolarity(Polarity.BIPOLAR);
  public final CompoundParameter color2raw = (CompoundParameter)new CompoundParameter("color2raw").setPolarity(Polarity.BIPOLAR);
  public final CompoundParameter color3raw = (CompoundParameter)new CompoundParameter("color3raw").setPolarity(Polarity.BIPOLAR);
  public final CompoundParameter color4raw = (CompoundParameter)new CompoundParameter("color4raw").setPolarity(Polarity.BIPOLAR);
  public final CompoundParameter colorParameter = new CompoundParameter("colorParam", 0.55, 0.1, 1);
  public final CompoundParameter colorSensitivity = (CompoundParameter) new CompoundParameter("ColorSensitivity", 2, 1, 3)
    .setDescription("Color  knob sensitivity. Adjust per DJ.")
    .addListener((p) -> {
        recalculateAllColors();
      });

  // Normalized EQ values
  public final CompoundParameter low1 = new CompoundParameter("low1");
  public final CompoundParameter low2 = new CompoundParameter("low2");
  public final CompoundParameter low3 = new CompoundParameter("low3");
  public final CompoundParameter low4 = new CompoundParameter("low4");
  public final CompoundParameter mid1 = new CompoundParameter("mid1");
  public final CompoundParameter mid2 = new CompoundParameter("mid2");
  public final CompoundParameter mid3 = new CompoundParameter("mid3");
  public final CompoundParameter mid4 = new CompoundParameter("mid4");
  public final CompoundParameter high1 = new CompoundParameter("high1");
  public final CompoundParameter high2 = new CompoundParameter("high2");
  public final CompoundParameter high3 = new CompoundParameter("high3");
  public final CompoundParameter high4 = new CompoundParameter("high4");
  Range eqRange = new Range(0, 0.5);
  public final CompoundParameter eqRangeMax = (CompoundParameter) new CompoundParameter("EQmax", 0.5)
    .setDescription("Equalizer knob value that will act as a maximum position. Adjust per DJ. Defaults to center.")
    .addListener(new LXParameterListener() {
      public void onParameterChanged(LXParameter p) {
        eqRange = new Range(0, eqRangeMax.getValue());
        recalculateAllEq();
      } });

  // Normalized EQ values multiplied by faders
  public final CompoundParameter low1net = new CompoundParameter("low1net");
  public final CompoundParameter low2net = new CompoundParameter("low2net");
  public final CompoundParameter low3net = new CompoundParameter("low3net");
  public final CompoundParameter low4net = new CompoundParameter("low4net");
  public final CompoundParameter mid1net = new CompoundParameter("mid1net");
  public final CompoundParameter mid2net = new CompoundParameter("mid2net");
  public final CompoundParameter mid3net = new CompoundParameter("mid3net");
  public final CompoundParameter mid4net = new CompoundParameter("mid4net");
  public final CompoundParameter high1net = new CompoundParameter("high1net");
  public final CompoundParameter high2net = new CompoundParameter("high2net");
  public final CompoundParameter high3net = new CompoundParameter("high3net");
  public final CompoundParameter high4net = new CompoundParameter("high4net");

  // Normalized color values (center knob position will return zero)
  public final CompoundParameter color1 = new CompoundParameter("color1");
  public final CompoundParameter color2 = new CompoundParameter("color2");
  public final CompoundParameter color3 = new CompoundParameter("color3");
  public final CompoundParameter color4 = new CompoundParameter("color4");

  // Calculated net levels for each channel
  public final CompoundParameter level1net = new CompoundParameter("level1net", 0, 3); // Fade1 * Sum(low1,mid1,high1)
  public final CompoundParameter level2net = new CompoundParameter("level2net", 0, 3);
  public final CompoundParameter level3net = new CompoundParameter("level3net", 0, 3);
  public final CompoundParameter level4net = new CompoundParameter("level4net", 0, 3);


  // Parameter groupings for convenient access
  CompoundParameter[] lowraw = { low1raw, low2raw, low3raw, low4raw };
  CompoundParameter[] midraw = { mid1raw, mid2raw, mid3raw, mid4raw };
  CompoundParameter[] highraw = { high1raw, high2raw, high3raw, high4raw };
  CompoundParameter[] low = { low1, low2, low3, low4 };
  CompoundParameter[] mid = { mid1, mid2, mid3, mid4 };
  CompoundParameter[] high = { high1, high2, high3, high4 };
  CompoundParameter[] lowNnet = { low1net, low2net, low3net, low4net };
  CompoundParameter[] midNnet = { mid1net, mid2net, mid3net, mid4net };
  CompoundParameter[] highNnet = { high1net, high2net, high3net, high4net };
  CompoundParameter[] fade = { fade1, fade2, fade3, fade4 };
  CompoundParameter[] levelNnet = { level1net, level2net, level3net, level4net };
  CompoundParameter[] colorraw = { color1raw, color2raw, color3raw, color4raw };
  CompoundParameter[] color = { color1, color2, color3, color4 };


  // A/B channel abstraction for retaining mappings when target channels are changed
  public final DiscreteParameter aChannel = (DiscreteParameter)new DiscreteParameter("AChan", 1, 1, 4+1)
    .addListener((p) -> {
      updateAeq();
      updateAcolor();
      updateLowNet();
      updateMidNet();
      updateHighNet();
    });

  public final DiscreteParameter bChannel = (DiscreteParameter)new DiscreteParameter("BChan", 2, 1, 4+1)
    .addListener((p) -> {
      updateBeq();
      updateBcolor();
      updateLowNet();
      updateMidNet();
      updateHighNet();
    });

  public final CompoundParameter lowA = new CompoundParameter("lowA");
  public final CompoundParameter lowB = new CompoundParameter("lowB");
  public final CompoundParameter midA = new CompoundParameter("midA");
  public final CompoundParameter midB = new CompoundParameter("midB");
  public final CompoundParameter highA = new CompoundParameter("highA");
  public final CompoundParameter highB = new CompoundParameter("highB");
  public final CompoundParameter lowAnet = new CompoundParameter("lowA");
  public final CompoundParameter lowBnet = new CompoundParameter("lowB");
  public final CompoundParameter midAnet = new CompoundParameter("midA");
  public final CompoundParameter midBnet = new CompoundParameter("midB");
  public final CompoundParameter highAnet = new CompoundParameter("highA");
  public final CompoundParameter highBnet = new CompoundParameter("highB");
  public final CompoundParameter fadeA = new CompoundParameter("fadeA");
  public final CompoundParameter fadeB = new CompoundParameter("fadeB");
  public final CompoundParameter colorA = new CompoundParameter("colorA");
  public final CompoundParameter colorB = new CompoundParameter("colorB");
  public final CompoundParameter levelAnet = new CompoundParameter("levelAnet", 0, 3);
  public final CompoundParameter levelBnet = new CompoundParameter("levelBnet", 0, 3);

  // Calculated values for A/B
  public final CompoundParameter lowNet = new CompoundParameter("lowNet");  // Max(lowA*fadeA, lowB*fadeB)
  public final CompoundParameter midNet = new CompoundParameter("midNet");
  public final CompoundParameter highNet = new CompoundParameter("highNet");

  public final CompoundParameter smartXF = new CompoundParameter("SmartXF")
    .setDescription("Crossfader position calculated using relative levels of A vs B");


  public DJM900nxs2(LX lx, LXMidiInput input, LXMidiOutput output) {
    super(lx, input, output);
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
    for (int c=1; c<5; c++) {
      updateLow(c, .5);
      updateMid(c, .5);
      updateHigh(c, .5);
      updateFade(c, 1);
      updateColor(c, .5);
    }
  }

  protected void recalculateAllEq() {
    for (int c = 1; c<5; c++) {
      low[c].setValue(scaleEq(lowraw[c].getValue()));
      mid[c].setValue(scaleEq(midraw[c].getValue()));
      high[c].setValue(scaleEq(highraw[c].getValue()));
      lowNnet[c].setValue(low[c].getValue() * fade[c].getValue());
      midNnet[c].setValue(mid[c].getValue() * fade[c].getValue());
      highNnet[c].setValue(high[c].getValue() * fade[c].getValue());
      updateLevelNet(c);
    }
    updateAeq();
    updateBeq();
    updateLowNet();
    updateMidNet();
    updateHighNet();
  }

  protected double scaleEq(double value) {
    return this.eqRange.getNormalized(value);
  }

  protected void updateLow(int channel, double value) {
    lowraw[channel-1].setValue(value);
    low[channel-1].setValue(scaleEq(value));
    lowNnet[channel-1].setValue(low[channel-1].getValue() * fade[channel-1].getValue());
    updateLevelNet(channel);
    updateABeq(channel);
    updateLowNet();
  }

  protected void updateMid(int channel, double value) {
    midraw[channel-1].setValue(value);
    mid[channel-1].setValue(scaleEq(value));
    midNnet[channel-1].setValue(mid[channel-1].getValue() * fade[channel-1].getValue());
    updateLevelNet(channel);
    updateABeq(channel);
    updateMidNet();
  }

  protected void updateHigh(int channel, double value) {
    highraw[channel-1].setValue(value);
    high[channel-1].setValue(scaleEq(value));
    highNnet[channel-1].setValue(high[channel-1].getValue() * fade[channel-1].getValue());
    updateLevelNet(channel);
    updateABeq(channel);
    updateHighNet();
  }

  protected void updateFade(int channel, double value) {
    fade[channel-1].setValue(value);
    lowNnet[channel-1].setValue(low[channel-1].getValue() * value);
    midNnet[channel-1].setValue(mid[channel-1].getValue() * value);
    highNnet[channel-1].setValue(high[channel-1].getValue() * value);
    updateLevelNet(channel);
    updateABeq(channel);
  }

  protected void updateLevelNet(int channel) {
    int i = channel - 1;
    levelNnet[i].setValue(fade[i].getValue() * (low[i].getValue() + mid[i].getValue() + high[i].getValue()));
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

  protected void updateABeq(int channel) {
    if (this.aChannel.getValuei() == channel) {
      updateAeq(channel);
    }
    if (this.bChannel.getValuei() == channel) {
      updateBeq(channel);
    }
  }

  protected void updateAeq() {
    updateAeq(this.aChannel.getValuei());
  }

  protected void updateBeq() {
    updateBeq(this.bChannel.getValuei());
  }

  protected void updateAeq(int channel) {
    lowA.setValue(low[channel-1].getValue());
    midA.setValue(mid[channel-1].getValue());
    highA.setValue(high[channel-1].getValue());
    fadeA.setValue(fade[channel-1].getValue());
    lowAnet.setValue(lowNnet[channel-1].getValue());
    midAnet.setValue(midNnet[channel-1].getValue());
    highAnet.setValue(highNnet[channel-1].getValue());
    levelAnet.setValue(levelNnet[channel-1].getValue());
    updateSmartXF();
  }

  protected void updateBeq(int channel) {
    lowB.setValue(low[channel-1].getValue());
    midB.setValue(mid[channel-1].getValue());
    highB.setValue(high[channel-1].getValue());
    fadeB.setValue(fade[channel-1].getValue());
    lowBnet.setValue(lowNnet[channel-1].getValue());
    midBnet.setValue(midNnet[channel-1].getValue());
    highBnet.setValue(highNnet[channel-1].getValue());
    levelBnet.setValue(levelNnet[channel-1].getValue());
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
    for (int c = 0; c<4; c++) {
      double value = colorraw[c].getValue();
      color[c].setValue((value < .5 ? (.5-value) * 2 : (value-.5) * 2) * colorParameter.getValue() * colorSensitivity.getValue());
    }
    updateAcolor();
    updateBcolor();
  }

  protected void updateColor(int channel, double value) {
    colorraw[channel-1].setValue(value);
    color[channel-1].setValue((value < .5 ? (.5-value) * 2 : (value-.5) * 2) * colorParameter.getValue() * colorSensitivity.getValue());
    updateABcolor(channel);
  }

  protected void updateABcolor(int channel) {
    if (this.aChannel.getValuei() == channel) {
      updateAcolor(channel);
    }
    if (this.bChannel.getValuei() == channel) {
      updateBcolor(channel);
    }
  }

  protected void updateAcolor() {
    updateAcolor(aChannel.getValuei());
  }

  protected void updateBcolor() {
    updateBcolor(bChannel.getValuei());
  }

  protected void updateAcolor(int channel) {
    colorA.setValue(color[channel-1].getValue());
  }

  protected void updateBcolor(int channel) {
    colorB.setValue(color[channel-1].getValue());
  }


  @Override
  public void controlChangeReceived(MidiControlChange cc) {
    int number = cc.getCC();

    switch (number) {
    case LOW1:
      updateLow(1, cc.getNormalized());
      return;
    case LOW2:
      updateLow(2, cc.getNormalized());
      return;
    case LOW3:
      updateLow(3, cc.getNormalized());
      return;
    case LOW4:
      updateLow(4, cc.getNormalized());
      return;
    case MID1:
      updateMid(1, cc.getNormalized());
      return;
    case MID2:
      updateMid(2, cc.getNormalized());
      return;
    case MID3:
      updateMid(3, cc.getNormalized());
      return;
    case MID4:
      updateMid(4, cc.getNormalized());
      return;
    case HIGH1:
      updateHigh(1, cc.getNormalized());
      return;
    case HIGH2:
      updateHigh(2, cc.getNormalized());
      return;
    case HIGH3:
      updateHigh(3, cc.getNormalized());
      return;
    case HIGH4:
      updateHigh(4, cc.getNormalized());
      return;
    case CHANNEL_FADER1:
      updateFade(1, cc.getNormalized());
      return;
    case CHANNEL_FADER2:
      updateFade(2, cc.getNormalized());
      return;
    case CHANNEL_FADER3:
      updateFade(3, cc.getNormalized());
      return;
    case CHANNEL_FADER4:
      updateFade(4, cc.getNormalized());
      return;
    case MASTER_FADER:
      this.masterFader.setNormalized(cc.getNormalized());
      return;
    case BOOTH_FADER:
      this.boothMonitor.setNormalized(cc.getNormalized());
      return;
    case CROSSFADER:
      this.crossfader.setNormalized(cc.getNormalized());
      return;
    case COLOR1:
      updateColor(1, cc.getNormalized());
      return;
    case COLOR2:
      updateColor(2, cc.getNormalized());
      return;
    case COLOR3:
      updateColor(3, cc.getNormalized());
      return;
    case COLOR4:
      updateColor(4, cc.getNormalized());
      return;
    case COLOR_PARAMETER:
      this.colorParameter.setNormalized(cc.getNormalized());
      recalculateAllColors();
      return;
    }

    //System.out.println("DJM-900NXS2 UNMAPPED CC: " + cc);
  }

  private void noteReceived(MidiNote note, boolean on) {
    int pitch = note.getPitch();

    switch (pitch) {
    case QUANTIZE:
      return;
    }

    //System.out.println("DJM-900NXS2 UNMAPPED Note: " + note + " " + on);
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

}
