/**
 * Copyright 2023- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.audio;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXNormalizationBounds;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXListenableParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.structure.LXFixture;
import heronarts.lx.transform.LXMatrix;
import heronarts.lx.transform.LXVector;
import heronarts.lx.utils.LXUtils;

public class SoundStage extends LXComponent implements LXOscComponent {

  public enum StageMode {
    DEFAULT("Default"),
    RELATIVE("Relative"),
    ABSOLUTE("Absolute");

    public final String label;

    private StageMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public enum MeterMode {
    NONE("None"),
    OBJECT_SIZE("Size"),
    METER("Meter");

    public final String label;

    private MeterMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  public enum ObjectPositionMode {
    ABSOLUTE("Abs"),
    RADIAL("Radial"),
    BOX("Box");

    public final String label;

    private ObjectPositionMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  private final LXMatrix rotationMatrix = new LXMatrix();

  private final LXVector basisX = new LXVector();

  private final LXVector basisY = new LXVector();

  private final LXVector basisZ = new LXVector();

  public final LXVector rotation = new LXVector();

  public final LXVector center = new LXVector();

  public final LXVector size = new LXVector();

  public final EnumParameter<StageMode> mode =
    new EnumParameter<StageMode>("Stage Mode", StageMode.DEFAULT)
    .setDescription("How to specify sound stage dimensions");

  public final BoundedParameter xAbsolute =
    new BoundedParameter("X", 0, -LXFixture.POSITION_RANGE, LXFixture.POSITION_RANGE)
    .setDescription("Absolute X position of the sound stage center");

  public final BoundedParameter yAbsolute =
    new BoundedParameter("Y", 0, -LXFixture.POSITION_RANGE, LXFixture.POSITION_RANGE)
    .setDescription("Absolute Y position of the sound stage center");

  public final BoundedParameter zAbsolute =
    new BoundedParameter("Z", 0, -LXFixture.POSITION_RANGE, LXFixture.POSITION_RANGE)
    .setDescription("Absolute Z position of the sound stage center");

  public final BoundedParameter widthAbsolute =
    new BoundedParameter("Width", 100, 0, LXFixture.POSITION_RANGE)
    .setDescription("Absolute width of the sound stage");

  public final BoundedParameter heightAbsolute =
    new BoundedParameter("Height", 100, 0, LXFixture.POSITION_RANGE)
    .setDescription("Absolute height of the sound stage");

  public final BoundedParameter depthAbsolute =
    new BoundedParameter("Depth", 100, 0, LXFixture.POSITION_RANGE)
    .setDescription("Absolute depth of the sound stage");

  public final BoundedParameter azimuthAbsolute =
    new BoundedParameter("Azimuth", 0, -180, 180)
    .setUnits(BoundedParameter.Units.DEGREES)
    .setDescription("Azimuth of the front of the sound stage");

  public final BoundedParameter elevationAbsolute =
    new BoundedParameter("Elevation", 0, -90, 90)
    .setUnits(BoundedParameter.Units.DEGREES)
    .setDescription("Elevation of the front of the sound stage");

  public final BoundedParameter xRelative =
    new BoundedParameter("X", 0, -5, 5)
    .setUnits(BoundedParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Relative X position of the sound stage center");

  public final BoundedParameter yRelative =
    new BoundedParameter("Y", 0, -5, 5)
    .setUnits(BoundedParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Relative Y position of the sound stage center");

  public final BoundedParameter zRelative =
    new BoundedParameter("Z", 0, -5, 5)
    .setUnits(BoundedParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Relative Z position of the sound stage center");

  public final BoundedParameter widthRelative =
    new BoundedParameter("Width", 1, 0, 5)
    .setUnits(BoundedParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Relative width of the sound stage");

  public final BoundedParameter heightRelative =
    new BoundedParameter("Height", 1, 0, 5)
    .setUnits(BoundedParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Relative height of the sound stage");

  public final BoundedParameter depthRelative =
    new BoundedParameter("Depth", 1, 0, 5)
    .setUnits(BoundedParameter.Units.PERCENT_NORMALIZED)
    .setDescription("Relative depth of the sound stage");

  public final BoundedParameter azimuthRelative =
    new BoundedParameter("Azimuth", 0, -180, 180)
    .setUnits(BoundedParameter.Units.DEGREES)
    .setDescription("Azimuth of the front of the sound stage");

  public final BoundedParameter elevationRelative =
    new BoundedParameter("Elevation", 0, -90, 90)
    .setUnits(BoundedParameter.Units.DEGREES)
    .setDescription("Elevation of the front of the sound stage");

  public final BooleanParameter showSoundStage =
    new BooleanParameter("Show Sound Stage", false)
    .setDescription("Render sound stage in the UI");

  public final BooleanParameter showSoundObjects =
    new BooleanParameter("Show Sound Objects", true)
    .setDescription("Render sound objects in the UI");

  public final EnumParameter<MeterMode> soundObjectMeterMode =
    new EnumParameter<MeterMode>("Meter Mode", MeterMode.NONE)
    .setDescription("How to show sound object meters");

  public final BoundedParameter soundObjectSize =
    new BoundedParameter("Sound Object Size", 10, 1, 100000)
    .setDescription("Size of sound objects");

  public SoundStage(LX lx) {
    super(lx);

    addGeometryParameter("mode", this.mode);
    addGeometryParameter("xAbsolute", this.xAbsolute);
    addGeometryParameter("yAbsolute", this.yAbsolute);
    addGeometryParameter("zAbsolute", this.zAbsolute);
    addGeometryParameter("widthAbsolute", this.widthAbsolute);
    addGeometryParameter("heightAbsolute", this.heightAbsolute);
    addGeometryParameter("depthAbsolute", this.depthAbsolute);
    addGeometryParameter("azimuthAbsolute", this.azimuthAbsolute);
    addGeometryParameter("elevationAbsolute", this.elevationAbsolute);

    addGeometryParameter("xRelative", this.xRelative);
    addGeometryParameter("yRelative", this.yRelative);
    addGeometryParameter("zRelative", this.zRelative);
    addGeometryParameter("widthRelative", this.widthRelative);
    addGeometryParameter("heightRelative", this.heightRelative);
    addGeometryParameter("depthRelative", this.depthRelative);
    addGeometryParameter("azimuthRelative", this.azimuthRelative);
    addGeometryParameter("elevationRelative", this.elevationRelative);

    addParameter("showSoundStage", this.showSoundStage);
    addParameter("showSoundObjects", this.showSoundObjects);
    addParameter("soundObjectMeterMode", this.soundObjectMeterMode);
    addParameter("soundObjectSize", this.soundObjectSize);

    recomputeBounds(null);

    lx.addListener(new LX.Listener() {
      @Override
      public void modelChanged(LX lx, LXModel model) {
        recomputeBounds(null);
      }
      @Override
      public void modelGenerationChanged(LX lx, LXModel model) {
        recomputeBounds(null);
      }
    });
  }

  private void addGeometryParameter(String path, LXListenableParameter p) {
    addParameter(path, p);
    p.addListener(this::recomputeBounds);
  }

  private void setRotation(float azimuth, float elevation) {
    this.rotation.set(elevation, azimuth, 0);
    this.rotationMatrix
      .identity()
      .rotateX((float) Math.toRadians(elevation))
      .rotateY((float) -Math.toRadians(azimuth));

    this.basisX.set(this.rotationMatrix.m11, this.rotationMatrix.m12, this.rotationMatrix.m13);
    this.basisY.set(this.rotationMatrix.m21, this.rotationMatrix.m22, this.rotationMatrix.m23);
    this.basisZ.set(this.rotationMatrix.m31, this.rotationMatrix.m32, this.rotationMatrix.m33);
  }

  private void recomputeBounds(LXParameter p) {
    final LXModel model = this.lx.getModel();

    switch (this.mode.getEnum()) {
    case DEFAULT:
      setRotation(0, 0);
      this.center.set(model.cx, model.cy, model.cz);
      this.size.set(model.xRange, model.yRange, model.zRange);
      break;

    case RELATIVE:
      setRotation(this.azimuthRelative.getValuef(), this.elevationRelative.getValuef());
      this.center.set(
        model.cx + model.xRange * this.xRelative.getValuef(),
        model.cy + model.yRange * this.yRelative.getValuef(),
        model.cz + model.zRange * this.zRelative.getValuef()
      );
      this.size.set(
        model.xRange * this.widthRelative.getValuef(),
        model.yRange * this.heightRelative.getValuef(),
        model.zRange * this.depthRelative.getValuef()
      );
      break;

    case ABSOLUTE:
      setRotation(this.azimuthAbsolute.getValuef(), this.elevationAbsolute.getValuef());
      this.center.set(
        this.xAbsolute.getValuef(),
        this.yAbsolute.getValuef(),
        this.zAbsolute.getValuef()
      );
      this.size.set(
        this.widthAbsolute.getValuef(),
        this.heightAbsolute.getValuef(),
        this.depthAbsolute.getValuef()
      );
      break;
    }
  }

  /**
   * Gets the normalized sound object position in the global model space
   *
   * @param object Sound object
   * @return Vector of normalized x/y/z values for sound object position
   */
  public LXVector getNormalizedObjectPosition(SoundObject object) {
    return getNormalizedObjectPosition(object, this.lx.getModel());
  }

  /**
   * Gets the normalized sound object position in the global model space
   *
   * @param object Sound object
   * @param reference Model to perform normalization against
   * @return Vector of normalized x/y/z values for sound object position
   */
  public LXVector getNormalizedObjectPosition(SoundObject object, LXModel reference) {
    return getNormalizedObjectPosition(object, ObjectPositionMode.ABSOLUTE, reference);
  }

  /**
   * For the given sound object, determine its position using the given mode of placement
   * in the sound stage, and then re-normalized it against the bounds of the reference model.
   * Note that this version of the method allocates memory.
   *
   * @param object Sound object
   * @param mode Sound stage positioning mode
   * @param reference Reference model
   * @return The position vector
   */
  public LXVector getNormalizedObjectPosition(SoundObject object, ObjectPositionMode mode, LXModel reference) {
    return getNormalizedObjectPosition(object, mode, reference, null);
  }

  /**
   * For the given sound object, determine its position using the given mode of placement
   * in the sound stage, and then re-normalized it against the bounds of the reference model
   *
   * @param object Sound object
   * @param mode Sound stage positioning mode
   * @param model Model being rendered to
   * @param position Position vector to fill
   * @return The position vector
   */
  public LXVector getNormalizedObjectPosition(SoundObject object, ObjectPositionMode mode, LXModel model, LXVector position) {
    if (position == null) {
      position = new LXVector();
    }

    switch (mode) {
    case ABSOLUTE:
      position.set(-.5f, -.5f, -.5f).add(object.position);
      break;
    case BOX:
      // We pin to the outside of the box, scaling up by whatever's needed for the absolute
      // value of the largest dimension to reach 1
      position.set(object.normalized);
      final float scale = LXUtils.maxf(Math.abs(position.x), Math.abs(position.y), Math.abs(position.z));
      position.mult(.5f / scale);
      break;
    case RADIAL:
      // Scale the normalized [-1,1] vectors by half
      position.set(object.normalized).mult(.5f);
      break;
    default:
      break;
    }

    // At this point the vector values are in range [-.5f,.5f] (possibly exceeding in ABSOLUTE)
    // but they are relative to the sound stage bounds. We will scale by the basis vectors and
    // move to the center of the sound field
    final float xb = position.x;
    final float yb = position.y;
    final float zb = position.z;

    position
      .set(this.center)
      .add(this.basisX, xb * this.size.x)
      .add(this.basisY, yb * this.size.y)
      .add(this.basisZ, zb * this.size.z);

    // Now the values are absolutely in the sound stage, we need to normalized them into to the
    // reference model by doing an inverse interpolation
    final LXNormalizationBounds bounds = model.getNormalizationBounds();
    position.set(
      (bounds.xRange == 0) ? .5f : LXUtils.ilerpf(position.x, bounds.xMin, bounds.xMax),
      (bounds.yRange == 0) ? .5f : LXUtils.ilerpf(position.y, bounds.yMin, bounds.yMax),
      (bounds.zRange == 0) ? .5f : LXUtils.ilerpf(position.z, bounds.zMin, bounds.zMax)
    );

    return position;
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    if (obj.has(KEY_RESET)) {
      for (LXParameter p : getParameters()) {
        p.reset();
      }
    }
    super.load(lx, obj);
  }

}
