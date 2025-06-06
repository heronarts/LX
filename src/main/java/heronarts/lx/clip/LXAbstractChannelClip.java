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

package heronarts.lx.clip;

import heronarts.lx.LX;
import heronarts.lx.midi.LXShortMessage;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.mixer.LXAbstractChannel;

public abstract class LXAbstractChannelClip extends LXClip implements LXAbstractChannel.MidiListener {

  public final LXAbstractChannel channel;
  public final MidiNoteClipLane midiNoteLane = new MidiNoteClipLane(this);

  protected LXAbstractChannelClip(LX lx, LXAbstractChannel channel, int index, boolean registerListener) {
    super(lx, channel, index, registerListener);
    this.channel = channel;
    this.mutableLanes.add(this.midiNoteLane);
    registerParameter(channel.fader);
    registerParameter(channel.enabled);
    channel.addMidiListener(this);
  }

  @Override
  protected void onStopPlayback() {
    super.onStopPlayback();
    this.midiNoteLane.onStopPlayback();
  }

  @Override
  protected void onStopRecording() {
    super.onStopRecording();
    this.midiNoteLane.onStopRecording();
  }

  @Override
  public void midiReceived(LXAbstractChannel channel, LXShortMessage message) {
    if (message instanceof MidiNote note) {
      if (isRecording()) {
        this.midiNoteLane.recordNote(note);
      }
    }
  }

  @Override
  public void dispose() {
    unregisterParameter(this.channel.fader);
    unregisterParameter(this.channel.enabled);
    this.channel.removeMidiListener(this);
    super.dispose();
  }

}
