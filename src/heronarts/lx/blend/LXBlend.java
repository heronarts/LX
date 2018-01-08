/**
 * Copyright 2016- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.blend;

import heronarts.lx.LX;
import heronarts.lx.LXBuffer;
import heronarts.lx.LXModulatorComponent;

/**
 * An LXBlend is a loop-based implementation of a compositing algorithm.
 * Two color buffers are blended together using some logic, typically
 * a standard alpha-compositing technique. However, more complex blend
 * modes may be authored, taking into account position information from
 * the model, for instance.
 */
public abstract class LXBlend extends LXModulatorComponent {

  protected static final int ALPHA_SHIFT = 24;
  protected static final int R_SHIFT = 16;
  protected static final int G_SHIFT = 8;
  protected static final int R_MASK = 0x00ff0000;
  protected static final int G_MASK = 0x0000ff00;
  protected static final int B_MASK = 0x000000ff;
  protected static final int RB_MASK = R_MASK | B_MASK;

  protected static int min(int a, int b) {
    return (a < b) ? a : b;
  }

  protected static int max(int a, int b) {
    return (a > b) ? a : b;
  }

  private String name;

  protected LXBlend(LX lx) {
    super(lx);
    String simple = this.getClass().getSimpleName();
    if (simple.endsWith("Blend")) {
      simple = simple.substring(0, simple.length() - "Blend".length());
    }
    this.name = simple;
  }

  /**
   * Sets name of this blend mode
   *
   * @param name UI name of blend
   * @return this
   */
  public LXBlend setName(String name) {
    this.name = name;
    return this;
  }

  /**
   * Returns the name of this blend, to be shown in UI
   *
   * @return Blend name
   */
  public String getName() {
    return this.name;
  }

  /**
   * Gets the name of this blend.
   *
   */
  @Override
  public String getLabel() {
    return getName();
  }

  /**
   * Name of the blend
   */
  @Override
  public String toString() {
    return getName();
  }

  public void blend(int[] dst, int[] src, double alpha, LXBuffer buffer) {
    blend(dst, src, alpha, buffer.getArray());
  }

  /**
   * Blends the src buffer onto the destination buffer at the specified alpha amount.
   *
   * @param dst Destination buffer (lower layer)
   * @param src Source buffer (top layer)
   * @param alpha Alpha blend, from 0-1
   * @param output Output buffer, which may be the same as src or dst
   */
  public abstract void blend(int[] dst, int[] src, double alpha, int[] output);
}
