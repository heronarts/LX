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

import java.util.Arrays;

import heronarts.lx.parameter.NormalizedParameter;

class LXMeterImpl {

  final int numBands;

  final float[] rawBands;
  final float[] rmsBands;
  final double[] dbBands;
  final NormalizedParameter[] bands;

  LXMeterImpl(int numBands) {
    this.numBands = numBands;
    this.rawBands = new float[this.numBands];
    this.rmsBands = new float[this.numBands];
    this.dbBands = new double[this.numBands];
    this.bands = new NormalizedParameter[this.numBands];
    for (int i = 0; i < this.numBands; ++i) {
      this.bands[i] = new NormalizedParameter("Band-" + (i+1), 0);
    }
    clear();
  }

  void compute(FourierTransform fft, double attackGain, double releaseGain, double gain, double range, double slope) {
    final double bandOctaveRatio = fft.getBandOctaveRatio();
    for (int i = 0; i < this.numBands; ++i) {
      float rmsLevel = fft.getBand(i) / fft.getSize();
      double rmsGain = (rmsLevel >= this.rmsBands[i]) ? attackGain : releaseGain;
      this.rmsBands[i] = rmsLevel + (float) rmsGain * (this.rmsBands[i] - rmsLevel);
      this.dbBands[i] = DecibelMeter.amplitudeToDecibels(this.rmsBands[i]) + gain + i * slope * bandOctaveRatio;
      this.bands[i].setValue(1 + this.dbBands[i] / range);
    }
  }

  void onStop() {
    clear();
  }

  private void clear() {
    Arrays.fill(this.rawBands, 0);
    Arrays.fill(this.rmsBands, 0);
    Arrays.fill(this.dbBands, -96);
    for (NormalizedParameter band : this.bands) {
      band.setValue(0);
    }
  }

  double getBand(int i) {
    return this.bands[i].getValue();
  }

  /**
   * Averages the value of a set of bands
   *
   * @param minBand The first band to start at
   * @param avgBands How many bands to average
   * @return Average value of all these bands
   */
  double getAverage(int minBand, int avgBands) {
    double avg = 0;
    int i = 0;
    for (; i < avgBands; ++i) {
      if (minBand + i >= this.numBands) {
        break;
      }
      avg += getBand(minBand + i);
    }
    return avg / i;
  }

}
