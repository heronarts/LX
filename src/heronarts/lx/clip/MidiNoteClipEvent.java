package heronarts.lx.clip;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXChannel;
import heronarts.lx.midi.MidiNote;

public class MidiNoteClipEvent extends LXClipEvent {

  public final MidiNote midiNote;

  MidiNoteClipEvent(LXClipLane lane, MidiNote midiNote) {
    super(lane);
    this.midiNote = midiNote;
  }

  @Override
  public void execute() {
    ((LXChannel) lane.clip.bus).midiDispatch(this.midiNote);
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
