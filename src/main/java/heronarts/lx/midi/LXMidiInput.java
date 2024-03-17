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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Transmitter;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXSerializable;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXParameterListener;

public class LXMidiInput extends LXMidiDevice implements LXSerializable {

  private final List<LXMidiListener> listeners = new ArrayList<LXMidiListener>();
  private boolean isOpen = false;
  private Transmitter transmitter = null;

  private final Receiver receiver = new Receiver();

  public final BooleanParameter channelEnabled =
    new BooleanParameter("Channel", false)
    .setMappable(false)
    .setDescription("Whether midi events from this input are forwarded to channel and modulator devices");

  public final BooleanParameter controlEnabled =
    new BooleanParameter("Control", false)
    .setMappable(false)
    .setDescription("Whether midi events from this input are used for control mapping");

  public final BooleanParameter syncEnabled =
    new BooleanParameter("Sync", false)
    .setMappable(false)
    .setDescription("Whether midi clock signal from this input is used to control tempo");

  LXMidiInput(LXMidiEngine engine, MidiDevice device) {
    super(engine, device);

    LXParameterListener enabledListener = (p) -> {
      this.enabled.setValue(this.channelEnabled.isOn() || this.controlEnabled.isOn() || this.syncEnabled.isOn());
    };
    this.channelEnabled.addListener(enabledListener);
    this.controlEnabled.addListener(enabledListener);
    this.syncEnabled.addListener(enabledListener);
  }

  @Override
  protected void close() {
    if (this.isOpen) {
      try {
        this.transmitter.close();
      } finally {
        this.transmitter = null;
        this.isOpen = false;
      }
    }
  }

  @Override
  protected void onEnabled(boolean enabled) {
    if (enabled && !this.isOpen) {
      try {
        if (!this.device.isOpen()) {
          this.device.open();
        }
        this.transmitter = this.device.getTransmitter();
        this.transmitter.setReceiver(this.receiver);
        this.isOpen = true;
      } catch (MidiUnavailableException mux) {
        LXMidiEngine.error("Could not enable LXMidiInput device " + this + ": " + mux.getLocalizedMessage());
        this.enabled.setValue(false);
      }
    }
  }

  /**
   * Registers a listener to this MIDI input
   *
   * @param listener Listener to receive callbacks
   * @return this
   */
  public LXMidiInput addListener(LXMidiListener listener) {
    Objects.requireNonNull(listener, "May not add null LXMidiInput.LXMidiListener");
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate LXMidiInput.LXMidiListener: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  /**
   * Removes a listener to this MIDI input
   *
   * @param listener Listener to stop receiving callbacks
   * @return this
   */
  public LXMidiInput removeListener(LXMidiListener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot remove non-existent LXMidiInput.LXMidiListener: " + listener);
    }
    this.listeners.remove(listener);
    return this;
  }

  /**
   * This receiver is called by a MIDI thread, it just puts messages
   * into a queue that can then be called by the engine thread.
   */
  private class Receiver implements javax.sound.midi.Receiver {

    private final static int PULSES_PER_QUARTER_NOTE = 24;

    private int beatClock = 0;
    private long lastBeatNanos = -1;

    @Override
    public void close() {
      // No resources owned by this receiver alone
    }

    @Override
    public void send(MidiMessage midiMessage, long timeStamp) {
      if (midiMessage instanceof ShortMessage) {
        ShortMessage sm = (ShortMessage) midiMessage;
        LXShortMessage message = null;
        switch (sm.getCommand()) {
        case ShortMessage.NOTE_ON:
          message = new MidiNoteOn(sm);
          break;
        case ShortMessage.NOTE_OFF:
          message = new MidiNoteOff(sm);
          break;
        case ShortMessage.CONTROL_CHANGE:
          message = new MidiControlChange(sm);
          break;
        case ShortMessage.PROGRAM_CHANGE:
          message = new MidiProgramChange(sm);
          break;
        case ShortMessage.PITCH_BEND:
          message = new MidiPitchBend(sm);
          break;
        case ShortMessage.CHANNEL_PRESSURE:
          message = new MidiAftertouch(sm);
          break;
        case SysexMessage.SYSTEM_EXCLUSIVE:
          switch (sm.getStatus()) {
          case ShortMessage.START:
            this.beatClock = 0;
            this.lastBeatNanos = System.nanoTime();
            message = new MidiBeat(sm, 0, this.lastBeatNanos);
            break;
          case ShortMessage.CONTINUE:
            if (this.beatClock % PULSES_PER_QUARTER_NOTE == 0) {
              this.lastBeatNanos = System.nanoTime();
              message = new MidiBeat(sm, this.beatClock / PULSES_PER_QUARTER_NOTE, this.lastBeatNanos);
            }
            break;
          case ShortMessage.STOP:
            message = new MidiBeat(sm, MidiBeat.STOP, 0);
            this.lastBeatNanos = -1;
            break;
          case ShortMessage.SONG_POSITION_POINTER:
            this.lastBeatNanos = -1;
            int sixteenthNotes = sm.getData1() + (sm.getData2() << 7);
            this.beatClock = sixteenthNotes * PULSES_PER_QUARTER_NOTE / 4;
            break;
          case ShortMessage.TIMING_CLOCK:
            ++this.beatClock;
            if (this.beatClock % PULSES_PER_QUARTER_NOTE == 0) {
              long now = System.nanoTime();
              MidiBeat beat = new MidiBeat(sm, this.beatClock / PULSES_PER_QUARTER_NOTE, now);
              if (this.lastBeatNanos > 0) {
                beat.setPeriod((now - this.lastBeatNanos) / 1000000.);
              }
              message = beat;
              this.lastBeatNanos = now;
            }
            break;
          }
          break;
        }

        if (message != null) {
          message.setInput(LXMidiInput.this);
          engine.queueInputMessage(message);
        }
      }
    }
  }

  /**
   * This method is invoked on the engine thread to process the MIDI message.
   *
   * @param message Midi message
   */
  void dispatch(LXShortMessage message) {
    for (LXMidiListener listener : this.listeners) {
      message.dispatch(listener);
    }
  }

  final static String KEY_NAME = "name";
  private final static String KEY_CHANNEL = "channel";
  private final static String KEY_CONTROL = "control";
  private final static String KEY_SYNC = "sync";


  @Override
  public void save(LX lx, JsonObject object) {
    object.addProperty(KEY_NAME, getName());
    object.addProperty(KEY_CHANNEL, this.channelEnabled.isOn());
    object.addProperty(KEY_CONTROL, this.controlEnabled.isOn());
    object.addProperty(KEY_SYNC, this.syncEnabled.isOn());
  }

  @Override
  public void load(LX lx, JsonObject object) {
    LXSerializable.Utils.loadBoolean(this.channelEnabled, object, KEY_CHANNEL);
    LXSerializable.Utils.loadBoolean(this.controlEnabled, object, KEY_CONTROL);
    LXSerializable.Utils.loadBoolean(this.syncEnabled, object, KEY_SYNC);
  }

}
