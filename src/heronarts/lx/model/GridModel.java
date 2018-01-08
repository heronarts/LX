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

import heronarts.lx.transform.LXVector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Model of points in a simple grid.
 */
public class GridModel extends LXModel {

  public static class Metrics {
    public final int width;

    public final int height;

    private float xSpacing = 1;

    private float ySpacing = 1;

    private LXVector origin = new LXVector(0, 0, 0);

    public Metrics(int width, int height) {
      this.width = width;
      this.height = height;
    }

    public Metrics setXSpacing(float xSpacing) {
      this.xSpacing = xSpacing;
      return this;
    }

    public Metrics setYSpacing(float ySpacing) {
      this.ySpacing = ySpacing;
      return this;
    }

    public Metrics setSpacing(float xSpacing, float ySpacing) {
      this.xSpacing = xSpacing;
      this.ySpacing = ySpacing;
      return this;
    }

    public float xSpacing() {
      return this.xSpacing();
    }

    public float ySpacing() {
      return this.ySpacing();
    }

    public Metrics setOrigin(float x, float y, float z) {
      this.origin.set(x, y, z);
      return this;
    }

    public Metrics setOrigin(LXVector v) {
      this.origin.set(v);
      return this;
    }
  }

  public class Strip extends LXModel {

    public int index;

    public final GridPoint[] points;

    public Strip(int index, List<LXPoint> pointList) {
      super(pointList);
      this.index = index;
      LXPoint[] points = ((LXModel) this).points;
      this.points = new GridPoint[points.length];
      System.arraycopy(points, 0, this.points, 0, points.length);
    }
  }

  /**
   * Points in the model
   */
  public final GridPoint[] points;

  /**
   * All the rows in this model
   */
  public final List<Strip> rows;

  /**
   * All the columns in this model
   */
  public final List<Strip> columns;

  /**
   * Metrics for the grid
   */
  public final Metrics metrics;

  /**
   * Width of the grid
   */
  public final int width;

  /**
   * Height of the grid
   */
  public final int height;

  /**
   * Spacing on the x-axis
   */
  public final float xSpacing;

  /**
   * Spacing on the y-axis
   */
  public final float ySpacing;

  /**
   * Constructs a grid model with the given metrics
   *
   * @param metrics Metrics
   */
  public GridModel(Metrics metrics) {
    super(new Fixture(metrics));

    this.metrics = metrics;
    this.width = metrics.width;
    this.height = metrics.height;
    this.xSpacing = metrics.xSpacing;
    this.ySpacing = metrics.ySpacing;

    LXPoint[] points = ((LXModel) this).points;
    this.points = new GridPoint[points.length];
    System.arraycopy(points, 0, this.points, 0, points.length);

    List<Strip> rows = new ArrayList<Strip>();
    for (int y = 0; y < height; ++y) {
      List<LXPoint> row = new ArrayList<LXPoint>();
      for (int x = 0; x < width; ++x) {
        row.add(points[x + y * this.width]);
      }
      rows.add(new Strip(y, row));
    }
    this.rows = Collections.unmodifiableList(rows);

    List<Strip> columns = new ArrayList<Strip>();
    for (int x = 0; x < width; ++x) {
      List<LXPoint> column = new ArrayList<LXPoint>();
      for (int y = 0; y < height; ++y) {
        column.add(points[x + y * this.width]);
      }
      columns.add(new Strip(x, column));
    }
    this.columns = Collections.unmodifiableList(columns);
  }

  /**
   * Constructs a uniformly spaced grid model of the given size with all pixels
   * apart by a unit of 1.
   *
   * @param width Width in pixels
   * @param height Height in pixels
   */
  public GridModel(int width, int height) {
    this(width, height, 1, 1);
  }

  /**
   * Constructs a grid model with specified x and y spacing
   *
   * @param width Number of nodes in x dimension
   * @param height Number of nodes in y dimension
   * @param xSpacing Spacing of nodes in x dimension
   * @param ySpacing Spacing of nodes in y dimension
   */
  public GridModel(int width, int height, float xSpacing, float ySpacing) {
    this(new Metrics(width, height).setSpacing(xSpacing, ySpacing));
  }

  public GridPoint getPoint(int x, int y) {
    return this.points[y * this.width + x];
  }

  private static class Fixture extends LXAbstractFixture {
    private Fixture(Metrics metrics) {
      for (int y = 0; y < metrics.height; ++y) {
        for (int x = 0; x < metrics.width; ++x) {
          this.points.add(new GridPoint(
            x, y,
            metrics.origin.x + x * metrics.xSpacing,
            metrics.origin.y + y * metrics.ySpacing,
            metrics.origin.z
          ));
        }
      }
    }
  }

}
