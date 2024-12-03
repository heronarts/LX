/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
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

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.modulator.LXTriggerSource;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.NormalizedParameter;

/**
 * A frequency gate monitors a Graphic Meter for a particular frequency range and
 * triggers when that range passes a certain threshold. Note that the beat detect
 * does *not* respect the attack and release settings of the underlying meter, it
 * merely shares the raw values. The BeatDetect applies its own time-band filtering.
 */
@LXCategory(LXCategory.AUDIO)
@LXModulator.Global("Beat Detect")
@LXModulator.Device("Beat Detect")
public class BandGate extends BandFilter implements LXNormalizedParameter, LXTriggerSource, LXOscComponent {

  /**
   * The gate level at which the trigger is engaged. When the signal crosses
   * this threshold, the gate fires. Value is in the normalized space from 0 to
   * 1.
   */
  public final BoundedParameter threshold =
    new BoundedParameter("Threshold", 0.8)
    .setUnits(BoundedParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Sets the level at which the band is triggered");

  /**
   * The floor at which the trigger releases. Once triggered, the signal must
   * fall below this amount before a new trigger may occur. This value is
   * specified as a fraction of the threshold. So, a value of 0.75 means the
   * signal must fall to 75% of the threshold value.
   */
  public final BoundedParameter floor =
    new BoundedParameter("Floor", 0.75)
    .setUnits(BoundedParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Sets the level the signal must drop below before being triggered again");

  /**
   * The time the trigger takes to falloff from 1 to 0 after triggered, in
   * milliseconds
   */
  public final BoundedParameter decay =
    new BoundedParameter("Decay", 400, 0, 1600)
    .setDescription("Sets the decay time of the trigger signal")
    .setUnits(LXParameter.Units.MILLISECONDS_RAW);

  /**
   * Gate parameter is set to true for one frame when the beat is triggered.
   */
  public final BooleanParameter gate =
    new BooleanParameter("Gate")
    .setDescription("Engages when the beat is first detected")
    .setMode(BooleanParameter.Mode.MOMENTARY);

  /**
   * Turn this parameter on to have this modulator tap the tempo system
   */
  public final BooleanParameter teachTempo =
    new BooleanParameter("Tap", false)
    .setDescription("When enabled, each triggering of the band taps the global tempo");

  private int tapCount = 0;

  /**
   * Level parameter is the average of the monitored band
   */
  public final NormalizedParameter average =
    new NormalizedParameter("Average")
    .setDescription("Computed average level of the audio within the frequency range");

  /**
   * Envelope value that goes from 1 to 0 after this band is triggered
   */
  private double envelope = 0;

  private boolean waitingForFloor = false;

  public BandGate(LX lx) {
    this("Beat", lx);
  }

  public BandGate(String label, LX lx) {
    this(label, lx.engine.audio.meter);
  }

  /**
   * Constructs a gate that monitors a specified frequency band
   *
   * @param label Label
   * @param meter GraphicEQ object to drive this gate
   */
  public BandGate(String label, GraphicMeter meter) {
    super(label, meter);

    addParameter("threshold", this.threshold);
    addParameter("floor", this.floor);
    addParameter("decay", this.decay);
    addParameter("gate", this.gate);
    addParameter("average", this.average);
    addParameter("tap", this.teachTempo);

    setDescription("Envelope that fires when a beat is detected");
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.teachTempo) {
      this.tapCount = 0;
    }
  }

  /**
   * Constructs a gate that monitors a specified frequency band
   *
   * @param meter Equalizer to monitor
   * @param minHz Minimum frequency band
   * @param maxHz Maximum frequency band
   */
  public BandGate(GraphicMeter meter, float minHz, float maxHz) {
    this("Beat", meter);
    setFrequencyRange(minHz, maxHz);
  }

  /**
   * Constructs a gate that monitors a specified frequency band
   *
   * @param label Label
   * @param meter Equalizer to monitor
   * @param minHz Minimum frequency band
   * @param maxHz Maximum frequency band
   */
  public BandGate(String label, GraphicMeter meter, int minHz, int maxHz) {
    this(label, meter);
    setFrequencyRange(minHz, maxHz);
  }

  @Override
  protected double computeValue(double deltaMs) {
    double filterAverage = super.computeValue(deltaMs);
    this.average.setValue(filterAverage);

    double thresholdValue = this.threshold.getValue();

    if (this.waitingForFloor) {
      double floorValue = thresholdValue * this.floor.getValue();
      if (averageNorm < floorValue) {
        this.waitingForFloor = false;
      }
    }

    boolean triggered = !this.waitingForFloor && (thresholdValue > 0) && (averageNorm >= thresholdValue);
    if (triggered) {
      if (this.teachTempo.isOn()) {
        this.lx.engine.tempo.tap();
        if (++this.tapCount >= 4) {
          this.teachTempo.setValue(false);
        }
      }
      this.waitingForFloor = true;
      this.envelope = 1;
    } else {
      this.envelope = Math.max(0, this.envelope - deltaMs / this.decay.getValue());
    }
    this.gate.setValue(triggered);

    return this.envelope;
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException("BandGate does not support setNormalized()");
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.gate;
  }
}
