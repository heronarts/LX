/**
 * Copyright 2020- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.pattern.form;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXLayer;
import heronarts.lx.LXSerializable;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.LXPoint;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.FORM)
public class PlanesPattern extends LXPattern {

  public final static int MAX_PLANES = 8;

  private class PlaneConstants {
    public float a;
    public float b;
    public float c;
    public float d;

    public float ap;
    public float bp;
    public float cp;

    public float invSqrt;
  }

  public interface AxisFunction {
    public float getDistance(LXPoint p, PlaneConstants args);
  }

  public enum Axis {
    X("X-axis", (p, a) -> {
      return a.invSqrt * Math.abs(
        (p.xn - a.ap) * a.a +
        (p.yn - a.bp) * a.b +
        (p.zn - a.cp) * a.c);
    }),

    Y("Y-axis", (p, a) -> {
      return a.invSqrt * Math.abs(
        (p.yn - a.ap) * a.a +
        (p.zn - a.bp) * a.b +
        (p.xn - a.cp) * a.c);
    }),

    Z("Z-axis", (p, a) -> {
      return a.invSqrt * Math.abs(
        (p.zn - a.ap) * a.a +
        (p.xn - a.bp) * a.b +
        (p.yn - a.cp) * a.c);
    }),

    FREE("Free", (p, a) -> {
      return a.invSqrt * Math.abs(
        (p.xn - a.ap) * a.a +
        (p.yn - a.bp) * a.b +
        (p.zn - a.cp) * a.c +
        a.d);
    }),

    RC("R-center", (p, a) -> {
      return p.rcn - a.d;
    }),

    RO("R-origin", (p, a) -> {
      return p.rn - a.d;
    });

    public final String label;
    public final AxisFunction function;

    private Axis(String label, AxisFunction function) {
      this.label = label;
      this.function = function;
    }

    @Override
    public String toString() {
      return this.label;
    }

    public boolean hasRotationControls() {
      switch (this) {
      case X:
      case Y:
      case Z:
        return true;
      default:
        return false;
      }
    }
  }

  public class Plane extends LXLayer implements LXOscComponent {

    public final int index;

    public final BooleanParameter active = new BooleanParameter("Active", false);

    public final EnumParameter<Axis> axis = new EnumParameter<Axis>("Axis", Axis.X);

    public final CompoundParameter level =
      new CompoundParameter("Level", 1)
      .setDescription("Brightness of this plane");

    public final CompoundParameter position =
      new CompoundParameter("Position", 0, -1, 1)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setDescription("Position of the plane");

    public final BoundedParameter positionMin =
      new BoundedParameter("Min Position", 0)
      .setDescription("Minimum position of the plane");

    public final BoundedParameter positionMax =
      new BoundedParameter("Max Position", 1)
      .setDescription("Maximum position of the plane");

    public final CompoundParameter width =
      new CompoundParameter("Width", 0.1)
      .setDescription("Width of the plane");

    public final BoundedParameter widthMin =
      new BoundedParameter("Min Width", 0)
      .setDescription("Minimum width of the plane");

    public final BoundedParameter widthMax =
      new BoundedParameter("Max Width", 1)
      .setDescription("Maximum width of the plane");

    public final CompoundParameter fade =
      new CompoundParameter("Fade", 0.1)
      .setDescription("Fade from the plane edge");

    public final CompoundParameter tiltPosition =
      new CompoundParameter("Tilt Pos", 0, -1, 1)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setDescription("Tilt position");

    public final CompoundParameter tilt =
      new CompoundParameter("Tilt", 0, -1, 1)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setDescription("Tilt of the plane");

    public final CompoundParameter rollPosition =
      new CompoundParameter("Roll Pos", 0, -1, 1)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setDescription("Roll position");

    public final CompoundParameter roll =
      new CompoundParameter("Roll", 0, -1, 1)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setDescription("Roll of the plane");

    public final CompoundParameter planeA =
      new CompoundParameter("Ax", 1, -1, 1)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setDescription("Plane constant A");

    public final CompoundParameter planeB =
      new CompoundParameter("By", 0, -1, 1)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setDescription("Plane constant B");

    public final CompoundParameter planeC =
      new CompoundParameter("Cz", 0, -1, 1)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setDescription("Plane constant C");

    public final CompoundParameter planeD =
      new CompoundParameter("D", 0, -1, 1)
      .setPolarity(CompoundParameter.Polarity.BIPOLAR)
      .setDescription("Plane constant D");

    private final PlaneConstants args = new PlaneConstants();

    protected Plane(LX lx, int index) {
      super(lx);
      this.index = index;
      addParameter("level", this.level);
      addParameter("position", this.position);
      addParameter("width", this.width);
      addParameter("fade", this.fade);
      addParameter("tilt", this.tilt);
      addParameter("roll", this.roll);
      addParameter("tiltPosition", this.tiltPosition);
      addParameter("rollPosition", this.rollPosition);

      addParameter("active", this.active);

      addParameter("positionMin", this.positionMin);
      addParameter("positionMax", this.positionMax);

      addParameter("widthMin", this.widthMin);
      addParameter("widthMax", this.widthMax);

      addParameter("planeA", this.planeA);
      addParameter("planeB", this.planeB);
      addParameter("planeC", this.planeC);
      addParameter("planeD", this.planeD);

      this.active.setValue(index == 0);
    }

    @Override
    public String getPath() {
      return "plane/" + (this.index + 1);
    }

    @Override
    public void run(double deltaMs) {
      if (!this.active.isOn()) {
        return;
      }

      Axis axis = this.axis.getEnum();
      AxisFunction function = axis.function;
      float position = LXUtils.lerpf(this.positionMin.getValuef(), this.positionMax.getValuef(), .5f * (1 + this.position.getValuef()));
      float width = .5f * LXUtils.lerpf(this.widthMin.getValuef(), this.widthMax.getValuef(), this.width.getValuef());
      float fade = 1 / width / this.fade.getValuef();

      switch (axis) {
      case X:
      case Y:
      case Z:
        args.ap = position;
        args.bp = .5f * (1 + this.tiltPosition.getValuef());
        args.cp = .5f * (1 + this.rollPosition.getValuef());

        args.a = (float) (Math.cos(-Math.PI * this.tilt.getValuef()) * Math.cos(-Math.PI * this.roll.getValuef()));
        args.b = (float) (Math.sin(-Math.PI * this.tilt.getValuef()) * Math.cos(-Math.PI * this.roll.getValuef()));
        args.c = (float) (Math.sin(-Math.PI * this.roll.getValuef()) * Math.cos(-Math.PI * this.tilt.getValuef()));
        args.invSqrt = (float) (1 / Math.sqrt(args.a * args.a + args.b * args.b + args.c * args.c));
        break;

      case RC:
      case RO:
        args.d = position;
        break;

      default:
      case FREE:
        args.a = this.planeA.getValuef();
        args.b = this.planeB.getValuef();
        args.c = this.planeC.getValuef();
        args.d = -position - this.planeD.getValuef();
        break;

      }

      float level = this.level.getValuef();
      for (LXPoint p : model.points) {
        float d = Math.abs(function.getDistance(p, args));
        float bn = LXUtils.minf(1, 1 - (d - width) * fade);
        if (bn > 0) {
          addColor(p.index, LXColor.grayn(level * bn));
        }
      }
    }

  }

  public final List<Plane> planes;

  public PlanesPattern(LX lx) {
    super(lx);
    List<Plane> mutablePlanes = new ArrayList<Plane>();
    for (int i = 0; i < MAX_PLANES; ++i) {
      Plane plane = new Plane(lx, i);
      addLayer(plane);
      mutablePlanes.add(plane);
    }
    this.planes = Collections.unmodifiableList(mutablePlanes);
    addArray("plane", this.planes);
  }

  private static final String KEY_PLANES = "planes";

  @Override
  public void run(double deltaMs) {
    setColors(LXColor.BLACK);
  }

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_PLANES, LXSerializable.Utils.toArray(lx, this.planes));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(KEY_PLANES)) {
      JsonArray planesArray = obj.getAsJsonArray(KEY_PLANES);
      int i = 0;
      for (JsonElement planeElement : planesArray) {
        this.planes.get(i++).load(lx, planeElement.getAsJsonObject());
      }
    }
  }

}
