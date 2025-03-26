/**
 * Copyright 2021- Mark C. Slee, Heron Arts LLC
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

import java.util.List;

import heronarts.lx.parameter.FixedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.utils.LXUtils;

/**
 * An IndexBuffer is a representation of all the pixels that an output packet will
 * send. It can be comprised of multiple segments at various offsets, each of which
 * may have its own byte ordering. The segments are not required to be strictly ordered
 * or continuous, there may be gaps in the buffer (for instance DMX channels 1-10 may
 * be send along with channels 30-40, leaving the space between blank).
 *
 * Each segment itself is continuous, specifying the indices into the master color buffer
 * that are to be sent, along with their byte ordering.
 */
public class IndexBuffer {

  /**
   * Placeholder value that can be used in index buffers to output an empty
   * pixel with all byte values of 0, rather than a value from the colors
   * array.
   */
  public static final int EMPTY_PIXEL = -1;

  /**
   * A Segment is a continous portion of an index buffer which specifies the indices
   * into the final color buffer which are to be sent, along with the byte ordering.
   */
  public static class Segment {
    /**
     * Static bytes to be sent.
     */
    public final byte[] staticBytes;

    /**
     * Globally indexed buffer of point indices in this segment, relative to master
     * color buffer.
     */
    public final int[] indices;

    /**
     * Byte order to use when sending the points in this segment, which implicitly
     * specifies how many bytes are used per pixel
     */
    public final LXBufferOutput.ByteEncoder byteEncoder;

    /**
     * Starting channel in output packet for this segment (e.g. ArtNet channel)
     */
    public final int startChannel;

    /**
     * How many bytes spacing is placed between each pixel value, typically this
     * is byteEncoder.numBytes() but may be specified larger
     */
    public final int outputStride;

    /**
     * Parameter to track the brightness level of this segment
     */
    public final LXParameter brightness;

    /**
     * Default RGB segment for a given set of indices at offset 0
     *
     * @param indices Point indices for this segment
     */
    public Segment(int[] indices) {
      this(indices, LXBufferOutput.ByteOrder.RGB);
    }

    /**
     * Segment with specified indices and byte ordering
     *
     * @param indices Array of indices into master color buffer
     * @param byteEncoder Byte encoder to use when sending
     */
    public Segment(int[] indices, LXBufferOutput.ByteEncoder byteEncoder) {
      this(indices, byteEncoder, 0);
    }

    /**
     * Segment with specified indices, byte ordering and channel offset
     *
     * @param indices Array of indices into master color buffer
     * @param byteEncoder Byte encoder to  use when sending send
     * @param channel Channel offset in the output packet
     */
    public Segment(int[] indices, LXBufferOutput.ByteEncoder byteEncoder, int channel) {
      this(indices, byteEncoder, channel, new FixedParameter(1));
    }

    /**
     * Segment with specified indices, byte ordering and channel offset
     *
     * @param indices Array of indices into master color buffer
     * @param byteEncoder Byte encoder to send
     * @param channel Channel offset in the output packet
     * @param brightness Brightness of this segment
     */
    public Segment(int[] indices, LXBufferOutput.ByteEncoder byteEncoder, int channel, LXParameter brightness) {
      this(indices, byteEncoder, channel, byteEncoder.getNumBytes(), brightness);
    }

    /**
     * Segment with specified indices, byte ordering and channel offset, custom output stride
     *
     * @param indices Array of indices into master color buffer
     * @param byteEncoder Byte encoder to send
     * @param channel Channel offset in the output packet
     * @param brightness Brightness of this segment
     */
    public Segment(int[] indices, LXBufferOutput.ByteEncoder byteEncoder, int channel, int outputStride, LXParameter brightness) {
      this.indices = indices;
      this.byteEncoder = byteEncoder;
      this.outputStride = outputStride;
      this.startChannel = channel;
      this.brightness = brightness;

      // Unused here
      this.staticBytes = null;
    }

    /**
     * Segment that represents flat, static byte data
     *
     * @param staticBytes Fixed byte array data in the segment
     * @param channel Channel to copy data at
     */
    public Segment(byte[] staticBytes, int channel) {
      this.staticBytes = staticBytes;
      this.startChannel = channel;

      // Unused here
      this.indices = null;
      this.outputStride = 1;
      this.byteEncoder = null;
      this.brightness = null;
    }

    /**
     * Gets the number of channels required in an output packet to send this segment
     *
     * @return required channels to send this segment
     */
    public int getRequiredChannels() {
      int numChannels = 0;
      if (this.byteEncoder != null) {
        numChannels = this.outputStride * this.indices.length;
        if (this.indices.length > 0) {
          numChannels -= this.outputStride - this.byteEncoder.getNumBytes();
        }
      } else {
        numChannels = this.staticBytes.length;
      }
      return this.startChannel + numChannels;
    }
  }

  /**
   * All of the segments in this index buffer
   */
  public final Segment[] segments;

  /**
   * The total number of single-byte DMX channels required by this index buffer
   * to fit all of the data it contains
   */
  public final int numChannels;

  /**
   * Makes an IndexBuffer with a single segment, RGB with offset 0
   *
   * @param indices Indices for segment
   */
  public IndexBuffer(int[] indices) {
    this(new Segment(indices));
  }

  /**
   * Makes an IndexBuffer with a single segment, given indices and byte order, at offset 0
   *
   * @param indices Indices for single segment
   * @param byteOrder Byte order
   */
  public IndexBuffer(int[] indices, LXBufferOutput.ByteOrder byteOrder) {
    this(new Segment(indices, byteOrder));
  }

  /**
   * Makes a single-semgent IndexBuffer with specified indices, byte ordering and channel offset
   *
   * @param indices Array of indices into master color buffer
   * @param byteOrder Byte ordering to send
   * @param channel Channel offset in the output packet
   */
  public IndexBuffer(int[] indices, LXBufferOutput.ByteOrder byteOrder, int channel) {
    this(new Segment(indices, byteOrder, channel));
  }

  /**
   * Makes a single-semgent IndexBuffer with specified indices, byte ordering and channel offset
   *
   * @param indices Array of indices into master color buffer
   * @param byteOrder Byte ordering to send
   * @param channel Channel offset in the output packet
   * @param brightness Brightness for the output packet
   */
  public IndexBuffer(int[] indices, LXBufferOutput.ByteOrder byteOrder, int channel, LXParameter brightness) {
    this(new Segment(indices, byteOrder, channel, brightness));
  }

  /**
   * Makes an IndexBuffer with the given list of segments.
   *
   * @param segments Segments to include in index buffer
   */
  public IndexBuffer(List<Segment> segments) {
    this(segments.toArray(new Segment[0]));
  }

  /**
   * Makes an IndexBuffer with the given list of segments.
   *
   * @param segments Segments to include in index buffer
   */
  public IndexBuffer(Segment ... segments) {
    this.segments = segments;
    int numChannels = 0;
    for (Segment segment : segments) {
      numChannels = LXUtils.max(numChannels, segment.getRequiredChannels());
    }
    this.numChannels = numChannels;
  }
}
