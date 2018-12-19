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
import java.util.Set;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.transform.LXMatrix;
import heronarts.lx.transform.LXTransform;

public abstract class LXFixture extends LXComponent implements LXComponent.Renamable {

  public enum Protocol {
    NONE("None"),
    ARTNET("Art-Net"),
    OPC("OPC"),
    SACN("E1.31 Streaming ACN"),
    DDP("DDP"),
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

  public final BooleanParameter identify =
    new BooleanParameter("Identify", false);

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

  public final EnumParameter<Protocol> protocol =
    new EnumParameter<Protocol>("Protocol", Protocol.NONE)
    .setDescription("Which lighting data protocol this fixture uses");

  protected LXMatrix parentTransformMatrix = new LXMatrix();

  private LXTransform transform = new LXTransform();

  private final List<LXPoint> mutablePoints = new ArrayList<LXPoint>();
  public final List<LXPoint> points = Collections.unmodifiableList(this.mutablePoints);

  private final Set<LXParameter> geometryParameters = new HashSet<LXParameter>();

  protected LXFixture(LX lx) {
    super(lx);
    this.label.setValue(getClass().getSimpleName());
    addGeometryParameter("x", this.x);
    addGeometryParameter("y", this.y);
    addGeometryParameter("z", this.z);
    addGeometryParameter("yaw", this.yaw);
    addGeometryParameter("pitch", this.pitch);
    addGeometryParameter("roll", this.roll);
    addParameter("protocol", this.protocol);
    addParameter("idenfity", this.identify);
  }

  protected LXFixture addGeometryParameter(String path, LXParameter parameter) {
    super.addParameter(path, parameter);
    this.geometryParameters.add(parameter);
    return this;
  }

  protected LXFixture setParentTransformMatrix(LXMatrix parentTransformMatrix) {
    this.parentTransformMatrix = parentTransformMatrix;
    regenerate();
    return this;
  }

  protected LXMatrix getTransformMatrix() {
    return this.transform.getMatrix();
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (this.geometryParameters.contains(p)) {
      regenerate();
      getLX().structure.regenerateModel();
    }
  }

  protected LXFixture addPoints(List<LXPoint> points) {
    this.mutablePoints.addAll(points);
    return this;
  }

  protected LXFixture addPoint(LXTransform transform) {
    return addPoint(new LXPoint(transform));
  }

  protected LXFixture addPoint(LXPoint point) {
    this.mutablePoints.add(point);
    return this;
  }

  public LXFixture setOrder(int index) {
    getLX().structure.setFixtureIndex(this, index);
    return this;
  }

  void regenerate() {
    double degreesToRadians = Math.PI / 180;
    this.transform.reset(this.parentTransformMatrix);
    this.transform.translate(this.x.getValuef(), this.y.getValuef(), this.z.getValuef());
    this.transform.rotateY(this.yaw.getValuef() * degreesToRadians);
    this.transform.rotateX(this.pitch.getValuef() * degreesToRadians);
    this.transform.rotateZ(this.roll.getValuef() * degreesToRadians);
    this.mutablePoints.clear();
    this.transform.push();
    generatePoints(this.transform);
    this.transform.pop();
  }

  protected abstract void generatePoints(LXTransform transform);

}
