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

package heronarts.lx.model;

import java.util.List;

/**
 * An LXFixture is a base object that represents a raw set of points.
 */
@Deprecated
public interface LXFixture {

  public List<LXPoint> getPoints();

  public static class Utils {

    /**
     * Returns an array of raw integer point indices for all the points in this fixture.
     *
     * @param fixture Fixture
     * @return Integer array of points indices in the fixture
     */
    public static int[] getIndices(LXFixture fixture) {
      List<LXPoint> points = fixture.getPoints();
      int[] indices = new int[points.size()];
      int i = 0;
      for (LXPoint p : points) {
        indices[i++] = p.index;
      }
      return indices;
    }

    /**
     * Returns an array of arrays of raw integer point indices for all the points in
     * this fixture, with no array being longer than the specificed chunk size.
     *
     * @param fixture Fixture
     * @param chunkSize Maximum chunk size for any set of points
     * @return Two-dimensional array of chunks of points indices
     */
    public static int[][] getIndices(LXFixture fixture, int chunkSize) {
      List<LXPoint> points = fixture.getPoints();
      int numPoints = points.size();
      int numChunks = (numPoints - 1 + chunkSize) / chunkSize;
      int[][] chunks = new int[numChunks][];
      for (int i = 0; i < numChunks; ++i) {
        chunks[i] = new int[(i == numChunks - 1) ? (numPoints % chunkSize) : chunkSize];
      }
      int i = 0;
      for (LXPoint p : points) {
        chunks[i / chunkSize][i % chunkSize] = p.index;
        ++i;
      }
      return chunks;
    }
  }
}
