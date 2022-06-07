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

package heronarts.lx.snapshot;

import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.LXLoopTask;
import heronarts.lx.clip.LXClip;
import heronarts.lx.command.LXCommand;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.utils.LXUtils;

public class LXClipSnapshot extends LXSnapshot implements LXOscComponent, LXLoopTask {

  private boolean inTransition = false;
  private double transitionProgress = 0;

  public LXClipSnapshot(LX lx) {
    super(lx);
  }

  public LXClip getClip() {
    return (LXClip) getParent();
  }

  @Override
  public void initialize() {
    initializeBus(getClip().bus);
  }

  public boolean isInTransition() {
    return this.inTransition;
  }

  public double getTransitionProgress() {
    return this.transitionProgress;
  }

  public void recall() {
    recall(null);
  }

  public void recall(List<LXCommand> commands) {
    boolean transitionEnabled = getClip().snapshotTransitionEnabled.isOn();
    for (View view : this.views) {
      if (commands != null) {
        commands.add(view.getCommand());
      }
      if (transitionEnabled) {
        view.startTransition();
      } else {
        view.recall();
      }
    }
    if (transitionEnabled) {
      this.inTransition = true;
      this.transitionProgress = 0;
    }
  }

  public void loop(double deltaMs) {
    if (this.inTransition) {
      double increment = deltaMs / (1000 * getClip().snapshot.transitionTimeSecs.getValue());
      this.transitionProgress = LXUtils.min(1., this.transitionProgress + increment);
      if (this.transitionProgress == 1.) {
        for (View view : this.views) {
          view.finishTransition();
        }
        this.inTransition = false;
      } else {
        for (View view : this.views) {
          view.interpolate(this.transitionProgress);
        }
      }
    }
  }

  public void stopTransition() {
    this.inTransition = false;
  }

  @Override
  public String getPath() {
    return "snapshot";
  }

}
