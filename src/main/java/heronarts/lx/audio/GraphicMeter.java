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

package heronarts.lx.audio;

import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.NormalizedParameter;

/**
 * A graphic meter splits the signal into frequency bands and computes
 * envelopes for each of the bands independently. It can also give the overall
 * level, just like a normal decibel meter.
 *
 * Since energy is not typically evenly distributed through the spectrum, a
 * slope can be applied to the equalizer to even out the levels, typically
 * something like 4.5 dB/octave is used, though this varies by recording.
 */
public class GraphicMeter extends DecibelMeter {

  private final LXMeterImpl impl;

  /**
   * dB/octave slope applied to the equalizer
   */
  public final CompoundParameter slope = (CompoundParameter)
    new CompoundParameter("Slope", 4.5, -3, 12)
    .setDescription("Sets the slope of the meter in dB per octave")
    .setUnits(LXParameter.Units.DECIBELS);

  /**
   * Number of bands in the equalizer
   */
  public final int numBands;

  public final FourierTransform fft;

  private final float[] sampleBuffer;

  public final NormalizedParameter[] bands;

  public GraphicMeter(LXAudioComponent component) {
    this(component.mix);
  }

  /**
   * Default graphic equalizer with 2 bands per octave
   *
   * @param buffer Audio buffer
   */
  public GraphicMeter(LXAudioBuffer buffer) {
    this(buffer, FourierTransform.DEFAULT_NUM_BANDS);
  }

  /**
   * Default graphic equalizer with 2 bands per octave
   *
   * @param label Label
   * @param buffer Audio buffer
   */
  public GraphicMeter(String label, LXAudioBuffer buffer) {
    this(label, buffer, FourierTransform.DEFAULT_NUM_BANDS);
  }

  /**
   * Makes a graphic equalizer with a default slope of 4.5 dB/octave
   *
   * @param buffer Audio buffer to monitor
   * @param numBands Number of bands
   */
  public GraphicMeter(LXAudioBuffer buffer, int numBands) {
    this("Meter", buffer, numBands);
  }

  /**
   * Makes a graphic equalizer with a default slope of 4.5 dB/octave
   *
   * @param label Label
   * @param buffer Audio buffer to monitor
   * @param numBands Number of bands
   */
  public GraphicMeter(String label, LXAudioBuffer buffer, int numBands) {
    super(label, buffer);
    addParameter("slope", this.slope);
    this.sampleBuffer = new float[buffer.bufferSize()];
    this.fft = new FourierTransform(buffer.bufferSize(), buffer.sampleRate());
    this.fft.setNumBands(this.numBands = numBands);
    this.impl = new LXMeterImpl(this.numBands, this.fft.getBandOctaveRatio());
    this.bands = this.impl.bands;
    int i = 1;
    for (NormalizedParameter band : this.bands) {
      addParameter("band-" + (i++), band);
    }
  }

  @Override
  protected double computeValue(double deltaMs) {
    double result = super.computeValue(deltaMs);
    this.buffer.getSamples(this.sampleBuffer);
    this.fft.compute(this.sampleBuffer);

    this.impl.compute(
      this.fft,
      this.attackGain,
      this.releaseGain,
      this.gain.getValue(),
      this.range.getValue(),
      this.slope.getValue()
    );

    return result;
  }

  /**
   * Returns a snapshot of the last raw audio sample buffer frame that was used to compute
   * this meter. Note that this is copy of the audio buffer local to the LX thread and this particular
   * meter. The buffer is only updated when the meter is running, once per LX engine loop.
   *
   * @return Raw audio sample buffer used to compute this meter
   */
  public float[] getSamples() {
    return this.sampleBuffer;
  }

  /**
   * Get most recent raw unsmoothed RMS amplitude of band i
   *
   * @param i Raw band index
   * @return Raw value of band i
   */
  public float getRaw(int i) {
    return this.impl.rawBands[i];
  }

  /**
   * @param i Which frequency band to access
   * @return Level of that band in decibels
   */
  public double getDecibels(int i) {
    return -this.range.getValue() * (1 - getBand(i));
  }

  /**
   * @param i Which frequency band to access
   * @return Level of that band in decibels as a float
   */
  public float getDecibelsf(int i) {
    return (float) getDecibels(i);
  }

  /**
   * Number of bands on the meter
   *
   * @return Number of bands
   */
  public int getNumBands() {
    return this.bands.length;
  }

  /**
   * @param i Which frequency band to retrieve
   * @return The value of the ith frequency band
   */
  public double getBand(int i) {
    return this.bands[i].getValue();
  }

  /**
   * @param i Which frequency band to retrieve
   * @return The value of that band, as a float
   */
  public float getBandf(int i) {
    return (float) getBand(i);
  }

  /**
   * Gets the squared value of the i-th band
   *
   * @param i Frequency band
   * @return Squared normalized value
   */
  public double getSquare(int i) {
    double norm = getBand(i);
    return norm * norm;
  }

  /**
   * Gets the squared value of the i-th band
   *
   * @param i Frequency band
   * @return Squared normalized value as a float
   */
  public float getSquaref(int i) {
    return (float) getSquare(i);
  }

  /**
   * Averages the value of a set of bands
   *
   * @param minBand The first band to start at
   * @param avgBands How many bands to average
   * @return Average value of all these bands
   */
  public double getAverage(int minBand, int avgBands) {
    return this.impl.getAverage(minBand, avgBands);
  }

  /**
   * Averages the value of a set of bands
   *
   * @param minBand The first band to start at
   * @param avgBands How many bands to average
   * @return Average value of all these bands as a float
   */
  public float getAveragef(int minBand, int avgBands) {
    return (float) getAverage(minBand, avgBands);
  }

}
