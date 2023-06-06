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

public abstract class LXBufferOutput extends LXOutput {

  public interface ByteEncoder {

    /**
     * Number of bytes per pixel in this encoding
     *
     * @return Number of bytes per pixel in this encoder
     */
    public int getNumBytes();

    /**
     * Writes the bytes for a color pixel to an output
     *
     * @param argb Integer color value, ARGB format
     * @param gamma Gamma lookup table
     * @param output Output byte array
     * @param offset Offset to write at in output array
     */
    public void writeBytes(int argb, GammaTable.Curve gamma, byte[] output, int offset);

  }

  /**
   * Most common static orderings for RGB buffer data
   */
  public enum ByteOrder implements ByteEncoder {
    RGB(new int[] { 0, 1, 2 }),
    RBG(new int[] { 0, 2, 1 }),
    GRB(new int[] { 1, 0, 2 }),
    GBR(new int[] { 2, 0, 1 }),
    BRG(new int[] { 1, 2, 0 }),
    BGR(new int[] { 2, 1, 0 }),

    RGBW(new int[] { 0, 1, 2, 3 }, true),
    RBGW(new int[] { 0, 2, 1, 3 }, true),
    GRBW(new int[] { 1, 0, 2, 3 }, true),
    GBRW(new int[] { 2, 0, 1, 3 }, true),
    BRGW(new int[] { 1, 2, 0, 3 }, true),
    BGRW(new int[] { 2, 1, 0, 3 }, true),

    WRGB(new int[] { 1, 2, 3, 0 }, true),
    WRBG(new int[] { 1, 3, 2, 0 }, true),
    WGRB(new int[] { 2, 1, 3, 0 }, true),
    WGBR(new int[] { 3, 1, 2, 0 }, true),
    WBRG(new int[] { 2, 3, 1, 0 }, true),
    WBGR(new int[] { 3, 2, 1, 0 }, true),

    W(new int[] { 0 }, true);

    /**
     * Byte offet is array of integer offsets in order RGBW, indicating
     * at what position the red, green, blue, and optionally white byte
     * go in the payload.
     */
    private final int[] byteOffset;

    public final boolean hasWhite;

    ByteOrder(int[] byteOffset) {
      this(byteOffset, false);
    }

    ByteOrder(int[] byteOffset, boolean hasWhite) {
      this.byteOffset = byteOffset;
      this.hasWhite = hasWhite;
    }

    public int getNumBytes() {
      return this.byteOffset.length;
    }

    public int[] getByteOffset() {
      return this.byteOffset;
    }

    public void writeBytes(int color, GammaTable.Curve gamma, byte[] output, int offset) {
      final int r = ((color >> 16) & 0xff);
      final int g = ((color >> 8) & 0xff);
      final int b = (color & 0xff);
      if (this.hasWhite) {
        if (this.byteOffset.length == 1) {
          output[offset] = gamma.white[(r + b + g) / 3];
        } else {
          final int w = (r < g) ? ((r < b) ? r : b) : ((g < b) ? g : b);
          output[offset + this.byteOffset[0]] = gamma.red[r-w];
          output[offset + this.byteOffset[1]] = gamma.green[g-w];
          output[offset + this.byteOffset[2]] = gamma.blue[b-w];
          output[offset + this.byteOffset[3]] = gamma.white[w];
        }
      } else {
        output[offset + this.byteOffset[0]] = gamma.red[r];
        output[offset + this.byteOffset[1]] = gamma.green[g];
        output[offset + this.byteOffset[2]] = gamma.blue[b];
      }
    }
  };

  protected final IndexBuffer indexBuffer;

  protected LXBufferOutput(LX lx, IndexBuffer indexBuffer) {
    super(lx);
    this.indexBuffer = indexBuffer;
  }

  /**
   * Subclasses should provide a handle to a raw byte buffer
   *
   * @return Raw byte buffer for output data
   */
  protected abstract byte[] getDataBuffer();

  /**
   * Offset into raw byte buffer where color data is written
   *
   * @return Offset into raw byte buffer for color data
   */
  protected abstract int getDataBufferOffset();

  /**
   * Helper for subclasses to copy a list of points into the data buffer at a
   * specified offset. For many subclasses which wrap RGB buffers, onSend() will
   * be a simple call to this method with the right parameters.
   *
   * @param colors Array of color values
   * @param glut Look-up tables for gamma-corrected brightness values by brightness
   * @param brightness Master brightness
   * @return this
   */
  protected LXBufferOutput updateDataBuffer(int[] colors, GammaTable glut, double brightness) {
    final byte[] buffer = getDataBuffer();

    for (IndexBuffer.Segment segment : this.indexBuffer.segments) {
      // Determine the appropriate gamma curve for segment brightness
      final int glutIndex = (int) Math.round(255. * brightness * segment.brightness.getValue());
      final GammaTable.Curve gamma = glut.level[glutIndex];

      final ByteEncoder byteEncoder = segment.byteEncoder;
      final int numBytes = byteEncoder.getNumBytes();

      // Determine data offsets and byte size
      int offset = getDataBufferOffset() + segment.startChannel;

      // TODO(mcslee): determine if there's actually any performance gain at all here by
      // putting branches outside of the for loops? If not just nuke this...
      if (byteEncoder instanceof ByteOrder) {
        final ByteOrder byteOrder = (ByteOrder) byteEncoder;
        final int[] byteOffset = byteOrder.getByteOffset();

        if (byteOrder.hasWhite) {
          if (numBytes == 1) {
            for (int i = 0; i < segment.indices.length; ++i) {
              int index = segment.indices[i];
              int color = (index >= 0) ? colors[index] : 0;
              int r = ((color >> 16) & 0xff);
              int g = ((color >> 8) & 0xff);
              int b = (color & 0xff);
              int w = (r + b + g) / 3;
              buffer[offset] = gamma.white[w];
              offset += numBytes;
            }
          } else {
            for (int i = 0; i < segment.indices.length; ++i) {
              int index = segment.indices[i];
              int color = (index >= 0) ? colors[index] : 0;
              int r = ((color >> 16) & 0xff);
              int g = ((color >> 8) & 0xff);
              int b = (color & 0xff);
              int w = (r < g) ? ((r < b) ? r : b) : ((g < b) ? g : b);
              r -= w;
              g -= w;
              b -= w;
              buffer[offset + byteOffset[0]] = gamma.red[r];
              buffer[offset + byteOffset[1]] = gamma.green[g];
              buffer[offset + byteOffset[2]] = gamma.blue[b];
              buffer[offset + byteOffset[3]] = gamma.white[w];
              offset += numBytes;
            }
          }
        } else {
          for (int i = 0; i < segment.indices.length; ++i) {
            int index = segment.indices[i];
            int color = (index >= 0) ? colors[index] : 0;
            buffer[offset + byteOffset[0]] = gamma.red[((color >> 16) & 0xff)]; // R
            buffer[offset + byteOffset[1]] = gamma.green[((color >> 8) & 0xff)]; // G
            buffer[offset + byteOffset[2]] = gamma.blue[(color & 0xff)]; // B
            offset += numBytes;
          }
        }
      } else {

        // Generic ByteEncoder implementation
        for (int i = 0; i < segment.indices.length; ++i) {
          int index = segment.indices[i];
          int color = (index >= 0) ? colors[index] : 0;
          byteEncoder.writeBytes(color, gamma, buffer, offset);
          offset += numBytes;
        }
      }
    }

    return this;
  }

}
