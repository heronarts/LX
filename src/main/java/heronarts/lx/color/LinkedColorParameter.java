/**
 * Copyright 2022- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.color;

import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;

public class LinkedColorParameter extends ColorParameter {

  public enum Mode {
    STATIC("Static"),
    PALETTE("Palette");

    public final String label;

    private Mode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  };

  public final EnumParameter<Mode> mode =
    new EnumParameter<Mode>("Mode", Mode.STATIC)
    .setDescription("Whether to use a custom color or a fixed palette swatch index");

  public final LXPalette.IndexSelector index = new LXPalette.IndexSelector("Index");

  public LinkedColorParameter(String label) {
    this(label, 0xff000000);
  }

  public LinkedColorParameter(String label, int color) {
    super(label, color);
    addSubparameter("mode", this.mode);
    addSubparameter("index", this.index);
  }

  @Override
  public LinkedColorParameter setDescription(String description) {
    return (LinkedColorParameter) super.setDescription(description);
  }

  public LinkedColorParameter setMode(Mode mode) {
    this.mode.setValue(mode);
    return this;
  }

  public LinkedColorParameter setIndex(int index) {
    this.index.setValue(index);
    return this;
  }

  public LXDynamicColor getPaletteColor() {
    return getParent().getLX().engine.palette.swatch.getColor(this.index.getValuei() - 1);
  }

  @Override
  protected void onSubparameterUpdate(LXParameter p) {
    if (this.mode.getEnum() == Mode.PALETTE) {
      setColor(getPaletteColor().getColor());
    } else {
      super.onSubparameterUpdate(p);
    }
  }

  // Returns the real-time value of the color, which may be different from what
  // getColor() returns if there are LFOs/etc being applied.
  @Override
  public int calcColor() {
    switch (this.mode.getEnum()) {
    case PALETTE:
      return this.getPaletteColor().getColor();
    default:
    case STATIC:
      return super.calcColor();
    }
  }

  @Override
  public int getColor() {
    switch (this.mode.getEnum()) {
    case PALETTE:
      return getPaletteColor().getColor();
    default:
    case STATIC:
      return super.getColor();
    }
  }

}
