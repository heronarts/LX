package heronarts.lx.clip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    return "MIDI";
  }

  private void dispatchNote(MidiNote note) {
    this.clip.channel.midiDispatch(note);
  }

  void playNote(MidiNoteClipEvent note) {
    if (note.isNoteOn() ) {
      playNoteOn(note);
    } else {
      playNoteOff(note);
    }
  }

  private void playNoteOn(MidiNoteClipEvent noteOn) {
    final int pitch = noteOn.midiNote.getPitch();
    final MidiNoteClipEvent noteHeld = this.playNoteStack[pitch];
    if (noteHeld != null) {
      debug("Firing note-off for stacked playback note: " + noteOn);
      try {
        this.clip.channel.midiDispatch(new MidiNoteOff(noteOn.midiNote.getChannel(), pitch));
      } catch (InvalidMidiDataException imdx) {
        LX.error(imdx, "WTF invalid note-clone in MidiNoteClipLane.playNote");
      }
    }
    // Dispatch the note-on to the channel
    this.playNoteStack[pitch] = noteOn;
    this.recordNoteStack[pitch] = noteOn;
    dispatchNote(noteOn.midiNote);
  }

  private void playNoteOff(MidiNoteClipEvent noteOff) {
    final int pitch = noteOff.midiNote.getPitch();
    final MidiNoteClipEvent noteHeld = this.playNoteStack[pitch];
    if (noteHeld == null) {
      debug("Ignoring note-off for non-held note: " + noteOff.midiNote);
    } else {
      final int heldChannel = noteHeld.midiNote.getChannel();
      MidiNote dispatch = noteOff.midiNote;
      if (heldChannel != dispatch.getChannel()) {
        try {
          dispatch = new MidiNoteOff(heldChannel, pitch);
          debug("Adjusted note-off channel (" + heldChannel + ") for playback note-off: " + noteOff.midiNote);
        } catch (InvalidMidiDataException imdx) {
          LX.error(imdx, "WTF invalid note-clone in MidiNoteClipLane.playNote");
        }
      }
      dispatchNote(dispatch);
    }

    // Clear state
    this.playNoteStack[pitch] = null;
    this.recordNoteStack[pitch] = null;
  }

  private void terminatePlaybackNotes() {
    for (MidiNoteClipEvent noteOn : this.playNoteStack) {
      if (noteOn != null) {
        try {
          dispatchNote(new MidiNoteOff(
            noteOn.midiNote.getChannel(),
            noteOn.midiNote.getPitch()
          ));
          debug("Firing note-off for lingering note: " + noteOn.midiNote);
        } catch (InvalidMidiDataException imdx) {
          LX.error(imdx, "WTF, note clone has bad MIDI data");
        }
      }
    }
    Arrays.fill(this.playNoteStack, null);
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
          debug("Recorded note-off for hung note on recording: " + noteOn);
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
  private final MidiNoteClipEvent[] playNoteStack = new MidiNoteClipEvent[MidiNote.NUM_PITCHES];

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
    Arrays.fill(this.playNoteStack, null);
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

  private final Set<MidiNoteClipEvent> overdubEvents = new HashSet<>();
  private boolean inOverdub = false;

  @Override
  void overdubCursor(Cursor from, Cursor to, boolean inclusive) {
    // Commit the new stuff, which may remove things and resolve
    // note-stack conflicts!
    this.inOverdub = true;

    boolean changed = false;
    this.mutableEvents.begin();

    if (!this.recordQueue.isEmpty()) {
      commitRecordQueue(false);
      changed = true;
    }

    final Cursor.Operator CursorOp = CursorOp();
    final int limit = inclusive ? 0 : -1;
    for (int index = cursorPlayIndex(from); index < this.events.size(); ++index) {
      final MidiNoteClipEvent note = this.events.get(index);
      if (CursorOp.compare(note.cursor, to) > limit) {
        break;
      }

      // Don't trigger events that were created/inserted by the overdub action
      if (!this.overdubEvents.contains(note)) {
        // We're curent recording this note and need to clobber a future one!
        if (note.isNoteOn() && (this.recordInputStack[note.midiNote.getPitch()] != null)) {
          this.mutableEvents.remove(index);
          this.mutableEvents.remove(note.getNoteOff()); // strictly ahead of the noteOn
          --index;
          changed = true;
        } else {
          playNote(note);
        }
      }
    }

    this.overdubEvents.clear();
    this.inOverdub = false;

    this.mutableEvents.commit();
    if (changed) {
      this.onChange.bang();
    }
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
    recordEvent(new MidiNoteClipEvent(this, note.mutableCopy()));
  }

  @Override
  MidiNoteClipLane commitRecordQueue(boolean notify) {
    if (!this.recordQueue.isEmpty()) {
      this.mutableEvents.begin();
      this.recordQueue.forEach(note -> _recordNote(note));
      this.recordQueue.clear();
      this.mutableEvents.commit();
      if (notify) {
        this.onChange.bang();
      }
    }
    return this;
  }

  private void _recordNote(MidiNoteClipEvent note) {
    if (note.isNoteOn()) {
      _recordNoteOn(note);
    } else {
      _recordNoteOff(note);
    }
  }

  private void _recordNoteOn(MidiNoteClipEvent noteOn) {
    // Clip lanes disallow multiple notes stacked on the same pitch
    final int pitch = noteOn.midiNote.getPitch();
    final MidiNoteClipEvent recordNoteOn = this.recordNoteStack[pitch];
    if (recordNoteOn != null) {
      // Terminate the previously held note with a note-off
      final int noteOffChannel = recordNoteOn.midiNote.getChannel();
      try {
        // NOTE: always note-off on the same channel as held note
        MidiNoteClipEvent noteOff = recordNoteOn.getNoteOff();
        if (noteOff != null) {
          // Move the existing note-off to this point
          debug("Truncating earlier note: " + recordNoteOn);
          this.mutableEvents.remove(noteOff);
          noteOff.setCursor(noteOn.cursor);
          noteOff.midiNote.setChannel(noteOffChannel); // should always match already...
        } else {
          debug("Adding missing note-off: " + recordNoteOn);
          noteOff = new MidiNoteClipEvent(this, ShortMessage.NOTE_OFF, noteOffChannel, pitch, 0);
          recordNoteOn.setNoteOff(noteOff);
        }
        _insertNoteOff(noteOff);
        if (this.inOverdub) {
          this.overdubEvents.add(noteOff);
        }

        // The clip engine had actually played this note, we need to note-off it
        if (this.playNoteStack[pitch] != null) {
          playNote(noteOff);
        }

        // Clear playback stacks on this pitch
        this.recordNoteStack[pitch] = null;
        this.playNoteStack[pitch] = null;

      } catch (InvalidMidiDataException imdx) {
        // Not possible with args from a valid event
        LX.error(imdx, "WTF, note clone has bad MIDI data");
      }
    }

    // Insert the new note on
    this.playNoteStack[pitch] = noteOn;
    this.recordNoteStack[pitch] = noteOn;
    this.recordInputStack[pitch] = noteOn;
    _insertEvent(noteOn);
    if (this.inOverdub) {
      this.overdubEvents.add(noteOn);
    }
  }

  private void _recordNoteOff(MidiNoteClipEvent noteOff) {
    final int pitch = noteOff.midiNote.getPitch();

    // Note-off
    if (this.recordInputStack[pitch] == null) {
      // IGNORE THIS! We didn't actually input this
      // note-on during the recording session. This would
      // typically happen if we went all the way around
      // an overdub loop recording boundary and the note-off happens
      // after the recording input was cleared. Just skip
      // this completely.
      return;
    }

    // It's a note-off, but is it redundant??
    final MidiNoteClipEvent recordNoteOn = this.recordNoteStack[pitch];
    if (recordNoteOn == null) {
      debug("Ignoring Note-off that had no counterpart");
    } else {
      // Only record note-off events if the note was actually held!
      // Enforce note-off being on the same channel as the note-on it succeeds
      final int noteOffChannel = recordNoteOn.midiNote.getChannel();
      MidiNoteClipEvent existingNoteOff = recordNoteOn.getNoteOff();
      if (existingNoteOff != null) {
        debug("Moving already-existing note-off: " + recordNoteOn);
        this.mutableEvents.remove(existingNoteOff);
        existingNoteOff.midiNote.setChannel(noteOffChannel); // should always match already...
        existingNoteOff.setCursor(noteOff.cursor);
        noteOff = existingNoteOff;
      } else {
        if (noteOff.midiNote.getChannel() != noteOffChannel) {
          noteOff.midiNote.setChannel(noteOffChannel);
          debug("Fixed MIDI channel (" + noteOffChannel + ") for note-off on held pitch: " + noteOff);
        }
        recordNoteOn.setNoteOff(noteOff);
      }
      _insertNoteOff(noteOff);
      if (this.inOverdub) {
        this.overdubEvents.add(noteOff);
      }
    }

    // Clear state
    this.playNoteStack[pitch] = null;
    this.recordNoteStack[pitch] = null;
    this.recordInputStack[pitch] = null;
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

  private int _insertNoteOff(MidiNoteClipEvent noteOff) {
    if (noteOff.getNoteOn() == null) {
      LX.error(new Exception("Note off with no note-on"));
    }

    MidiNoteClipEvent noteOn = noteOff.getNoteOn();
    int index = -1;
    if ((noteOn != null) && CursorOp().isEqual(noteOn.cursor, noteOff.cursor)) {
      index = cursorInsertIndex(noteOn.cursor) + 1;
    } else {
      index = cursorPlayIndex(noteOff.cursor);
    }
    this.mutableEvents.add(index, noteOff);
    return index;
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

  private static void debug(String str) {
    LX.debug(str);
  }

}
