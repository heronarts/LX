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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import heronarts.lx.LX;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.parameter.TriggerParameter;

public class LXAudioOutput extends LXAudioComponent implements LXOscComponent {

  private static final int BUFFER_FRAMES = 2;
  private static final AudioFormat AUDIO_OUTPUT_FORMAT = AUDIO_FORMAT_STEREO_44K;

  private AudioInputStream inputStream;

  public final TriggerParameter trigger =
    new TriggerParameter("Trigger")
    .setDescription("Triggers playback of the audio file from its beginning");

  public final BooleanParameter play = new BooleanParameter("Play", false)
    .setDescription("Play/Pause state of the output audio file");

  public final BooleanParameter looping = new BooleanParameter("Loop", false)
    .setDescription("Whether playback of the audio file should loop");

  public final StringParameter file = new StringParameter("File")
    .setDescription("File for audio playback");

  private final LXParameterListener toggle = p -> _toggle();

  public LXAudioOutput(LX lx, LXAudioEngine audio) {
    super(lx, "Output");
    addParameter("file", this.file);
    addParameter("trigger", this.trigger);
    addParameter("looping", this.looping);
    addParameter("play", this.play);

    audio.enabled.addListener(this.toggle);
    audio.mode.addListener(this.toggle);
  }

  private OutputThread outputThread = null;

  private class OutputThread extends Thread implements LineListener {

    private final SourceDataLine line;
    private final int sampleRate;

    private boolean stopped = true;
    private boolean closed = false;

    private final byte[] buffer = new byte[STEREO_BUFFER_SIZE_16];

    private volatile boolean trigger = false;
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

        try {
          if (this.flush) {
            this.line.flush();
            this.flush = false;
          }

          if (this.trigger) {
            if (inputStream.markSupported()) {
              inputStream.reset();
            } else {
              LX.error("Audio format does not support reset");
            }
            this.trigger = false;
          }

          final boolean isMono = isMono(inputStream.getFormat());

          // Read from the input stream
          int len = inputStream.read(this.buffer, 0, isMono ? MONO_BUFFER_SIZE_16 : STEREO_BUFFER_SIZE_16);

          // Reached the end of the file...
          if (len <= 0) {
            this.line.drain();
            this.trigger = true;
            if (!looping.isOn()) {
              play.setValue(false);
            }
            continue;
          }

          // When reading mono files, double the length for stereo output
          if (isMono) {
            for (int i = len - MONO_FRAME_SIZE_16; i >= 0; i -= MONO_FRAME_SIZE_16) {
              this.buffer[2*i] = this.buffer[2*i+2] = this.buffer[i];
              this.buffer[2*i+1] = this.buffer[2*i+3] = this.buffer[i+1];
            }
            len *= 2;
          }

          // Write to the output line
          try {
            this.line.write(this.buffer, 0, len);
          } catch (Exception x) {
            LX.error(x, "LXAudioOutput error writing to line: " + x.getLocalizedMessage());
            play.setValue(false);
          }

          // TODO(mcslee): Need some kind of timing-fu in here so that the metering
          // is in sync. Right now this sort of rushes ahead as the output buffer for
          // the line is multiple frames

          // Put the left and right buffers
          left.putSamples(this.buffer, 0, STEREO_BUFFER_SIZE_16, STEREO_FRAME_SIZE_16, this.sampleRate);
          right.putSamples(this.buffer, BYTES_PER_SAMPLE_16, STEREO_BUFFER_SIZE_16, STEREO_FRAME_SIZE_16, this.sampleRate);
          mix.computeMix(left, right);

        } catch (IOException iox) {
          LX.error(iox);
          break;
        }
      }

      this.line.flush();
      this.line.removeLineListener(this);
    }

    @Override
    public void update(LineEvent event) {
      final LineEvent.Type eventType = event.getType();
      LX.debug("LXAudioOutput.line.update(LineEvent.Type." + eventType + ")");
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

  public void setInputStream(File file) {
    try {
      setInputStream(new FileInputStream(file));
    } catch (FileNotFoundException fnfx) {
      LX.error(fnfx, "Audio file does not exist for setInputStream: " + file);
    }
  }

  public boolean setInputStream(InputStream inputStream) {
    if (!inputStream.markSupported()) {
      inputStream = new BufferedInputStream(inputStream);
    }
    try {
      return setAudioInputStream(AudioSystem.getAudioInputStream(inputStream));
    } catch (UnsupportedAudioFileException uafx) {
      LX.error(uafx);
    } catch (IOException iox) {
      LX.error(iox);
    }
    return false;
  }

  public boolean setAudioInputStream(AudioInputStream inputStream) {
    AudioFormat inputFormat = inputStream.getFormat();

    // Decode MP3 formats or whatever-or-other we got
    if (inputFormat.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
      try {
        inputStream = AudioSystem.getAudioInputStream(AUDIO_OUTPUT_FORMAT, inputStream);
        if (!inputStream.markSupported()) {
          // Buffer it! We need reset/mark support
          inputStream = new AudioInputStream(new BufferedInputStream(inputStream), AUDIO_OUTPUT_FORMAT, inputStream.getFrameLength());
        }
        inputFormat = inputStream.getFormat();
      } catch (Exception x) {
        LX.error(x, "Invalid audio format: " + x.getLocalizedMessage());
        return false;
      }
    }

    if (inputFormat.getSampleRate() != SAMPLE_RATE_44K) {
      LX.error("Audio must have a sample rate of 44.1kHz");
      return false;
    }
    if (inputFormat.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
      LX.error("Audio must be decodable to PCM_SIGNED data");
      return false;
    }
    if (inputFormat.getSampleSizeInBits() != BITS_PER_SAMPLE_16) {
      LX.error("Audio file must have " + BITS_PER_SAMPLE_16 + " bits per sample");
      return false;
    }
    if (inputFormat.isBigEndian()) {
      LX.error("Audio file must be little endian");
      return false;
    }
    if (inputFormat.getChannels() > 2) {
      LX.error("Audio file has more than 2 channels");
    }

    // Okay we're valid!
    this.inputStream = inputStream;
    if (this.inputStream.markSupported()) {
      this.inputStream.mark(Integer.MAX_VALUE);
    }
    if (this.outputThread != null) {
      this.outputThread.flush = true;
    }
    open();
    return true;
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.play) {
      _toggle();
    } else if (p == this.trigger) {
      if (this.trigger.isOn()) {
        if (this.outputThread != null) {
          this.play.setValue(true);
          this.outputThread.trigger = true;
        }
      }
    } else if (p == this.file) {
      String path = this.file.getString();
      if (path != null && path.length() > 0) {
        setInputStream(new File(path));
      } else {
        stop();
      }
    }
  }

  public String getFileName() {
    return new File(this.file.getString()).getName();
  }

  private void open() {
    if (this.outputThread == null) {
      try {
        final SourceDataLine line = (SourceDataLine) AudioSystem.getLine(getSourceLineInfo(AUDIO_OUTPUT_FORMAT));
        line.open(AUDIO_OUTPUT_FORMAT, BUFFER_FRAMES * bufferSize(AUDIO_OUTPUT_FORMAT));
        this.outputThread = new OutputThread(line, AUDIO_OUTPUT_FORMAT);
        _toggle();
      } catch (Exception x) {
        LX.error(x, "Exception opening stereo output audio line");
      }
    }
  }

  private void _toggle() {
    if (this.lx.engine.audio.enabled.isOn() &&
        (this.lx.engine.audio.mode.getEnum() == LXAudioEngine.Mode.OUTPUT) &&
        this.play.isOn()) {
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

  void close() {
    if (this.outputThread != null) {
      this.outputThread.line.close();
      this.outputThread.setState(true, true);
    }
  }

  void stop() {
    if (this.outputThread != null) {
      this.outputThread.line.stop();
      this.outputThread.setState(true, false);
    }
  }

  void reset() {
    this.play.setValue(false);
    this.looping.setValue(false);
    this.file.setValue("");
  }

  @Override
  public void dispose() {
    this.lx.engine.audio.enabled.removeListener(this.toggle);
    this.lx.engine.audio.mode.removeListener(this.toggle);
    close();
    super.dispose();
  }
}
