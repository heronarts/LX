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

import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;

public abstract class LXDatagram {

  long sendAfter = 0;

  int failureCount = 0;

  /**
   * Various orderings for RGB buffer data
   */
  public enum ByteOrder {
    RGB, RBG, GRB, GBR, BRG, BGR,
  };

  /**
   * Note that the order here MUST match the order specified above
   */
  protected static final int[][] BYTE_ORDERING = {
    // R G B
    { 0, 1, 2 }, // RGB
    { 0, 2, 1 }, // RBG
    { 1, 0, 2 }, // GRB
    { 2, 0, 1 }, // GBR
    { 1, 2, 0 }, // BRG
    { 2, 1, 0 }, // BGR
  };

  protected ByteOrder byteOrder = ByteOrder.RGB;

  protected final byte[] buffer;

  final DatagramPacket packet;

  /**
   * Whether this datagram is active
   */
  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", true)
    .setDescription("Whether this datagram is active");

  public final BooleanParameter error =
    new BooleanParameter("Error", false)
    .setDescription("Whether there have been errors sending to this datagram");

  /**
   * Brightness of the datagram
   */
  public final BoundedParameter brightness =
    new BoundedParameter("Brightness", 1)
    .setDescription("Level of the output");

  protected LXDatagram(int bufferSize) {
    this.buffer = new byte[bufferSize];
    for (int i = 0; i < bufferSize; ++i) {
      this.buffer[i] = 0;
    }
    this.packet = new DatagramPacket(this.buffer, bufferSize);
  }

  /**
   * Sets the byte ordering of data in this datagram buffer
   *
   * @param byteOrder Byte ordering
   * @return this
   */
  public LXDatagram setByteOrder(ByteOrder byteOrder) {
    this.byteOrder = byteOrder;
    return this;
  }

  /**
   * Sets the destination address of this datagram
   *
   * @param ipAddress IP address or hostname as string
   * @return this
   * @throws UnknownHostException Bad address
   */
  public LXDatagram setAddress(String ipAddress) throws UnknownHostException {
    this.packet.setAddress(InetAddress.getByName(ipAddress));
    return this;
  }

  /**
   * Sets the destination address of this datagram
   *
   * @param address Destination address
   * @return this
   */
  public LXDatagram setAddress(InetAddress address) {
    this.packet.setAddress(address);
    return this;
  }

  /**
   * Gets the address this datagram sends to
   *
   * @return Destination address
   */
  public InetAddress getAddress() {
    return this.packet.getAddress();
  }

  /**
   * Sets the destination port number to send this datagram on
   *
   * @param port Port number
   * @return this
   */
  public LXDatagram setPort(int port) {
    this.packet.setPort(port);
    return this;
  }

  /**
   * Helper for subclasses to copy a list of points into the data buffer at a
   * specified offset. For many subclasses which wrap RGB buffers, onSend() will
   * be a simple call to this method with the right parameters.
   *
   * @param colors Array of color values
   * @param glut Look-up table of gamma-corrected brightness values
   * @param indexBuffer Array of point indices
   * @param offset Offset in buffer to write
   * @return this
   */
  protected LXDatagram copyPoints(int[] colors, byte[] glut, int[] indexBuffer, int offset) {
    int[] byteOffset = BYTE_ORDERING[this.byteOrder.ordinal()];
    for (int index : indexBuffer) {
      int color = (index >= 0) ? colors[index] : 0;
      this.buffer[offset + byteOffset[0]] = glut[((color >> 16) & 0xff)]; // R
      this.buffer[offset + byteOffset[1]] = glut[((color >> 8) & 0xff)]; // G
      this.buffer[offset + byteOffset[2]] = glut[(color & 0xff)]; // B
      offset += 3;
    }
    return this;
  }

  /**
   * Invoked by engine to send this packet when new color data is available. The
   * LXDatagram should update the packet object accordingly to contain the
   * appropriate buffer.
   *
   * @param colors Color buffer
   * @param glut Look-up table with gamma-adjusted brightness values
   */
  public abstract void onSend(int[] colors, byte[] glut);
}
