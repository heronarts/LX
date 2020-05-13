/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.audio;

import heronarts.lx.LX;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.modulator.LXTriggerSource;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.NormalizedParameter;
import heronarts.lx.utils.LXUtils;

/**
 * A frequency gate monitors a Graphic Meter for a particular frequency range and
 * triggers when that range passes a certain threshold. Note that the beat detect
 * does *not* respect the attack and release settings of the underlying meter, it
 * merely shares the raw values. The BeatDetect applies its own time-band filtering.
 */
public class BandGate extends LXModulator implements LXNormalizedParameter, LXTriggerSource, LXOscComponent {

  /**
   * Gain of the meter, in decibels
   */
  public final BoundedParameter gain = (BoundedParameter)
    new BoundedParameter("Gain", 0, -48, 48)
    .setDescription("Sets the gain of the meter in dB")
    .setUnits(LXParameter.Units.DECIBELS);

  /**
   * Range of the meter, in decibels.
   */
  public final BoundedParameter range = (BoundedParameter)
    new BoundedParameter("Range", 36, 6, 96)
    .setDescription("Sets the range of the meter in dB")
    .setUnits(LXParameter.Units.DECIBELS);

  /**
   * Meter attack time, in milliseconds
   */
  public final BoundedParameter attack = (BoundedParameter)
    new BoundedParameter("Attack", 10, 0, 100)
    .setDescription("Sets the attack time of the meter response")
    .setUnits(LXParameter.Units.MILLISECONDS);

  /**
   * Meter release time, in milliseconds
   */
  public final BoundedParameter release = (BoundedParameter)
    new BoundedParameter("Release", 100, 0, 1000)
    .setDescription("Sets the release time of the meter response")
    .setExponent(2)
    .setUnits(LXParameter.Units.MILLISECONDS);

  /**
   * dB/octave slope applied to the equalizer
   */
  public final BoundedParameter slope = (BoundedParameter)
    new BoundedParameter("Slope", 4.5, -3, 12)
    .setDescription("Sets the slope of the meter in dB per octave")
    .setUnits(LXParameter.Units.DECIBELS);

  /**
   * The gate level at which the trigger is engaged. When the signal crosses
   * this threshold, the gate fires. Value is in the normalized space from 0 to
   * 1.
   */
  public final BoundedParameter threshold =
    new BoundedParameter("Threshold", 0.8)
    .setDescription("Sets the level at which the band is triggered");

  /**
   * The floor at which the trigger releases. Once triggered, the signal must
   * fall below this amount before a new trigger may occur. This value is
   * specified as a fraction of the threshold. So, a value of 0.75 means the
   * signal must fall to 75% of the threshold value.
   */
  public final BoundedParameter floor =
    new BoundedParameter("Floor", 0.75)
    .setDescription("Sets the level the signal must drop below before being triggered again");

  /**
   * The time the trigger takes to falloff from 1 to 0 after triggered, in
   * milliseconds
   */
  public final BoundedParameter decay = (BoundedParameter)
    new BoundedParameter("Decay", 400, 0, 1600)
    .setDescription("Sets the decay time of the trigger signal")
    .setUnits(LXParameter.Units.MILLISECONDS);

  /**
   * Minimum frequency for the band
   */
  public final BoundedParameter minFreq;

  /**
   * Maximum frequency for the band
   */
  public final BoundedParameter maxFreq;

  public final GraphicMeter meter;

  /**
   * Gate parameter is set to true for one frame when the beat is triggered.
   */
  public final BooleanParameter gate =
    new BooleanParameter("Gate")
    .setDescription("Engages when the beat is first detected")
    .setMode(BooleanParameter.Mode.MOMENTARY);

  /**
   * Turn this parameter on to have this modulator tap the tempo system
   */
  public final BooleanParameter teachTempo =
    new BooleanParameter("Tap", false)
    .setDescription("When enabled, each triggering of the band taps the global tempo");

  private int tapCount = 0;

  /**
   * Level parameter is the average of the monitored band
   */
  public final NormalizedParameter average =
    new NormalizedParameter("Average")
    .setDescription("Computed average level of the audio within the frequency range");

  private float averageRaw = 0;

  /**
   * Envelope value that goes from 1 to 0 after this band is triggered
   */
  private double envelope = 0;

  private double averageOctave = 1;

  private boolean waitingForFloor = false;

  private final LXMeterImpl impl;

  public BandGate(LX lx) {
    this("Beat", lx);
  }

  public BandGate(String label, LX lx) {
    this(label, lx.engine.audio.meter);
  }

  /**
   * Constructs a gate that monitors a specified frequency band
   *
   * @param label Label
   * @param meter GraphicEQ object to drive this gate
   */
  public BandGate(String label, GraphicMeter meter) {
    super(label);
    this.impl = new LXMeterImpl(meter.numBands, meter.fft.getBandOctaveRatio());
    this.meter = meter;
    int nyquist = meter.fft.getSampleRate() / 2;
    this.minFreq = (BoundedParameter) new BoundedParameter("Min Freq", 60, 0, nyquist)
      .setDescription("Minimum frequency the gate responds to")
      .setExponent(4)
      .setUnits(LXParameter.Units.HERTZ);
    this.maxFreq = (BoundedParameter) new BoundedParameter("Max Freq", 120, 0, nyquist)
      .setDescription("Maximum frequency the gate responds to")
      .setExponent(4)
      .setUnits(LXParameter.Units.HERTZ);

    addParameter("gain", this.gain);
    addParameter("range", this.range);
    addParameter("attack", this.attack);
    addParameter("release", this.release);
    addParameter("slope", this.slope);
    addParameter("threshold", this.threshold);
    addParameter("floor", this.floor);
    addParameter("decay", this.decay);
    addParameter("minFreq", this.minFreq);
    addParameter("maxFreq", this.maxFreq);
    addParameter("gate", this.gate);
    addParameter("average", this.average);
    addParameter("tap", this.teachTempo);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.minFreq) {
      if (this.minFreq.getValue() > this.maxFreq.getValue()) {
        this.minFreq.setValue(this.maxFreq.getValue());
      } else {
        updateAverageOctave();
      }
    } else if (p == this.maxFreq) {
      if (this.maxFreq.getValue() < this.minFreq.getValue()) {
        this.maxFreq.setValue(this.minFreq.getValue());
      } else {
        updateAverageOctave();
      }
    } else if (p == this.teachTempo) {
      this.tapCount = 0;
    }
  }

  private void updateAverageOctave() {
    double averageFreq = (this.minFreq.getValue() + this.maxFreq.getValue()) / 2.;
    this.averageOctave = Math.log(averageFreq / FourierTransform.BASE_BAND_HZ) / FourierTransform.LOG_2;
  }

  /**
   * Constructs a gate that monitors a specified frequency band
   *
   * @param meter Equalizer to monitor
   * @param minHz Minimum frequency band
   * @param maxHz Maximum frequency band
   */
  public BandGate(GraphicMeter meter, float minHz, float maxHz) {
    this("Beat", meter);
    setFrequencyRange(minHz, maxHz);
  }

  /**
   * Constructs a gate that monitors a specified frequency band
   *
   * @param label Label
   * @param meter Equalizer to monitor
   * @param minHz Minimum frequency band
   * @param maxHz Maximum frequency band
   */
  public BandGate(String label, GraphicMeter meter, int minHz, int maxHz) {
    this(label, meter);
    setFrequencyRange(minHz, maxHz);
  }

  public double getExponent() {
    throw new UnsupportedOperationException("BandGate does not support exponent");
  }

  /**
   * Sets range of frequencies to look at
   *
   * @param minHz Minimum frequency
   * @param maxHz Maximum frequency
   * @return this
   */
  public BandGate setFrequencyRange(float minHz, float maxHz) {
    this.minFreq.setValue(minHz);
    this.maxFreq.setValue(maxHz);
    return this;
  }

  public double getBand(int i) {
    return this.impl.getBand(i);
  }

  @Override
  protected double computeValue(double deltaMs) {
    float attackGain = (float) Math.exp(-deltaMs / this.attack.getValue());
    float releaseGain = (float) Math.exp(-deltaMs / this.release.getValue());
    double rangeValue = this.range.getValue();
    double gainValue = this.gain.getValue();
    double slopeValue = this.slope.getValue();

    // Computes all the underlying bands
    this.impl.compute(
      this.meter.fft,
      attackGain,
      releaseGain,
      gainValue,
      rangeValue,
      slopeValue
    );

    float newAverage = this.meter.fft.getAverage(this.minFreq.getValuef(), this.maxFreq.getValuef()) / this.meter.fft.getSize();
    float averageGain = (newAverage >= this.averageRaw) ? attackGain : releaseGain;
    this.averageRaw = newAverage + averageGain * (this.averageRaw - newAverage);
    double averageDb = 20 * Math.log(this.averageRaw) / DecibelMeter.LOG_10 + gainValue + slopeValue * this.averageOctave;

    double averageNorm = 1 + averageDb / rangeValue;
    this.average.setValue(LXUtils.constrain(averageNorm, 0, 1));

    double thresholdValue = this.threshold.getValue();

    if (this.waitingForFloor) {
      double floorValue = thresholdValue * this.floor.getValue();
      if (averageNorm < floorValue) {
        this.waitingForFloor = false;
      }
    }

    boolean triggered = !this.waitingForFloor && (thresholdValue > 0) && (averageNorm >= thresholdValue);
    if (triggered) {
      if (this.teachTempo.isOn()) {
        this.lx.engine.tempo.tap();
        if (++this.tapCount >= 4) {
          this.teachTempo.setValue(false);
        }
      }
      this.waitingForFloor = true;
      this.envelope = 1;
    } else {
      this.envelope = Math.max(0, this.envelope - deltaMs / this.decay.getValue());
    }
    this.gate.setValue(triggered);

    return this.envelope;
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException("BandGate does not support setNormalized()");
  }

  @Override
  public double getNormalized() {
    return this.envelope;
  }

  @Override
  public float getNormalizedf() {
    return (float) getNormalized();
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.gate;
  }
}
