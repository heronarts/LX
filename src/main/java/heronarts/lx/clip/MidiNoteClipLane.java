package heronarts.lx.clip;

import java.util.Arrays;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.midi.LXShortMessage;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.MidiNoteOff;

public class MidiNoteClipLane extends LXClipLane<MidiNoteClipEvent> {

  protected MidiNoteClipLane(LXClip clip) {
    super(clip);
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

  protected void recordNote(MidiNote note) {
    // Clip lanes disallow multiple notes stacked on the same pitch
    final int pitch = note.getPitch();
    final MidiNoteClipEvent noteOn = this.noteStack[pitch];
    if (note.isNoteOn()) {
      if (noteOn != null) {
        // Terminate the previously held note with a note-off
        try {
          MidiNoteOff noteOff = new MidiNoteOff(note.getChannel(), pitch);
          MidiNoteClipEvent noteOffEvent = new MidiNoteClipEvent(this, noteOff);
          noteOn.setNoteOff(noteOffEvent);
          recordEvent(noteOffEvent);
          this.noteStack[pitch] = null;
        } catch (InvalidMidiDataException imdx) {
          // Not possible with args from a valid event
        }
      }
      // Record the new note on
      recordEvent(this.noteStack[pitch] = new MidiNoteClipEvent(this, note));
    } else {
      if (noteOn != null) {
        // Only record note-off events if the note was actually held!
        MidiNoteClipEvent noteOffEvent = new MidiNoteClipEvent(this, note);
        noteOn.setNoteOff(noteOffEvent);
        recordEvent(noteOffEvent);
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
      ShortMessage sm = new ShortMessage();
      sm.setMessage(command, channel, data1, data2);
      MidiNote midiNote = (MidiNote) LXShortMessage.fromShortMessage(sm);
      int pitch = midiNote.getPitch();

      MidiNoteClipEvent noteHeld = this.loadStack[pitch];
      if (midiNote.isNoteOn()) {
        if (noteHeld == null) {
          MidiNoteClipEvent noteOnEvent = new MidiNoteClipEvent(this, (MidiNote) LXShortMessage.fromShortMessage(sm));
          this.loadStack[pitch] = noteOnEvent;
          return noteOnEvent;
        } else {
          LX.error("Ignoring stacked MIDI note in MIDI clip lane: " + eventObj);
        }
      } else {
        if (noteHeld != null) {
          MidiNoteClipEvent noteOffEvent = new MidiNoteClipEvent(this, (MidiNote) LXShortMessage.fromShortMessage(sm));
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
