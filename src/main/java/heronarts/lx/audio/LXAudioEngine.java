/**
 * Copyright 2016- Mark C. Slee, Heron Arts LLC
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
import heronarts.lx.LXModulatorComponent;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.MutableParameter;

public class LXAudioEngine extends LXModulatorComponent implements LXOscComponent {

  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", false)
    .setDescription("Sets whether the audio engine is active");

  public final BooleanParameter expandedPerformance =
    new BooleanParameter("Enabled", true)
    .setDescription("Whether the audio pane is expanded in performance mode");

  /**
   * Sound objects are modulators, which are restored *after* the mixer and channel
   * engines. However, patterns + effects etc. may make reference to SoundObject.Selector,
   * which needs to know the total number of sound objects that are going to exist. So we
   * track and restore that number here. When the sound objects are actually instantiated,
   * their names will be restored to the selector.
   */
  final MutableParameter numSoundObjects = (MutableParameter)
    new MutableParameter("Sound Objects", 0)
    .setDescription("Number of registered sound objects");

  /**
   * Audio input object
   */
  public final LXAudioInput input;

  public final LXAudioOutput output;

  public final Meter meter;

  public final SoundStage soundStage;

  public final ADM adm;

  public final Envelop envelop;

  public final Reaper reaper;

  public enum Mode {
    INPUT("Input"),
    OUTPUT("Output");

    public final String label;

    private Mode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  };

  public final EnumParameter<Mode> mode = new EnumParameter<Mode>("Mode", Mode.INPUT);

  public final BooleanParameter ioExpanded =
    new BooleanParameter("I/O Expanded", false)
    .setDescription("Show I/O section in the UI");

  public class Meter extends GraphicMeter {

    /**
     * Metering of the left channel only
     */
    public final DecibelMeter left;

    /**
     * Metering of the right channel only
     */
    public final DecibelMeter right;

    private Meter() {
      super("Meter", input.mix);
      this.left = new DecibelMeter("Left", input.left, this.gain, this.range, this.attack, this.release);
      this.right = new DecibelMeter("Right", input.right, this.gain, this.range, this.attack, this.release);
    }

    public Meter setBuffer(LXAudioComponent device) {
      setBuffer(device.mix);
      this.left.setBuffer(device.left);
      this.right.setBuffer(device.right);
      return this;
    }

    @Override
    public void onStart() {
      this.left.start();
      this.right.start();
    }

    @Override
    public void onStop() {
      this.left.stop();
      this.right.stop();
    }

    @Override
    public double computeValue(double deltaMs) {
      double value = super.computeValue(deltaMs);

      // Run the left and right meters
      this.left.loop(deltaMs);
      this.right.loop(deltaMs);

      return value;
    }


  }

  public LXAudioEngine(LX lx) {
    super(lx, "Audio");
    addParameter("enabled", this.enabled);
    addParameter("mode", this.mode);
    addParameter("expandedPerformance", this.expandedPerformance);
    addInternalParameter("ioExpanded", this.ioExpanded);
    addInternalParameter("numSoundObjects", this.numSoundObjects);

    addChild("input", this.input = new LXAudioInput(lx));
    addChild("output", this.output = new LXAudioOutput(lx));
    addChild("soundStage", this.soundStage = new SoundStage(lx));
    addChild("adm", this.adm = new ADM(lx));
    addChild("envelop", this.envelop = new Envelop(lx));
    addChild("reaper", this.reaper = new Reaper(lx));

    addModulator("meter", this.meter = new Meter());
  }

  @Override
  public void loop(double deltaMs) {
    super.loop(deltaMs);
    this.envelop.loop(deltaMs);
    this.reaper.loop(deltaMs);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.enabled) {
      if (this.enabled.isOn()) {
        this.input.open();
        this.input.start();
        // TODO(mcslee): start/stop output?
      } else {
        this.input.stop();
        // TODO(mcslee): start/stop output?
      }
      this.meter.running.setValue(this.enabled.isOn());
    } else if (p == this.mode) {
      switch (this.mode.getEnum()) {
      case INPUT: this.meter.setBuffer(this.input); break;
      case OUTPUT: this.meter.setBuffer(this.output); break;
      }
    }
  }

  /**
   * Retrieves the audio input object at default sample rate of 44.1kHz
   *
   * @return Audio input object
   */
  public final LXAudioInput getInput() {
    return this.input;
  }

  @Override
  public void dispose() {
    this.input.close();
    this.output.close();
    super.dispose();
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    this.output.reset();
    this.numSoundObjects.setValue(0);
    super.load(lx, obj);
    SoundObject.updateSelectors(lx);
  }

}
