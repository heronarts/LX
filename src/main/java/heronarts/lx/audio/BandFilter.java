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
import heronarts.lx.LXCategory;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.AUDIO)
@LXModulator.Global("Band Filter")
@LXModulator.Device("Band Filter")
public class BandFilter extends LXModulator implements LXNormalizedParameter, LXOscComponent {

  private static final int NYQUIST_FREQ = 24000;

  /**
   * Gain of the meter, in decibels
   */
  public final BoundedParameter gain =
    new BoundedParameter("Gain", 0, -48, 48)
    .setDescription("Sets the gain of the meter in dB")
    .setUnits(LXParameter.Units.DECIBELS);

  /**
   * Range of the meter, in decibels.
   */
  public final BoundedParameter range =
    new BoundedParameter("Range", 36, 6, 96)
    .setDescription("Sets the range of the meter in dB")
    .setUnits(LXParameter.Units.DECIBELS);

  /**
   * Meter attack time, in milliseconds
   */
  public final BoundedParameter attack =
    new BoundedParameter("Attack", 10, 0, 100)
    .setDescription("Sets the attack time of the meter response")
    .setUnits(LXParameter.Units.MILLISECONDS_RAW);

  /**
   * Meter release time, in milliseconds
   */
  public final BoundedParameter release =
    new BoundedParameter("Release", 100, 0, 1000)
    .setDescription("Sets the release time of the meter response")
    .setExponent(2)
    .setUnits(LXParameter.Units.MILLISECONDS_RAW);

  /**
   * dB/octave slope applied to the equalizer
   */
  public final BoundedParameter slope =
    new BoundedParameter("Slope", 4.5, -3, 12)
    .setDescription("Sets the slope of the meter in dB per octave")
    .setUnits(LXParameter.Units.DECIBELS);

  /**
   * Minimum frequency for the band
   */
  public final BoundedParameter minFreq;

  /**
   * Maximum frequency for the band
   */
  public final BoundedParameter maxFreq;

  public final GraphicMeter meter;

  private double averageOctave = 1;

  private float averageRaw = 0;

  protected double averageNorm = 0;

  private final LXMeterImpl impl;

  public BandFilter(LX lx) {
    this("Filter", lx);
  }

  public BandFilter(String label, LX lx) {
    this(label, lx.engine.audio.meter);
  }

  /**
   * Constructs a gate that monitors a specified frequency band
   *
   * @param label Label
   * @param meter GraphicEQ object to drive this gate
   */
  public BandFilter(String label, GraphicMeter meter) {
    super(label);

    this.impl = new LXMeterImpl(meter.numBands);
    this.meter = meter;

    this.minFreq = new BoundedParameter("Min Freq", 60, 0, NYQUIST_FREQ)
      .setDescription("Minimum frequency the gate responds to")
      .setExponent(4)
      .setUnits(LXParameter.Units.HERTZ);
    this.maxFreq = new BoundedParameter("Max Freq", 120, 0, NYQUIST_FREQ)
      .setDescription("Maximum frequency the gate responds to")
      .setExponent(4)
      .setUnits(LXParameter.Units.HERTZ);

    addParameter("gain", this.gain);
    addParameter("range", this.range);
    addParameter("attack", this.attack);
    addParameter("release", this.release);
    addParameter("slope", this.slope);
    addParameter("minFreq", this.minFreq);
    addParameter("maxFreq", this.maxFreq);

    setDescription("Average level metering for a frequency range");
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
    }
  }

  /**
   * Sets range of frequencies to look at
   *
   * @param minHz Minimum frequency
   * @param maxHz Maximum frequency
   * @return this
   */
  public BandFilter setFrequencyRange(float minHz, float maxHz) {
    this.minFreq.setValue(minHz);
    this.maxFreq.setValue(maxHz);
    return this;
  }

  public double getBand(int i) {
    return this.impl.getBand(i);
  }

  private void updateAverageOctave() {
    double averageFreq = (this.minFreq.getValue() + this.maxFreq.getValue()) / 2.;
    this.averageOctave = Math.log(averageFreq / FourierTransform.BASE_BAND_HZ) / FourierTransform.LOG_2;
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
    double averageDb = DecibelMeter.amplitudeToDecibels(this.averageRaw) + gainValue + slopeValue * this.averageOctave;
    this.averageNorm = 1 + averageDb / rangeValue;

    return LXUtils.constrain(this.averageNorm, 0, 1);
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException("BandFilter does not support setNormalized()");
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

}
