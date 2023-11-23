/**
 * Copyright 2023- Justin K. Belcher, Mark C. Slee, Heron Arts LLC
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
 * @author Justin K. Belcher <justin@jkb.studio>
 */

package heronarts.lx.dmx;

import heronarts.lx.LXCategory;
import heronarts.lx.color.ColorParameter;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXDynamicColor;
import heronarts.lx.color.LXPalette;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;

/**
 * Extracts a color from three DMX channels starting at a given address.
 */
@LXModulator.Global("DMX Color")
@LXModulator.Device("DMX Color")
@LXCategory(LXCategory.DMX)
public class DmxColorModulator extends AbstractDmxModulator implements LXOscComponent {

  public final EnumParameter<LXDmxEngine.ByteOrder> byteOrder =
    new EnumParameter<LXDmxEngine.ByteOrder>("Byte Order", LXDmxEngine.ByteOrder.RGB);

  public final BooleanParameter updatePalette =
    new BooleanParameter("Palette", false)
    .setDescription("Updates the global palette's active swatch with the DMX color");

  public final DiscreteParameter paletteIndex =
    new LXPalette.IndexSelector("Index")
    .setDescription("Target index in the global palette's active swatch");

  public final BooleanParameter setPaletteFixed =
    new BooleanParameter("Fixed", true)
    .setDescription("When sending DMX color to the palette, also set the target color mode to Fixed");

  public final ColorParameter color =
    new ColorParameter("Color", LXColor.BLACK)
    .setDescription("Color received by DMX");

  public DmxColorModulator() {
    this("DMX Color");
  }

  public DmxColorModulator(String label) {
    super(label, 3);
    addParameter("byteOrder", this.byteOrder);
    addParameter("updatePalette", this.updatePalette);
    addParameter("paletteIndex", this.paletteIndex);
    addParameter("setPaletteFixed", this.setPaletteFixed);
    addParameter("color", this.color);
    setMappingSource(false);
  }

  @Override
  protected double computeValue(double deltaMs) {
    final LXDmxEngine.ByteOrder byteOrder = this.byteOrder.getEnum();

    final int color = this.lx.engine.dmx.getColor(
      this.universe.getValuei(),
      this.channel.getValuei(),
      byteOrder
    );

    // Store color locally for preview
    this.color.setColor(color);

    // Send to target color in global palette
    if (this.updatePalette.isOn()) {
      final int index = this.paletteIndex.getValuei() - 1;
      while (this.lx.engine.palette.swatch.colors.size() <= index) {
        this.lx.engine.palette.swatch.addColor().primary.setColor(LXColor.BLACK);
      }
      this.lx.engine.palette.swatch.getColor(index).primary.setColor(color);
      if (this.setPaletteFixed.isOn()) {
        this.lx.engine.palette.swatch.getColor(index).mode.setValue(LXDynamicColor.Mode.FIXED);
      }
    }

    return this.color.getValue();
  }

  public int getColor() {
    return this.color.getColor();
  }

}
