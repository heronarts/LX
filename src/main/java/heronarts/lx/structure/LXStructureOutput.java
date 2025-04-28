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
import java.util.Arrays;
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
    private final int universe; // holds data offset for DDP/OPC
    private int priority;
    private boolean sequenceEnabled;
    private final KinetDatagram.Version kinetVersion;
    private float fps = 0f;
    private final int[] collisionMask;

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
      this.collisionMask = new int[(int) Math.ceil(protocol.maxChannels / Integer.SIZE)];
    }

    private void segmentCollision(int collisionStart, int collisionEnd) {
      String err = this.protocol.toString() + this.address.toString() + " - collisions in ";
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
        err +=
          "data offset " + this.universe +
          ((collisionStart == collisionEnd) ? (" + [" + collisionStart + "]") : (" + [" + collisionStart + "-" + collisionEnd + "]"));
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

    private void checkSegmentCollisions(IndexBuffer.Segment segment) {
      int minCollision = -1, maxCollision = -1;
      int outputStride = 1;
      int bytesPerIndex = 1;
      if (segment.byteEncoder != null) {
        bytesPerIndex = segment.byteEncoder.getNumBytes();
        outputStride = segment.outputStride;
      }
      if (bytesPerIndex != outputStride) {
        int indexChannel = segment.startChannel;
        for (int i = 0; i < segment.indices.length; ++i) {
          for (int j = 0; j < bytesPerIndex; ++j) {
            int channel = indexChannel + j;
            int bucket = channel / Integer.SIZE;
            int mask = 1 << (channel % Integer.SIZE);
            if (0 != (this.collisionMask[bucket] & mask)) {
              if (minCollision < 0) {
                minCollision = channel;
              }
              maxCollision = channel;
            }
            this.collisionMask[bucket] |= mask;
          }
          indexChannel += outputStride;
        }
      } else {
        final int requiredChannels = segment.getRequiredChannels();
        for (int channel = segment.startChannel; channel < requiredChannels; ++channel) {
          int bucket = channel / Integer.SIZE;
          int mask = 1 << (channel % Integer.SIZE);
          if (0 != (this.collisionMask[bucket] & mask)) {
            if (minCollision < 0) {
              minCollision = channel;
            }
            maxCollision = channel;
          }
          this.collisionMask[bucket] |= mask;
        }
      }

      if (minCollision >= 0) {
        segmentCollision(minCollision, maxCollision);
      }
    }

    private void addStaticSegment(byte[] staticBytes, int startChannel, float fps) {
      final IndexBuffer.Segment segment = new IndexBuffer.Segment(staticBytes, startChannel);
      checkSegmentCollisions(segment);
      this.segments.add(segment);

      // Reduce packet max FPS to the specified limit, if one exists and a lower limit is not already present
      if (fps > 0) {
        if ((this.fps == 0) || (fps < this.fps)) {
          this.fps = fps;
        }
      }
    }

    private void addDynamicSegment(LXFixture.Segment segment, int startChannel, int chunkStart, int chunkLength, float fps) {
      // Translate the fixture-scoped Segment into global address space
      final IndexBuffer.Segment indexSegment = new IndexBuffer.Segment(
        segment.toIndexBuffer(chunkStart, chunkLength),
        segment.byteEncoder,
        startChannel,
        segment.outputStride,
        segment.getBrightness()
      );
      checkSegmentCollisions(indexSegment);
      this.segments.add(indexSegment);

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
      try {
        rebuildFixtureOutput(fixture, output);
      } catch (FixtureOutputException fox) {
        this.outputErrors.add(fox.getMessage());
      }
    }
  }

  private static class FixtureOutputException extends Exception {
    private static final long serialVersionUID = 1L;

    public FixtureOutputException(String str) {
      super(str);
    }
  }

  private class FixtureOutputState {

    private final LXFixture.OutputDefinition output;

    private int universe;
    private int channel;

    private Packet packet;

    private FixtureOutputState(LXFixture.OutputDefinition output) throws FixtureOutputException {
      this.output = output;
      this.universe = output.universe;
      this.channel = output.channel;
      if (this.channel >= output.protocol.maxChannels) {
        throw new FixtureOutputException(output.protocol.toString() + output.address.toString() + " - invalid channel " + this.channel + " > " + output.protocol.maxChannels);
      }
      this.packet = _findPacket();
    }

    private int availableBytes() {
      return output.protocol.maxChannels - this.channel;
    }

    private void checkUniverseOverflow(int overflowChannel, boolean force) throws FixtureOutputException {
      if (force || (this.channel >= output.protocol.maxChannels)) {
        // Does this output protocol support universes? And/or are we at the last one?
        switch (output.protocol) {
        case ARTNET:
        case SACN:
          if (this.universe >= ArtNetDatagram.MAX_UNIVERSE) {
            throw new FixtureOutputException(output.protocol.toString() + output.address.toString() + " - overflow univ " + this.universe);
          }
          break;
        case KINET:
          if (this.universe >= KinetDatagram.MAX_KINET_PORT) {
            throw new FixtureOutputException(output.protocol.toString() + output.address.toString() + " - overflow port" + output.universe);
          }
          break;
        case DDP:
        case OPC:
        default:
          throw new FixtureOutputException(output.protocol.toString() + output.address.toString() + " - data length overflow");
        }

        // All good made it thru, let's bump up to the next universe
        ++this.universe;
        this.channel = overflowChannel;
        this.packet = _findPacket();
      }
    }

    private void addStaticBytes(byte[] staticBytes) throws FixtureOutputException {
      if (staticBytes == null || staticBytes.length == 0) {
        return;
      }

      checkUniverseOverflow(0, false);

      int offset = 0;
      int remaining = staticBytes.length;
      int available = availableBytes();
      while (available < remaining) {
        _addStaticSegment(Arrays.copyOfRange(staticBytes, offset, offset + available));
        offset += available;
        remaining -= available;
        checkUniverseOverflow(0, false);
        available = availableBytes();
      }
      _addStaticSegment(Arrays.copyOfRange(staticBytes, offset, offset + remaining));
    }

    private void _addStaticSegment(byte[] staticBytes) {
      this.packet.addStaticSegment(staticBytes, this.channel, this.output.fps);
      this.channel += staticBytes.length;
    }

    private void addDynamicSegment(LXFixture.Segment segment) throws FixtureOutputException {
      if (segment.length == 0) {
        return;
      }

      checkUniverseOverflow(0, false);

      int availableBytes = availableBytes();

      int chunkStart = 0;
      int chunkLength = segment.length;

      // Is there not enough available space for the entire segment? If so, chunk the packet
      while (availableBytes < segment.getRequiredBytes(chunkLength)) {
        // How many indices can fit into the remaining available bytes? Add padding in the
        // case that stride exceeds bytes, since we don't necessarily need to fit those
        // padding bytes into this universe
        int padding = segment.outputStride - segment.byteEncoder.getNumBytes();
        int chunkLimit = (availableBytes + padding) / segment.outputStride;
        int overflowChannel = 0;

        // It could be 0, e.g. channel = 510 byteOrder RGB, can't fit an RGB pixel so
        // we must overflow...
        if (chunkLimit > 0) {
          _addDynamicSegment(segment, chunkStart, chunkLimit);
          // If the output stride walks us *into* the next universe, we
          // preserve that spacing (e.g. sending a white byte at
          // channel offset 3 with stride 4, the 2nd universe it lands in
          // should also start on channel 3)
          if (this.channel > output.protocol.maxChannels) {
            overflowChannel = this.channel % output.protocol.maxChannels;
          }
          chunkStart += chunkLimit;
          chunkLength -= chunkLimit;
        }

        checkUniverseOverflow(overflowChannel, true);
        availableBytes = availableBytes();
      }

      // Add the final chunk (the whole segment in the common case)
      _addDynamicSegment(segment, chunkStart, chunkLength);
    }

    private void _addDynamicSegment(LXFixture.Segment segment, int chunkStart, int chunkLength) {
      this.packet.addDynamicSegment(segment, this.channel, chunkStart, chunkLength, this.output.fps);
      this.channel += chunkLength * segment.outputStride;
    }

    private Packet _findPacket() {
      return findPacket(
        output.protocol,
        output.transport,
        output.address,
        output.port,
        this.universe,
        output.priority,
        output.sequenceEnabled,
        output.kinetVersion
      );
    }

  }

  private void rebuildFixtureOutput(LXFixture fixture, LXFixture.OutputDefinition output) throws FixtureOutputException {
    if (output.segments.length == 0) {
      // Don't waste time here...
      return;
    }

    // State object for iterating through packets which may overflow from
    // one universe to the next
    final FixtureOutputState state = new FixtureOutputState(output);

    // Iterate over all segments and generate output components
    for (LXFixture.Segment segment : output.segments) {
      state.addStaticBytes(segment.headerBytes);
      state.addDynamicSegment(segment);
      state.addStaticBytes(segment.footerBytes);
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