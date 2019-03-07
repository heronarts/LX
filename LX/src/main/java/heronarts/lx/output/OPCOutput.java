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
import heronarts.lx.model.LXModel;

/**
 * TCP/IP streaming socket implementation of http://openpixelcontrol.org/
 */
public class OPCOutput extends LXSocketOutput implements OPCConstants {

  static final int OFFSET_R = 0;
  static final int OFFSET_G = 1;
  static final int OFFSET_B = 2;

  private final byte[] packetData;

  private final int[] indexBuffer;

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

  public OPCOutput(LX lx, String host, int port, LXModel model) {
    this(lx, host, port, model.toIndexBuffer());
  }

  public OPCOutput(LX lx, String host, int port, int[] pointIndices) {
    super(lx, host, port);
    this.indexBuffer = pointIndices;

    int dataLength = BYTES_PER_PIXEL * pointIndices.length;
    this.packetData = new byte[HEADER_LEN + dataLength];
    this.packetData[OFFSET_CHANNEL] = CHANNEL_BROADCAST;
    this.packetData[OFFSET_COMMAND] = COMMAND_SET_PIXEL_COLORS;
    this.packetData[OFFSET_DATA_LEN_MSB] = (byte)(dataLength >>> 8);
    this.packetData[OFFSET_DATA_LEN_LSB] = (byte)(dataLength & 0xFF);
  }

  @Override
  protected byte[] getPacketData(int[] colors, byte[] glut) {
    for (int i = 0; i < this.indexBuffer.length; ++i) {
      int dataOffset = OFFSET_DATA + i * BYTES_PER_PIXEL;
      int c = colors[this.indexBuffer[i]];
      this.packetData[dataOffset + OFFSET_R] = glut[(0xFF & (c >> 16))];
      this.packetData[dataOffset + OFFSET_G] = glut[(0xFF & (c >> 8))];
      this.packetData[dataOffset + OFFSET_B] = glut[(byte) (0xFF & c)];
    }
    return this.packetData;
  }

  public OPCOutput setChannel(byte channel) {
    this.packetData[OFFSET_CHANNEL] = channel;
    return this;
  }

}
