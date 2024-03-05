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
 * @author Justin K. Belcher <jkbelcher@gmail.com>
 */

package heronarts.lx.snapshot;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.TriggerParameter;

/**
 * A snapshot holds a memory of the state of the program at a point in time.
 * The snapshot contains a collection of "views" which are memories of a piece
 * of state in the program at some time. Typically this is a parameter value,
 * but some special cases exist, like the active pattern on a channel.
 */
public class LXGlobalSnapshot extends LXSnapshot implements LXComponent.Renamable, LXOscComponent {

  private int index = -1;

  public final TriggerParameter recall =
    new TriggerParameter("Recall", () -> { this.lx.engine.snapshots.recall(this); })
    .setDescription("Restores the values of this snapshot");

  public final BooleanParameter autoCycleEligible =
    new BooleanParameter("Cycle", true)
    .setDescription("Whether the snapshot is eligible for auto-cycle");

  public final BoundedParameter cycleTimeSecs =
    new BoundedParameter("Cycle Time", 60, .1, 60*60*24)
    .setDescription("Sets the number of seconds after which the engine cycles to the next snapshot")
    .setUnits(LXParameter.Units.SECONDS);

  public final BooleanParameter hasCustomCycleTime =
    new BooleanParameter("Custom Cycle", false)
    .setDescription("When enabled, this snapshot uses its own custom duration rather than the default cycle time");

  public final BooleanParameter hasCustomTransitionTime =
    new BooleanParameter("Custom Transition", false)
    .setDescription("When enabled, this snapshot uses its own custom transition rather than the default transition time");


  public LXGlobalSnapshot(LX lx) {
    super(lx, null);
    setParent(lx.engine.snapshots);
    addParameter("recall", this.recall);
    addParameter("autoCycleEligible", this.autoCycleEligible);
    addParameter("hasCustomCycleTime", this.hasCustomCycleTime);
    addParameter("cycleTimeSecs", this.cycleTimeSecs);
    addParameter("hasCustomTransitionTime", this.hasCustomTransitionTime);
  }

  // Package-only method for LXSnapshotEngine to update indices
  void setIndex(int index) {
    this.index = index;
  }

  /**
   * Public accessor for the index of this snapshot in the list
   *
   * @return This snapshot's position in the global list
   */
  public int getIndex() {
    return this.index;
  }

  @Override
  public String getPath() {
    return "snapshot/" + (this.index+1);
  }

  @Override
  public String getOscPath() {
    String path = super.getOscPath();
    if (path != null) {
      return path;
    }
    return getOscLabel();
  }

  @Override
  public String getOscAddress() {
    LXComponent parent = getParent();
    if (parent instanceof LXOscComponent) {
      return parent.getOscAddress() + "/" + getOscPath();
    }
    return null;
  }

  // Initializes a new snapshot with views of everything
  // relevant in the project scope. It's a bit of an arbitrary selection at the moment
  @Override
  public void initialize() {
    LX lx = getLX();

    addParameterView(ViewScope.OUTPUT, lx.engine.output.brightness);
    addParameterView(ViewScope.MIXER, lx.engine.mixer.crossfader);
    for (LXAbstractChannel bus : lx.engine.mixer.channels) {
      initializeGlobalBus(bus);
    }
    initializeGlobalBus(lx.engine.mixer.masterBus);

    // Modulator settings
    for (LXModulator modulator : lx.engine.modulation.getModulators()) {
      for (LXParameter p : modulator.getParameters()) {
        addParameterView(ViewScope.MODULATION, p);
      }
    }

    // Global component settings
    for (LXComponent global : lx.engine.snapshots.globalComponents) {
      for (LXParameter p : global.getParameters()) {
        if (p != global.label) {
          addParameterView(ViewScope.GLOBAL, p);
        }
      }
    }
  }


}
