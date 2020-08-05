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
 * UDP implementation of http://openpixelcontrol.org/
 */
public class OPCDatagram extends LXDatagram implements OPCOutput {

  public final static int MAX_DATA_LENGTH = 65535;

  public OPCDatagram(LX lx, LXModel model) {
    this(lx, model, CHANNEL_BROADCAST);
  }

  public OPCDatagram(LX lx, LXModel model, byte channel) {
    this(lx, model.toIndexBuffer(), channel);
  }

  public OPCDatagram(LX lx, int[] indexBuffer) {
    this(lx, indexBuffer, CHANNEL_BROADCAST);
  }

  public OPCDatagram(LX lx, int[] indexBuffer, byte channel) {
    this(lx, indexBuffer, ByteOrder.RGB, channel);
  }

  public OPCDatagram(LX lx, int[] indexBuffer, ByteOrder byteOrder, byte channel) {
    super(lx, indexBuffer, byteOrder, OPCSocket.HEADER_LEN + byteOrder.getNumBytes() * indexBuffer.length);
    int dataLength = byteOrder.getNumBytes() * indexBuffer.length;
    validateBufferSize();

    this.buffer[OFFSET_CHANNEL] = channel;
    this.buffer[OFFSET_COMMAND] = COMMAND_SET_PIXEL_COLORS;
    this.buffer[OFFSET_DATA_LEN_MSB] = (byte)(dataLength >>> 8);
    this.buffer[OFFSET_DATA_LEN_LSB] = (byte)(dataLength & 0xFF);
  }

  @Override
  public OPCDatagram setChannel(byte channel) {
    this.buffer[OFFSET_CHANNEL] = channel;
    return this;
  }

  public byte getChannel() {
    return this.buffer[OFFSET_CHANNEL];
  }

  @Override
  protected int getDataBufferOffset() {
    return OFFSET_DATA;
  }

}
