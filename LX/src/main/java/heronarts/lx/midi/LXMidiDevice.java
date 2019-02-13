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

package heronarts.lx.midi;

import javax.sound.midi.MidiDevice;

import heronarts.lx.parameter.BooleanParameter;

public abstract class LXMidiDevice {

  protected final LXMidiEngine engine;
  protected MidiDevice device;

  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", false);

  // Helper used by LXMidiEngine to check active
  boolean keepAlive = true;

  public final BooleanParameter connected =
    new BooleanParameter("Connected", true);

  protected LXMidiDevice(LXMidiEngine engine, MidiDevice device) {
    this.engine = engine;
    this.device = device;
    this.enabled.addListener((p) -> {
      onEnabled(enabled.isOn());
    });
  }

  void setDevice(MidiDevice device) {
    if (device == null) {
      throw new IllegalArgumentException("Cannot set null device on LXMidiDevice");
    }
    if (this.device != device) {
      close();
      this.device = device;
      this.connected.setValue(true);
      this.enabled.bang();
    }
  }

  MidiDevice getDevice() {
    return this.device;
  }

  protected abstract void close();

  /**
   * Open the device for input or output
   *
   * @return this
   */
  public LXMidiDevice open() {
    this.enabled.setValue(true);
    return this;
  }

  /**
   * Get the name of the device.
   *
   * @return Device name
   */
  public String getName() {
    return LXMidiEngine.getDeviceName(this.device.getDeviceInfo());
  }

  /**
   * Get a description of this device
   *
   * @return Device description
   */
  public String getDescription() {
    return this.device.getDeviceInfo().getDescription();
  }

  protected abstract void onEnabled(boolean enabled);

}
