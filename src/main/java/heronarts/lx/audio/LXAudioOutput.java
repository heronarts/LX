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
import heronarts.lx.parameter.StringParameter;

public class LXAudioOutput extends LXAudioComponent implements LXOscComponent, LineListener {

  private SourceDataLine line;
  private AudioFormat format;
  private AudioInputStream inputStream;

  private boolean stopped = false;
  private boolean closed = false;

  public final BooleanParameter trigger = new BooleanParameter("Trigger", false)
    .setDescription("Triggers playback of the audio file from its beginning");

  public final BooleanParameter play = new BooleanParameter("Play", false)
    .setDescription("Play/Pause state of the output audio file");

  public final BooleanParameter looping = new BooleanParameter("Loop", false)
    .setDescription("Whether playback of the audio file should loop");

  public final StringParameter file = new StringParameter("File")
    .setDescription("File for audio playback");

  public LXAudioOutput(LX lx) {
    super(lx, "Output");
    this.format = STEREO;
    addParameter("file", this.file);
    addParameter("trigger", this.trigger);
    addParameter("looping", this.looping);
    addParameter("play", this.play);
  }

  private OutputThread outputThread = null;

  private class OutputThread extends Thread {

    private final SourceDataLine line;

    private final byte[] buffer = new byte[STEREO_BUFFER_SIZE];

    private volatile boolean trigger = false;
    private volatile boolean flush = false;

    private OutputThread(SourceDataLine line) {
      super("LXAudioEngine Output Thread");
      this.line = line;
    }

    @Override
    public void run() {
      while (!closed) {
        while (stopped) {
          if (closed) {
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
            line.flush();
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

          boolean mono = inputStream.getFormat().getChannels() == 1;

          // Read from the input stream
          int len = inputStream.read(this.buffer, 0, mono ? MONO_BUFFER_SIZE : STEREO_BUFFER_SIZE);

          // Reached the end of the file...
          if (len <= 0) {
            line.drain();
            this.trigger = true;
            if (!looping.isOn()) {
              play.setValue(false);
            }
            continue;
          }

          // When reading mono files, double the length for stereo output
          if (mono) {
            for (int i = len - MONO_FRAME_SIZE; i >= 0; i -= MONO_FRAME_SIZE) {
              this.buffer[2*i] = this.buffer[2*i+2] = this.buffer[i];
              this.buffer[2*i+1] = this.buffer[2*i+3] = this.buffer[i+1];
            }
            len *= 2;
          }

          // Write to the output line
          try {
            line.write(this.buffer, 0, len);
          } catch (Exception x) {
            LX.error(x, "LXAudioOutput error writing to line: " + x.getLocalizedMessage());
            play.setValue(false);
          }

          // TODO(mcslee): Need some kind of timing-fu in here so that the metering
          // is in sync. Right now this sort of rushes ahead as the ouptut buffer is
          // big.

          // Put the left and right buffers
          left.putSamples(this.buffer, 0, STEREO_BUFFER_SIZE, STEREO_FRAME_SIZE);
          right.putSamples(this.buffer, 2, STEREO_BUFFER_SIZE, STEREO_FRAME_SIZE);
          mix.computeMix(left, right);

        } catch (IOException iox) {
          LX.error(iox);
          break;
        }
      }

      line.flush();
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
    AudioFormat format = inputStream.getFormat();

    // Decode MP3 formats or whatever-or-other we got
    if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
      try {
        inputStream = AudioSystem.getAudioInputStream(STEREO, inputStream);
        if (!inputStream.markSupported()) {
          // Buffer it! We need reset/mark support
          inputStream = new AudioInputStream(new BufferedInputStream(inputStream), STEREO, inputStream.getFrameLength());
        }
        format = inputStream.getFormat();
      } catch (Exception x) {
        LX.error(x, "Invalid audio format: " + x.getLocalizedMessage());
        return false;
      }
    }

    if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
      LX.error("Audio must be decodable to PCM_SIGNED data");
      return false;
    }
    if (format.getSampleRate() != SAMPLE_RATE) {
      LX.error("Audio file must have sample rate of " + SAMPLE_RATE);
      return false;
    }
    if (format.getSampleSizeInBits() != BITS_PER_SAMPLE) {
      LX.error("Audio file must have " + BITS_PER_SAMPLE + " bits per sample");
      return false;
    }
    if (format.isBigEndian()) {
      LX.error("Audio file must be little endian");
      return false;
    }
    if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
      LX.error("Audio file must be PCM signed");
      return false;
    }
    if (format.getChannels() > 2) {
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
      if (this.play.isOn()) {
        if (this.line == null) {
          this.play.setValue(false);
        } else {
          start();
        }
      } else {
        stop();
      }
    } else if (p == this.trigger) {
      if (this.trigger.isOn()) {
        if (this.line != null) {
          this.play.setValue(true);
          this.outputThread.trigger = true;
        }
        this.trigger.setValue(false);
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
    if (this.line == null) {
      try {
        this.line = (SourceDataLine) AudioSystem.getLine(STEREO_SOURCE_LINE);
        this.line.addLineListener(this);
        this.closed = false;
        this.line.open(this.format, 4*STEREO_BUFFER_SIZE);
        this.stopped = true;
        if (this.play.isOn()) {
          this.stopped = false;
          this.line.start();
        }
        this.outputThread = new OutputThread(this.line);
        this.outputThread.start();
      } catch (Exception x) {
        LX.error(x, "Exception opening stereo output audio line");
        return;
      }
    }
  }

  void start() {
    if (this.line != null) {
      this.stopped = false;
      this.line.start();
      synchronized (this.outputThread) {
        this.outputThread.notify();
      }
    }
  }

  public void close() {
    if (this.line != null) {
      this.closed = true;
      this.line.close();
    }
  }

  void stop() {
    if (this.line != null) {
      this.stopped = true;
      this.line.stop();
    }
  }

  @Override
  public void update(LineEvent event) {
    LineEvent.Type type = event.getType();
    if (type == LineEvent.Type.OPEN) {
      LX.log("LXAudioOuput OPEN");
    } else if (type == LineEvent.Type.START){
      LX.log("LXAudioOuput START");
    } else if (type == LineEvent.Type.STOP) {
      LX.log("LXAudioOuput STOP");
      if (this.line == event.getLine()) {
        this.stopped = true;
      }
    } else if (type == LineEvent.Type.CLOSE) {
      LX.log("LXAudioOuput CLOSE");
      if (this.line == event.getLine()) {
        this.closed = true;
      }
    }
  }

  void reset() {
    this.play.setValue(false);
    this.looping.setValue(false);
    this.file.setValue("");
  }
}
