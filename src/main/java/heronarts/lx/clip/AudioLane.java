package heronarts.lx.clip;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.parameter.BooleanParameter;

import java.io.File;

/**
 * Audio lane for playing back audio files on the composition.
 * Supported formats are determined by javax.sound.sampled.AudioSystem, including: WAV, AIFF
 */
public class AudioLane extends LXClipLane<AudioLaneEvent> {

  private static final int SAMPLE_RATE = 44100;

  public final Composition composition;

  public final BooleanParameter mute =
    new BooleanParameter("Mute")
    .setDescription("Mutes audio playback on this lane");

  // Active playback state, read by audio thread
  volatile AudioLaneEvent activeEvent = null;
  volatile long activeSampleOffset = 0;

  AudioLane(Composition composition) {
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

  // Playback

  @Override
  void initializeCursorPlayback(Cursor to) {
    setActiveEventAt(to);
  }

  @Override
  void playCursor(Cursor from, Cursor to, boolean inclusive) {
    if (this.activeEvent != null) {
      Cursor eventEnd = this.activeEvent.cursor.add(this.activeEvent.length);
      if (CursorOp().isAfter(to, eventEnd)) {
        // Current event has ended, find next event
        findNextEvent(eventEnd, to);
      }
      // Otherwise: audio thread is advancing the offset, nothing to do
    } else {
      // No active event, check if cursor has entered one
      findNextEvent(from, to);
    }
    // Explicitly not calling super
  }

  @Override
  void jumpCursor(Cursor from, Cursor to) {
    setActiveEventAt(to);
    this.composition.notifyAudioJump();
  }

  @Override
  void loopCursor(Cursor from, Cursor to) {
    setActiveEventAt(to);
    this.composition.notifyAudioJump();
  }

  /**
   * Find the event at the given cursor position and set it as active
   */
  private void setActiveEventAt(Cursor to) {
    this.activeEvent = null;
    AudioLaneEvent event = getPreviousEvent(to);
    if (event != null) {
      Cursor eventEnd = event.cursor.add(event.length);
      if (CursorOp().isBeforeOrEqual(to, eventEnd)) {
        double offsetMs = to.subtract(event.cursor).getMillis() + event.playbackOffset.getMillis();
        this.activeSampleOffset = toSampleOffsetFromMs(offsetMs);
        this.activeEvent = event;
      }
    }
  }

  /**
   * Scan forward for an event starting in range [from, to) that is still active at to
   */
  private void findNextEvent(Cursor from, Cursor to) {
    this.activeEvent = null;
    for (int i = cursorPlayIndex(from); i < this.events.size(); i++) {
      AudioLaneEvent event = this.events.get(i);
      if (CursorOp().isAfterOrEqual(event.cursor, to)) {
        break;
      }
      Cursor eventEnd = event.cursor.add(event.length);
      if (CursorOp().isAfterOrEqual(eventEnd, to)) {
        double offsetMs = to.subtract(event.cursor).getMillis() + event.playbackOffset.getMillis();
        this.activeSampleOffset = toSampleOffsetFromMs(offsetMs);
        this.activeEvent = event;
        return;
      }
    }
  }

  /**
   * Stop audio playback, clearing the active event
   */
  void stopPlayback() {
    this.activeEvent = null;
  }

  @Override
  void overdubCursor(Cursor from, Cursor to, boolean inclusive) {}

  /**
   * Convert milliseconds to a stereo interleaved sample offset
   */
  static long toSampleOffsetFromMs(double ms) {
    return (long) (ms * SAMPLE_RATE / 1000.0) * 2;
  }

  // Event management

  public AudioLane addEvent(File file) {
    AudioLaneEvent event = new AudioLaneEvent(this.lx, this, file);
    this.mutableEvents.add(event);
    this.onChange.bang();
    return this;
  }

  @Override
  protected AudioLaneEvent loadEvent(LX lx, JsonObject eventObj) {
    AudioLaneEvent event = new AudioLaneEvent(this.lx, this);
    event.load(lx, eventObj);
    return event;
  }

  @Override
  protected void onRemoveEvent(AudioLaneEvent event) {
    event.dispose();
  }

}
