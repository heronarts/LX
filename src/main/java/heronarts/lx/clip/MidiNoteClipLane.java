package heronarts.lx.clip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOff;
import heronarts.lx.parameter.MutableParameter;

public class MidiNoteClipLane extends LXClipLane<MidiNoteClipEvent> {

  public final LXAbstractChannelClip clip;

  /**
   * Zoom is specified as multiple of lane height
   */
  public final MutableParameter uiZoom =
    new MutableParameter("UI Zoom", 4)
    .setDescription("Amount of UI zoom on the MIDI clip lane");

  public final MutableParameter uiOffset =
    new MutableParameter("UI Offset", -1)
    .setDescription("Scroll offset of MIDI piano roll");

  protected MidiNoteClipLane(LXAbstractChannelClip clip) {
    super(clip);
    this.clip = clip;
    addInternalParameter("uiZoom ", this.uiZoom);
    addInternalParameter("uiOffset", this.uiOffset);
  }

  @Override
  public String getPath() {
    return "MIDI";
  }

  @Override
  public String getLabel() {
    return "MIDI Note";
  }

  void playNote(MidiNoteClipEvent event) {
    final int pitch = event.midiNote.getPitch();
    MidiNoteClipEvent noteHeld = this.playbackNoteStack[pitch];
    if (event.isNoteOn()) {
      if (noteHeld != null) {
        LX.warning("Firing note-off for stacked playback note: " + event);
        try {
          this.clip.channel.midiDispatch(new MidiNoteOff(event.midiNote.getChannel(), pitch));
        } catch (InvalidMidiDataException imdx) {
          LX.error(imdx, "WTF invalid note-clone in MidiNoteClipLane.playNote");
        }
      }
      this.playbackNoteStack[pitch] = event;
      this.recordNoteStack[pitch] = event;
      this.clip.channel.midiDispatch(event.midiNote);
    } else {
      if (noteHeld != null) {
        final int heldChannel = noteHeld.midiNote.getChannel();
        MidiNote noteOff = event.midiNote;
        if (heldChannel != event.midiNote.getChannel()) {
          // Ensure the channel is correct
          try {
            noteOff = new MidiNoteOff(heldChannel, pitch);
            LX.warning("Adjusting note-off channel (" + heldChannel + ") for playback note-off: " + event.midiNote);
          } catch (InvalidMidiDataException imdx) {
            LX.error(imdx, "WTF invalid note-clone in MidiNoteClipLane.playNote");
          }
        }
        this.clip.channel.midiDispatch(noteOff);
      } else {
        LX.warning("Ignoring note-off for non-held note: " + event.midiNote);
      }
      this.playbackNoteStack[pitch] = null;
      this.recordNoteStack[pitch] = null;
    }
  }

  private void terminatePlaybackNotes() {
    for (MidiNoteClipEvent noteOn : this.playbackNoteStack) {
      if (noteOn != null) {
        try {
          this.clip.channel.midiDispatch(new MidiNoteOff(
            noteOn.midiNote.getChannel(),
            noteOn.midiNote.getPitch()
          ));
          LX.warning("Firing note-off for lingering note: " + noteOn.midiNote);
        } catch (InvalidMidiDataException imdx) {
          LX.error(imdx, "WTF, note clone has bad MIDI data");
        }
      }
    }
    Arrays.fill(this.playbackNoteStack, null);
  }

  private void terminateRecordingNotes(Cursor to) {
    // Recording is finished, add note-off messages for any hung notes
    this.mutableEvents.begin();

    boolean changed = false;
    for (MidiNoteClipEvent noteOn : this.recordInputStack) {
      if (noteOn != null) {
        try {
          MidiNoteClipEvent noteOff = new MidiNoteClipEvent(
            this,
            ShortMessage.NOTE_OFF,
            noteOn.midiNote.getChannel(),
            noteOn.midiNote.getPitch(),
            0
          );
          noteOff.setCursor(to);
          noteOn.setNoteOff(noteOff);
          int insertIndex = CursorOp().isEqual(noteOn.cursor, noteOff.cursor) ?
            cursorInsertIndex(noteOff.cursor) :
            cursorPlayIndex(noteOff.cursor);
          this.mutableEvents.add(insertIndex, noteOff);
          LX.warning("Recorded note-off for hung note on recording: " + noteOn);
          changed = true;
        } catch (InvalidMidiDataException imdx) {
          LX.error(imdx, "WTF, note clone has bad MIDI data");
        }
      }
    }
    Arrays.fill(this.recordNoteStack, null);
    Arrays.fill(this.recordInputStack, null);

    this.mutableEvents.commit();
    if (changed) {
      this.onChange.bang();
    }
  }

  void onStopPlayback() {
    terminatePlaybackNotes();
  }

  // All notes that the clip has actually triggered, whether due to timeline playback or input
  // which need to be cleared when playback stops
  private final MidiNoteClipEvent[] playbackNoteStack = new MidiNoteClipEvent[MidiNote.NUM_PITCHES];

  // Notes that new recording input may conflict with, but were not necessarily actually played,
  // e.g. notes that are on the clip timeline that began before the start playback point but
  // continue past the current cursor. These notes may need to be truncated if new recording
  // comes in that conflicts with them.
  private final MidiNoteClipEvent[] recordNoteStack = new MidiNoteClipEvent[MidiNote.NUM_PITCHES];

  // Notes that have been actually input by new recording. When recording stops or the loop finishes
  // these notes will need to be terminated.
  private final MidiNoteClipEvent[] recordInputStack = new MidiNoteClipEvent[MidiNote.NUM_PITCHES];

  private void initializeRecordNoteStack(Cursor to) {
    Arrays.fill(this.recordNoteStack, null);
    Arrays.fill(this.recordInputStack, null);

    // Process everything up to the initial playback point
    int index = cursorPlayIndex(to);
    for (int i = 0; i < index; ++i) {
      MidiNoteClipEvent noteEvent = this.events.get(i);
      this.recordNoteStack[noteEvent.midiNote.getPitch()] = noteEvent.midiNote.isNoteOn() ? noteEvent : null;
    }
  }

  void onStopRecording() {
    terminatePlaybackNotes();
    terminateRecordingNotes(this.clip.cursor);
  }

  @Override
  void initializeCursorPlayback(Cursor to) {
    Arrays.fill(this.playbackNoteStack, null);
    initializeRecordNoteStack(to);
  }

  @Override
  void jumpCursor(Cursor from, Cursor to) {
    // TODO(clips): this could be improved to compare the before/after cursor positions and figure
    // out if some notes were held across those positions, don't terminate them all, etc.
    terminatePlaybackNotes();
    terminateRecordingNotes(from);
    initializeCursorPlayback(to);
  }

  @Override
  void loopCursor(Cursor from, Cursor to) {
    jumpCursor(this.clip.loopEnd.cursor, to);
  }

  @Override
  void overdubCursor(Cursor from, Cursor to, boolean inclusive) {
    commitRecordQueue(false);
    playCursor(from, to, inclusive);
  }

  @Override
  MidiNoteClipLane commitRecordQueue(boolean notify) {
    // TODO(clips): handle all the mess here! check the state of the various
    // note stacks and insert/remove any manner of stuff that needs changing
    return this;
  }

  public MidiNoteClipLane removeNote(MidiNoteClipEvent note) {
    this.mutableEvents.begin();
    this.mutableEvents.remove(note);
    this.mutableEvents.remove(note.getNoteOff()); // no-op if null
    this.mutableEvents.commit();
    this.onChange.bang();
    return this;
  }

  @Override
  public boolean removeRange(Cursor from, Cursor to) {
    // Use a set here since we'll add note on/off pairs without
    // checking for redundancy
    List<MidiNoteClipEvent> toRemove = null;
    final Cursor.Operator CursorOp = CursorOp();
    for (MidiNoteClipEvent noteOn : this.events) {
      if (CursorOp.isAfter(noteOn.cursor, to)) {
        break;
      }
      if (noteOn.isNoteOn() && CursorOp.isBefore(noteOn.cursor, to)) {
        // Check that noteOff exists... it might be absent when we're actively
        // in recording mode and a note is being held. Don't delete it
        // from the range in that case (who is trying to delete as they record
        // anyways??)
        MidiNoteClipEvent noteOff = noteOn.getNoteOff();
        if ((noteOff != null) && CursorOp.isAfter(noteOff.cursor, from)) {
          // Ensure we remove note on and off pairs
          if (toRemove == null) {
            toRemove = new ArrayList<>();
          }
          toRemove.add(noteOn);
          toRemove.add(noteOff);
        }
      }
    }
    if (toRemove != null) {
      this.mutableEvents.removeAll(toRemove);
      this.onChange.bang();
      return true;
    }
    return false;
  }

  protected void recordNote(MidiNote note) {
    // Clip lanes disallow multiple notes stacked on the same pitch
    final int pitch = note.getPitch();
    final MidiNoteClipEvent recordNoteOn = this.recordNoteStack[pitch];
    if (note.isNoteOn()) {
      if (recordNoteOn != null) {
        // Terminate the previously held note with a note-off
        try {
          // NOTE: always note-off on the same channel as held note
          final int channel = recordNoteOn.midiNote.getChannel();
          MidiNoteClipEvent noteOff = new MidiNoteClipEvent(this, ShortMessage.NOTE_OFF, channel, pitch, 0);
          recordNoteOn.setNoteOff(noteOff);
          recordEvent(noteOff);
          this.recordNoteStack[pitch] = null;
          LX.warning("Terminated previous note for stacked note-on: " + recordNoteOn);
        } catch (InvalidMidiDataException imdx) {
          // Not possible with args from a valid event
          LX.error(imdx, "WTF, note clone has bad MIDI data");
        }
      }
      // Record the new note on
      MidiNoteClipEvent noteOn = new MidiNoteClipEvent(this, note.mutableCopy());
      this.recordNoteStack[pitch] = noteOn;
      this.recordInputStack[pitch] = noteOn;
      recordEvent(noteOn);
    } else {
      if (recordNoteOn != null) {
        // Only record note-off events if the note was actually held!
        // Enforce note-off being on the same channel as the note-on it succeeds
        final int heldChannel = recordNoteOn.midiNote.getChannel();
        MidiNote mutableOff = note.mutableCopy();
        if (mutableOff.getChannel() != heldChannel) {
          mutableOff.setChannel(heldChannel);
          LX.warning("Fixed MIDI channel (" + heldChannel + ") for note-off on held pitch: " + note);
        }
        MidiNoteClipEvent noteOff = new MidiNoteClipEvent(this, mutableOff);
        recordNoteOn.setNoteOff(noteOff);
        recordEvent(noteOff);
      }
      this.recordInputStack[pitch] = null;
    }
  }

  // When loading MIDI clip lane events, we keep track of note on/off pairs and
  private final MidiNoteClipEvent[] loadEventNoteStack = new MidiNoteClipEvent[MidiNote.NUM_PITCHES];

  @Override
  protected void beginLoadEvents(List<MidiNoteClipEvent> loadEvents) {
    Arrays.fill(this.loadEventNoteStack, null);
  }

  @Override
  protected MidiNoteClipEvent loadEvent(LX lx, JsonObject eventObj) {
    final int channel = eventObj.get(MidiNoteClipEvent.KEY_CHANNEL).getAsInt();
    final int command = eventObj.get(MidiNoteClipEvent.KEY_COMMAND).getAsInt();
    final int data1 = eventObj.get(MidiNoteClipEvent.KEY_DATA_1).getAsInt();
    final int data2 = eventObj.get(MidiNoteClipEvent.KEY_DATA_2).getAsInt();
    try {
      MidiNote midiNote = MidiNote.constructMutable(command, channel, data1, data2);
      final int pitch = midiNote.getPitch();
      MidiNoteClipEvent noteHeld = this.loadEventNoteStack[pitch];
      if (midiNote.isNoteOn()) {
        if (noteHeld == null) {
          return this.loadEventNoteStack[pitch] = new MidiNoteClipEvent(this, midiNote);
        } else {
          LX.error("Ignored stacked MIDI note in MIDI clip lane: " + eventObj);
        }
      } else {
        if (noteHeld != null) {
          if (channel != noteHeld.midiNote.getChannel()) {
            LX.error("Fixing note-off channel mismatch in MIDI clip lane: " + eventObj);
            midiNote.setChannel(noteHeld.midiNote.getChannel());
          }
          MidiNoteClipEvent noteOffEvent = new MidiNoteClipEvent(this, midiNote);
          noteHeld.setNoteOff(noteOffEvent);
          this.loadEventNoteStack[pitch] = null;
          return noteOffEvent;
        } else {
          LX.error("Ignored MIDI note off in MIDI clip lane with no note on: " + eventObj);
        }
      }
    } catch (InvalidMidiDataException imdx) {
      LX.error(imdx, "Invalid MIDI in clip event: " + channel + " " + command + " " + data1 + " " + data2);
    }
    return null;
  }

  @Override
  protected void endLoadEvents(List<MidiNoteClipEvent> loadEvents) {
    for (MidiNoteClipEvent noteOn : this.loadEventNoteStack) {
      if (noteOn != null) {
        try {
          MidiNoteClipEvent noteOff = new MidiNoteClipEvent(this, ShortMessage.NOTE_OFF, noteOn.midiNote.getChannel(), noteOn.midiNote.getPitch(), 0);
          noteOff.setCursor(this.clip.length.cursor);
          noteOn.setNoteOff(noteOff);
          loadEvents.add(noteOff);
          LX.error("Added note-off for hung MIDI note on in MIDI clip lane: " + noteOn);
        } catch (InvalidMidiDataException imdx) {
          // Won't ever happen, MIDI data known-valid from existing note
          LX.error(imdx, "Invalid MIDI in note-off clone: " + noteOn.midiNote);
        }
      }
    }
    Arrays.fill(this.loadEventNoteStack, null);
  }

  public boolean isClear(int pitch, Cursor from, Cursor to) {
    Cursor.Operator CursorOp = CursorOp();
    for (MidiNoteClipEvent noteOn : this.events) {
      if (CursorOp.isAfter(noteOn.cursor, to)) {
        return true;
      }
      if (noteOn.isNoteOn() && (noteOn.midiNote.getPitch() == pitch)) {
        MidiNoteClipEvent noteOff = noteOn.getNoteOff();
        if (noteOff == null) {
          return false;
        }
        if (CursorOp.isAfter(noteOff.cursor, from)) {
          return false;
        }
      }
    }
    return true;
  }

  private void _insertNote(MidiNoteClipEvent noteOn, MidiNoteClipEvent noteOff) {
    // Insert properly, note on goes *after* last note-off of the same note
    int insertIndex = cursorInsertIndex(noteOn.cursor);
    this.mutableEvents.add(insertIndex, noteOn);
    if (CursorOp().isEqual(noteOn.cursor, noteOff.cursor)) {
      // Zero-length notes allowed! Ensure note on/off are sequential adjacent
      this.mutableEvents.add(insertIndex + 1, noteOff);
    } else {
      // Ensure the note off goes *before* any subsequent noteOn of the same pitch
      this.mutableEvents.add(cursorPlayIndex(noteOff.cursor), noteOff);
    }
  }

  public MidiNoteClipEvent insertNote(int pitch, int velocity, Cursor from, Cursor to) {
    if (!isClear(pitch, from, to)) {
      return null;
    }
    try {
      MidiNoteClipEvent noteOn = new MidiNoteClipEvent(this, ShortMessage.NOTE_ON, 0, pitch, velocity);
      MidiNoteClipEvent noteOff = new MidiNoteClipEvent(this, ShortMessage.NOTE_OFF, 0, pitch, 0);
      noteOn.setNoteOff(noteOff);
      noteOn.setCursor(from);
      noteOff.setCursor(to);

      this.mutableEvents.begin();
      _insertNote(noteOn, noteOff);
      this.mutableEvents.commit();
      this.onChange.bang();

      return noteOn;
    } catch (InvalidMidiDataException imdx) {
      LX.error(imdx, "WTF, note clone has bad MIDI data");
      return null;
    }
  }

  public void editNote(MidiNoteClipEvent editNoteOn, int toPitch, int toVelocity, Cursor toStart, Cursor toEnd, List<MidiNoteClipEvent> restoreOriginal, boolean checkDelete, boolean cursorMoved) {
    Cursor.Operator CursorOp = CursorOp();

    this.mutableEvents.begin();

    // The edit may have caused deletions or re-orderings, first put the array back how it was
    this.mutableEvents.set(restoreOriginal);

    if (checkDelete) {
      // Delete stuff that conflicts with the new location!
      List<MidiNoteClipEvent> toRemove = null;
      for (MidiNoteClipEvent noteOn : this.events) {
        if (noteOn.isNoteOff() || (noteOn == editNoteOn)) {
          // Skip note-off messages or note being edited
          continue;
        }

        // Notes after toEnd can't possibly overlap
        if (CursorOp.isAfter(noteOn.cursor, toEnd)) {
          break;
        }

        // Find all notes that overlap with the target move in some way
        if ((noteOn.midiNote.getPitch() == toPitch) &&
            CursorOp.isBefore(noteOn.cursor, toEnd)) {

          // Check for existence of noteOff. It might not exist if we're actively in recording
          // mode. Trying to drag some other note over an actively-recording note?? Unclear how
          // this is going to play out...
          MidiNoteClipEvent noteOff = noteOn.getNoteOff();
          if ((noteOff != null) && CursorOp.isAfter(noteOn.getNoteOff().cursor, toStart)) {
            if (toRemove == null) {
              toRemove = new ArrayList<>();
            }
            toRemove.add(noteOn);
            toRemove.add(noteOff);
          }
        }
      }
      if (toRemove != null) {
        this.mutableEvents.removeAll(toRemove);
      }
    }

    // Update velocity
    editNoteOn.midiNote.setVelocity(toVelocity);

    // Update pitch
    MidiNoteClipEvent editNoteOff = editNoteOn.getNoteOff();
    editNoteOn.midiNote.setPitch(toPitch);
    editNoteOff.midiNote.setPitch(toPitch);

    // Move position
    editNoteOn.cursor.set(toStart);
    editNoteOff.cursor.set(toEnd);
    if (cursorMoved) {
      // Need to ensure proper ordering if cursors were modified from original
      this.mutableEvents.remove(editNoteOn);
      this.mutableEvents.remove(editNoteOff);
      _insertNote(editNoteOn, editNoteOff);
    }

    this.mutableEvents.commit();
    this.onChange.bang();
  }

}
