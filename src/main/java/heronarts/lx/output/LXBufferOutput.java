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
import heronarts.lx.utils.LXUtils;

public abstract class LXBufferOutput extends LXOutput {

  /**
   * Various orderings for RGB buffer data
   */
  public enum ByteOrder {
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
  protected LXBufferOutput updateDataBuffer(int[] colors, byte[][] glut, double brightness) {
    byte[] buffer = getDataBuffer();

    for (IndexBuffer.Segment segment : this.indexBuffer.segments) {
      // Determine the appropriate gamma curve for segment brightness
      byte[] gamma = glut[(int) Math.round(255. * brightness * segment.brightness.getValue())];

      // Determine data offsets and byte size
      int offset = getDataBufferOffset() + segment.startChannel;
      ByteOrder byteOrder = segment.byteOrder;
      int[] byteOffset = byteOrder.getByteOffset();
      int numBytes = byteOrder.getNumBytes();
      if (byteOrder.hasWhite) {
        if (numBytes == 1) {
          for (int i = 0; i < segment.indices.length; ++i) {
            int index = segment.indices[i];
            int color = (index >= 0) ? colors[index] : 0;
            int r = ((color >> 16) & 0xff);
            int g = ((color >> 8) & 0xff);
            int b = (color & 0xff);
            int w = (r + b + g) / 3;
            buffer[offset] = gamma[w];
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
            buffer[offset + byteOffset[0]] = gamma[r];
            buffer[offset + byteOffset[1]] = gamma[g];
            buffer[offset + byteOffset[2]] = gamma[b];
            buffer[offset + byteOffset[3]] = gamma[w];
            offset += numBytes;
          }
        }
      } else {
        for (int i = 0; i < segment.indices.length; ++i) {
          int index = segment.indices[i];
          int color = (index >= 0) ? colors[index] : 0;
          buffer[offset + byteOffset[0]] = gamma[((color >> 16) & 0xff)]; // R
          buffer[offset + byteOffset[1]] = gamma[((color >> 8) & 0xff)]; // G
          buffer[offset + byteOffset[2]] = gamma[(color & 0xff)]; // B
          offset += numBytes;
        }
      }
    }

    return this;
  }

}
