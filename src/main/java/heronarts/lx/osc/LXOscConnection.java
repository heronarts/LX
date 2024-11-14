/**
 * Copyright 2024- Mark C. Slee, Heron Arts LLC
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

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.osc.LXOscEngine.EngineTransmitter;
import heronarts.lx.osc.LXOscEngine.IOState;
import heronarts.lx.osc.LXOscEngine.Receiver;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

public abstract class LXOscConnection extends LXComponent {

  public static final String DEFAULT_INPUT_HOST = "0.0.0.0";
  public static final String DEFAULT_OUTPUT_HOST = "localhost";

  private final boolean isInput = (this instanceof Input);

  private final String prefix = isInput ? "RX" : "TX";

  public final BooleanParameter active =
    new BooleanParameter(prefix + " Active", false)
    .setMappable(false)
    .setDescription(isInput ?
      "Enables or disables OSC engine input" :
      "Enables or disables OSC engine output"
    );

  public final StringParameter host =
    new StringParameter(prefix + " Host", isInput ? DEFAULT_INPUT_HOST : DEFAULT_OUTPUT_HOST)
    .setMappable(false)
    .setDescription(isInput ?
      "Hostname to which OSC input socket is bound" :
      "Hostname to which OSC messages are sent"
    );

  private int _defaultInputPort() {
    int max = lx.engine.osc.receivePort.getValuei();
    for (LXOscConnection connection : lx.engine.osc.inputs) {
      max = LXUtils.max(max, connection.port.getValuei());
    }
    return max + 1;
  }

  private int _defaultOutputPort() {
    int max = lx.engine.osc.transmitPort.getValuei();
    for (LXOscConnection connection : lx.engine.osc.outputs) {
      max = LXUtils.max(max, connection.port.getValuei());
    }
    return max + 1;
  }

  public final DiscreteParameter port =
    new DiscreteParameter(prefix + " Port", isInput ? _defaultInputPort() : _defaultOutputPort(), 1, 65535)
    .setMappable(false)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription(this.isInput ?
      "UDP port on which the engine listens for OSC messages" :
      "UDP port on which the engine transmits OSC messages"
    );

  public final BooleanParameter log =
    new BooleanParameter(prefix + " Log", false)
    .setDescription(isInput ?
      "Whether to log OSC input messages" :
      "Whether to log OSC output messages"
    );

  public final BooleanParameter unknownHost =
    new BooleanParameter("Unknown Host", false)
    .setMappable(false)
    .setDescription(isInput ?
      "Set to true if the receive host is unknown" :
      "Set to true if the transmit host is unknown"
    );

  public final EnumParameter<IOState> state =
    new EnumParameter<IOState>(prefix + " State", IOState.STOPPED)
    .setMappable(false)
    .setDescription("The state of the OSC connection");

  public final TriggerParameter activity =
    new TriggerParameter(prefix + " Activity")
    .setMappable(false)
    .setDescription("Triggers when there is activity on the OSC connection");

  LXOscConnection(LX lx) {
    super(lx);

    // NOTE: order matters here, put active last so that when load() is called the
    // host and port are recalled first, then the connection turned on
    addParameter("host", this.host);
    addParameter("port", this.port);
    addParameter("log", this.log);
    addParameter("active", this.active);
  }

  /**
   * An OSC input connection
   */
  public static class Input extends LXOscConnection {

    private Receiver receiver;

    Input(LX lx) {
      super(lx);
    }

    @Override
    public void onParameterChanged(LXParameter p) {
      super.onParameterChanged(p);
      if (p == this.port) {
        if (this.active.isOn()) {
          startReceiver();
        }
      } else if (p == this.host) {
        try {
          InetAddress.getByName(this.host.getString());
          this.unknownHost.setValue(false);
          if (this.active.isOn()) {
            startReceiver();
          }
        } catch (UnknownHostException uhx) {
          LXOscEngine.error("Invalid OSC receive host: " + uhx.getLocalizedMessage());
          this.unknownHost.setValue(true);
          stopReceiver(IOState.UNKNOWN_HOST);
        }
      } else if (p == this.active) {
        if (this.active.isOn()) {
          startReceiver();
        } else {
          stopReceiver(IOState.STOPPED);
        }
      }
    }

    private void startReceiver() {
      if (this.receiver != null) {
        stopReceiver(IOState.STOPPED);
      }
      String host = this.host.getString();
      int port = this.port.getValuei();
      try {
        this.state.setValue(IOState.BINDING);
        this.receiver = lx.engine.osc.receiver(port, host);
        this.receiver.setLog(this.log);
        this.receiver.setActivity(this.activity);
        this.receiver.addListener(lx.engine.osc.engineListener);
        this.unknownHost.setValue(false);
        this.state.setValue(IOState.BOUND);
        LXOscEngine.log("Started OSC listener " + this.receiver.address);
      } catch (UnknownHostException uhx) {
        LXOscEngine.error("Bad OSC receive host: " + uhx.getLocalizedMessage());
        this.unknownHost.setValue(true);
        stopReceiver(IOState.UNKNOWN_HOST);
      } catch (SocketException sx) {
        LXOscEngine.error("Failed to start OSC receiver: " + sx.getLocalizedMessage());
        this.lx.pushError(sx, "Failed to start OSC receiver at " + host + ":"
          + port + "\n" + sx.getLocalizedMessage());
        stopReceiver(IOState.SOCKET_ERROR);
      }
    }

    private void stopReceiver(IOState state) {
      if (this.receiver != null) {
        this.receiver.stop();
        this.receiver = null;
      }
      this.state.setValue(state);
    }

    @Override
    public void dispose() {
      stopReceiver(IOState.STOPPED);
      super.dispose();
    }
  }

  /**
   * An OSC output connection
   */
  public static class Output extends LXOscConnection {

    EngineTransmitter transmitter;

    Output(LX lx) {
      super(lx);
    }

    @Override
    public void onParameterChanged(LXParameter p) {
      super.onParameterChanged(p);
      if (p == this.port) {
        if (this.transmitter != null) {
          this.transmitter.setPort(this.port.getValuei());
        }
      } else if (p == this.host) {
        try {
          InetAddress address = InetAddress.getByName(this.host.getString());
          this.unknownHost.setValue(false);
          if (this.transmitter != null) {
            this.transmitter.setAddress(address);
            this.state.setValue(IOState.BOUND);
          }
        } catch (UnknownHostException uhx) {
          LXOscEngine.error("Invalid OSC output host: " + uhx.getLocalizedMessage());
          this.unknownHost.setValue(true);
          this.state.setValue(IOState.UNKNOWN_HOST);
        }
      } else if (p == this.active) {
        if (this.active.isOn()) {
          if (this.unknownHost.isOn()) {
            this.state.setValue(IOState.UNKNOWN_HOST);
          } else {
            startTransmitter();
          }
        } else {
          // No need to actually stop sending stuff here? Handled
          // by the isActive() check in EngineTransmitter, so that we
          // can easily toggle back on
          this.state.setValue(IOState.STOPPED);
        }
      }
    }

    private void startTransmitter() {
      if (this.transmitter == null) {
        String host = this.host.getString();
        int port = this.port.getValuei();
        try {
          this.state.setValue(IOState.BINDING);
          InetAddress address = InetAddress.getByName(host);
          this.unknownHost.setValue(false);
          this.transmitter = lx.engine.osc.transmitter(address, port, this);
          this.transmitter.setLog(this.log);
          this.transmitter.setActivity(this.activity);
          this.state.setValue(IOState.BOUND);
        } catch (UnknownHostException uhx) {
          LXOscEngine.error("Invalid host: " + uhx.getLocalizedMessage());
          this.unknownHost.setValue(true);
          this.state.setValue(IOState.UNKNOWN_HOST);
        } catch (SocketException sx) {
          LXOscEngine.error("Could not start transmitter: " + sx.getLocalizedMessage());
          this.lx.pushError(sx, "Failed to start OSC transmitter at " + host + ":"
            + port + "\n" + sx.getLocalizedMessage());
          this.state.setValue(IOState.SOCKET_ERROR);
        }
      } else {
        this.state.setValue(IOState.BOUND);
      }
    }

    @Override
    public void dispose() {
      if (this.transmitter != null) {
        this.transmitter.dispose();
      }
      super.dispose();
    }
  }
}
