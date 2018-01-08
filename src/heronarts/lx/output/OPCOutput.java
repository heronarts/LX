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

package heronarts.lx.output;

import heronarts.lx.LX;
import heronarts.lx.model.LXFixture;

/**
 * TCP/IP streaming socket implementation of http://openpixelcontrol.org/
 */
public class OPCOutput extends LXSocketOutput implements OPCConstants {

  static final int OFFSET_R = 0;
  static final int OFFSET_G = 1;
  static final int OFFSET_B = 2;

  private final byte[] packetData;

  private final int[] pointIndices;

  private static int[] allPoints(LX lx) {
    int[] points = new int[lx.total];
    for (int i = 0; i < points.length; ++i) {
      points[i] = i;
    }
    return points;
  }

  public OPCOutput(LX lx, String host, int port) {
    this(lx, host, port, allPoints(lx));
  }

  public OPCOutput(LX lx, String host, int port, LXFixture fixture) {
    this(lx, host, port, LXOutput.fixtureToIndices(fixture));
  }

  public OPCOutput(LX lx, String host, int port, int[] pointIndices) {
    super(lx, host, port);
    this.pointIndices = pointIndices;

    int dataLength = BYTES_PER_PIXEL * pointIndices.length;
    this.packetData = new byte[HEADER_LEN + dataLength];
    this.packetData[INDEX_CHANNEL] = CHANNEL_BROADCAST;
    this.packetData[INDEX_COMMAND] = COMMAND_SET_PIXEL_COLORS;
    this.packetData[INDEX_DATA_LEN_MSB] = (byte)(dataLength >>> 8);
    this.packetData[INDEX_DATA_LEN_LSB] = (byte)(dataLength & 0xFF);
  }

  @Override
  protected byte[] getPacketData(int[] colors) {
    for (int i = 0; i < this.pointIndices.length; ++i) {
      int dataOffset = INDEX_DATA + i * BYTES_PER_PIXEL;
      int c = colors[this.pointIndices[i]];
      this.packetData[dataOffset + OFFSET_R] = (byte) (0xFF & (c >> 16));
      this.packetData[dataOffset + OFFSET_G] = (byte) (0xFF & (c >> 8));
      this.packetData[dataOffset + OFFSET_B] = (byte) (0xFF & c);
    }
    return this.packetData;
  }

  public OPCOutput setChannel(byte channel) {
    this.packetData[INDEX_CHANNEL] = channel;
    return this;
  }

}
