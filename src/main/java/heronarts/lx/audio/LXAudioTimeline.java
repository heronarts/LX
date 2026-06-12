/**
 * Copyright 2026- Mark C. Slee, Heron Arts LLC
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
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.SourceDataLine;

import heronarts.lx.LX;
import heronarts.lx.clip.AudioClipEvent;
import heronarts.lx.clip.AudioClipLane;
import heronarts.lx.clip.LXComposition;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.utils.LXUtils;

public class LXAudioTimeline extends LXAudioComponent {

  public static final AudioFormat AUDIO_OUTPUT_FORMAT = LXAudioOutput.AUDIO_OUTPUT_FORMAT;

  public final BooleanParameter play = new BooleanParameter("Play", false)
    .setDescription("Play/Pause state of the timeline audio");

  private LXComposition composition = null;

  public LXAudioTimeline(LX lx, LXAudioEngine audio) {
    super(lx, "Timeline");
    addParameter("play", this.play);
    audio.enabled.addListener(this.toggle);
    audio.mode.addListener(this.toggle);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.play) {
      _toggle();
    }
  }

  public void onCompositionChanged(LXComposition composition) {
    this.composition = composition;
    if (this.composition != null) {
      if (this.play.isOn()) {
        this.play.bang();
      }
    } else {
      this.play.setValue(false);
    }
  }

  private void open() {
    if (this.outputThread == null) {
      try {
        final SourceDataLine line = (SourceDataLine) AudioSystem.getLine(getSourceLineInfo(AUDIO_OUTPUT_FORMAT));
        line.open(AUDIO_OUTPUT_FORMAT, LXAudioOutput.BUFFER_FRAMES * bufferSize(AUDIO_OUTPUT_FORMAT));
        this.outputThread = new OutputThread(line, AUDIO_OUTPUT_FORMAT);
        _toggle();
      } catch (Exception x) {
        LX.error(x, "Exception opening stereo output audio line");
      }
    }
  }

  private final LXParameterListener toggle = p -> _toggle();

  private void _toggle() {
    if (this.lx.engine.audio.enabled.isOn() &&
      (this.lx.engine.audio.mode.getEnum() == LXAudioEngine.Mode.TIMELINE) &&
      this.play.isOn() &&
      this.composition != null) {
      open();
      if (this.outputThread == null) {
        this.play.setValue(false);
      } else {
        start();
      }
    } else {
      stop();
    }
  }

  void start() {
    if (this.outputThread != null) {
      this.outputThread.line.start();
      this.outputThread.setState(false, false);
    }
  }

  void stop() {
    if (this.outputThread != null) {
      this.outputThread.line.stop();
      this.outputThread.setState(true, false);
    }
  }

  void close() {
    if (this.outputThread != null) {
      this.outputThread.line.close();
      this.outputThread.setState(true, true);
    }
  }

  private OutputThread outputThread = null;

  private class OutputThread extends Thread implements LineListener {

    private final SourceDataLine line;
    private final int sampleRate;

    private boolean stopped = true;
    private boolean closed = false;

    private final byte[] buffer = new byte[STEREO_BUFFER_SIZE_16];
    private final float[] mixBuffer = new float[STEREO_BUFFER_SIZE_16 / 2];

    private volatile boolean flush = false;

    private OutputThread(SourceDataLine line, AudioFormat format) {
      super("LXAudioEngine Output Thread");
      this.line = line;
      this.line.addLineListener(this);
      this.sampleRate = (int) format.getSampleRate();
      start();
    }

    private void setState(boolean stopped, boolean closed) {
      this.stopped = stopped;
      this.closed = closed;
      synchronized (this) {
        notify();
      }
    }

    @Override
    public void run() {
      while (!this.closed) {
        while (this.stopped) {
          if (this.closed) {
            return;
          }
          try {
            synchronized (this) {
              wait();
            }
          } catch (InterruptedException ix) {}
        }

        if (this.flush) {
          this.line.flush();
          this.flush = false;
        }

        Arrays.fill(this.mixBuffer, 0f);

        // Grab volatile composition reference
        final LXComposition composition = LXAudioTimeline.this.composition;

        // Mix samples from all active audio lane events
        if (composition != null) {
          for (AudioClipLane lane : composition.getAudioLanes()) {
            final AudioClipEvent event = lane.getActiveEvent();
            if (event == null) {
              continue;
            }

            // No audio data available
            final float[] pcm = event.getPlaybackData();
            if (pcm == null || pcm.length == 0) {
              continue;
            }

            // We have run off the end of this event
            final int offset = lane.getActiveSampleOffset();
            if (offset >= pcm.length) {
              lane.clearActiveEvent();
              continue;
            }

            // Mix audio if enabled and non-zero gain
            if (lane.enabled.isOn() && (lane.gain.getNormalized() > 0)) {
              final float gainf = (float) Math.pow(10., lane.gain.getValue() / 20.);
              int bufferStart = 0;
              int pcmOffset = offset;
              if (offset < 0) {
                // Negative offset = lead-in silence, skip ahead in buffer
                long silenceSamples = Math.min(-offset, this.mixBuffer.length);
                bufferStart = (int) silenceSamples;
                pcmOffset = 0;
              }

              if (bufferStart < this.mixBuffer.length) {
                int remaining = pcm.length - pcmOffset;
                int samplesToMix = Math.min(this.mixBuffer.length - bufferStart, remaining);
                for (int i = 0; i < samplesToMix; i++) {
                  this.mixBuffer[bufferStart + i] += gainf * pcm[pcmOffset + i];
                }
              }
            }

            // Be sure to advance the sample offset even if disabled or gain was 0
            lane.setActiveSampleOffset(offset + this.mixBuffer.length);
          }
        }

        // Convert float mix buffer to 16-bit PCM bytes (little-endian)
        // When no events are active, mixBuffer is all zeros → outputs silence
        for (int i = 0; i < this.mixBuffer.length; i++) {
          float sample = LXUtils.constrainf(this.mixBuffer[i], -1f, 1f);
          short s = (short) (sample * 32767);
          this.buffer[i * 2] = (byte) (s & 0xFF);
          this.buffer[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }

        // Write to the audio line
        try {
          line.write(this.buffer, 0, this.buffer.length);
        } catch (Exception x) {
          LX.error(x, "LXAudioTimeline audio playback line.write error");
          play.setValue(false);
        }

        // TODO(mcslee): Need some kind of timing-fu in here so that the metering
        // is in sync. Right now this sort of rushes ahead if the output buffer for
        // the line is multiple frames

        // Put the left and right buffers
        left.putSamples(this.buffer, 0, STEREO_BUFFER_SIZE_16, STEREO_FRAME_SIZE_16, this.sampleRate);
        right.putSamples(this.buffer, BYTES_PER_SAMPLE_16, STEREO_BUFFER_SIZE_16, STEREO_FRAME_SIZE_16, this.sampleRate);
        mix.computeMix(left, right);
      }

      this.line.flush();
      this.line.removeLineListener(this);
    }

    @Override
    public void update(LineEvent event) {
      final LineEvent.Type eventType = event.getType();
      LX.debug("LXAudioTimeline.line.update(LineEvent.Type." + eventType + ")");
      if (eventType == LineEvent.Type.START){
        this.stopped = false;
        synchronized (this) {
          notify();
        }
      } else if (eventType == LineEvent.Type.STOP) {
        this.stopped = true;
        synchronized (this) {
          notify();
        }
      } else if (eventType == LineEvent.Type.CLOSE) {
        this.closed = true;
        synchronized (this) {
          notify();
        }
      }
    }
  }

  void reset() {
    this.play.setValue(false);
  }

  @Override
  public void dispose() {
    this.lx.engine.audio.enabled.removeListener(this.toggle);
    this.lx.engine.audio.mode.removeListener(this.toggle);
    close();
    super.dispose();
  }
}
