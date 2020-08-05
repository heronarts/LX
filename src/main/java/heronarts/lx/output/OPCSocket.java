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
public class OPCSocket extends LXSocket implements OPCOutput {

  static final int OFFSET_R = 0;
  static final int OFFSET_G = 1;
  static final int OFFSET_B = 2;

  private final byte[] packetData;

  public OPCSocket(LX lx) {
    this(lx, lx.getModel());
  }

  public OPCSocket(LX lx, LXModel model) {
    this(lx, model.toIndexBuffer());
  }

  public OPCSocket(LX lx, int[] indexBuffer) {
    this(lx, indexBuffer, ByteOrder.RGB, CHANNEL_BROADCAST);
  }

  public OPCSocket(LX lx, int[] indexBuffer, byte channel) {
    this(lx, indexBuffer, ByteOrder.RGB, channel);
  }

  public OPCSocket(LX lx, int[] indexBuffer, ByteOrder byteOrder) {
    this(lx, indexBuffer, byteOrder, CHANNEL_BROADCAST);
  }

  public OPCSocket(LX lx, int[] indexBuffer, ByteOrder byteOrder, byte channel) {
    super(lx, indexBuffer, byteOrder);

    int dataLength = byteOrder.getNumBytes() * indexBuffer.length;
    this.packetData = new byte[HEADER_LEN + dataLength];
    this.packetData[OFFSET_CHANNEL] = channel;
    this.packetData[OFFSET_COMMAND] = COMMAND_SET_PIXEL_COLORS;
    this.packetData[OFFSET_DATA_LEN_MSB] = (byte)(dataLength >>> 8);
    this.packetData[OFFSET_DATA_LEN_LSB] = (byte)(dataLength & 0xFF);
  }

  @Override
  public OPCSocket setChannel(byte channel) {
    this.packetData[OFFSET_CHANNEL] = channel;
    return this;
  }

  @Override
  protected byte[] getDataBuffer() {
    return this.packetData;
  }

  @Override
  protected int getDataBufferOffset() {
    return HEADER_LEN;
  }

}
