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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.MalformedJsonException;

import heronarts.lx.LX;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.output.ArtNetDatagram;
import heronarts.lx.output.ArtSyncDatagram;
import heronarts.lx.output.DDPDatagram;
import heronarts.lx.output.KinetDatagram;
import heronarts.lx.output.LXBufferOutput;
import heronarts.lx.output.LXDatagram;
import heronarts.lx.output.LXOutput;
import heronarts.lx.output.OPCDatagram;
import heronarts.lx.output.OPCSocket;
import heronarts.lx.output.StreamingACNDatagram;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXListenableParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.transform.LXMatrix;
import heronarts.lx.transform.LXVector;
import heronarts.lx.utils.LXUtils;

public class JsonFixture extends LXFixture {

  // Label
  private static final String KEY_LABEL = "label";

  // Model keys
  private static final String KEY_MODEL_KEY = "modelKey";
  private static final String KEY_MODEL_KEYS = "modelKeys";

  // Geometry
  private static final String KEY_X = "x";
  private static final String KEY_Y = "y";
  private static final String KEY_Z = "z";
  private static final String KEY_YAW = "yaw";
  private static final String KEY_PITCH = "pitch";
  private static final String KEY_ROLL = "roll";
  private static final String KEY_SCALE = "scale";
  private static final String KEY_DIRECTION = "direction";
  private static final String KEY_NORMAL = "normal";

  // Points
  private static final String KEY_POINTS = "points";

  // Strips
  private static final String KEY_STRIPS = "strips";
  private static final String KEY_NUM_POINTS = "numPoints";
  private static final String KEY_SPACING = "spacing";

  // Arcs
  private static final String KEY_ARCS = "arcs";
  private static final String KEY_RADIUS = "radius";
  private static final String KEY_DEGREES = "degrees";
  private static final String KEY_ARC_MODE = "mode";
  private static final String VALUE_ARC_MODE_ORIGIN = "origin";
  private static final String VALUE_ARC_MODE_CENTER = "center";

  // Children
  private static final String KEY_CHILDREN = "children";
  private static final String KEY_TYPE = "type";

  // Parameters
  private static final String KEY_PARAMETERS = "parameters";
  private static final String KEY_PARAMETER_LABEL = "label";
  private static final String KEY_PARAMETER_DESCRIPTION = "description";
  private static final String KEY_PARAMETER_TYPE = "type";
  private static final String KEY_PARAMETER_DEFAULT = "default";
  private static final String KEY_PARAMETER_MIN = "min";
  private static final String KEY_PARAMETER_MAX = "max";

  // Outputs
  private static final String KEY_OUTPUT = "output";
  private static final String KEY_OUTPUTS = "outputs";
  private static final String KEY_PROTOCOL = "protocol";
  private static final String KEY_TRANSPORT = "transport";
  private static final String KEY_HOST = "host";
  private static final String KEY_PORT = "port";
  private static final String KEY_BYTE_ORDER = "byteOrder";
  private static final String KEY_UNIVERSE = "universe";
  private static final String KEY_DDP_DATA_OFFSET = "dataOffset";
  private static final String KEY_KINET_PORT = "kinetPort";
  private static final String KEY_OPC_CHANNEL = "channel";
  private static final String KEY_START = "start";
  private static final String KEY_NUM = "num";
  private static final String KEY_STRIDE = "stride";
  private static final String KEY_REVERSE = "reverse";
  private static final String KEY_SEGMENTS = "segments";

  // Metadata
  private static final String KEY_META = "meta";

  private static final String LABEL_PLACEHOLDER = "UNKNOWN";

  private enum ProtocolDefinition {
    ARTNET(KEY_UNIVERSE, "artnet", "artdmx"),
    ARTSYNC(null, "artsync"),
    SACN(KEY_UNIVERSE, "sacn", "e131"),
    DDP(KEY_DDP_DATA_OFFSET, "ddp"),
    OPC(KEY_OPC_CHANNEL, "opc"),
    KINET(KEY_KINET_PORT, "kinet");

    private final String universeKey;
    private final String[] protocolKeys;

    private ProtocolDefinition(String universeKey, String ... protocolKeys) {
      this.universeKey = universeKey;
      this.protocolKeys = protocolKeys;
    }

    public String getUniverseKey() {
      return this.universeKey;
    }

    public boolean requiresExplicitPort() {
      return (this == OPC);
    }

    private static ProtocolDefinition get(String key) {
      for (ProtocolDefinition protocol : values()) {
        for (String protocolKey : protocol.protocolKeys) {
          if (protocolKey.equals(key)) {
            return protocol;
          }
        }
      }
      return null;
    }
  }


  private enum TransportDefinition {
    UDP("udp"),
    TCP("tcp");

    private final String transportKey;

    private TransportDefinition(String transportKey) {
      this.transportKey = transportKey;
    }

    private static TransportDefinition get(String key) {
      for (TransportDefinition protocol : values()) {
        if (protocol.transportKey.equals(key)) {
          return protocol;
        }
      }
      return null;
    }

  }

  // A bit superfluous, but avoiding using the LXBufferOutput stuff
  // directly as JSON-loading is a separate namespace and want the code
  // to clearly reflect that, as may diverge in the future
  private enum ByteOrderDefinition {

    RGB(LXBufferOutput.ByteOrder.RGB),
    RBG(LXBufferOutput.ByteOrder.RBG),
    GBR(LXBufferOutput.ByteOrder.GBR),
    GRB(LXBufferOutput.ByteOrder.GRB),
    BRG(LXBufferOutput.ByteOrder.BRG),
    BGR(LXBufferOutput.ByteOrder.BGR),

    RGBW(LXBufferOutput.ByteOrder.RGBW),
    RBGW(LXBufferOutput.ByteOrder.RBGW),
    GRBW(LXBufferOutput.ByteOrder.GRBW),
    GBRW(LXBufferOutput.ByteOrder.GBRW),
    BRGW(LXBufferOutput.ByteOrder.BRGW),
    BGRW(LXBufferOutput.ByteOrder.BGRW),

    WRGB(LXBufferOutput.ByteOrder.WRGB),
    WRBG(LXBufferOutput.ByteOrder.WRBG),
    WGRB(LXBufferOutput.ByteOrder.WGRB),
    WGBR(LXBufferOutput.ByteOrder.WGBR),
    WBRG(LXBufferOutput.ByteOrder.WBRG),
    WBGR(LXBufferOutput.ByteOrder.WBGR);

    private final LXBufferOutput.ByteOrder byteOrder;

    private ByteOrderDefinition(LXBufferOutput.ByteOrder byteOrder) {
      this.byteOrder = byteOrder;
    }

    private LXBufferOutput.ByteOrder getByteOrder() {
      return this.byteOrder;
    }

    private static ByteOrderDefinition get(String order) {
      for (ByteOrderDefinition byteOrder : ByteOrderDefinition.values()) {
        if (order.toLowerCase().equals(byteOrder.name().toLowerCase())) {
          return byteOrder;
        }
      }
      return null;
    }
  }

  public enum ParameterType {
    STRING,
    INT,
    FLOAT,
    BOOLEAN;

    private static ParameterType get(String str) {
      for (ParameterType type : values()) {
        if (type.name().toLowerCase().equals(str.toLowerCase())) {
          return type;
        }
      }
      return null;
    }
  }

  public class ParameterDefinition implements LXParameterListener {

    public final String name;
    public final String label;
    public final String description;
    public final ParameterType type;
    public final LXListenableParameter parameter;
    public final DiscreteParameter intParameter;
    public final BoundedParameter floatParameter;
    public final StringParameter stringParameter;
    public final BooleanParameter booleanParameter;

    private boolean isReferenced = false;

    private ParameterDefinition(String name, String label, String description, ParameterType type, LXListenableParameter parameter) {
      this.name = name;
      this.label = label;
      this.description = description;
      this.type = type;
      this.parameter = parameter;
      if (description != null) {
        parameter.setDescription(description);
      }
      switch (type) {
      case STRING:
        this.stringParameter = (StringParameter) parameter;
        this.intParameter = null;
        this.floatParameter = null;
        this.booleanParameter = null;
        break;
      case INT:
        this.stringParameter = null;
        this.intParameter = (DiscreteParameter) parameter;
        this.floatParameter = null;
        this.booleanParameter = null;
        break;
      case FLOAT:
        this.stringParameter = null;
        this.intParameter = null;
        this.floatParameter = (BoundedParameter) parameter;
        this.booleanParameter = null;
        break;
      case BOOLEAN:
        this.stringParameter = null;
        this.intParameter = null;
        this.floatParameter = null;
        this.booleanParameter = (BooleanParameter) parameter;;
        break;
      default:
        throw new IllegalStateException("Unknown ParameterType: " + type);
      }
      parameter.addListener(this);
    }

    private ParameterDefinition(String name, String label, String description, String defaultStr) {
      this(name, label, description, ParameterType.STRING, new StringParameter(name, defaultStr));
    }

    private ParameterDefinition(String name, String label, String description, int defaultInt, int minInt, int maxInt) {
      this(name, label, description, ParameterType.INT, new DiscreteParameter(name, defaultInt, minInt, maxInt + 1));
    }

    private ParameterDefinition(String name, String label, String description, float defaultFloat) {
      this(name, label, description, ParameterType.FLOAT, new BoundedParameter(name, defaultFloat, Float.MIN_VALUE, Float.MAX_VALUE));
    }

    private ParameterDefinition(String name, String label, String description, boolean defaultBoolean) {
      this(name, label, description, ParameterType.BOOLEAN, new BooleanParameter(name, defaultBoolean));
    }

    private void dispose() {
      this.parameter.removeListener(this);
      this.parameter.dispose();
    }

    @Override
    public void onParameterChanged(LXParameter p) {
      if (this.isReferenced) {
        reload(false);
      }
    }

  }

  private class OutputDefinition {

    private static final int ALL_POINTS = -1;
    private static final int DEFAULT_PORT = -1;

    private final ProtocolDefinition protocol;
    private final TransportDefinition transport;
    private final ByteOrderDefinition byteOrder;
    private final InetAddress address;
    private final int port;
    private final int universe;
    private final List<SegmentDefinition> segments;

    private OutputDefinition(ProtocolDefinition protocol, TransportDefinition transport, ByteOrderDefinition byteOrder, InetAddress address, int port, int universe, List<SegmentDefinition> segments) {
      this.protocol = protocol;
      this.transport = transport;
      this.byteOrder = byteOrder;
      this.address = address;
      this.port = port;
      this.universe = universe;
      this.segments = segments;
    }

  }

  private class SegmentDefinition {
    private final int start;
    private final int num;
    private final int stride;
    private final boolean reverse;
    private final ByteOrderDefinition dynamicByteOrder;

    private SegmentDefinition(int start, int num, int stride, boolean reverse, ByteOrderDefinition dynamicByteOrder) {
      this.start = start;
      this.num = num;
      this.stride = stride;
      this.reverse = reverse;
      this.dynamicByteOrder = dynamicByteOrder;
    }
  }

  private class StripDefinition {

    private static final int MAX_POINTS = 65535;

    private final int index;
    private final int numPoints;
    private final float pointSpacing;
    private final LXMatrix transform;
    private final List<OutputDefinition> outputs;
    private final Map<String, String> metaData;
    private final String[] modelKeys;

    private StripDefinition(int numPoints, float pointSpacing, LXVector origin, LXMatrix transform, List<OutputDefinition> outputs, Map<String, String> metaData, String[] modelKeys) {
      this.index = size;
      this.numPoints = numPoints;
      this.pointSpacing = pointSpacing;
      this.transform = transform;
      this.outputs = outputs;
      this.metaData = metaData;
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
    private final Map<String, String> metaData;
    private final String[] modelKeys;

    private ArcDefinition(int numPoints, float radius, float degrees, boolean isCenter, LXMatrix transform, List<OutputDefinition> outputs, Map<String, String> metaData, String[] modelKeys) {
      this.index = size;
      this.numPoints = numPoints;
      this.radius = radius;
      this.degrees = degrees;
      this.isCenter = isCenter;
      this.transform = transform;
      this.outputs = outputs;
      this.metaData = metaData;
      this.modelKeys = modelKeys;
      size += numPoints;
    }
  }

  private final StringParameter fixtureType =
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

  public final MutableParameter parametersReloaded =
    (MutableParameter) new MutableParameter("Reload", 0)
    .setDescription("Monitor for when fixture parameters are reloaded");

  public final List<String> warnings = new CopyOnWriteArrayList<String>();

  private String[] modelKeys = { LXModel.Key.MODEL };

  private final List<LXVector> definedPoints = new ArrayList<LXVector>();
  private final List<StripDefinition> definedStrips = new ArrayList<StripDefinition>();
  private final List<ArcDefinition> definedArcs = new ArrayList<ArcDefinition>();
  private final List<OutputDefinition> definedOutputs = new ArrayList<OutputDefinition>();
  private final LinkedHashMap<String, ParameterDefinition> definedParameters = new LinkedHashMap<String, ParameterDefinition>();
  private final LinkedHashMap<String, ParameterDefinition> reloadParameterValues = new LinkedHashMap<String, ParameterDefinition>();

  private final Map<String, String> metaData = new HashMap<String, String>();

  private int size = 0;

  private final JsonFixture jsonParameterContext;
  private final boolean isJsonSubfixture;
  private JsonObject jsonParameterValues = new JsonObject();

  public JsonFixture(LX lx) {
    this(lx, null);
  }

  public JsonFixture(LX lx, String fixtureType) {
    super(lx, LABEL_PLACEHOLDER);
    this.isJsonSubfixture = false;
    this.jsonParameterContext = this;
    addParameter("fixtureType", this.fixtureType);
    addGeometryParameter("scale", this.scale);
    if (fixtureType != null) {
      this.fixtureType.setValue(fixtureType);
    }
  }

  private JsonFixture(LX lx, JsonFixture parentFixture, JsonObject subFixture, String fixtureType) {
    super(lx, LABEL_PLACEHOLDER);
    this.jsonParameterContext = parentFixture;
    this.jsonParameterValues = subFixture;
    this.isJsonSubfixture = true;
    addParameter("fixtureType", this.fixtureType);
    addGeometryParameter("scale", this.scale);
    this.fixtureType.setValue(fixtureType);
  }

  @Override
  protected String[] getModelKeys() {
    return this.modelKeys;
  }

  @Override
  protected Map<String, String> getMetaData() {
    return this.metaData;
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.fixtureType) {
      loadFixture(true);
    } else if (p == this.enabled) {
      // JSON fixture enabled cascades down children...
      for (LXFixture child : this.children) {
        child.enabled.setValue(this.enabled.isOn());
      }
    }
    super.onParameterChanged(p);
  }

  private void addJsonParameter(ParameterDefinition parameter) {
    if (this.definedParameters.containsKey(parameter.name)) {
      addWarning("Cannot define two parameters of same name: " + parameter.name);
      return;
    }
    this.definedParameters.put(parameter.name, parameter);
  }

  public Collection<ParameterDefinition> getJsonParameters() {
    return Collections.unmodifiableCollection(this.definedParameters.values());
  }

  private void removeJsonParameters() {
    // We're done with these...
    for (ParameterDefinition parameter : this.definedParameters.values()) {
      parameter.dispose();
    }
    // Clear them!
    this.definedParameters.clear();
  }

  private boolean isLoaded = false;

  public void reload() {
    this.reloadParameterValues.clear();
    for (Map.Entry<String, ParameterDefinition> entry : this.definedParameters.entrySet()) {
      this.reloadParameterValues.put(entry.getKey(), entry.getValue());
    }
    reload(true);
    this.reloadParameterValues.clear();
  }

  private void reload(boolean reloadParameters) {
    if (reloadParameters) {
      removeJsonParameters();
    }

    this.warnings.clear();
    this.warning.setValue(false);
    this.errorMessage.setValue("");
    this.error.setValue(false);

    this.size = 0;
    this.definedPoints.clear();
    this.definedStrips.clear();
    this.definedArcs.clear();
    this.definedOutputs.clear();
    this.metaData.clear();

    // Clear the children
    for (LXFixture child : this.children) {
      child.dispose();
    }
    this.mutableChildren.clear();

    this.isLoaded = false;
    loadFixture(reloadParameters);
    regenerate();

  }

  private void loadFixture(boolean loadParameters) {
    if (this.isLoaded) {
      LX.error(new Exception(), "Trying to load JsonFixture twice, why?");
      return;
    }
    this.isLoaded = true;

    File fixtureFile = this.lx.getMediaFile(LX.Media.FIXTURES, this.fixtureType.getString().replace("/", File.separator) + ".lxf", false);
    if (!fixtureFile.exists()) {
      setError("Invalid fixture type, could not find file: " + fixtureFile);
      return;
    } else if (!fixtureFile.isFile()) {
      setError("Invalid fixture type, not a normal file: " + fixtureFile);
      return;
    }

    try (FileReader fr = new FileReader(fixtureFile)) {
      JsonObject obj = new Gson().fromJson(fr, JsonObject.class);

      if (loadParameters) {
        loadLabel(obj);
        this.modelKeys = loadModelKeys(obj, true, true, LXModel.Key.MODEL);
        loadParameters(obj);
        this.parametersReloaded.bang();
      }

      loadPoints(obj);
      loadStrips(obj);
      loadArcs(obj);

      loadChildren(obj);

      loadOutputs(this.definedOutputs, obj);

      loadMetaData(obj, this.metaData);

    } catch (JsonParseException jpx) {
      String message = jpx.getLocalizedMessage();
      Throwable cause = jpx.getCause();
      if (cause instanceof MalformedJsonException) {
        message = "Invalid JSON in " + fixtureFile.getName() + ": " + ((MalformedJsonException)cause).getLocalizedMessage();
      }
      setError(jpx, message);
    } catch (Exception x) {
      setError(x, "Error loading fixture from " + fixtureFile.getName() + ": " + x.getLocalizedMessage());
    }
  }

  private void setError(String error) {
    setError(null, error);
  }

  private void setError(Exception x, String error) {
    this.errorMessage.setValue(error);
    this.error.setValue(true);
    LX.error(x, "Fixture " + this.fixtureType.getString() + ".lxf: " + error);
  }

  private void addWarning(String warning) {
    this.warnings.add(warning);
    if (this.warning.isOn()) {
      this.warning.bang();
    } else {
      this.warning.setValue(true);
    }
    LX.error("Fixture " + this.fixtureType.getString() + ".lxf: " + warning);
  }

  private void warnDuplicateKeys(JsonObject obj, String key1, String key2) {
    if (obj.has(key1) && obj.has(key2)) {
      addWarning("Should use only one of " + key1 + " or " + key2 + " - " + key1 + " will be ignored.");
    }
  }

  private static final Pattern parameterPattern = Pattern.compile("\\$\\{([a-zA-Z0-9]+)\\}");

  private String replaceVariables(String key, String expression, ParameterType returnType) {
    StringBuilder result = new StringBuilder();
    int index = 0;
    Matcher matcher = parameterPattern.matcher(expression);
    while (matcher.find()) {
      String parameterName = matcher.group(1);
      ParameterDefinition parameter = this.definedParameters.get(parameterName);
      if (parameter == null) {
        addWarning("Illegal reference in " + key + ", there is no parameter: " + parameterName);
        return null;
      }
      parameter.isReferenced = true;
      String parameterValue = "";
      switch (returnType) {
      case FLOAT:
        if (parameter.type == ParameterType.FLOAT || parameter.type == ParameterType.INT) {
          parameterValue = String.valueOf(parameter.parameter.getValue());
        } else {
          addWarning("Cannot load non-numeric parameter " + parameterName + " into a float type: " + key);
          return null;
        }
        break;
      case INT:
        if (parameter.type == ParameterType.INT) {
          parameterValue = String.valueOf(parameter.intParameter.getValuei());
        } else {
          addWarning("Cannot load non-integer variable ${" + parameterName + "} into an integer type: " + key);
          return null;
        }
        break;
      case STRING:
        if (parameter.type == ParameterType.STRING) {
          parameterValue = parameter.stringParameter.getString();
        } else {
          parameterValue = String.valueOf(parameter.parameter.getValue());
        }
        break;
      case BOOLEAN:
        if (parameter.type == ParameterType.BOOLEAN) {
          parameterValue = String.valueOf(parameter.booleanParameter.isOn());
        } else {
          addWarning("Cannot load non-boolean variable ${" + parameterName + "} into a boolean type: " + key);
          return null;
        }
        break;
      }
      result.append(expression, index, matcher.start());
      result.append(parameterValue);
      index = matcher.end();
    }
    if (index < expression.length()) {
      result.append(expression, index, expression.length());
    }
    return result.toString();
  }

  private float evaluateVariableExpression(JsonObject obj, String key, String expression, ParameterType type) {
    String substitutedExpression = replaceVariables(key, expression, type);
    if (substitutedExpression == null) {
      return 0;
    }
    try {
      return _evaluateSimpleExpression(obj, key, substitutedExpression);
    } catch (NumberFormatException nfx) {
      addWarning("Bad formating in expression: " + expression);
      nfx.printStackTrace();
      return 0;
    }
  }

  private final static char[] SIMPLE_EXPRESSION_OPERATORS = { '+', '-', '*', '/' };

  // Super-trivial implementation of *very* basic math expressions
  private float _evaluateSimpleExpression(JsonObject obj, String key, String expression) {
    for (char operator : SIMPLE_EXPRESSION_OPERATORS) {
      int index = expression.indexOf(operator);
      if ((index > 0) && (index < expression.length() - 1)) {
        float left = _evaluateSimpleExpression(obj, key, expression.substring(0, index));
        float right = _evaluateSimpleExpression(obj, key, expression.substring(index + 1));
        switch (operator) {
        case '+': return left + right;
        case '-': return left - right;
        case '*': return left * right;
        case '/': return left / right;
        }
      }
    }
    return Float.parseFloat(expression);
  }

  private float loadFloat(JsonObject obj, String key, boolean variablesAllowed) {
    return loadFloat(obj, key, variablesAllowed, key + " should be primitive float value");
  }

  private float loadFloat(JsonObject obj, String key, boolean variablesAllowed, String warning) {
    if (obj.has(key)) {
      JsonElement floatElem = obj.get(key);
      if (floatElem.isJsonPrimitive()) {
        JsonPrimitive floatPrimitive = floatElem.getAsJsonPrimitive();
        if (variablesAllowed && floatPrimitive.isString()) {
          return evaluateVariableExpression(obj, key, floatPrimitive.getAsString(), ParameterType.FLOAT);
        }
        return floatElem.getAsFloat();
      }
      addWarning(warning);
    }
    return 0f;
  }

  private boolean loadBoolean(JsonObject obj, String key, boolean variablesAllowed, String warning) {
    if (obj.has(key)) {
      JsonElement boolElem = obj.get(key);
      if (boolElem.isJsonPrimitive()) {
        JsonPrimitive boolPrimitive = boolElem.getAsJsonPrimitive();
        if (variablesAllowed && boolPrimitive.isString()) {
          // TODO: perhaps in the future we support boolean expressions? waiting for a use case to justify it first...
          return Boolean.parseBoolean(replaceVariables(key, boolPrimitive.getAsString(), ParameterType.BOOLEAN));
        }
        return boolElem.getAsBoolean();
      }
      addWarning(warning);
    }
    return false;
  }

  private int loadInt(JsonObject obj, String key, boolean variablesAllowed, String warning) {
    if (obj.has(key)) {
      JsonElement intElem = obj.get(key);
      if (intElem.isJsonPrimitive()) {
        JsonPrimitive intPrimitive = intElem.getAsJsonPrimitive();
        if (variablesAllowed && intPrimitive.isString()) {
          return (int) evaluateVariableExpression(obj, key, intPrimitive.getAsString(), ParameterType.INT);
        }
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
      loadFloat(obj, KEY_X, true),
      loadFloat(obj, KEY_Y, true),
      loadFloat(obj, KEY_Z, true)
    );
  }

  private String loadString(JsonObject obj, String key, boolean variablesAllowed, String warning) {
    if (obj.has(key)) {
      JsonElement stringElem = obj.get(key);
      if (stringElem.isJsonPrimitive() && stringElem.getAsJsonPrimitive().isString()) {
        if (variablesAllowed) {
          return replaceVariables(key, stringElem.getAsString(), ParameterType.STRING);
        } else {
          return stringElem.getAsString();
        }
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

  private void loadGeometry(JsonFixture fixture, JsonObject obj) {
    if (obj.has(KEY_X)) {
      fixture.x.setValue(loadFloat(obj, KEY_X, true));
    }
    if (obj.has(KEY_Y)) {
      fixture.y.setValue(loadFloat(obj, KEY_Y, true));
    }
    if (obj.has(KEY_Z)) {
      fixture.z.setValue(loadFloat(obj, KEY_Z, true));
    }
    if (obj.has(KEY_YAW)) {
      fixture.yaw.setValue(loadFloat(obj, KEY_YAW, true));
    }
    if (obj.has(KEY_PITCH)) {
      fixture.pitch.setValue(loadFloat(obj, KEY_PITCH, true));
    }
    if (obj.has(KEY_ROLL)) {
      fixture.roll.setValue(loadFloat(obj, KEY_ROLL, true));
    }
    if (obj.has(KEY_SCALE)) {
      fixture.scale.setValue(loadFloat(obj, KEY_SCALE, true));
    }
  }

  private void loadLabel(JsonObject obj) {
    // Don't reload this if the user has renamed it
    if (!this.label.getString().equals(LABEL_PLACEHOLDER)) {
      return;
    }
    String validLabel = this.fixtureType.getString();
    String testLabel = loadString(obj, KEY_LABEL, false, KEY_LABEL + " should contain a string");
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

  private String[] loadModelKeys(JsonObject obj, boolean required, boolean includeParent, String ... defaultKeys) {
    warnDuplicateKeys(obj, KEY_MODEL_KEY, KEY_MODEL_KEYS);
    List<String> validModelKeys = new ArrayList<String>();

    if (obj.has(KEY_MODEL_KEYS)) {
      JsonArray modelKeyArr = loadArray(obj, KEY_MODEL_KEYS);
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
    } else if (obj.has(KEY_MODEL_KEY)) {
      String key = loadString(obj, KEY_MODEL_KEY, false, KEY_MODEL_KEY + " should contain a single string value");
      if (key != null) {
        key = key.trim();
        if (key.isEmpty()) {
          addWarning(KEY_MODEL_KEY + " must contain a non-empty string value");
        } else {
          validModelKeys.add(key);
        }
      }
    } else if (required) {
      LX.warning("Fixture definition should specify one of " + KEY_MODEL_KEY + " or " + KEY_MODEL_KEYS);
    }

    if (includeParent) {
      for (String key : loadModelKeys(this.jsonParameterValues, false, false)) {
        if (validModelKeys.contains(key)) {
          LX.warning("Parent JSON fixture redundantly specifies model key: " + key);
        } else {
          validModelKeys.add(key);
        }
      }
    }

    if (validModelKeys.isEmpty()) {
      return defaultKeys;
    }
    return validModelKeys.toArray(new String[0]);
  }

  private void loadParameters(JsonObject obj) {
    JsonObject parametersObj = loadObject(obj, KEY_PARAMETERS, KEY_PARAMETERS + " must be a JSON object");
    if (parametersObj == null) {
      return;
    }
    for (String parameterName : parametersObj.keySet()) {
      if (!parameterName.matches("^[a-zA-Z0-9]+$")) {
        addWarning("Invalid parameter name, must be non-empty only containing ASCII alphanumerics: " + parameterName);
        continue;
      }
      String parameterLabel = parameterName;
      if (this.definedParameters.containsKey(parameterName)) {
        addWarning("Parameter cannot be defined twice: " + parameterName);
        continue;
      }
      JsonElement parameterElem = parametersObj.get(parameterName);
      if (!parameterElem.isJsonObject()) {
        addWarning("Definition for parameter " + parameterName + " must be a JSON object specifying " + KEY_PARAMETER_TYPE + " and " + KEY_PARAMETER_DEFAULT);
        continue;
      }
      JsonObject parameterObj = parameterElem.getAsJsonObject();
      if (parameterObj.has(KEY_PARAMETER_LABEL)) {
        String rawLabel = loadString(parameterObj, KEY_PARAMETER_LABEL, false, "Parameter " + KEY_PARAMETER_LABEL + " must be valid String");
        if (!rawLabel.matches("^[a-zA-Z0-9 ]+$")) {
          addWarning("Invalid parameter label, must be non-empty only containing ASCII alphanumerics: " + rawLabel);
        } else {
          parameterLabel = rawLabel;
        }
      }

      String parameterDescription = loadString(parameterObj, KEY_PARAMETER_DESCRIPTION, false, "Parameter " + KEY_PARAMETER_DESCRIPTION + " must be strign value.");

      String typeStr = loadString(parameterObj, KEY_PARAMETER_TYPE, false, "Parameter " + parameterName + " must specify valid type string");;
      ParameterType type = ParameterType.get(typeStr);
      if (type == null) {
        addWarning("Parameter " + parameterName + " must specify valid type string");
        continue;
      }
      if (!parameterObj.has(KEY_PARAMETER_DEFAULT)) {
        addWarning("Parameter " + parameterName + " must specify " + KEY_PARAMETER_DEFAULT);
        continue;
      }
      JsonElement defaultElem = parameterObj.get(KEY_PARAMETER_DEFAULT);
      if (!defaultElem.isJsonPrimitive()) {
        addWarning("Parameter " + parameterName + " must specify primitive value for " + KEY_PARAMETER_DEFAULT);
        continue;
      }

      ParameterDefinition reloadDefinition = this.reloadParameterValues.get(parameterName);
      if (reloadDefinition != null && reloadDefinition.type != type) {
        reloadDefinition = null;
      }

      switch (type) {
      case FLOAT:
        float floatValue = defaultElem.getAsFloat();
        if (this.jsonParameterValues.has(parameterName)) {
          floatValue = this.jsonParameterContext.loadFloat(this.jsonParameterValues, parameterName, true);
        } else if (reloadDefinition != null) {
          floatValue = reloadDefinition.floatParameter.getValuef();
        }
        addJsonParameter(new ParameterDefinition(parameterName, parameterLabel, parameterDescription, floatValue));
        break;
      case INT:
        int minInt = 0;
        int maxInt = 1 << 16;
        if (parameterObj.has(KEY_PARAMETER_MIN)) {
          minInt = loadInt(parameterObj, KEY_PARAMETER_MIN, false, "Parameter min value must be an integer");
        }
        if (parameterObj.has(KEY_PARAMETER_MAX)) {
          maxInt = loadInt(parameterObj, KEY_PARAMETER_MAX, false, "Parameter min value must be an integer");
        }
        if (minInt > maxInt) {
          addWarning("Parameter minimum may not be greater than maximum: " + minInt + ">" + maxInt);
          break;
        }
        int intValue = defaultElem.getAsInt();
        if (this.jsonParameterValues.has(parameterName)) {
          intValue = this.jsonParameterContext.loadInt(this.jsonParameterValues, parameterName, true, "Child parameter should be an int: " + parameterName);
        } else if (reloadDefinition != null) {
          intValue = LXUtils.constrain(reloadDefinition.intParameter.getValuei(), minInt, maxInt);
        }
        addJsonParameter(new ParameterDefinition(parameterName, parameterLabel, parameterDescription, intValue, minInt, maxInt));
        break;
      case STRING:
        String stringValue = defaultElem.getAsString();
        if (this.jsonParameterValues.has(parameterName)) {
          stringValue = this.jsonParameterContext.loadString(this.jsonParameterValues, parameterName, true, "Child parameter should be an string: " + parameterName);
        } else if (reloadDefinition != null) {
          stringValue = reloadDefinition.stringParameter.getString();
        }
        addJsonParameter(new ParameterDefinition(parameterName, parameterLabel, parameterDescription, stringValue));
        break;
      case BOOLEAN:
        boolean booleanValue = defaultElem.getAsBoolean();
        if (this.jsonParameterValues.has(parameterName)) {
          booleanValue = this.jsonParameterContext.loadBoolean(this.jsonParameterValues, parameterName, true, "Child parameter should be a boolean: " + parameterName);
        } else if (reloadDefinition != null) {
          booleanValue = reloadDefinition.booleanParameter.isOn();
        }
        addJsonParameter(new ParameterDefinition(parameterName, parameterLabel, parameterDescription, booleanValue));
        break;
      }

    }
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
    int numPoints = loadInt(stripObj, KEY_NUM_POINTS, true, "Strip must specify a positive integer for " + KEY_NUM_POINTS);
    if (numPoints <= 0) {
      addWarning("Strip must specify positive integer value for " + KEY_NUM_POINTS);
      return;
    }
    if (numPoints > StripDefinition.MAX_POINTS) {
      addWarning("Single strip may not define more than " + StripDefinition.MAX_POINTS + " points");
      return;
    }

    String[] modelKeys = loadModelKeys(stripObj, false, false, LXModel.Key.STRIP);

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
      transform.rotateY((float) Math.toRadians(loadFloat(stripObj, KEY_YAW, true)));
      transform.rotateX((float) Math.toRadians(loadFloat(stripObj, KEY_PITCH, true)));
      transform.rotateZ((float) Math.toRadians(loadFloat(stripObj, KEY_ROLL, true)));
    }

    if (stripObj.has(KEY_SPACING)) {
      float testSpacing = loadFloat(stripObj, KEY_SPACING, true, "Strip must specify a positive " + KEY_SPACING);
      if (testSpacing > 0) {
        spacing = testSpacing;
      } else {
        addWarning("Strip may not specify a negative spacing");
      }
    }

    List<OutputDefinition> outputs = new ArrayList<OutputDefinition>();
    loadOutputs(outputs, stripObj);

    Map<String, String> stripMetaData = new HashMap<String, String>();
    loadMetaData(stripObj, stripMetaData);

    this.definedStrips.add(new StripDefinition(numPoints, spacing, origin, transform, outputs, stripMetaData, modelKeys));
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
    int numPoints = loadInt(arcObj, KEY_NUM_POINTS, true, "Arc must specify a positive integer for " + KEY_NUM_POINTS);
    if (numPoints <= 0) {
      addWarning("Arc must specify positive integer value for " + KEY_NUM_POINTS);
      return;
    }
    if (numPoints > ArcDefinition.MAX_POINTS) {
      addWarning("Single arc may not define more than " + ArcDefinition.MAX_POINTS + " points");
      return;
    }

    String[] modelKeys = loadModelKeys(arcObj, false, false, LXModel.Key.STRIP, LXModel.Key.ARC);

    LXMatrix transform = new LXMatrix();

    // Load position
    LXVector position = loadVector(arcObj, "Arc should specify at least one x/y/z value");
    transform.translate(position.x, position.y, position.z);
    boolean isCenter = false;

    float radius = loadFloat(arcObj, KEY_RADIUS, true, "Arc must specify radius");
    if (radius < 0) {
      addWarning("Arc must specify positive value for " + KEY_RADIUS);
      return;
    }

    float degrees = loadFloat(arcObj, KEY_DEGREES, true, "Arc must specify number of degrees to cover");

    if (arcObj.has(KEY_ARC_MODE)) {
      String arcMode = loadString(arcObj, KEY_ARC_MODE, true, "Arc " + KEY_ARC_MODE + " must be a string");
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
          transform.rotateZ((float) Math.toRadians(loadFloat(arcObj, KEY_ROLL, true)));
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
          transform.rotateX((float) Math.toRadians(loadFloat(arcObj, KEY_PITCH, true))); // pitch
          transform.rotateZ((float) Math.asin(direction.y / direction.mag())); // roll
        }
      }
    } else {
      transform.rotateY((float) Math.toRadians(loadFloat(arcObj, KEY_YAW, true)));
      transform.rotateX((float) Math.toRadians(loadFloat(arcObj, KEY_PITCH, true)));
      transform.rotateZ((float) Math.toRadians(loadFloat(arcObj, KEY_ROLL, true)));
    }

    List<OutputDefinition> outputs = new ArrayList<OutputDefinition>();
    loadOutputs(outputs, arcObj);

    Map<String, String> arcMetaData = new HashMap<String, String>();
    loadMetaData(arcObj, arcMetaData);

    this.definedArcs.add(new ArcDefinition(numPoints, radius, degrees, isCenter, transform, outputs, arcMetaData, modelKeys));
  }

  private void loadChildren(JsonObject obj) {
    JsonArray childrenArr = loadArray(obj, KEY_CHILDREN);
    if (childrenArr == null) {
      return;
    }
    for (JsonElement childElem : childrenArr) {
      if (childElem.isJsonObject()) {
        loadChild(childElem.getAsJsonObject());
      } else if (!childElem.isJsonNull()) {
        addWarning(KEY_CHILDREN + " should only contain childd elements in JSON object format, found invalid: " + childElem);
      }
    }
  }

  private void loadChild(JsonObject childObj) {
    if (!childObj.has(KEY_TYPE)) {
      addWarning("Child object must specify type");
      return;
    }
    String type = loadString(childObj, KEY_TYPE, true, "Child object must specify string type");
    if (type == null || type.isEmpty()) {
      addWarning("Child object must specify valid non-empty type: " + type);
      return;
    }
    JsonFixture child = new JsonFixture(this.lx, this, childObj, type);
    if (child.error.isOn()) {
      setError(child.errorMessage.getString());
      return;
    }
    if (child.warning.isOn() ) {
      this.warnings.addAll(child.warnings);
      if (this.warning.isOn()) {
        this.warning.bang();
      } else {
        this.warning.setValue(true);
      }
    }
    loadGeometry(child, childObj);
    loadMetaData(childObj, child.metaData);

    // Ensure child has correct enabled status
    child.enabled.setValue(this.enabled.isOn());

    // TODO(mcslee): should we allow adding outputs here? if so do they supercede
    // those of the child or are they in addition?

    addChild(child, true);
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
          addWarning(KEY_OUTPUTS + " should only contain output elements in JSON object format, found invalid: " + outputElem);
        }
      }
    }
  }

  private void loadOutput(List<OutputDefinition> outputs, JsonObject outputObj) {
    ProtocolDefinition protocol = ProtocolDefinition.get(loadString(outputObj, KEY_PROTOCOL, true, "Output must specify a valid " + KEY_PROTOCOL));
    if (protocol == null) {
      addWarning("Output definition must define a valid protocol");
      return;
    }

    TransportDefinition transport = TransportDefinition.UDP;
    if (outputObj.has(KEY_TRANSPORT)) {
      transport = TransportDefinition.get(loadString(outputObj, KEY_TRANSPORT, true, "Output must specify valid transport"));
      if (transport == null) {
        transport = TransportDefinition.UDP;
        addWarning("Output should define a valid transport");
      }
    }

    String host = loadString(outputObj, KEY_HOST, true, "Output must specify a valid host");
    if (host.isEmpty()) {
      addWarning("Output must define a valid, non-empty host");
      return;
    }
    InetAddress address;
    try {
      address = InetAddress.getByName(host);
    } catch (UnknownHostException uhx) {
      addWarning("Cannot send output to invalid host: " + host);
      return;
    }

    int port = OutputDefinition.DEFAULT_PORT;
    if (outputObj.has(KEY_PORT)) {
      port = loadInt(outputObj, KEY_PORT, true, "Output must specify a valid host");
      if (port <= 0) {
        addWarning("Output port number must be positive: " + port);
        return;
      }
    } else if (protocol.requiresExplicitPort()) {
      addWarning("Protcol " + protocol + " requires an expicit port number to be specified");
      return;
    }

    String universeKey = protocol.getUniverseKey();
    int universe = loadInt(outputObj, universeKey, true, "Output " + universeKey + " must be a valid integer");
    if (universe < 0) {
      addWarning("Output " + universeKey + " may not be negative");
      return;
    }

    // Top level output byte-order
    ByteOrderDefinition byteOrder = loadByteOrder(outputObj, ByteOrderDefinition.RGB);

    // Load up the segment definitions
    List<SegmentDefinition> segments = new ArrayList<SegmentDefinition>();
    loadSegments(segments, outputObj, byteOrder);

    outputs.add(new OutputDefinition(protocol, transport, byteOrder, address, port, universe, segments));
  }

  private void loadMetaData(JsonObject obj, Map<String, String> metaData) {
    JsonObject metaDataObj = loadObject(obj, KEY_META, KEY_META + " must be a JSON object");
    if (metaDataObj != null) {
      for (Map.Entry<String, JsonElement> entry : metaDataObj.entrySet()) {
        String key = entry.getKey();
        JsonElement value = entry.getValue();
        if (!value.isJsonPrimitive()) {
          addWarning("Meta data values must be primtives, key has invalid type: " + key);
        } else {
          metaData.put(key, replaceVariables(key, value.getAsJsonPrimitive().getAsString(), ParameterType.STRING));
        }
      }
    }
  }

  private static final String[] SEGMENT_KEYS = { KEY_NUM, KEY_START, KEY_STRIDE, KEY_REVERSE };

  private void loadSegments(List<SegmentDefinition> segments, JsonObject outputObj, ByteOrderDefinition defaultByteOrder) {
    if (outputObj.has(KEY_SEGMENTS)) {
      // Specifying an array of segments, keys should not be there by default
      for (String segmentKey : SEGMENT_KEYS) {
        if (outputObj.has(segmentKey)) {
          addWarning(KEY_OUTPUT + " specifies " + KEY_SEGMENTS + ", may not also specify " + segmentKey + ", will be ignored");
        }
      }

      JsonArray segmentsArr = loadArray(outputObj, KEY_SEGMENTS, KEY_SEGMENTS + " must be an array of segments");
      if (segmentsArr != null) {
        for (JsonElement segmentElem : segmentsArr) {
          if (segmentElem.isJsonObject()) {
            loadSegment(segments, segmentElem.getAsJsonObject(), defaultByteOrder, false);
          } else if (!segmentElem.isJsonNull()) {
            addWarning(KEY_SEGMENTS + " should only contain segment elements in JSON object format, found invalid: " + segmentElem);
          }
        }
      }
    } else {
      // Just specifying one, no need for segments key, defined directly in output
      loadSegment(segments, outputObj, defaultByteOrder, true);
    }
  }

  private void loadSegment(List<SegmentDefinition> segments, JsonObject segmentObj, ByteOrderDefinition outputByteOrder, boolean isOutput) {
    int start = loadInt(segmentObj, KEY_START, true, "Output " + KEY_START + " must be a valid integer");
    if (start < 0) {
      addWarning("Output " + KEY_START + " may not be negative");
      return;
    }
    int num = OutputDefinition.ALL_POINTS;
    if (segmentObj.has(KEY_NUM)) {
      num = loadInt(segmentObj, KEY_NUM, true, "Output " + KEY_NUM + " must be a valid integer");
      if (num < 0) {
        addWarning("Output " + KEY_NUM + " may not be negative");
        return;
      }
    }
    int stride = 1;
    if (segmentObj.has(KEY_STRIDE)) {
      stride = loadInt(segmentObj, KEY_STRIDE, true, "Output " + KEY_STRIDE + " must be a valid integer");
      if (stride <= 0) {
        addWarning("Output stride must be a positive value, use 'reverse: true' to invert pixel order");
        return;
      }
    }
    boolean reverse = loadBoolean(segmentObj, KEY_REVERSE, true, "Output " + KEY_REVERSE + " must be a valid boolean");

    ByteOrderDefinition segmentByteOrder = null;
    if (!isOutput) {
      segmentByteOrder = loadByteOrder(segmentObj, null);
      if (segmentByteOrder != null) {
        if (segmentByteOrder.getByteOrder().getNumBytes() != outputByteOrder.getByteOrder().getNumBytes()) {
          addWarning("Segment byte order must have same number of bytes as output byte order (" +
            segmentByteOrder.getByteOrder() + " != " + outputByteOrder.getByteOrder() + "), ignoring segment byte order"
          );
          segmentByteOrder = null;
        }
      }
    }

    segments.add(new SegmentDefinition(start, num, stride, reverse, segmentByteOrder));
  }

  private ByteOrderDefinition loadByteOrder(JsonObject obj, ByteOrderDefinition defaultByteOrder) {
    ByteOrderDefinition byteOrder = defaultByteOrder;
    String byteOrderStr = loadString(obj, KEY_BYTE_ORDER, true, "Output must specify a valid string " + KEY_BYTE_ORDER);
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
    return byteOrder;
  }

  @Override
  protected void computeGeometryMatrix(LXMatrix geometryMatrix) {
    super.computeGeometryMatrix(geometryMatrix);
    geometryMatrix.scale(this.scale.getValuef());
  }

  @Override
  protected void computePointGeometry(LXMatrix matrix, List<LXPoint> points) {
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
  protected void buildOutputs() {
    // Add strip-specific outputs
    for (StripDefinition strip : this.definedStrips) {
      for (OutputDefinition output : strip.outputs) {
        buildOutput(output, strip.index, strip.numPoints);
      }
    }
    // Add strip-specific outputs
    for (ArcDefinition arc : this.definedArcs) {
      for (OutputDefinition output : arc.outputs) {
        buildOutput(output, arc.index, arc.numPoints);
      }
    }
    // Add top level outputs last
    int totalSize = totalSize();
    for (OutputDefinition output : this.definedOutputs) {
      buildOutput(output, 0, totalSize);
    }
  }

  private void buildOutput(OutputDefinition output, int fixtureOffset, int fixtureSize) {
    if (output.protocol == ProtocolDefinition.ARTSYNC) {
      buildArtSyncDatagram(output);
      return;
    }

    boolean hasDynamicByteOrder = false;

    List<IndexBufferSegment> segments = new ArrayList<IndexBufferSegment>();
    for (SegmentDefinition segment : output.segments) {

      if (segment.start < 0 || (segment.start >= fixtureSize)) {
        addWarning("Output specifies invalid start position: " + segment.start + " should be between [0, " + (fixtureSize-1) + "]");
        return;
      }

      int start = segment.start + fixtureOffset;
      int num = segment.num;
      if (num == OutputDefinition.ALL_POINTS) {
        num = fixtureSize;
      }
      int stride = segment.stride;
      if (start + stride * (num-1) >= fixtureSize) {
        addWarning("Output specifies excessive size beyond fixture limits: start=" + segment.start + " num=" + num + " stride=" + stride + " fixtureSize=" + fixtureSize);
        return;
      }

      // Want those backwards? No problem!
      if (segment.reverse) {
        start = start + stride * (num-1);
        stride = -stride;
      }

      if (segment.dynamicByteOrder != null) {
        hasDynamicByteOrder = true;
      }

      segments.add(new IndexBufferSegment(start, num, stride));
    }

    LXBufferOutput.ByteOrder outputByteOrder = output.byteOrder.getByteOrder();
    int[] indexBuffer = toDynamicIndexBuffer(segments.toArray(new IndexBufferSegment[0]));
    LXBufferOutput.ByteOrder[] byteOrderBuffer = null;
    int dataLength = indexBuffer.length * outputByteOrder.getNumBytes();
    LXBufferOutput bufferOutput = null;

    if (hasDynamicByteOrder) {
      byteOrderBuffer = new LXBufferOutput.ByteOrder[indexBuffer.length];
      int i = 0;
      for (SegmentDefinition segment : output.segments) {
        LXBufferOutput.ByteOrder segmentByteOrder = (segment.dynamicByteOrder != null) ?
          segment.dynamicByteOrder.getByteOrder() : outputByteOrder;
        for (int s = 0; s < segment.num; ++s) {
          byteOrderBuffer[i++] = segmentByteOrder;
        }
      }
    }

    if (dataLength >= (1 << 16)) {
      addWarning("Output packet would have length > 16-bits (" + dataLength + ") - not possible");
      return;
    }

    switch (output.protocol) {
    case ARTNET:
      if (dataLength > ArtNetDatagram.MAX_DATA_LENGTH) {
        addWarning("Art-Net packet using noncompliant size: " + dataLength + ">" + ArtNetDatagram.MAX_DATA_LENGTH);
      }
      bufferOutput = new ArtNetDatagram(this.lx, indexBuffer, outputByteOrder, output.universe);
      break;
    case SACN:
      if (dataLength > StreamingACNDatagram.MAX_DATA_LENGTH) {
        addWarning("Streaming ACN / E1.31 packet using noncompliant size: " + dataLength + ">" + StreamingACNDatagram.MAX_DATA_LENGTH);
      }
      bufferOutput = new StreamingACNDatagram(this.lx, indexBuffer, outputByteOrder, output.universe);
      break;
    case DDP:
      if (outputByteOrder != LXBufferOutput.ByteOrder.RGB) {
        addWarning("DDP packets do not support non-RGB byte order, using RGB");
      }
      bufferOutput = new DDPDatagram(this.lx, indexBuffer, output.universe);
      break;
    case KINET:
      if (outputByteOrder != LXBufferOutput.ByteOrder.RGB) {
        addWarning("KiNET packets do not support non-RGB byte order, using RGB");
      }
      if (dataLength > KinetDatagram.MAX_DATA_LENGTH) {
        addWarning("KiNET packets cannot support more than 512 bytes payload, ignoring this output");
        return;
      }
      bufferOutput = new KinetDatagram(this.lx, indexBuffer, output.universe);
      break;
    case OPC:
      if (output.transport == TransportDefinition.TCP) {
        bufferOutput = new OPCSocket(this.lx, indexBuffer, outputByteOrder, (byte) output.universe);
      } else {
        bufferOutput = new OPCDatagram(this.lx, indexBuffer, outputByteOrder, (byte) output.universe);
      }
      break;
    case ARTSYNC:
    default:
      // Already handled above
      break;
    }
    if (bufferOutput != null) {
      // Set a dynamic byte order if we have one
      if (byteOrderBuffer != null) {
        bufferOutput.setByteOrder(byteOrderBuffer);
      }
      if (bufferOutput instanceof LXOutput.InetOutput) {
        LXOutput.InetOutput inetOutput = (LXOutput.InetOutput) bufferOutput;
        if (output.port != OutputDefinition.DEFAULT_PORT) {
          inetOutput.setPort(output.port);
        }
        inetOutput.setAddress(output.address);
      }
      addOutput(bufferOutput);
    }
  }

  private void buildArtSyncDatagram(OutputDefinition output) {
    LXDatagram artSync = new ArtSyncDatagram(this.lx);
    if (output.port != OutputDefinition.DEFAULT_PORT) {
      artSync.setPort(output.port);
    }
    artSync.setAddress(output.address);
    addOutput(artSync);
  }

  @Override
  protected Submodel[] toSubmodels() {
    List<Submodel> submodels = new ArrayList<Submodel>();
    for (StripDefinition strip : this.definedStrips) {
      submodels.add(new Submodel(strip.index, strip.numPoints, strip.metaData, strip.modelKeys));
    }
    for (ArcDefinition arc : this.definedArcs) {
      submodels.add(new Submodel(arc.index, arc.numPoints, arc.metaData, arc.modelKeys));
    }
    return submodels.toArray(new Submodel[0]);
  }

  @Override
  protected int size() {
    return this.size;
  }

  private static final String KEY_FIXTURE_TYPE = "jsonFixtureType";
  private static final String KEY_JSON_PARAMETERS = "jsonParameters";

  @Override
  public void load(LX lx, JsonObject obj) {
    if (this.isJsonSubfixture) {
      throw new IllegalStateException("Should never be loading/saving a child JsonSubfixture");
    }

    // This has been saved, let's call up the values for the JSON parameters we customized
    this.jsonParameterValues =
      obj.has(KEY_JSON_PARAMETERS) ? obj.get(KEY_JSON_PARAMETERS).getAsJsonObject() : new JsonObject();

    // Now do the normal LXFixture loading, which will trigger regeneration if
    // we're part of a hierarchy
    super.load(lx, obj);

    // Now get rid of this, don't want to interfere with manual reload()
    this.jsonParameterValues = new JsonObject();
  }

  @Override
  public void save(LX lx, JsonObject obj) {
    if (this.isJsonSubfixture) {
      throw new IllegalStateException("Should never be loading/saving a child JsonSubfixture");
    }
    obj.addProperty(KEY_FIXTURE_TYPE, this.fixtureType.getString());
    JsonObject jsonParameters = new JsonObject();
    for (ParameterDefinition parameter : this.definedParameters.values()) {
      switch (parameter.type) {
      case FLOAT:
        jsonParameters.addProperty(parameter.name, parameter.floatParameter.getValue());
        break;
      case INT:
        jsonParameters.addProperty(parameter.name, parameter.intParameter.getValuei());
        break;
      case STRING:
        jsonParameters.addProperty(parameter.name, parameter.stringParameter.getString());
        break;
      case BOOLEAN:
        jsonParameters.addProperty(parameter.name, parameter.booleanParameter.isOn());
        break;
      }
    }
    obj.add(KEY_JSON_PARAMETERS, jsonParameters);
    super.save(lx, obj);
  }

}
