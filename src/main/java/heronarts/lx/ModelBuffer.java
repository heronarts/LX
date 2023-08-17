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

import java.util.Arrays;

import heronarts.lx.model.LXModel;

public class ModelBuffer implements LXBuffer {

  private final LX lx;
  private int[] array;
  private final int defaultColor;

  private final LX.Listener modelListener = new LX.Listener() {
    @Override
    public void modelChanged(LX lx, LXModel model) {
      if (array.length != model.size) {
        initArray(model);
      }
    }
  };

  public ModelBuffer(LX lx) {
    this(lx, 0);
  }

  public ModelBuffer(LX lx, int defaultColor) {
    this.lx = lx;
    this.defaultColor = defaultColor;
    initArray(lx.model);
    lx.addListener(this.modelListener);
  }

  private void initArray(LXModel model) {
    this.array = new int[model.size];
    Arrays.fill(this.array, this.defaultColor);
  }

  public int[] getArray() {
    return this.array;
  }

  public void dispose() {
    this.lx.removeListener(this.modelListener);
  }

}
