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

package heronarts.lx.effect;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.COLOR)
public class InvertEffect extends LXEffect {

  public final CompoundParameter amount =
    new CompoundParameter("Amount", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Amount of inversion to apply");

  public final CompoundParameter redAmount =
    new CompoundParameter("Red", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Amount of inversion to apply to the green channel");

  public final CompoundParameter greenAmount =
    new CompoundParameter("Green", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Amount of inversion to apply to the red channel");

  public final CompoundParameter blueAmount =
    new CompoundParameter("Blue", 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Amount of inversion to apply to the blue channel");

  private final int[] redLUT = new int[256];
  private final int[] greenLUT = new int[256];
  private final int[] blueLUT = new int[256];

  private double pRedAmount = -1;
  private double pGreenAmount = -1;
  private double pBlueAmount = -1;

  public InvertEffect(LX lx) {
    super(lx);
    addParameter("amount", this.amount);
    addParameter("redAmount", this.redAmount);
    addParameter("greenAmount", this.greenAmount);
    addParameter("blueAmount", this.blueAmount);
  }

  private void buildLookupTable(int[] table, double amount) {
    for (int i = 0; i < table.length; ++i) {
      table[i] = (int) Math.round(LXUtils.lerp(i, 255 - i, amount));
    }
  }

  @Override
  protected void run(double deltaMs, double enabledAmount) {
    double amount = enabledAmount * this.amount.getValue();
    if (amount == 0) {
      // Nothing needs to happen!
      return;
    }
    double redAmount = amount * this.redAmount.getValue();
    double greenAmount = amount * this.greenAmount.getValue();
    double blueAmount = amount * this.blueAmount.getValue();
    if ((redAmount == 0) && (greenAmount == 0) && (blueAmount == 0)) {
      // Nothing needs to happen!
      return;
    }
    // Generate new lookup tables for any amount that has changed
    if (redAmount != pRedAmount) {
      buildLookupTable(this.redLUT, this.pRedAmount = redAmount);
    }
    if (greenAmount != pGreenAmount) {
      buildLookupTable(this.greenLUT, this.pGreenAmount = greenAmount);
    }
    if (blueAmount != pBlueAmount) {
      buildLookupTable(this.blueLUT, this.pBlueAmount = blueAmount);
    }

    for (LXPoint p : model.points) {
      int c = this.colors[p.index];
      int a = c & LXColor.ALPHA_MASK;
      int r = (c & LXColor.R_MASK) >> LXColor.R_SHIFT;
      int g = (c & LXColor.G_MASK) >> LXColor.G_SHIFT;
      int b = (c & LXColor.B_MASK);
      r = this.redLUT[r];
      g = this.greenLUT[g];
      b = this.blueLUT[b];
      this.colors[p.index] = a | (r << LXColor.R_SHIFT) | (g << LXColor.G_SHIFT) | b;
    }
  }
}
