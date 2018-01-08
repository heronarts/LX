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
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;

public abstract class LXMidiDevice {

  protected final LXMidiEngine engine;
  protected final MidiDevice device;

  public final BooleanParameter enabled = new BooleanParameter("Enabled", false);

  protected LXMidiDevice(LXMidiEngine engine, MidiDevice device) {
    this.engine = engine;
    this.device = device;
    this.enabled.addListener(new LXParameterListener() {
      public void onParameterChanged(LXParameter p) {
        onEnabled(enabled.isOn());
      }
    });
  }

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
    return this.device.getDeviceInfo().getName();
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
