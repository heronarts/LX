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

import heronarts.lx.LXComponent;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.parameter.BooleanParameter;

import java.util.List;

/**
 * Provides clip context such as an Arm parameter.
 * Used on Bus and LXCompositionEngine
 */
public interface LXClipContainer {
  public BooleanParameter getArmParameter();

  public List<LXClip> getClips();
  public void onClipStart(LXClip clip);
  public void onClipStop(LXClip clip);

  // TODO: doesn't seem like these should really be here?
  public List<LXEffect> getEffects();
  public void addEffectsListener(LXBus.Listener listener);
  public void removeEffectsListener(LXBus.Listener listener);

  public LXComponent getComponent();
}
