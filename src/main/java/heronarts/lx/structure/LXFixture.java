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

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.output.ArtNetDatagram;
import heronarts.lx.output.DDPDatagram;
import heronarts.lx.output.KinetDatagram;
import heronarts.lx.output.LXDatagram;
import heronarts.lx.output.LXOutput;
import heronarts.lx.output.OPCDatagram;
import heronarts.lx.output.StreamingACNDatagram;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
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

  public final EnumParameter<Protocol> protocol =
    new EnumParameter<Protocol>("Protocol", Protocol.NONE)
    .setDescription("Which lighting data protocol this fixture uses");

  public final StringParameter host =
    new StringParameter("Host", "127.0.0.1")
    .setDescription("Host/IP this fixture transmits to");

  public final DiscreteParameter artNetUniverse = (DiscreteParameter)
    new DiscreteParameter("ArtNet Universe", 0, 0, 32768).setUnits(LXParameter.Units.INTEGER)
    .setDescription("Which ArtNet universe is used");

  public final DiscreteParameter opcChannel = (DiscreteParameter)
    new DiscreteParameter("OPC Channel", 0, 0, 256)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("Which OPC channel is used");

  public final DiscreteParameter ddpDataOffset = (DiscreteParameter)
    new DiscreteParameter("DDP Offset", 0, 0, 32768)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("The DDP data offset for this packet");

  public final DiscreteParameter kinetPort = (DiscreteParameter)
    new DiscreteParameter("KiNET Port", 1, 0, 256)
    .setUnits(LXParameter.Units.INTEGER)
    .setDescription("Which KiNET physical output port is used");

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

  private LXDatagram datagram = null;

  private final Set<LXParameter> metricsParameters = new HashSet<LXParameter>();

  private final Set<LXParameter> geometryParameters = new HashSet<LXParameter>();

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
    addParameter("protocol", this.protocol);
    addParameter("host", this.host);
    addParameter("artNetUniverse", this.artNetUniverse);
    addParameter("opcChannel", this.opcChannel);
    addParameter("ddpDataOffset", this.ddpDataOffset);
    addParameter("kinetPort", this.kinetPort);
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
   * Changes to these parameters require re-generating the points array.
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

  /**
   * Accessor for the datagram that corresponds to this fixture
   *
   * @return Datagram that sends data for this fixture
   */
  public LXDatagram getDatagram() {
    return this.datagram;
  }

  protected void updateDatagram() {
    this.datagram = makeDatagram();
  }

  private LXDatagram makeDatagram() {
    Protocol protocol = this.protocol.getEnum();
    if (protocol == Protocol.NONE) {
      return null;
    }

    // Build index buffer
    int[] indexBuffer = toIndexBuffer();

    LXDatagram datagram;
    switch (protocol) {
    case ARTNET:
      datagram = new ArtNetDatagram(indexBuffer, this.artNetUniverse.getValuei());
      break;
    case SACN:
      datagram = new StreamingACNDatagram(indexBuffer, this.artNetUniverse.getValuei());
      break;
    case OPC:
      datagram = new OPCDatagram(indexBuffer, (byte) this.opcChannel.getValuei());
      break;
    case DDP:
      datagram = new DDPDatagram(indexBuffer).setDataOffset(this.ddpDataOffset.getValuei());
      break;
    case KINET:
      datagram = new KinetDatagram(this.kinetPort.getValuei(), indexBuffer);
      break;
    default:
    case NONE:
      throw new IllegalStateException("Unhandled protocol type: " + protocol);
    }

    datagram.brightness.setValue(this.brightness.getValue());
    datagram.enabled.setValue(this.enabled.isOn());

    try {
      datagram.setAddress(this.host.getString());
    } catch (UnknownHostException uhx) {
      // TODO(mcslee): get an error to the UI...
      datagram.enabled.setValue(false);
      LXOutput.error(uhx, "Unkown host for fixture datagram: " + this.host.getString());
    }

    return datagram;
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (this.metricsParameters.contains(p)) {
      if (!this.isLoading) {
        regeneratePoints();
        this.lx.structure.regenerateModel();
      }
    } else if (this.geometryParameters.contains(p)) {
      // If only geometry has changed, we can be a little more clever here and
      // just re-generate
      if (!this.isLoading) {
        regenerateGeometry();
        this.lx.structure.fixtureGeometryRegenerated(this);
      }
    }
    if (p == this.protocol) {
      updateDatagram();
    } else if (p == this.enabled) {
      if (this.datagram != null) {
        this.datagram.enabled.setValue(this.enabled.isOn());
      }
    } else if (p == this.brightness) {
      if (this.datagram != null) {
        this.datagram.brightness.setValue(this.brightness.getValue());
      }
    } else if (p == this.artNetUniverse) {
      if (this.datagram instanceof ArtNetDatagram) {
        ((ArtNetDatagram) this.datagram)
          .setUniverseNumber(this.artNetUniverse.getValuei());
      } else if (this.datagram instanceof StreamingACNDatagram) {
        ((StreamingACNDatagram) this.datagram)
          .setUniverseNumber(this.artNetUniverse.getValuei());
      }
    } else if (p == this.ddpDataOffset) {
      if (this.datagram instanceof DDPDatagram) {
        ((DDPDatagram) this.datagram)
          .setDataOffset(this.ddpDataOffset.getValuei());
      }
    } else if (p == this.opcChannel) {
      if (this.datagram instanceof OPCDatagram) {
        ((OPCDatagram) this.datagram)
          .setChannel((byte) this.opcChannel.getValuei());
      }
    } else if (p == this.kinetPort) {
      if (this.datagram instanceof KinetDatagram) {
        ((KinetDatagram) this.datagram)
          .setKinetPort((byte) this.kinetPort.getValuei());
      }
    } else if (p == this.host) {
      if (this.datagram != null) {
        try {
          this.datagram.setAddress(this.host.getString());
        } catch (UnknownHostException uhx) {
          this.datagram.enabled.setValue(false);
          // TODO(mcslee): get an error to the UI...
          LXOutput.error(uhx, "Unkown host for fixture datagram: " + this.host.getString());
        }
      }
    } else if (p == this.solo) {
      if (this.solo.isOn()) {
        this.lx.structure.soloFixture(this);
      }
    }
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

  private void reindexPoints(int startIndex) {
    for (LXPoint p : this.points) {
      p.index = startIndex++;
    }
  }

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
    reindexPoints(startIndex);

    // Our point indices may have changed... we'll need to update the datagram
    updateDatagram();

    // Generate a deep copy of all our points
    this.modelPoints = new ArrayList<LXPoint>(this.points.size());
    for (LXPoint p : this.points) {
      // Note: we make a deep copy here because a change to the number of points in one
      // fixture will alter point indices in all fixtures after it. When we're in multi-threaded
      // mode, that point might have been passed to the UI, which holds a reference to the model.
      // The indices passed to the UI cannot be changed mid-flight, so we make new copies of all
      // points here to stay safe.
      this.modelPoints.add(new LXPoint(p));
    }

    // Generate our submodels
    LXModel[] submodels = toSubmodels();

    // Okay, good to go
    return new LXModel(this.modelPoints, submodels, getModelKey());
  }

  /**
   * Subclasses should impelment, specifying the type key of this fixture in the model
   * hierarchy.
   *
   * @return String key for the model type
   */
  protected abstract String getModelKey();

  protected final static LXModel[] NO_SUBMODELS = new LXModel[0];

  /**
   * Subclasses may override when they contain submodels
   *
   * @return Array of submodel objects
   */
  protected LXModel[] toSubmodels() {
    return NO_SUBMODELS;
  }

  /**
   * Subclasses may use this helper to construct a submodel object from a set of
   * points in this model.
   *
   * @param start Start index
   * @param n Number of points in the submodel
   * @param stride Stride size for selecting submodel points
   * @return Submodel object
   */
  protected LXModel toSubmodel(int start, int n, int stride) {
    return toSubmodel(start, n, stride, LXModel.Key.MODEL);
  }

  /**
   * Subclasses may use this helper to construct a submodel object from a set of
   * points in this model.
   *
   * @param start Start index
   * @param n Number of points in the submodel
   * @param stride Stride size for selecting submodel points
   * @param keys Model type key identifier for submodel
   * @return Submodel object
   */
  protected LXModel toSubmodel(int start, int n, int stride, String ... keys) {
    List<LXPoint> submodel = new ArrayList<LXPoint>(n);
    for (int i = 0; i < n; ++i) {
      submodel.add(this.modelPoints.get(start + i*stride));
    }
    return new LXModel(submodel, keys);
  }

  /**
   * Subclasses must implement to specify the number of points in the fixture
   *
   * @return number of points in the fixture
   */
  protected abstract int size();

  private boolean isLoading = false;

  @Override
  public void load(LX lx, JsonObject obj) {
    this.isLoading = true;
    super.load(lx, obj);
    this.isLoading = false;
    regeneratePoints();
    this.lx.structure.regenerateModel();
    updateDatagram();
  }
}
