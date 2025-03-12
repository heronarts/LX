package heronarts.lx.clip;

import javax.sound.midi.InvalidMidiDataException;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.mixer.LXAbstractChannel;

public class MidiNoteClipEvent extends LXClipEvent<MidiNoteClipEvent> {

  public final MidiNote midiNote;
  private MidiNoteClipEvent partner;

  MidiNoteClipEvent(MidiNoteClipLane lane, int command, int channel, int data1, int data2) throws InvalidMidiDataException {
    this(lane, MidiNote.constructMutable(command, channel, data1, data2));
  }

  MidiNoteClipEvent(MidiNoteClipLane lane, MidiNote midiNote) {
    super(lane);
    this.midiNote = midiNote;
  }

  public boolean isNoteOn() {
    return this.midiNote.isNoteOn();
  }

  public boolean isNoteOff() {
    return this.midiNote.isNoteOff();
  }

  void setNoteOff(MidiNoteClipEvent noteOff) {
    if (!this.isNoteOn()) {
      throw new IllegalStateException("Cannot setNoteOff() on a MIDI event that isn't note-on: " + this);
    }
    if (noteOff.isNoteOn()) {
      throw new IllegalStateException("Cannot setNoteOff() to a MIDI event that isn't note-off: " + noteOff);
    }
    if (noteOff.partner != null) {
      throw new IllegalStateException("Cannot setNoteOff() to a MIDI event already with a partner: " + this + " -> " + noteOff);
    }
    this.partner = noteOff;
    noteOff.partner = this;
  }

  public MidiNoteClipEvent getNoteOff() {
    if (!isNoteOn()) {
      throw new UnsupportedOperationException("Can only getNoteOff() for a note on event");
    }
    return this.partner;
  }

  public MidiNoteClipEvent getNoteOn() {
    if (isNoteOn()) {
      throw new UnsupportedOperationException("Can only getNoteOn() for a note off event");
    }
    return this.partner;
  }

  @Override
  public void execute() {
    ((LXAbstractChannel) lane.clip.bus).midiDispatch(this.midiNote);
  }

  protected final static String KEY_CHANNEL = "channel";
  protected final static String KEY_COMMAND = "command";
  protected final static String KEY_DATA_1 = "data1";
  protected final static String KEY_DATA_2 = "data2";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_CHANNEL, this.midiNote.getChannel());
    obj.addProperty(KEY_COMMAND, this.midiNote.getCommand());
    obj.addProperty(KEY_DATA_1, this.midiNote.getData1());
    obj.addProperty(KEY_DATA_2, this.midiNote.getData2());
  }
}
