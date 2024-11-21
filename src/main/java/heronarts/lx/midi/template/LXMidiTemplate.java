/**
 * Copyright 2024- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.midi.template;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.midi.LXMidiEngine;
import heronarts.lx.midi.LXMidiInput;
import heronarts.lx.midi.LXMidiListener;
import heronarts.lx.midi.LXMidiOutput;
import heronarts.lx.midi.MidiSelector;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;

/**
 * A MIDI template is a component that holds parameters for a known MIDI device, which
 * does not implement a full MIDI surface implementation, but rather just exposes those
 * parameters for modulation mapping via the UI.
 */
public abstract class LXMidiTemplate extends LXComponent implements LXComponent.Renamable, LXMidiListener {

  @Documented
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Name {
    String value();
  }

  @Documented
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface DeviceName {
    String value();
  }

  /**
   * Marker interface for MIDI templates that include output back to the device
   */
  public interface Bidirectional {}

  public final MidiSelector.Source.Device sourceDevice =
    new MidiSelector.Source.Device("Source");

  public final MidiSelector.Destination.Device destinationDevice;

  public final BooleanParameter controlsExpanded =
    new BooleanParameter("Expanded", true)
    .setDescription("Whether UI controls are expanded");

  public final BooleanParameter connected =
    new BooleanParameter("Connected", false)
    .setDescription("Active when the device is connected");

  private LXMidiInput input = null;
  private LXMidiOutput output = null;

  protected LXMidiTemplate(LX lx) {
    super(lx);
    this.sourceDevice.setMissing();
    this.label.setValue(getTemplateName());
    setParent(lx.engine.midi);
    addParameter("sourceDevice", this.sourceDevice);
    if (this instanceof Bidirectional) {
      this.destinationDevice = new MidiSelector.Destination.Device("Destination");
      this.destinationDevice.setMissing();
      addParameter("destinationDevice", this.destinationDevice);
    } else {
      this.destinationDevice = null;
    }
    addInternalParameter("controlsExpanded", this.controlsExpanded);
  }

  public int getIndex() {
    return this.lx.engine.midi.templates.indexOf(this);
  }

  @Override
  public String getPath() {
    return LXMidiEngine.TEMPLATE_PATH + "/" + (getIndex() + 1);
  }

  private final LXParameterListener onConnected = p -> {
    boolean connected = (this.input != null) && this.input.connected.isOn();
    if (connected && (this instanceof Bidirectional)) {
      connected = (this.output != null) && this.output.connected.isOn();
    }
    this.connected.setValue(connected);
  };

  public LXMidiTemplate initializeDefaultIO() {
    LXMidiTemplate.DeviceName annotation = getClass().getAnnotation(LXMidiTemplate.DeviceName.class);
    if (annotation != null) {
      String deviceName = annotation.value();
      this.sourceDevice.setInput(this.lx.engine.midi.findInput(deviceName));
      if (this instanceof Bidirectional) {
        this.destinationDevice.setOutput(this.lx.engine.midi.findOutput(deviceName));
      }
    }
    return this;
  }

  public String getTemplateName() {
    return getTemplateName(getClass());
  }

  public static String getTemplateName(Class<? extends LXMidiTemplate> templateClass) {
    LXMidiTemplate.Name annotation = templateClass.getAnnotation(LXMidiTemplate.Name.class);
    if (annotation != null) {
      return annotation.value();
    }
    return templateClass.getSimpleName();
  }

  /**
   * Subclasses should override this to initialize output values
   * in the case where device synchronization is needed
   */
  protected /* abstract */ void initializeOutput() {}

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.sourceDevice) {
      setInput(this.sourceDevice.getInput());
    } else if (p == this.destinationDevice) {
      setOutput(this.destinationDevice.getOutput());
    }
  }

  private void setInput(LXMidiInput input) {
    if (this.input != null) {
      this.input.connected.removeListener(this.onConnected);
      this.input.removeListener(this);
      this.connected.setValue(false);
      this.input = null;
    }
    this.input = input;
    if (this.input != null) {
      this.input.addListener(this);
      this.input.connected.addListener(this.onConnected, true);
      this.input.open();
    }
  }

  private void setOutput(LXMidiOutput output) {
    if (this.output != null) {
      this.output.connected.removeListener(this.onConnected);
      this.connected.setValue(false);
      this.output = null;
    }
    this.output = output;
    if (this.output != null) {
      this.output.open();
      this.output.connected.addListener(this.onConnected, true);
      initializeOutput();
    }
  }

  protected void sendNoteOn(int channel, int note, int velocity) {
    if (this.output != null) {
      this.output.sendNoteOn(channel, note, velocity);
    }
  }

  protected void sendControlChange(int channel, int cc, int value) {
    if (this.output != null) {
      this.output.sendControlChange(channel, cc, value);
    }
  }

  protected void sendSysex(byte[] sysex) {
    if (this.output != null) {
      this.output.sendSysex(sysex);
    }
  }

  @Override
  public void dispose() {
    setOutput(null);
    setInput(null);
    super.dispose();
  }

}
