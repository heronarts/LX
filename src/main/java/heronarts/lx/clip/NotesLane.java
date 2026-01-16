package heronarts.lx.clip;

import com.google.gson.JsonObject;
import heronarts.lx.LX;

/**
 * A composition lane for text notes.
 */
public class NotesLane extends LXClipLane<NotesLaneEvent> {

  public final Composition composition;

  NotesLane(Composition composition) {
    super(composition);
    this.composition = composition;
    this.label.setValue("Notes");
  }

  @Override
  public String getLabel() {
    return this.label.getString();
  }

  @Override
  public String getPath() {
    return "notesLane/" + getIndex();
  }

  @Override
  void overdubCursor(Cursor from, Cursor to, boolean inclusive) {}

  public NotesLaneEvent addEvent(String note) {
    return addEvent(note, this.clip.cursor, null);
  }

  public NotesLaneEvent addEvent(String note, Cursor cursor, Cursor length) {
    NotesLaneEvent event = new NotesLaneEvent(this.lx, this, cursor, length);
    this.mutableEvents.add(event);
    this.onChange.bang();
    return event;
  }

  @Override
  protected NotesLaneEvent loadEvent(LX lx, JsonObject eventObj) {
    NotesLaneEvent event = new NotesLaneEvent(this.lx, this);
    event.load(lx, eventObj);
    return event;
  }

  @Override
  protected void onRemoveEvent(NotesLaneEvent event) {
    event.dispose();
  }

}
