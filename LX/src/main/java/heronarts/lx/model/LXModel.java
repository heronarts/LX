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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An LXModel is a representation of a set of points in 3-d space. Each LXPoint
 * corresponds to a single point. Models are comprised of Fixtures. An LXFixture
 * specifies a set of points, the Model object takes some number of these and
 * wraps them up with a few useful additional fields, such as the center
 * position of all points and the min/max/range on each axis.
 */
public class LXModel implements LXFixture {

  public interface Listener {
    public void onModelUpdated(LXModel model);
  }

  /**
   * An immutable list of all the points in this model
   */
  public final LXPoint[] points;

  private final List<LXPoint> pointList;

  /**
   * An immutable list of all the fixtures in this model
   */
  public final List<LXFixture> fixtures;

  /**
   * Number of points in the model
   */
  public final int size;

  /**
   * Center of the model in x space
   */
  public float cx;

  /**
   * Center of the model in y space
   */
  public float cy;

  /**
   * Center of the model in z space
   */
  public float cz;

  /**
   * Average x point
   */
  public float ax;

  /**
   * Average y point
   */
  public float ay;

  /**
   * Average z points
   */
  public float az;

  /**
   * Minimum x value
   */
  public float xMin;

  /**
   * Maximum x value
   */
  public float xMax;

  /**
   * Range of x values
   */
  public float xRange;

  /**
   * Minimum y value
   */
  public float yMin;

  /**
   * Maximum y value
   */
  public float yMax;

  /**
   * Range of y values
   */
  public float yRange;

  /**
   * Minimum z value
   */
  public float zMin;

  /**
   * Maximum z value
   */
  public float zMax;

  /**
   * Range of z values
   */
  public float zRange;

  /**
   * Smallest radius from origin
   */
  public float rMin;

  /**
   * Greatest radius from origin
   */
  public float rMax;

  /**
   * Range of radial values
   */
  public float rRange;

  /**
   * Constructs a null model with no points
   */
  public LXModel() {
    this(new LXFixture[] {});
  }

  /**
   * Constructs a model from a list of points
   *
   * @param points Points
   */
  public LXModel(List<LXPoint> points) {
    this(new BasicFixture(points));
  }

  /**
   * Constructs a model with one fixture
   *
   * @param fixture Fixture
   */
  public LXModel(LXFixture fixture) {
    this(new LXFixture[] { fixture });
  }

  /**
   * Constructs a model with the given fixtures
   *
   * @param fixtures Fixtures
   */
  public LXModel(LXFixture[] fixtures) {
    List<LXFixture> _fixtures = new ArrayList<LXFixture>();
    List<LXPoint> _points = new ArrayList<LXPoint>();
    for (LXFixture fixture : fixtures) {
      _fixtures.add(fixture);
      for (LXPoint point : fixture.getPoints()) {
        _points.add(point);
      }
    }

    this.size = _points.size();
    this.pointList = Collections.unmodifiableList(_points);
    this.points = _points.toArray(new LXPoint[0]);
    this.fixtures = Collections.unmodifiableList(_fixtures);
    average();
    normalize();
  }

  private final List<Listener> listeners = new ArrayList<Listener>();

  public final LXModel addListener(Listener listener) {
    if (listener == null) {
      throw new IllegalArgumentException("Cannot add null modellistener");
    }
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate listener to model " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  public final LXModel removeListener(Listener listener) {
    this.listeners.remove(listener);
    return this;
  }

  /**
   * Update the meta-values in this model. Re-normalizes the points relative to
   * this model and recomputes its averages
   *
   * @return this
   */
  public LXModel update() {
    return update(true, false);
  }

  /**
   * Update the averages and mins/maxes of the model.
   *
   * @param normalize If true, normalize the points relative to this model
   * @return this
   */
  public LXModel update(boolean normalize) {
    return update(normalize, false);
  }

  /**
   * Updates the averages and min/maxes of the model
   *
   * @param normalize If true, normalize the points relative to this model
   * @param recurse If true, compute averages for sub-models as well
   * @return this
   */
  public LXModel update(boolean normalize, boolean recurse) {
    // Recursively update values of sub-models
    if (recurse) {
      for (LXFixture fixture : this.fixtures) {
        if (fixture instanceof LXModel) {
          // NOTE: normals are relative to master model,
          // flip to false for sub-models
          ((LXModel) fixture).average();
        }
      }
    }
    average();
    if (normalize) {
      normalize();
    }
    bang();
    return this;
  }

  public LXModel bang() {
    // Notify the listeners of this model that it has changed
    for (Listener listener : this.listeners) {
      listener.onModelUpdated(this);
    }
    return this;
  }

  /**
   * Recompute the averages in this model
   *
   * @return this
   */
  public LXModel average() {
    float ax = 0, ay = 0, az = 0;
    float xMin = 0, xMax = 0, yMin = 0, yMax = 0, zMin = 0, zMax = 0, rMin = 0, rMax = 0;

    boolean firstPoint = true;
    for (LXPoint p : this.points) {
      ax += p.x;
      ay += p.y;
      az += p.z;
      if (firstPoint) {
        xMin = xMax = p.x;
        yMin = yMax = p.y;
        zMin = zMax = p.z;
        rMin = rMax = p.r;
      } else {
        if (p.x < xMin)
          xMin = p.x;
        if (p.x > xMax)
          xMax = p.x;
        if (p.y < yMin)
          yMin = p.y;
        if (p.y > yMax)
          yMax = p.y;
        if (p.z < zMin)
          zMin = p.z;
        if (p.z > zMax)
          zMax = p.z;
        if (p.r < rMin)
          rMin = p.r;
        if (p.r > rMax)
          rMax = p.r;
      }
      firstPoint = false;
    }
    this.ax = ax / Math.max(1, this.points.length);
    this.ay = ay / Math.max(1, this.points.length);
    this.az = az / Math.max(1, this.points.length);
    this.xMin = xMin;
    this.xMax = xMax;
    this.xRange = xMax - xMin;
    this.yMin = yMin;
    this.yMax = yMax;
    this.yRange = yMax - yMin;
    this.zMin = zMin;
    this.zMax = zMax;
    this.zRange = zMax - zMin;
    this.rMin = rMin;
    this.rMax = rMax;
    this.rRange = rMax - rMin;
    this.cx = xMin + xRange / 2.f;
    this.cy = yMin + yRange / 2.f;
    this.cz = zMin + zRange / 2.f;

    return this;
  }

  /**
   * Sets the normalized values of all the points in this model (xn, yn, zn)
   * relative to this model's absolute bounds.
   */
  public void normalize() {
    // TODO(mcslee): correct when to call this... since submodels may normalize
    // points differently... we only want it relative to top-level model
    for (LXPoint p : this.points) {
      p.normalize(this);
    }
  }

  public List<LXPoint> getPoints() {
    return this.pointList;
  }

  private final static class BasicFixture implements LXFixture {
    private final List<LXPoint> points;

    private BasicFixture(List<LXPoint> points) {
      this.points = points;
    }

    public List<LXPoint> getPoints() {
      return this.points;
    }
  }

}
