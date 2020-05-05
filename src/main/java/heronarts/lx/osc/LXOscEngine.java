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

package heronarts.lx.osc;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.color.ColorParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.StringParameter;

public class LXOscEngine extends LXComponent {

  public final static int DEFAULT_RECEIVE_PORT = 3030;
  public final static int DEFAULT_TRANSMIT_PORT = 3131;

  public final static String DEFAULT_RECEIVE_HOST = "0.0.0.0";
  public final static String DEFAULT_TRANSMIT_HOST = "localhost";

  private final static int DEFAULT_MAX_PACKET_SIZE = 8192;

  public final StringParameter receiveHost = new StringParameter("RX Host",
    DEFAULT_RECEIVE_HOST)
      .setDescription("Hostname to which OSC input socket is bound");

  public final BooleanParameter unknownReceiveHost = (BooleanParameter) new BooleanParameter(
    "Unknown RX Host", false).setMappable(false)
      .setDescription("Set to true if the receive host is unknown");

  public final DiscreteParameter receivePort = (DiscreteParameter) new DiscreteParameter(
    "RX Port", DEFAULT_RECEIVE_PORT, 1, 65535)
      .setDescription("UDP port on which the engine listens for OSC message")
      .setMappable(false).setUnits(LXParameter.Units.INTEGER);

  public final StringParameter transmitHost = (StringParameter) new StringParameter(
    "TX Host", DEFAULT_TRANSMIT_HOST).setMappable(false)
      .setDescription("Hostname to which OSC messages are sent");

  public final BooleanParameter unknownTransmitHost = (BooleanParameter) new BooleanParameter(
    "Unknown TX Host", false).setMappable(false)
      .setDescription("Set to true if the transmit host is unknown");

  public final DiscreteParameter transmitPort = (DiscreteParameter) new DiscreteParameter(
    "TX Port", DEFAULT_TRANSMIT_PORT, 1, 65535)
      .setDescription("UDP port on which the engine transmits OSC messages")
      .setMappable(false).setUnits(LXParameter.Units.INTEGER);

  public final BooleanParameter receiveActive = (BooleanParameter) new BooleanParameter(
    "RX Active", false).setMappable(false)
      .setDescription("Enables or disables OSC engine input");

  public final BooleanParameter transmitActive = (BooleanParameter) new BooleanParameter(
    "TX Active", false).setMappable(false)
      .setDescription("Enables or disables OSC engine output");

  private final List<Receiver> receivers = new ArrayList<Receiver>();

  private Receiver engineReceiver;
  private final EngineListener engineListener = new EngineListener();

  private EngineTransmitter engineTransmitter;

  public LXOscEngine(LX lx) {
    super(lx, "OSC");
    addParameter("receiveHost", this.receiveHost);
    addParameter("receivePort", this.receivePort);
    addParameter("receiveActive", this.receiveActive);
    addParameter("transmitHost", this.transmitHost);
    addParameter("transmitPort", this.transmitPort);
    addParameter("transmitActive", this.transmitActive);
  }

  public LXOscEngine sendMessage(String path, int value) {
    if (this.engineTransmitter != null) {
      this.engineTransmitter.sendMessage(path, value);
    }
    return this;
  }

  public LXOscEngine sendParameter(LXParameter parameter) {
    if (this.engineTransmitter != null) {
      this.engineTransmitter.onParameterChanged(parameter);
    }
    return this;
  }

  /**
   * Gets the OSC address pattern for a parameter
   *
   * @param p parameter
   * @return OSC address
   */
  public static String getOscAddress(LXParameter p) {
    LXComponent component = p.getParent();
    if (component instanceof LXOscComponent) {
      String componentAddress = component.getOscAddress();
      if (componentAddress != null) {
        return componentAddress + "/" + p.getPath();
      }
    }
    return null;
  }

  private class EngineListener implements LXOscListener {

    @Override
    public void oscMessage(OscMessage message) {
      try {
        String[] parts = message.getAddressPattern().getValue().split("/");
        if (parts[1].equals(lx.engine.getPath())) {
          lx.engine.handleOscMessage(message, parts, 2);
        } else {
          throw new OscException();
        }
      } catch (Exception x) {
        error("Failed to handle OSC message: "
          + message.getAddressPattern().getValue());
      }
    }

  }

  public class Transmitter {

    private final byte[] bytes;
    private final ByteBuffer buffer;
    private final DatagramSocket socket;
    protected final DatagramPacket packet;

    private Transmitter(InetAddress address, int port, int bufferSize)
      throws SocketException {
      this.bytes = new byte[bufferSize];
      this.buffer = ByteBuffer.wrap(this.bytes);
      this.packet = new DatagramPacket(this.bytes, this.bytes.length, address,
        port);
      this.socket = new DatagramSocket();
    }

    public void send(OscPacket packet) throws IOException {
      this.buffer.rewind();
      packet.serialize(this.buffer);
      this.packet.setLength(this.buffer.position());
      this.socket.send(this.packet);
    }

    public void setPort(int port) {
      this.packet.setPort(port);
    }

    public void setAddress(InetAddress host) {
      this.packet.setAddress(host);
    }
  }

  private class EngineTransmitter extends Transmitter
    implements LXParameterListener {
    private EngineTransmitter(InetAddress address, int port, int bufferSize)
      throws SocketException {
      super(address, port, bufferSize);
    }

    private final OscMessage oscMessage = new OscMessage("");
    private final OscFloat oscFloat = new OscFloat(0);
    private final OscInt oscInt = new OscInt(0);
    private final OscString oscString = new OscString("");

    @Override
    public void onParameterChanged(LXParameter parameter) {
      if (transmitActive.isOn()) {
        // TODO(mcslee): contemplate accumulating OscMessages into OscBundle
        // and sending once per engine loop?? Probably a bad tradeoff since
        // it would require dynamic memory allocations that we can skip here...
        String address = getOscAddress(parameter);
        if (address != null) {
          oscMessage.clearArguments();
          oscMessage.setAddressPattern(address);
          if (parameter instanceof BooleanParameter) {
            oscInt.setValue(((BooleanParameter) parameter).isOn() ? 1 : 0);
            oscMessage.add(oscInt);
          } else if (parameter instanceof StringParameter) {
            oscString.setValue(((StringParameter) parameter).getString());
            oscMessage.add(oscString);
          } else if (parameter instanceof ColorParameter) {
            oscInt.setValue(((ColorParameter) parameter).getColor());
            oscMessage.add(oscInt);
          } else if (parameter instanceof DiscreteParameter) {
            oscInt.setValue(((DiscreteParameter) parameter).getValuei());
            oscMessage.add(oscInt);
          } else if (parameter instanceof LXNormalizedParameter) {
            oscFloat
              .setValue(((LXNormalizedParameter) parameter).getNormalizedf());
            oscMessage.add(oscFloat);
          } else {
            oscFloat.setValue(parameter.getValuef());
            oscMessage.add(oscFloat);
          }
          sendMessage(oscMessage);
        }
      }
    }

    private void sendMessage(String address, int value) {
      oscMessage.clearArguments();
      oscMessage.setAddressPattern(address);
      oscInt.setValue(value);
      oscMessage.add(oscInt);
      sendMessage(oscMessage);
    }

    private void sendMessage(OscMessage message) {
      try {
        send(oscMessage);
      } catch (IOException iox) {
        error(iox, "Failed to transmit message: "
          + message.getAddressPattern().toString());
      }
    }
  }

  public class Receiver {

    public final int port;
    private final DatagramSocket socket;
    public final SocketAddress address;
    private final DatagramPacket packet;
    private final byte[] buffer;
    private final ReceiverThread thread;

    private final List<OscMessage> threadSafeEventQueue = Collections
      .synchronizedList(new ArrayList<OscMessage>());

    private final List<OscMessage> engineThreadEventQueue = new ArrayList<OscMessage>();

    private final List<LXOscListener> listeners = new ArrayList<LXOscListener>();
    private final List<LXOscListener> listenerSnapshot = new ArrayList<LXOscListener>();

    private Receiver(int port, InetAddress address, int bufferSize)
      throws SocketException {
      this(new DatagramSocket(port, address), port, bufferSize);
    }

    private Receiver(int port, int bufferSize) throws SocketException {
      this(new DatagramSocket(port), port, bufferSize);
    }

    private Receiver(DatagramSocket socket, int port, int bufferSize)
      throws SocketException {
      this.socket = socket;
      this.address = socket.getLocalSocketAddress();
      this.port = port;
      this.buffer = new byte[bufferSize];
      this.packet = new DatagramPacket(this.buffer, bufferSize);
      this.thread = new ReceiverThread();
      this.thread.start();
    }

    public Receiver addListener(LXOscListener listener) {
      Objects.requireNonNull("May not add null LXOscListener");
      if (this.listeners.contains(listener)) {
        throw new IllegalStateException(
          "Cannot add duplicate LXOscEngine.Receiver.LXOscListener: "
            + listener);
      }
      this.listeners.add(listener);
      return this;
    }

    public Receiver removeListener(LXOscListener listener) {
      if (!this.listeners.contains(listener)) {
        throw new IllegalStateException(
          "Cannot remove non-existent LXOscEngine.Receiver.LXOscListener: "
            + listener);
      }
      this.listeners.remove(listener);
      return this;
    }

    class ReceiverThread extends Thread {
      @Override
      public void run() {
        while (!isInterrupted()) {
          try {
            socket.receive(packet);
            try {
              // Parse the OSC packet
              OscPacket oscPacket = OscPacket.parse(packet);

              // Add all messages in the packet to the queue
              if (oscPacket instanceof OscMessage) {
                threadSafeEventQueue.add((OscMessage) oscPacket);
              } else if (oscPacket instanceof OscBundle) {
                for (OscMessage message : (OscBundle) oscPacket) {
                  threadSafeEventQueue.add(message);
                }
              }
            } catch (OscException oscx) {
              error(oscx, "Error handling OscPacket in receiver");
            }
          } catch (IOException iox) {
            if (!isInterrupted()) {
              error(iox, "Exception in OSC listener on port " + port + ":"
                + iox.getLocalizedMessage());
            }
          }
        }
        socket.close();
        log("Stopped OSC listener " + address);
      }
    }

    private void dispatch() {
      this.engineThreadEventQueue.clear();
      synchronized (this.threadSafeEventQueue) {
        this.engineThreadEventQueue.addAll(this.threadSafeEventQueue);
        this.threadSafeEventQueue.clear();
      }
      // TODO(mcslee): do we want to handle NTP timetags?

      // NOTE(mcslee): we iterate this way so that listeners can modify the
      // listener list
      this.listenerSnapshot.clear();
      this.listenerSnapshot.addAll(this.listeners);
      for (OscMessage message : this.engineThreadEventQueue) {
        for (LXOscListener listener : this.listenerSnapshot) {
          listener.oscMessage(message);
        }
      }
    }

    public void stop() {
      this.thread.interrupt();
      this.socket.close();
      this.listeners.clear();
    }
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.receivePort) {
      if (this.receiveActive.isOn()) {
        startReceiver();
      }
    } else if (p == this.receiveHost) {
      try {
        InetAddress.getByName(this.receiveHost.getString());
        this.unknownReceiveHost.setValue(false);
        if (this.receiveActive.isOn()) {
          startReceiver();
        }
      } catch (UnknownHostException uhx) {
        error("Invalid OSC receive host: " + uhx.getLocalizedMessage());
        this.unknownReceiveHost.setValue(true);
        this.receiveActive.setValue(false);
      }
    } else if (p == this.receiveActive) {
      if (this.receiveActive.isOn()) {
        startReceiver();
      } else {
        stopReceiver();
      }
    } else if (p == this.transmitPort) {
      if (this.engineTransmitter != null) {
        this.engineTransmitter.setPort(this.transmitPort.getValuei());
      }
    } else if (p == this.transmitHost) {
      try {
        InetAddress address = InetAddress
          .getByName(this.transmitHost.getString());
        this.unknownTransmitHost.setValue(false);
        if (this.engineTransmitter != null) {
          this.engineTransmitter.setAddress(address);
        }
      } catch (UnknownHostException uhx) {
        error("Invalid OSC output host: " + uhx.getLocalizedMessage());
        this.unknownTransmitHost.setValue(true);
        this.transmitActive.setValue(false);
      }
    } else if (p == this.transmitActive) {
      if (this.transmitActive.isOn()) {
        if (this.unknownTransmitHost.isOn()) {
          this.transmitActive.setValue(false);
        } else {
          startTransmitter();
        }
      }
    }
  }

  private void startReceiver() {
    if (this.engineReceiver != null) {
      stopReceiver();
    }
    String host = this.receiveHost.getString();
    int port = this.receivePort.getValuei();
    try {
      this.engineReceiver = receiver(port, host);
      this.engineReceiver.addListener(this.engineListener);
      this.unknownReceiveHost.setValue(false);
      log("Started OSC listener " + this.engineReceiver.address);
    } catch (UnknownHostException uhx) {
      error("Bad OSC receive host: " + uhx.getLocalizedMessage());
      this.unknownReceiveHost.setValue(true);
      this.receiveActive.setValue(false);
    } catch (SocketException sx) {
      error("Failed to start OSC receiver: " + sx.getLocalizedMessage());
      this.lx.pushError(sx, "Failed to start OSC receiver at " + host + ":"
        + port + "\n" + sx.getLocalizedMessage());
      this.receiveActive.setValue(false);
    }
  }

  private void stopReceiver() {
    if (this.engineReceiver != null) {
      this.engineReceiver.stop();
      this.engineReceiver = null;
    }
  }

  private void startTransmitter() {
    if (this.engineTransmitter == null) {
      String host = this.transmitHost.getString();
      int port = this.transmitPort.getValuei();
      try {
        InetAddress address = InetAddress.getByName(host);
        this.unknownTransmitHost.setValue(false);
        this.engineTransmitter = new EngineTransmitter(address, port,
          DEFAULT_MAX_PACKET_SIZE);
      } catch (UnknownHostException uhx) {
        error("Invalid host: " + uhx.getLocalizedMessage());
        this.unknownTransmitHost.setValue(true);
        this.transmitActive.setValue(false);
      } catch (SocketException sx) {
        error("Could not start transmitter: " + sx.getLocalizedMessage());
        this.lx.pushError(sx, "Failed to start OSC transmitter at " + host + ":"
          + port + "\n" + sx.getLocalizedMessage());
        this.transmitActive.setValue(false);
      }
    }
  }

  public Receiver receiver(int port, String host)
    throws SocketException, UnknownHostException {
    return receiver(port, InetAddress.getByName(host));
  }

  public Receiver receiver(int port, InetAddress address)
    throws SocketException {
    return receiver(port, address, DEFAULT_MAX_PACKET_SIZE);
  }

  public Receiver receiver(int port, InetAddress address, int bufferSize)
    throws SocketException {
    Receiver receiver = new Receiver(port, address, bufferSize);
    this.receivers.add(receiver);
    return receiver;
  }

  public Receiver receiver(int port) throws SocketException {
    return receiver(port, DEFAULT_MAX_PACKET_SIZE);
  }

  public Receiver receiver(int port, int bufferSize) throws SocketException {
    Receiver receiver = new Receiver(port, bufferSize);
    this.receivers.add(receiver);
    return receiver;
  }

  public Transmitter transmitter(String host, int port)
    throws SocketException, UnknownHostException {
    return transmitter(InetAddress.getByName(host), port);
  }

  public Transmitter transmitter(InetAddress address, int port)
    throws SocketException {
    return transmitter(address, port, DEFAULT_MAX_PACKET_SIZE);
  }

  public Transmitter transmitter(InetAddress address, int port, int bufferSize)
    throws SocketException {
    return new Transmitter(address, port, bufferSize);
  }

  /**
   * Invoked by the main engine to dispatch all OSC messages on the input queue.
   */
  public void dispatch() {
    for (Receiver receiver : this.receivers) {
      receiver.dispatch();
    }
  }

  @Override
  public void dispose() {
    super.dispose();
    stopReceiver();
    for (Receiver receiver : this.receivers) {
      receiver.stop();
    }
  }

  private static final String OSC_LOG_PREFIX = "[OSC] ";

  public static final void log(String message) {
    LX.log(OSC_LOG_PREFIX + message);
  }

  public static final void error(String message) {
    LX.error(OSC_LOG_PREFIX + message);
  }

  public static final void error(Exception x, String message) {
    LX.error(x, OSC_LOG_PREFIX + message);
  }

}
