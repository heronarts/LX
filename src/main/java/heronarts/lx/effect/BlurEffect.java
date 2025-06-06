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
import heronarts.lx.LXComponent;
import heronarts.lx.ModelBuffer;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.CORE)
@LXComponent.Description("Blends the content from prior frames to create blur motion")
public class BlurEffect extends LXEffect {

  public enum Mode {
    MIX("Mix"),
    ADD("Add"),
    SCREEN("Screen"),
    MULTIPLY("Multiply"),
    LIGHTEST("Lightest");

    public final String label;

    private Mode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public final CompoundParameter level =
    new CompoundParameter("Level", 0, 0, 1)
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Level of the blur relative to original signal");

  public final CompoundParameter decay =
    new CompoundParameter("Decay", 1, 0.01, 60)
    .setDescription("Decay time for the motion blur to diminish to decay factor")
    .setExponent(3)
    .setUnits(CompoundParameter.Units.SECONDS);

  public final CompoundParameter decayFactor =
    new CompoundParameter("Factor", .5, 0.01, 1)
    .setDescription("Decay factor, the level reached in decay time (e.g. half-life if at 50%)")
    .setUnits(CompoundParameter.Units.PERCENT_NORMALIZED);

  public final EnumParameter<Mode> mode =
    new EnumParameter<Mode>("Mode", Mode.MIX)
    .setDescription("Which blending mode the blur uses");

  private final ModelBuffer blurBuffer;

  public BlurEffect(LX lx) {
    super(lx);
    this.blurBuffer = new ModelBuffer(lx, LXColor.BLACK);
    addParameter("level", this.level);
    addParameter("decay", this.decay);
    addParameter("decayFactor", this.decayFactor);
    addParameter("mode", this.mode);
  }

  @Override
  protected void onEnable() {
    int[] blurArray = this.blurBuffer.getArray();
    for (int i = 0; i < blurArray.length; ++i) {
      blurArray[i] = LXColor.BLACK;
    }
  }

  private static int decrementColor(int argb) {
    int r = (argb & LXColor.R_MASK) >> LXColor.R_SHIFT;
    int g = (argb & LXColor.G_MASK) >> LXColor.G_SHIFT;
    int b = (argb & LXColor.B_MASK);
    r = LXUtils.max(0, r-1);
    g = LXUtils.max(0, g-1);
    b = LXUtils.max(0, b-1);
    return
      (argb & LXColor.ALPHA_MASK) |
      (r << LXColor.R_SHIFT) |
      (g << LXColor.G_SHIFT) |
      b;
  }

  @Override
  public void run(double deltaMs, double amount) {
    final int blurAlpha = (int) (LXColor.BLEND_ALPHA_FULL * amount * this.level.getValue());
    final int[] blurColors = this.blurBuffer.getArray();

    final double decayScale = Math.pow(this.decayFactor.getValue(), deltaMs / (1000 * this.decay.getValue()));
    final int decayColor = LXColor.grayn(decayScale);

    for (LXPoint p : model.points) {
      int i = p.index;
      // Apply exponential decay to the blur
      int pre = blurColors[i];
      blurColors[i] = LXColor.multiply(blurColors[i], decayColor, LXColor.BLEND_ALPHA_FULL);

      // Prevent blur decay from sticking, integer multiplication rounding errors
      // can particularly often result in getting stuck on low byte values, e.g. 0x01
      if (blurColors[i] == pre) {
        if (pre != LXColor.BLACK) {
          blurColors[i] = decrementColor(pre);
        }
      }

      // Mix into the new blur buffer frame, use lightest so that blur cannot
      // accumulate brightness beyond what is present in the source material
      blurColors[i] = LXColor.lightest(blurColors[i], this.colors[i], LXColor.BLEND_ALPHA_FULL);
    }

    // If blur value is present, blend the blur value into the color buffer
    if (blurAlpha > 0) {
      switch (this.mode.getEnum()) {
      case MIX:
        for (LXPoint p : model.points) {
          int i = p.index;
          this.colors[i] = LXColor.lerp(this.colors[i], blurColors[i], blurAlpha);
        }
        break;
      case ADD:
        for (LXPoint p : model.points) {
          int i = p.index;
          this.colors[i] = LXColor.add(this.colors[i], blurColors[i], blurAlpha);
        }
        break;
      case SCREEN:
        for (LXPoint p : model.points) {
          int i = p.index;
          this.colors[i] = LXColor.screen(this.colors[i], blurColors[i], blurAlpha);
        }
        break;
      case MULTIPLY:
        for (LXPoint p : model.points) {
          int i = p.index;
          this.colors[i] = LXColor.multiply(this.colors[i], blurColors[i], blurAlpha);
        }
        break;
      case LIGHTEST:
        for (LXPoint p : model.points) {
          int i = p.index;
          this.colors[i] = LXColor.lightest(this.colors[i], blurColors[i], blurAlpha);
        }
        break;
      }
    }
  }

  @Override
  public void dispose() {
    this.blurBuffer.dispose();
    super.dispose();
  }
}
