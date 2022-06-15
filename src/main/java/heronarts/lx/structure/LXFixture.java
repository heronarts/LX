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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.output.ArtNetDatagram;
import heronarts.lx.output.DDPDatagram;
import heronarts.lx.output.KinetDatagram;
import heronarts.lx.output.LEDscapeDatagram;
import heronarts.lx.output.LXBufferOutput;
import heronarts.lx.output.LXOutput;
import heronarts.lx.output.OPCDatagram;
import heronarts.lx.output.StreamingACNDatagram;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.transform.LXMatrix;

/**
 * An LXFixture is a rich LXComponent representing a physical lighting fixture which may
 * be addressed by output packets. This class encapsulates the ability to configure the
 * dimensions and location of the lighting fixture as well as to specify its output modes
 * and protocol.
 */
public abstract class LXFixture extends LXComponent implements LXFixtureContainer, LXComponent.Renamable {

  /**
   * Output protocols
   */
  public static enum Protocol {
    /**
     * No network output
     */
    NONE("None", -1, -1),

    /**
     * Art-Net - <a href="https://art-net.org.uk/">https://art-net.org.uk/</a>
     */
    ARTNET("Art-Net", ArtNetDatagram.ARTNET_PORT, ArtNetDatagram.MAX_DATA_LENGTH),

    /**
     * E1.31 Streaming ACN - <a href="https://opendmx.net/index.php/E1.31">https://opendmx.net/index.php/E1.31/</a>
     */
    SACN("E1.31 sACN", StreamingACNDatagram.DEFAULT_PORT, StreamingACNDatagram.MAX_DATA_LENGTH),

    /**
     * Open Pixel Control - <a href="http://openpixelcontrol.org/">http://openpixelcontrol.org/</a>
     */
    OPC("OPC", OPCDatagram.DEFAULT_PORT, OPCDatagram.MAX_DATA_LENGTH),

    /**
     * Distributed Display Protocol - <a href="http://www.3waylabs.com/ddp/">http://www.3waylabs.com/ddp/</a>
     */
    DDP("DDP", DDPDatagram.DEFAULT_PORT, DDPDatagram.MAX_DATA_LENGTH),

    /**
     * Color Kinetics KiNET - <a href="https://www.colorkinetics.com/">https://www.colorkinetics.com/</a>
     */
    KINET("KiNET", KinetDatagram.KINET_PORT, KinetDatagram.MAX_DATA_LENGTH),

    /**
     * LEDscape - <a href="https://github.com/Yona-Appletree/LEDscape">https://github.com/Yona-Appletree/LEDscape</a>
     */
    LEDSCAPE("LEDscape", LEDscapeDatagram.LEDSCAPE_DEFAULT_PORT, LEDscapeDatagram.MAX_CHANNELS_TEMP);

    private final String label;
    public final int defaultPort;
    public final int maxChannels;

    Protocol(String label, int defaultPort, int maxChannels) {
      this.label = label;
      this.defaultPort = defaultPort;
      this.maxChannels = maxChannels;
    }

    @Override
    public String toString() {
      return this.label;
    }
  };

  public static enum Transport {
    UDP,
    TCP;
  }

  /**
   * Class which represents a segment of the pixels in this fixture object (and its children),
   * with a starting point, number and stride length all relative to this fixture's size.
   */
  protected class Segment {

    // Index buffer defined relative to this fixture
    protected final int[] indexBuffer;

    // Byte order for this segment
    protected final LXBufferOutput.ByteOrder byteOrder;

    protected final int length;

    protected final int numChannels;

    private final FunctionalParameter brightness = new FunctionalParameter() {
      @Override
      public double getValue() {
        double brightness = 1.;
        LXFixture fixture = LXFixture.this;
        while (fixture != null) {
          brightness *= fixture.brightness.getValue();
          fixture = fixture.getParentFixture();
        }
        return brightness;
      }
    };

    protected Segment(int start, int num, int stride) {
      this(start, num, stride, false);
    }

    protected Segment(int start, int num, int stride, boolean reverse) {
      this(start, num, stride, reverse, LXBufferOutput.ByteOrder.RGB);
    }

    protected Segment(int start, int num, int stride, boolean reverse, LXBufferOutput.ByteOrder byteOrder) {
      this.indexBuffer = new int[num];
      if (reverse) {
        start = start + stride * (num-1);
        stride = -stride;
      }
      for (int s = 0; s < num; ++s) {
        this.indexBuffer[s] = start + s * stride;
      }
      this.byteOrder = byteOrder;
      this.length = num;
      this.numChannels = num * byteOrder.getNumBytes();
    }

    /**
     * Constructs a segment with a fixed index buffer. Addresses in this index buffer are relative
     * to the fixture itself, not global.
     *
     * @param indexBuffer Relative-indexed buffer
     * @param byteOrder Byte order for this segment
     */
    protected Segment(int[] indexBuffer, LXBufferOutput.ByteOrder byteOrder) {
      this.indexBuffer = indexBuffer;
      this.byteOrder = byteOrder;
      this.length = indexBuffer.length;
      this.numChannels = indexBuffer.length * byteOrder.getNumBytes();
    }

    /**
     * Returns a globally indexed buffer of the subset of points in this fixture
     *
     * @param start Start index
     * @param len Length of buffer
     * @return Globally indexed set of indices for this fixture
     */
    protected int[] toIndexBuffer(int start, int len) {
      int[] indices = new int[len];
      for (int i = 0; i < len; ++i) {
        indices[i] = getPoint(this.indexBuffer[start + i]).index;
      }
      return indices;
    }

    protected LXFixture getFixture() {
      return LXFixture.this;
    }

    protected LXParameter getBrightness() {
      return this.brightness;
    }

  }

  /**
   * Class which defines a LXFixture output. Note that this class specifies only the output setting, not the
   * actual LXOutput object. These are constructed at the LXStructure layer, where separate packets may be merged.
   */
  public static class OutputDefinition {

    protected final static float FPS_UNSPECIFIED = 0f;

    protected final Protocol protocol;
    protected final Transport transport;
    protected final InetAddress address;
    protected final int port;
    protected final int universe;
    protected final int channel;
    protected final float fps;
    protected final Segment[] segments;

    public OutputDefinition(Protocol protocol, Transport transport, InetAddress address, int port, int universe, int channel, float fps, Segment ... segments) {
      this.protocol = protocol;
      this.transport = transport;
      this.address = address;
      this.port = port;
      this.universe = universe;
      this.channel = channel;
      this.fps = fps;
      this.segments = segments;
    }
  }

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

  public final BoundedParameter scale =
    new BoundedParameter("Scale", 1, 0, 1000)
    .setDescription("Scale the size of the fixture");

  public final BooleanParameter deactivate =
    new BooleanParameter("Deactivate", false)
    .setDescription("Whether to deactivate this fixture");

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

  public final StringParameter tags =
    new StringParameter("Tags", "")
    .setDescription("Tags to be applied to the fixture in model");

  final List<LXFixture> mutableChildren = new ArrayList<LXFixture>();

  protected final List<LXFixture> children = Collections.unmodifiableList(this.mutableChildren);

  private final List<LXOutput> mutableOutputsDirect = new ArrayList<LXOutput>();

  /**
   * Publicly accessible list of the outputs that should be sent to this fixture
   */
  public final List<LXOutput> outputsDirect = Collections.unmodifiableList(this.mutableOutputsDirect);

  protected final List<OutputDefinition> outputDefinitions = new ArrayList<OutputDefinition>();

  private final List<String> tagList = new ArrayList<String>();

  protected final Map<String, String> metaData = new HashMap<String, String>();

  private final LXMatrix parentTransformMatrix = new LXMatrix();

  private final LXMatrix geometryMatrix = new LXMatrix();

  private final List<LXPoint> mutablePoints = new ArrayList<LXPoint>();

  private LXModel model = null;

  private LXFixtureContainer container = null;

  /**
   * Publicly accessible immutable view of the points in this fixture. Should not
   * be directly modified. Only contains this fixture's direct points, not any of
   * its children.
   */
  public final List<LXPoint> points = Collections.unmodifiableList(this.mutablePoints);

  /**
   * A deep copy of the points array used for passing to the model layer. We need a separate copy there
   * because the model is passed to the UI layer which runs on a separate thread. That copy of the points
   * shouldn't be modified by re-indexing that occurs when we modify this.
   */
  private final List<LXPoint> modelPoints = new ArrayList<LXPoint>();

  private final Set<LXParameter> metricsParameters = new HashSet<LXParameter>();

  private final Set<LXParameter> geometryParameters = new HashSet<LXParameter>();

  private final Set<LXParameter> outputParameters = new HashSet<LXParameter>();

  private int index = 0;

  private int firstPointIndex = -1;

  protected LXFixture(LX lx) {
    this(lx, "Fixture");
  }

  protected LXFixture(LX lx, String label) {
    super(lx, label);

    this.tags.setValue(String.join(" ", getDefaultTags()));

    // Geometry parameters
    addGeometryParameter("x", this.x);
    addGeometryParameter("y", this.y);
    addGeometryParameter("z", this.z);
    addGeometryParameter("yaw", this.yaw);
    addGeometryParameter("pitch", this.pitch);
    addGeometryParameter("roll", this.roll);
    addGeometryParameter("scale", this.scale);

    // Output parameters
    addParameter("selected", this.selected);
    addParameter("deactivate", this.deactivate);
    addParameter("enabled", this.enabled);
    addParameter("brightness", this.brightness);
    addParameter("identify", this.identify);
    addParameter("mute", this.mute);
    addParameter("solo", this.solo);

    // Tags
    addParameter("tags", this.tags);

    this.brightness.setMappable(true);
    this.enabled.setMappable(true);
    this.identify.setMappable(true);
    this.mute.setMappable(true);
    this.solo.setMappable(true);
  }

  @Override
  protected LXComponent addParameter(String path, LXParameter parameter) {
    // Disable use of MIDI/modulation mapping to modify the structure in real-time
    parameter.setMappable(false);
    return super.addParameter(path, parameter);
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

  protected int getFirstPointIndex() {
    return this.firstPointIndex;
  }

  /**
   * Returns the parent fixture that this fixture is a child of, if any. Otherwise null.
   *
   * @return Parent fixture, or null if none
   */
  private LXFixture getParentFixture() {
    if (this.container instanceof LXFixture) {
      return (LXFixture) this.container;
    }
    return null;
  }

  private void setContainer(LXFixtureContainer container) {
    Objects.requireNonNull(container, "Cannot set null on LXFixture.setContainer");
    if (this.container != null) {
      throw new IllegalStateException("LXFixture already has container set: " + this + " " + this.container);
    }
    this.container = container;
  }

  protected void setStructure(LXStructure structure) {
    setParent(structure);
    setContainer(structure);
    regenerate();
  }

  private void _reindexChildren() {
    int i = 0;
    for (LXFixture child : this.children) {
      child.setIndex(i++);
    }
  }

  protected void addChild(LXFixture child) {
    addChild(child, false);
  }

  void addChild(LXFixture child, boolean generateFirst) {
    Objects.requireNonNull(child, "Cannot add null child to LXFixture");
    if (this.children.contains(child)) {
      throw new IllegalStateException("Cannot add duplicate child to LXFixture: " + child);
    }
    this.mutableChildren.add(child);
    _reindexChildren();

    child.parentTransformMatrix.set(this.geometryMatrix);

    if (generateFirst) {
      child.regenerate();
    }

    // It's acceptable to remove and re-add a child to the same container
    if (child.container != this) {
      child.setParent(this);
      child.setContainer(this);
    }

    if (!generateFirst) {
      child.regenerate();
    }
  }

  protected void removeChild(LXFixture child) {
    if (!this.children.contains(child)) {
      throw new IllegalStateException("Cannot remove non-existent child from LXFixture: " + this + " " + child);
    }
    this.mutableChildren.remove(child);
    _reindexChildren();

    // Notify the structure of change, rebuild will occur
    fixtureGenerationChanged(this);
  }

  // Invoked when a child fixture has been altered
  @Override
  public final void fixtureGenerationChanged(LXFixture fixture) {
    if (this.container != null) {
      this.container.fixtureGenerationChanged(fixture);
    }
  }

  // Invoked when a child fixture has had its geometry altered
  @Override
  public final void fixtureGeometryChanged(LXFixture fixture) {
    if (this.container != null) {
      this.container.fixtureGeometryChanged(fixture);
    }
  }

  // Invoked when a child fixture has had its output settings altered
  @Override
  public final void fixtureOutputChanged(LXFixture fixture) {
    if (this.container != null) {
      this.container.fixtureOutputChanged(fixture);
    }
  }

  // Invoked when a child fixture has had its output settings altered
  @Override
  public final void fixtureTagsChanged(LXFixture fixture) {
    if (this.container != null) {
      this.container.fixtureTagsChanged(fixture);
    }
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
    addParameter(path, parameter);
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
    addParameter(path, parameter);
    this.geometryParameters.add(parameter);
    return this;
  }

  /**
   * Adds a parameter which impacts the outputs of the fixture. Whenever
   * one is changed, the outputs will be regenerated.
   *
   * @param path Path to parameter
   * @param parameter Parameter
   * @return this
   */
  protected LXFixture addOutputParameter(String path, LXParameter parameter) {
    addParameter(path, parameter);
    this.outputParameters.add(parameter);
    return this;
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if ((this.container != null) && !this.isLoading) {
      if (this.metricsParameters.contains(p)) {
        // Note: this will rebuild this fixture and trigger the structure
        // to rebuild as well
        regenerate();
      } else if (this.geometryParameters.contains(p)) {
        // We don't need to rebuild the whole model here, just update the
        // geometry for affected fixtures
        regenerateGeometry();
      } else if (this.outputParameters.contains(p)) {
        regenerateOutputs();
        if (this.enabled.isOn()) {
          this.container.fixtureOutputChanged(this);
        }
      } else if (this.enabled == p) {
        if (!this.outputDefinitions.isEmpty()) {
          this.container.fixtureOutputChanged(this);
        }
      } else if (this.deactivate == p) {
        this.container.fixtureGenerationChanged(this);
      } else if (p == this.tags) {
        this.container.fixtureTagsChanged(this);
      }
    }
    if (p == this.solo) {
      if (this.solo.isOn()) {
        this.lx.structure.soloFixture(this);
      }
    }
  }

  protected void regenerateOutputs() {
    // Clear output definitions and dispose of direct outputs
    this.outputDefinitions.clear();
    for (LXOutput output : this.outputsDirect) {
      output.dispose();
    }
    this.mutableOutputsDirect.clear();

    // Rebuild
    this.isInBuildOutputs = true;
    buildOutputs();
    this.isInBuildOutputs = false;
  }

  private boolean isInBuildOutputs = false;

  /**
   * Subclasses must override this method to provide an implementation that
   * produces the necessary set of outputs for this fixture to be sent.
   * The subclass should call {@link #addOutputDefinition(OutputDefinition)} or
   * {@link #addOutputDirect(LXOutput)} for each output.
   */
  protected abstract void buildOutputs();

  /**
   * Subclasses may override this method to update their outputs in the
   * case that the point indexing of this fixture has changed. Outputs
   * may be removed and re-added inside this method if necessary.
   */
  protected void reindexOutputs() {}

  /**
   * This method adds an output definition to the fixture
   *
   * @param output Output definition to add to the fixture
   */
  protected void addOutputDefinition(OutputDefinition output) {
    if (!this.isInBuildOutputs) {
      throw new IllegalStateException("May not add outputs from outside buildOutputs() method");
    }
    Objects.requireNonNull(output, "Cannot add null output to LXFixture.addOutputDefinition");
    if (this.outputDefinitions.contains(output)) {
      throw new IllegalStateException("May not add duplicate LXOutput to LXFixture: " + output);
    }
    this.outputDefinitions.add(output);
  }

  protected void removeOutputDefinition(OutputDefinition output) {
    if (!this.isInBuildOutputs) {
      throw new IllegalStateException("May not remove outputs from outside reindexOutputs() method");
    }
    if (!this.outputDefinitions.contains(output)) {
      throw new IllegalStateException("May not remove non-existent OutputDefinition from LXFixture: " + output + " " + this);
    }
    this.outputDefinitions.remove(output);
  }

  /**
   * Subclasses call this method to add a direct output to this fixture. This may only
   * be called from within the buildOutputs() function. Generally, usage of this method
   * is strongly discouraged in favor of addOutput(), which enables merging of packets
   * across multiple fixtures.
   *
   * @param output Output to add directly to this fixture
   */
  protected void addOutputDirect(LXOutput output) {
    if (!this.isInBuildOutputs) {
      throw new IllegalStateException("May not add outputs from outside buildOutputs() method");
    }
    Objects.requireNonNull(output, "Cannot add null output to LXFixture.addOutputDirect");
    if (this.mutableOutputsDirect.contains(output)) {
      throw new IllegalStateException("May not add duplicate LXOutput to LXFixture: " + output);
    }
    this.mutableOutputsDirect.add(output);
  }

  /**
   * Subclasses call this method to remove a output from the fixture. This may only
   * be performed from within the reindexOutputs or buildOutputs methods.
   *
   * @param output Output to remove
   */
  protected void removeOutputDirect(LXOutput output) {
    if (!this.isInBuildOutputs) {
      throw new IllegalStateException("May not remove outputs from outside reindexOutputs() method");
    }
    if (!this.mutableOutputsDirect.contains(output)) {
      throw new IllegalStateException("May not remove non-existent LXOutput from LXFixture: " + output + " " + this);
    }
    this.mutableOutputsDirect.remove(output);
  }

  /**
   * Invoked when this fixture has been loaded or added to some container. Will
   * rebuild the points and the metrics, and notify container of the change to
   * this fixture's generation
   */
  protected final void regenerate() {
    // We may have a totally new size, blow out the points array and rebuild
    int numPoints = size();
    this.mutablePoints.clear();
    for (int i = 0; i < numPoints; ++i) {
      LXPoint p = constructPoint(i);
      p.index = this.firstPointIndex + i;
      this.mutablePoints.add(p);
    }

    // A new model will have to be created, forget these points
    this.model = null;
    this.modelPoints.clear();

    // Chance for subclasses to do custom prep work
    beforeRegenerate();

    // Regenerate our geometry, note that we bypass regenerateGeometry()
    // here because we don't need to notify our container about the change. We're
    // going to notify them after this of even more substantive generation change.
    _regenerateGeometry();

    // Rebuild output objects
    regenerateOutputs();

    // Let our container know that our structural generation has changed
    if (this.container != null) {
      this.container.fixtureGenerationChanged(this);
    }
  }

  /**
   * Subclasses may override this method to do custom preparation work before
   * {@link #computeGeometryMatrix(LXMatrix)} is called.
   */
  protected void beforeRegenerate() {}

  private void regenerateGeometry() {
    _regenerateGeometry();
    if (this.container != null) {
      this.container.fixtureGeometryChanged(this);
    }
  }

  /**
   * Subclasses may override this if they perform geometric transformations in a
   * different order or using totally different parameters. The supplied parameter is a
   * mutable matrix which will initially hold the value of the parent transformation matrix.
   * It can then be further manipulated based upon the parameters.
   *
   * @param geometryMatrix The geometry transformation matrix for this object
   */
  protected void computeGeometryMatrix(LXMatrix geometryMatrix) {
    geometryMatrix.translate(this.x.getValuef(), this.y.getValuef(), this.z.getValuef());
    geometryMatrix.rotateY((float) Math.toRadians(this.yaw.getValue()));
    geometryMatrix.rotateX((float) Math.toRadians(this.pitch.getValue()));
    geometryMatrix.rotateZ((float) Math.toRadians(this.roll.getValue()));
    geometryMatrix.scale(this.scale.getValuef());
  }

  private void _regenerateGeometry() {
    // Reset and compute the transformation matrix based upon geometry parameters
    this.geometryMatrix.set(this.parentTransformMatrix);
    computeGeometryMatrix(this.geometryMatrix);

    // Regenerate the point geometry
    regeneratePointGeometry();

    // No indices have changed but points may have moved, we are not going
    // to rebuilt the entire model, but we do need to update the locations
    // of these points in their reflected deep copies, if those have already
    // been made
    if (this.model != null) {
      this.model.transform.set(this.geometryMatrix);
    }
    if (!this.modelPoints.isEmpty()) {
      int i = 0;
      for (LXPoint p : this.points) {
        this.modelPoints.get(i++).set(p);
      }
    }
  }

  private final LXMatrix _computePointGeometryMatrix = new LXMatrix();

  private void regeneratePointGeometry() {
    this._computePointGeometryMatrix.set(this.geometryMatrix);
    computePointGeometry(this._computePointGeometryMatrix, this.points);

    // Regenerate children
    for (LXFixture child : this.children) {
      child.parentTransformMatrix.set(this.geometryMatrix);
      child._regenerateGeometry();
    }
  }

  /**
   * This method should be implemented by subclasses to generate the geometry of the
   * fixture any time its geometry parameters have changed. The correct number of points
   * will have already been computed, and merely need to have their positions set.
   *
   * @param transform A transform matrix representing the fixture's position
   * @param points The list of points that need to have their positions set
   */
  protected abstract void computePointGeometry(LXMatrix transform, List<LXPoint> points);

  /**
   * Reindex the points in this fixture. Package-level access, should only ever
   * be called by LXStructure. Subclasses should not use.
   *
   * @param startIndex Buffer index for the start of this fixture
   */
  final void reindex(int startIndex) {
    _reindex(startIndex);
  }

  // Internal private recursive implementation
  private boolean _reindex(int startIndex) {
    boolean somethingChanged = false;
    if (this.firstPointIndex != startIndex) {
      somethingChanged = true;
      this.firstPointIndex = startIndex;
      for (LXPoint p : this.points) {
        p.index = startIndex++;
      }
    }

    // Reindex our children
    startIndex = this.firstPointIndex + this.points.size();
    for (LXFixture child : this.children) {
      if (child._reindex(startIndex)) {
        somethingChanged = true;
      }
      startIndex += child.totalSize();
    }

    // Only update index buffers and outputs if any indices were changed
    if (somethingChanged) {
      reindexOutputs();
    }

    return somethingChanged;
  }

  /**
   * Constructs an LXModel object for this Fixture
   *
   * @return Model representation of this fixture
   */
  final LXModel toModel() {
    // Creating a new model, clear our set of points
    this.modelPoints.clear();

    // Note: we make a deep copy here because a change to the number of points in one
    // fixture will alter point indices in all fixtures after it. When we're in multi-threaded
    // mode, that point might have been passed to the UI, which holds a reference to the model.
    // The indices passed to the UI cannot be changed mid-flight, so we make new copies of all
    // points here to stay safe.
    for (LXPoint p : this.points) {
      this.modelPoints.add(copyPoint(p));
    }

    // Now iterate over our children and add their points too
    List<LXModel> childModels = new ArrayList<LXModel>();
    for (LXFixture child : this.children) {
      LXModel childModel = child.toModel();
      for (LXPoint p : childModel.points) {
        this.modelPoints.add(p);
      }
      childModels.add(childModel);
    }

    // Generate any submodels references into of this fixture
    for (Submodel submodel : toSubmodels()) {
      childModels.add(submodel);
    }

    // Okay, good to go, construct the model
    LXModel model = constructModel(this.modelPoints, childModels, getModelTags());
    model.transform.set(this.geometryMatrix);
    return this.model = model;
  }

  /**
   * Subclasses may override this method to use custom model type
   *
   * @param modelPoints Points in the model
   * @param childModels Child models
   * @param tags Model tags
   * @return LXModel instance, or concrete subclass
   */
  protected LXModel constructModel(List<LXPoint> modelPoints, List<? extends LXModel> childModels, List<String> tags) {
    return new LXModel(modelPoints, childModels.toArray(new LXModel[0]), getMetaData(), tags);
  }

  /**
   * Subclasses may override this method to use custom point types
   *
   * @param localIndex Index of the point relative to this fixture
   * @return LXPoint or concrete subclass
   */
  protected LXPoint constructPoint(int localIndex) {
    return new LXPoint();
  }

  /**
   * Subclasses may override this method to use custom point types
   *
   * @param copy Point to make a copy of
   * @return LXPoint or concrete subclass, should be deep copy of the original
   */
  protected LXPoint copyPoint(LXPoint copy) {
    return new LXPoint(copy);
  }

  /**
   * Used to generate the meta data fields in model construction
   *
   * @return Map of key-value String pairs
   */
  private final Map<String, String> getMetaData() {
    addModelMetaData(this.metaData);
    return this.metaData;
  }

  /**
   * Subclasses may override to add additiona metadata fields for inclusion in the model
   *
   * @param metaData Map to add meta-data fields to
   */
  protected void addModelMetaData(Map<String, String> metaData) {

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
     */
    public Submodel(int start, int n) {
      this(start, n, 1);
    }

    /**
     * Subclasses may use this helper to construct a submodel object from a set of
     * points in this model.
     *
     * @param start Start index
     * @param n Number of points in the submodel
     * @param tags Model tags for submodel
     */
    public Submodel(int start, int n, String ... tags) {
      this(start, n, 1, tags);
    }

    /**
     * Subclasses may use this helper to construct a submodel object from a set of
     * points in this model.
     *
     * @param start Start index
     * @param n Number of points in the submodel
     * @param stride Stride size for selecting submodel points
     * @param tags Model tags for submodel
     */
    public Submodel(int start, int n, int stride, String ... tags) {
      this(start, n, stride, null, tags);
    }

    /**
     * Subclasses may use this helper to construct a submodel object from a set of
     * points in this model.
     *
     * @param start Start index
     * @param n Number of points in the submodel
     * @param metaData Metadata for this submodel
     * @param tags Model tags for submodel
     */
    public Submodel(int start, int n, Map<String, String> metaData, String ... tags) {
      this(start, n, 1, metaData, tags);
    }

    /**
     * Subclasses may use this helper to construct a submodel object from a set of
     * points in this model.
     *
     * @param start Start index
     * @param n Number of points in the submodel
     * @param stride Stride size for selecting submodel points
     * @param metaData Metadata for submodel
     * @param tags Model tags for submodel
     */
    public Submodel(int start, int n, int stride, Map<String, String> metaData, String ... tags) {
      super(subpoints(start, n, stride), metaData, tags);
      this.transform.set(geometryMatrix);
    }
  }

  /**
   * Subclasses may override to return an array of default tag types for this fixture in the model hierarchy
   *
   * @return List of model tag types for this fixture
   */
  protected String[] getDefaultTags() {
    return new String[0];
  }

  /**
   * Set the string tag values for this fixture
   *
   * @param tags Tag values
   * @return this
   */
  protected LXFixture setTags(List<String> tags) {
    this.tagList.clear();
    this.tagList.addAll(tags);
    return this;
  }

  /**
   * Gets a list of all tags attached to this fixture
   *
   * @return Tags
   */
  private List<String> getModelTags() {
    // Copy the tag list that we own (specified by JSON)
    List<String> modelTags = new ArrayList<String>(this.tagList);

    // Add any extra tags specified by string field
    boolean hasExtra = false;
    String extraTags = this.tags.getString();
    if ((extraTags != null) && !extraTags.isEmpty()) {
      String[] tags = extraTags.trim().split("\\s+");
      for (String tag : tags) {
        tag = tag.trim();
        if (!tag.isEmpty() && LXModel.Tag.isValid(tag)) {
          hasExtra = true;
          modelTags.add(tag);
        }
      }
    }

    // Was no custom tag list specified, and no parameter? Use defaults...
    if (this.tagList.isEmpty() && !hasExtra) {
      for (String tag : getDefaultTags()) {
        modelTags.add(tag);
      }
    }
    return modelTags;
  }

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
   * Subclasses must implement to specify the number of points in the fixture.
   * This does not include the number of points that are in children.
   *
   * @return number of points immediately in the fixture
   */
  protected abstract int size();

  /**
   * Returns the offset of this fixture in the index buffer
   *
   * @return Offset into the index buffer
   */
  public final int getIndexBufferOffset() {
    return this.firstPointIndex;
  }

  /**
   * Returns a copy of the geometry matrix for this fixture
   *
   * @return Copy of geometry matrix
   */
  public LXMatrix getGeometryMatrix() {
    return new LXMatrix(this.geometryMatrix);
  }

  /**
   * Returns the geometry transformation matrix, copied into the given matrix
   *
   * @param m LXMatrix object to copy into
   * @return Geometric transformation matrix, copied into parameter value
   */
  public LXMatrix getGeometryMatrix(LXMatrix m) {
    return m.set(this.geometryMatrix);
  }

  /**
   * Total points in this model and all its submodels
   *
   * @return Total number of points in this model and all submodels
   */
  public final int totalSize() {
    int sum = size();
    for (LXFixture child : this.children) {
      sum += child.totalSize();
    }
    return sum;
  }

  /**
   * Retrieves the point at a given offset in this fixture. This may recursively descend into
   * child fixtures.
   *
   * @param i Index relative to this fixture
   * @return Point at that index, if any
   */
  private LXPoint getPoint(int i) {
    // Check directly owned points first
    if (i < this.points.size()) {
      return this.points.get(i);
    }
    // Not in those, go to subfixtures...
    int ci = i - this.points.size();
    for (LXFixture fixture : children) {
      int fixtureTotalSize = fixture.totalSize();
      if (ci < fixtureTotalSize) {
        return fixture.getPoint(ci);
      }
      ci -= fixtureTotalSize;
    }
    throw new IllegalArgumentException("Point index " + i + " exceeds fixture bounds: " + this + " (" + totalSize() + ")");
  }

  @Override
  public void dispose() {
    for (LXFixture child : this.children) {
      child.dispose();
    }
    for (LXOutput output : this.outputsDirect) {
      output.dispose();
    }
    this.mutableOutputsDirect.clear();
    this.outputDefinitions.clear();
    super.dispose();
  }

  // Flag to avoid unnecessary work while parameters are being loaded... we'll fix
  // everything *after* the parameters are all loaded.
  private boolean isLoading = false;

  @Override
  public void load(LX lx, JsonObject obj) {
    this.isLoading = true;
    super.load(lx, obj);
    this.isLoading = false;

    // Regenerate the whole thing once
    if (this.container != null) {
      regenerate();
    }
  }
}
