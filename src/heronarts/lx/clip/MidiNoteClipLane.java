package heronarts.lx.clip;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.midi.LXShortMessage;
import heronarts.lx.midi.MidiNote;

public class MidiNoteClipLane extends LXClipLane {

  protected MidiNoteClipLane(LXClip clip) {
    super(clip);
  }

  @Override
  public String getLabel() {
    return "MIDI Note";
  }

  @Override
  protected LXClipEvent loadEvent(LX lx, JsonObject eventObj) {
    int channel = eventObj.get(MidiNoteClipEvent.KEY_CHANNEL).getAsInt();
    int command = eventObj.get(MidiNoteClipEvent.KEY_COMMAND).getAsInt();
    int data1 = eventObj.get(MidiNoteClipEvent.KEY_DATA_1).getAsInt();
    int data2 = eventObj.get(MidiNoteClipEvent.KEY_DATA_2).getAsInt();
    try {
      ShortMessage sm = new ShortMessage();
      sm.setMessage(command, channel, data1, data2);
      return new MidiNoteClipEvent(this, (MidiNote) LXShortMessage.fromShortMessage(sm));
    } catch (InvalidMidiDataException imdx) {
      System.err.println("Invalid MIDI in Clip: " + channel + " " + command + " " + data1 + " " + data2);
    }
    return null;
  }

  protected void appendNote(MidiNote note) {
    super.appendEvent(new MidiNoteClipEvent(this, note));
  }

}
