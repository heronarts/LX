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

package heronarts.lx.effect;

import java.util.ArrayList;
import java.util.List;
import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.COLOR)
public class DynamicsEffect extends LXEffect {

  // These could be bytes but it's a small table and simpler
  // not to deal with unsigned issues
  private final int[] lookupTable = new int[256];

  private final int[] rTable = new int[256];
  private final int[] gTable = new int[256];
  private final int[] bTable = new int[256];

  private class ParameterMonitor {
    private final CompoundParameter parameter;
    private double lastValue;

    private ParameterMonitor(CompoundParameter parameter) {
      this.parameter = parameter;
      this.lastValue = parameter.getValue();
    }

    public boolean checkForChange() {
      double value = this.parameter.getValue();
      boolean dirty = (value != this.lastValue);
      this.lastValue = value;
      return dirty;
    }

    public void clean() {
      this.lastValue = this.parameter.getValue();
    }
  }

  private final List<ParameterMonitor> lookupParameters = new ArrayList<ParameterMonitor>();
  private final List<ParameterMonitor> channelParameters = new ArrayList<ParameterMonitor>();

  public final CompoundParameter floor =
    new CompoundParameter("Floor", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Minimum possible value");

  public final CompoundParameter ceiling =
    new CompoundParameter("Ceiling", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Maximum possible value");

  public final CompoundParameter shape =
    new CompoundParameter("Shape", 0, -1, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription("Shaping factor");

  public final CompoundParameter contrast =
    new CompoundParameter("Contrast", 0, -1, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setPolarity(CompoundParameter.Polarity.BIPOLAR)
    .setDescription("Contrast factor");

  public final CompoundParameter gain =
    new CompoundParameter("Gain", 1, 1, 10)
    .setExponent(2)
    .setDescription("Gain factor");

  public final CompoundParameter gate =
    new CompoundParameter("Gate", 0)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Minimum incoming level to generate output");

  public final CompoundParameter redAmount =
    new CompoundParameter("Red", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Amount of effect to apply to red channel");

  public final CompoundParameter greenAmount =
    new CompoundParameter("Green", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Amount of effect to apply to green channel");

  public final CompoundParameter blueAmount =
    new CompoundParameter("Blue", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Amount of effect to apply to blue channel");

  public final MutableParameter waveChanged =
    new MutableParameter("Wave Changed");

  public DynamicsEffect(LX lx) {
    super(lx);
    addLookupParameter("ceiling", this.ceiling);
    addLookupParameter("contrast", this.contrast);
    addLookupParameter("gain", this.gain);

    addLookupParameter("floor", this.floor);
    addLookupParameter("gate", this.gate);
    addLookupParameter("shape", this.shape);

    addChannelParameter("red", this.redAmount);
    addChannelParameter("green", this.greenAmount);
    addChannelParameter("blue", this.blueAmount);

    buildLookupTable();
  }

  private void addLookupParameter(String path, CompoundParameter parameter) {
    super.addParameter(path, parameter);
    this.lookupParameters.add(new ParameterMonitor(parameter));
  }

  private void addChannelParameter(String path, CompoundParameter parameter) {
    super.addParameter(path, parameter);
    this.channelParameters.add(new ParameterMonitor(parameter));
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    for (ParameterMonitor monitor : this.lookupParameters) {
      if (monitor.parameter == p) {
        monitor.clean();
        buildLookupTable();
        this.waveChanged.bang();
      }
    }
  }

  public int[] getLookupTable() {
    return this.lookupTable;
  }

  private void buildLookupTable() {
    float floor = 255 * this.floor.getValuef();
    float ceiling = 255 * this.ceiling.getValuef();
    float shape = this.shape.getValuef();
    float gain = this.gain.getValuef();

    float gate = this.gate.getValuef();
    float gateFactor = (gate < 1) ? (1 / (1 - gate)) : 0;

    float shapePow = 1;
    if (shape >= 0) {
      // Push up to cube root
      shapePow = 1 - shape * .75f;
    } else if (shape < 0) {
      // Push down to power of 3
      shapePow = 1 - 3 * shape;
    }

    float contrast = this.contrast.getValuef();
    float contrastPow = 1;
    if (contrast >= 0) {
      contrastPow = 1 - contrast * .75f;
    } else {
      contrastPow = 1 - contrast * 3f;
    }

    for (int i = 0; i < this.lookupTable.length; ++i) {
      float lerp = i / (float) (this.lookupTable.length - 1);
      lerp = LXUtils.maxf(0, (lerp - gate)) * gateFactor;

      lerp = 0.5f + (float) Math.pow(2 * Math.abs(lerp - .5f), contrastPow) * ((lerp > .5f) ? 0.5f : -0.5f);

      lerp = (float) Math.pow(lerp, shapePow);
      this.lookupTable[i] = (int) LXUtils.lerpf(floor, ceiling, LXUtils.clampf(lerp * gain, 0, 1));
    }

    buildRGBTables();
  }

  private void buildRGBTables() {
    float r = this.redAmount.getValuef();
    float g = this.greenAmount.getValuef();
    float b = this.blueAmount.getValuef();
    for (int i = 0; i < this.lookupTable.length; ++i) {
      this.rTable[i] = (int) LXUtils.lerpf(i, this.lookupTable[i], r);
      this.gTable[i] = (int) LXUtils.lerpf(i, this.lookupTable[i], g);
      this.bTable[i] = (int) LXUtils.lerpf(i, this.lookupTable[i], b);
    }
  }

  private void rebuildTablesIfNecessary() {
    boolean rebuild = false;
    for (ParameterMonitor monitor : this.lookupParameters) {
      if (monitor.checkForChange()) {
        rebuild = true;
      }
    }
    if (rebuild) {
      buildLookupTable();
      this.waveChanged.bang();
      for (ParameterMonitor monitor : this.channelParameters) {
        monitor.clean();
      }
    } else {
      boolean rebuildRGB = false;
      for (ParameterMonitor monitor : this.channelParameters) {
        if (monitor.checkForChange()) {
          rebuildRGB = true;
        }
      }
      if (rebuildRGB) {
        buildRGBTables();
      }
    }

  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    // Check if any changes occurred
    rebuildTablesIfNecessary();

    if (enabledAmount < 1) {
      // Extra lerping required here, keep this out of the code
      // path when fully enabled...
      float enabledf = (float) enabledAmount;
      for (LXPoint p : model.points) {
        int i = p.index;
        int c = colors[i];
        int a = (c & LXColor.ALPHA_MASK);
        int r = (c & LXColor.R_MASK) >> LXColor.R_SHIFT;
        int g = (c & LXColor.G_MASK) >> LXColor.G_SHIFT;
        int b = (c & LXColor.B_MASK);

        r = (int) LXUtils.lerpf(r, this.rTable[r], enabledf);
        g = (int) LXUtils.lerpf(g, this.gTable[g], enabledf);
        b = (int) LXUtils.lerpf(b, this.bTable[b], enabledf);

        colors[i] = a |
          (r << LXColor.R_SHIFT) |
          (g << LXColor.G_SHIFT) |
          (b);
      }

    } else {

      for (LXPoint p : model.points) {
        int i = p.index;
        int c = colors[i];
        int a = (c & LXColor.ALPHA_MASK);
        int r = (c & LXColor.R_MASK) >> LXColor.R_SHIFT;
        int g = (c & LXColor.G_MASK) >> LXColor.G_SHIFT;
        int b = (c & LXColor.B_MASK);
        colors[i] = a |
          (this.rTable[r] << LXColor.R_SHIFT) |
          (this.gTable[g] << LXColor.G_SHIFT) |
          (this.bTable[b]);
      }
    }
  }

}
