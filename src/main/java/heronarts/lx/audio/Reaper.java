/**
 * Copyright 2023- Mark C. Slee, Heron Arts LLC
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
package heronarts.lx.audio;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXRunnableComponent;
import heronarts.lx.osc.LXOscEngine;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.NormalizedParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

public class Reaper extends LXRunnableComponent {

  public static final int MAX_METERS = 32;

  public class Meter {

    private static final int MASTER = -1;
    public static final int TIMEOUT = 1000;

    private double timeout = 0;
    private float target = 0;

    public final String id;
    public final BooleanParameter active;
    public final NormalizedParameter level;

    private Meter(int channel) {
      final String prefix, path, description;
      if (channel == MASTER) {
        this.id = "M";
        prefix = "Master";
        path = "master";
        description = "Reaper master level";
      } else {
        this.id = String.valueOf(channel+1);
        prefix = "Meter-" + this.id;
        path = "meter-" + this.id;
        description = "Reaper track " + this.id;
      }

      this.active =
        new BooleanParameter(prefix + "-Active", false)
        .setDescription(description + " active");

      this.level =
        new NormalizedParameter(prefix)
        .setDescription(description);

      addParameter(path, this.level);
      addParameter(path + "-active", this.active);
    }

    private void run(double deltaMs, double meterAttack, double meterRelease) {
      this.timeout += deltaMs;
      if (this.timeout > Meter.TIMEOUT) {
        this.target = 0;
        this.active.setValue(false);
      }
      final double value = this.level.getValue();
      if (value != this.target) {
        double timeFactor = (this.target > value) ? meterAttack : meterRelease;
        if (timeFactor > 0) {
          double gain = Math.exp(-deltaMs / timeFactor);
          this.level.setValue(LXUtils.lerp(this.target, value, gain));
        } else {
          this.level.setValue(this.target);
        }
      }
    }

    private void setTarget(float target) {
      this.active.setValue(true);
      this.target = LXUtils.constrainf(target, 0, 1);
      this.timeout = 0;
    }
  }

  public final Meter master;

  public final Meter[] meters = new Meter[MAX_METERS];

  public final BoundedParameter meterAttack =
    new BoundedParameter("Attack", 0, 0, 50)
    .setDescription("Sets the attack time of the meter response")
    .setUnits(BoundedParameter.Units.MILLISECONDS);

  public final BoundedParameter meterRelease =
    new BoundedParameter("Release", 0, 0, 500)
    .setDescription("Sets the release time of the meter response")
    .setUnits(BoundedParameter.Units.MILLISECONDS);

  public final TriggerParameter clearMeters =
    new TriggerParameter("Clear", () -> {
      for (Meter meter : this.meters) {
        meter.target = 0;
      }
    });

  public enum FoldMode {
    ALL("All"),
    ACTIVE("Active");

    private final String label;

    private FoldMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final EnumParameter<FoldMode> foldMode =
    new EnumParameter<FoldMode>("Auto-Fold", FoldMode.ACTIVE)
    .setDescription("Which tracks to fold in the UI");

  public final BooleanParameter metersExpanded =
    new BooleanParameter("Meters Expanded", false)
    .setDescription("Show Reaper meters in the UI");

  public Reaper(LX lx) {
    super(lx, "Reaper");
    addParameter("meterAttack", this.meterAttack);
    addParameter("meterRelease", this.meterRelease);
    addParameter("foldMode", this.foldMode);
    addInternalParameter("metersExpanded", this.metersExpanded);

    this.master = new Meter(Meter.MASTER);
    for (int i = 0; i < MAX_METERS; ++i) {
      this.meters[i] = new Meter(i);
    }

  }

  @Override
  public void onStop() {
    this.master.level.setValue(0);
    for (Meter meter : this.meters) {
      meter.level.setValue(0);
      meter.active.setValue(false);
    }
  }

  @Override
  public void run(double deltaMs) {
    final double meterAttack = this.meterAttack.getValue();
    final double meterRelease = this.meterRelease.getValue();

    this.master.run(deltaMs, meterAttack, meterRelease);
    for (Meter meter : this.meters) {
      meter.run(deltaMs, meterAttack, meterRelease);
    }
  }

  public static final String REAPER_OSC_PATH = "reaper";
  public static final String REAPER_MASTER_PATH = "master";
  public static final String REAPER_TRACK_PATH = "track";
  public static final String REAPER_VU_PATH = "vu";

  public boolean handleReaperOscMessage(OscMessage message, String[] parts, int index) {
    // LXOscEngine.log("[Reaper] " + message.toString());
    if (REAPER_MASTER_PATH.equals(parts[2])) {
      if (REAPER_VU_PATH.equals(parts[3])) {
        this.master.setTarget(message.getFloat());
      }
    } else if (REAPER_TRACK_PATH.equals(parts[2])) {
      if ((parts.length == 5) && REAPER_VU_PATH.equals(parts[4])) {
        int meterIndex = Integer.parseInt(parts[3]) - 1;
        if (meterIndex < 0 || meterIndex >= MAX_METERS) {
          LXOscEngine.error("Bad Reaper track meter index: " + message.getAddressPattern());
        } else {
          this.meters[meterIndex].setTarget(message.getFloat());
        }
      }
    }
    return true;
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(KEY_RESET)) {
      this.foldMode.reset();
      this.metersExpanded.setValue(false);
      stop();
    }
  }

}