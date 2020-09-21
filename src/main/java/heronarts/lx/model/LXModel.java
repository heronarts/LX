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
import java.util.Objects;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.output.LXOutput;
import heronarts.lx.transform.LXMatrix;
import heronarts.lx.transform.LXVector;

/**
 * An LXModel is a representation of a set of points in 3D space. Each LXPoint
 * corresponds to a single point. Though the positions of points may be updated,
 * a model is immutable. Its overall structure, number of points, and hierarchy
 * of submodels may not be changed once it has been created.
 *
 * This class should generally not be used directly to construct model objects.
 * It is heavily preferred to use the {@link heronarts.lx.structure.LXStructure}
 * and {@link heronarts.lx.structure.LXFixture} APIs to dynamically generate
 * a model.
 *
 * In cases where fixed-model programming is preferred, it is recommended to use
 * the {@link LXModelBuilder} class to aid in model construction.
 */
public class LXModel implements LXSerializable {

  /**
   * A collection of helpful pre-defined constants for the most common model
   * key types.
   */
  public static class Key {
    public final static String MODEL = "model";
    public final static String GRID = "grid";
    public final static String ROW = "row";
    public final static String COLUMN = "column";
    public final static String STRIP = "strip";
    public final static String POINT = "point";
    public final static String ARC = "arc";
  }

  /**
   * Listener interface for changes to the location of points in a model
   */
  public interface Listener {
    /**
     * Invoked when the geometry of a model has been updated. Note that its
     * structure may not be changed, only the position of points may be
     * different. The hierarchy of submodels and children is always unchanged.
     *
     * @param model Model
     */
    public void modelGenerationUpdated(LXModel model);
  }

  /**
   * A transform matrix that represents the positioning of this model
   * in the global space, if part of the structure. For manually constructed
   * models this value is undefined.
   */
  public final LXMatrix transform = new LXMatrix();

  /**
   * An immutable list of all the points in this model
   */
  public final LXPoint[] points;

  private final List<LXPoint> pointList;

  /**
   * An immutable map of String key/value pairs, metadata on the model object
   */
  public final Map<String, String> metaData;

  /**
   * An immutable list of all the children of this model
   */
  public final LXModel[] children;

  private final Map<String, List<LXModel>> childDict =
    new HashMap<String, List<LXModel>>();

  private final Map<String, List<LXModel>> subDict =
    new HashMap<String, List<LXModel>>();

  /**
   * An ordered list of outputs that should be sent for this model.
   */
  public final List<LXOutput> outputs;

  /**
   * A list of String keys by which this model type can be identified. Keys are non-empty strings.
   * These keys can be used with the {@link LXModel#children(String)} and {@link LXModel#sub(String)}
   * methods to dynamically navigate this model's hierarchy.
   */
  public final List<String> keys;

  private LXModel parent;

  private int generation = 0;

  /**
   * Total number of points in the model
   */
  public final int size;

  /**
   * Center position in the model (half-way between extremes)
   */
  public final LXVector center = new LXVector(0, 0, 0);

  /**
   * Average position in the model (weighted by point density)
   */
  public final LXVector average = new LXVector(0, 0, 0);

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
   * Average z point
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
   * Smallest radius from origin (0, 0)
   */
  public float rMin;

  /**
   * Greatest radius from origin (0, 0)
   */
  public float rMax;

  /**
   * Range of radial values from origin (0, 0)
   */
  public float rRange;

  /**
   * Smallest radius from center of model, this is only
   * valid on the top-level LXModel
   */
  public float rcMin;

  /**
   * Greatest radius from center of model, this is only valid
   * on the top-level LXModel
   */
  public float rcMax;

  /**
   * Range of radial values from center of model, only validd
   * on the top-level LXModel
   */
  public float rcRange;

  /**
   * Constructs a null model with no points
   */
  public LXModel() {
    this(new ArrayList<LXPoint>());
  }

  /**
   * Constructs a model from a list of points
   *
   * @param points Points in the model
   */
  public LXModel(List<LXPoint> points) {
    this(points, new LXModel[0]);
  }

  /**
   * Constructs a model from a list of points
   *
   * @param points Points in the model
   * @param keys Key identifiers for the model type
   */
  public LXModel(List<LXPoint> points, String ... keys) {
    this(points, new LXModel[0], null, keys);
  }

  /**
   * Constructs a model from a list of points
   *
   * @param points Points in the model
   * @param metaData Metadata for the model
   * @param keys Key identifiers for the model type
   */
  public LXModel(List<LXPoint> points, Map<String, String> metaData, String ... keys) {
    this(points, new LXModel[0], metaData, keys);
  }

  /**
   * Constructs a model with a given set of points and pre-constructed children. In this case, points
   * from the children are not added to the points array, they are assumed to already be contained by
   * the points list.
   *
   * @param points Points in this model
   * @param children Pre-built direct child model array
   */
  public LXModel(List<LXPoint> points, LXModel[] children) {
    this(points, children, LXModel.Key.MODEL);
  }

  /**
   * Constructs a model with a given set of points and pre-constructed submodels. In this case, points
   * from the submodels are not added to the points array, they are assumed to already be contained by
   * the points list.
   *
   * @param points Points in this model
   * @param children Pre-built direct submodel child array
   * @param keys Key identifier for this model
   */
  public LXModel(List<LXPoint> points, LXModel[] children, String ... keys) {
    this(points, children, null, keys);
  }

  /**
   * Constructs a model with a given set of points and pre-constructed submodels. In this case, points
   * from the submodels are not added to the points array, they are assumed to already be contained by
   * the points list.
   *
   * @param points Points in this model
   * @param children Pre-built direct submodel child array
   * @param metaData Metadata map
   * @param keys Key identifier for this model
   */
  public LXModel(List<LXPoint> points, LXModel[] children, Map<String, String> metaData, String ... keys) {
    this.keys = validateKeys(keys);
    this.pointList = Collections.unmodifiableList(new ArrayList<LXPoint>(points));
    addChildren(children);
    this.children = children.clone();
    this.points = this.pointList.toArray(new LXPoint[0]);
    this.size = this.points.length;
    this.outputs = Collections.unmodifiableList(new ArrayList<LXOutput>());

    Map<String, String> mutableMetadata = new HashMap<String, String>();
    if (metaData != null) {
      mutableMetadata.putAll(metaData);
    }
    this.metaData = Collections.unmodifiableMap(mutableMetadata);

    recomputeGeometry();
  }

  /**
   * Constructs a model from the given submodels. The point list is generated from
   * all points in the submodels, on the assumption that they have not yet been
   * added.
   *
   * @param children Sub-models
   */
  public LXModel(LXModel[] children) {
    this(children, LXModel.Key.MODEL);
  }

  /**
   *
   * Constructs a model from the given submodels. The point list is generated from
   * all points in the submodels, on the assumption that they have not yet been
   * added.
   *
   * @param children Pre-built sub-models
   * @param keys Key identifier for this model
   */
  private LXModel(LXModel[] children, String ... keys) {
    this.keys = validateKeys(keys);
    List<LXPoint> _points = new ArrayList<LXPoint>();
    addChildren(children);
    for (LXModel child : children) {
      for (LXPoint p : child.points) {
        _points.add(p);
      }
    }
    this.children = children.clone();
    this.points = _points.toArray(new LXPoint[0]);
    this.pointList = Collections.unmodifiableList(_points);
    this.size = _points.size();
    this.outputs = Collections.unmodifiableList(new ArrayList<LXOutput>());
    this.metaData = Collections.unmodifiableMap(new HashMap<String, String>());
    recomputeGeometry();
  }

  public LXModel(LXModelBuilder builder) {
    this(builder, true);
  }

  protected LXModel(LXModelBuilder builder, boolean isRoot) {
    if (builder.model != null) {
      throw new IllegalStateException("LXModelBuilder may only be used once: " + builder);
    }
    this.keys = validateKeys(builder.keys.toArray(new String[0]));
    this.children = new LXModel[builder.children.size()];
    List<LXPoint> _points = new ArrayList<LXPoint>(builder.points);
    int ci = 0;
    for (LXModelBuilder child : builder.children) {
      this.children[ci++] = new LXModel(child, false);
      for (LXPoint p : child.points) {
        _points.add(p);
      }
    }
    addChildren(children);
    this.points = _points.toArray(new LXPoint[0]);
    this.pointList = Collections.unmodifiableList(_points);
    this.size = this.points.length;
    this.outputs = Collections.unmodifiableList(new ArrayList<LXOutput>(builder.outputs));
    this.metaData = Collections.unmodifiableMap(new HashMap<String, String>());
    recomputeGeometry();
    if (isRoot) {
      reindexPoints();
      normalizePoints();
    }
    builder.model = this;
  }

  private static List<String> validateKeys(String[] keys) {
    Objects.requireNonNull(keys, "May not construct LXModel with null keys");
    List<String> _keys = new ArrayList<String>(keys.length);
    for (String key : keys) {
      if (key == null) {
        throw new IllegalArgumentException("May not pass null key to LXModel");
      }
      key = key.trim();
      if (key.isEmpty()) {
        throw new IllegalArgumentException("May not pass empty string key to LXModel");
      }
      // Filter out any duplicates that got in somehow
      if (!_keys.contains(key)) {
        _keys.add(key);
      }
    }
    return Collections.unmodifiableList(_keys);
  }

  public LXModel getParent() {
    return this.parent;
  }

  public String getPath() {
    LXModel parent = this.parent;
    if (parent == null) {
      return "/" + this.keys.get(0);
    }
    int index = 0;
    for (LXModel child : parent.childDict.get(this.keys.get(0))) {
      if (child == this) {
        break;
      }
      ++index;
    }
    return parent.getPath() + "/" + this.keys.get(0) + "[" + index + "]";
  }

  /**
   * Reindexes all the points in this model from 0 up to the number of points.
   *
   * @return this
   */
  public LXModel reindexPoints() {
    int index = 0;
    for (LXPoint p : this.points) {
      p.index = index++;
    }
    return this;
  }

  private void addChildren(LXModel[] children) {
    for (LXModel child : children) {
      child.parent = this;
    }
    addSubmodels(children, this.childDict, false);
    addSubmodels(children, this.subDict, true);
  }

  private void addSubmodels(LXModel[] submodels, Map<String, List<LXModel>> dict, boolean recurse) {
    for (LXModel submodel : submodels) {
      for (String key : submodel.keys) {
        List<LXModel> sub = dict.get(key);
        if (sub == null) {
          dict.put(key, sub = new ArrayList<LXModel>());
        }
        sub.add(submodel);
      }
      if (recurse) {
        addSubmodels(submodel.children, dict, recurse);
      }
    }
  }

  private static final List<LXModel> EMPTY_LIST = Collections.unmodifiableList(new ArrayList<LXModel>());

  /**
   * Returns a list of all the direct child components by particular key. Children are only one-level
   * deep.
   *
   * @param key Child key type
   * @return List of direct children by type
   */
  public List<LXModel> children(String key) {
    List<LXModel> children = this.childDict.get(key);
    return (children == null) ? EMPTY_LIST : children;
  }

  /**
   * Returns a list of all the submodel components by particular key, at any level of depth, may be
   * many levels of descendants contained here
   *
   * @param key Submodel key
   * @return List of all descendant submodels
   */
  public List<LXModel> sub(String key) {
    List<LXModel> sub = this.subDict.get(key);
    return (sub == null) ? EMPTY_LIST : sub;
  }

  /**
   * Gets a meta data property
   *
   * @param key Meta data key
   * @return Value if there is one, otherwise null
   */
  public String meta(String key) {
    return this.metaData.get(key);
  }

  private final List<Listener> listeners = new ArrayList<Listener>();

  public final LXModel addListener(Listener listener) {
    Objects.requireNonNull(listener);
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate LXModel.Listener: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  public final LXModel removeListener(Listener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot remove non-registered LXModel.Listener: " + listener);
    }
    this.listeners.remove(listener);
    return this;
  }

  /**
   * Update the meta-values in this model. Re-normalizes the points relative to
   * this model and recomputes its averages.
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
    recomputeGeometry();
    if (normalize) {
      normalizePoints();
    }
    bang();
    return this;
  }

  /**
   * Should be invoked when some of the geometry inside a model has been changed, but the
   * total point count and structure is the same. Will increment a sentinel value and notify
   * listeners of the change.
   *
   * @return this
   */
  public LXModel bang() {
    ++this.generation;
    // Notify the listeners of this model that it has changed
    for (Listener listener : this.listeners) {
      listener.modelGenerationUpdated(this);
    }
    return this;
  }

  /**
   * Returns an integer identifying the generation of this model. Each time geometry in the model is
   * changed, this value is incremented;
   *
   * @return Monotonically increasing integer verson number of the geometry revision
   */
  public int getGeneration() {
    return this.generation;
  }

  /**
   * Creates an index buffer of all the point indices in this model.
   *
   * @return Index buffer of all points in this model, containing their global color indices
   */
  public int[] toIndexBuffer() {
    return toIndexBuffer(0, this.points.length);
  }

  /**
   * Creates an index buffer of a subset of points in this model.
   *
   * @param offset Initial offset into this model's points
   * @param length Length of the index buffer
   * @return Array that contains the global color buffer indices for the specified points in this model
   */
  public int[] toIndexBuffer(int offset, int length) {
    if (offset < 0 || ((offset+length) > this.points.length)) {
      throw new IllegalArgumentException("Index buffer cannot exceed points array length - offset:" + offset + " length:" + length);
    }
    if (length < 0) {
      throw new IllegalArgumentException("Index buffer length cannot be negative: " + length);
    }
    int[] indexBuffer = new int[length];
    for (int i = 0; i < length; ++i) {
      indexBuffer[i] = this.points[offset+i].index;
    }
    return indexBuffer;
  }

  /**
   * Recompute the geometry values of the model
   */
  private void recomputeGeometry() {
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
        firstPoint = false;
      } else {
        if (p.x < xMin) {
          xMin = p.x;
        }
        if (p.x > xMax) {
          xMax = p.x;
        }
        if (p.y < yMin) {
          yMin = p.y;
        }
        if (p.y > yMax) {
          yMax = p.y;
        }
        if (p.z < zMin) {
          zMin = p.z;
        }
        if (p.z > zMax) {
          zMax = p.z;
        }
        if (p.r < rMin) {
          rMin = p.r;
        }
        if (p.r > rMax) {
          rMax = p.r;
        }
      }
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
    this.cx = xMin + xRange / 2f;
    this.cy = yMin + yRange / 2f;
    this.cz = zMin + zRange / 2f;
    this.center.set(this.cx, this.cy, this.cz);
    this.average.set(this.ax, this.ay, this.az);
  }

  /**
   * Sets the normalized values of all the points in this model (xn, yn, zn)
   * relative to this model's absolute bounds.
   *
   * @return this
   */
  public LXModel normalizePoints() {
    for (LXPoint p : this.points) {
      p.normalize(this);
    }
    float rcMin = 0, rcMax = 0;
    boolean firstPoint = true;
    for (LXPoint p : this.points) {
      if (firstPoint) {
        rcMin = p.rc;
        rcMax = p.rc;
        firstPoint = false;
      } else {
        if (p.rc < rcMin) {
          rcMin = p.rc;
        }
        if (p.rc > rcMax) {
          rcMax = p.rc;
        }
      }
    }
    this.rcMin = rcMin;
    this.rcMax = rcMax;
    this.rcRange = rcMax - rcMin;
    if (rcMax == 0) {
      for (LXPoint p : this.points) {
        p.rcn = 0.5f;
      }
    } else {
      for (LXPoint p : this.points) {
        p.rcn = p.rc / rcMax;
      }
    }

    return this;
  }

  /**
   * Accessor for a list of all points in the model. Generally preferable
   * to directly access the points array when iterating over a full buffer,
   * but this is useful for situations where a container is needed.
   *
   * @return List of all points
   */
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

  public void dispose() {
    for (LXModel child : this.children) {
      child.dispose();
    }
    this.listeners.clear();
  }

}
