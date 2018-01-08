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

package heronarts.lx;

import heronarts.lx.model.LXModel;

public abstract class LXModelComponent extends LXModulatorComponent {

  protected LXModel model;

  protected LXModelComponent(LX lx) {
    this(lx, null);
  }

  protected LXModelComponent(LX lx, String label) {
    super(lx, label);
    this.model = lx.model;
  }

  public LXModel getModel() {
    return this.model;
  }

  public LXModelComponent setModel(LXModel model) {
    if (model == null) {
      throw new IllegalArgumentException("May not set null model");
    }
    if (this.model != model) {
      this.model = model;
      onModelChanged(model);
    }
    return this;
  }

  /**
   * Subclasses should override to handle changes to which model
   * they are addressing.
   *
   * @param model New model
   */
  protected void onModelChanged(LXModel model) {}

}
