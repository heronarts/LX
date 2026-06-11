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

/**
 * A composition lane for text notes.
 */
public class TextNoteClipLane extends LXClipLane<TextNoteClipEvent> {

  public final LXComposition composition;

  TextNoteClipLane(LXComposition composition) {
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

  public TextNoteClipEvent addEvent(String note) {
    return addEvent(note, this.clip.cursor, null);
  }

  public TextNoteClipEvent addEvent(String note, Cursor cursor, Cursor length) {
    TextNoteClipEvent event = new TextNoteClipEvent(this.lx, this, cursor, length);
    this.mutableEvents.add(event);
    this.onChange.bang();
    return event;
  }

  @Override
  protected TextNoteClipEvent loadEvent(LX lx, JsonObject eventObj) {
    TextNoteClipEvent event = new TextNoteClipEvent(this.lx, this);
    event.load(lx, eventObj);
    return event;
  }

  @Override
  protected void onRemoveEvent(TextNoteClipEvent event) {
    event.dispose();
  }

}
