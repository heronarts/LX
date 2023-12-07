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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.audio.ADM;
import heronarts.lx.audio.Envelop;
import heronarts.lx.audio.Reaper;
import heronarts.lx.color.ColorParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
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

  public enum IOState {
    STOPPED,
    BINDING,
    BOUND,
    UNKNOWN_HOST,
    SOCKET_ERROR
  };

  public final BooleanParameter receiveActive =
    new BooleanParameter("RX Active", false)
    .setMappable(false)
    .setDescription("Enables or disables OSC engine input");

  public final StringParameter receiveHost =
    new StringParameter("RX Host", DEFAULT_RECEIVE_HOST)
    .setDescription("Hostname to which OSC input socket is bound");

  public final BooleanParameter unknownReceiveHost =
    new BooleanParameter("Unknown RX Host", false)
    .setMappable(false)
    .setDescription("Set to true if the receive host is unknown");

  public final EnumParameter<IOState> receiveState =
    new EnumParameter<IOState>("RX State", IOState.STOPPED)
    .setMappable(false)
    .setDescription("The state of the OSC receiver");

  public final DiscreteParameter receivePort =
    new DiscreteParameter("RX Port", DEFAULT_RECEIVE_PORT, 1, 65535)
    .setDescription("UDP port on which the engine listens for OSC message")
    .setMappable(false).setUnits(LXParameter.Units.INTEGER);

  public final BooleanParameter transmitActive =
    new BooleanParameter("TX Active", false)
    .setMappable(false)
    .setDescription("Enables or disables OSC engine output");

  public final StringParameter transmitHost = (StringParameter)
    new StringParameter("TX Host", DEFAULT_TRANSMIT_HOST)
    .setMappable(false)
    .setDescription("Hostname to which OSC messages are sent");

  public final BooleanParameter unknownTransmitHost =
    new BooleanParameter("Unknown TX Host", false)
    .setMappable(false)
    .setDescription("Set to true if the transmit host is unknown");

  public final EnumParameter<IOState> transmitState =
    new EnumParameter<IOState>("TX State", IOState.STOPPED)
    .setMappable(false)
    .setDescription("The state of the OSC transmitter");

  public final DiscreteParameter transmitPort =
    new DiscreteParameter("TX Port", DEFAULT_TRANSMIT_PORT, 1, 65535)
    .setDescription("UDP port on which the engine transmits OSC messages")
    .setMappable(false)
    .setUnits(LXParameter.Units.INTEGER);

  public final BooleanParameter logInput =
    new BooleanParameter("Log OSC Input", false)
    .setDescription("Whether to log all OSC input messages");

  public final BooleanParameter logOutput =
    new BooleanParameter("Log OSC Output", false)
    .setDescription("Whether to log all OSC output messages");

  private final List<Receiver> receivers =
    new CopyOnWriteArrayList<Receiver>();

  private Receiver engineReceiver;
  private final EngineListener engineListener = new EngineListener();

  private EngineTransmitter engineTransmitter;

  private final List<LXOscListener> listeners =
    new ArrayList<LXOscListener>();

  private final LXOscQueryServer oscQueryServer;
  private final Zeroconf zeroconf;

  private static class Zeroconf {

    private final String serviceName;
    private final JmDNS jmdns;
    private boolean registered = false;

    private static Zeroconf create(String serviceName) {
      try {
        return new Zeroconf(serviceName);
      } catch (Exception x) {
        error(x, "Failed to create Zeroconf instance");
      }
      return null;
    }

    private Zeroconf(String serviceName) throws IOException {
      this.serviceName = serviceName;
      this.jmdns = JmDNS.create(InetAddress.getLocalHost());
    }

    private void register(int port) {
      unregister();
      try {
        log("Registering zeroconf OSC services on port " + port);
        this.jmdns.registerService(ServiceInfo.create(
          "_osc._udp.local.",
          this.serviceName + ":" + port,
          port,
          ""
        ));
        this.registered = true;
        this.jmdns.registerService(ServiceInfo.create(
          "_oscjson._tcp.local.",
          this.serviceName + ":" + port,
          port,
          ""
        ));
      } catch (Exception x) {
        error(x, "Failed to register zeroconf services");
      }
    }

    private void unregister() {
      unregister(false);
    }

    private void unregister(final boolean close) {
      final boolean registered = this.registered;
      if (registered || close) {
        // NOTE(mcslee): horrible hack here... firing this off on a separate thread
        // because this call can unfortunately block for many seconds
        new Thread(() -> {
          if (registered) {
            this.jmdns.unregisterAllServices();
          }
          if (close) {
            try {
              this.jmdns.close();
            } catch (IOException iox) {
              error(iox, "Exception closing JmDNS");
            }
          }
        }).start();
      }
      this.registered = false;
    }

    private void dispose() {
      unregister(true);
    }
  }

  public LXOscEngine(LX lx) {
    super(lx, "OSC");

    if (lx.flags.zeroconf) {
      this.oscQueryServer = new LXOscQueryServer(lx);
      this.zeroconf = Zeroconf.create(lx.flags.zeroconfServiceName);
    } else {
      this.oscQueryServer = null;
      this.zeroconf = null;
    }

    // Note order of ioActive parameter coming after host / port, this saves some
    // churn on a reload, update the host and port before trying to bind
    addParameter("receiveHost", this.receiveHost);
    addParameter("receivePort", this.receivePort);
    addParameter("receiveActive", this.receiveActive);
    addParameter("transmitHost", this.transmitHost);
    addParameter("transmitPort", this.transmitPort);
    addParameter("transmitActive", this.transmitActive);
    addParameter("logInput", this.logInput);
    addParameter("logOutput", this.logOutput);
  }

  public LXOscEngine addListener(LXOscListener listener) {
    Objects.requireNonNull("May not add null LXOscListener");
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException(
        "Cannot add duplicate LXOscEngine.LXOscListener: "
          + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  public LXOscEngine removeListener(LXOscListener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException(
        "Cannot remove non-existent LXOscEngine.LXOscListener: "
          + listener);
    }
    this.listeners.remove(listener);
    return this;
  }

  public LXOscEngine sendMessage(String path, int value) {
    if (this.engineTransmitter != null) {
      this.engineTransmitter.sendMessage(path, value);
    }
    return this;
  }

  public LXOscEngine sendMessage(String path, float value) {
    if (this.engineTransmitter != null) {
      this.engineTransmitter.sendMessage(path, value);
    }
    return this;
  }

  public LXOscEngine sendMessage(String path, String value) {
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
    if (p instanceof LXOscComponent) {
      return ((LXOscComponent) p).getOscAddress();
    }
    final LXComponent component = p.getParent();
    if ((component instanceof LXOscComponent) && component.isValidOscParameter(p)) {
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
        if (logInput.isOn()) {
          log("[RX] " + message.toString());
        }

        String raw = message.getAddressPattern().getValue();
        String trim = raw.trim();
        if (trim != raw) {
          error("Trailing whitespace in OSC address pattern: \"" + raw + "\"");
        }
        String[] parts = trim.split("/");
        if (parts[1].equals(lx.engine.getPath())) {
          lx.engine.handleOscMessage(message, parts, 2);
        } else if (parts[1].equals(ADM.ADM_OSC_PATH)) {
          lx.engine.audio.adm.handleAdmOscMessage(message, parts, 1);
        } else if (parts[1].equals(Envelop.ENVELOP_OSC_PATH)) {
          lx.engine.audio.envelop.handleEnvelopOscMessage(message, parts, 1);
        }  else if (parts[1].equals(Reaper.REAPER_OSC_PATH)) {
          lx.engine.audio.reaper.handleReaperOscMessage(message, parts, 1);
        } else if (LXOscEngine.this.listeners.isEmpty()) {
          throw new OscException();
        }
      } catch (Exception x) {
        error("Failed to handle OSC message: " + message.getAddressPattern().getValue());
      }

      // Dispatch to custom listeners
      for (LXOscListener listener : LXOscEngine.this.listeners) {
        try {
          listener.oscMessage(message);
        } catch (Exception x) {
          error(x, "Uncaught exception in custom listener: " + message.getAddressPattern().getValue());
        }
      }
    }

  }

  public class Transmitter {

    private final byte[] bytes;
    private final ByteBuffer buffer;
    private final DatagramSocket socket;
    protected final DatagramPacket packet;

    private Transmitter(InetAddress address, int port, int bufferSize) throws SocketException {
      this.bytes = new byte[bufferSize];
      this.buffer = ByteBuffer.wrap(this.bytes);
      this.packet = new DatagramPacket(this.bytes, this.bytes.length, address, port);
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

    public void dispose() {
      if (this.socket != null) {
        this.socket.close();
      }
    }
  }

  private class EngineTransmitter extends Transmitter implements LXParameterListener {
    private EngineTransmitter(InetAddress address, int port, int bufferSize) throws SocketException {
      super(address, port, bufferSize);
    }

    private final OscMessage oscMessage = new OscMessage("");
    private final OscFloat oscFloat = new OscFloat(0);
    private final OscInt oscInt = new OscInt(0);
    private final OscString oscString = new OscString("");

    private boolean isActive() {
      return transmitActive.isOn() && (transmitState.getEnum() == IOState.BOUND);
    }

    @Override
    public void onParameterChanged(LXParameter parameter) {
      if (isActive()) {
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
            oscInt.setValue(((ColorParameter) parameter).getBaseColor());
            oscMessage.add(oscInt);
          } else if (parameter instanceof DiscreteParameter) {
            oscInt.setValue(((DiscreteParameter) parameter).getBaseValuei());
            oscMessage.add(oscInt);
          } else if (parameter instanceof LXNormalizedParameter) {
            LXNormalizedParameter normalizedParameter = (LXNormalizedParameter) parameter;
            if (normalizedParameter.getOscMode() == LXNormalizedParameter.OscMode.ABSOLUTE) {
              oscFloat.setValue(normalizedParameter.getBaseValuef());
            } else {
              oscFloat.setValue(normalizedParameter.getBaseNormalizedf());
            }
            oscMessage.add(oscFloat);
          } else {
            oscFloat.setValue(parameter.getBaseValuef());
            oscMessage.add(oscFloat);
          }
          _sendMessage(oscMessage);
        }
      }
    }

    private void sendMessage(String address, int value) {
      if (isActive()) {
        oscMessage.clearArguments();
        oscMessage.setAddressPattern(address);
        oscInt.setValue(value);
        oscMessage.add(oscInt);
        _sendMessage(oscMessage);
      }
    }

    private void sendMessage(String address, float value) {
      if (isActive()) {
        oscMessage.clearArguments();
        oscMessage.setAddressPattern(address);
        oscFloat.setValue(value);
        oscMessage.add(oscFloat);
        _sendMessage(oscMessage);
      }
    }

    private void sendMessage(String address, String value) {
      if (isActive()) {
        oscMessage.clearArguments();
        oscMessage.setAddressPattern(address);
        oscString.setValue(value);
        oscMessage.add(oscString);
        _sendMessage(oscMessage);
      }
    }

    // Internal helper, this should not be used directly as
    // it does not redundantly check for isActive(), which all
    // the above helpers will have done before constructing
    // OscMessage objects.
    private void _sendMessage(OscMessage message) {
      try {
        if (logOutput.isOn()) {
          log("[TX] " + message.toString());
        }
        send(oscMessage);
      } catch (IOException iox) {
        error(iox, "Failed to transmit message: " + message.getAddressPattern().toString());
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

    private final AtomicBoolean hasMessages = new AtomicBoolean(false);

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
                hasMessages.set(true);
              } else if (oscPacket instanceof OscBundle) {
                for (OscMessage message : (OscBundle) oscPacket) {
                  threadSafeEventQueue.add(message);
                }
                hasMessages.set(true);
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
      if (this.hasMessages.compareAndSet(true, false)) {
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
            try {
              listener.oscMessage(message);
            } catch (Exception x) {
              error(x, "Uncaught exception in OSC listener: " + message.getAddressPattern().getValue());
            }
          }
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
        stopReceiver(IOState.UNKNOWN_HOST);
      }
    } else if (p == this.receiveActive) {
      if (this.receiveActive.isOn()) {
        startReceiver();
      } else {
        stopReceiver(IOState.STOPPED);
      }
    } else if (p == this.transmitPort) {
      if (this.engineTransmitter != null) {
        this.engineTransmitter.setPort(this.transmitPort.getValuei());
      }
    } else if (p == this.transmitHost) {
      try {
        InetAddress address = InetAddress.getByName(this.transmitHost.getString());
        this.unknownTransmitHost.setValue(false);
        if (this.engineTransmitter != null) {
          this.engineTransmitter.setAddress(address);
          this.transmitState.setValue(IOState.BOUND);
        }
      } catch (UnknownHostException uhx) {
        error("Invalid OSC output host: " + uhx.getLocalizedMessage());
        this.unknownTransmitHost.setValue(true);
        this.transmitState.setValue(IOState.UNKNOWN_HOST);
      }
    } else if (p == this.transmitActive) {
      if (this.transmitActive.isOn()) {
        if (this.unknownTransmitHost.isOn()) {
          this.transmitState.setValue(IOState.UNKNOWN_HOST);
        } else {
          startTransmitter();
        }
      } else {
        this.transmitState.setValue(IOState.STOPPED);
      }
    }
  }

  private void startReceiver() {
    if (this.engineReceiver != null) {
      stopReceiver(IOState.STOPPED);
    }
    String host = this.receiveHost.getString();
    int port = this.receivePort.getValuei();
    try {
      this.receiveState.setValue(IOState.BINDING);
      this.engineReceiver = receiver(port, host);
      this.engineReceiver.addListener(this.engineListener);
      this.unknownReceiveHost.setValue(false);
      this.receiveState.setValue(IOState.BOUND);
      log("Started OSC listener " + this.engineReceiver.address);
      if (this.oscQueryServer != null) {
        this.oscQueryServer.bind(port);
      }
      if (this.zeroconf != null) {
        this.zeroconf.register(port);
      }
    } catch (UnknownHostException uhx) {
      error("Bad OSC receive host: " + uhx.getLocalizedMessage());
      this.unknownReceiveHost.setValue(true);
      stopReceiver(IOState.UNKNOWN_HOST);
    } catch (SocketException sx) {
      error("Failed to start OSC receiver: " + sx.getLocalizedMessage());
      this.lx.pushError(sx, "Failed to start OSC receiver at " + host + ":"
        + port + "\n" + sx.getLocalizedMessage());
      stopReceiver(IOState.SOCKET_ERROR);
    }
  }

  private void stopReceiver(IOState state) {
    if (this.engineReceiver != null) {
      this.engineReceiver.stop();
      this.engineReceiver = null;
    }
    if (this.oscQueryServer != null) {
      this.oscQueryServer.unbind();
    }
    if (this.zeroconf != null) {
      this.zeroconf.unregister();
    }
    this.receiveState.setValue(state);
  }

  private void startTransmitter() {
    if (this.engineTransmitter == null) {
      String host = this.transmitHost.getString();
      int port = this.transmitPort.getValuei();
      try {
        this.transmitState.setValue(IOState.BINDING);
        InetAddress address = InetAddress.getByName(host);
        this.unknownTransmitHost.setValue(false);
        this.engineTransmitter = new EngineTransmitter(address, port, DEFAULT_MAX_PACKET_SIZE);
        this.transmitState.setValue(IOState.BOUND);
      } catch (UnknownHostException uhx) {
        error("Invalid host: " + uhx.getLocalizedMessage());
        this.unknownTransmitHost.setValue(true);
        this.transmitState.setValue(IOState.UNKNOWN_HOST);
      } catch (SocketException sx) {
        error("Could not start transmitter: " + sx.getLocalizedMessage());
        this.lx.pushError(sx, "Failed to start OSC transmitter at " + host + ":"
          + port + "\n" + sx.getLocalizedMessage());
        this.transmitState.setValue(IOState.SOCKET_ERROR);
      }
    } else {
      this.transmitState.setValue(IOState.BOUND);
    }
  }

  public Receiver receiver(int port, String host) throws SocketException, UnknownHostException {
    return receiver(port, InetAddress.getByName(host));
  }

  public Receiver receiver(int port, InetAddress address) throws SocketException {
    return receiver(port, address, DEFAULT_MAX_PACKET_SIZE);
  }

  public Receiver receiver(int port, InetAddress address, int bufferSize) throws SocketException {
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

  public Transmitter transmitter(String host, int port) throws SocketException, UnknownHostException {
    return transmitter(InetAddress.getByName(host), port);
  }

  public Transmitter transmitter(InetAddress address, int port) throws SocketException {
    return transmitter(address, port, DEFAULT_MAX_PACKET_SIZE);
  }

  public Transmitter transmitter(InetAddress address, int port, int bufferSize) throws SocketException {
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
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(LXComponent.KEY_RESET)) {
      this.receiveActive.setValue(false);
      this.transmitActive.setValue(false);
    }
  }

  @Override
  public void dispose() {
    super.dispose();
    this.listeners.clear();
    if (this.engineTransmitter != null) {
      this.engineTransmitter.dispose();
    }
    if (this.oscQueryServer != null) {
      this.oscQueryServer.unbind();
    }
    if (this.zeroconf != null) {
      this.zeroconf.dispose();
    }
    stopReceiver(IOState.STOPPED);
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

  public static final void error(Throwable x, String message) {
    LX.error(x, OSC_LOG_PREFIX + message);
  }

}
