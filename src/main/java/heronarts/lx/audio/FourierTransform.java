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

public class FourierTransform {

  public static final float LOG_2 = (float) Math.log(2);
  public final static float BASE_BAND_HZ = 65.41f; // C2
  public static final int DEFAULT_NUM_BANDS = 16;

  enum Window {
    RECTANGULAR,
    HAMMING;

    public float getCoefficient(int i, int n) {
      switch (this) {
      case HAMMING: return 0.54f - 0.46f * (float) Math.cos(2 * Math.PI * i / (n-1));
      default:
      case RECTANGULAR: return 1.f;
      }
    }
  };

  private Window window = Window.HAMMING;

  private final int bufferSize;
  private final int sampleRate;
  private final float bandWidthInv;

  private final int logN;
  private final float[] sinN;
  private final float[] cosN;
  private final float[] windowCoefficients;

  private final int[] bitReverseIndex;
  private final float[] real;
  private final float[] imaginary;
  private final float[] amplitude;

  private int numBands = 0;
  private float[] bands;
  private int[] bandOffset;
  private float bandOctaveRatio;

  public FourierTransform(int bufferSize, int sampleRate) {
    if ((bufferSize & (bufferSize - 1)) != 0) {
      throw new IllegalArgumentException("bufferSize must be a power of two: " + bufferSize);
    }

    this.bufferSize = bufferSize;
    this.sampleRate = sampleRate;
    this.bandWidthInv = this.bufferSize / (float) this.sampleRate;

    this.logN = (int) (Math.log(bufferSize) / Math.log(2));

    this.sinN = new float[this.logN];
    this.cosN = new float[this.logN];
    computePhaseTables();

    this.windowCoefficients = new float[this.bufferSize];
    computeWindowCoefficients();

    this.bitReverseIndex = new int[this.bufferSize];
    computeBitReverseIndices();

    this.real = new float[this.bufferSize];
    this.imaginary = new float[this.bufferSize];
    this.amplitude = new float[this.bufferSize/2 + 1];

    setNumBands(DEFAULT_NUM_BANDS);
  }

  private void computePhaseTables() {
    // given i, N = 2^i
    // sin[i] = sin(-PI / N);
    // cos[i] = cos(-PI / N);
    for (int i = 0, N = 1; i < this.logN; ++i, N <<= 1) {
      this.sinN[i] = (float) Math.sin(-Math.PI / N);
      this.cosN[i] = (float) Math.cos(-Math.PI / N);
    }
  }

  private void computeBitReverseIndices() {
    this.bitReverseIndex[0] = 0;
    for (int limit = 1, bit = this.bufferSize/2; limit < this.bufferSize; limit <<= 1, bit >>= 1) {
      for (int i = 0; i < limit; ++i) {
        this.bitReverseIndex[i + limit] = this.bitReverseIndex[i] + bit;
      }
    }
  }

  public int getSize() {
    return this.bufferSize;
  }

  public int getSampleRate() {
    return this.sampleRate;
  }

  public FourierTransform setWindow(Window window) {
    if (this.window != window) {
      this.window = window;
      computeWindowCoefficients();
    }
    return this;
  }

  private void computeWindowCoefficients() {
    for (int i = 0; i < this.bufferSize; ++i) {
      this.windowCoefficients[i] = this.window.getCoefficient(i, this.bufferSize);
    }
  }

  public FourierTransform compute(float[] samples) {
    if (samples.length != this.bufferSize) {
      throw new IllegalArgumentException("Samples must have same length as FourierTransform size: " + samples.length);
    }
    // Apply window function, initialize bit-reverse-indexed values
    for (int i = 0; i < this.bufferSize; ++i) {
      int bri = this.bitReverseIndex[i];
      this.real[i] = samples[bri] * this.windowCoefficients[bri];
      this.imaginary[i] = 0f;
    }
    // Iterate through l = [0, logN-1], N = 2^l
    for (int l = 0, n = 1; l < this.logN; ++l, n <<= 1) {
      float cosN = this.cosN[l];
      float sinN = this.sinN[l];
      float phaseR = 1f;
      float phaseI = 0f;
      for (int f = 0; f < n; ++f) {
        for (int i = f; i < this.bufferSize; i += 2*n) {
          int n2 = i + n;
          float tR = phaseR * this.real[n2] - phaseI * this.imaginary[n2];
          float tI = phaseR * this.imaginary[n2] + phaseI * this.real[n2];
          this.real[n2] = this.real[i] - tR;
          this.imaginary[n2] = this.imaginary[i] - tI;
          this.real[i] += tR;
          this.imaginary[i] += tI;
        }
        float tmpR = phaseR;
        phaseR = (phaseR * cosN) - (phaseI * sinN);
        phaseI = (tmpR * sinN) + (phaseI * cosN);
      }
    }
    // Amplitude
    for (int i = 0; i < this.amplitude.length; ++i) {
      this.amplitude[i] = (float) Math.sqrt(this.real[i]*this.real[i] + this.imaginary[i]*this.imaginary[i]);
    }

    // Compute octave averages
    if (this.numBands > 0) {
      for (int band = 0; band < this.numBands; ++band) {
        float avg = 0;
        for (int i = this.bandOffset[band]; i <= this.bandOffset[band+1]; ++i) {
          avg += this.amplitude[i];
        }
        this.bands[band] = avg / (this.bandOffset[band+1] - this.bandOffset[band] + 1);
      }
    }

    return this;
  }

  public float get(int i) {
    return this.amplitude[i];
  }

  public FourierTransform setNumBands(int numBands) {
    if (this.numBands != numBands) {
      this.numBands = numBands;
      this.bands = new float[this.numBands];
      this.bandOffset = new int[this.numBands + 1];
      this.bandOffset[0] = 0;

      float nyquist = this.sampleRate / 2;
      float nyquistRatio = nyquist / BASE_BAND_HZ;
      float bandExpRange = (float) Math.log(nyquistRatio) / LOG_2;
      this.bandOctaveRatio = bandExpRange / (this.numBands - 1);

      for (int i = 0; i < this.numBands; ++i) {
        float bandLimitHz = (float) Math.pow(2, i * this.bandOctaveRatio) * BASE_BAND_HZ;
        this.bandOffset[i+1] = Math.round(this.bandWidthInv * bandLimitHz);
      }
    }
    return this;
  }

  public int getNumBands() {
    return this.numBands;
  }

  public float getBandOctaveRatio() {
    return this.bandOctaveRatio;
  }

  public float getBand(int i) {
    return this.bands[i];
  }

  public float getAverage(float minHz, float maxHz) {
    int low = Math.round(minHz * this.bandWidthInv);
    int high = Math.round(maxHz * this.bandWidthInv);
    float avg = 0;
    for (int i = low; i <= high; ++i) {
      avg += this.amplitude[i];
    }
    return avg / (high - low + 1);
  }

}
