package heronarts.lx.clip;

import java.util.Arrays;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import heronarts.lx.LX;

/**
 * Manages audio playback for composition audio lanes. Runs a dedicated thread that
 * mixes audio from all active audio lane events and writes to a SourceDataLine
 * at 44.1kHz for smooth, gap-free output independent of the LX engine frame rate.
 */
class AudioPlayer {

  private static final int SAMPLE_RATE = 44100;
  private static final int BUFFER_FRAMES = 512;
  private static final int STEREO_SAMPLES_PER_BUFFER = BUFFER_FRAMES * 2;
  private static final int BYTES_PER_BUFFER = STEREO_SAMPLES_PER_BUFFER * 2; // 16-bit

  private static final AudioFormat FORMAT = new AudioFormat(
    AudioFormat.Encoding.PCM_SIGNED,
    SAMPLE_RATE,
    16,
    2,
    4,
    SAMPLE_RATE,
    false
  );

  private static final int LINE_BUFFER_FRAMES = 2;

  private final Composition composition;
  private SourceDataLine line;
  private PlaybackThread playbackThread;

  private volatile boolean playing = false;
  private volatile boolean pendingJump = false;
  private volatile boolean closed = false;

  AudioPlayer(Composition composition) {
    this.composition = composition;
  }

  /**
   * Ensure the audio output is running. Does nothing if already playing.
   */
  void ensureRunning() {
    if (!this.playing) {
      start();
    }
  }

  /**
   * Start audio playback
   */
  void start() {
    if (this.closed) {
      return;
    }
    if (this.line == null) {
      openLine();
    }
    if (this.line == null) {
      return;
    }
    this.playing = true;
    this.line.start();
    synchronized (this) {
      notifyAll();
    }
  }

  /**
   * Stop audio playback
   */
  void stop() {
    this.playing = false;
    if (this.line != null) {
      this.line.stop();
      this.line.flush();
    }
  }

  private void openLine() {
    try {
      this.line = (SourceDataLine) AudioSystem.getLine(
        new javax.sound.sampled.DataLine.Info(SourceDataLine.class, FORMAT)
      );
      this.line.open(FORMAT, LINE_BUFFER_FRAMES * BYTES_PER_BUFFER);
      this.playbackThread = new PlaybackThread();
      this.playbackThread.start();
    } catch (Exception x) {
      LX.error(x, "Failed to open composition audio output line");
      this.line = null;
    }
  }

  /**
   * Notify that a jump/seek has occurred, requiring the audio line buffer to be flushed
   */
  void notifyJump() {
    this.pendingJump = true;
  }

  /**
   * Close the audio line and terminate the playback thread
   */
  public void dispose() {
    this.closed = true;
    this.playing = false;
    synchronized (this) {
      notifyAll();
    }
    if (this.line != null) {
      this.line.close();
      this.line = null;
    }
  }

  private class PlaybackThread extends Thread {

    private final float[] mixBuffer = new float[STEREO_SAMPLES_PER_BUFFER];
    private final byte[] outputBuffer = new byte[BYTES_PER_BUFFER];

    PlaybackThread() {
      super("LX Composition Audio Playback");
      setDaemon(true);
    }

    @Override
    public void run() {
      while (!closed) {
        // Wait while not playing
        while (!playing && !closed) {
          synchronized (AudioPlayer.this) {
            try {
              AudioPlayer.this.wait();
            } catch (InterruptedException ix) {
              // Interrupted, check conditions
            }
          }
        }
        if (closed) {
          return;
        }

        // Handle jump: flush stale audio from the line buffer
        if (pendingJump) {
          line.flush();
          pendingJump = false;
        }

        // Clear mix buffer
        Arrays.fill(this.mixBuffer, 0f);

        // Mix samples from all active audio lane events
        List<AudioLane> audioLanes = composition.getAudioLanes();
        for (AudioLane lane : audioLanes) {
          if (lane.mute.isOn()) {
            continue;
          }
          AudioLaneEvent event = lane.activeEvent;
          if (event == null) {
            continue;
          }

          float[] pcm = event.getPlaybackData();
          if (pcm == null) {
            continue;
          }

          long offset = lane.activeSampleOffset;
          if (offset >= pcm.length) {
            lane.activeEvent = null;
            continue;
          }

          int bufferStart = 0;
          long pcmOffset = offset;
          if (offset < 0) {
            // Negative offset = lead-in silence, skip ahead in buffer
            long silenceSamples = Math.min(-offset, STEREO_SAMPLES_PER_BUFFER);
            bufferStart = (int) silenceSamples;
            pcmOffset = 0;
          }

          if (bufferStart < STEREO_SAMPLES_PER_BUFFER) {
            int remaining = (int) (pcm.length - pcmOffset);
            int samplesToMix = Math.min(STEREO_SAMPLES_PER_BUFFER - bufferStart, remaining);
            for (int i = 0; i < samplesToMix; i++) {
              this.mixBuffer[bufferStart + i] += pcm[(int) pcmOffset + i];
            }
          }

          lane.activeSampleOffset = offset + STEREO_SAMPLES_PER_BUFFER;
        }

        // Convert float mix buffer to 16-bit PCM bytes (little-endian)
        // When no events are active, mixBuffer is all zeros → outputs silence
        for (int i = 0; i < STEREO_SAMPLES_PER_BUFFER; i++) {
          float sample = this.mixBuffer[i];
          if (sample > 1f) {
            sample = 1f;
          } else if (sample < -1f) {
            sample = -1f;
          }
          short s = (short) (sample * 32767);
          this.outputBuffer[i * 2] = (byte) (s & 0xFF);
          this.outputBuffer[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }

        // Write to SourceDataLine — blocks when buffer is full, providing timing
        try {
          line.write(this.outputBuffer, 0, BYTES_PER_BUFFER);
        } catch (Exception x) {
          LX.error(x, "Composition audio playback write error");
          playing = false;
        }
      }
    }
  }
}
