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
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;

/**
 * Maps a sequential set of DMX channels onto a 2D grid of size [rows x columns]
 */
@LXModulator.Global("DMX Grid")
@LXModulator.Device("DMX Grid")
@LXCategory(LXCategory.DMX)
public class DmxGridModulator extends DmxModulator {

  public final DiscreteParameter rows =
    new DiscreteParameter("Rows", 1, 100)
    .setDescription("Number of rows in the grid");

  public final DiscreteParameter columns =
    new DiscreteParameter("Columns", 1, 100)
    .setDescription("Number of columns in the grid");

  private int[][] values;

  public DmxGridModulator() {
    this("DMX Grid");
  }

  public DmxGridModulator(String label) {
    super(label);
    addParameter("rows", this.rows);
    addParameter("columns", this.columns);
    resize();
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.rows || p == this.columns) {
      resize();
      if (this.lx != null) {
        readDmx();
      }
    }
  }

  private void resize() {
    final int rows = this.rows.getValuei();
    final int columns = this.columns.getValuei();
    this.values = new int[rows][columns];
  }

  @Override
  protected double computeValue(double deltaMs) {
    return readDmx();
  }

  /**
   * Retrieve DMX input values and store in 2D array
   *
   * @return average normalized value
   */
  private double readDmx() {
    int univ = this.universe.getValuei();
    int ch = this.channel.getValuei();
    final int rows = this.rows.getValuei();
    final int columns = this.columns.getValuei();

    int r = 0, c = 0, sum = 0;
    final int resolution = rows * columns;
    for (int i = 0; i < resolution; i++) {
      this.values[r][c] = this.lx.engine.dmx.getValuei(univ, ch);
      sum += this.values[r][c];

      // Left->Right, Top->Bottom, wrap
      if (++c >= columns) {
        c = 0;
        r++;
      }

      // DMX data is assumed to wrap sequentially onto following universes
      if (++ch >= LXDmxEngine.MAX_CHANNEL) {
        ch = 0;
        if (++univ >= LXDmxEngine.MAX_UNIVERSE) {
          // Grid did not fit within ArtNet data
          break;
        }
      }
    }

    return sum / (double)resolution / 255.;
  }

  public int getValue(int row, int column) {
    return this.values[row][column];
  }

}
