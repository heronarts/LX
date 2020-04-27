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

import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.transform.LXMatrix;
import heronarts.lx.transform.LXTransform;

public class GridFixture extends LXBasicFixture {

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

  public GridFixture(LX lx) {
    super(lx, "Grid");
    addMetricsParameter("numRows", this.numRows);
    addMetricsParameter("numColumns", this.numColumns);
    addGeometryParameter("rowSpacing", this.rowSpacing);
    addGeometryParameter("columnSpacing", this.columnSpacing);
  }

  @Override
  public Submodel[] toSubmodels() {
    int numRows = this.numRows.getValuei();
    int numColumns = this.numColumns.getValuei();

    int i = 0;
    Submodel[] submodels = new Submodel[numRows + numColumns];
    for (int r = 0; r < numRows; ++r) {
      submodels[i++] = new Submodel(r * numColumns, numColumns, 1, LXModel.Key.STRIP, LXModel.Key.ROW);
    }
    for (int c = 0; c < numColumns; ++c) {
      submodels[i++] = new Submodel(c, numRows, numColumns, LXModel.Key.STRIP, LXModel.Key.COLUMN);
    }
    return submodels;
  }

  @Override
  protected void computePointGeometry(LXMatrix matrix, List<LXPoint> points) {
    LXTransform transform = new LXTransform(matrix);
    int numRows = this.numRows.getValuei();
    int numColumns = this.numColumns.getValuei();
    float rowSpacing = this.rowSpacing.getValuef();
    float columnSpacing = this.columnSpacing.getValuef();
    int pi = 0;
    for (int r = 0; r < numRows; ++r) {
      transform.push();
      for (int c = 0; c < numColumns; ++c) {
        points.get(pi++).set(transform);
        transform.translateX(columnSpacing);
      }
      transform.pop();
      transform.translateY(rowSpacing);
    }
  }

  @Override
  protected int size() {
    return this.numRows.getValuei() * this.numColumns.getValuei();
  }

  @Override
  protected String getModelKey() {
    return LXModel.Key.GRID;
  }

}
