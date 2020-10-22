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
 * Distributed Display Protocol is a simple protocol developed by 3waylabs. It's
 * a simple framing of raw color buffers, without DMX size limitations.
 *
 * The specification is available at http://www.3waylabs.com/ddp/
 */
public class DDPDatagram extends LXDatagram {

  public final static int MAX_DATA_LENGTH = 65535;

  private static final int HEADER_LENGTH = 10;
  private static final int DEFAULT_PORT = 4048;

  private static final int FLAGS_INDEX = 0;
  private static final int OFFSET_DATA_OFFSET = 4;

  public DDPDatagram(LX lx, LXModel model) {
    this(lx, model.toIndexBuffer());
  }

  public DDPDatagram(LX lx, int[] indexBuffer) {
    this(lx, indexBuffer, 0);
  }

  public DDPDatagram(LX lx, int[] indexBuffer, int dataOffset) {
    super(lx, indexBuffer, ByteOrder.RGB, HEADER_LENGTH + indexBuffer.length * 3);
    setPort(DEFAULT_PORT);
    validateBufferSize();

    // Flags: V V x T S R Q P
    this.buffer[0] = 0x41;

    // Reserved
    this.buffer[1] = 0x00;

    // Data type
    this.buffer[2] = 0x00;

    // Destination ID, default
    this.buffer[3] = 0x01;

    // Data offset
    setDataOffset(dataOffset);

    // Data length
    int dataLen = indexBuffer.length * 3;
    this.buffer[8] = (byte) (0xff & (dataLen >> 8));
    this.buffer[9] = (byte) (0xff & dataLen);
  }

  /**
   * Sets whether the push flag is set on this datagram.
   *
   * @param push Whether push flag is true
   * @return this
   */
  public DDPDatagram setPushFlag(boolean push) {
    if (push) {
      this.buffer[FLAGS_INDEX] |= 0x01;
    } else {
      this.buffer[FLAGS_INDEX] &= ~0x01;
    }
    return this;
  }

  /**
   * Sets the data offset for this packet
   *
   * @param offset Offset into the remote data buffer
   * @return this
   */
  public DDPDatagram setDataOffset(int offset) {
    this.buffer[OFFSET_DATA_OFFSET] = (byte) (0xff & (offset >>> 24));
    this.buffer[OFFSET_DATA_OFFSET + 1] = (byte) (0xff & (offset >>> 16));
    this.buffer[OFFSET_DATA_OFFSET + 2] = (byte) (0xff & (offset >>> 8));
    this.buffer[OFFSET_DATA_OFFSET + 3] = (byte) (0xff & offset);
    return this;
  }

  @Override
  protected int getDataBufferOffset() {
    return HEADER_LENGTH;
  }
}
