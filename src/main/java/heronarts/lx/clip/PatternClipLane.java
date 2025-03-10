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
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.utils.LXUtils;

public class PatternClipLane extends LXClipLane<PatternClipEvent> implements LXChannel.Listener {

  public final LXChannel channel;

  PatternClipLane(LXClip clip) {
    super(clip);
    this.channel = (LXChannel) clip.bus;
    this.channel.addListener(this);
  }

  public void patternRemoved(LXChannel channel, LXPattern pattern) {
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
    return "Pattern";
  }

  private LXPattern getPatternAtCursor(Cursor to) {
    if (!this.events.isEmpty()) {
      int index = cursorPlayIndex(to);
      return this.events.get(index > 0 ? index - 1 : index).getPattern();
    }
    return null;
  }

  private void setPatternAtCursor(Cursor to) {
    LXPattern pattern = getPatternAtCursor(to);
    if (pattern != null) {
      LXChannel channel = pattern.getChannel();
      if ((channel.getActivePattern() != pattern) && (channel.getNextPattern() != pattern)) {
        channel.goPattern(pattern);
      }
    }
  }

  @Override
  void initializeCursor(Cursor to) {
    setPatternAtCursor(to);
  }

  @Override
  void loopCursor(Cursor to) {
    setPatternAtCursor(to);
  }

  @Override
  protected PatternClipEvent loadEvent(LX lx, JsonObject eventObj) {
    final int numPatterns = this.channel.patterns.size();
    final int patternIndex = eventObj.get(PatternClipEvent.KEY_PATTERN_INDEX).getAsInt();
    if (!LXUtils.inRange(patternIndex, 0, numPatterns - 1)) {
      LX.error("Invalid pattern index found in PatternClipLane.loadEvent on channel with " + numPatterns + " patterns: " + eventObj);
      return null;
    }
    return new PatternClipEvent(this, this.channel.patterns.get(patternIndex));
  }

  @Override
  public void dispose() {
    this.channel.removeListener(this);
    super.dispose();
  }

}
