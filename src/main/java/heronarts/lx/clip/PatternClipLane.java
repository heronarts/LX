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

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.pattern.LXPattern;

public class PatternClipLane extends LXClipLane {
  PatternClipLane(LXClip clip) {
    super(clip);
  }

  @Override
  public String getLabel() {
    return "Pattern";
  }

  @Override
  protected LXClipEvent loadEvent(LX lx, JsonObject eventObj) {
    LXChannel channel = (LXChannel) this.clip.bus;
    LXPattern pattern = channel.patterns.get(eventObj.get(PatternClipEvent.KEY_PATTERN_INDEX).getAsInt());
    return new PatternClipEvent(this, channel, pattern);
  }
}
