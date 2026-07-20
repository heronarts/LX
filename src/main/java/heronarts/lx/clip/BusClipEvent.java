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

/**
 * A composition bus lane event is a light container around... a clip! What fun!
 */
public class BusClipEvent extends LXCompositionEvent<BusClipEvent> {

  BusClipEvent(BusClipLane lane) {
    super(lane);
    throw new UnsupportedOperationException("Cannot construct BusClipEvent");
  }

  @Override
  public void execute() {
    throw new UnsupportedOperationException("Cannot execute BusClipEvent");
  }
}
