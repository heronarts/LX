/**
 * Copyright 2018- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import heronarts.lx.clip.LXClip;
import heronarts.lx.clip.LXGroupClip;

public class LXGroup extends LXChannelBus {

  private final List<LXChannel> mutableChannels = new ArrayList<LXChannel>();
  public final List<LXChannel> channels = Collections.unmodifiableList(this.mutableChannels);

  public LXGroup(LX lx, int index) {
    super(lx, index, "Group-" + (index+1));
  }

  @Override
  protected LXClip constructClip(int index) {
    return new LXGroupClip(this.lx, this, index);
  }

  public LXGroup addChannel(LXChannel channel) {
    if (this.channels.contains(channel)) {
      throw new IllegalStateException("Cannot add channel to group twice: " + channel + " " + this);
    }
    this.mutableChannels.add(channel);
    return this;
  }

  public LXGroup removeChannel(LXChannel channel) {
    if (!this.channels.contains(channel)) {
      throw new IllegalStateException("Cannot remove channel not in group: " + channel + " " + this);
    }
    this.mutableChannels.remove(channel);
    return this;
  }

}
