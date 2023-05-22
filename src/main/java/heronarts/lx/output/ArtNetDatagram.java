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

import heronarts.lx.LX;
import heronarts.lx.model.LXModel;

public class ArtNetDatagram extends LXDatagram {

  public final static int ARTNET_PORT = 6454;
  public final static int MAX_DATA_LENGTH = 512;
  public final static int MAX_UNIVERSE = 32768; // 0x01 << 15

  public final static int ARTNET_HEADER_LENGTH = 18;
  public final static int SEQUENCE_INDEX = 12;

  private final static int DEFAULT_UNIVERSE = 0;

  public final static int UNIVERSE_LSB = 14;
  public final static int UNIVERSE_MSB = 15;

  public final static byte[] HEADER = { 'A', 'r', 't', '-', 'N', 'e', 't', 0 };

  private boolean sequenceEnabled = false;

  private byte sequence = 1;

  private final int dataLength;

  private int universeNumber;

  // ArtNet DMX data length must be even
  private static int dmxDataLength(int length) {
    return length + (length % 2);
  }

  /**
   * Creates an ArtNetDatagram for the given model
   *
   * @param lx LX instance
   * @param model Model of points
   */
  public ArtNetDatagram(LX lx, LXModel model) {
    this(lx, model, DEFAULT_UNIVERSE);
  }

  /**
   * Creates an ArtNetDatagram for the given index buffer
   *
   * @param lx LX instance
   * @param indexBuffer Index buffer
   */
  public ArtNetDatagram(LX lx, int[] indexBuffer) {
    this(lx, indexBuffer, DEFAULT_UNIVERSE);
  }

  /**
   * Creates an ArtNetDatagram for the given index buffer and byte ordering
   *
   * @param lx LX instance
   * @param indexBuffer Index buffer
   * @param byteOrder Byte ordering for points
   */
  public ArtNetDatagram(LX lx, int[] indexBuffer, ByteOrder byteOrder) {
    this(lx, indexBuffer, byteOrder, DEFAULT_UNIVERSE);
  }

  /**
   * Creates an ArtNetDatagram for the given model and universe number
   *
   * @param lx LX instance
   * @param model Model of points
   * @param universeNumber universe number
   */
  public ArtNetDatagram(LX lx, LXModel model, int universeNumber) {
    this(lx, model.toIndexBuffer(), universeNumber);
  }

  /**
   * Creates an ArtNetDatagram for the given model, universe, and byte order
   *
   * @param lx LX instance
   * @param model Model of points
   * @param byteOrder Byte ordering
   * @param universeNumber Universe number
   */
  public ArtNetDatagram(LX lx, LXModel model, ByteOrder byteOrder, int universeNumber) {
    this(lx, model.toIndexBuffer(), byteOrder, universeNumber);
  }

  /**
   * Creates an ArtNetDatagram for the given index buffer and universe number
   *
   * @param lx LX instance
   * @param indexBuffer Index buffer
   * @param universeNumber Universe number
   */
  public ArtNetDatagram(LX lx, int[] indexBuffer, int universeNumber) {
    this(lx, indexBuffer, ByteOrder.RGB, universeNumber);
  }

  /**
   * Creates an ArtNetDatagram for the given index buffer, universe and byte ordering
   *
   * @param lx LX instance
   * @param indexBuffer Index buffer
   * @param byteOrder Byte ordering
   * @param universeNumber Universe number
   */
  public ArtNetDatagram(LX lx, int[] indexBuffer, ByteOrder byteOrder, int universeNumber) {
    this(lx, indexBuffer, byteOrder, byteOrder.getNumBytes() * indexBuffer.length, universeNumber);
  }

  /**
   * Creates an ArtNetDatagram for the given model, with fixed data length and universe
   *
   * @param lx LX instance
   * @param model Model
   * @param dataLength Fixed data payload length
   * @param universeNumber Universe number
   */
  public ArtNetDatagram(LX lx, LXModel model, int dataLength, int universeNumber) {
    this(lx, model.toIndexBuffer(), ByteOrder.RGB, dataLength, universeNumber);
  }

  /**
   * Creates an ArtNetDatagram with fixed data length for given model, universe, and byte ordering
   *
   * @param lx LX instance
   * @param model Model of points
   * @param byteOrder Byte ordering
   * @param dataLength Fixed data payload length
   * @param universeNumber Universe number
   */
  public ArtNetDatagram(LX lx, LXModel model, ByteOrder byteOrder, int dataLength, int universeNumber) {
    this(lx, model.toIndexBuffer(), byteOrder, dataLength, universeNumber);
  }

  /**
   * Creates an ArtNetDatagram with fixed data length for given index buffer and universe
   *
   * @param lx LX instance
   * @param indexBuffer Index buffer
   * @param dataLength Fixed data payload length
   * @param universeNumber Universe number
   */
  public ArtNetDatagram(LX lx, int[] indexBuffer, int dataLength, int universeNumber) {
    this(lx, indexBuffer, ByteOrder.RGB, dataLength, universeNumber);
  }

  /**
   * Creates an ArtNetDatagram with fixed data length for given index buffer, universe, and byte order
   *
   * @param lx LX instance
   * @param indexBuffer Index buffer
   * @param byteOrder Byte order
   * @param dataLength Fixed data payload length
   * @param universeNumber Universe number
   */
  public ArtNetDatagram(LX lx, int[] indexBuffer, ByteOrder byteOrder, int dataLength, int universeNumber) {
    this(lx, new IndexBuffer(indexBuffer, byteOrder), dataLength, universeNumber);
  }

  /**
   * Creates an ArtNetDatagram with fixed data length for given index buffer, universe, and byte order
   *
   * @param lx LX instance
   * @param indexBuffer Index buffer
   * @param universeNumber Universe number
   */
  public ArtNetDatagram(LX lx, IndexBuffer indexBuffer, int universeNumber) {
    this(lx, indexBuffer, indexBuffer.numChannels, universeNumber);
  }

  /**
   * Creates an ArtNetDatagram with fixed data length for given index buffer, universe, and byte order
   *
   * @param lx LX instance
   * @param indexBuffer Index buffer
   * @param dataLength Fixed data payload length
   * @param universeNumber Universe number
   */
  public ArtNetDatagram(LX lx, IndexBuffer indexBuffer, int dataLength, int universeNumber) {
    super(lx, indexBuffer, ARTNET_HEADER_LENGTH + dmxDataLength(dataLength));

    // DMX alignment requirement, ensure data length is even number of bytes
    this.dataLength = dmxDataLength(dataLength);
    setPort(ARTNET_PORT);

    validateBufferSize();

    System.arraycopy(HEADER, 0, this.buffer, 0, HEADER.length);
    this.buffer[8] = 0x00; // ArtDMX opcode
    this.buffer[9] = 0x50; // ArtDMX opcode
    this.buffer[10] = 0; // Protocol version
    this.buffer[11] = 14; // Protocol version
    this.buffer[12] = 0; // Sequence
    this.buffer[13] = 0; // Physical

    setUniverseNumber(universeNumber);

    this.buffer[16] = (byte) ((this.dataLength >>> 8) & 0xff);
    this.buffer[17] = (byte) (this.dataLength & 0xff);

  }

  public ArtNetDatagram setUniverseNumber(int universeNumber) {
    this.universeNumber = universeNumber;
    this.buffer[UNIVERSE_LSB] = (byte) (universeNumber & 0xff); // Universe LSB
    this.buffer[UNIVERSE_MSB] = (byte) ((universeNumber >>> 8) & 0xff); // Universe MSB
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
    if (!this.sequenceEnabled) {
      this.buffer[SEQUENCE_INDEX] = 0;
    }
    return this;
  }

  @Override
  protected int getDataBufferOffset() {
    return ARTNET_HEADER_LENGTH;
  }

  @Override
  protected void updateSequenceNumber() {
    if (this.sequenceEnabled) {
      // NOTE: ++ will overflow byte and wrap-around, but 0
      // means sequence disabled, so push 0 to 1
      if (++this.sequence == 0) {
        ++this.sequence;
      }
      this.buffer[SEQUENCE_INDEX] = this.sequence;
    }
  }
}
