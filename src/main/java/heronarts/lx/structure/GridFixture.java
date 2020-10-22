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

import java.net.InetAddress;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.output.ArtNetDatagram;
import heronarts.lx.output.DDPDatagram;
import heronarts.lx.output.KinetDatagram;
import heronarts.lx.output.LXOutput;
import heronarts.lx.output.OPCDatagram;
import heronarts.lx.output.OPCOutput;
import heronarts.lx.output.OPCSocket;
import heronarts.lx.output.StreamingACNDatagram;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.transform.LXMatrix;
import heronarts.lx.transform.LXTransform;

public class GridFixture extends LXProtocolFixture {

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

  public final DiscreteParameter numRows = (DiscreteParameter)
    new DiscreteParameter("Rows", 10, 1, 257)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("Number of rows in the grid");

  public final DiscreteParameter numColumns = (DiscreteParameter)
    new DiscreteParameter("Columns", 10, 1, 257)
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

  public final BooleanParameter splitPacket =
    new BooleanParameter("Split Packet", false)
    .setDescription("Whether to break a large grid into multiple datagrams on separate channels");

  public final DiscreteParameter pointsPerPacket =
    new DiscreteParameter("Points Per Packet", 170, 1, 21845)
    .setDescription("Number of LED points per packet");

  public GridFixture(LX lx) {
    super(lx, "Grid");
    addParameter("host", this.host);
    addParameter("port", this.port);
    addOutputParameter("protocol", this.protocol);
    addOutputParameter("artNetUniverse", this.artNetUniverse);
    addOutputParameter("opcChannel", this.opcChannel);
    addOutputParameter("ddpDataOffset", this.ddpDataOffset);
    addOutputParameter("kinetPort", this.kinetPort);

    addMetricsParameter("numRows", this.numRows);
    addMetricsParameter("numColumns", this.numColumns);
    addGeometryParameter("rowSpacing", this.rowSpacing);
    addGeometryParameter("columnSpacing", this.columnSpacing);
    addGeometryParameter("positionMode", this.positionMode);
    addOutputParameter("wiring", this.wiring);
    addOutputParameter("splitPacket", this.splitPacket);
    addOutputParameter("pointsPerPacket", this.pointsPerPacket);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.host) {
      InetAddress address = resolveHostAddress();
      for (LXOutput output : this.outputs) {
        if (output instanceof LXOutput.InetOutput) {
          output.enabled.setValue(address != null);
          if (address != null) {
            ((LXOutput.InetOutput) output).setAddress(address);
          }
        }
      }
    } else if (p == this.port) {
      for (LXOutput output : this.outputs) {
        if (output instanceof OPCOutput) {
          ((OPCOutput) output).setPort(this.port.getValuei());
        }
      }
    }
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
  protected String getModelKey() {
    return LXModel.Key.GRID;
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
          indexBuffer[i++] = this.points.get(x + y * numColumns).index;
        }
      }
      break;
    case COLUMNS_B2T_R2L:
      for (int x = 0; x < numColumns; ++x) {
        for (int y = numRows - 1; y >= 0; --y) {
          indexBuffer[i++] = this.points.get(x + y * numColumns).index;
        }
      }
      break;
    case COLUMNS_T2B_L2R:
      for (int x = numColumns - 1; x >= 0; --x) {
        for (int y = 0; y < numRows; ++y) {
          indexBuffer[i++] = this.points.get(x + y * numColumns).index;
        }
      }
      break;
    case COLUMNS_T2B_R2L:
      for (int x = numColumns - 1; x >= 0; --x) {
        for (int y = numRows - 1; y >= 0; --y) {
          indexBuffer[i++] = this.points.get(x + y * numColumns).index;
        }
      }
      break;
    case ROWS_L2R_B2T:
      for (int y = 0; y < numRows; ++y) {
        for (int x = 0; x < numColumns; ++x) {
          indexBuffer[i++] = this.points.get(x + y * numColumns).index;
        }
      }
      break;
    case ROWS_L2R_T2B:
      for (int y = numRows - 1; y >= 0; --y) {
        for (int x = 0; x < numColumns; ++x) {
          indexBuffer[i++] = this.points.get(x + y * numColumns).index;
        }
      }
      break;
    case ROWS_R2L_B2T:
      for (int y = 0; y < numRows; ++y) {
        for (int x = numColumns - 1; x >= 0; --x) {
          indexBuffer[i++] = this.points.get(x + y * numColumns).index;
        }
      }
      break;
    case ROWS_R2L_T2B:
      for (int y = numRows - 1; y >= 0; --y) {
        for (int x = numColumns - 1; x >= 0; --x) {
          indexBuffer[i++] = this.points.get(x + y * numColumns).index;
        }
      }
      break;
    case ZIGZAG_HORIZ_BL:
      for (int y = 0; y < numRows; ++y) {
        if (y % 2 == 0) {
          for (int x = 0; x < numColumns; ++x) {
            indexBuffer[i++] = this.points.get(x + y * numColumns).index;
          }
        } else {
          for (int x = numColumns - 1; x >= 0; --x) {
            indexBuffer[i++] = this.points.get(x + y * numColumns).index;
          }
        }
      }
      break;
    case ZIGZAG_HORIZ_BR:
      for (int y = 0; y < numRows; ++y) {
        if (y % 2 != 0) {
          for (int x = 0; x < numColumns; ++x) {
            indexBuffer[i++] = this.points.get(x + y * numColumns).index;
          }
        } else {
          for (int x = numColumns - 1; x >= 0; --x) {
            indexBuffer[i++] = this.points.get(x + y * numColumns).index;
          }
        }
      }
      break;
    case ZIGZAG_HORIZ_TL:
      for (int y = numRows - 1; y >= 0; --y) {
        if ((y % 2) != (numRows % 2)) {
          for (int x = 0; x < numColumns; ++x) {
            indexBuffer[i++] = this.points.get(x + y * numColumns).index;
          }
        } else {
          for (int x = numColumns - 1; x >= 0; --x) {
            indexBuffer[i++] = this.points.get(x + y * numColumns).index;
          }
        }
      }
      break;
    case ZIGZAG_HORIZ_TR:
      for (int y = numRows - 1; y >= 0; --y) {
        if ((y % 2) == (numRows % 2)) {
          for (int x = 0; x < numColumns; ++x) {
            indexBuffer[i++] = this.points.get(x + y * numColumns).index;
          }
        } else {
          for (int x = numColumns - 1; x >= 0; --x) {
            indexBuffer[i++] = this.points.get(x + y * numColumns).index;
          }
        }
      }
      break;
    case ZIGZAG_VERT_BL:
      for (int x = 0; x < numColumns; ++x) {
        if (x % 2 == 0) {
          for (int y = 0; y < numRows; ++y) {
            indexBuffer[i++] = this.points.get(x + y * numColumns).index;
          }
        } else {
          for (int y = numRows - 1; y >= 0; --y) {
            indexBuffer[i++] = this.points.get(x + y * numColumns).index;
          }
        }
      }
      break;
    case ZIGZAG_VERT_BR:
      for (int x = numColumns - 1; x >= 0; --x) {
        if ((x % 2) != (numColumns % 2)) {
          for (int y = 0; y < numRows; ++y) {
            indexBuffer[i++] = this.points.get(x + y * numColumns).index;
          }
        } else {
          for (int y = numRows - 1; y >= 0; --y) {
            indexBuffer[i++] = this.points.get(x + y * numColumns).index;
          }
        }
      }
      break;
    case ZIGZAG_VERT_TL:
      for (int x = 0; x < numColumns; ++x) {
        if (x % 2 == 0) {
          for (int y = numRows - 1; y >= 0; --y) {
            indexBuffer[i++] = this.points.get(x + y * numColumns).index;
          }
        } else {
          for (int y = 0; y < numRows; ++y) {
            indexBuffer[i++] = this.points.get(x + y * numColumns).index;
          }
        }
      }
      break;
    case ZIGZAG_VERT_TR:
      for (int x = numColumns - 1; x >= 0; --x) {
        if ((x % 2) == (numColumns % 2)) {
          for (int y = 0; y < numRows; ++y) {
            indexBuffer[i++] = this.points.get(x + y * numColumns).index;
          }
        } else {
          for (int y = numRows - 1; y >= 0; --y) {
            indexBuffer[i++] = this.points.get(x + y * numColumns).index;
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
  protected void buildOutputs() {
    Protocol protocol = this.protocol.getEnum();
    if (protocol == Protocol.NONE) {
      return;
    }
    InetAddress address = resolveHostAddress();
    int[] wiringIndexBuffer = getWiringIndexBuffer();
    int pointsPerPacket = this.pointsPerPacket.getValuei();
    if (this.splitPacket.isOn() && (wiringIndexBuffer.length > pointsPerPacket)) {
      int i = 0;
      int channel = getProtocolChannel();
      while (i < wiringIndexBuffer.length) {
        int chunkSize = Math.min(pointsPerPacket, wiringIndexBuffer.length - i);
        int chunkIndexBuffer[] = new int[chunkSize];
        System.arraycopy(wiringIndexBuffer, i, chunkIndexBuffer, 0, chunkSize);
        addOutput(address, chunkIndexBuffer, channel++);
        i += chunkSize;
      }
    } else {
      addOutput(address, wiringIndexBuffer, getProtocolChannel());
    }
  }

  private void addOutput(InetAddress address, int[] indexBuffer, int channel) {
    LXOutput output = null;
    switch (this.protocol.getEnum()) {
    case ARTNET:
      output = new ArtNetDatagram(this.lx, indexBuffer, channel);
      break;
    case SACN:
      output = new StreamingACNDatagram(this.lx, indexBuffer, channel);
      break;
    case DDP:
      output = new DDPDatagram(this.lx, indexBuffer, channel);
      break;
    case KINET:
      output = new KinetDatagram(this.lx, indexBuffer, channel);
      break;
    case OPC:
      switch (this.transport.getEnum()) {
      case TCP:
        output = new OPCSocket(this.lx, toDynamicIndexBuffer(), (byte) channel);
        break;
      default:
      case UDP:
        output = new OPCDatagram(this.lx, toDynamicIndexBuffer(), (byte) channel);
        break;
      }
      ((OPCOutput) output).setPort(this.port.getValuei());
      break;
    default:
      LX.error("Undefined output protocol in GridFixture: " + this.protocol.getEnum());
      break;
    }
    if (output != null) {
      output.enabled.setValue(address != null);
      if (address != null && (output instanceof LXOutput.InetOutput)) {
        ((LXOutput.InetOutput) output).setAddress(address);
      }
      addOutput(output);
    }
  }

  @Override
  protected void reindexOutputs() {

  }

}
