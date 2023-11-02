/**
 * Copyright 2023- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.dmx;

import heronarts.lx.LXCategory;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.modulator.LXTriggerSource;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;

/**
 * A modulator converting DMX input to normalized output, with three modes:
 *   8-bit: single DMX channel scaled to normalized output
 *   16-bit: two DMX channels for high resolution, scaled to normalized output
 *   Range: A range from [min] to [max] within a DMX channel.
 *          Outputs a normalized value and a boolean indicator of whether
 *          the DMX value is within the range.
 */
@LXModulator.Global("DMX Channel")
@LXModulator.Device("DMX Channel")
@LXCategory(LXCategory.DMX)
public class DmxModulator extends AbstractDmxModulator implements LXOscComponent, LXNormalizedParameter, LXTriggerSource {

  public enum Mode {
    CHANNEL_8("8-bit", 1),
    CHANNEL_16("16-bit", 2),
    RANGE("Range", 1);

    public final String label;
    public final int bytes;

    private Mode(String label, int bytes) {
      this.label = label;
      this.bytes = bytes;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final EnumParameter<Mode> mode =
    new EnumParameter<Mode>("Mode", Mode.CHANNEL_8)
    .setDescription("8-bit = one DMX channel, 16-bit = two DMX channels, Range = part of one DMX channel");

  public final DiscreteParameter min =
    new DiscreteParameter("Min", 0, 256)
    .setDescription("Minimum input value for range");

  public final DiscreteParameter max =
    new DiscreteParameter("Max", 255, 0, 256)
    .setDescription("Maximum input value for range");

  public final BooleanParameter rangeActive =
    new BooleanParameter("Range Active", false)
    .setDescription("True in range mode when DMX value is within [min-max] inclusive");

  public DmxModulator() {
    this("DMX");
  }

  public DmxModulator(String label) {
    super(label);
    addParameter("mode", this.mode);
    addParameter("min", this.min);
    addParameter("max", this.max);
    addParameter("rangeActive", this.rangeActive);
  }

  private boolean internal = false;

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.mode) {
      final Mode mode = this.mode.getEnum();
      setBytes(mode.bytes);
      this.rangeActive.setValue(false);
    }

    // Changes to min or max will push the other value if needed
    if (this.internal) {
      return;
    }
    this.internal = true;
    if (p == this.min) {
      final int min = this.min.getValuei();
      if (this.max.getValuei() < min) {
        this.max.setValue(min);
      }
    } else if (p == this.max) {
      final int max = this.max.getValuei();
      if (this.min.getValuei() > max) {
        this.min.setValue(max);
      }
    }
    this.internal = false;
  }

  @Override
  protected double computeValue(double deltaMs) {
    final Mode mode = this.mode.getEnum();
    final int universe = this.universe.getValuei();
    final int channel = this.channel.getValuei();

    switch (mode) {
    case CHANNEL_16:
      final byte byte1 = this.lx.engine.dmx.getByte(universe, channel);
      final byte byte2 = this.lx.engine.dmx.getByte(universe, channel + 1);
      return (((byte1 & 0xff) << 8) | (byte2 & 0xff)) / 65535.;
    case RANGE:
      final int min = this.min.getValuei();
      final int max = this.max.getValuei();
      final int dmx = this.lx.engine.dmx.getValuei(universe, channel);

      final boolean active = dmx >= min && dmx <= max;
      this.rangeActive.setValue(active);

      if (active) {
        return (max == min) ? 1 : ((double) dmx - min) / (max - min);
      }
      return 0;

    default:
    case CHANNEL_8:
      return lx.engine.dmx.getNormalized(universe, channel);
    }
  }

  @Override
  public BooleanParameter getTriggerSource() {
    return this.rangeActive;
  }

  @Override
  public LXNormalizedParameter setNormalized(double value) {
    throw new UnsupportedOperationException("May not setNormalized on DmxModulator");
  }

  @Override
  public double getNormalized() {
    return getValue();
  }
}
