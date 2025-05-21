/**
 * Copyright 2017- Mark C. Slee, Heron Arts LLC
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
 * @author Mark C. Slee <mark@heronarts.com>
 */

package heronarts.lx.clip;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXPatternEngine;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.pattern.PatternRack;
import heronarts.lx.utils.LXUtils;

public class PatternClipLane extends LXClipLane<PatternClipEvent> implements LXPatternEngine.Listener {

  public final LXPatternEngine engine;

  PatternClipLane(LXClip clip) {
    this(clip, ((LXChannel) clip.bus).patternEngine);
  }

  PatternClipLane(LXClip clip, PatternRack rack) {
    this(clip, rack.patternEngine);
  }

  PatternClipLane(LXClip clip, LXPatternEngine engine) {
    super(clip);
    this.engine = engine;
    this.engine.addListener(this);
  }

  @Override
  public void patternRemoved(LXPatternEngine channel, LXPattern pattern) {
    boolean changed = false;
    for (int i = 0; i < this.mutableEvents.size(); ++i) {
      PatternClipEvent event = this.mutableEvents.get(i);
      if (event.getPattern() == pattern) {
        if (!changed) {
          this.mutableEvents.begin();
          changed = true;
        }
        this.mutableEvents.remove(i);
        --i;
      }
    }
    if (changed) {
      this.mutableEvents.commit();
      this.onChange.bang();
    }
  }

  private boolean internalTrigger = false;

  private void triggerPattern(LXPattern pattern) {
    this.internalTrigger = true;
    this.engine.goPattern(pattern);
    this.internalTrigger = false;
  }

  void playPatternEvent(PatternClipEvent pattern) {
    triggerPattern(pattern.getPattern());
  }

  void recordPatternEvent(LXPattern pattern) {
    if (!this.internalTrigger) {
      recordEvent(new PatternClipEvent(this, pattern));
      this.overdubActive = true;
    }
  }

  /**
   * Return a list of the indices of events in this clip lane that reference the given pattern
   *
   * @param pattern Pattern
   * @return List of indices that reference this pattern, or null if there are none
   */
  public List<Integer> findEventIndices(LXPattern pattern) {
    List<Integer> indices = null;
    int i = 0;
    for (PatternClipEvent event : this.events) {
      if (event.getPattern() == pattern) {
        if (indices == null) {
          indices = new ArrayList<>();
        }
        indices.add(i);
      }
      ++i;
    }
    return indices;
  }

  @Override
  public String getPath() {
    return "Pattern";
  }

  @Override
  public String getLabel() {
    String label = "Pattern";
    LXComponent component = this.engine.component;
    int count = 1;
    while ((component instanceof PatternRack rack) && (count++ < 3)) {
      label = rack.getLabel() + " | " + label;
      component = component.getParent();
    }
    return label;
  }

  private LXPattern getPatternBeforeCursor(Cursor to) {
    if (!this.events.isEmpty()) {
      int index = cursorPlayIndex(to);
      return this.events.get(index > 0 ? index - 1 : index).getPattern();
    }
    return null;
  }

  private LXPattern getPatternAtCursor(Cursor to) {
    if (!this.events.isEmpty()) {
      int index = cursorInsertIndex(to);
      return this.events.get(index > 0 ? index - 1 : index).getPattern();
    }
    return null;
  }

  private void triggerPatternAtCursor(Cursor to) {
    LXPattern pattern = getPatternAtCursor(to);
    if ((pattern != null) && (this.engine.getTargetPattern() != pattern)) {
      triggerPattern(pattern);
    }
  }

  @Override
  void initializeCursorPlayback(Cursor to) {
    triggerPatternAtCursor(to);
  }

  @Override
  void loopCursor(Cursor from, Cursor to) {
    if (this.overdubActive) {
      LXPattern loopStart = getPatternBeforeCursor(to);
      LXPattern loopEnd = getPatternBeforeCursor(from);
      if (loopStart != loopEnd) {
        recordEvent(new PatternClipEvent(this, to, loopEnd));
      }
    } else {
      triggerPatternAtCursor(to);
    }
  }

  @Override
  void overdubCursor(Cursor from, Cursor to, boolean inclusive) {
    if (this.overdubActive) {

      boolean changed = false;
      this.mutableEvents.begin();

      PatternClipEvent stitchOuter = null;

      if (!this.mutableEvents.isEmpty()) {
        int startIndex = cursorPlayIndex(from);
        int endIndex = inclusive ? cursorInsertIndex(to) : cursorPlayIndex(to);

        if (CursorOp().isBefore(to, this.clip.length.cursor)) {
          stitchOuter = new PatternClipEvent(this, to, this.events.get(endIndex > 0 ? endIndex - 1 : endIndex).getPattern());
        }

        if (endIndex > startIndex) {
          this.mutableEvents.removeRange(startIndex, endIndex);
          changed = true;
        }
      }

      // Insert the new stuff
      if (!this.recordQueue.isEmpty()) {
        commitRecordQueue(false);
        changed = true;
      }

      // Add an outer stitch if it's not redundant
      if (stitchOuter != null) {
        int stitchIndex = cursorInsertIndex(stitchOuter.cursor);
        if ((stitchIndex == 0) || (this.events.get(stitchIndex-1).getPattern() != stitchOuter.getPattern())) {
          this.mutableEvents.add(stitchIndex, stitchOuter);
          changed = true;
        }
      }

      // All done
      this.mutableEvents.commit();
      if (changed) {
        this.onChange.bang();
      }

    } else {

      // Just play it back
      playCursor(from, to, inclusive);

    }
  }

  public static final String KEY_RACK = "rack";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    if (this.engine.component instanceof PatternRack rack) {
      obj.addProperty(KEY_RACK, rack.getCanonicalPath(this.clip.bus));
    }
  }

  @Override
  protected PatternClipEvent loadEvent(LX lx, JsonObject eventObj) {
    final int numPatterns = this.engine.patterns.size();
    final int patternIndex = eventObj.get(PatternClipEvent.KEY_PATTERN_INDEX).getAsInt();
    if (!LXUtils.inRange(patternIndex, 0, numPatterns - 1)) {
      LX.error("Invalid pattern index found in PatternClipLane.loadEvent on channel with " + numPatterns + " patterns: " + eventObj);
      return null;
    }
    return new PatternClipEvent(this, this.engine.patterns.get(patternIndex));
  }

  @Override
  public void dispose() {
    this.engine.removeListener(this);
    super.dispose();
  }

}
