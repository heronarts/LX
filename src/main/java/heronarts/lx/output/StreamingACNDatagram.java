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
 * Streaming ACN, also referred to as E1.31, is a standardized protocol for
 * streaming DMX data over ACN protocol. It's a fairly simple UDP-based wrapper
 * on 512 bytes of data with a 16-bit universe number.
 *
 * See: https://tsp.esta.org/tsp/documents/docs/ANSI_E1-31-2018.pdf
 */
public class StreamingACNDatagram extends LXDatagram {

  public final static int OFFSET_PRIORITY = 108;
  public final static int OFFSET_SEQUENCE_NUMBER = 111;
  public final static int OFFSET_UNIVERSE_NUMBER = 113;
  public final static int OFFSET_DMX_DATA = 126;

  public final static int MAX_DATA_LENGTH = 512;

  public final static int DEFAULT_PORT = 5568;

  private final static int DEFAULT_UNIVERSE_NUMBER = 1;

  public final static int DEFAULT_PRIORITY = 100;
  public final static int MAX_PRIORITY = 200;

  /**
   * The universe number that this packet sends to.
   */
  private int universeNumber;

  private int priority = DEFAULT_PRIORITY;

  /**
   * Creates a StreamingACNDatagram for the given model
   *
   * @param lx LX instance
   * @param model Model of points
   */
  public StreamingACNDatagram(LX lx, LXModel model) {
    this(lx, model, DEFAULT_UNIVERSE_NUMBER);
  }

  /**
   * Constructs a StreamingACNDatagram on default universe
   *
   * @param lx LX instance
   * @param indexBuffer Points to send on this universe
   */
  public StreamingACNDatagram(LX lx, int[] indexBuffer) {
    this(lx, indexBuffer, DEFAULT_UNIVERSE_NUMBER);
  }

  /**
   * Creates a StreamingACNDatagram for the model on given universe
   *
   * @param lx LX instance
   * @param model Model of points
   * @param universeNumber Universe number
   */
  public StreamingACNDatagram(LX lx, LXModel model, int universeNumber) {
    this(lx, model.toIndexBuffer(), universeNumber);
  }

  /**
   * Constructs a datagram, sends the list of point indices on the given
   * universe number.
   *
   * @param lx LX instance
   * @param indexBuffer List of point indices to encode in packet
   * @param universeNumber Universe number
   */
  public StreamingACNDatagram(LX lx, int[] indexBuffer, int universeNumber) {
    this(lx, indexBuffer, ByteOrder.RGB, universeNumber);
  }

  /**
   * Creates a StreamingACNDatagrm for given index buffer on universe and byte order
   *
   * @param lx LX instance
   * @param indexBuffer Index buffer
   * @param universeNumber Universe number
   * @param byteOrder Byte order
   */
  public StreamingACNDatagram(LX lx, int[] indexBuffer, ByteOrder byteOrder, int universeNumber) {
    this(lx, indexBuffer, byteOrder, indexBuffer.length * byteOrder.getNumBytes(), universeNumber);
  }

  /**
   * Subclasses may use this constructor for datagrams with custom DMX data of a fixed length.
   *
   * @param lx LX instance
   * @param dataSize Data size
   * @param universeNumber Universe number
   */
  protected StreamingACNDatagram(LX lx, int dataSize, int universeNumber) {
    this(lx, new int[0], ByteOrder.RGB, dataSize, universeNumber);
  }

  /**
   * Creates a StreamingACNDatagram for a given index buffer with fixed data size and universe number
   *
   * @param lx LX instance
   * @param indexBuffer Index buffer
   * @param dataSize Fixed DMX data size
   * @param universeNumber Universe number
   */
  public StreamingACNDatagram(LX lx, int[] indexBuffer, int dataSize, int universeNumber) {
    this(lx, indexBuffer, ByteOrder.RGB, dataSize, universeNumber);
  }

  /**
   * Creates a StreamingACNDatagram for a given index buffer with fixed data size and universe number
   *
   * @param lx LX instance
   * @param indexBuffer Index buffer
   * @param byteOrder Byte order
   * @param dataSize Fixed DMX data size
   * @param universeNumber Universe number
   */
  public StreamingACNDatagram(LX lx, int[] indexBuffer, ByteOrder byteOrder, int dataSize, int universeNumber) {
    this(lx, new IndexBuffer(indexBuffer, byteOrder), dataSize, universeNumber);
  }

  /**
   * Creates a StreamingACNDatagram for a given index buffer with fixed data size and universe number
   *
   * @param lx LX instance
   * @param indexBuffer Index buffer
   * @param universeNumber Universe number
   */
  public StreamingACNDatagram(LX lx, IndexBuffer indexBuffer, int universeNumber) {
    this(lx, indexBuffer, indexBuffer.numChannels, universeNumber);
  }

  /**
   * Creates a StreamingACNDatagram for a given index buffer with fixed data size and universe number
   *
   * @param lx LX instance
   * @param indexBuffer Index buffer
   * @param dataSize Fixed DMX data size
   * @param universeNumber Universe number
   */
  public StreamingACNDatagram(LX lx, IndexBuffer indexBuffer, int dataSize, int universeNumber) {
    super(lx, indexBuffer, OFFSET_DMX_DATA + dataSize);
    setPort(DEFAULT_PORT);
    setUniverseNumber(universeNumber);
    setPriority(this.priority);

    validateBufferSize();

    int flagLength;

    // Preamble size
    this.buffer[0] = (byte) 0x00;
    this.buffer[1] = (byte) 0x10;

    // Post-amble size
    this.buffer[2] = (byte) 0x00;
    this.buffer[3] = (byte) 0x00;

    // ACN Packet Identifier
    this.buffer[4] = (byte) 0x41;
    this.buffer[5] = (byte) 0x53;
    this.buffer[6] = (byte) 0x43;
    this.buffer[7] = (byte) 0x2d;
    this.buffer[8] = (byte) 0x45;
    this.buffer[9] = (byte) 0x31;
    this.buffer[10] = (byte) 0x2e;
    this.buffer[11] = (byte) 0x31;
    this.buffer[12] = (byte) 0x37;
    this.buffer[13] = (byte) 0x00;
    this.buffer[14] = (byte) 0x00;
    this.buffer[15] = (byte) 0x00;

    // Flags and length
    flagLength = 0x00007000 | ((this.buffer.length - 16) & 0x0fffffff);
    this.buffer[16] = (byte) ((flagLength >> 8) & 0xff);
    this.buffer[17] = (byte) (flagLength & 0xff);

    // RLP 1.31 Protocol PDU Identifier
    // VECTOR_ROOT_E131_DATA
    this.buffer[18] = (byte) 0x00;
    this.buffer[19] = (byte) 0x00;
    this.buffer[20] = (byte) 0x00;
    this.buffer[21] = (byte) 0x04;

    // Sender's CID - unique number
    for (int i = 22; i < 38; ++i) {
      this.buffer[i] = (byte) i;
    }

    // Flags and length
    flagLength = 0x7000 | ((this.buffer.length - 38) & 0x0fff);
    this.buffer[38] = (byte) ((flagLength >> 8) & 0xff);
    this.buffer[39] = (byte) (flagLength & 0xff);

    // DMP Protocol PDU Identifier
    // VECTOR_E131_DATA_PACKET
    this.buffer[40] = (byte) 0x00;
    this.buffer[41] = (byte) 0x00;
    this.buffer[42] = (byte) 0x00;
    this.buffer[43] = (byte) 0x02;

    // Source name
    this.buffer[44] = 'L';
    this.buffer[45] = 'X';
    this.buffer[46] = '-';
    byte[] versionBytes = LX.VERSION.getBytes();
    System.arraycopy(versionBytes, 0, this.buffer, 47, versionBytes.length);
    for (int i = 47 + versionBytes.length; i < 108; ++i) {
      this.buffer[i] = 0;
    }

    // Priority
    // Handled in setPriority()

    // Reserved
    this.buffer[109] = 0x00;
    this.buffer[110] = 0x00;

    // Sequence Number
    this.buffer[OFFSET_SEQUENCE_NUMBER] = 0x00;

    // Options
    this.buffer[112] = 0x00;

    // Universe number
    // 113-114 are done in setUniverseNumber()

    // Flags and length
    flagLength = 0x7000 | ((this.buffer.length - 115) & 0x0fff);
    this.buffer[115] = (byte) ((flagLength >> 8) & 0xff);
    this.buffer[116] = (byte) (flagLength & 0xff);

    // DMP Set Property Message PDU
    this.buffer[117] = (byte) 0x02;

    // Address Type & Data Type
    this.buffer[118] = (byte) 0xa1;

    // First Property Address
    this.buffer[119] = 0x00;
    this.buffer[120] = 0x00;

    // Address Increment
    this.buffer[121] = 0x00;
    this.buffer[122] = 0x01;

    // Property value count
    int numProperties = 1 + dataSize;
    this.buffer[123] = (byte) ((numProperties >> 8) & 0xff);
    this.buffer[124] = (byte) (numProperties & 0xff);

    // DMX Start
    this.buffer[125] = 0x00;
  }

  /**
   * Sets the priority for this datagram
   *
   * @param priority sACN priority level, 0-200
   * @return this
   */
  public StreamingACNDatagram setPriority(int priority) {
    if (priority < 0 || priority > MAX_PRIORITY) {
      throw new IllegalArgumentException("sACN priority must be 0-" + MAX_PRIORITY);
    }
    this.priority = priority;
    this.buffer[OFFSET_PRIORITY] = (byte) (0xff & this.priority);
    return this;
  }

  /**
   * Priority for this sACN datagram
   *
   * @return Priority level
   */
  public int getPriority() {
    return this.priority;
  }

  /**
   * Sets the universe for this datagram
   *
   * @param universeNumber DMX universe
   * @return this
   */
  public StreamingACNDatagram setUniverseNumber(int universeNumber) {
    this.universeNumber = (universeNumber &= 0x0000ffff);
    this.buffer[OFFSET_UNIVERSE_NUMBER] = (byte) ((universeNumber >> 8) & 0xff);
    this.buffer[OFFSET_UNIVERSE_NUMBER + 1] = (byte) (universeNumber & 0xff);
    return this;
  }

  /**
   * Universe number for datagram.
   *
   * @return Universe number
   */
  public int getUniverseNumber() {
    return this.universeNumber;
  }

  public void setDmxData(byte data, int channel) {
    if (channel < 0 || channel >= this.buffer.length - OFFSET_DMX_DATA) {
      throw new IndexOutOfBoundsException("Channel is greater than DMX data length");
    }
    this.buffer[OFFSET_DMX_DATA + channel] = data;
  }

  public void setDmxData(byte[] data, int channel) {
    if (channel < 0 || channel > this.buffer.length - OFFSET_DMX_DATA - data.length) {
      throw new IndexOutOfBoundsException("Channel is greater than DMX data length");
    }
    System.arraycopy(data, 0, this.buffer, OFFSET_DMX_DATA, data.length);
  }

  @Override
  protected int getDataBufferOffset() {
    return OFFSET_DMX_DATA;
  }

  @Override
  protected void updateSequenceNumber() {
    this.buffer[OFFSET_SEQUENCE_NUMBER]++;
  }

}
