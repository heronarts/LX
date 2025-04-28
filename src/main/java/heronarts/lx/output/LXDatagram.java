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

  public static class BufferException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public BufferException(String message) {
      super(message);
    }
  }

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

  protected LXDatagram(LX lx, IndexBuffer indexBuffer, int datagramSize) {
    super(lx, indexBuffer);

    this.buffer = new byte[datagramSize];
    for (int i = 0; i < datagramSize; ++i) {
      this.buffer[i] = 0;
    }
    this.packet = new DatagramPacket(this.buffer, datagramSize);
  }

  protected void validateBufferSize() {
    // Validate that the data size on this thing is valid...
    int dataSize = this.buffer.length - getDataBufferOffset();
    if (dataSize < this.indexBuffer.numChannels) {
      String cls = getClass().getSimpleName();
      throw new BufferException(cls + " dataSize " + dataSize + " is insufficient for indexBuffer with " + this.indexBuffer.numChannels + " channels");
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

  private static long slowPacketInitMs = -1;
  private static int slowPacketCount = 0;
  private static boolean slowPacketLogged = false;

  /**
   * Invoked by engine to send this packet when new color data is available. The
   * LXDatagram should update the packet object accordingly to contain the
   * appropriate buffer.
   *
   * @param colors Color buffer
   * @param glut Look-up table with gamma curves for 0-255 levels
   * @param brightness Brightness level to send at
   */
  @Override
  protected void onSend(int[] colors, GammaTable glut, double brightness) {
    // Check for error state on this datagram's output
    ErrorState datagramErrorState = getErrorState();
    if (datagramErrorState.sendAfter >= this.lx.engine.nowMillis) {
      // This datagram can't be sent now... mark its error state
      this.error.setValue(true);
      return;
    }

    // Update the data buffer and sequence number
    updateDataBuffer(colors, glut, brightness);
    updateSequenceNumber();

    // Try sending the packet
    try {
      final DatagramSocket socket = (this.socket != null) ? this.socket : LXDatagram.getDefaultSocket();
      final long latencyCheck = System.currentTimeMillis();
      if (slowPacketInitMs < 0) {
        slowPacketInitMs = latencyCheck;
      }

      socket.send(this.packet);

      if (!slowPacketLogged) {
        final long afterSend = System.currentTimeMillis();
        if (afterSend - latencyCheck > 1) {
          // Check that this call is not blocking, this issue has been noticed by multiple users on
          // Raspberry Pi systems, when any address being sent is unresolvable, the output queues fill causing
          // major framerate degradation to all addresses. A solution for this (thanks to Brian Bulkowski)
          // is documented on the wiki. On Mac/Windows this never seems to be an issue, streaming as many
          // UDP packets as you like to an unresolvable address will not choke the system.
          if (++slowPacketCount > 10) {
            if (afterSend - slowPacketInitMs > 5000) {
              LX.error("Calls to DatagramSocket.send() appear to be unexpectedly blocking, you may be sending to an unresolvable address or network queues may be saturated. If you are on Linux/Raspberry-Pi, consult the following URL for guidance on relevant kernel parameters: https://github.com/heronarts/LXStudio/wiki/Raspberry-Pi");
              slowPacketLogged = true;
            }
          }
        }
      }

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

}
