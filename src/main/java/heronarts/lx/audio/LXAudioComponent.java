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

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;

public class LXAudioComponent extends LXComponent {

  protected static final int SAMPLE_RATE = 44100;
  protected static final int SAMPLE_BUFFER_SIZE = 512;

  protected static final int BYTES_PER_SAMPLE = 2;
  protected static final int BITS_PER_SAMPLE = BYTES_PER_SAMPLE * Byte.SIZE;

  protected static final int MONO_FRAME_SIZE = BYTES_PER_SAMPLE;
  protected static final int STEREO_FRAME_SIZE = BYTES_PER_SAMPLE * 2;
  protected static final int MONO_BUFFER_SIZE = SAMPLE_BUFFER_SIZE * MONO_FRAME_SIZE;
  protected static final int STEREO_BUFFER_SIZE = SAMPLE_BUFFER_SIZE * STEREO_FRAME_SIZE;

  protected static final AudioFormat MONO = new AudioFormat(SAMPLE_RATE, 8*BYTES_PER_SAMPLE, 1, true, false);
  protected static final AudioFormat STEREO = new AudioFormat(SAMPLE_RATE, 8*BYTES_PER_SAMPLE, 2, true, false);

  protected static final DataLine.Info MONO_SOURCE_LINE = new DataLine.Info(SourceDataLine.class, MONO);
  protected static final DataLine.Info STEREO_SOURCE_LINE = new DataLine.Info(SourceDataLine.class, STEREO);

  protected static final DataLine.Info MONO_TARGET_LINE = new DataLine.Info(TargetDataLine.class, MONO);
  protected static final DataLine.Info STEREO_TARGET_LINE = new DataLine.Info(TargetDataLine.class, STEREO);

  public final LXAudioBuffer left = new LXAudioBuffer(SAMPLE_BUFFER_SIZE, SAMPLE_RATE);
  public final LXAudioBuffer right = new LXAudioBuffer(SAMPLE_BUFFER_SIZE, SAMPLE_RATE);
  public final LXAudioBuffer mix = new LXAudioBuffer(SAMPLE_BUFFER_SIZE, SAMPLE_RATE);

  LXAudioComponent(LX lx, String label) {
    super(lx, label);
  }
}
