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

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.color.LXColor;
import heronarts.lx.osc.LXOscEngine.IOState;
import heronarts.lx.output.ArtNetDatagram;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.TriggerParameter;

public class LXDmxEngine extends LXComponent {

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

  public final DiscreteParameter artNetReceivePort =
    new DiscreteParameter("Art-Net RX Port", ArtNetDatagram.ARTNET_PORT, 1, 65535)
    .setDescription("UDP port on which the engine listens for Art-Net")
    .setMappable(false)
    .setUnits(LXParameter.Units.INTEGER);

  public final TriggerParameter artNetActivity = (TriggerParameter)
    new TriggerParameter("Art-Net Activity")
    .setMappable(false)
    .setDescription("Triggers when art-net input is received");

  private ArtNetReceiver artNetReceiver = null;

  public static final int MAX_CHANNEL = 512;
  public static final int MAX_UNIVERSE = 512;

  private final byte[][] data =
    new byte[MAX_UNIVERSE][ArtNetDatagram.MAX_DATA_LENGTH];

  public LXDmxEngine(LX lx) {
    super(lx);
    addParameter("artNetReceivePort", this.artNetReceivePort);
    addParameter("artNetReceiveActive", this.artNetReceiveActive);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.artNetReceiveActive) {
      stopReceiver(IOState.STOPPED);
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
    final int artNetPort = this.artNetReceivePort.getValuei();
    try {
      final DatagramSocket socket = new DatagramSocket(artNetPort);
      this.artNetReceiver = new ArtNetReceiver(socket);
      this.artNetReceiver.start();
      this.artNetReceiveState.setValue(IOState.BOUND);
    } catch (SocketException sx) {
      error(sx, "Could not create Art-Net listener socket: " + sx.getMessage());
      this.lx.pushError("Failed to start Art-Net receiver on port " + artNetPort + "\n" + sx.getLocalizedMessage());
      this.artNetReceiveState.setValue(IOState.SOCKET_ERROR);
    } catch (Throwable x) {
      error(x, "Unknown error starting art-net receiver: " + x.getMessage());
      this.lx.pushError("Uknown error starting Art-Net receiver on port " + artNetPort + "\n" + x.getLocalizedMessage());
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

  public double getNormalized(int universe, int channel) {
    return (this.data[universe][channel] & 0xff) / 255.;
  }

  private class ArtNetReceiver extends Thread {

    private final DatagramSocket socket;

    private ArtNetReceiver(DatagramSocket socket) {
      super("Art-Net Receiver Thread");
      this.socket = socket;
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
      log("Starting Art-Net listener on port " + ArtNetDatagram.ARTNET_PORT);
      final byte[] buffer = new byte[ArtNetDatagram.ARTNET_HEADER_LENGTH + ArtNetDatagram.MAX_DATA_LENGTH];
      final DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

      while (!isInterrupted()) {
        try {
          this.socket.receive(packet);
          artNetActivity.trigger();
          final byte[] dmxData = packet.getData();
          final int offset = packet.getOffset();
          final int dmxOffset = offset + ArtNetDatagram.ARTNET_HEADER_LENGTH;
          if (!checkHeader(dmxData, offset)) {
            continue;
          }

          final int universe =
            (dmxData[offset + ArtNetDatagram.UNIVERSE_LSB] & 0xff) |
            ((dmxData[offset + ArtNetDatagram.UNIVERSE_MSB] & 0xff) << 8);

          if (universe >= MAX_UNIVERSE) {
            error("Ignoring packet, universe exceeds max: " + universe);
            continue;
          }

          final int dataLength =
            (dmxData[offset + ArtNetDatagram.DATA_LENGTH_LSB] & 0xff) |
            ((dmxData[offset + ArtNetDatagram.DATA_LENGTH_MSB] & 0xff) << 8);

          System.arraycopy(dmxData, dmxOffset, data[universe], 0, dataLength);

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
      log("Art-Net receiver thread finished.");
    }
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(LXComponent.KEY_RESET)) {
      this.artNetReceiveActive.reset();
      this.artNetReceivePort.reset();
    }
  }

  @Override
  public void dispose() {
    stopReceiver(IOState.STOPPED);
    super.dispose();
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
