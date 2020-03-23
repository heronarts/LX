/**
 * Copyright 2016- Mark C. Slee, Heron Arts LLC
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

import heronarts.lx.model.LXModel;

public class ArtNetDatagram extends LXDatagram {

  public final static int ARTNET_PORT = 6454;

  private final static int DEFAULT_UNIVERSE = 0;
  private final static int ARTNET_HEADER_LENGTH = 18;
  private final static int SEQUENCE_INDEX = 12;

  private final int[] indexBuffer;

  private boolean sequenceEnabled = false;

  private byte sequence = 1;

  private final int dataLength;

  private int universeNumber;

  /**
   * Creates an ArtNetDatagram for the given model
   *
   * @param model Model of points
   */
  public ArtNetDatagram(LXModel model) {
    this(model, DEFAULT_UNIVERSE);
  }

  /**
   * Creates an ArtNetDatagram for the given index buffer
   *
   * @param indexBuffer Index buffer
   */
  public ArtNetDatagram(int[] indexBuffer) {
    this(indexBuffer, DEFAULT_UNIVERSE);
  }

  /**
   * Creates an ArtNetDatagram for the given index buffer and byte ordering
   *
   * @param indexBuffer Index buffer
   * @param byteOrder Byte ordering for points
   */
  public ArtNetDatagram(int[] indexBuffer, ByteOrder byteOrder) {
    this(indexBuffer, DEFAULT_UNIVERSE, byteOrder);
  }

  /**
   * Creates an ArtNetDatagram for the given model and universe number
   *
   * @param model Model of points
   * @param universeNumber universe number
   */
  public ArtNetDatagram(LXModel model, int universeNumber) {
    this(model.toIndexBuffer(), universeNumber);
  }

  /**
   * Creates an ArtNetDatagram for the given model, universe, and byte order
   *
   * @param model Model of points
   * @param universeNumber Universe number
   * @param byteOrder Byte ordering
   */
  public ArtNetDatagram(LXModel model, int universeNumber, ByteOrder byteOrder) {
    this(model.toIndexBuffer(), universeNumber, byteOrder);
  }

  /**
   * Creates an ArtNetDatagram for the given index buffer and universe number
   *
   * @param indexBuffer Index buffer
   * @param universeNumber Universe number
   */
  public ArtNetDatagram(int[] indexBuffer, int universeNumber) {
    this(indexBuffer, universeNumber, ByteOrder.RGB);
  }

  /**
   * Creates an ArtNetDatagram for the given index buffer, universe and byte ordering
   *
   * @param indexBuffer Index buffer
   * @param universeNumber Universe number
   * @param byteOrder Byte ordering
   */
  public ArtNetDatagram(int[] indexBuffer, int universeNumber, ByteOrder byteOrder) {
    this(indexBuffer, byteOrder.getNumBytes() * indexBuffer.length, universeNumber, byteOrder);
  }

  /**
   * Creates an ArtNetDatagram for the given model, with fixed data length and universe
   *
   * @param model Model
   * @param dataLength Fixed data payload length
   * @param universeNumber Universe number
   */
  public ArtNetDatagram(LXModel model, int dataLength, int universeNumber) {
    this(model.toIndexBuffer(), dataLength, universeNumber, ByteOrder.RGB);
  }

  /**
   * Creates an ArtNetDatagram with fixed data length for given model, universe, and byte ordering
   *
   * @param model Model of points
   * @param dataLength Fixed data payload length
   * @param universeNumber Universe number
   * @param byteOrder Byte ordering
   */
  public ArtNetDatagram(LXModel model, int dataLength, int universeNumber, ByteOrder byteOrder) {
    this(model.toIndexBuffer(), dataLength, universeNumber, byteOrder);
  }

  /**
   * Creates an ArtNetDatagram with fixed data length for given index buffer and universe
   *
   * @param indexBuffer Index buffer
   * @param dataLength Fixed data payload length
   * @param universeNumber Universe number
   */
  public ArtNetDatagram(int[] indexBuffer, int dataLength, int universeNumber) {
    this(indexBuffer, dataLength, universeNumber, ByteOrder.RGB);
  }

  /**
   * Creates an ArtNetDatagram with fixed data length for given index buffer, universe, and byte order
   *
   * @param indexBuffer Index buffer
   * @param dataLength Fixed data payload length
   * @param universeNumber Universe number
   * @param byteOrder Byte order
   */
  public ArtNetDatagram(int[] indexBuffer, int dataLength, int universeNumber, ByteOrder byteOrder) {
    super(ARTNET_HEADER_LENGTH + dataLength + (dataLength % 2), byteOrder);

    // DMX alignment requirement, ensure data length is even number of bytes
    this.dataLength = dataLength + (dataLength % 2);

    this.indexBuffer = indexBuffer;
    setPort(ARTNET_PORT);

    this.buffer[0] = 'A';
    this.buffer[1] = 'r';
    this.buffer[2] = 't';
    this.buffer[3] = '-';
    this.buffer[4] = 'N';
    this.buffer[5] = 'e';
    this.buffer[6] = 't';
    this.buffer[7] = 0;
    this.buffer[8] = 0x00; // ArtDMX opcode
    this.buffer[9] = 0x50; // ArtDMX opcode
    this.buffer[10] = 0; // Protocol version
    this.buffer[11] = 14; // Protocol version
    this.buffer[12] = 0; // Sequence
    this.buffer[13] = 0; // Physical

    setUniverseNumber(universeNumber);

    this.buffer[16] = (byte) ((this.dataLength >>> 8) & 0xff);
    this.buffer[17] = (byte) (this.dataLength & 0xff);

    // Ensure zero rest of buffer
    for (int i = ARTNET_HEADER_LENGTH; i < this.buffer.length; ++i) {
     this.buffer[i] = 0;
    }
  }

  public ArtNetDatagram setUniverseNumber(int universeNumber) {
    this.universeNumber = universeNumber;
    this.buffer[14] = (byte) (universeNumber & 0xff); // Universe LSB
    this.buffer[15] = (byte) ((universeNumber >>> 8) & 0xff); // Universe MSB
    return this;
  }

  public int getUniverseNumber() {
    return this.universeNumber;
  }

  public int getDataLength() {
    return this.dataLength;
  }

  /**
   * Set whether to increment and send sequence numbers
   *
   * @param sequenceEnabled true if sequence should be incremented and transmitted
   * @return this
   */
  public ArtNetDatagram setSequenceEnabled(boolean sequenceEnabled) {
    this.sequenceEnabled = sequenceEnabled;
    return this;
  }

  @Override
  public void onSend(int[] colors, byte[] glut) {
    copyPoints(colors, glut, this.indexBuffer, ARTNET_HEADER_LENGTH);
    if (this.sequenceEnabled) {
      if (++this.sequence == 0) {
        ++this.sequence;
      }
      this.buffer[SEQUENCE_INDEX] = this.sequence;
    }
  }
}
