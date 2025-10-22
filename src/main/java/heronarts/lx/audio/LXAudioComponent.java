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

  protected static final int SAMPLE_BUFFER_SIZE = 512;

  protected static final int SAMPLE_RATE_44K = 44100;
  protected static final int SAMPLE_RATE_48K = 48000;

  protected static final int BITS_PER_SAMPLE_16 = 16;
  protected static final int BYTES_PER_SAMPLE_16 = BITS_PER_SAMPLE_16 / Byte.SIZE;

  protected static final int MONO_FRAME_SIZE_16 = BYTES_PER_SAMPLE_16;
  protected static final int STEREO_FRAME_SIZE_16 = BYTES_PER_SAMPLE_16 * 2;
  protected static final int MONO_BUFFER_SIZE_16 = SAMPLE_BUFFER_SIZE * MONO_FRAME_SIZE_16;
  protected static final int STEREO_BUFFER_SIZE_16 = SAMPLE_BUFFER_SIZE * STEREO_FRAME_SIZE_16;

  protected static final AudioFormat AUDIO_FORMAT_MONO_44K = new AudioFormat(SAMPLE_RATE_44K, BITS_PER_SAMPLE_16, 1, true, false);
  protected static final AudioFormat AUDIO_FORMAT_MONO_48K = new AudioFormat(SAMPLE_RATE_48K, BITS_PER_SAMPLE_16, 1, true, false);
  protected static final AudioFormat AUDIO_FORMAT_STEREO_44K = new AudioFormat(SAMPLE_RATE_44K, BITS_PER_SAMPLE_16, 2, true, false);
  protected static final AudioFormat AUDIO_FORMAT_STEREO_48K = new AudioFormat(SAMPLE_RATE_48K, BITS_PER_SAMPLE_16, 2, true, false);

  protected static final DataLine.Info MONO_SOURCE_LINE_44K = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT_MONO_44K);
  protected static final DataLine.Info MONO_SOURCE_LINE_48K = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT_MONO_48K);
  protected static final DataLine.Info STEREO_SOURCE_LINE_44K = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT_STEREO_44K);
  protected static final DataLine.Info STEREO_SOURCE_LINE_48K = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT_STEREO_48K);

  protected static final DataLine.Info MONO_TARGET_LINE_44K = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT_MONO_44K);
  protected static final DataLine.Info MONO_TARGET_LINE_48K = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT_MONO_48K);
  protected static final DataLine.Info STEREO_TARGET_LINE_44K = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT_STEREO_44K);
  protected static final DataLine.Info STEREO_TARGET_LINE_48K = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT_STEREO_48K);

  public final LXAudioBuffer left = new LXAudioBuffer(SAMPLE_BUFFER_SIZE);
  public final LXAudioBuffer right = new LXAudioBuffer(SAMPLE_BUFFER_SIZE);
  public final LXAudioBuffer mix = new LXAudioBuffer(SAMPLE_BUFFER_SIZE);

  LXAudioComponent(LX lx, String label) {
    super(lx, label);
  }

  protected static int bufferSize(AudioFormat format) {
    return isMono(format) ? MONO_BUFFER_SIZE_16 : STEREO_BUFFER_SIZE_16;
  }

  protected static boolean isMono(AudioFormat format) {
    return format.getChannels() == 1;
  }

  protected static DataLine.Info getSourceLineInfo(AudioFormat format) {
    if (format == AUDIO_FORMAT_MONO_44K) {
      return MONO_SOURCE_LINE_44K;
    } else if (format == AUDIO_FORMAT_MONO_48K) {
      return MONO_SOURCE_LINE_48K;
    } else if (format == AUDIO_FORMAT_STEREO_44K) {
      return STEREO_SOURCE_LINE_44K;
    } else if (format == AUDIO_FORMAT_STEREO_48K) {
      return STEREO_SOURCE_LINE_48K;
    }
    throw new IllegalArgumentException("Unsupported source line audio format: " + format);
  }

  protected static DataLine.Info getTargetLineInfo(AudioFormat format) {
    if (format == AUDIO_FORMAT_MONO_44K) {
      return MONO_TARGET_LINE_44K;
    } else if (format == AUDIO_FORMAT_MONO_48K) {
      return MONO_TARGET_LINE_48K;
    } else if (format == AUDIO_FORMAT_STEREO_44K) {
      return STEREO_TARGET_LINE_44K;
    } else if (format == AUDIO_FORMAT_STEREO_48K) {
      return STEREO_TARGET_LINE_48K;
    }
    throw new IllegalArgumentException("Unsupported target line audio format: " + format);
  }

}
