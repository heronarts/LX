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

  /**
   * Whether the midi device is enabled for communication. This means that it should be
   * opened if possible, or re-opened if its connection is lost. This parameter being
   * true doesn't guarantee availability, but it indicates that we want availability
   * if at all possible.
   */
  public final BooleanParameter enabled = (BooleanParameter)
    new BooleanParameter("Enabled", false)
    .setMappable(false);

  // Helper used by LXMidiEngine to check active
  boolean keepAlive = true;

  /**
   * Whether the MIDI device is connected. It is possible for enabled to be true, but for
   * this parameter to end up being false if the device connection is lost. So long as
   * enabled remains set to true, the connection will attempt to be restored when possible.
   *
   * This is a "read-API" parameter. Do not set its value, the internal implementation will
   * set its value and it may be observed by API clients to determine current connection state.
   *
   * Note that connected is optimistically assumed, the default value is true, but
   * the implementation will set it to false if it notices that this MIDI device is not
   * connected to the system anymore.
   */
  public final BooleanParameter connected =
    new BooleanParameter("Connected", true);

  protected LXMidiDevice(LXMidiEngine engine, MidiDevice device) {
    this.engine = engine;
    this.device = device;
    this.enabled.addListener((p) -> {
      onEnabled(this.enabled.isOn());
    });
  }

  void setDevice(MidiDevice device) {
    if (device == null) {
      throw new IllegalArgumentException("Cannot set null device on LXMidiDevice");
    }
    if (this.device != device) {
      // Close any existing device, update
      close();
      this.device = device;

      // Try to re-do whatever enabled was set to. This should re-open the device
      // if enabled was set to true. Then mark connected as true for this new device
      this.enabled.bang();
      this.connected.setValue(true);
    }
  }

  MidiDevice getDevice() {
    return this.device;
  }

  protected abstract void close();

  /**
   * Open the device for input or output. Simply sets the enabled flag to
   * true, which implementation classes should observe and attempt to comply
   * with.
   *
   * @return this
   */
  public final LXMidiDevice open() {
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
   * Get the unique name of the device.
   *
   * @return Unique device name
   */
  public String getNameUnique() {
    return LXMidiEngine.getDeviceNameUnique(this.device.getDeviceInfo());
  }

  /**
   * Get a description of this device
   *
   * @return Device description
   */
  public String getDescription() {
    return this.device.getDeviceInfo().getDescription();
  }

  /**
   * Subclasses have this method invoked when the enabled state changes.
   *
   * @param enabled Enabled state, if newly set to true, attempt to open
   */
  protected abstract void onEnabled(boolean enabled);

  void dispose() {
    // Close the connection
    close();
    try {
      if (this.device.isOpen()) {
        this.device.close();
      }
    } catch (Exception ignored) {
      // Technically should never happen, but just to beware of weird
      // MIDI implementations on strange systems
    }
  }

}
