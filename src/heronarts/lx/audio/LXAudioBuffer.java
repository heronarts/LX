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

public class LXAudioBuffer {

  protected static final float INV_16_BIT = 1 / 32768.0f;

  private final int sampleRate;

  final float[] samples;
  float rms;

  LXAudioBuffer(int bufferSize, int sampleRate) {
    this.samples = new float[bufferSize];
    this.sampleRate = sampleRate;
  }

  public int bufferSize() {
    return this.samples.length;
  }

  public int sampleRate() {
    return this.sampleRate;
  }

  public float getRms() {
    return this.rms;
  }

  protected synchronized void computeMix(LXAudioBuffer left, LXAudioBuffer right) {
    float sumSquares = 0;
    for (int i = 0; i < samples.length; ++i) {
      this.samples[i] = (left.samples[i] + right.samples[i]) * .5f;
      sumSquares += this.samples[i] * this.samples[i];
    }
    this.rms = (float) Math.sqrt(sumSquares / this.samples.length);
  }

  protected synchronized void putSamples(byte[] rawBytes, int offset, int dataSize, int frameSize) {
    int frameIndex = 0;
    float sumSquares = 0;
    for (int i = 0; i < dataSize; i += frameSize) {
      this.samples[frameIndex] = ((rawBytes[offset + i+1] << 8) | (rawBytes[offset + i] & 0xff)) * INV_16_BIT;
      sumSquares += this.samples[frameIndex] * this.samples[frameIndex];
      ++frameIndex;
    }
    this.rms = (float) Math.sqrt(sumSquares / this.samples.length);
  }

  public synchronized void getSamples(float[] dest) {
    if (this.samples.length != dest.length) {
      throw new IllegalArgumentException("LXAudioBuffer getSamples destination array must have same length");
    }
    System.arraycopy(this.samples, 0, dest, 0, dest.length);
  }

}
