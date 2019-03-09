/**
 * Copyright 2019- Mark C. Slee, Heron Arts LLC
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

import java.io.File;
import java.io.FileReader;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.model.LXModel;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.transform.LXTransform;

public class JsonFixture extends LXFixture {

  private static final String KEY_FIXTURE_TYPE = "fixtureType";
  private static final String KEY_POINTS = "points";
  private static final String KEY_STRIPS = "strips";

  private static final String KEY_X = "x";
  private static final String KEY_Y = "y";
  private static final String KEY_Z = "z";
  private static final String KEY_YAW = "yaw";
  private static final String KEY_PITCH = "pitch";
  private static final String KEY_ROLL = "roll";

  private static final String KEY_NUM_POINTS = "numPoints";
  private static final String KEY_SPACING = "spacing";

  public StringParameter fixtureType =
    new StringParameter("Fixture Type")
    .setDescription("Fixture definition file name");

  private JsonArray jsonPoints;
  private JsonArray jsonStrips;
  private int sz;

  public JsonFixture(LX lx) {
    super(lx);
    addMetricsParameter("fixtureType", this.fixtureType);
  }

  @Override
  protected String getModelType() {
    return this.fixtureType.getString();
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.fixtureType) {
      reloadFixture();
    }
    super.onParameterChanged(p);
  }

  private void reloadFixture() {
    this.sz = 0;
    this.jsonPoints = null;
    this.jsonStrips = null;

    File fixtureFile = new File(this.lx.getMediaPath() + File.separator + "fixtures" + File.separator + this.fixtureType.getString() + ".lxf");
    if (!fixtureFile.exists() || !fixtureFile.isFile()) {
      System.err.println("Invalid fixture type, could not find file: " + this.fixtureType.getString());
      return;
    }

    try (FileReader fr = new FileReader(fixtureFile)) {
      JsonObject obj = new Gson().fromJson(fr, JsonObject.class);
      if (obj.has(KEY_FIXTURE_TYPE)) {
        if (this.label.getString().equals("Json")) {
          this.label.setValue(obj.get(KEY_FIXTURE_TYPE).getAsString());
        }
      }
      if (obj.has(KEY_POINTS) ) {
        this.jsonPoints = obj.get(KEY_POINTS).getAsJsonArray();
        this.sz += this.jsonPoints.size();
      }
      if (obj.has(KEY_STRIPS) ) {
        this.jsonStrips = obj.get(KEY_STRIPS).getAsJsonArray();
        for (int i = 0; i < this.jsonStrips.size(); ++i) {
          JsonObject stripObj = this.jsonStrips.get(i).getAsJsonObject();
          this.sz += stripObj.get(KEY_NUM_POINTS).getAsInt();
        }
      }
    } catch (Exception x) {
      System.err.println("Exception loading fixture: " + x.getMessage());
      x.printStackTrace();
    }
  }

  @Override
  protected void computePointGeometry(LXTransform transform) {
    int pi = 0;
    try {
      if (this.jsonPoints != null) {
        for (int i = 0; i < this.jsonPoints.size(); ++i) {
          JsonObject pointObj = this.jsonPoints.get(i).getAsJsonObject();
          float x = getFloat(pointObj, KEY_X, 0);
          float y = getFloat(pointObj, KEY_Y, 0);
          float z = getFloat(pointObj, KEY_Z, 0);
          transform.push();
          transform.translate(x, y, z);
          this.points.get(pi++).set(transform);
          transform.pop();
        }
      }
      if (this.jsonStrips != null) {
        for (int i = 0; i < this.jsonStrips.size(); ++i) {
          JsonObject stripObj = this.jsonStrips.get(i).getAsJsonObject();
          float x = getFloat(stripObj, KEY_X, 0);
          float y = getFloat(stripObj, KEY_Y, 0);
          float z = getFloat(stripObj, KEY_Z, 0);
          float yaw = getFloat(stripObj, KEY_YAW, 0);
          float pitch = getFloat(stripObj, KEY_PITCH, 0);
          float roll = getFloat(stripObj, KEY_ROLL, 0);
          int numPoints = stripObj.get(KEY_NUM_POINTS).getAsInt();
          float spacing = stripObj.get(KEY_SPACING).getAsFloat();
          transform.push();
          transform.translate(x, y, z);
          transform.rotateY(Math.toRadians(yaw));
          transform.rotateX(Math.toRadians(pitch));
          transform.rotateZ(Math.toRadians(roll));
          for (int p = 0; p < numPoints; ++p) {
            this.points.get(pi++).set(transform);
            transform.translate(spacing, 0);
          }
          transform.pop();
        }
      }
    } catch (Exception x) {
      System.err.println("Bad JSON data in fixture " + this.fixtureType.getString() + ": " + x.getMessage());
      x.printStackTrace();
    }
  }

  @Override
  protected LXModel[] toSubmodels() {
    if (this.jsonStrips == null) {
      return NO_SUBMODELS;
    }
    int startIndex = 0;
    if (this.jsonPoints != null) {
      startIndex = this.jsonPoints.size();
    }
    try {
      LXModel[] submodels = new LXModel[this.jsonStrips.size()];
      for (int i = 0; i < this.jsonStrips.size(); ++i) {
        JsonObject stripObj = this.jsonStrips.get(i).getAsJsonObject();
        int numPoints = stripObj.get(KEY_NUM_POINTS).getAsInt();
        submodels[i] = toSubmodel(startIndex, numPoints, 1).setType("strip");
        startIndex += numPoints;
      }
      return submodels;
    } catch (Exception x) {
      x.printStackTrace();
    }
    return NO_SUBMODELS;
  }

  private static float getFloat(JsonObject obj, String key, float def) {
    return obj.has(key) ? obj.get(key).getAsFloat() : def;
  }

  @Override
  protected int size() {
    return this.sz;
  }

}
