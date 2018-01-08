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

import heronarts.lx.model.LXFixture;

/**
 * UDP implementation of http://openpixelcontrol.org/
 */
public class OPCDatagram extends LXDatagram implements OPCConstants {

  private final int[] indices;

  public OPCDatagram(LXFixture fixture) {
    this(fixture, CHANNEL_BROADCAST);
  }

  public OPCDatagram(LXFixture fixture, byte channel) {
    this(LXOutput.fixtureToIndices(fixture), channel);
  }

  public OPCDatagram(int[] indices) {
    this(indices, CHANNEL_BROADCAST);
  }

  public OPCDatagram(int[] indices, byte channel) {
    super(OPCOutput.HEADER_LEN + OPCOutput.BYTES_PER_PIXEL * indices.length);
    this.indices = indices;
    int dataLength = BYTES_PER_PIXEL * indices.length;
    this.buffer[INDEX_CHANNEL] = channel;
    this.buffer[INDEX_COMMAND] = COMMAND_SET_PIXEL_COLORS;
    this.buffer[INDEX_DATA_LEN_MSB] = (byte)(dataLength >>> 8);
    this.buffer[INDEX_DATA_LEN_LSB] = (byte)(dataLength & 0xFF);
  }

  public OPCDatagram setChannel(byte channel) {
    this.buffer[INDEX_CHANNEL] = channel;
    return this;
  }

  public byte getChannel() {
    return this.buffer[INDEX_CHANNEL];
  }

  @Override
  public void onSend(int[] colors) {
    copyPoints(colors, this.indices, INDEX_DATA);
  }

}
