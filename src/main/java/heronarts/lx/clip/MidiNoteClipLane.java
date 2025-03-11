package heronarts.lx.clip;

import java.util.Arrays;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Set;

import javax.sound.midi.InvalidMidiDataException;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOff;
import heronarts.lx.parameter.MutableParameter;

public class MidiNoteClipLane extends LXClipLane<MidiNoteClipEvent> {

  /**
   * Zoom is specified as multiple of lane height
   */
  public final MutableParameter uiZoom =
    new MutableParameter("UI Zoom", 4)
    .setDescription("Amount of UI zoom on the MIDI clip lane");

  public final MutableParameter uiOffset =
    new MutableParameter("UI Offset", -1)
    .setDescription("Scroll offset of MIDI piano roll");

  protected MidiNoteClipLane(LXClip clip) {
    super(clip);
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

  private final MidiNoteClipEvent[] noteStack = new MidiNoteClipEvent[MidiNote.NUM_PITCHES];

  private void resetNoteStack(Cursor to) {
    Arrays.fill(this.noteStack, null);
    int index = cursorInsertIndex(to);
    for (int i = 0; i < index; ++i) {
      MidiNoteClipEvent noteEvent = this.events.get(i);
      this.noteStack[noteEvent.midiNote.getPitch()] =
        noteEvent.midiNote.isNoteOn() ? noteEvent : null;
    }
  }

  @Override
  void initializeCursor(Cursor to) {
    resetNoteStack(to);
  }

  @Override
  void loopCursor(Cursor to) {
    resetNoteStack(to);
  }

  public MidiNoteClipLane removeNote(MidiNoteClipEvent note) {
    this.mutableEvents.begin();
    this.mutableEvents.remove(note);
    this.mutableEvents.remove(note.getNoteOff());
    this.mutableEvents.commit();
    this.onChange.bang();
    return this;
  }

  @Override
  public boolean removeRange(Cursor from, Cursor to) {
    // Use a set here since we'll add note on/off pairs without
    // checking for redundancy
    final Set<MidiNoteClipEvent> toRemove = new HashSet<>();
    final ListIterator<MidiNoteClipEvent> iter = eventIterator(from);
    final Cursor.Operator CursorOp = CursorOp();
    while (iter.hasNext()) {
      MidiNoteClipEvent event = iter.next();
      if (CursorOp.isAfter(event.cursor, to)) {
        break;
      }
      // Ensure we remove note on and off pairs
      toRemove.add(event);
      toRemove.add(event.partner);
    }

    if (!toRemove.isEmpty()) {
      this.mutableEvents.removeAll(toRemove);
      this.onChange.bang();
      return true;
    }
    return false;
  }

  protected void recordNote(MidiNote note) {
    // Clip lanes disallow multiple notes stacked on the same pitch
    final int pitch = note.getPitch();
    final MidiNoteClipEvent existingNoteOn = this.noteStack[pitch];
    if (note.isNoteOn()) {
      if (existingNoteOn != null) {
        // Terminate the previously held note with a note-off
        try {
          MidiNoteClipEvent noteOff = new MidiNoteClipEvent(this, new MidiNoteOff(note.getChannel(), pitch));
          existingNoteOn.setNoteOff(noteOff);
          recordEvent(noteOff);
          this.noteStack[pitch] = null;
        } catch (InvalidMidiDataException imdx) {
          // Not possible with args from a valid event
        }
      }
      // Record the new note on
      recordEvent(this.noteStack[pitch] = new MidiNoteClipEvent(this, note.mutableCopy()));
    } else {
      if (existingNoteOn != null) {
        // Only record note-off events if the note was actually held!
        MidiNoteClipEvent noteOff = new MidiNoteClipEvent(this, note.mutableCopy());
        existingNoteOn.setNoteOff(noteOff);
        recordEvent(noteOff);
      }
      this.noteStack[pitch] = null;
    }
  }

  private final MidiNoteClipEvent[] loadStack = new MidiNoteClipEvent[MidiNote.NUM_PITCHES];

  @Override
  protected void beginLoadEvents() {
    Arrays.fill(this.loadStack, null);
  }

  @Override
  protected void endLoadEvents() {
    Arrays.fill(this.loadStack, null);
  }

  @Override
  protected MidiNoteClipEvent loadEvent(LX lx, JsonObject eventObj) {
    int channel = eventObj.get(MidiNoteClipEvent.KEY_CHANNEL).getAsInt();
    int command = eventObj.get(MidiNoteClipEvent.KEY_COMMAND).getAsInt();
    int data1 = eventObj.get(MidiNoteClipEvent.KEY_DATA_1).getAsInt();
    int data2 = eventObj.get(MidiNoteClipEvent.KEY_DATA_2).getAsInt();
    try {
      MidiNote midiNote = MidiNote.constructMutable(command, channel, data1, data2);
      int pitch = midiNote.getPitch();

      MidiNoteClipEvent noteHeld = this.loadStack[pitch];
      if (midiNote.isNoteOn()) {
        if (noteHeld == null) {
          MidiNoteClipEvent noteOnEvent = new MidiNoteClipEvent(this, midiNote);
          this.loadStack[pitch] = noteOnEvent;
          return noteOnEvent;
        } else {
          LX.error("Ignoring stacked MIDI note in MIDI clip lane: " + eventObj);
        }
      } else {
        if (noteHeld != null) {
          MidiNoteClipEvent noteOffEvent = new MidiNoteClipEvent(this, midiNote);
          noteHeld.setNoteOff(noteOffEvent);
          this.loadStack[pitch] = null;
          return noteOffEvent;
        } else {
          LX.error("Ignoring MIDI note off in MIDI clip lane with no note on: " + eventObj);
        }
      }
    } catch (InvalidMidiDataException imdx) {
      LX.error(imdx, "Invalid MIDI in clip event: " + channel + " " + command + " " + data1 + " " + data2);
    }
    return null;
  }

}
