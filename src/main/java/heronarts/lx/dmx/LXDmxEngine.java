/**
 * Copyright 2017- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.dmx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.osc.LXOscEngine.IOState;
import heronarts.lx.output.ArtNetDatagram;
import heronarts.lx.output.StreamingACNDatagram;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.parameter.TriggerParameter;

public class LXDmxEngine extends LXComponent {

  public final static String DEFAULT_ARTNET_HOST = "0.0.0.0";

  public enum ByteOrder {
    RGB(0,1,2),
    RBG(0,2,1),
    GRB(1,0,2),
    GBR(2,0,1),
    BRG(1,2,0),
    BGR(2,1,0);

    public final int r, g, b;

    private ByteOrder(int r, int g, int b) {
      this.r = r;
      this.g = g;
      this.b = b;
    }
  }

  public final EnumParameter<IOState> artNetReceiveState =
    new EnumParameter<IOState>("Art-Net RX State", IOState.STOPPED)
    .setMappable(false)
    .setDescription("The state of the Art-Net receiver");

  public final BooleanParameter artNetReceiveActive =
    new BooleanParameter("Art-Net Active", false)
    .setMappable(false)
    .setDescription("Enables or disables Art-Net DMX input");

  public final StringParameter artNetReceiveHost =
    new StringParameter("Art-Net Host", DEFAULT_ARTNET_HOST)
    .setDescription("Hostname to which Art-Net receiver socket is bound");

  public final DiscreteParameter artNetReceivePort =
    new DiscreteParameter("Art-Net RX Port", ArtNetDatagram.ARTNET_PORT, 1, 65535)
    .setDescription("UDP port on which the engine listens for Art-Net")
    .setMappable(false)
    .setUnits(LXParameter.Units.INTEGER);

  public final TriggerParameter artNetActivity =
    new TriggerParameter("Art-Net Activity")
    .setMappable(false)
    .setDescription("Triggers when art-net input is received");

  public final BooleanParameter artNetLog =
    new BooleanParameter("Log Art-Net Activity", false)
    .setDescription("Whether to write Art-Net activity to the log");

  private ArtNetReceiver artNetReceiver = null;

  public static final int MAX_CHANNEL = 512;
  public static final int MAX_UNIVERSE = 512;

  private final byte[][] data =
    new byte[MAX_UNIVERSE][ArtNetDatagram.MAX_DATA_LENGTH];

  private DatagramSocket artPollSocket;

  public LXDmxEngine(LX lx) {
    super(lx);
    addParameter("artNetReceiveHost", this.artNetReceiveHost);
    addParameter("artNetReceivePort", this.artNetReceivePort);
    addParameter("artNetReceiveActive", this.artNetReceiveActive);
    addParameter("artNetLog", this.artNetLog);
  }

  private DatagramSocket _getArtPollSocket() {
    if (this.artPollSocket != null) {
      return this.artPollSocket;
    }
    try {
      this.artPollSocket = new DatagramSocket();
    } catch (SocketException sx) {
      error(sx, "Could not create DatagramSocket for ArtPollReply");
    }
    return this.artPollSocket;
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.artNetReceiveActive) {
      stopReceiver(IOState.STOPPED);
      if (this.artNetReceiveActive.isOn()) {
        startReceiver();
      }
    } else if (p == this.artNetReceiveHost) {
      if (this.artNetReceiveActive.isOn()) {
        startReceiver();
      }
    } else if (p == this.artNetReceivePort) {
      if (this.artNetReceiveActive.isOn()) {
        startReceiver();
      }
    }
  }

  private void stopReceiver(IOState state) {
    if (this.artNetReceiver != null) {
      this.artNetReceiver.interrupt();
      this.artNetReceiver.socket.close();
      this.artNetReceiver = null;
    }
    this.artNetReceiveState.setValue(state);
  }

  private void startReceiver() {
    if (this.artNetReceiver != null) {
      stopReceiver(IOState.STOPPED);
    }
    this.artNetReceiveState.setValue(IOState.BINDING);
    final String artNetHost = this.artNetReceiveHost.getString();
    final int artNetPort = this.artNetReceivePort.getValuei();
    try {
      final InetAddress addr = InetAddress.getByName(artNetHost);
      final DatagramSocket socket = new DatagramSocket(artNetPort, addr);
      this.artNetReceiver = new ArtNetReceiver(socket);
      this.artNetReceiver.start();
      this.artNetReceiveState.setValue(IOState.BOUND);
    } catch (SocketException sx) {
      error(sx, "Could not create Art-Net listener socket: " + sx.getMessage());
      this.lx.pushError("Failed to start Art-Net receiver at " + artNetHost + ":" + artNetPort + "\n" + sx.getLocalizedMessage());
      this.artNetReceiveState.setValue(IOState.SOCKET_ERROR);
    } catch (Throwable x) {
      error(x, "Unknown error starting art-net receiver: " + x.getMessage());
      this.lx.pushError("Uknown error starting Art-Net receiver at " + artNetHost + ":" + artNetPort + "\n" + x.getLocalizedMessage());
      this.artNetReceiveState.setValue(IOState.SOCKET_ERROR);
    }
  }

  public int getColor(int universe, int channel) {
    return getColor(universe, channel, ByteOrder.RGB);
  }

  public int getColor(int universe, int channel, ByteOrder byteOrder) {
    return LXColor.rgba(
      this.data[universe][channel + byteOrder.r],
      this.data[universe][channel + byteOrder.g],
      this.data[universe][channel + byteOrder.b],
      0xff
    );
  }

  public byte getByte(int universe, int channel) {
    return this.data[universe][channel];
  }

  public int getValuei(int universe, int channel) {
    return this.data[universe][channel] & 0xff;
  }

  public double getNormalized(int universe, int channel) {
    return (this.data[universe][channel] & 0xff) / 255.;
  }

  private class ArtNetReceiver extends Thread {

    private final DatagramSocket socket;
    private final byte[] artPollReply;
    private int artPollCount = 0;

    private ArtNetReceiver(DatagramSocket socket) {
      super("Art-Net Receiver Thread");
      this.socket = socket;
      this.artPollReply = constructArtPollReply();
    }

    private byte[] constructArtPollReply() {
      final Inet4Address localAddress =
        (this.socket.getLocalAddress() instanceof Inet4Address inet4) ?
          inet4 : getLocalhostIPv4();
      if (localAddress == null) {
        return null;
      }

      final byte[] ip = localAddress.getAddress();
      final int port = this.socket.getLocalPort();

      final ByteArrayOutputStream buffer = new ByteArrayOutputStream(207);
      try {
        buffer.write(ArtNetDatagram.HEADER);
        buffer.write(new byte[] {
          0x00, 0x21, // OpPollReply
          ip[0], ip[1], ip[2], ip[3], // IP
          (byte) (port & 0xff), (byte) ((port >>> 8) & 0xff), // Port
          0x00, 0x01, // Firmware
          0x00, 0x00, // NetSwitch, SubSwitch
          0x00, 0x00, // OEM
          0x00, // UBEA
          0x00, // Status1
          0x00, 0x00 // ESTA
        });
        buffer.write(byteStr(18, "Chromatik")); // PortName
        buffer.write(byteStr(64, "Chromatik Digital Lighting Workstation - " + LX.VERSION)); // LongName
        buffer.write(byteStr(64, "#0001 [0000] Chromatik Status OK")); // NodeReport
        buffer.write(new byte[] {
          0x00, 0x04, // NumPorts
          (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, // PortTypes (output DMX)
          0x00, 0x00, 0x00, 0x00, // GoodInput
          (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, // GoodOutput
          0x00, 0x00, 0x00, 0x00, // SwIn
          0x00, 0x01, 0x02, 0x03, // SwOut
          StreamingACNDatagram.DEFAULT_PRIORITY, // AcnPriority
          0x00, // SwMacro
          0x00, // SwRemote
          0x00, 0x00, 0x00, // Spare
          0x02, // Style (media server)
          0x00, 0x00, 0x00, 0x00, 0x00, 0x00 // MAC unspecified
        });
      } catch (IOException iox) {
        error(iox, "Failed to create Art Poll Reply");
        return null;
      }

      return buffer.toByteArray();
    }

    private byte[] byteStr(int len, String str) {
      byte[] bytes = new byte[len];
      int i = 0;
      for (byte b : str.getBytes()) {
        bytes[i++] = b;
      }
      return bytes;
    }

    private Inet4Address getLocalhostIPv4() {
      try {
        final Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
          final NetworkInterface networkInterface = networkInterfaces.nextElement();
          if (!networkInterface.isLoopback() && networkInterface.isUp()) {
            final Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
              InetAddress inetAddress = inetAddresses.nextElement();
              if ((inetAddress instanceof Inet4Address inet4) &&
                  !inetAddress.isLoopbackAddress() &&
                  !inetAddress.isLinkLocalAddress()) {
                return inet4;
              }
            }
          }
        }
      } catch (SocketException sx) {
        error(sx, "Exception searching for local IPv4 address");
      }
      error("Could not determine a local IPv4 address, will not respond to ArtPoll");
      return null;
    }

    private boolean checkHeader(byte[] dmxData, int offset) {
      for (int i = 0; i < ArtNetDatagram.HEADER.length; ++i) {
        if (dmxData[offset + i] != ArtNetDatagram.HEADER[i]) {
          error("Packet missing valid Art-Net header");
          return false;
        }
      }
      return true;
    }

    @Override
    public void run() {
      final String address = this.socket.getLocalAddress() + ":" + this.socket.getLocalPort();
      log("Starting Art-Net listener on " + address);
      final byte[] buffer = new byte[ArtNetDatagram.ARTNET_HEADER_LENGTH + ArtNetDatagram.MAX_DATA_LENGTH];
      final DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

      while (!isInterrupted()) {
        try {
          this.socket.receive(packet);
          final byte[] artNetData = packet.getData();
          final int offset = packet.getOffset();
          if (!checkHeader(artNetData, offset)) {
            continue;
          }

          artNetActivity.trigger();

          final int opcode =
            (artNetData[offset + ArtNetDatagram.OPCODE_LSB] & 0xff) |
            ((artNetData[offset + ArtNetDatagram.OPCODE_MSB] & 0xff) << 8);

          switch (opcode) {
            case ArtNetDatagram.OPCODE_POLL -> receivePoll(packet, artNetData, offset);
            case ArtNetDatagram.OPCODE_POLL_REPLY -> receivePollReply(packet, artNetData, offset);
            case ArtNetDatagram.OPCODE_DMX -> receiveDmx(packet, artNetData, offset);
            case ArtNetDatagram.OPCODE_SYNC -> receiveSync(packet, artNetData, offset);
            default -> error("Unsupported ArtNet opcode: " + String.format("0x%04X", opcode));
          }

        } catch (Throwable x) {
          if (isInterrupted()) {
            break;
          }
          error(x, x.getMessage());
        }
      }

      // Thread is finished, close the socket
      try {
        this.socket.close();
      } catch (Throwable x) {
        LX.error(x, "Error closing Art-Net DatagramSocket: " + x.getMessage());
      }
      log("Art-Net receiver thread finished " + address + ".");
    }

    private void receivePoll(DatagramPacket packet, byte[] artNetData, int offset) {
      final InetAddress from = packet.getAddress();
      final boolean log = artNetLog.isOn();
      if (log) {
        log("[RX] ArtPoll <- " + from);
      }

      ++this.artPollCount;
      if (this.artPollCount > 9999) {
        this.artPollCount = 0;
      }

      if (this.artPollReply == null) {
        error("Cannot respond to ArtPoll because artPollReply failed to initialize");
        return;
      }

      final int nodeReportOffset = 108;
      final int nodeReportLength = 64;
      String nodeReport = "#0001 [" + String.format("%04d", this.artPollCount) + "] Chromatik Status OK";
      int n = nodeReportOffset;
      for (byte b : nodeReport.getBytes()) {
        this.artPollReply[n++] = b;
      }
      while (n < nodeReportLength) {
        this.artPollReply[n++] = 0;
      }

      // NB: we're technically supposed to wait a random amount up to 1sec here
      // per Art-Net spec, but we're just going to respond immediately...
      final DatagramSocket socket = _getArtPollSocket();
      if (socket != null) {
        DatagramPacket reply = new DatagramPacket(this.artPollReply, this.artPollReply.length, from, ArtNetDatagram.ARTNET_PORT);
        try {
          socket.send(reply);
          if (log) {
            log("[TX] ArtPollReply -> " + packet.getAddress());
          }
        } catch (IOException iox) {
          error(iox, "Failed to send ArtPollReply: " + iox.getMessage());
        }
      }
    }

    private void receivePollReply(DatagramPacket packet, byte[] artNetData, int offset) {
      if (artNetLog.isOn()) {
        log("[RX] ArtPollReply <- " + packet.getAddress() + " (will be ignored)");
      }
    }

    private void receiveDmx(DatagramPacket packet, byte[] artNetData, int offset) {
      final int dmxOffset = offset + ArtNetDatagram.ARTNET_HEADER_LENGTH;

      final int universe =
        (artNetData[offset + ArtNetDatagram.UNIVERSE_LSB] & 0xff) |
        ((artNetData[offset + ArtNetDatagram.UNIVERSE_MSB] & 0xff) << 8);

      if (universe >= MAX_UNIVERSE) {
        error("Ignoring ArtDmx packet, universe exceeds max: " + universe);
        return;
      }

      final int dataLength =
        (artNetData[offset + ArtNetDatagram.DATA_LENGTH_LSB] & 0xff) |
        ((artNetData[offset + ArtNetDatagram.DATA_LENGTH_MSB] & 0xff) << 8);

      System.arraycopy(artNetData, dmxOffset, data[universe], 0, dataLength);

      if (artNetLog.isOn()) {
        log("[RX] ArtDmx <- " + packet.getAddress() + " univ:" + universe + " len:" + dataLength);
      }
    }

    private void receiveSync(DatagramPacket packet, byte[] artNetData, int offset) {
      if (artNetLog.isOn()) {
        log("[RX] ArtSync <- " + packet.getAddress());
      }
    }

  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(LXComponent.KEY_RESET)) {
      this.artNetReceiveActive.reset();
      this.artNetReceiveHost.reset();
      this.artNetReceivePort.reset();
    }
  }

  @Override
  public void dispose() {
    stopReceiver(IOState.STOPPED);
    super.dispose();
    if (this.artPollSocket != null) {
      this.artPollSocket.close();
    }
  }

  public static void error(String log) {
    LX.error("[ArtNet] " + log);
  }

  public static void error(Throwable x, String log) {
    LX.error(x, "[ArtNet] " + log);
  }

  public static void log(String log) {
    LX.log("[ArtNet] " + log);
  }
}
