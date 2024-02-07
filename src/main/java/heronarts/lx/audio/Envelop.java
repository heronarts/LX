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

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXRunnableComponent;
import heronarts.lx.Tempo.ClockSource;
import heronarts.lx.osc.LXOscEngine;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.NormalizedParameter;
import heronarts.lx.utils.LXUtils;

public class Envelop extends LXRunnableComponent {

  // Envelop source sound object meters
  public final Source source;

  // Envelop decoded output meters for the columns
  public final Decode decode;

  public final BooleanParameter metersExpanded =
    new BooleanParameter("Meters Expanded", false)
    .setDescription("Show Envelop meters in the UI");

  public Envelop(LX lx) {
    super(lx, "Envelop");
    addInternalParameter("metersExpanded", this.metersExpanded);
    addChild("source", this.source = new Source());
    addChild("decode", this.decode = new Decode());
    this.source.start();
    this.decode.start();
  }

  @Override
  public void onStop() {
    this.source.clear();
    this.decode.clear();
  }

  @Override
  public void run(double deltaMs) {
    this.source.loop(deltaMs);
    this.decode.loop(deltaMs);
  }

  protected abstract class MultiMeter extends LXRunnableComponent {

    private static final double TIMEOUT = 1000;

    private final double[] targets;
    private final double[] timeouts;

    public final BoundedParameter gain =
      new BoundedParameter("Gain", 0, -24, 24)
      .setDescription("Sets the dB gain of the meter")
      .setUnits(BoundedParameter.Units.DECIBELS);

    public final BoundedParameter range =
      new BoundedParameter("Range", 24, 6, 96)
      .setDescription("Sets the dB range of the meter")
      .setUnits(BoundedParameter.Units.DECIBELS);

    public final BoundedParameter attack =
      new BoundedParameter("Attack", 0, 0, 100)
      .setDescription("Sets the attack time of the meter response")
      .setUnits(BoundedParameter.Units.MILLISECONDS);

    public final BoundedParameter release =
      new BoundedParameter("Release", 50, 0, 500)
      .setDescription("Sets the release time of the meter response")
      .setUnits(BoundedParameter.Units.MILLISECONDS);

    protected MultiMeter(String label, int numChannels) {
      super(label);
      this.targets = new double[numChannels];
      this.timeouts = new double[numChannels];
      addParameter("gain", this.gain);
      addParameter("range", this.range);
      addParameter("attack", this.attack);
      addParameter("release", this.release);
    }

    protected void clear() {
      for (NormalizedParameter channel : getChannels()) {
        channel.setValue(0);
      }
    }

    @Override
    public void run(double deltaMs) {
      final double attack = this.attack.getValue();
      final double release = this.release.getValue();
      NormalizedParameter[] channels = getChannels();
      for (int i = 0; i < channels.length; ++i) {
        this.timeouts[i] += deltaMs;
        if (this.timeouts[i] > TIMEOUT) {
          this.targets[i] = 0;
        }
        final double target = this.targets[i];
        final double value = channels[i].getValue();
        final double gain = Math.exp(-deltaMs / ((target >= value) ? attack : release));
        channels[i].setValue(LXUtils.lerp(target, value, gain));
      }
    }

    protected void setLevel(int index, OscMessage message) {
      double gainValue = this.gain.getValue();
      double rangeValue = this.range.getValue();
      this.targets[index] = LXUtils.constrain((float) (1 + (message.getFloat() + gainValue) / rangeValue), 0, 1);
      this.timeouts[index] = 0;
    }

    protected void setLevels(OscMessage message) {
      double gainValue = this.gain.getValue();
      double rangeValue = this.range.getValue();
      for (int i = 0; i < this.targets.length; ++i) {
        this.targets[i] = LXUtils.constrain((float) (1 + (message.getFloat() + gainValue) / rangeValue), 0, 1);
        this.timeouts[i] = 0;
      }
    }

    protected abstract NormalizedParameter[] getChannels();
  }

  public class Source extends MultiMeter {

    public static final int NUM_CHANNELS = 32;

    public final NormalizedParameter[] channels = new NormalizedParameter[NUM_CHANNELS];

    private Source() {
      super("Source", NUM_CHANNELS);
      for (int i = 0; i < this.channels.length; ++i) {
        addParameter(
          "source-" + (i+1),
          this.channels[i] = new NormalizedParameter("Source-" + (i+1))
          .setDescription("Envelop source object " + (i+1))
        );
      }
    }

    @Override
    public NormalizedParameter[] getChannels() {
      return this.channels;
    }
  }

  public class Decode extends MultiMeter {

    public static final int NUM_CHANNELS = 8;

    public final NormalizedParameter[] channels = new NormalizedParameter[NUM_CHANNELS];

    private Decode() {
      super("Decode", NUM_CHANNELS);
      for (int i = 0; i < this.channels.length; ++i) {
        addParameter(
          "decode-" + (i+1),
          this.channels[i] = new NormalizedParameter("Decode-" + (i+1))
          .setDescription("Envelop column decode " + (i+1))
        );
      }
    }

    @Override
    public NormalizedParameter[] getChannels() {
      return this.channels;
    }
  }

  public static final String ENVELOP_OSC_PATH = "envelop";
  public static final String ENVELOP_METER_PATH = "meter";
  public static final String ENVELOP_SOURCE_PATH = "source";
  public static final String ENVELOP_DECODE_PATH = "decode";
  public static final String ENVELOP_SOURCE_AED = "aed";
  public static final String ENVELOP_SOURCE_XYZ = "xyz";
  public static final String ENVELOP_TEMPO_PATH = "tempo";
  public static final String ENVELOP_BEAT_PATH = "beat";
  public static final String ENVELOP_BPM_PATH = "bpm";

  public boolean handleEnvelopOscMessage(OscMessage message, String[] parts, int index) {
    if (ENVELOP_SOURCE_PATH.equals(parts[2])) {
      final int sourceIndex = Integer.parseInt(parts[3]) - 1;
      if (sourceIndex < 0 || sourceIndex >= ADM.MAX_ADM_OBJECTS) {
        LXOscEngine.error("Bad Envelop source channel index: " + message.getAddressPattern());
        return false;
      }
      ADM.Obj obj = this.lx.engine.audio.adm.obj.get(sourceIndex);
      final String sourceFormat = message.getString();
      if (ENVELOP_SOURCE_AED.equals(sourceFormat)) {

        float azimuth = message.getFloat();
        float elevation = message.getFloat();
        float distance = message.getFloat();

        // Let's handle normalizing values that might be out of strict range
        float sign = (elevation >= 0) ? 1 : -1;
        boolean flipAzimuth = false;

        // Elevation values that go beyond 90 degrees will require adjusting
        // the elevation value and possibly a 180 degree azimuth rotation to
        // get back into normalized polar coordinate space.
        final float absElevation = Math.abs(elevation);
        if (elevation >= 270f) {
          // We're flipped 3/4 way around, top/bottom are flipped but
          // azimuth is unchanged
          elevation = sign * (absElevation - 360f);
        } else if (absElevation > 90f) {
          // We're flipped onto the other lateral "side" of the sphere,
          // where azimuth is 180 degrees opposite but top stays top
          // and bottom stays bottom
          flipAzimuth = true;
          elevation = sign * (180f - absElevation);
        }

        // The azimuth may need a 180 degree rotation if elevation put us
        // on the other side of the sphere
        if (flipAzimuth) {
          azimuth += 180f;
        }

        // Squash azimuth into [-180,180] bounds
        azimuth = ((azimuth + 540f) % 360f) - 180f;

        // Set values
        obj.azimuth.setValue(-azimuth);
        obj.elevation.setValue(elevation);
        obj.distance.setValue(distance);

      } else if (ENVELOP_SOURCE_XYZ.equals(sourceFormat)) {
        obj.x.setValue(message.getFloat());
        obj.y.setValue(message.getFloat());
        obj.z.setValue(message.getFloat());
        obj.updatePolar();
      } else {
        LXOscEngine.error("Bad Envelop source format: " + message.getAddressPattern());
        return false;
      }
      return true;
    } else if (ENVELOP_METER_PATH.equals(parts[2])) {
      if (ENVELOP_SOURCE_PATH.equals(parts[3])) {
        final int sourceIndex = Integer.parseInt(parts[4]) - 1;
        if (sourceIndex < 0 || sourceIndex >= Source.NUM_CHANNELS) {
          LXOscEngine.error("Invalid Envelop source meter index: " + message.getAddressPattern());
        } else {
          this.source.setLevel(sourceIndex, message);
        }
      } else if (ENVELOP_DECODE_PATH.equals(parts[3])) {
        if (message.size() < Decode.NUM_CHANNELS) {
          LXOscEngine.error("Not enough channels in Envelop decode message (expecting " + Decode.NUM_CHANNELS + "): " + message.toString());
        } else {
          this.decode.setLevels(message);
        }
      }
      return true;
    } else if (ENVELOP_TEMPO_PATH.equals(parts[2])) {
      if (this.lx.engine.tempo.clockSource.getEnum() == ClockSource.OSC) {
        if (ENVELOP_BPM_PATH.equals(parts[3])) {
          if (message.size() == 0) {
            LXOscEngine.error("Envelop bpm message missing argument: " + message.toString());
          } else {
            this.lx.engine.tempo.bpm.setValue(message.getFloat());
          }
        } else if (ENVELOP_BEAT_PATH.equals(parts[3])) {
          if (message.size() == 0) {
            LXOscEngine.error("Envelop beat message missing arguments: " + message.toString());
          } else {
            final int beat = message.getInt();
            if (message.size() >= 3) {
              final int bar = message.getInt();
              final float bpm = message.getFloat();
              if (bar >= 1 && beat >= 1) {
                this.lx.engine.tempo.triggerBarAndBeat(bar, beat, message);
              }
              this.lx.engine.tempo.bpm.setValue(bpm);
            } else {
              this.lx.engine.tempo.triggerBeatWithinBar(beat, message);
            }
          }
        }
      }
    }
    return false;
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(KEY_RESET)) {
      this.metersExpanded.setValue(false);
      stop();
    }
  }
}