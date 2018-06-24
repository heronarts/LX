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
 * Streaming ACN, also referred to as E1.31, is a standardized protocol for
 * streaming DMX data over ACN protocol. It's a fairly simple UDP-based wrapper
 * on 512 bytes of data with a 16-bit universe number.
 *
 * See: http://tsp.plasa.org/tsp/documents/docs/E1-31_2009.pdf
 */
public class StreamingACNDatagram extends LXDatagram {

  protected final static int DMX_DATA_POSITION = 126;

  protected final static int SEQUENCE_NUMBER_POSITION = 111;

  protected final static int UNIVERSE_NUMBER_POSITION = 113;

  private final static int DEFAULT_PORT = 5568;

  private final static int DEFAULT_UNIVERSE_NUMBER = 1;

  private final int[] pointIndices;

  /**
   * The universe number that this packet sends to.
   */
  private int universeNumber;

  public StreamingACNDatagram(LXFixture fixture) {
    this(DEFAULT_UNIVERSE_NUMBER, fixture);
  }

  /**
   * Constructs a datagram on universe 1
   *
   * @param pointIndices Points to send on this universe
   */
  public StreamingACNDatagram(int[] pointIndices) {
    this(DEFAULT_UNIVERSE_NUMBER, pointIndices);
  }

  public StreamingACNDatagram(int universeNumber, LXFixture fixture) {
    this(universeNumber, LXFixture.Utils.getIndices(fixture));
  }

  /**
   * Constructs a datagram, sends the list of point indices on the given
   * universe number.
   *
   * @param universeNumber Universe
   * @param pointIndices List of point indices to encode in packet
   */
  public StreamingACNDatagram(int universeNumber, int[] pointIndices) {
    this(universeNumber, pointIndices.length * 3, pointIndices);
  }

  /**
   * Subclasses may override for a custom payload with fixed size, not necessarily
   * based upon an array of point indices - such as custom DMX data
   *
   * @param universeNumber Universe number
   * @param dataSize Data payload size
   */
  protected StreamingACNDatagram(int universeNumber, int dataSize) {
    this(universeNumber, dataSize, null);
  }

  private StreamingACNDatagram(int universeNumber, int dataSize, int[] pointIndices) {
    super(DMX_DATA_POSITION + dataSize);
    setPort(DEFAULT_PORT);
    setUniverseNumber(universeNumber);
    this.pointIndices = pointIndices;

    int flagLength;

    // Preamble size
    this.buffer[0] = (byte) 0x00;
    this.buffer[1] = (byte) 0x10;

    // Post-amble size
    this.buffer[0] = (byte) 0x00;
    this.buffer[1] = (byte) 0x10;

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
    this.buffer[18] = (byte) 0x00;
    this.buffer[19] = (byte) 0x00;
    this.buffer[20] = (byte) 0x00;
    this.buffer[21] = (byte) 0x04;

    // Sender's CID
    for (int i = 22; i < 38; ++i) {
      this.buffer[i] = (byte) i;
    }

    // Flags and length
    flagLength = 0x00007000 | ((this.buffer.length - 38) & 0x0fffffff);
    this.buffer[38] = (byte) ((flagLength >> 8) & 0xff);
    this.buffer[39] = (byte) (flagLength & 0xff);

    // DMP Protocol PDU Identifier
    this.buffer[40] = (byte) 0x00;
    this.buffer[41] = (byte) 0x00;
    this.buffer[42] = (byte) 0x00;
    this.buffer[43] = (byte) 0x02;

    // Source name
    this.buffer[44] = 'L';
    this.buffer[45] = 'X';
    for (int i = 46; i < 108; ++i) {
      this.buffer[i] = 0;
    }

    // Priority
    this.buffer[108] = 100;

    // Reserved
    this.buffer[109] = 0x00;
    this.buffer[110] = 0x00;

    // Sequence Number
    this.buffer[111] = 0x00;

    // Options
    this.buffer[112] = 0x00;

    // Universe number
    // 113-114 are done in setUniverseNumber()

    // Flags and length
    flagLength = 0x00007000 | ((this.buffer.length - 115) & 0x0fffffff);
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
   * Sets the universe for this datagram
   *
   * @param universeNumber DMX universe
   * @return this
   */
  public StreamingACNDatagram setUniverseNumber(int universeNumber) {
    this.universeNumber = (universeNumber &= 0x0000ffff);
    this.buffer[UNIVERSE_NUMBER_POSITION] = (byte) ((universeNumber >> 8) & 0xff);
    this.buffer[UNIVERSE_NUMBER_POSITION + 1] = (byte) (universeNumber & 0xff);
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
  
  public void writeDmxData(byte data, int channel) {
    // TODO: bounds checking? Should channel < 0 be allowed? Maybe throw an OutOfBoundsException
    this.buffer[DMX_DATA_POSITION + channel] = data;
  }
  
  public void writeDmxData(byte[] data, int channel) {
    for (byte d : data) writeDmxData(d, channel++);
  }
  
  protected void advanceFrame() {
    this.buffer[SEQUENCE_NUMBER_POSITION]++;
  }

  @Override
  public void onSend(int[] colors) {
    advanceFrame();
    copyPoints(colors, this.pointIndices, DMX_DATA_POSITION);
  }
}
