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

import heronarts.lx.LX;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.parameter.LXParameter;

/**
 * Clips that live on the mixer grid, always associated with a fixed mixer bus
 */
public abstract class LXGridClip extends LXClip {

  private final LXBus bus;
  private final boolean hasBusListener;

  protected LXGridClip(LX lx, LXBus bus, int index, boolean registerListener) {
    super(lx, bus, index);
    this.bus = bus;
    if (this.hasBusListener = registerListener) {
      // This class is not always registered as a listener... in the case of LXChannelClip,
      // that parent class will take care of registering as a listener and this will avoid
      // having duplicated double-listeners
      if (registerListener) {
        bus.addEffectsListener(this);
      }
    }
    for (LXEffect effect : bus.effects) {
      registerComponent(effect);
    }
    registerParameter(bus.fader);
  }

  @Override
  protected final boolean isLaneRecording(LXClipBus bus) {
    return true;
  }

  @Override
  protected final boolean isLaneRecording(LXClipLane<?> lane) {
    return true;
  }

  @Override
  protected final boolean isLaneRecording(LXParameter p) {
    return true;
  }

  @Override
  public void dispose() {
    unregisterParameter(bus.fader);
    for (LXEffect effect : this.bus.getEffects()) {
      unregisterComponent(effect);
    }
    if (this.hasBusListener) {
      this.bus.removeEffectsListener(this);
    }
    super.dispose();
  }

}
