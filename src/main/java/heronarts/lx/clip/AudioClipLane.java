package heronarts.lx.clip;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.parameter.BooleanParameter;

import java.io.File;

/**
 * Audio lane for playing back audio files on the composition.
 * Supported formats are determined by javax.sound.sampled.AudioSystem, including: WAV, AIFF
 */
public class AudioClipLane extends LXClipLane<AudioClipEvent> {

  private static final int SAMPLE_RATE = 44100;

  public final LXComposition composition;

  public final BooleanParameter mute =
    new BooleanParameter("Mute")
    .setDescription("Mutes audio playback on this lane");

  // Active playback state, read by audio thread
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
  }

  @Override
  void loopCursor(Cursor from, Cursor to) {
    setActiveEventAt(to);
  }

  /**
   * Find the event at the given cursor position and set it as active
   */
  private void setActiveEventAt(Cursor to) {
    this.activeEvent = null;
    AudioClipEvent event = getPreviousEvent(to);
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
      AudioClipEvent event = this.events.get(i);
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

  @Override
  void overdubCursor(Cursor from, Cursor to, boolean inclusive) {}

  /**
   * Convert milliseconds to a stereo interleaved sample offset
   */
  static int toSampleOffsetFromMs(double ms) {
    return (int) (ms * SAMPLE_RATE / 1000.0) * 2;
  }

  // Event management

  public AudioClipLane addEvent(File file) {
    AudioClipEvent event = new AudioClipEvent(this.lx, this, file);
    this.mutableEvents.add(event);
    this.onChange.bang();
    return this;
  }

  @Override
  protected AudioClipEvent loadEvent(LX lx, JsonObject eventObj) {
    AudioClipEvent event = new AudioClipEvent(this.lx, this);
    event.load(lx, eventObj);
    return event;
  }

  @Override
  protected void onRemoveEvent(AudioClipEvent event) {
    event.dispose();
  }

}
