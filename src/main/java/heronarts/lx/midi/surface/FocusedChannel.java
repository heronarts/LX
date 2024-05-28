/**
 * Copyright 2024- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.midi.surface;

import heronarts.lx.LX;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;

public class FocusedChannel {

  public interface Listener {
    public void onChannelFocused(LXBus bus);
  }

  private final LX lx;
  private final Listener listener;
  private final boolean isAux;

  private final LXParameterListener focusListener = this::onChannelFocused;

  public FocusedChannel(LX lx, Listener listener) {
    this(lx, false, listener);
  }

  public FocusedChannel(LX lx, boolean isAux, Listener listener) {
    this.lx = lx;
    this.isAux = isAux;
    this.listener = listener;
  }

  private LXChannel focusedChannel = null;

  private void setFocusedChannel(LXBus channel) {
    if (this.focusedChannel != null) {
      this.focusedChannel.controlSurfaceSemaphore.decrement();
      this.focusedChannel = null;
    }
    if (channel instanceof LXChannel) {
      this.focusedChannel = (LXChannel) channel;
      this.focusedChannel.controlSurfaceSemaphore.increment();
    }
  }

  private void onChannelFocused(LXParameter p) {
    LXBus bus = this.isAux ? this.lx.engine.mixer.getFocusedChannelAux() : this.lx.engine.mixer.getFocusedChannel();
    setFocusedChannel(bus);
    this.listener.onChannelFocused(bus);
  }

  public void register() {
    (this.isAux ? this.lx.engine.mixer.focusedChannelAux : this.lx.engine.mixer.focusedChannel)
    .addListener(this.focusListener, true);
  }

  public void unregister() {
    (this.isAux ? this.lx.engine.mixer.focusedChannelAux : this.lx.engine.mixer.focusedChannel)
    .removeListener(this.focusListener);
    setFocusedChannel(null);
  }

}
