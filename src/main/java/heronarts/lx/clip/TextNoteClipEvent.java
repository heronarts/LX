/**
 * Copyright 2025- Justin K. Belcher, Heron Arts LLC
 *
 * This file is part of the LX Studio software library. By using
 * LX, you agree to the terms of the LX Studio Software License
 * and Distribution Agreement, available at: http://lx.studio/license
 *
 * Please note that the LX license is not open-source. The license
 * allows for free, non-commercial use.
 *
 * HERON ARTS MAKES NO WARRANTY, EXPRESS, IMPLIED, STATUTORY, OR
 * OTHERWISE, AND SPECIFICALLY DISCLAIMS ANY WARRANTY OF
 * MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR
 * PURPOSE, WITH RESPECT TO THE SOFTWARE.
 *
 * @author Justin K. Belcher <justin@jkb.studio>
 */

package heronarts.lx.clip;

import com.google.gson.JsonObject;
import heronarts.lx.LX;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.StringParameter;

/**
 * A composition event containing a text note
 */
public class TextNoteClipEvent extends LXCompositionEvent<TextNoteClipEvent> {

  public final TextNoteClipLane lane;

  public final StringParameter note =
    new StringParameter("Note")
    .setDescription("Contents of the note");

  private final LXParameterListener noteListener;

  TextNoteClipEvent(LX lx, TextNoteClipLane lane) {
    this(lx, lane, lane.clip.cursor, null);
  }

  TextNoteClipEvent(LX lx, TextNoteClipLane lane, Cursor cursor, Cursor length) {
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
