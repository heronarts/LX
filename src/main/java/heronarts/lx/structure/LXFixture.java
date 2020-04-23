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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.output.LXDatagram;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.transform.LXMatrix;
import heronarts.lx.transform.LXTransform;

/**
 * An LXFixture is a rich LXComponent representing a physical lighting fixture which is
 * addressed by a datagram packet. This class encapsulates the ability to configure the
 * dimensions and location of the lighting fixture as well as to specify its otuput modes
 * and protocol.
 */
public abstract class LXFixture extends LXComponent implements LXComponent.Renamable {

  /**
   * Output datagram protocols
   */
  public enum Protocol {
    /**
     * No network output
     */
    NONE("None"),

    /**
     * Art-Net - <a href="https://art-net.org.uk/">https://art-net.org.uk/</a>
     */
    ARTNET("Art-Net"),

    /**
     * E1.31 Streaming ACN - <a href="https://opendmx.net/index.php/E1.31">https://opendmx.net/index.php/E1.31/</a>
     */
    SACN("E1.31 Streaming ACN"),

    /**
     * Open Pixel Control - <a href="http://openpixelcontrol.org/">http://openpixelcontrol.org/</a>
     */
    OPC("OPC"),

    /**
     * Distributed Display Protocol - <a href="http://www.3waylabs.com/ddp/">http://www.3waylabs.com/ddp/</a>
     */
    DDP("DDP"),

    /**
     * Color Kinetics KiNET - <a href="https://www.colorkinetics.com/">https://www.colorkinetics.com/</a>
     */
    KINET("KiNET");

    private final String label;

    Protocol(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  };

  private static final double POSITION_RANGE = 1000000;

  public final BooleanParameter selected =
    new BooleanParameter("Selected", false)
    .setDescription("Whether this fixture is selected for editing");

  public final BooleanParameter identify =
    new BooleanParameter("Identify", false)
    .setDescription("Causes the fixture to flash red for identification");

  public final BoundedParameter x =
    new BoundedParameter("X", 0, -POSITION_RANGE, POSITION_RANGE)
    .setDescription("Base X position of the fixture in space");

  public final BoundedParameter y =
    new BoundedParameter("Y", 0, -POSITION_RANGE, POSITION_RANGE)
    .setDescription("Base Y position of the fixture in space");

  public final BoundedParameter z =
    new BoundedParameter("Z", 0, -POSITION_RANGE, POSITION_RANGE)
    .setDescription("Base Z position of the fixture in space");

  public final BoundedParameter yaw = (BoundedParameter)
    new BoundedParameter("Yaw", 0, -360, 360)
    .setDescription("Rotation of the fixture about the vertical axis")
    .setUnits(LXParameter.Units.DEGREES);

  public final BoundedParameter pitch = (BoundedParameter)
    new BoundedParameter("Pitch", 0, -360, 360)
    .setDescription("Rotation of the fixture about the horizontal plane")
    .setUnits(LXParameter.Units.DEGREES);

  public final BoundedParameter roll = (BoundedParameter)
    new BoundedParameter("Roll", 0, -360, 360)
    .setDescription("Rotation of the fixture about its normal vector")
    .setUnits(LXParameter.Units.DEGREES);

  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", false)
    .setDescription("Whether output to this fixture is enabled");

  public final BoundedParameter brightness =
    new BoundedParameter("Brightness", 1)
    .setDescription("Brightness level of this fixture");

  public final BooleanParameter mute =
    new BooleanParameter("Mute", false)
    .setDescription("Mutes this fixture, sending all black pixels");

  public final BooleanParameter solo =
    new BooleanParameter("Solo", false)
    .setDescription("Solos this fixture, no other fixtures illuminated");


  private final List<LXDatagram> mutableDatagrams = new ArrayList<LXDatagram>();

  /**
   * Publicly accessible list of the datagrams that should be sent to this fixture
   */
  public final List<LXDatagram> datagrams = Collections.unmodifiableList(this.mutableDatagrams);

  protected LXMatrix parentTransformMatrix = new LXMatrix();

  private LXTransform transform = new LXTransform();

  private final List<LXPoint> mutablePoints = new ArrayList<LXPoint>();

  /**
   * Publicly accessible immutable view of the points in this fixture. Should not
   * be directly modified.
   */
  public final List<LXPoint> points = Collections.unmodifiableList(this.mutablePoints);

  /**
   * A deep copy of the points array used for passing to the model layer. We need a separate copy there
   * because the model is passed to the UI layer which runs on a separate thread. That copy of the points
   * shouldn't be modified by re-indexing that occurs when we modify this.
   */
  private List<LXPoint> modelPoints;

  private final Set<LXParameter> metricsParameters = new HashSet<LXParameter>();

  private final Set<LXParameter> geometryParameters = new HashSet<LXParameter>();

  private final Set<LXParameter> datagramParameters = new HashSet<LXParameter>();

  private int index = 0;

  protected LXFixture(LX lx) {
    super(lx);
    setParent(lx.structure);

    // Geometry parameters
    addGeometryParameter("x", this.x);
    addGeometryParameter("y", this.y);
    addGeometryParameter("z", this.z);
    addGeometryParameter("yaw", this.yaw);
    addGeometryParameter("pitch", this.pitch);
    addGeometryParameter("roll", this.roll);

    // Output parameters
    addParameter("selected", this.selected);
    addParameter("enabled", this.enabled);
    addParameter("brightness", this.brightness);
    addParameter("identify", this.identify);
    addParameter("mute", this.mute);
    addParameter("solo", this.solo);
  }

  @Override
  public String getPath() {
    return "fixture/" + (this.index + 1);
  }

  void setIndex(int index) {
    this.index = index;
  }

  public int getIndex() {
    return this.index;
  }

  /**
   * Adds a parameter which impacts the number of LEDs that are in the fixture.
   * Changes to these parameters require re-generating the whole points array.
   *
   * @param path Path to parameter
   * @param parameter Parameter
   * @return this
   */
  protected LXFixture addMetricsParameter(String path, LXParameter parameter) {
    super.addParameter(path, parameter);
    this.metricsParameters.add(parameter);
    return this;
  }

  /**
   * Adds a parameter which impacts the position of points in the fixture.
   * Changes to these parameters do not require rebuilding the points array, but
   * the point positions are updated and model change notifications are
   * required.
   *
   * @param path Path to parameter
   * @param parameter Parameter
   * @return this
   */
  protected LXFixture addGeometryParameter(String path, LXParameter parameter) {
    super.addParameter(path, parameter);
    this.geometryParameters.add(parameter);
    return this;
  }

  /**
   * Adds a parameter which impacts the output datagrams of the fixture. Whenever
   * one is changed, the datagrams will be regenerated.
   *
   * @param path Path to parameter
   * @param parameter Parameter
   * @return this
   */
  protected LXFixture addDatagramParameter(String path, LXParameter parameter) {
    super.addParameter(path, parameter);
    this.datagramParameters.add(parameter);
    return this;
  }

  /**
   * To be used in a future hierarchical fixture system
   *
   * @param parentTransformMatrix Transform matrix of the parent fixture or group
   * @return this
   */
  protected LXFixture setParentTransformMatrix(LXMatrix parentTransformMatrix) {
    this.parentTransformMatrix = parentTransformMatrix;
    regeneratePoints();
    return this;
  }

  protected LXMatrix getTransformMatrix() {
    return this.transform.getMatrix();
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (!this.isLoading) {
      if (this.metricsParameters.contains(p)) {
        regeneratePoints();

        // TODO: find better / cleaner way of handling this, including in
        // hierarchical subfixtures
        this.lx.structure.regenerateModel();

      } else if (this.geometryParameters.contains(p)) {
        // If only geometry has changed, we can be a little more clever here and
        // just re-generate
        regenerateGeometry();
        this.lx.structure.fixtureGeometryRegenerated(this);
      } else if (this.datagramParameters.contains(p)) {
        regenerateDatagrams();
      }
    }
    if (p == this.solo) {
      if (this.solo.isOn()) {
        this.lx.structure.soloFixture(this);
      }
    } else if (p == this.enabled) {
      for (LXDatagram datagram : this.datagrams) {
        datagram.enabled.setValue(this.enabled.isOn());
      }
    } else if (p == this.brightness) {
      for (LXDatagram datagram : this.datagrams) {
        datagram.enabled.setValue(this.brightness.getValue());
      }
    }
  }

  private void regenerateDatagrams() {
    for (LXDatagram datagram : this.datagrams) {
      datagram.dispose();
    }
    this.mutableDatagrams.clear();
    this.isInBuildDatagrams = true;
    buildDatagrams();
    this.isInBuildDatagrams = false;
  }

  private boolean isInBuildDatagrams = false;

  /**
   * Subclasses must override this method to provide an implementation that
   * produces the necessary set of datagrams for this fixture to be sent.
   * The subclass should call {@link addDatagram(LXDatagram)} for each datagram.
   */
  protected abstract void buildDatagrams();

  /**
   * Subclasses call this method to add a datagram to thix fixture. This may only
   * be called from within the buildDatagrams() function.
   *
   * @param datagram
   */
  protected void addDatagram(LXDatagram datagram) {
    if (!this.isInBuildDatagrams) {
      throw new IllegalStateException("May not add add datagrams from outside buildDatagrams() method");
    }
    Objects.requireNonNull(datagram, "Cannot add null datagram to LXFixture.addDatagram");
    if (this.mutableDatagrams.contains(datagram)) {
      throw new IllegalStateException("May not add duplicate LXDatagram to LXFixture: " + datagram);
    }
    this.mutableDatagrams.add(datagram);
  }

  void regeneratePoints() {
    int numPoints = size();
    this.mutablePoints.clear();
    for (int i = 0; i < numPoints; ++i) {
      this.mutablePoints.add(new LXPoint());
    }
    regenerateGeometry();
  }

  private void regenerateGeometry() {
    double degreesToRadians = Math.PI / 180;
    this.transform.reset(this.parentTransformMatrix);
    this.transform.translate(this.x.getValuef(), this.y.getValuef(), this.z.getValuef());
    this.transform.rotateY(this.yaw.getValuef() * degreesToRadians);
    this.transform.rotateX(this.pitch.getValuef() * degreesToRadians);
    this.transform.rotateZ(this.roll.getValuef() * degreesToRadians);
    this.transform.push();
    computePointGeometry(this.transform);
    this.transform.pop();
  }

  /**
   * This method should be implemented by subclasses to generate the geometry of the
   * fixture any time its geometry parameters have changed. The correct number of points
   * will have already been computed, and merely need to have their positions set.
   *
   * @param transform A transform matrix representing the fixture's position in the structure
   */
  protected abstract void computePointGeometry(LXTransform transform);

  /**
   * Get an index buffer version of this fixture
   *
   * @return Index buffer of the points in this fixture
   */
  public int[] toIndexBuffer() {
    int[] indexBuffer = new int[this.points.size()];
    int i = 0;
    for (LXPoint p : this.points) {
      indexBuffer[i++] = p.index;
    }
    return indexBuffer;
  }

  /**
   * Constructs an LXModel object for this Fixture
   *
   * @param startIndex Global index position for the first point in this fixture model
   * @return Model representation of this fixture
   */
  final LXModel toModel(int startIndex) {
    // Reindex the points in this fixture from the given start
    for (LXPoint p : this.points) {
      p.index = startIndex++;
    }
    // Our point indices may have changed... we'll need to rebuild our datagrams
    regenerateDatagrams();

    // Note: we make a deep copy here because a change to the number of points in one
    // fixture will alter point indices in all fixtures after it. When we're in multi-threaded
    // mode, that point might have been passed to the UI, which holds a reference to the model.
    // The indices passed to the UI cannot be changed mid-flight, so we make new copies of all
    // points here to stay safe.
    this.modelPoints = new ArrayList<LXPoint>(this.points.size());
    for (LXPoint p : this.points) {
      this.modelPoints.add(new LXPoint(p));
    }

    // Generate any submodels references into of this fixture
    LXModel[] submodels = toSubmodels();

    // Okay, good to go, construct the model
    return new LXModel(this.modelPoints, submodels, getModelKey());
  }

  private List<LXPoint> subpoints(int start, int n, int stride) {
    List<LXPoint> subpoints = new ArrayList<LXPoint>(n);
    for (int i = 0; i < n; ++i) {
      subpoints.add(this.modelPoints.get(start + i*stride));
    }
    return subpoints;
  }

  /**
   * Helper class to ensure that Submodels are *only* constructed
   * using the points from the produced LXModel array. No other
   * constructors are allowed.
   */
  public class Submodel extends LXModel {

    /**
     * Subclasses may use this helper to construct a submodel object from a set of
     * points in this model.
     *
     * @param start Start index
     * @param n Number of points in the submodel
     * @param stride Stride size for selecting submodel points
     */
    public Submodel(int start, int n, int stride) {
      this(start, n, stride, LXModel.Key.MODEL);
    }

    /**
     * Subclasses may use this helper to construct a submodel object from a set of
     * points in this model.
     *
     * @param start Start index
     * @param n Number of points in the submodel
     * @param stride Stride size for selecting submodel points
     * @param keys Model type key identifier for submodel
     */
    public Submodel(int start, int n, int stride, String ... keys) {
      super(subpoints(start, n, stride), keys);
    }
  }

  /**
   * Subclasses should implement, specifying the type key of this fixture in the model
   * hierarchy.
   *
   * @return String key for the model type
   */
  protected abstract String getModelKey();

  protected final static Submodel[] NO_SUBMODELS = new Submodel[0];

  /**
   * Subclasses may override when they specify submodels
   *
   * @return Array of submodel objects
   */
  protected Submodel[] toSubmodels() {
    return NO_SUBMODELS;
  }

  /**
   * Subclasses must implement to specify the number of points in the fixture
   *
   * @return number of points in the fixture
   */
  protected abstract int size();

  // Flag to avoid unnecessary work while parameters are being loaded... we'll fix
  // everything *after* the parameters are all loaded.
  private boolean isLoading = false;

  @Override
  public void load(LX lx, JsonObject obj) {
    this.isLoading = true;
    super.load(lx, obj);
    this.isLoading = false;

    regeneratePoints();

    // TODO: work this out, probably doesn't belong in this base class...
    this.lx.structure.regenerateModel();

    // TODO(mcslee): maybe not completely necessary, the datagrams will be
    // regenerated by structure.regenerateModel call?
    regenerateDatagrams();
  }
}
