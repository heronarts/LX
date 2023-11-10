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

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.osc.LXOscEngine;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.utils.LXUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of Audio Definition Model data. This class does not do any processing,
 * it merely holds data that was received via the ADM-OSC protocol.
 *
 * https://github.com/immersive-audio-live/ADM-OSC
 */
public class ADM extends LXComponent {

  public static final int MAX_ADM_OBJECTS = 64;

  public class Obj extends LXComponent {

    public final int objectId;

    public final BoundedParameter azimuth =
      new BoundedParameter("Azimuth", 0, -180, 180)
      .setUnits(BoundedParameter.Units.DEGREES)
      .setDescription("Azimuth about horizontal plane in degrees, counter-clockwise");

    public final BoundedParameter elevation =
      new BoundedParameter("Elevation", 0, -90, 90)
      .setUnits(BoundedParameter.Units.DEGREES)
      .setDescription("Elevation from horizontal plane in degrees");

    public final BoundedParameter distance =
      new BoundedParameter("Distance", 1, 0, 1)
      .setDescription("Distance from center point, normalized 0-1");

    public final BoundedParameter x =
      new BoundedParameter("X", 0, -1, 1)
      .setDescription("Normalized X position, left-right");

    public final BoundedParameter y =
      new BoundedParameter("Y", 0, -1, 1)
      .setDescription("Normalized Y position, back-front");

    public final BoundedParameter z =
      new BoundedParameter("Z", 0, -1, 1)
      .setDescription("Normalized Z position, bottom-top");

    public final BooleanParameter cartesian =
      new BooleanParameter("Cartesian", false)
      .setDescription("Whether cartesian units are used");

    private Obj(LX lx, int objectId) {
      super(lx, "ADMObject-" + objectId);
      this.objectId = objectId;
      setParent(ADM.this);

      addParameter("azimuth", this.azimuth);
      addParameter("elevation", this.elevation);
      addParameter("distance", this.distance);

      addParameter("x", this.x);
      addParameter("y", this.y);
      addParameter("z", this.z);

      addParameter("cartesian", this.cartesian);
    }

    protected void updatePolar() {
      final double x = this.x.getValue();
      final double y = this.y.getValue();
      final double z = this.z.getValue();
      final double dist = LXUtils.dist(x, y, z, 0, 0, 0);
      this.distance.setValue(dist);
      if (dist > 0) {
        final double xydist = LXUtils.dist(x, y, 0, 0);
        if (xydist > 0) {
          this.azimuth.setValue(Math.toDegrees(Math.atan2(-x, y)));
          this.elevation.setValue(Math.atan(z / xydist));
        } else if (z != 0) {
          this.elevation.setValue(z > 0 ? 90 : -90);
        }
      }
    }

    @Override
    public String getPath() {
      return ADM_OBJ_PATH + "/" + this.objectId;
    }
  }

  public final List<Obj> obj;

  public ADM(LX lx) {
    super(lx, "ADM");
    List<Obj> mutableObj = new ArrayList<Obj>(MAX_ADM_OBJECTS);
    for (int i = 0; i < MAX_ADM_OBJECTS; ++i) {
      mutableObj.add(new Obj(lx, i+1));
    }
    this.obj = Collections.unmodifiableList(mutableObj);
    addArray(ADM_OBJ_PATH, this.obj);
  }

  public static final String ADM_OSC_PATH = "adm";
  public static final String ADM_OBJ_PATH = "obj";
  public static final String ADM_AZIM_PATH = "azim";
  public static final String ADM_ELEV_PATH = "elev";
  public static final String ADM_DIST_PATH = "dist";
  public static final String ADM_AED_PATH = "aed";
  public static final String ADM_X_PATH = "x";
  public static final String ADM_Y_PATH = "y";
  public static final String ADM_Z_PATH = "z";
  public static final String ADM_XYZ_PATH = "xyz";
  public static final String ADM_CONFIG_PATH = "config";
  public static final String ADM_CARTESIAN_PATH = "cartesian";

  public boolean handleAdmOscMessage(OscMessage message, String[] parts, int index) {
    if (parts.length < 5) {
      LXOscEngine.error("ADM address pattern is incomplete: " + message.getAddressPattern().getValue());
      return false;
    }
    if (ADM_OBJ_PATH.equals(parts[2])) {
      try {
        final int objIndex = Integer.parseInt(parts[3]);
        if (objIndex <= 0) {
          throw new NumberFormatException("ADM OSC object index must be positive: " + message.getAddressPattern().getValue());
        } else if (objIndex > ADM.MAX_ADM_OBJECTS) {
          throw new NumberFormatException("ADM OSC object out of bounds: " + message.getAddressPattern().getValue());
        }
        final Obj obj = this.obj.get(objIndex-1);
        final String field = parts[4];

        if (ADM_AED_PATH.equals(field)) {
          obj.azimuth.setValue(message.getFloat(0));
          obj.elevation.setValue(message.getFloat(1));
          obj.distance.setValue(message.getFloat(2));
        } else if (ADM_AZIM_PATH.equals(field)) {
          obj.azimuth.setValue(message.getFloat(0));
        } else if (ADM_ELEV_PATH.equals(field)) {
          obj.elevation.setValue(message.getFloat(0));
        } else if (ADM_DIST_PATH.equals(field)) {
          obj.distance.setValue(message.getFloat(0));
        } else if (ADM_XYZ_PATH.equals(field)) {
          obj.x.setValue(message.getFloat(0));
          obj.y.setValue(message.getFloat(1));
          obj.z.setValue(message.getFloat(2));
          obj.updatePolar();
        } else if (ADM_X_PATH.equals(field)) {
          obj.x.setValue(message.getFloat(0));
          obj.updatePolar();
        } else if (ADM_Y_PATH.equals(field)) {
          obj.y.setValue(message.getFloat(0));
          obj.updatePolar();
        } else if (ADM_Z_PATH.equals(field)) {
          obj.z.setValue(message.getFloat(0));
          obj.updatePolar();
        } else if (ADM_CONFIG_PATH.equals(field)) {
          if (ADM_CARTESIAN_PATH.equals(parts[5])) {
            obj.cartesian.setValue(message.getBoolean());
          }
        }
        return true;
      } catch (NumberFormatException nfx) {
        LXOscEngine.error("Bad ADM object identifier: " + message.getAddressPattern().getValue());
      }
    }

    return false;
  }
}
