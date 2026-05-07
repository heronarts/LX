package heronarts.lx.clip;

import com.google.gson.JsonObject;
import heronarts.lx.LX;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.StringParameter;

/**
 * A composition event containing a text note
 */
public class NotesLaneEvent extends LXCompositionEvent<NotesLaneEvent> {

  public final NotesLane lane;

  public final StringParameter note =
    new StringParameter("Note")
    .setDescription("Contents of the note");

  private final LXParameterListener noteListener;

  NotesLaneEvent(LX lx, NotesLane lane) {
    this(lx, lane, lane.clip.cursor, null);
  }

  NotesLaneEvent(LX lx, NotesLane lane, Cursor cursor, Cursor length) {
    super(lane, cursor);
    this.lane = lane;

    if (length != null) {
      this.length.set(length);
    }
    refreshEnd();

    this.note.addListener(this.noteListener = (p) -> { this.lane.onChange.bang(); });
  }

  @Override
  public void execute() {}

  public void dispose() {
    this.note.removeListener(this.noteListener);
  }

  private static final String KEY_NOTE = "note";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_NOTE, this.note.getString());
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(KEY_NOTE)) {
      this.note.setValue(obj.get(KEY_NOTE).getAsString());
    } else {
      this.note.setValue("");
    }
  }
}
