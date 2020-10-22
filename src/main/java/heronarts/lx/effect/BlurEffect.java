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
import heronarts.lx.ModelBuffer;
import heronarts.lx.color.LXColor;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;

@LXCategory(LXCategory.CORE)
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
    new CompoundParameter("Level", .5f)
    .setDescription("Sets the level of the blur relative to original signal");

  public final CompoundParameter decay = (CompoundParameter)
    new CompoundParameter("Decay", 1, 0.01, 60)
    .setDescription("Sets the decay of the motion blur, time to half brightness")
    .setExponent(3)
    .setUnits(CompoundParameter.Units.SECONDS);

  public final EnumParameter<Mode> mode =
    new EnumParameter<Mode>("Mode", Mode.MIX)
    .setDescription("Which blending mode the blur uses");

  private final ModelBuffer blurBuffer;

  public BlurEffect(LX lx) {
    super(lx);
    this.blurBuffer = new ModelBuffer(lx);
    int[] blurArray = blurBuffer.getArray();
    for (int i = 0; i < blurArray.length; ++i) {
      blurArray[i] = LXColor.BLACK;
    }
    addParameter("level", this.level);
    addParameter("decay", this.decay);
    addParameter("mode", this.mode);
  }

  @Override
  protected void onEnable() {
    int[] blurArray = this.blurBuffer.getArray();
    for (int i = 0; i < blurArray.length; ++i) {
      blurArray[i] = LXColor.BLACK;
    }
  }

  @Override
  public void run(double deltaMs, double amount) {
    float blurf = (float) (amount * this.level.getValuef());
    int[] blurColors = this.blurBuffer.getArray();

    double decayFactor = Math.pow(.5, deltaMs / (1000 * this.decay.getValue()));
    int decayColor = LXColor.gray(decayFactor * 100);

    for (int i = 0; i < blurColors.length; ++i) {
      // Apply exponential decay to the blur
      blurColors[i] = LXColor.multiply(blurColors[i], decayColor, 0x100);
      // Add the new blur buffer frame
      blurColors[i] = LXColor.add(blurColors[i], this.colors[i], 0x100);
    }

    // If blur value is present, blend the blur value into the color buffer
    if (blurf > 0) {
      int blurAlpha = (int) (0x100 * blurf);
      switch (this.mode.getEnum()) {
      case MIX:
        for (int i = 0; i < blurColors.length; ++i) {
          this.colors[i] = LXColor.lerp(this.colors[i], blurColors[i], blurAlpha);
        }
        break;
      case ADD:
        for (int i = 0; i < blurColors.length; ++i) {
          this.colors[i] = LXColor.add(this.colors[i], blurColors[i], blurAlpha);
        }
        break;
      case SCREEN:
        for (int i = 0; i < blurColors.length; ++i) {
          this.colors[i] = LXColor.screen(this.colors[i], blurColors[i], blurAlpha);
        }
        break;
      case MULTIPLY:
        for (int i = 0; i < blurColors.length; ++i) {
          this.colors[i] = LXColor.multiply(this.colors[i], blurColors[i], blurAlpha);
        }
        break;
      case LIGHTEST:
        for (int i = 0; i < blurColors.length; ++i) {
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
