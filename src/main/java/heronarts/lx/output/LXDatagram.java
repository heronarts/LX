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
import heronarts.lx.parameter.BooleanParameter;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

public abstract class LXDatagram extends LXBufferOutput implements LXOutput.InetOutput {

  private static DatagramSocket defaultSocket = null;

  private static DatagramSocket getDefaultSocket() throws SocketException {
    if (defaultSocket == null) {
      defaultSocket = new DatagramSocket();
    }
    return defaultSocket;
  }

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

  protected final byte[] buffer;

  ErrorState errorState = null;

  final DatagramPacket packet;

  private DatagramSocket socket;

  /**
   * Whether this datagram is in an error state
   */
  public final BooleanParameter error =
    new BooleanParameter("Error", false)
    .setDescription("Whether there have been errors sending to this datagram address");

  protected LXDatagram(LX lx, int[] indexBuffer, int datagramSize) {
    this(lx, indexBuffer, ByteOrder.RGB, datagramSize);
  }

  protected LXDatagram(LX lx, int[] indexBuffer, ByteOrder byteOrder, int datagramSize) {
    super(lx, indexBuffer, byteOrder);

    this.buffer = new byte[datagramSize];
    for (int i = 0; i < datagramSize; ++i) {
      this.buffer[i] = 0;
    }
    this.packet = new DatagramPacket(this.buffer, datagramSize);
  }

  protected void validateBufferSize() {
    // Validate that the data size on this thing is valid...
    int dataSize = this.buffer.length - getDataBufferOffset();
    if (dataSize < this.indexBuffer.length * this.byteOrder.getNumBytes()) {
      String cls = getClass().getSimpleName();
      throw new IllegalArgumentException(cls + " dataSize " + dataSize + " is insufficient for indexBuffer of length " + this.indexBuffer.length + " with ByteOrder " + this.byteOrder.toString());
    }

  }

  public LXDatagram setSocket(DatagramSocket socket) {
    this.socket = socket;
    return this;
  }

  protected ErrorState getErrorState() {
    if (this.errorState != null) {
      return this.errorState;
    }
    return this.errorState = getDatagramErrorState(this);
  }

  /**
   * Sets the destination address of this datagram
   *
   * @param address Destination address
   * @return this
   */
  @Override
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
  @Override
  public InetAddress getAddress() {
    return this.packet.getAddress();
  }

  /**
   * Sets the destination port number to send this datagram to
   *
   * @param port Port number
   * @return this
   */
  @Override
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
  @Override
  public int getPort() {
    return this.packet.getPort();
  }

  /**
   * Returns the data buffer
   */
  @Override
  public byte[] getDataBuffer() {
    return this.buffer;
  }

  /**
   * Subclasses may override to update a sequence number in the packet when
   * appropriate as part of the protocol.
   */
  protected void updateSequenceNumber() {}

  /**
   * Invoked by engine to send this packet when new color data is available. The
   * LXDatagram should update the packet object accordingly to contain the
   * appropriate buffer.
   *
   * @param colors Color buffer
   * @param glut Look-up table with gamma-adjusted brightness values
   */
  @Override
  protected void onSend(int[] colors, byte[] glut) {
    // Check for error state on this datagram's output
    ErrorState datagramErrorState = getErrorState();
    if (datagramErrorState.sendAfter >= this.lx.engine.nowMillis) {
      // This datagram can't be sent now... mark its error state
      this.error.setValue(true);
      return;
    }

    // Update the data buffer and sequence number
    updateDataBuffer(colors, glut);
    updateSequenceNumber();

    // Try sending the packet
    try {
      DatagramSocket socket = (this.socket != null) ? this.socket : LXDatagram.getDefaultSocket();
      socket.send(this.packet);
      if (datagramErrorState.failureCount > 0) {
        LXOutput.log("Recovered connectivity to " + datagramErrorState.destination);
      }
      // Sent fine! All good here...
      datagramErrorState.failureCount = 0;
      datagramErrorState.sendAfter = 0;
      this.error.setValue(false);
    } catch (IOException iox) {
      this.error.setValue(true);
      if (datagramErrorState.failureCount == 0) {
        LXOutput.error("IOException sending to "
            + datagramErrorState.destination + " (" + iox.getLocalizedMessage()
            + "), will initiate backoff after 3 consecutive failures");
      }
      ++datagramErrorState.failureCount;
      if (datagramErrorState.failureCount >= 3) {
        int pow = Math.min(5, datagramErrorState.failureCount - 3);
        long waitFor = (long) (50 * Math.pow(2, pow));
        LXOutput.error("Retrying " + datagramErrorState.destination
            + " in " + waitFor + "ms" + " (" + datagramErrorState.failureCount
            + " consecutive failures)");
        datagramErrorState.sendAfter = this.lx.engine.nowMillis + waitFor;
      }
    }

  }

  /**
   * Invoked when the datagram is no longer needed. Typically a no-op, but subclasses
   * may override if cleanup work is necessary.
   */
  @Override
  public void dispose() {}
}
