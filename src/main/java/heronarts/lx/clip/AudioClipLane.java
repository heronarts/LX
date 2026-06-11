/**
 * Copyright 2025- Justin K. Belcher, Heron Arts LLC
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
 * @author Justin K. Belcher <justin@jkb.studio>
 */

package heronarts.lx.clip;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.audio.LXAudioTimeline;
import heronarts.lx.parameter.BooleanParameter;

import java.io.File;

/**
 * Audio lane for playing back audio files on the composition.
 * Supported formats are determined by javax.sound.sampled.AudioSystem, including: WAV, AIFF
 */
public class AudioClipLane extends LXClipLane<AudioClipEvent> {

  public final LXComposition composition;

  public final BooleanParameter mute =
    new BooleanParameter("Mute")
    .setDescription("Mutes audio playback on this lane");

  // Active playback state, read by audio thread via LXAudioTimeline
  private volatile AudioClipEvent activeEvent = null;
  private volatile int activeSampleOffset = 0;

  AudioClipLane(LXComposition composition) {
    super(composition);
    this.composition = composition;
    this.label.setValue("Audio");
    addParameter("mute", this.mute);
  }

  @Override
  public String getLabel() {
    return this.label.getString();
  }

  @Override
  public String getPath() {
    return "audioLane/" + getIndex();
  }

  public AudioClipEvent getActiveEvent() {
    return this.activeEvent;
  }

  public void clearActiveEvent() {
    this.activeEvent = null;
    this.activeSampleOffset = 0;
  }

  public int getActiveSampleOffset() {
    return this.activeSampleOffset;
  }

  public void setActiveSampleOffset(int offset) {
    this.activeSampleOffset = offset;
  }

  // Playback

  @Override
  void initializeCursorPlayback(Cursor to) {
    setActiveEventAt(to);
  }

  @Override
  void playCursor(Cursor from, Cursor to, boolean inclusive) {
    if (this.activeEvent != null) {
      if (CursorOp().isAfter(to, this.activeEvent.end)) {
        // Current event has ended, find next event
        findNextEvent(this.activeEvent.end, to);
      }
      // Otherwise: audio thread will carry on advancing the offset, nothing to do
    } else {
      // No active event, check if cursor has entered one
      findNextEvent(from, to);
    }
    // Explicitly not calling super
  }

  @Override
  void jumpCursor(Cursor from, Cursor to) {
    setActiveEventAt(to);
  }

  @Override
  void loopCursor(Cursor from, Cursor to) {
    setActiveEventAt(to);
  }

  /**
   * Find the event at the given cursor position and set it as active
   */
  private void setActiveEventAt(Cursor to) {
    clearActiveEvent();
    final AudioClipEvent event = getPreviousEvent(to);
    if ((event != null) && CursorOp().isBeforeOrEqual(to, event.end)) {
      double offsetMs = to.subtract(event.cursor).getMillis() + event.playbackOffset.getMillis();
      this.activeSampleOffset = toSampleOffsetFromMs(offsetMs);
      this.activeEvent = event;
    }

  }

  /**
   * Scan forward for an event starting in range [from, to) that is still active at to
   */
  private void findNextEvent(Cursor from, Cursor to) {
    clearActiveEvent();
    for (int i = cursorPlayIndex(from); i < this.events.size(); i++) {
      AudioClipEvent event = this.events.get(i);
      if (CursorOp().isAfterOrEqual(event.cursor, to)) {
        // Next event starts after the cursor, got nothing
        return;
      }
      if (CursorOp().isAfterOrEqual(event.end, to)) {
        double offsetMs = to.subtract(event.cursor).getMillis() + event.playbackOffset.getMillis();
        this.activeSampleOffset = toSampleOffsetFromMs(offsetMs);
        this.activeEvent = event;
        return;
      }
    }

  }

  @Override
  void overdubCursor(Cursor from, Cursor to, boolean inclusive) {}

  /**
   * Convert milliseconds to a stereo interleaved sample offset
   */
  static int toSampleOffsetFromMs(double ms) {
    return LXAudioTimeline.AUDIO_OUTPUT_FORMAT.getChannels() * (int) (ms * LXAudioTimeline.AUDIO_OUTPUT_FORMAT.getSampleRate() / 1000.0);
  }

  // Event management

  public AudioClipLane addEvent(File file) {
    this.mutableEvents.add(new AudioClipEvent(this.lx, this, file));
    this.onChange.bang();
    return this;
  }

  @Override
  protected AudioClipEvent loadEvent(LX lx, JsonObject eventObj) {
    final AudioClipEvent event = new AudioClipEvent(this.lx, this);
    event.load(lx, eventObj);
    return event;
  }

  @Override
  protected void onRemoveEvent(AudioClipEvent event) {
    event.dispose();
  }

}
