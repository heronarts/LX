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

import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import heronarts.lx.LX;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.ObjectParameter;

public class LXAudioInput extends LXAudioComponent implements LXOscComponent {

  private static final int BUFFER_FRAMES = 2;

  private AudioFormat format;

  public final ObjectParameter<Device> device;

  private InputThread inputThread = null;

  private class InputThread extends Thread implements LineListener {

    private boolean closed = false;
    private boolean stopped = false;

    private final Device device;
    private final TargetDataLine line;

    private final AudioFormat format;
    private final int sampleRate;

    private final byte[] rawBytes;

    private InputThread(Device device, TargetDataLine line, AudioFormat format) {
      super("LXAudioEngine Input Thread");
      this.device = device;
      this.line = line;
      this.format = format;
      this.sampleRate = (int) format.getSampleRate();
      this.rawBytes = new byte[bufferSize(this.format)];

      this.line.addLineListener(this);
      this.line.start();

      // Kick off the thread
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
      LX.log("Started audio input thread: " + this.device);
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

        // Read from the audio line
        this.line.read(this.rawBytes, 0, this.rawBytes.length);

        if (isMono(this.format)) {
          left.putSamples(this.rawBytes, 0, MONO_BUFFER_SIZE_16, MONO_FRAME_SIZE_16, this.sampleRate);
          right.putSamples(this.rawBytes, 0, MONO_BUFFER_SIZE_16, MONO_FRAME_SIZE_16, this.sampleRate);
          mix.putSamples(this.rawBytes, 0, MONO_BUFFER_SIZE_16, MONO_FRAME_SIZE_16, this.sampleRate);
        } else {
          left.putSamples(this.rawBytes, 0, STEREO_BUFFER_SIZE_16, STEREO_FRAME_SIZE_16, this.sampleRate);
          right.putSamples(this.rawBytes, BYTES_PER_SAMPLE_16, STEREO_BUFFER_SIZE_16, STEREO_FRAME_SIZE_16, this.sampleRate);
          mix.computeMix(left, right);
        }
      }
      this.line.removeLineListener(this);
      LX.log("Finished audio input thread: " + this.device);
    }

    @Override
    public void update(LineEvent event) {
      final LineEvent.Type eventType = event.getType();
      LX.debug("LXAudioInput.line.update(LineEvent.Type." + eventType + ")");
      if (eventType == LineEvent.Type.START) {
        setState(false, false);
      } else if (eventType == LineEvent.Type.STOP) {
        setState(true, false);
      } else if (eventType == LineEvent.Type.CLOSE) {
        setState(true, true);
      }
    }

  };

  public static class Device {
    public final Mixer.Info info;
    public final Mixer mixer;
    public final DataLine.Info line;

    Device(Mixer.Info info, Mixer mixer, DataLine.Info dataLine) {
      this.info = info;
      this.mixer = mixer;
      this.line = dataLine;
    }

    @Override
    public String toString() {
      return this.info.getName();
    }

    public boolean isAvailable() {
      return true;
    }

    public static class Unavailable extends Device {
      Unavailable() {
        super(null, null, null);
      }

      @Override
      public String toString() {
        return "No Input";
      }

      @Override
      public boolean isAvailable() {
        return false;
      }

    }
  }

  LXAudioInput(LX lx) {
    super(lx, "Input");

    // Find system input devices...
    List<Device> devices = new ArrayList<Device>();
    Mixer.Info[] mixers = AudioSystem.getMixerInfo();
    for (Mixer.Info mixerInfo : mixers) {
      Mixer mixer = AudioSystem.getMixer(mixerInfo);
      Line.Info[] targetLines = mixer.getTargetLineInfo();
      for (Line.Info lineInfo : targetLines) {
        if (lineInfo instanceof DataLine.Info) {
          devices.add(new Device(mixerInfo, mixer, (DataLine.Info) lineInfo));
          break;
        }
      }
    }
    if (devices.size() == 0) {
      devices.add(new Device.Unavailable());
    }

    this.device = new ObjectParameter<Device>("Device", devices.toArray(new Device[0]));
    addParameter("device", this.device);

  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.device) {
      close();
      if (this.lx.engine.audio.enabled.isOn()) {
        open();
      }
    }
  }

  public AudioFormat getFormat() {
    return this.format;
  }

  private static AudioFormat[] LINE_FORMAT_PRIORITY = {
    AUDIO_FORMAT_STEREO_48K,
    AUDIO_FORMAT_STEREO_44K,
    AUDIO_FORMAT_MONO_48K,
    AUDIO_FORMAT_MONO_44K
  };

  void open() {
    if (this.inputThread == null) {
      final Device device = this.device.getObject();
      if (!device.isAvailable()) {
        LX.error("LXAudioInput device is not available, audio input will not work: " + device);
        return;
      }

      DataLine.Info info = null;
      for (AudioFormat format : LINE_FORMAT_PRIORITY) {
        final DataLine.Info candidate = getTargetLineInfo(format);
        if (device.mixer.isLineSupported(candidate)) {
          this.format = format;
          info = candidate;
          break;
        }
      }
      if (info == null) {
        LX.error("Audio device does not support mono/stereo 16-bit input: " + device);
        return;
      }

      try {
        TargetDataLine targetLine = (TargetDataLine) device.mixer.getLine(info);
        targetLine.open(this.format, BUFFER_FRAMES * bufferSize(this.format));
        // This line seems good, start an input thread to service it
        this.inputThread = new InputThread(device, targetLine, this.format);
      } catch (Exception x) {
        LX.error(x, "Exception opening audio input line and starting audio input thread: " + device);
      }
    }
  }

  void start() {
    if (this.inputThread == null) {
      LX.error("Cannot start LXAudioInput - it is not open: " + this.device.getObject());
      return;
    }
    this.inputThread.line.start();

    // NOTE: the above does *not* always result in a LineListener START callback! That callback
    // does not seem to happen until the InputThread actually resumes and makes another read()
    // call
    this.inputThread.setState(false, false);
  }

  void stop() {
    stop(true);
  }

  void stop(boolean errorIfClosed) {
    if (this.inputThread == null) {
      if (errorIfClosed) {
        LX.error("Cannot stop LXAudioInput - it is not open: " + this.device.getObject());
      }
      return;
    }
    this.inputThread.line.stop();

    // NOTE: LineListener should handle this, but don't trust that completely
    this.inputThread.setState(true, false);
  }

  void close() {
    if (this.inputThread != null) {
      stop();
      this.inputThread.line.flush();
      this.inputThread.line.close();

      // Just in case LineListener doesn't trigger close action...
      this.inputThread.setState(true, true);

      try {
        this.inputThread.join();
      } catch (InterruptedException ix) {
        LX.error(ix, "Interrupted waiting to join audio input thread: " + ix.getLocalizedMessage());
      }
      this.inputThread = null;
    }
  }

  @Override
  public void dispose() {
    close();
    super.dispose();
  }

}
