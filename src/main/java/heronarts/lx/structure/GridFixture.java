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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.transform.LXMatrix;
import heronarts.lx.transform.LXTransform;

@LXCategory(LXCategory.CORE)
public class GridFixture extends LXBasicFixture {

  public enum PositionMode {
    CORNER("Corner"),
    CENTER("Center");

    private final String str;

    PositionMode(String str) {
      this.str = str;
    }

    @Override
    public String toString() {
      return this.str;
    }
  }

  public enum Wiring {
    ROWS_L2R_B2T("Rows - Left→Right - Bot→Top"),
    ROWS_L2R_T2B("Rows - Left→Right - Top→Bot"),
    ROWS_R2L_B2T("Rows - Right→Left - Bot→Top"),
    ROWS_R2L_T2B("Rows - Right→Left - Top→Bot"),

    COLUMNS_B2T_L2R("Cols - Bot→Top - Left→Right"),
    COLUMNS_B2T_R2L("Cols - Bot→Top - Right→Left"),
    COLUMNS_T2B_L2R("Cols - Top→Bot - Left→Right"),
    COLUMNS_T2B_R2L("Cols - Top→Bot - Right→Left"),

    ZIGZAG_HORIZ_BL("ZigZag - Horiz - Bot Left"),
    ZIGZAG_HORIZ_TL("ZigZag - Horiz - Top Left"),
    ZIGZAG_HORIZ_BR("ZigZag - Horiz - Bot Right"),
    ZIGZAG_HORIZ_TR("ZigZag - Horiz - Top Right"),

    ZIGZAG_VERT_BL("ZigZag - Vert - Bot Left"),
    ZIGZAG_VERT_TL("ZigZag - Vert - Top Left"),
    ZIGZAG_VERT_BR("ZigZag - Vert - Bot Right"),
    ZIGZAG_VERT_TR("ZigZag - Vert - Top Right");

    private final String description;

    Wiring(String description) {
      this.description = description;
    }

    @Override
    public String toString() {
      return this.description;
    }
  }

  public final DiscreteParameter numRows =
    new DiscreteParameter("Rows", 10, 1, 1025)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("Number of rows in the grid");

  public final DiscreteParameter numColumns =
    new DiscreteParameter("Columns", 10, 1, 1025)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("Number of columns in the grid");

  public final BoundedParameter rowSpacing =
    new BoundedParameter("Row Spacing", 10, 0, 1000000)
    .setDescription("Spacing between rows in the grid");

  public final BoundedParameter columnSpacing =
    new BoundedParameter("Column Spacing", 10, 0, 1000000)
    .setDescription("Spacing between columns in the grid");

  public final EnumParameter<PositionMode> positionMode =
    new EnumParameter<PositionMode>("Mode", PositionMode.CORNER)
    .setDescription("Whether the arc is positioned by its starting point or center");

  public final EnumParameter<Wiring> wiring =
    new EnumParameter<Wiring>("Wiring", Wiring.ROWS_L2R_B2T)
    .setDescription("How the strips in the grid are sequentially wired");

  public final StringParameter rowTags =
    new StringParameter("Row Tags", LXModel.Tag.ROW)
    .setDescription("Tags to be applied to rows in model");

  public final StringParameter columnTags =
    new StringParameter("Column Tags", LXModel.Tag.COLUMN)
    .setDescription("Tags to be applied to columns in model");

  public GridFixture(LX lx) {
    super(lx, "Grid");
    addMetricsParameter("numRows", this.numRows);
    addMetricsParameter("numColumns", this.numColumns);
    addGeometryParameter("rowSpacing", this.rowSpacing);
    addGeometryParameter("columnSpacing", this.columnSpacing);
    addGeometryParameter("positionMode", this.positionMode);
    addOutputParameter("wiring", this.wiring);
    addTagParameter("rowTags", this.rowTags);
    addTagParameter("columnTags", this.columnTags);
  }

  private String[] tagArray(StringParameter parameter) {
    List<String> validTags = new ArrayList<String>();
    String tagString = parameter.getString();
    if ((tagString != null) && !tagString.isEmpty()) {
      for (String tag : tagString.trim().split("\\s+")) {
        tag = tag.trim();
        if (!tag.isEmpty() && LXModel.Tag.isValid(tag)) {
          validTags.add(tag);
        }
      }
    }
    return validTags.toArray(new String[0]);
  }

  @Override
  public Submodel[] toSubmodels() {
    final int numRows = this.numRows.getValuei();
    final int numColumns = this.numColumns.getValuei();

    int i = 0;
    final Submodel[] submodels = new Submodel[numRows + numColumns];

    final Map<String, String> metaData = new HashMap<String, String>();
    metaData.put("numPoints", String.valueOf(numColumns));
    metaData.put("spacing", String.valueOf(this.columnSpacing.getValue()));

    final String[] rowTags = tagArray(this.rowTags);
    final String[] columnTags = tagArray(this.columnTags);

    for (int r = 0; r < numRows; ++r) {
      metaData.put("rowIndex", String.valueOf(r));
      submodels[i] = new Submodel(r * numColumns, numColumns, 1, metaData, rowTags);
      submodels[i].transform.translate(0, r * this.rowSpacing.getValuef(), 0);
      ++i;
    }

    metaData.clear();
    metaData.put("numPoints", String.valueOf(numRows));
    metaData.put("spacing", String.valueOf(this.rowSpacing.getValue()));
    for (int c = 0; c < numColumns; ++c) {
      metaData.put("columnIndex", String.valueOf(c));
      submodels[i] = new Submodel(c, numRows, numColumns, metaData, columnTags);
      submodels[i].transform.translate(c * this.columnSpacing.getValuef(), 0, 0);
      ++i;
    }

    return submodels;
  }

  @Override
  protected void computePointGeometry(LXMatrix matrix, List<LXPoint> points) {
    if (this.positionMode.getEnum() == PositionMode.CENTER) {
      matrix.translate(
        -.5f * (this.numColumns.getValuei() - 1) * this.columnSpacing.getValuef(),
        -.5f * (this.numRows.getValuei() - 1) * this.rowSpacing.getValuef(),
        0f
      );
    }

    // We create the points from left-to-right (increasing X), bottom-to-top (increasing Y)
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
  protected String[] getDefaultTags() {
    return new String[] { LXModel.Tag.GRID };
  }

  private int[] getWiringIndexBuffer() {
    int size = size();
    int numRows = this.numRows.getValuei();
    int numColumns = this.numColumns.getValuei();

    int[] indexBuffer = new int[size];
    int i = 0;

    // Note: this code could certainly be more clever and less repetitive,
    // but erring on the side of transparency here.
    switch (this.wiring.getEnum()) {
    case COLUMNS_B2T_L2R:
      for (int x = 0; x < numColumns; ++x) {
        for (int y = 0; y < numRows; ++y) {
          indexBuffer[i++] = x + y * numColumns;
        }
      }
      break;
    case COLUMNS_B2T_R2L:
      for (int x = 0; x < numColumns; ++x) {
        for (int y = numRows - 1; y >= 0; --y) {
          indexBuffer[i++] = x + y * numColumns;
        }
      }
      break;
    case COLUMNS_T2B_L2R:
      for (int x = numColumns - 1; x >= 0; --x) {
        for (int y = 0; y < numRows; ++y) {
          indexBuffer[i++] = x + y * numColumns;
        }
      }
      break;
    case COLUMNS_T2B_R2L:
      for (int x = numColumns - 1; x >= 0; --x) {
        for (int y = numRows - 1; y >= 0; --y) {
          indexBuffer[i++] = x + y * numColumns;
        }
      }
      break;
    case ROWS_L2R_B2T:
      for (int y = 0; y < numRows; ++y) {
        for (int x = 0; x < numColumns; ++x) {
          indexBuffer[i++] = x + y * numColumns;
        }
      }
      break;
    case ROWS_L2R_T2B:
      for (int y = numRows - 1; y >= 0; --y) {
        for (int x = 0; x < numColumns; ++x) {
          indexBuffer[i++] = x + y * numColumns;
        }
      }
      break;
    case ROWS_R2L_B2T:
      for (int y = 0; y < numRows; ++y) {
        for (int x = numColumns - 1; x >= 0; --x) {
          indexBuffer[i++] = x + y * numColumns;
        }
      }
      break;
    case ROWS_R2L_T2B:
      for (int y = numRows - 1; y >= 0; --y) {
        for (int x = numColumns - 1; x >= 0; --x) {
          indexBuffer[i++] = x + y * numColumns;
        }
      }
      break;
    case ZIGZAG_HORIZ_BL:
      for (int y = 0; y < numRows; ++y) {
        if (y % 2 == 0) {
          for (int x = 0; x < numColumns; ++x) {
            indexBuffer[i++] = x + y * numColumns;
          }
        } else {
          for (int x = numColumns - 1; x >= 0; --x) {
            indexBuffer[i++] = x + y * numColumns;
          }
        }
      }
      break;
    case ZIGZAG_HORIZ_BR:
      for (int y = 0; y < numRows; ++y) {
        if (y % 2 != 0) {
          for (int x = 0; x < numColumns; ++x) {
            indexBuffer[i++] = x + y * numColumns;
          }
        } else {
          for (int x = numColumns - 1; x >= 0; --x) {
            indexBuffer[i++] = x + y * numColumns;
          }
        }
      }
      break;
    case ZIGZAG_HORIZ_TL:
      for (int y = numRows - 1; y >= 0; --y) {
        if ((y % 2) != (numRows % 2)) {
          for (int x = 0; x < numColumns; ++x) {
            indexBuffer[i++] = x + y * numColumns;
          }
        } else {
          for (int x = numColumns - 1; x >= 0; --x) {
            indexBuffer[i++] = x + y * numColumns;
          }
        }
      }
      break;
    case ZIGZAG_HORIZ_TR:
      for (int y = numRows - 1; y >= 0; --y) {
        if ((y % 2) == (numRows % 2)) {
          for (int x = 0; x < numColumns; ++x) {
            indexBuffer[i++] = x + y * numColumns;
          }
        } else {
          for (int x = numColumns - 1; x >= 0; --x) {
            indexBuffer[i++] = x + y * numColumns;
          }
        }
      }
      break;
    case ZIGZAG_VERT_BL:
      for (int x = 0; x < numColumns; ++x) {
        if (x % 2 == 0) {
          for (int y = 0; y < numRows; ++y) {
            indexBuffer[i++] = x + y * numColumns;
          }
        } else {
          for (int y = numRows - 1; y >= 0; --y) {
            indexBuffer[i++] = x + y * numColumns;
          }
        }
      }
      break;
    case ZIGZAG_VERT_BR:
      for (int x = numColumns - 1; x >= 0; --x) {
        if ((x % 2) != (numColumns % 2)) {
          for (int y = 0; y < numRows; ++y) {
            indexBuffer[i++] = x + y * numColumns;
          }
        } else {
          for (int y = numRows - 1; y >= 0; --y) {
            indexBuffer[i++] = x + y * numColumns;
          }
        }
      }
      break;
    case ZIGZAG_VERT_TL:
      for (int x = 0; x < numColumns; ++x) {
        if (x % 2 == 0) {
          for (int y = numRows - 1; y >= 0; --y) {
            indexBuffer[i++] = x + y * numColumns;
          }
        } else {
          for (int y = 0; y < numRows; ++y) {
            indexBuffer[i++] = x + y * numColumns;
          }
        }
      }
      break;
    case ZIGZAG_VERT_TR:
      for (int x = numColumns - 1; x >= 0; --x) {
        if ((x % 2) == (numColumns % 2)) {
          for (int y = 0; y < numRows; ++y) {
            indexBuffer[i++] = x + y * numColumns;
          }
        } else {
          for (int y = numRows - 1; y >= 0; --y) {
            indexBuffer[i++] = x + y * numColumns;
          }
        }
      }
      break;
    default:
      throw new IllegalStateException("Grid Wiring has non-existed enum value: " + this.wiring);
    }
    return indexBuffer;
  }

  @Override
  protected Segment buildSegment() {
    return new Segment(getWiringIndexBuffer(), this.byteOrder.getEnum());
  }

  @Override
  public void addModelMetaData(Map<String, String> metaData) {
    metaData.put("numRows", String.valueOf(this.numRows.getValuei()));
    metaData.put("numColumns", String.valueOf(this.numColumns.getValuei()));
    metaData.put("rowSpacing", String.valueOf(this.rowSpacing.getValue()));
    metaData.put("columnSpacing", String.valueOf(this.columnSpacing.getValue()));
    metaData.put("positionMode", this.positionMode.getEnum().toString());

  }

}
