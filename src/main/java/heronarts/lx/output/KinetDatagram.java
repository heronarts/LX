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
 * A datagram implementing the Kinet protocol, used by Color Kinetics devices.
 * These datagrams have a header followed by 512 bytes of color data. A port
 * number on the output device is specified, distinct from the UDP port. For
 * instance, an sPDS-480 has 16 outputs.
 */
public class KinetDatagram extends LXDatagram {

  private final static int DMXOUT_HEADER_LENGTH = 21;
  private final static int PORTOUT_HEADER_LENGTH = 24;
  private final static int DATA_LENGTH = 512;

  public final static int MAX_DATA_LENGTH = DATA_LENGTH;
  public final static int MAX_KINET_PORT = 255;

  private final static int PORTOUT_PACKET_LENGTH = PORTOUT_HEADER_LENGTH + DATA_LENGTH;
  private final static int DMXOUT_PACKET_LENGTH = DMXOUT_HEADER_LENGTH + DATA_LENGTH;

  public final static int KINET_PORT = 6038;

  public enum Version {
    DMXOUT(DMXOUT_HEADER_LENGTH, DMXOUT_PACKET_LENGTH),
    PORTOUT(PORTOUT_HEADER_LENGTH, PORTOUT_PACKET_LENGTH);

    public final int headerLength;
    public final int packetLength;

    private Version(int headerLength, int packetLength) {
      this.headerLength = headerLength;
      this.packetLength = packetLength;
    }
  };

  private final Version version;

  /**
   * Constructs a datagram that sends on the given kinet supply output port
   *
   * @param lx LX instance
   * @param model Model to output points for
   * @param kinetPort Number of the output port on the kinet power supply
   */
  public KinetDatagram(LX lx, LXModel model, int kinetPort) {
    this(lx, model, kinetPort, Version.PORTOUT);
  }

  /**
   * Constructs a datagram that sends on the given kinet supply output port
   *
   * @param lx LX instance
   * @param model Model that this datagram outputs points for
   * @param kinetPort Number of the output port on the kinet power supply
   * @param version Version of Kinet Protocol
   */
  public KinetDatagram(LX lx, LXModel model, int kinetPort, Version version) {
    this(lx, model.toIndexBuffer(), ByteOrder.RGB, kinetPort, version);
  }

  /**
   * Constructs a datagram that sends on the given kinet supply output port
   *
   * @param lx LX instance
   * @param indexBuffer A list of the point indices that should be sent on this port
   * @param kinetPort Number of the output port on the kinet power supply
   */
  public KinetDatagram(LX lx, int[] indexBuffer, int kinetPort) {
    this(lx, indexBuffer, ByteOrder.RGB, kinetPort, Version.PORTOUT);
  }

  /**
   * Constructs a datagram that sends on the given kinet supply output port
   *
   * @param lx LX instance
   * @param indexBuffer A list of the point indices that should be sent on this port
   * @param byteOrder Which byte ordering to use for the output
   * @param kinetPort Number of the output port on the kinet power supply
   */
  public KinetDatagram(LX lx, int[] indexBuffer, ByteOrder byteOrder, int kinetPort) {
    this(lx, indexBuffer, byteOrder, kinetPort, Version.PORTOUT);
  }

  /**
   * Constructs a datagram that sends on the given kinet supply output port
   *
   * @param lx LX instance
   * @param indexBuffer Index buffer that this datagram outputs points for
   * @param byteOrder Which byte ordering to use for the output
   * @param kinetPort Number of the output port on the kinet power supply
   * @param version Version of Kinet Protocol
   */
  public KinetDatagram(LX lx, int[] indexBuffer, ByteOrder byteOrder, int kinetPort, Version version) {
    this(lx, new IndexBuffer(indexBuffer, byteOrder), kinetPort, version);
  }

  /**
   * Constructs a datagram that sends on the given kinet supply output port
   *
   * @param lx LX instance
   * @param indexBuffer Index buffer that this datagram outputs points for
   * @param kinetPort Number of the output port on the kinet power supply
   */
  public KinetDatagram(LX lx, IndexBuffer indexBuffer, int kinetPort) {
    this(lx, indexBuffer, kinetPort, Version.PORTOUT);
  }

  /**
   * Constructs a datagram that sends on the given kinet supply output port
   *
   * @param lx LX instance
   * @param indexBuffer Index buffer that this datagram outputs points for
   * @param kinetPort Number of the output port on the kinet power supply
   * @param version Version of Kinet Protocol
   */
  public KinetDatagram(LX lx, IndexBuffer indexBuffer, int kinetPort, Version version) {
    super(lx, indexBuffer, version.packetLength);
    setPort(KINET_PORT);
    this.version = version;

    validateBufferSize();

    // Kinet Header
    this.buffer[0] = (byte) 0x04;
    this.buffer[1] = (byte) 0x01;
    this.buffer[2] = (byte) 0xdc;
    this.buffer[3] = (byte) 0x4a;

    switch (this.version) {
    case PORTOUT:
      // Version
      this.buffer[4] = (byte) 0x01;
      this.buffer[5] = (byte) 0x00;

      // Type (PORTOUT)
      this.buffer[6] = (byte) 0x08;
      this.buffer[7] = (byte) 0x01;

      // Padding / sequence
      this.buffer[8] = (byte) 0x00;
      this.buffer[9] = (byte) 0x00;
      this.buffer[10] = (byte) 0x00;
      this.buffer[11] = (byte) 0x00;

      // Universe
      this.buffer[12] = (byte) 0xff;
      this.buffer[13] = (byte) 0xff;
      this.buffer[14] = (byte) 0xff;
      this.buffer[15] = (byte) 0xff;

      // Port number
      this.buffer[16] = (byte) (0xff & kinetPort);

      // Pad
      this.buffer[17] = (byte) 0x00;

      // Flags (irrelevant?)
      this.buffer[18] = (byte) 0x00;
      this.buffer[19] = (byte) 0x00;
      this.buffer[20] = (byte) 0x00;
      this.buffer[21] = (byte) 0x02; // Possibly # of ports on controller? (irrelevant)

      // Start code
      this.buffer[22] = (byte) 0x00;
      this.buffer[23] = (byte) 0x00;
      break;

    case DMXOUT:
      // Version
      this.buffer[4] = (byte) 0x01;
      this.buffer[5] = (byte) 0x00;

      // Type (DMXOUT)
      this.buffer[6] = (byte) 0x01;
      this.buffer[7] = (byte) 0x01;

      // Sequence number
      this.buffer[8] = (byte) 0x00;
      this.buffer[9] = (byte) 0x00;
      this.buffer[10] = (byte) 0x00;
      this.buffer[11] = (byte) 0x00;

      // Port
      this.buffer[12] = (byte) 0x00;

      // Priority / Flags
      this.buffer[13] = (byte) 0x40; // Priority? Checksum? Irrelevant padding?

      // Timer Val? Padding?
      this.buffer[14] = (byte) 0x00;
      this.buffer[15] = (byte) 0x00;

      // Universe, little-endian
      this.buffer[16] = (byte) (0xff & kinetPort);
      this.buffer[17] = (byte) 0x00;
      this.buffer[18] = (byte) 0x00;
      this.buffer[19] = (byte) 0x00;

      // One byte to start data
      this.buffer[20] = (byte) 0x00;
      break;
    }
  }

  public KinetDatagram setKinetPort(byte kinetPort) {
    this.buffer[16] = kinetPort;
    return this;
  }

  @Override
  protected int getDataBufferOffset() {
    return this.version.headerLength;
  }

}
