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

package heronarts.lx.effect.color;

import java.util.HashSet;
import java.util.Set;

import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.utils.LXUtils;

public class DynamicsEffect extends LXEffect {

  // These could be bytes but it's a small table and simpler
  // not to deal with unsigned issues
  private final int[] lookupTable = new int[256];

  private final int[] rTable = new int[256];
  private final int[] gTable = new int[256];
  private final int[] bTable = new int[256];

  private final Set<LXParameter> lookupParameters = new HashSet<LXParameter>();

  private final Set<LXParameter> channelParameters = new HashSet<LXParameter>();

  public final BoundedParameter floor =
    new BoundedParameter("Floor", 0)
    .setDescription("Minimum possible value");

  public final BoundedParameter ceiling =
    new BoundedParameter("Ceiling", 1)
    .setDescription("Maximum possible value");

  public final BoundedParameter shape =
    new BoundedParameter("Shape", 0, -1, 1)
    .setPolarity(BoundedParameter.Polarity.BIPOLAR)
    .setDescription("Shaping factor");

  public final BoundedParameter contrast =
    new BoundedParameter("Contrast", 0, -1, 1)
    .setPolarity(BoundedParameter.Polarity.BIPOLAR)
    .setDescription("Contrast factor");

  public final BoundedParameter redAmount =
    new BoundedParameter("Red", 1)
    .setDescription("Amount of effect to apply to red channel");

  public final BoundedParameter greenAmount =
    new BoundedParameter("Green", 1)
    .setDescription("Amount of effect to apply to green channel");

  public final BoundedParameter blueAmount =
    new BoundedParameter("Blue", 1)
    .setDescription("Amount of effect to apply to blue channel");

  public final MutableParameter waveChanged =
    new MutableParameter("Wave Changed");

  public DynamicsEffect(LX lx) {
    super(lx);
    addLookupParameter("floor", this.floor);
    addLookupParameter("ceiling", this.ceiling);
    addLookupParameter("shape", this.shape);
    addLookupParameter("contrast", this.contrast);

    addChannelParameter("red", this.redAmount);
    addChannelParameter("green", this.greenAmount);
    addChannelParameter("blue", this.blueAmount);

    buildLookupTable();
  }

  private void addLookupParameter(String path, LXParameter parameter) {
    super.addParameter(path, parameter);
    this.lookupParameters.add(parameter);
  }

  private void addChannelParameter(String path, LXParameter parameter) {
    super.addParameter(path, parameter);
    this.channelParameters.add(parameter);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (this.lookupParameters.contains(p)) {
      buildLookupTable();
      this.waveChanged.bang();
    } else if (this.channelParameters.contains(p)) {
      buildRGBTables();
    }
  }

  public int[] getLookupTable() {
    return this.lookupTable;
  }

  private void buildLookupTable() {
    float floor = 255 * this.floor.getValuef();
    float ceiling = 255 * this.ceiling.getValuef();
    float shape = this.shape.getValuef();

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
      lerp = 0.5f + (float) Math.pow(2 * Math.abs(lerp - .5f), contrastPow) * ((lerp > .5f) ? 0.5f : -0.5f);
      lerp = (float) Math.pow(lerp, shapePow);
      this.lookupTable[i] = (int) LXUtils.lerpf(floor, ceiling, lerp);
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

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    if (enabledAmount < 1) {
      // Extra lerping required here, keep this out of the code
      // path when fully enabled...
      float enabledf = (float) enabledAmount;
      for (int i = 0; i < colors.length; ++i) {
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

      for (int i = 0; i < colors.length; ++i) {
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
