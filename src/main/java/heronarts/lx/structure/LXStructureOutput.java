/**
 * Copyright 2018- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.structure;

import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.output.ArtNetDatagram;
import heronarts.lx.output.DDPDatagram;
import heronarts.lx.output.IndexBuffer;
import heronarts.lx.output.KinetDatagram;
import heronarts.lx.output.LXOutput;
import heronarts.lx.output.OPCDatagram;
import heronarts.lx.output.OPCSocket;
import heronarts.lx.output.StreamingACNDatagram;
import heronarts.lx.utils.LXUtils;

public class LXStructureOutput extends LXOutput {

  /**
   * A packet definition contains the metadata for what will become one output packet or socket.
   * This is specified by a protocol, transport, network address, and protocol packet signifier,
   * for instance a universe number in ArtNet / KiNET, or an OPC channel.
   *
   * Multiple segments of output may be added to these packets, which requires error-checking
   * for collisions in which multiple outputs are attempting to send different data to the
   * same address.
   */
  private class Packet {

    private final LXFixture.Protocol protocol;
    private final LXFixture.Transport transport;
    private final InetAddress address;
    private final int port;
    private final int universe;
    private int priority;
    private boolean sequenceEnabled;
    private final KinetDatagram.Version kinetVersion;
    private float fps = 0f;

    private final List<IndexBuffer.Segment> segments = new ArrayList<IndexBuffer.Segment>();

    private Packet(LXFixture.Protocol protocol, LXFixture.Transport transport, InetAddress address, int port, int universe, int priority, boolean sequenceEnabled, KinetDatagram.Version kinetVersion) {
      this.protocol = protocol;
      this.transport = transport;
      this.address = address;
      this.port = port;
      this.universe = universe;
      this.priority = priority;
      this.sequenceEnabled = sequenceEnabled;
      this.kinetVersion = kinetVersion;
    }

    private boolean checkOverflow() {
      switch (this.protocol) {
      case ARTNET:
      case SACN:
        if (this.universe >= ArtNetDatagram.MAX_UNIVERSE) {
          outputErrors.add(this.protocol.toString() + this.address.toString() + " - overflow univ " + this.universe);
          return false;
        }
        return true;
      case KINET:
        if (this.universe >= KinetDatagram.MAX_KINET_PORT) {
          outputErrors.add(this.protocol.toString() + this.address.toString() + " - overflow port" + this.universe);
          return false;
        }
        return true;
      case DDP:
      case OPC:
      default:
        outputErrors.add(this.protocol.toString() + this.address.toString() + " - data length overflow");
        return false;
      }
    }

    private void segmentCollision(int collisionStart, int collisionEnd) {
      String err = this.protocol.toString() + this.address.toString() + " - duplicated ";
      switch (this.protocol) {
      case ARTNET:
      case SACN:
        err +=
          "univ " + this.universe +
          ((collisionStart == collisionEnd) ? (" channel " + collisionStart) : (" channels " + collisionStart + "-" + collisionEnd));
        break;
      case KINET:
        err +=
          "port " + this.universe +
          ((collisionStart == collisionEnd) ? (" channel " + collisionStart) : (" channels " + collisionStart + "-" + collisionEnd));
        break;
      case DDP:
        err += "data offset " + this.universe;
        break;
      case OPC:
        err +=
          "channel " + this.universe +
          ((collisionStart == collisionEnd) ? (" offset " + collisionStart) : (" offsets " + collisionStart + "-" + collisionEnd));
        break;
      case NONE:
        break;
      }

      outputErrors.add(err);
    }

    private void addSegment(LXFixture.Segment segment, int startChannel, int chunkStart, int chunkLength, float fps) {
      int endChannel = startChannel + segment.numChannels - 1;
      for (IndexBuffer.Segment existing : this.segments) {
        // If this one starts before an existing...
        if (startChannel < existing.startChannel) {
          // Then check if its end goes over the start, bad news
          if (endChannel >= existing.startChannel) {
            segmentCollision(existing.startChannel, LXUtils.min(endChannel, existing.endChannel));
          }
        } else if (startChannel <= existing.endChannel) {
          // If it's start point is before the end of an exiting one, also bad news
          segmentCollision(startChannel, LXUtils.min(endChannel, existing.endChannel));
        }
      }

      // Translate the fixture-scoped Segment into global address space
      this.segments.add(new IndexBuffer.Segment(segment.toIndexBuffer(chunkStart, chunkLength), segment.byteEncoder, startChannel, segment.getBrightness()));

      // Reduce packet max FPS to the specified limit, if one exists and a lower limit is not already present
      if (fps > 0) {
        if ((this.fps == 0) || (fps < this.fps)) {
          this.fps = fps;
        }
      }
    }

    private IndexBuffer toIndexBuffer() {
      return new IndexBuffer(this.segments);
    }

    private LXOutput toOutput() {
      LXOutput output = null;
      switch (this.protocol) {
      case ARTNET:
        output =
          new ArtNetDatagram(lx, toIndexBuffer(), this.universe)
          .setSequenceEnabled(this.sequenceEnabled);
        break;
      case SACN:
        output = new StreamingACNDatagram(lx, toIndexBuffer(), this.universe).setPriority(this.priority);
        break;
      case KINET:
        output = new KinetDatagram(lx, toIndexBuffer(), this.universe, this.kinetVersion);
        break;
      case OPC:
        if (this.transport == LXFixture.Transport.TCP) {
          output = new OPCSocket(lx, toIndexBuffer(), (byte) this.universe);
        } else {
          output = new OPCDatagram(lx, toIndexBuffer(), (byte) this.universe);
        }
        break;
      case DDP:
        output = new DDPDatagram(lx, toIndexBuffer(), this.universe);
        break;
      case NONE:
        break;
      }
      if (output instanceof InetOutput) {
        ((InetOutput) output).setAddress(this.address).setPort(port);;
      }
      if ((output != null) && (this.fps > 0)) {
        output.framesPerSecond.setValue(this.fps);
      }
      return output;
    }
  }

  private Packet findPacket(LXFixture.Protocol protocol, LXFixture.Transport transport, InetAddress address, int port, int universe, int priority, boolean sequenceEnabled, KinetDatagram.Version kinetVersion) {
    // Check if there's an existing packet for this address space
    for (Packet packet : this.packets) {
      if ((packet.protocol == protocol)
        && (packet.transport == transport)
        && (packet.address.equals(address))
        && (packet.port == port)
        && (packet.universe == universe)
        && (packet.kinetVersion == kinetVersion)) {

        // Priority is the max of any segment contained within
        packet.priority = LXUtils.max(packet.priority, priority);

        // Sequences enabled if any segment demands it
        packet.sequenceEnabled = packet.sequenceEnabled || sequenceEnabled;

        return packet;
      }
    }

    // Create a new packet for this address space
    Packet packet = new Packet(protocol, transport, address, port, universe, priority, sequenceEnabled, kinetVersion);
    this.packets.add(packet);
    return packet;
  }

  private final LXStructure structure;
  private final List<LXOutput> generatedOutputs = new ArrayList<LXOutput>();
  private final List<String> outputErrors = new ArrayList<String>();
  private final List<Packet> packets = new ArrayList<Packet>();

  LXStructureOutput(LX lx, LXStructure structure) throws SocketException {
    super(lx);
    this.structure = structure;
    this.gammaMode.setValue(GammaMode.DIRECT);
  }

  void clear() {
    this.packets.clear();
    for (LXOutput output : this.generatedOutputs) {
      LX.dispose(output);
    }
    this.generatedOutputs.clear();
    this.outputErrors.clear();
    this.structure.outputError.setValue(null);
  }

  void rebuildOutputs() {
    clear();

    // Iterate over all fixtures and build outputs
    for (LXFixture fixture : this.structure.fixtures) {
      rebuildFixtureOutputs(fixture);
    }

    // Generate an output for all those packets!
    for (Packet packet : this.packets) {
      this.generatedOutputs.add(packet.toOutput());
    }

    // Did errors occur? Oh no!
    if (!this.outputErrors.isEmpty()) {
      String str = "Output errors detected.";
      for (String err : this.outputErrors) {
        str += "\n" + err;
      }
      this.structure.outputError.setValue(str);
    }
  }

  private void rebuildFixtureOutputs(LXFixture fixture) {
    if (fixture.deactivate.isOn() || !fixture.enabled.isOn()) {
      return;
    }

    // First iterate recursively over child outputs
    for (LXFixture child : fixture.children) {
      rebuildFixtureOutputs(child);
    }

    // And every output definition for this fixtures
    for (LXFixture.OutputDefinition output : fixture.outputDefinitions) {
      rebuildFixtureOutput(fixture, output);
    }
  }

  private void rebuildFixtureOutput(LXFixture fixture, LXFixture.OutputDefinition output) {
    final LXFixture.Protocol protocol = output.protocol;
    final LXFixture.Transport transport = output.transport;
    final InetAddress address = output.address;
    final int port = output.port;
    int universe = output.universe;
    int channel = output.channel;
    final int priority = output.priority;
    final boolean sequenceEnabled = output.sequenceEnabled;
    final KinetDatagram.Version kinetVersion = output.kinetVersion;
    final float fps = output.fps;
    boolean overflow = false;

    // Find the starting packet for this output definition
    Packet packet = findPacket(protocol, transport, address, port, universe, priority, sequenceEnabled, kinetVersion);
    for (LXFixture.Segment segment : output.segments) {
      if (overflow) {
        // Is it okay for this type to overflow?
        if (!packet.checkOverflow()) {
          return;
        }
        // Roll over to next universe and packet
        overflow = false;
        ++universe;
        channel = 0;
        packet = findPacket(protocol, transport, address, port, universe, priority, sequenceEnabled, kinetVersion);
      }

      int chunkStart = 0;
      int chunkLength = segment.length;
      int availableBytes = protocol.maxChannels - channel;
      if (availableBytes <= 0) {
        outputErrors.add(protocol.toString() + address.toString() + " - invalid channel " + channel + " > " + protocol.maxChannels);
        return;
      }

      // Is there not enough available space? If so, chunk the packet
      while (availableBytes < chunkLength * segment.byteEncoder.getNumBytes()) {
        // How many indices can fit into the remaining available bytes?
        int chunkLimit = availableBytes / segment.byteEncoder.getNumBytes();

        // It could be 0, e.g. channel = 510 byteOrder RGB, can't fit an RGB pixel so
        // we must overflow...
        if (chunkLimit > 0) {
          packet.addSegment(segment, channel, chunkStart, chunkLimit, fps);
          chunkStart += chunkLimit;
          chunkLength -= chunkLimit;
        }

        // Is it okay for this type to overflow?
        if (!packet.checkOverflow()) {
          return;
        }

        // Roll over to the next universe and packet
        ++universe;
        channel = 0;
        availableBytes = protocol.maxChannels;
        packet = findPacket(protocol, transport, address, port, universe, priority, sequenceEnabled, kinetVersion);
      }

      // Add the final chunk (the whole segment in the common case)
      packet.addSegment(segment, channel, chunkStart, chunkLength, fps);
      channel += chunkLength * segment.byteEncoder.getNumBytes();

      // Set flag for whether we need to overflow on the next segment
      overflow = channel >= protocol.maxChannels;
    }
  }

  @Override
  protected void onSend(int[] colors, GammaTable glut, double brightness) {
    // Send all of the generated outputs
    for (LXOutput output : this.generatedOutputs) {
      output.setGammaDelegate(this);
      output.send(colors, brightness);
    }

    // Send any direct fixture outputs
    for (LXFixture fixture : this.structure.fixtures) {
      onSendFixture(fixture, colors, brightness);
    }
  }

  private void onSendFixture(LXFixture fixture, int[] colors, double brightness) {
    // Check enabled state of fixture
    if (!fixture.deactivate.isOn() && fixture.enabled.isOn()) {
      // Adjust by fixture brightness
      brightness *= fixture.brightness.getValue();

      // Recursively send all the fixture's children
      for (LXFixture child : fixture.children) {
        onSendFixture(child, colors, brightness);
      }

      // Then send the fixture's own direct packets
      for (LXOutput output : fixture.outputsDirect) {
        output.setGammaDelegate(this);
        output.send(colors, brightness);
      }
    }
  }

}