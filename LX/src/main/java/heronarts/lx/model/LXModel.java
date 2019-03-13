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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXSerializable;

/**
 * An LXModel is a representation of a set of points in 3-d space. Each LXPoint
 * corresponds to a single point. Models are comprised of Fixtures. An LXFixture
 * specifies a set of points, the Model object takes some number of these and
 * wraps them up with a few useful additional fields, such as the center
 * position of all points and the min/max/range on each axis.
 */
public class LXModel implements LXSerializable {

  public interface Listener {
    public void onModelUpdated(LXModel model);
  }
  /**
   * An immutable list of all the points in this model
   */
  public final LXPoint[] points;
  private final List<LXPoint> pointList;

  public final LXModel[] children;

  private final Map<String, List<LXModel>> childDict =
    new HashMap<String, List<LXModel>>();

  private final Map<String, List<LXModel>> subDict =
    new HashMap<String, List<LXModel>>();

  private String type = "model";

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
    this(new ArrayList<LXPoint>());
  }

  /**
   * Constructs a model from a list of points
   *
   * @param points Points
   */
  public LXModel(List<LXPoint> points) {
    this(points, new LXModel[0]);
  }

  /**
   * Constructs a model with a given set of points and pre-constructed submodels
   *
   * @param points Points in this model
   * @param children Pre-built direct child array
   */
  public LXModel(List<LXPoint> points, LXModel[] children) {
    List<LXPoint> _points = new ArrayList<LXPoint>(points);
    addChildren(children);
    this.children = children;
    this.points = _points.toArray(new LXPoint[0]);
    this.pointList = Collections.unmodifiableList(_points);
    this.size = this.points.length;
    computeAverages();
  }

  /**
   * Constructs a model from the given submodels. The point list is generated from
   * all points in the submodels.
   *
   * @param children Submodels
   */
  public LXModel(LXModel[] children) {
    List<LXPoint> _points = new ArrayList<LXPoint>();
    addChildren(children);
    for (LXModel submodel : children) {
      for (LXPoint p : submodel.points) {
        _points.add(p);
      }
    }
    this.children = new LXModel[children.length];
    System.arraycopy(children, 0, this.children, 0, children.length);
    this.pointList = Collections.unmodifiableList(_points);
    this.points = _points.toArray(new LXPoint[0]);
    this.size = _points.size();
    computeAverages();
  }

  private void addChildren(LXModel[] children) {
    addSubmodels(children, this.childDict, false);
    addSubmodels(children, this.subDict, true);
  }

  private void addSubmodels(LXModel[] submodels, Map<String, List<LXModel>> dict, boolean recurse) {
    for (LXModel submodel : submodels) {
      String type = submodel.getType().toLowerCase();
      List<LXModel> sub = dict.get(type);
      if (sub == null) {
        dict.put(type, sub = new ArrayList<LXModel>());
      }
      sub.add(submodel);
      if (recurse) {
        addSubmodels(submodel.children, dict, recurse);
      }
    }
  }

  public String getType() {
    return this.type;
  }

  public LXModel setType(String type) {
    this.type = type;
    return this;
  }

  private static final List<LXModel> emptyList = Collections.unmodifiableList(new ArrayList<LXModel>());

  /**
   * Returns a list of all the direct child components by particular key
   *
   * @param key Child key, must be all lowercase
   * @return List of direct children by type
   */
  public List<LXModel> children(String key) {
    List<LXModel> children = this.childDict.get(key);
    return (children == null) ? emptyList : children;
  }

  /**
   * Returns a list of all the submodel components by particular key, at any level of depth
   *
   * @param key Submodel key, must be all lowercase
   * @return List of submodels
   */
  public List<LXModel> sub(String key) {
    List<LXModel> sub = this.subDict.get(key);
    return (sub == null) ? emptyList : sub;
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
      for (LXModel submodel : this.children) {
        // NOTE: normals are relative to master model,
        // flip to false for sub-models
        submodel.update(false, true);
      }
    }
    computeAverages();
    if (normalize) {
      normalizePoints();
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

  public int[] toIndexBuffer() {
    int[] indexBuffer = new int[this.points.length];
    int i = 0;
    for (LXPoint p : this.points) {
      indexBuffer[i++] = p.index;
    }
    return indexBuffer;
  }

  /**
   * Recompute the averages in this model
   *
   * @return this
   */
  public LXModel computeAverages() {
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
  public LXModel normalizePoints() {
    // TODO(mcslee): correct when to call this... since submodels may normalize
    // points differently... we only want it relative to top-level model
    for (LXPoint p : this.points) {
      p.normalize(this);
    }
    return this;
  }

  public List<LXPoint> getPoints() {
    return this.pointList;
  }

  @Override
  public void save(LX lx, JsonObject object) {
    object.addProperty(LXComponent.KEY_CLASS, getClass().getName());
  }

  @Override
  public void load(LX lx, JsonObject object) {
    // No-op, but can be overridden for custom model classes...
  }

}
