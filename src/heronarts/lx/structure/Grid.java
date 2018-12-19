/**
 * Copyright 2018- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.structure;

import heronarts.lx.LX;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.transform.LXTransform;

public class Grid extends LXFixture {

  public final DiscreteParameter numRows = (DiscreteParameter)
    new DiscreteParameter("Rows", 10, 1, 1025)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("Number of rows in the grid");

  public final DiscreteParameter numColumns = (DiscreteParameter)
    new DiscreteParameter("Columns", 10, 1, 1025)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("Number of columns in the grid");

  public final BoundedParameter rowSpacing =
    new BoundedParameter("Row Spacing", 10, 0, 1000000)
    .setDescription("Spacing between rows in the grid");

  public final BoundedParameter columnSpacing =
    new BoundedParameter("Column Spacing", 10, 0, 1000000)
    .setDescription("Spacing between columns in the grid");

  public Grid(LX lx) {
    super(lx);
    addGeometryParameter("numRows", this.numRows);
    addGeometryParameter("numColumns", this.numColumns);
    addGeometryParameter("rowSpacing", this.rowSpacing);
    addGeometryParameter("columnSpacing", this.columnSpacing);
  }

  @Override
  protected void generatePoints(LXTransform transform) {
    int numRows = this.numRows.getValuei();
    int numColumns = this.numColumns.getValuei();
    float rowSpacing = this.rowSpacing.getValuef();
    float columnSpacing = this.columnSpacing.getValuef();
    for (int r = 0; r < numRows; ++r) {
      transform.push();
      for (int c = 0; c < numColumns; ++c) {
        addPoint(transform);
        transform.translate(columnSpacing, 0);
      }
      transform.pop();
      transform.translate(0, rowSpacing);
    }
  }

}
