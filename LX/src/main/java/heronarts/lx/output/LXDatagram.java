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
import java.util.HashMap;
import java.util.Map;

public abstract class LXDatagram {

  protected static class ErrorState {
    // Destination address
    final String destination;

    // Number of failures sending to this datagram address
    int failureCount = 0;

    // Timestamp to re-try sending to this address again after
    long sendAfter = 0;

    private ErrorState(String destination) {
      this.destination = destination;
    }
  }

  private static final Map<String, ErrorState> _datagramErrorState =
    new HashMap<String, ErrorState>();

  private static ErrorState getDatagramErrorState(LXDatagram datagram) {
    String destination = datagram.getAddress() + ":" + datagram.getPort();
    ErrorState datagramErrorState = _datagramErrorState.get(destination);
    if (datagramErrorState == null) {
      _datagramErrorState.put(destination, datagramErrorState = new ErrorState(destination));
    }
    return datagramErrorState;
  }

  /**
   * Various orderings for RGB buffer data
   */
  public enum ByteOrder {
    RGB(new int[] { 0, 1, 2 }),
    RBG(new int[] { 0, 2, 1 }),
    GRB(new int[] { 1, 0, 2 }),
    GBR(new int[] { 2, 0, 1 }),
    BRG(new int[] { 1, 2, 0 }),
    BGR(new int[] { 2, 1, 0 }),

    RGBW(new int[] { 0, 1, 2, 3 }),
    RBGW(new int[] { 0, 2, 1, 3 }),
    GRBW(new int[] { 1, 0, 2, 3 }),
    GBRW(new int[] { 2, 0, 1, 3 }),
    BRGW(new int[] { 1, 2, 0, 3 }),
    BGRW(new int[] { 2, 1, 0, 3 }),

    WRGB(new int[] { 1, 2, 3, 0 }),
    WRBG(new int[] { 1, 3, 2, 0 }),
    WGRB(new int[] { 2, 1, 3, 0 }),
    WGBR(new int[] { 3, 1, 2, 0 }),
    WBRG(new int[] { 2, 3, 1, 0 }),
    WBGR(new int[] { 3, 2, 1, 0 });

    /**
     * Byte offet is array of integer offsets in order RGBW, indicating
     * at what position the red, green, blue, and optionally white byte
     * go in the payload.
     */
    private final int[] byteOffset;

    ByteOrder(int[] byteOffset) {
      this.byteOffset = byteOffset;
    }

    public boolean hasWhite() {
      return this.byteOffset.length == 4;
    }

    public int getNumBytes() {
      return this.byteOffset.length;
    }

    public int[] getByteOffset() {
      return this.byteOffset;
    }
  };

  protected ByteOrder byteOrder = ByteOrder.RGB;

  protected final byte[] buffer;

  ErrorState errorState = null;

  final DatagramPacket packet;

  /**
   * Whether this datagram is active
   */
  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", true)
    .setDescription("Whether this datagram is active");

  public final BooleanParameter error =
    new BooleanParameter("Error", false)
    .setDescription("Whether there have been errors sending to this datagram address");

  /**
   * Brightness of the datagram
   */
  public final BoundedParameter brightness =
    new BoundedParameter("Brightness", 1)
    .setDescription("Level of the output");

  protected LXDatagram(int bufferSize) {
    this(bufferSize, ByteOrder.RGB);
  }

  protected LXDatagram(int bufferSize, ByteOrder byteOrder) {
    this.buffer = new byte[bufferSize];
    for (int i = 0; i < bufferSize; ++i) {
      this.buffer[i] = 0;
    }
    this.packet = new DatagramPacket(this.buffer, bufferSize);
    this.byteOrder = byteOrder;
  }

  protected ErrorState getErrorState() {
    if (this.errorState != null) {
      return this.errorState;
    }
    return this.errorState = getDatagramErrorState(this);
  }

  /**
   * Sets the byte ordering of data in this datagram buffer
   *
   * @param byteOrder Byte ordering
   * @return this
   */
  public LXDatagram setByteOrder(ByteOrder byteOrder) {
    if (this.byteOrder.getNumBytes() != byteOrder.getNumBytes()) {
      throw new IllegalArgumentException("May not change number of bytes in order");
    }
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
    this.errorState = null;
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
    this.errorState = null;
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
   * Sets the destination port number to send this datagram to
   *
   * @param port Port number
   * @return this
   */
  public LXDatagram setPort(int port) {
    this.errorState = null;
    this.packet.setPort(port);
    return this;
  }

  /**
   * Gets the destination port number this datagram is sent to
   *
   * @return Destination port number
   */
  public int getPort() {
    return this.packet.getPort();
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
    int numBytes = this.byteOrder.getNumBytes();
    if (this.byteOrder.hasWhite()) {
      int[] byteOffset = this.byteOrder.getByteOffset();
      for (int index : indexBuffer) {
        int color = (index >= 0) ? colors[index] : 0;
        byte r = glut[((color >> 16) & 0xff)];
        byte g = glut[((color >> 8) & 0xff)];
        byte b = glut[(color & 0xff)];
        byte w = (r < g) ? ((r < b) ? r : b) : ((g < b) ? g : b);
        r -= w;
        g -= w;
        b -= w;
        this.buffer[offset + byteOffset[0]] = r;
        this.buffer[offset + byteOffset[1]] = g;
        this.buffer[offset + byteOffset[2]] = b;
        this.buffer[offset + byteOffset[3]] = w;
        offset += numBytes;
      }
    } else {
      int[] byteOffset = this.byteOrder.getByteOffset();
      for (int index : indexBuffer) {
        int color = (index >= 0) ? colors[index] : 0;
        this.buffer[offset + byteOffset[0]] = glut[((color >> 16) & 0xff)]; // R
        this.buffer[offset + byteOffset[1]] = glut[((color >> 8) & 0xff)]; // G
        this.buffer[offset + byteOffset[2]] = glut[(color & 0xff)]; // B
        offset += numBytes;
      }
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
