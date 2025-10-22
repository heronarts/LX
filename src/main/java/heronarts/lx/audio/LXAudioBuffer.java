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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import heronarts.lx.LX;

public class LXAudioBuffer {

  protected static final float INV_16_BIT = 1 / 32768.0f;

  final float[] samples;
  private float rms;
  private int sampleRate = -1;

  /**
   * Loosely thread-safe list of meters that need computations (e.g. db/FFT metering) on
   * the audio thread
   */
  private final List<DecibelMeter> meters = new CopyOnWriteArrayList<>();

  LXAudioBuffer(int bufferSize) {
    this.samples = new float[bufferSize];
  }

  public int sampleRate() {
    return this.sampleRate;
  }

  public int bufferSize() {
    return this.samples.length;
  }

  public float getRms() {
    return this.rms;
  }

  void addMeter(DecibelMeter meter) {
    this.meters.add(meter);
  }

  void removeMeter(DecibelMeter meter) {
    this.meters.remove(meter);
  }

  void computeMix(LXAudioBuffer left, LXAudioBuffer right) {
    if (left.sampleRate != right.sampleRate) {
      LX.error("LXAudioBuffer.computeMix given two different samplerates: " + left.sampleRate + " != " + right.sampleRate);
    }
    this.sampleRate = left.sampleRate;
    float sumSquares = 0;
    for (int i = 0; i < samples.length; ++i) {
      this.samples[i] = (left.samples[i] + right.samples[i]) * .5f;
      sumSquares += this.samples[i] * this.samples[i];
    }
    this.rms = (float) Math.sqrt(sumSquares / this.samples.length);
    updateMeters();
  }

  void putSamples(byte[] rawBytes, int offset, int dataSize, int frameSize, int sampleRate) {
    this.sampleRate = sampleRate;
    int frameIndex = 0;
    float sumSquares = 0;
    for (int i = 0; i < dataSize; i += frameSize) {
      this.samples[frameIndex] = ((rawBytes[offset + i+1] << 8) | (rawBytes[offset + i] & 0xff)) * INV_16_BIT;
      sumSquares += this.samples[frameIndex] * this.samples[frameIndex];
      ++frameIndex;
    }
    this.rms = (float) Math.sqrt(sumSquares / this.samples.length);
    updateMeters();
  }

  private void updateMeters() {
    // Compute meters based upon new samples
    for (DecibelMeter meter : this.meters) {
      meter.onAudioFrame();
    }
  }

}
