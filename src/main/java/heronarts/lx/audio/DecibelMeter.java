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

import heronarts.lx.modulator.LXModulator;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.utils.LXUtils;

/**
 * A DecibelMeter is a modulator that returns the level of an audio signal. Gain
 * may be applied to the signal. A decibel range is given in which values are
 * normalized from 0 to 1. Raw decibel values can be accessed if desired.
 */
public class DecibelMeter extends LXModulator implements LXNormalizedParameter, LXOscComponent {

  protected static final double LOG_10 = Math.log(10);
  protected static final double RATIO_20_LOG10 = 20. / LOG_10;

  protected LXAudioBuffer buffer;

  /**
   * Gain of the meter, in decibels
   */
  public final CompoundParameter gain;

  /**
   * Range of the meter, in decibels.
   */
  public final CompoundParameter range;

  /**
   * Meter attack time, in milliseconds
   */
  public final CompoundParameter attack;

  /**
   * Meter release time, in milliseconds
   */
  public final CompoundParameter release;

  private static class Parameters {

    /**
     * Gain of the meter, in decibels
     */
    public final CompoundParameter gain =
      new CompoundParameter("Gain", 0, -48, 48)
      .setDescription("Sets the gain of the meter in dB")
      .setUnits(LXParameter.Units.DECIBELS);

    /**
     * Range of the meter, in decibels.
     */
    public final CompoundParameter range =
      new CompoundParameter("Range", 48, 6, 96)
      .setDescription("Sets the range of the meter in dB")
      .setUnits(LXParameter.Units.DECIBELS);

    /**
     * Meter attack time, in milliseconds
     */
    public final CompoundParameter attack =
      new CompoundParameter("Attack", 10, 0, 100)
      .setDescription("Sets the attack time of the meter response")
      .setUnits(LXParameter.Units.MILLISECONDS_RAW);

    /**
     * Meter release time, in milliseconds
     */
    public final CompoundParameter release =
      new CompoundParameter("Release", 100, 0, 1000)
      .setDescription("Sets the release time of the meter response")
      .setExponent(2)
      .setUnits(LXParameter.Units.MILLISECONDS_RAW);
  };

  private final static double PEAK_HOLD_MS = 250;

  protected double attackGain;
  protected double releaseGain;

  private float rmsRaw = 0;
  private double rmsEnv = 0;
  private double rmsPeak = 0;

  private double dbEnv = -96;
  private double dbPeak = 0;

  private double normalizedPeak = 0;
  private double peakMillis = 0;

  /**
   * Default constructor, creates a meter with unity gain and 72dB dynamic range
   *
   * @param buffer Audio buffer to meter
   */
  public DecibelMeter(LXAudioBuffer buffer) {
    this("Meter", buffer);
  }

  /**
   * Default constructor, creates a meter with unity gain and 72dB dynamic range
   *
   * @param label Label
   * @param buffer Audio buffer to meter
   */
  public DecibelMeter(String label, LXAudioBuffer buffer) {
    this(label, buffer, new Parameters());
    addParameter("gain", this.gain);
    addParameter("range", this.range);
    addParameter("attack", this.attack);
    addParameter("release", this.release);
  }

  public DecibelMeter(String label, LXAudioBuffer buffer, Parameters params) {
    this(label, buffer, params.gain, params.range, params.attack, params.release);
  }

  public DecibelMeter(String label, LXAudioBuffer buffer, CompoundParameter gain, CompoundParameter range, CompoundParameter attack, CompoundParameter release) {
    super(label);
    this.gain = gain;
    this.range = range;
    this.attack = attack;
    this.release = release;
    setBuffer(buffer);
  }

  public final DecibelMeter setBuffer(LXAudioBuffer buffer) {
    if (this.buffer != null) {
      this.buffer.removeMeter(this);
    }
    this.buffer = buffer;
    if (this.buffer != null) {
      this.buffer.addMeter(this);
    }
    return this;
  }

  public int getBufferSize() {
    return this.buffer.bufferSize();
  }

  public int getSampleRate() {
    return this.buffer.sampleRate();
  }

  public double getExponent() {
    throw new UnsupportedOperationException("DecibelMeter does not support exponent");
  }

  /**
   * Converts an amplitude (normalized 0-1, typically RMS) to decibels, negative
   * value up to 0dbFS
   *
   * @param amplitude Normalized amplitude, 0-1
   * @return Decibel value, negative infinity -> max 0dBFS
   */
  public static double amplitudeToDecibels(double amplitude) {
    return RATIO_20_LOG10 * Math.log(amplitude);
  }

  /**
   * Return raw underlying levels, no attack/gain smoothing
   *
   * @return Raw RMS value
   */
  public float getRaw() {
    return this.rmsRaw;
  }

  /**
   * @return Raw decibel value of the meter
   */
  public double getDecibels() {
    return this.dbEnv;
  }

  /**
   * @return Raw decibel value of the meter as a float
   */
  public float getDecibelsf() {
    return (float) getDecibels();
  }

  /**
   * @return A value for the audio meter from 0 to 1 with quadratic scaling
   */
  public double getSquare() {
    double norm = getValue();
    return norm * norm;
  }

  /**
   * @return Quadratic scaled value as a float
   */
  public float getSquaref() {
    return (float) getSquare();
  }

  /**
   * Compute new values when a frame of audio input is received. This is called by the
   * thread that has filled the audio buffer.
   */
  protected void onAudioFrame() {
    this.rmsRaw = this.buffer.getRms();

    this.attackGain = Math.exp(-this.buffer.bufferSize() / (this.attack.getValue() * this.buffer.sampleRate() * .001));
    this.releaseGain = Math.exp(-this.buffer.bufferSize() / (this.release.getValue() * this.buffer.sampleRate() * .001));

    final double gain = (this.rmsRaw > this.rmsEnv) ? this.attackGain : this.releaseGain;
    this.rmsEnv = (this.rmsRaw + gain * (this.rmsEnv - this.rmsRaw));

    if (this.rmsRaw > this.rmsPeak) {
      this.rmsPeak = this.rmsRaw;
      this.peakMillis = 0;
    } else {
      this.peakMillis += this.buffer.bufferSize() * 1000. / this.buffer.sampleRate();
      if (this.peakMillis > PEAK_HOLD_MS) {
        final double r = Math.exp(-this.buffer.bufferSize() / (this.release.getValue() * .001 * this.buffer.sampleRate()));
        this.rmsPeak = this.rmsRaw + r * (this.rmsPeak - this.rmsRaw);
      }
    }
  }

  @Override
  protected double computeValue(double deltaMs) {
    final double range = this.range.getValue();
    final double gain = this.gain.getValue();

    this.dbPeak = amplitudeToDecibels(this.rmsPeak) + gain;
    this.normalizedPeak = LXUtils.constrain(1 + this.dbPeak / range, 0, 1);

    this.dbEnv = amplitudeToDecibels(this.rmsEnv) + gain;
    return LXUtils.constrain(1 + this.dbEnv / range, 0, 1);
  }

  @Override
  protected void onStop() {
    super.onStop();
    this.rmsRaw = 0;
    this.rmsEnv = this.dbEnv = 0;
    this.rmsPeak = this.dbPeak = this.normalizedPeak = 0;
    setValue(0);
  }


  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException("Cannot setNormalized on DecibelMeter");
  }

  public double getPeak() {
    return this.normalizedPeak;
  }

  public float getPeakf() {
    return (float) getPeak();
  }

  @Override
  public double getNormalized() {
    return getValue();
  }

  @Override
  public float getNormalizedf() {
    return (float) getNormalized();
  }
}
