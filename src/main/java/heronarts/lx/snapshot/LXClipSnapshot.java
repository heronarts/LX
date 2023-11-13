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
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.utils.LXUtils;

public class LXClipSnapshot extends LXSnapshot implements LXOscComponent, LXLoopTask {

  private boolean inTransition = false;
  private double transitionProgress = 0;

  public final LXClip clip;

  public LXClipSnapshot(LX lx, LXClip clip) {
    super(lx, clip.bus);
    this.clip = clip;
  }

  public LXClip getClip() {
    return this.clip;
  }

  public LXBus getBus() {
    return this.clip.bus;
  }

  @Override
  public LXChannel getClipChannel() {
    return (this.clip.bus instanceof LXChannel) ? (LXChannel) this.clip.bus : null;
  }

  @Override
  public void initialize() {
    initializeClipBus(this.clip.bus);
  }

  public boolean isInTransition() {
    return this.inTransition;
  }

  public double getTransitionProgress() {
    return this.transitionProgress;
  }

  public void getCommands(List<LXCommand> commands) {
    for (View view : this.views) {
      commands.add(view.getCommand());
    }
  }

  public BooleanParameter getSnapshotTransitionEnabledParameter() {
    return this.clip.customSnapshotTransition.isOn() ?
      this.clip.snapshotTransitionEnabled :
      lx.engine.clips.snapshotTransitionEnabled;
  }

  public BoundedParameter getSnapshotTransitionTimeParameter() {
    return this.clip.customSnapshotTransition.isOn() ?
      this.clip.snapshot.transitionTimeSecs :
      lx.engine.clips.snapshotTransitionTimeSecs;
  }

  public void recall() {
    boolean transitionEnabled = getSnapshotTransitionEnabledParameter().isOn();
    for (View view : this.views) {
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
      BoundedParameter transitionTimeSecs =
        this.clip.customSnapshotTransition.isOn() ?
        this.clip.snapshot.transitionTimeSecs :
        lx.engine.clips.snapshotTransitionTimeSecs;

      double increment = deltaMs / (1000 * transitionTimeSecs.getValue());
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
