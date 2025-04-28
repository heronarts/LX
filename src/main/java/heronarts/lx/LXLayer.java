/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
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

/**
 * A layer is a components that has a run method and operates on some other
 * buffer component. The layer does not actually own the color buffer. An
 * effect is an example of a layer, or patterns may compose themselves from
 * multiple layers.
 */
public abstract class LXLayer extends LXLayeredComponent {

  private int index = -1;

  protected LXLayer(LX lx) {
    super(lx);
  }

  protected LXLayer(LX lx, LXDeviceComponent buffer) {
    super(lx, buffer);
  }

  void setIndex(int index) {
    this.index = index;
  }

  public int getIndex() {
    return this.index;
  }

  @Override
  public String getPath() {
    return "layer/" + (this.index + 1);
  }

  @Override
  public String getLabel() {
    return "Layer " + (this.index + 1);
  }

  @Override
  protected final void onLoop(double deltaMs) {
    run(deltaMs);
  }

  /**
   * Removes this layer from the parent component after all
   * layer execution is finished.
   */
  public void remove() {
    ((LXLayeredComponent) getParent()).removeLayer(this);
  }

  /**
   * Run this layer.
   *
   * @param deltaMs Milliseconds elapsed since last frame
   */
  public abstract void run(double deltaMs);

}
