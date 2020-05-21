/**
 * Copyright 2020- Mark C. Slee, Heron Arts LLC
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
import heronarts.lx.LXComponent;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.MutableParameter;

public class LXClipEngine extends LXComponent implements LXOscComponent {

  public class FocusedClipParameter extends MutableParameter {

    private LXClip clip = null;

    private FocusedClipParameter() {
      super("Focused Clip");
      setDescription("Parameter which indicate the globally focused clip");
    }

    public FocusedClipParameter setClip(LXClip clip) {
      if (this.clip != clip) {
        this.clip = clip;
        bang();
      }
      return this;
    }

    public LXClip getClip() {
      return this.clip;
    }
  };

  public final FocusedClipParameter focusedClip = new FocusedClipParameter();

  public LXClipEngine(LX lx) {
    super(lx);
    addParameter("focusedClip", this.focusedClip);
  }

  public LXClip getFocusedClip() {
    return this.focusedClip.getClip();
  }

  public LXClipEngine setFocusedClip(LXClip clip) {
    this.focusedClip.setClip(clip);
    return this;
  }

}
