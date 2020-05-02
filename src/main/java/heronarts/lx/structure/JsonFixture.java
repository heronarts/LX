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
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.MalformedJsonException;

import heronarts.lx.LX;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.output.ArtNetDatagram;
import heronarts.lx.output.ArtSyncDatagram;
import heronarts.lx.output.DDPDatagram;
import heronarts.lx.output.KinetDatagram;
import heronarts.lx.output.LXBufferDatagram;
import heronarts.lx.output.LXDatagram;
import heronarts.lx.output.OPCDatagram;
import heronarts.lx.output.StreamingACNDatagram;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.transform.LXMatrix;
import heronarts.lx.transform.LXVector;

public class JsonFixture extends LXFixture {

  private static final String KEY_LABEL = "label";
  private static final String KEY_MODEL_KEY = "modelKey";
  private static final String KEY_MODEL_KEYS = "modelKeys";

  private static final String KEY_POINTS = "points";
  private static final String KEY_STRIPS = "strips";
  private static final String KEY_ARCS = "arcs";

  private static final String KEY_X = "x";
  private static final String KEY_Y = "y";
  private static final String KEY_Z = "z";
  private static final String KEY_YAW = "yaw";
  private static final String KEY_PITCH = "pitch";
  private static final String KEY_ROLL = "roll";
  private static final String KEY_DIRECTION = "direction";
  private static final String KEY_NORMAL = "normal";

  private static final String KEY_RADIUS = "radius";
  private static final String KEY_DEGREES = "degrees";

  private static final String KEY_ARC_MODE = "mode";
  private static final String VALUE_ARC_MODE_ORIGIN = "origin";
  private static final String VALUE_ARC_MODE_CENTER = "center";

  private static final String KEY_NUM_POINTS = "numPoints";
  private static final String KEY_SPACING = "spacing";

  private static final String KEY_OUTPUT = "output";
  private static final String KEY_OUTPUTS = "outputs";
  private static final String KEY_PROTOCOL = "protocol";
  private static final String KEY_BYTE_ORDER = "byteOrder";
  private static final String KEY_UNIVERSE = "universe";
  private static final String KEY_DATA_OFFSET = "dataOffset";
  private static final String KEY_KINET_PORT = "kinetPort";
  private static final String KEY_CHANNEL = "channel";
  private static final String KEY_START = "start";
  private static final String KEY_NUM = "num";
  private static final String KEY_STRIDE = "stride";
  private static final String KEY_REVERSE = "reverse";

  private static final String KEY_HOST = "host";
  private static final String KEY_PORT = "port";

  private static final String LABEL_PLACEHOLDER = "UNKNOWN";

  private static final Map<String, ProtocolDefinition> protocolMap = new HashMap<String, ProtocolDefinition>();

  enum ProtocolDefinition {
    ARTNET(KEY_UNIVERSE, "artnet", "artdmx"),
    ARTSYNC(null, "artsync"),
    SACN(KEY_UNIVERSE, "sacn", "e131"),
    DDP(KEY_DATA_OFFSET, "ddp"),
    OPC(KEY_CHANNEL, "opc"),
    KINET(KEY_KINET_PORT, "kinet");

    private final String universeKey;

    ProtocolDefinition(String universeKey, String ... keys) {
      this.universeKey = universeKey;
      for (String key : keys) {
        protocolMap.put(key, this);
      }
    }

    public String getUniverseKey() {
      return this.universeKey;
    }

    static ProtocolDefinition get(String key) {
      return protocolMap.get(key);
    }
  }

  // A bit superfluous, but avoiding using the LXBufferDatagram stuff
  // directly as JSON-loading is a separate namespace and want the code
  // to clearly reflect that, as may diverge in the future
  private enum ByteOrderDefinition {

    RGB(LXBufferDatagram.ByteOrder.RGB),
    RBG(LXBufferDatagram.ByteOrder.RBG),
    GBR(LXBufferDatagram.ByteOrder.GBR),
    GRB(LXBufferDatagram.ByteOrder.GRB),
    BRG(LXBufferDatagram.ByteOrder.BRG),
    BGR(LXBufferDatagram.ByteOrder.BGR),

    RGBW(LXBufferDatagram.ByteOrder.RGBW),
    RBGW(LXBufferDatagram.ByteOrder.RBGW),
    GRBW(LXBufferDatagram.ByteOrder.GRBW),
    GBRW(LXBufferDatagram.ByteOrder.GBRW),
    BRGW(LXBufferDatagram.ByteOrder.BRGW),
    BGRW(LXBufferDatagram.ByteOrder.BGRW),

    WRGB(LXBufferDatagram.ByteOrder.WRGB),
    WRBG(LXBufferDatagram.ByteOrder.WRBG),
    WGRB(LXBufferDatagram.ByteOrder.WGRB),
    WGBR(LXBufferDatagram.ByteOrder.WGBR),
    WBRG(LXBufferDatagram.ByteOrder.WBRG),
    WBGR(LXBufferDatagram.ByteOrder.WBGR);

    private final LXBufferDatagram.ByteOrder datagramByteOrder;

    ByteOrderDefinition(LXBufferDatagram.ByteOrder datagramByteOrder) {
      this.datagramByteOrder = datagramByteOrder;
    }

    LXBufferDatagram.ByteOrder getDatagramByteOrder() {
      return this.datagramByteOrder;
    }

    static ByteOrderDefinition get(String order) {
      for (ByteOrderDefinition byteOrder : ByteOrderDefinition.values()) {
        if (order.toLowerCase().equals(byteOrder.name().toLowerCase())) {
          return byteOrder;
        }
      }
      return null;
    }
  }

  private static class ParameterDefinition {

    enum Type {
      STRING,
      INT,
      FLOAT;
    }

    private final String name;
    private final Type type;
    private final String defaultString;
    private final int defaultInt;
    private final float defaultFloat;

    ParameterDefinition(String name, String defaultStr) {
      this.name = name;
      this.type = Type.STRING;
      this.defaultInt = 0;
      this.defaultFloat = 0f;
      this.defaultString = defaultStr;
    }

    ParameterDefinition(String name, int defaultInt) {
      this.name = name;
      this.type = Type.INT;
      this.defaultInt = defaultInt;
      this.defaultFloat = 0f;
      this.defaultString = null;
    }

    ParameterDefinition(String name, float defaultFloat) {
      this.name = name;
      this.type = Type.FLOAT;
      this.defaultInt = 0;
      this.defaultFloat = defaultFloat;
      this.defaultString = null;
    }

  }

  private class OutputDefinition {

    private static final int ALL_POINTS = -1;
    private static final int DEFAULT_PORT = -1;

    private final ProtocolDefinition protocol;
    private final ByteOrderDefinition byteOrder;
    private final String host;
    private final int port;
    private final int universe;
    private final int start;
    private final int num;
    private final int stride;
    private final boolean reverse;

    OutputDefinition(ProtocolDefinition protocol, ByteOrderDefinition byteOrder, String host, int port, int universe, int start, int num, int stride, boolean reverse) {
      this.protocol = protocol;
      this.byteOrder = byteOrder;
      this.host = host;
      this.port = port;
      this.universe = universe;
      this.start = start;
      this.num = num;
      this.stride = stride;
      this.reverse = reverse;
    }

  }

  private class StripDefinition {

    private static final int MAX_POINTS = 65535;

    private final int index;
    private final int numPoints;
    private final float pointSpacing;
    private final LXMatrix transform;
    private final List<OutputDefinition> outputs;
    private final String[] modelKeys;

    private StripDefinition(int numPoints, float pointSpacing, LXVector origin, LXMatrix transform, List<OutputDefinition> outputs, String[] modelKeys) {
      this.index = size;
      this.numPoints = numPoints;
      this.pointSpacing = pointSpacing;
      this.transform = transform;
      this.outputs = outputs;
      this.modelKeys = modelKeys;
      size += numPoints;
    }
  }

  private class ArcDefinition {

    private static final int MAX_POINTS = 65535;

    private final int index;
    private final int numPoints;
    private final float radius;
    private final float degrees;
    private final boolean isCenter;
    private final LXMatrix transform;
    private final List<OutputDefinition> outputs;
    private final String[] modelKeys;

    private ArcDefinition(int numPoints, float radius, float degrees, boolean isCenter, LXMatrix transform, List<OutputDefinition> outputs, String[] modelKeys) {
      this.index = size;
      this.numPoints = numPoints;
      this.radius = radius;
      this.degrees = degrees;
      this.isCenter = isCenter;
      this.transform = transform;
      this.outputs = outputs;
      this.modelKeys = modelKeys;
      size += numPoints;
    }
  }

  private final StringParameter fixtureFile =
    new StringParameter("Fixture File")
    .setDescription("Fixture definition file name");

  public final BoundedParameter scale =
    new BoundedParameter("Scale", 1, 0, 1000)
    .setDescription("Scale the size of the fixture");

  public final BooleanParameter error =
    new BooleanParameter("Error", false)
    .setDescription("Whether there was an error loading this fixture");

  public final StringParameter errorMessage =
    new StringParameter("Error Message", "")
    .setDescription("Message describing the error from loading");

  public final BooleanParameter warning =
    new BooleanParameter("Warning", false)
    .setDescription("Whether there are warnings from the loading of the JSON file");

  public final List<String> warnings = new CopyOnWriteArrayList<String>();

  private String[] modelKeys = { LXModel.Key.MODEL };

  private final List<LXVector> definedPoints = new ArrayList<LXVector>();
  private final List<StripDefinition> definedStrips = new ArrayList<StripDefinition>();
  private final List<ArcDefinition> definedArcs = new ArrayList<ArcDefinition>();
  private final List<OutputDefinition> definedOutputs = new ArrayList<OutputDefinition>();

  private int size = 0;

  private final boolean isSubfixture = false;

  public JsonFixture(LX lx) {
    this(lx, null);
  }

  public JsonFixture(LX lx, String fixtureFile) {
    super(lx, LABEL_PLACEHOLDER);
    addParameter("fixtureFile", this.fixtureFile);
    addGeometryParameter("scale", this.scale);
    if (fixtureFile != null) {
      this.fixtureFile.setValue(fixtureFile);
    }
  }

  @Override
  protected String[] getModelKeys() {
    return this.modelKeys;
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.fixtureFile) {
      loadFixture();
    }
    super.onParameterChanged(p);
  }

  private boolean isLoaded = false;

  public void reload() {
    this.size = 0;
    this.definedPoints.clear();
    this.definedStrips.clear();
    this.definedArcs.clear();
    this.definedOutputs.clear();
    this.warning.setValue(false);
    this.warnings.clear();
    this.error.setValue(false);
    this.errorMessage.setValue("");
    this.isLoaded = false;
    loadFixture();
    regenerate();
  }

  private void loadFixture() {
    if (this.isLoaded) {
      return;
    }
    this.isLoaded = true;

    File fixtureFile = this.lx.getMediaFile(LX.Media.FIXTURES, this.fixtureFile.getString() + ".lxf", false);
    if (!fixtureFile.exists()) {
      setError("Invalid fixture type, could not find file: " + fixtureFile);
      return;
    } else if (!fixtureFile.isFile()) {
      setError("Invalid fixture type, not a normal file: " + fixtureFile);
      return;
    }

    try (FileReader fr = new FileReader(fixtureFile)) {
      JsonObject obj = new Gson().fromJson(fr, JsonObject.class);

      this.modelKeys = loadModelKeys(obj, true, LXModel.Key.MODEL);
      if (this.isSubfixture) {
        loadGeometry(obj);
      } else {
        loadLabel(obj);
      }
      loadParameters(obj);
      loadPoints(obj);
      loadStrips(obj);
      loadArcs(obj);

      // TODO: add ability to import children at some point?
      // loadChildren();

      loadOutputs(this.definedOutputs, obj);

    } catch (JsonParseException jpx) {
      String message = jpx.getLocalizedMessage();
      Throwable cause = jpx.getCause();
      if (cause instanceof MalformedJsonException) {
        message = "Invalid JSON in " + fixtureFile.getName() + ": " + ((MalformedJsonException)cause).getLocalizedMessage();
      }
      setError(jpx, message);
    } catch (Exception x) {
      setError(x, "Exception loading fixture from " + fixtureFile + ": " + x.getLocalizedMessage());
    }
  }

  private void setError(String error) {
    setError(null, error);
  }

  private void setError(Exception x, String error) {
    this.errorMessage.setValue(error);
    this.error.setValue(true);
    LX.error(x, "Fixture " + this.fixtureFile.getString() + ".lxf: " + error);
  }

  private void addWarning(String warning) {
    this.warnings.add(warning);
    if (this.warning.isOn()) {
      this.warning.bang();
    } else {
      this.warning.setValue(true);
    }
    LX.error("Fixture " + this.fixtureFile.getString() + ".lxf: " + warning);
  }

  private void warnDuplicateKeys(JsonObject obj, String key1, String key2) {
    if (obj.has(key1) && obj.has(key2)) {
      addWarning("Should use only one of " + key1 + " or " + key2 + " - " + key1 + " will be ignored.");
    }
  }

  private float loadFloat(JsonObject obj, String key) {
    return loadFloat(obj, key, key + " should be primitive float value");
  }

  private float loadFloat(JsonObject obj, String key, String warning) {
    if (obj.has(key)) {
      JsonElement floatElem = obj.get(key);
      if (floatElem.isJsonPrimitive()) {
        return floatElem.getAsFloat();
      }
      addWarning(warning);
    }
    return 0f;
  }

  private boolean loadBoolean(JsonObject obj, String key, String warning) {
    if (obj.has(key)) {
      JsonElement boolElem = obj.get(key);
      if (boolElem.isJsonPrimitive()) {
        return boolElem.getAsBoolean();
      }
      addWarning(warning);
    }
    return false;
  }

  private int loadInt(JsonObject obj, String key, String warning) {
    if (obj.has(key)) {
      JsonElement intElem = obj.get(key);
      if (intElem.isJsonPrimitive()) {
        return intElem.getAsInt();
      }
      addWarning(warning);
    }
    return 0;
  }

  private LXVector loadVector(JsonObject obj, String warning) {
    if (!obj.has(KEY_X) && !obj.has(KEY_Y) && !obj.has(KEY_Z)) {
      addWarning(warning);
    }
    return new LXVector(
      loadFloat(obj, KEY_X),
      loadFloat(obj, KEY_Y),
      loadFloat(obj, KEY_Z)
    );
  }

  private String loadString(JsonObject obj, String key, String warning) {
    if (obj.has(key)) {
      JsonElement stringElem = obj.get(key);
      if (stringElem.isJsonPrimitive() && stringElem.getAsJsonPrimitive().isString()) {
        return stringElem.getAsString();
      }
      addWarning(warning);
    }
    return null;
  }

  private JsonArray loadArray(JsonObject obj, String key) {
    return loadArray(obj, key, key + " must be a JSON array");
  }

  private JsonArray loadArray(JsonObject obj, String key, String warning) {
    if (obj.has(key)) {
      JsonElement arrayElem = obj.get(key);
      if (arrayElem.isJsonArray()) {
        return arrayElem.getAsJsonArray();
      }
      addWarning(warning);
    }
    return null;
  }

  private JsonObject loadObject(JsonObject obj, String key, String warning) {
    if (obj.has(key)) {
      JsonElement objElem = obj.get(key);
      if (objElem.isJsonObject()) {
        return objElem.getAsJsonObject();
      }
      addWarning(warning);
    }
    return null;
  }

  private void loadGeometry(JsonObject obj) {
    if (obj.has(KEY_X)) {
      this.x.setValue(loadFloat(obj, KEY_X));
    }
    if (obj.has(KEY_Y)) {
      this.x.setValue(loadFloat(obj, KEY_Y));
    }
    if (obj.has(KEY_Z)) {
      this.x.setValue(loadFloat(obj, KEY_Z));
    }
    if (obj.has(KEY_YAW)) {
      this.yaw.setValue(loadFloat(obj, KEY_YAW));
    }
    if (obj.has(KEY_PITCH)) {
      this.pitch.setValue(loadFloat(obj, KEY_PITCH));
    }
    if (obj.has(KEY_ROLL)) {
      this.roll.setValue(loadFloat(obj, KEY_ROLL));
    }
  }

  private void loadLabel(JsonObject obj) {
    // Don't reload this if the user has renamed it
    if (!this.label.getString().equals(LABEL_PLACEHOLDER)) {
      return;
    }
    String validLabel = this.fixtureFile.getString();
    String testLabel = loadString(obj, KEY_LABEL, KEY_LABEL + " should contain a string");
    if (testLabel != null) {
      testLabel = testLabel.trim();
      if (testLabel.isEmpty()) {
        addWarning(KEY_LABEL + " should contain a non-empty string");
      } else {
        validLabel = testLabel;
      }
    }
    this.label.setValue(validLabel);
  }

  private String[] loadModelKeys(JsonObject obj, boolean required, String ... defaultKeys) {
    warnDuplicateKeys(obj, KEY_MODEL_KEY, KEY_MODEL_KEYS);
    if (obj.has(KEY_MODEL_KEYS)) {
      JsonArray modelKeyArr = loadArray(obj, KEY_MODEL_KEYS);
      List<String> validModelKeys = new ArrayList<String>();
      for (JsonElement modelKeyElem : modelKeyArr) {
        if (!modelKeyElem.isJsonPrimitive() || !modelKeyElem.getAsJsonPrimitive().isString()) {
          addWarning(KEY_MODEL_KEYS + " may only contain strings");
        } else {
          String key = modelKeyElem.getAsString().trim();
          if (key.isEmpty()) {
            addWarning(KEY_MODEL_KEYS + " should not contain empty string values");
          } else {
            validModelKeys.add(key);
          }
        }
      }
      if (validModelKeys.isEmpty()) {
        addWarning(KEY_MODEL_KEYS + " must contain at least one non-empty string value");
      } else {
        return validModelKeys.toArray(new String[0]);
      }
    } else if (obj.has(KEY_MODEL_KEY)) {
      String key = loadString(obj, KEY_MODEL_KEY, KEY_MODEL_KEY + " should contain a single string value");
      if (key != null) {
        key = key.trim();
        if (key.isEmpty()) {
          addWarning(KEY_MODEL_KEY + " must contain a non-empty string value");
        } else {
          return new String[] { key };
        }
      }
    } else if (required) {
      LX.warning("Fixture definition should specify one of " + KEY_MODEL_KEY + " or " + KEY_MODEL_KEYS);
    }
    return defaultKeys;
  }

  private void loadParameters(JsonObject obj) {
    // TODO(mcslee): implement this
  }

  private void loadPoints(JsonObject obj) {
    JsonArray pointsArr = loadArray(obj, KEY_POINTS);
    if (pointsArr == null) {
      return;
    }
    for (JsonElement pointElem : pointsArr) {
      if (pointElem.isJsonObject()) {
        this.definedPoints.add(loadVector(pointElem.getAsJsonObject(), "Point should specify at least one x/z/y value"));
        ++this.size;
      } else if (!pointElem.isJsonNull()) {
        addWarning(KEY_POINTS + " should only contain point elements in JSON object format, found invalid: " + pointElem);
      }
    }
  }

  private void loadStrips(JsonObject obj) {
    JsonArray stripsArr = loadArray(obj, KEY_STRIPS);
    if (stripsArr == null) {
      return;
    }
    for (JsonElement stripElem : stripsArr) {
      if (stripElem.isJsonObject()) {
        loadStrip(stripElem.getAsJsonObject());
      } else if (!stripElem.isJsonNull()) {
        addWarning(KEY_STRIPS + " should only contain strip elements in JSON object format, found invalid: " + stripElem);
      }
    }
  }

  private void loadStrip(JsonObject stripObj) {
    if (!stripObj.has(KEY_NUM_POINTS)) {
      addWarning("Strip must specify " + KEY_NUM_POINTS);
      return;
    }
    int numPoints = loadInt(stripObj, KEY_NUM_POINTS, "Strip must specify a positive integer for " + KEY_NUM_POINTS);
    if (numPoints <= 0) {
      addWarning("Strip must specify positive integer value for " + KEY_NUM_POINTS);
      return;
    }
    if (numPoints > StripDefinition.MAX_POINTS) {
      addWarning("Single strip may not define more than " + StripDefinition.MAX_POINTS + " points");
      return;
    }

    String[] modelKeys = loadModelKeys(stripObj, false, LXModel.Key.STRIP);

    float spacing = 1f;
    LXMatrix transform = new LXMatrix();

    // Set the strip origin
    LXVector origin = loadVector(stripObj, "Strip should specify at least one x/y/z value");
    transform.translate(origin.x, origin.y, origin.z);

    // Load the strip direction, one of two ways
    if (stripObj.has(KEY_DIRECTION)) {
      if (stripObj.has(KEY_YAW) || stripObj.has(KEY_PITCH) || stripObj.has(KEY_ROLL)) {
        addWarning("Strip object should not specify both " + KEY_DIRECTION + " and yaw/pitch/roll, only using " + KEY_DIRECTION);
      }
      JsonObject directionObj = loadObject(stripObj, KEY_DIRECTION, "Strip direction should be a vector object");
      if (directionObj != null) {
        LXVector direction = loadVector(directionObj, "Strip direction should specify at least one x/y/z value");
        if (direction.isZero()) {
          addWarning("Strip direction vector should not be all 0");
        } else {
          spacing = direction.mag();
          transform.rotateY((float) Math.atan2(-direction.z, direction.x)); // yaw
          transform.rotateZ((float) Math.asin(direction.y / spacing)); // roll
        }
      }
    } else {
      transform.rotateY((float) Math.toRadians(loadFloat(stripObj, KEY_YAW)));
      transform.rotateX((float) Math.toRadians(loadFloat(stripObj, KEY_PITCH)));
      transform.rotateZ((float) Math.toRadians(loadFloat(stripObj, KEY_ROLL)));
    }

    if (stripObj.has(KEY_SPACING)) {
      float testSpacing = loadFloat(stripObj, KEY_SPACING, "Strip must specify a positive " + KEY_SPACING);
      if (testSpacing > 0) {
        spacing = testSpacing;
      } else {
        addWarning("Strip may not specify a negative spacing");
      }
    }

    List<OutputDefinition> outputs = new ArrayList<OutputDefinition>();
    loadOutputs(outputs, stripObj);

    this.definedStrips.add(new StripDefinition(numPoints, spacing, origin, transform, outputs, modelKeys));
  }

  private void loadArcs(JsonObject obj) {
    JsonArray arcsArr = loadArray(obj, KEY_ARCS);
    if (arcsArr == null) {
      return;
    }
    for (JsonElement arcElem : arcsArr) {
      if (arcElem.isJsonObject()) {
        loadArc(arcElem.getAsJsonObject());
      } else if (!arcElem.isJsonNull()) {
        addWarning(KEY_ARCS + " should only contain arc elements in JSON object format, found invalid: " + arcElem);
      }
    }
  }

  private void loadArc(JsonObject arcObj) {
    if (!arcObj.has(KEY_NUM_POINTS)) {
      addWarning("Arc must specify " + KEY_NUM_POINTS + ", key was not found");
      return;
    }
    int numPoints = loadInt(arcObj, KEY_NUM_POINTS, "Arc must specify a positive integer for " + KEY_NUM_POINTS);
    if (numPoints <= 0) {
      addWarning("Arc must specify positive integer value for " + KEY_NUM_POINTS);
      return;
    }
    if (numPoints > ArcDefinition.MAX_POINTS) {
      addWarning("Single arc may not define more than " + ArcDefinition.MAX_POINTS + " points");
      return;
    }

    String[] modelKeys = loadModelKeys(arcObj, false, LXModel.Key.STRIP, LXModel.Key.ARC);

    LXMatrix transform = new LXMatrix();

    // Load position
    LXVector position = loadVector(arcObj, "Arc should specify at least one x/y/z value");
    transform.translate(position.x, position.y, position.z);
    boolean isCenter = false;

    float radius = loadFloat(arcObj, KEY_RADIUS, "Arc must specify radius");
    if (radius < 0) {
      addWarning("Arc must specify positive value for " + KEY_RADIUS);
      return;
    }

    float degrees = loadFloat(arcObj, KEY_DEGREES, "Arc must specify number of degrees to cover");

    if (arcObj.has(KEY_ARC_MODE)) {
      String arcMode = loadString(arcObj, KEY_ARC_MODE, "Arc " + KEY_ARC_MODE + " must be a string");
      if (VALUE_ARC_MODE_CENTER.equals(arcMode)) {
        isCenter = true;
      } else if (VALUE_ARC_MODE_ORIGIN.equals(arcMode)) {
        // All good
      } else if (arcMode != null) {
        addWarning("Arc " + KEY_ARC_MODE + " must be one of " + VALUE_ARC_MODE_CENTER + " or " + VALUE_ARC_MODE_ORIGIN + " - invalid value " + arcMode);
      }
    }

    // Load the strip direction, one of two ways
    if (arcObj.has(KEY_NORMAL)) {
      if (arcObj.has(KEY_DIRECTION) || arcObj.has(KEY_YAW) || arcObj.has(KEY_PITCH)) {
        addWarning("Arc object should not specify both " + KEY_NORMAL + " and direction/yaw/pitch, only using " + KEY_NORMAL);
      }
      JsonObject normalObj = loadObject(arcObj, KEY_NORMAL, "Arc normal should be a vector object");
      if (normalObj != null) {
        LXVector normal = loadVector(normalObj, "Arc normal should specify at least one x/y/z value");
        if (normal.isZero()) {
          addWarning("Arc normal vector should not be all 0");
        } else {
          transform.rotateY((float) Math.atan2(normal.x, normal.z)); // yaw
          transform.rotateX((float) Math.asin(normal.y / normal.mag())); // pitch
          transform.rotateZ((float) Math.toRadians(loadFloat(arcObj, KEY_ROLL)));
        }
      }
    } else if (arcObj.has(KEY_DIRECTION)) {
      if (arcObj.has(KEY_YAW) || arcObj.has(KEY_ROLL) || arcObj.has(KEY_NORMAL)) {
        addWarning("Arc object should not specify both " + KEY_DIRECTION + " and yaw/roll/normal, only using " + KEY_DIRECTION);
      }
      JsonObject directionObj = loadObject(arcObj, KEY_DIRECTION, "Arc direction should be a vector object");
      if (directionObj != null) {
        LXVector direction = loadVector(directionObj, "Arc direction should specify at least one x/y/z value");
        if (direction.isZero()) {
          addWarning("Arc direction vector should not be all 0");
        } else {
          transform.rotateY((float) Math.atan2(-direction.z, direction.x)); // yaw
          transform.rotateX((float) Math.toRadians(loadFloat(arcObj, KEY_PITCH))); // pitch
          transform.rotateZ((float) Math.asin(direction.y / direction.mag())); // roll
        }
      }
    } else {
      transform.rotateY((float) Math.toRadians(loadFloat(arcObj, KEY_YAW)));
      transform.rotateX((float) Math.toRadians(loadFloat(arcObj, KEY_PITCH)));
      transform.rotateZ((float) Math.toRadians(loadFloat(arcObj, KEY_ROLL)));
    }

    List<OutputDefinition> outputs = new ArrayList<OutputDefinition>();
    loadOutputs(outputs, arcObj);

    this.definedArcs.add(new ArcDefinition(numPoints, radius, degrees, isCenter, transform, outputs, modelKeys));
  }

  private void loadOutputs(List<OutputDefinition> outputs, JsonObject obj) {
    if (obj.has(KEY_OUTPUT) && obj.has(KEY_OUTPUTS)) {
      addWarning("Should not have both " + KEY_OUTPUT + " and " + KEY_OUTPUTS);
    }
    JsonObject outputObj = loadObject(obj, KEY_OUTPUT, KEY_OUTPUT + " must be an output object");
    if (outputObj != null) {
      loadOutput(outputs, outputObj);
    }
    JsonArray outputsArr = loadArray(obj, KEY_OUTPUTS, KEY_OUTPUTS + " must be an array of outputs");
    if (outputsArr != null) {
      for (JsonElement outputElem : outputsArr) {
        if (outputElem.isJsonObject()) {
          loadOutput(outputs, outputElem.getAsJsonObject());
        } else if (!outputElem.isJsonNull()) {
          addWarning(KEY_OUTPUTS + " should only contain arc elements in JSON object format, found invalid: " + outputElem);
        }
      }
    }
  }

  private void loadOutput(List<OutputDefinition> outputs, JsonObject outputObj) {
    ProtocolDefinition protocol = ProtocolDefinition.get(loadString(outputObj, KEY_PROTOCOL, "Output must specify a valid " + KEY_PROTOCOL));
    if (protocol == null) {
      addWarning("Output definition must define a valid protocol");
      return;
    }
    ByteOrderDefinition byteOrder = ByteOrderDefinition.RGB;
    String byteOrderStr = loadString(outputObj, KEY_BYTE_ORDER, "Output must specify a valid string " + KEY_BYTE_ORDER);
    if (byteOrderStr != null) {
      if (byteOrderStr.isEmpty()) {
        addWarning("Output must specify non-empty string value for " + KEY_BYTE_ORDER);
      } else {
        ByteOrderDefinition definedByteOrder = ByteOrderDefinition.get(byteOrderStr);
        if (definedByteOrder == null) {
          addWarning("Unrecognized byte order type: " + byteOrderStr);
        } else {
          byteOrder = definedByteOrder;
        }
      }
    }

    String host = loadString(outputObj, KEY_HOST, "Output must specify a valid host");
    if (host.isEmpty()) {
      addWarning("Output must define a valid, non-empty host");
      return;
    }
    int port = OutputDefinition.DEFAULT_PORT;
    if (outputObj.has(KEY_PORT)) {
      port = loadInt(outputObj, KEY_PORT, "Output must specify a valid host");
      if (port <= 0) {
        addWarning("Output port number must be positive: " + port);
        return;
      }
    }
    String universeKey = protocol.getUniverseKey();
    int universe = loadInt(outputObj, universeKey, "Output " + universeKey + " must be a valid integer");
    if (universe < 0) {
      addWarning("Output " + universeKey + " may not be negative");
      return;
    }

    int start = loadInt(outputObj, KEY_START, "Output " + KEY_START + " must be a valid integer");
    if (start < 0) {
      addWarning("Output " + KEY_START + " may not be negative");
      return;
    }
    int num = OutputDefinition.ALL_POINTS;
    if (outputObj.has(KEY_NUM)) {
      num = loadInt(outputObj, KEY_NUM, "Output " + KEY_NUM + " must be a valid integer");
      if (num < 0) {
        addWarning("Output " + KEY_NUM + " may not be negative");
        return;
      }
    }
    int stride = 1;
    if (outputObj.has(KEY_STRIDE)) {
      stride = loadInt(outputObj, KEY_STRIDE, "Output " + KEY_STRIDE + " must be a valid integer");
      if (stride <= 0) {
        addWarning("Output stride must be a positive value, use 'reverse: true' to invert pixel order");
        return;
      }
    }
    boolean reverse = loadBoolean(outputObj, KEY_REVERSE, "Output " + KEY_REVERSE + " must be a valid boolean");
    outputs.add(new OutputDefinition(protocol, byteOrder, host, port, universe, start, num, stride, reverse));
  }

  @Override
  protected void computePointGeometry(LXMatrix matrix, List<LXPoint> points) {
    matrix.scale(this.scale.getValuef());

    int i = 0;
    for (LXVector vector : this.definedPoints) {
      points.get(i++).set(matrix, vector);
    }

    LXMatrix transform = new LXMatrix();

    for (StripDefinition strip : this.definedStrips) {
      transform.set(matrix);
      transform.multiply(strip.transform);
      for (int s = 0; s < strip.numPoints; ++s) {
        points.get(i++).set(transform);
        transform.translateX(strip.pointSpacing);
      }
    }

    for (ArcDefinition arc :  this.definedArcs) {
      transform.set(matrix);
      transform.multiply(arc.transform);
      float rotation = (float) Math.toRadians(arc.degrees / (arc.numPoints - 1));
      if (arc.isCenter) {
        for (int a = 0; a < arc.numPoints; ++a) {
          transform.translateY(-arc.radius);
          points.get(i++).set(transform);
          transform.translateY(arc.radius);
          transform.rotateZ(rotation);
        }
      } else {
        for (int a = 0; a < arc.numPoints; ++a) {
          points.get(i++).set(transform);
          transform.translateY(arc.radius);
          transform.rotateZ(rotation);
          transform.translateY(-arc.radius);
        }
      }
    }

  }

  @Override
  protected void buildDatagrams() {
    // Add strip-specific outputs
    for (StripDefinition strip : this.definedStrips) {
      for (OutputDefinition output : strip.outputs) {
        buildDatagram(output, strip.index, strip.numPoints);
      }
    }
    // Add strip-specific outputs
    for (ArcDefinition arc : this.definedArcs) {
      for (OutputDefinition output : arc.outputs) {
        buildDatagram(output, arc.index, arc.numPoints);
      }
    }
    // Add top level outputs last
    for (OutputDefinition output : this.definedOutputs) {
      buildDatagram(output, 0, this.size);
    }
  }

  private void buildDatagram(OutputDefinition output, int fixtureOffset, int fixtureSize) {
    if (output.protocol == ProtocolDefinition.ARTSYNC) {
      buildArtSyncDatagram(output);
      return;
    }

    if (output.start < 0 || (output.start >= fixtureSize)) {
      addWarning("Output specifies invalid start position: " + output.start + " should be between [0, " + (fixtureSize-1) + "]");
      return;
    }

    int start = output.start + fixtureOffset;
    int num = output.num;
    if (num == OutputDefinition.ALL_POINTS) {
      num = fixtureSize;
    }
    int stride = output.stride;
    if (stride * (num-1) >= fixtureSize) {
      addWarning("Output specifies excessive size beyond fixture limits: start=" + output.start + " num=" + num + " stride=" + stride);
      return;
    }

    // Want those backwards? No problem!
    if (output.reverse) {
      start = start + stride * (num-1);
      stride = -stride;
    }
    LXBufferDatagram.ByteOrder byteOrder = output.byteOrder.getDatagramByteOrder();

    int[] indexBuffer = toDynamicIndexBuffer(start, num, stride);
    int dataLength = indexBuffer.length * byteOrder.getNumBytes();
    LXBufferDatagram bufferDatagram = null;

    if (dataLength >= (1 << 16)) {
      addWarning("Output packet would have length > 16-bits (" + dataLength + ") - not possible");
      return;
    }

    switch (output.protocol) {
    case ARTNET:
      if (dataLength > ArtNetDatagram.MAX_DATA_LENGTH) {
        addWarning("Art-Net packet using noncompliant size: " + dataLength +">" + ArtNetDatagram.MAX_DATA_LENGTH);
      }
      bufferDatagram = new ArtNetDatagram(indexBuffer, output.universe, byteOrder);
      break;
    case SACN:
      if (dataLength > StreamingACNDatagram.MAX_DATA_LENGTH) {
        addWarning("Streaming ACN / E1.31 packet using noncompliant size: " + dataLength + ">" + StreamingACNDatagram.MAX_DATA_LENGTH);
      }
      bufferDatagram = new StreamingACNDatagram(indexBuffer, output.universe, byteOrder);
      break;
    case DDP:
      if (byteOrder != LXBufferDatagram.ByteOrder.RGB) {
        addWarning("DDP packets do not support non-RGB byte order, using RGB");
      }
      bufferDatagram = new DDPDatagram(indexBuffer, output.universe);
      break;
    case KINET:
      if (byteOrder != LXBufferDatagram.ByteOrder.RGB) {
        addWarning("KiNET packets do not support non-RGB byte order, using RGB");
      }
      if (dataLength > KinetDatagram.MAX_DATA_LENGTH) {
        addWarning("KiNET packets cannot support more than 512 bytes payload, ignoring this output");
        return;
      }
      bufferDatagram = new KinetDatagram(indexBuffer, output.universe);
      break;
    case OPC:
      bufferDatagram = new OPCDatagram(indexBuffer, (byte) output.universe, byteOrder);
      break;
    case ARTSYNC:
    default:
      // Already handled above
      break;
    }
    if (bufferDatagram != null) {
      if (output.port != OutputDefinition.DEFAULT_PORT) {
        bufferDatagram.setPort(output.port);
      }
      try {
        bufferDatagram.setAddress(output.host);
        addDatagram(bufferDatagram);
      } catch (UnknownHostException uhx) {
        addWarning("Uknown host specified for output: " + uhx.getLocalizedMessage());
      }
    }
  }

  private void buildArtSyncDatagram(OutputDefinition output) {
    LXDatagram artSync = new ArtSyncDatagram();
    if (output.port != OutputDefinition.DEFAULT_PORT) {
      artSync.setPort(output.port);
    }
    try {
      artSync.setAddress(output.host);
      addDatagram(artSync);
    } catch (UnknownHostException uhx) {
      addWarning("Uknown host specified for ArtSync packet: " + uhx.getLocalizedMessage());
    }
  }

  @Override
  protected Submodel[] toSubmodels() {
    List<Submodel> submodels = new ArrayList<Submodel>();
    for (StripDefinition strip : this.definedStrips) {
      submodels.add(new Submodel(strip.index, strip.numPoints, strip.modelKeys));
    }
    for (ArcDefinition arc : this.definedArcs) {
      submodels.add(new Submodel(arc.index, arc.numPoints, arc.modelKeys));
    }
    return submodels.toArray(new Submodel[0]);
  }

  @Override
  protected int size() {
    return this.size;
  }

}
