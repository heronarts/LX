/**
 * Copyright 2015- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.output;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import heronarts.lx.LX;

/**
 * Simple concrete output class which does nothing but group its children.
 */
public class LXOutputGroup extends LXOutput {

  private final List<LXOutput> mutableChildren = new ArrayList<LXOutput>();

  public final List<LXOutput> children = Collections.unmodifiableList(this.mutableChildren);

  public LXOutputGroup(LX lx) {
    this(lx, "Output");
  }

  public LXOutputGroup(LX lx, String label) {
    super(lx, label);
    this.gammaMode.setValue(GammaMode.DIRECT);
  }

  /**
   * Adds a child to this output, sent after color-correction
   *
   * @param child Child output
   * @return this
   */
  public LXOutputGroup addChild(LXOutput child) {
    if (this.children.contains(child)) {
      throw new IllegalStateException("May not add duplicate child to LXOutputGroup: " + child);
    }
    child.setGroup(this);
    this.mutableChildren.add(child);
    return this;
  }

  /**
   * Removes a child
   *
   * @param child Child output
   * @return this
   */
  public LXOutputGroup removeChild(LXOutput child) {
    if (!this.children.contains(child)) {
      throw new IllegalStateException("May not add remove non-existent child from LXOutputGroup: " + child);
    }
    this.mutableChildren.remove(child);
    return this;
  }

  protected LXOutputGroup clearChildren() {
    this.mutableChildren.clear();
    return this;
  }

  @Override
  protected void onSend(int[] colors, GammaTable glut, double brightness) {
    //  Send to all children, with cascading brightness
    for (LXOutput child : this.children) {
      child.send(colors, brightness);
    }
  }

}
