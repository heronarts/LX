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

  private AudioFormat format = STEREO;

  public final ObjectParameter<Device> device;

  private InputThread inputThread = null;

  private class InputThread extends Thread implements LineListener {

    private boolean closed = false;
    private boolean stopped = false;

    private final Device device;
    private final TargetDataLine line;

    private final AudioFormat format;

    private final byte[] rawBytes;

    private InputThread(Device device, TargetDataLine line, AudioFormat format) {
      super("LXAudioEngine Input Thread");
      this.device = device;
      this.line = line;
      this.format = format;
      this.rawBytes = new byte[this.format == MONO ? MONO_BUFFER_SIZE : STEREO_BUFFER_SIZE];

      this.line.addLineListener(this);
      this.line.start();

      // Kick off the thread
      start();
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

        if (this.format == MONO) {
          mix.putSamples(rawBytes, 0, MONO_BUFFER_SIZE, MONO_FRAME_SIZE);
        } else {
          left.putSamples(rawBytes, 0, STEREO_BUFFER_SIZE, STEREO_FRAME_SIZE);
          right.putSamples(rawBytes, 2, STEREO_BUFFER_SIZE, STEREO_FRAME_SIZE);
          mix.computeMix(left, right);
        }
      }
      this.line.removeLineListener(this);
      LX.log("Finished audio input thread: " + this.device);
    }

    @Override
    public void update(LineEvent event) {
      LineEvent.Type type = event.getType();
      if (type == LineEvent.Type.OPEN) {
      } else if (type == LineEvent.Type.START) {
      } else if (type == LineEvent.Type.STOP) {
        this.stopped = true;
      } else if (type == LineEvent.Type.CLOSE) {
        this.closed = true;
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

  void open() {
    if (this.inputThread == null) {
      Device device = this.device.getObject();
      if (!device.isAvailable()) {
        LX.error("LXAudioInput device is not available, audio input will not work: " + device);
        return;
      }

      DataLine.Info info = null;
      if (device.mixer.isLineSupported(STEREO_TARGET_LINE)) {
        this.format = STEREO;
        info = STEREO_TARGET_LINE;
      } else if (device.mixer.isLineSupported(MONO_TARGET_LINE)) {
        this.format = MONO;
        info = MONO_TARGET_LINE;
      } else {
        LX.error("Audio device does not support mono/stereo 16-bit input: " + device);
        return;
      }

      TargetDataLine targetLine = null;
      try {
        targetLine = (TargetDataLine) device.mixer.getLine(info);
        targetLine.open(this.format, 2 * (this.format == MONO ? MONO_BUFFER_SIZE : STEREO_BUFFER_SIZE));
      } catch (Exception x) {
        LX.error(x, "Exception opening audio input line and starting audio input thread: " + device);
        return;
      }

      // This line seems good, start an input thread to service it
      this.inputThread = new InputThread(device, targetLine, this.format);
    }
  }

  void start() {
    if (this.inputThread == null) {
      LX.error("Cannot start LXAudioInput - it is not open: " + this.device.getObject());
      return;
    }
    this.inputThread.stopped = false;
    this.inputThread.line.start();
    synchronized (this.inputThread) {
      this.inputThread.notify();
    }
  }

  void stop() {
    if (this.inputThread == null) {
      LX.error("Cannot stop LXAudioInput - it is not open: " + this.device.getObject());
      return;
    }
    this.inputThread.stopped = true;
    this.inputThread.line.stop();
    synchronized (this.inputThread) {
      this.inputThread.notify();
    }
  }

  void close() {
    if (this.inputThread != null) {
      this.inputThread.line.flush();
      stop();
      this.inputThread.closed = true;
      this.inputThread.line.close();
      synchronized (this.inputThread) {
        this.inputThread.notify();
      }
      try {
        this.inputThread.join();
      } catch (InterruptedException ix) {
        LX.error(ix, "Interrupted waiting to join audio input thread: " + ix.getLocalizedMessage());
      }
      this.inputThread = null;
    }
  }

}
