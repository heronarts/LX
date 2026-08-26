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

  public static final int DEFAULT_PORT = 4048;

  private static final int HEADER_LENGTH = 10;

  private static final int FLAGS_INDEX = 0;

  private static final int OFFSET_SEQUENCE_NUMBER = 1;
  private static final int OFFSET_DATA_TYPE = 2;
  private static final int OFFSET_DATA_OFFSET = 4;

  public static final int DATA_TYPE_UNDEFINED = 0x00;
  public static final int DATA_TYPE_RGB = 0x01;
  public static final int DATA_TYPE_HSL = 0x02;
  public static final int DATA_TYPE_RGBW = 0x03;
  public static final int DATA_TYPE_GRAYSCALE = 0x04;

  public static final int DATA_TYPE_SIZE_1BIT = 1;
  public static final int DATA_TYPE_SIZE_4BIT = 2;
  public static final int DATA_TYPE_SIZE_8BIT = 3;
  public static final int DATA_TYPE_SIZE_16BIT = 4;
  public static final int DATA_TYPE_SIZE_24BIT = 5;
  public static final int DATA_TYPE_SIZE_32BIT = 6;

  private boolean sequenceEnabled = false;

  private byte sequence = 0;

  public DDPDatagram(LX lx, LXModel model) {
    this(lx, model.toIndexBuffer());
  }

  public DDPDatagram(LX lx, int[] indexBuffer) {
    this(lx, indexBuffer, 0);
  }

  public DDPDatagram(LX lx, int[] indexBuffer, int dataOffset) {
    this(lx, indexBuffer, ByteOrder.RGB, dataOffset);
  }

  public DDPDatagram(LX lx, int[] indexBuffer, ByteOrder byteOrder, int dataOffset) {
    this(lx, new IndexBuffer(indexBuffer, byteOrder), dataOffset);
  }

  public DDPDatagram(LX lx, IndexBuffer indexBuffer, int dataOffset) {
    super(lx, indexBuffer, HEADER_LENGTH + indexBuffer.numChannels);
    setPort(DEFAULT_PORT);
    validateBufferSize();

    // Flags: V V x T S R Q P
    this.buffer[0] = 0x41;

    // Sequence number
    this.buffer[OFFSET_SEQUENCE_NUMBER] = 0x00;

    // Data type
    this.buffer[OFFSET_DATA_TYPE] = DATA_TYPE_UNDEFINED;

    // Destination ID, default
    this.buffer[3] = 0x01;

    // Data offset
    setDataOffset(dataOffset);

    // Data length
    int dataLen = indexBuffer.numChannels;
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

  /**
   * Set whether to increment and send sequence numbers
   *
   * @param sequenceEnabled true if sequence should be incremented and transmitted
   * @return this
   */
  public DDPDatagram setSequenceEnabled(boolean sequenceEnabled) {
    this.sequenceEnabled = sequenceEnabled;
    this.sequence = 0;
    if (!this.sequenceEnabled) {
      this.buffer[OFFSET_SEQUENCE_NUMBER] = 0;
    }
    return this;
  }

  @Override
  protected LXBufferOutput updateDataBuffer(int[] colors, GammaTable glut, double brightness) {
    int dataType = DATA_TYPE_UNDEFINED;
    int dataTypeSize = DATA_TYPE_UNDEFINED;
    for (IndexBuffer.Segment segment : this.indexBuffer.segments) {
      if (segment.byteEncoder instanceof ByteOrder byteOrder) {
        dataType =
          (byteOrder == ByteOrder.W) ? DATA_TYPE_GRAYSCALE :
          (byteOrder.hasWhite ? DATA_TYPE_RGBW : DATA_TYPE_RGB);
        dataTypeSize = DATA_TYPE_SIZE_8BIT;
      }
      break;
    }

    this.buffer[OFFSET_DATA_TYPE] = (byte) (0xff & (dataType << 3) | dataTypeSize);

    return super.updateDataBuffer(colors, glut, brightness);
  }

  @Override
  protected void updateSequenceNumber() {
    if (this.sequenceEnabled) {
      // NOTE: DDP only uses 4-bit nonzero sequence numbers (1-15)
      if (++this.sequence > 0x0f) {
        this.sequence = 0x01;
      }
      this.buffer[OFFSET_SEQUENCE_NUMBER] = this.sequence;
    }
  }
}
