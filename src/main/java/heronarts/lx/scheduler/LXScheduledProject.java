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

package heronarts.lx.scheduler;

import java.io.File;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;

public class LXScheduledProject extends LXComponent implements LXComponent.Renamable {

  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", false)
    .setDescription("Whether this schedule entry is enabled");

  public final StringParameter projectFile = new StringParameter("Project")
    .setDescription("The project file that will be opened");

  public final DiscreteParameter hours =
    new DiscreteParameter("Hours", 0, 24)
    .setDescription("The hour of day the project will open on");

  public final DiscreteParameter minutes =
    new DiscreteParameter("Minutes", 0, 60)
    .setDescription("The minute of hour the project will open on");

  public final DiscreteParameter seconds =
    new DiscreteParameter("Seconds", 0, 60)
    .setDescription("The second of the minute the project will open on");

  private int index = 0;

  public LXScheduledProject(LXScheduler scheduler) {
    super(scheduler.getLX());
    setParent(scheduler);
    addParameter("enabled", this.enabled);
    addParameter("projectFile", this.projectFile);
    addParameter("hours", this.hours);
    addParameter("minutes", this.minutes);
    addParameter("seconds", this.seconds);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    this.lx.scheduler.dirty.setValue(true);
  }

  public void setProject(File projectFile) {
    this.projectFile.setValue(this.lx.getMediaPath(LX.Media.PROJECTS, projectFile));
    this.label.setValue(projectFile.getName());
  }

  void setIndex(int index) {
    this.index = index;
  }

  public int getIndex() {
    return this.index;
  }

  public void open() {
    this.lx.scheduler.openEntry(this);
  }
}
