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
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
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
import heronarts.lx.output.ArtSyncDatagram;
import heronarts.lx.output.KinetDatagram;
import heronarts.lx.output.LXBufferOutput;
import heronarts.lx.output.LXDatagram;
import heronarts.lx.output.LXOutput;
import heronarts.lx.output.LXOutput.GammaTable;
import heronarts.lx.output.StreamingACNDatagram;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXListenableParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.parameter.ObjectParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.transform.LXMatrix;
import heronarts.lx.transform.LXVector;
import heronarts.lx.utils.LXUtils;

public class JsonFixture extends LXFixture {

  public static final String PATH_SEPARATOR = "/";
  public static final char PATH_SEPARATOR_CHAR = '/';

  // Label
  private static final String KEY_LABEL = "label";

  // Model tags
  private static final String KEY_TAG = "tag";
  private static final String KEY_TAGS = "tags";
  private static final String KEY_MODEL_KEY = "modelKey";   // deprecated, backwards-compatible
  private static final String KEY_MODEL_KEYS = "modelKeys"; // deprecated, backwards-compatible

  // Geometry
  private static final String KEY_X = "x";
  private static final String KEY_Y = "y";
  private static final String KEY_Z = "z";
  private static final String KEY_YAW = "yaw";
  private static final String KEY_PITCH = "pitch";
  private static final String KEY_ROLL = "roll";
  private static final String KEY_ROTATE_X = "rotateX";
  private static final String KEY_ROTATE_Y = "rotateY";
  private static final String KEY_ROTATE_Z = "rotateZ";
  private static final String KEY_SCALE_X = "scaleX";
  private static final String KEY_SCALE_Y = "scaleY";
  private static final String KEY_SCALE_Z = "scaleZ";
  private static final String KEY_SCALE = "scale";
  private static final String KEY_DIRECTION = "direction";
  private static final String KEY_NORMAL = "normal";
  private static final String KEY_END = "end";
  private static final String KEY_TRANSFORMS = "transforms";
  private static final String KEY_BRIGHTNESS = "brightness";

  // Points
  private static final String KEY_POINTS = "points";
  private static final String KEY_COORDINATES = "coords";

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
  private static final String KEY_COMPONENTS = "components";
  private static final String KEY_CHILDREN = "children";
  private static final String KEY_TYPE = "type";
  private static final String KEY_ID = "id";
  private static final String KEY_INSTANCES = "instances";
  private static final String KEY_INSTANCE = "instance";
  private static final int MAX_INSTANCES = 4096;

  private static final String TYPE_POINT = "point";
  private static final String TYPE_POINTS = "points";
  private static final String TYPE_STRIP = "strip";
  private static final String TYPE_ARC = "arc";

  // Parameters
  private static final String KEY_PARAMETERS = "parameters";
  private static final String KEY_PARAMETER_LABEL = "label";
  private static final String KEY_PARAMETER_DESCRIPTION = "description";
  private static final String KEY_PARAMETER_TYPE = "type";
  private static final String KEY_PARAMETER_DEFAULT = "default";
  private static final String KEY_PARAMETER_MIN = "min";
  private static final String KEY_PARAMETER_MAX = "max";
  private static final String KEY_PARAMETER_OPTIONS = "options";

  // Outputs
  private static final String KEY_OUTPUT = "output";
  private static final String KEY_OUTPUTS = "outputs";
  private static final String KEY_ENABLED = "enabled";
  private static final String KEY_FPS = "fps";
  private static final String KEY_PROTOCOL = "protocol";
  private static final String KEY_TRANSPORT = "transport";
  private static final String KEY_HOST = "host";
  private static final String KEY_PORT = "port";
  private static final String KEY_BYTE_ORDER = "byteOrder";
  private static final String KEY_UNIVERSE = "universe";
  private static final String KEY_DDP_DATA_OFFSET = "dataOffset";
  private static final String KEY_KINET_PORT = "kinetPort";
  private static final String KEY_KINET_VERSION = "kinetVersion";
  private static final String KEY_OPC_CHANNEL = "channel";
  private static final String KEY_CHANNEL = "channel";
  private static final String KEY_PRIORITY = "priority";
  private static final String KEY_SEQUENCE_ENABLED = "sequenceEnabled";
  private static final String KEY_OFFSET = "offset";
  private static final String KEY_START = "start";
  private static final String KEY_COMPONENT_INDEX = "componentIndex";
  private static final String KEY_COMPONENT_ID = "componentId";
  private static final String KEY_NUM = "num";
  private static final String KEY_STRIDE = "stride";
  private static final String KEY_PAD_PRE = "padPre";
  private static final String KEY_PAD_POST = "padPost";
  private static final String KEY_REPEAT = "repeat";
  private static final String KEY_DUPLICATE = "duplicate";
  private static final String KEY_REVERSE = "reverse";
  private static final String KEY_HEADER_BYTES = "headerBytes";
  private static final String KEY_FOOTER_BYTES = "footerBytes";
  private static final String KEY_SEGMENTS = "segments";

  // Metadata
  private static final String KEY_META = "meta";

  private static final String LABEL_PLACEHOLDER = "UNKNOWN";

  private enum JsonProtocolDefinition {
    ARTNET(LXProtocolFixture.Protocol.ARTNET, KEY_UNIVERSE, KEY_CHANNEL, "artnet", "artdmx"),
    ARTSYNC(LXProtocolFixture.Protocol.ARTNET, null, null, "artsync"),
    SACN(LXProtocolFixture.Protocol.SACN, KEY_UNIVERSE, KEY_CHANNEL, "sacn", "e131"),
    DDP(LXProtocolFixture.Protocol.DDP, KEY_DDP_DATA_OFFSET, null, "ddp"),
    OPC(LXProtocolFixture.Protocol.OPC, KEY_OPC_CHANNEL, KEY_OFFSET, "opc"),
    KINET(LXProtocolFixture.Protocol.KINET, KEY_KINET_PORT, KEY_CHANNEL, "kinet");

    private final LXProtocolFixture.Protocol protocol;
    private final String universeKey;
    private final String channelKey;
    private final String[] protocolKeys;

    private JsonProtocolDefinition(LXProtocolFixture.Protocol protocol, String universeKey, String channelKey, String ... protocolKeys) {
      this.protocol = protocol;
      this.universeKey = universeKey;
      this.channelKey = channelKey;
      this.protocolKeys = protocolKeys;
    }

    public boolean requiresExplicitPort() {
      return (this == OPC);
    }

    private static JsonProtocolDefinition get(String key) {
      for (JsonProtocolDefinition protocol : values()) {
        for (String protocolKey : protocol.protocolKeys) {
          if (protocolKey.equals(key)) {
            return protocol;
          }
        }
      }
      return null;
    }
  }

  private enum JsonTransportDefinition {
    UDP(LXProtocolFixture.Transport.UDP, "udp"),
    TCP(LXProtocolFixture.Transport.TCP, "tcp");

    private final LXProtocolFixture.Transport transport;
    private final String transportKey;

    private JsonTransportDefinition(LXProtocolFixture.Transport transport, String transportKey) {
      this.transport = transport;
      this.transportKey = transportKey;
    }

    private static JsonTransportDefinition get(String key) {
      for (JsonTransportDefinition protocol : values()) {
        if (protocol.transportKey.equals(key)) {
          return protocol;
        }
      }
      return null;
    }

  }

  // A bit superfluous, but avoiding using the LXBufferOutput stuff
  // directly as JSON-loading is a separate namespace and want the code
  // to clearly reflect that, in case the two diverge in the future
  private static class JsonByteEncoderDefinition {

    private static final JsonByteEncoderDefinition RGB = new JsonByteEncoderDefinition(LXBufferOutput.ByteOrder.RGB);

    private static final Map<String, JsonByteEncoderDefinition> instances = new HashMap<String, JsonByteEncoderDefinition>();

    private final LXBufferOutput.ByteEncoder byteEncoder;

    private JsonByteEncoderDefinition(LXBufferOutput.ByteEncoder byteEncoder) {
      this.byteEncoder = byteEncoder;
    }

    private JsonByteEncoderDefinition(LX lx, final String className) throws NoSuchMethodException, ClassNotFoundException {
      final Class<?> cls = lx.instantiateStatic(className.replace('/', '$'));
      final Method getNumBytes = cls.getMethod("getNumBytes");
      final Method writeBytes = cls.getMethod("writeBytes", int.class, GammaTable.Curve.class, byte[].class, int.class);
      this.byteEncoder = new LXBufferOutput.ByteEncoder() {

        @Override
        public int getNumBytes() {
          try {
            return (int) getNumBytes.invoke(null);
          } catch (Throwable t) {
            LX.error("ByteEncoderClass " + cls  + " error on getNumBytes: " + t.getMessage());
            return 0;
          }
        }

        @Override
        public void writeBytes(int argb, GammaTable.Curve gamma, byte[] output, int offset) {
          try {
            writeBytes.invoke(null, argb, gamma, output, offset);
          } catch (Throwable t) {
            LX.error("ByteEncoderClass " + cls  + " error on writeBytes: " + t.getMessage());
          }
        }

      };
    }

    private static JsonByteEncoderDefinition get(LX lx, String order) {
      // Did we already look this encoder up? Re-use it!
      JsonByteEncoderDefinition instance = instances.get(order);
      if (instance != null) {
        return instance;
      }


      // Check for the basic byte order enum types
      for (LXBufferOutput.ByteOrder byteOrder : LXBufferOutput.ByteOrder.values()) {
        if (order.equalsIgnoreCase(byteOrder.name())) {
          instance = new JsonByteEncoderDefinition(byteOrder);
          instances.put(order, instance);
          return instance;
        }
      }

      // Try to make a new dynamic encoder based upon class-name
      try {
        final Class<?> cls = lx.instantiateStatic(order.replace('/', '$'));
        final Method getNumBytes = cls.getMethod("getNumBytes");
        final Method writeBytes = cls.getMethod("writeBytes", int.class, GammaTable.Curve.class, byte[].class, int.class);

        // Create a new dynamic encoder which uses reflection to call static methods
        // on the provided class
        instance = new JsonByteEncoderDefinition(new LXBufferOutput.ByteEncoder() {
          @Override
          public int getNumBytes() {
            try {
              return (int) getNumBytes.invoke(null);
            } catch (Throwable t) {
              LX.error("JsonByteEncoder " + cls  + " error on getNumBytes: " + t.getMessage());
              return 0;
            }
          }

          @Override
          public void writeBytes(int argb, GammaTable.Curve gamma, byte[] output, int offset) {
            try {
              writeBytes.invoke(null, argb, gamma, output, offset);
            } catch (Throwable t) {
              LX.error("JsonByteEncoder " + cls  + " error on writeBytes: " + t.getMessage());
            }
          }
        });
        instances.put(order, instance);
        return instance;
      } catch (Throwable x) {
        LX.error(x, "Could not instantiate JsonByteEncoder " + order + ": " + x.getMessage());
      }
      return null;
    }
  }

  public enum ChildType {
    POINT,
    POINTS,
    STRIP,
    ARC,
    JSON
  };

  public enum ParameterType {
    STRING("string"),
    INT("int"),
    FLOAT("float"),
    BOOLEAN("boolean"),
    STRING_SELECT(null);

    private final String key;

    private ParameterType(String key) {
      this.key = key;
    }

    private static ParameterType get(String str) {
      for (ParameterType type : values()) {
        if (str.toLowerCase().equals(type.key)) {
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
    public final ObjectParameter<String> stringSelectParameter;

    private boolean isReferenced = false;

    @SuppressWarnings("unchecked")
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
        this.stringSelectParameter = null;
        break;
      case INT:
        this.stringParameter = null;
        this.intParameter = (DiscreteParameter) parameter;
        this.floatParameter = null;
        this.booleanParameter = null;
        this.stringSelectParameter = null;
        break;
      case FLOAT:
        this.stringParameter = null;
        this.intParameter = null;
        this.floatParameter = (BoundedParameter) parameter;
        this.booleanParameter = null;
        this.stringSelectParameter = null;
        break;
      case BOOLEAN:
        this.stringParameter = null;
        this.intParameter = null;
        this.floatParameter = null;
        this.booleanParameter = (BooleanParameter) parameter;
        this.stringSelectParameter = null;
        break;
      case STRING_SELECT:
        this.stringSelectParameter = (ObjectParameter<String>) parameter;
        this.stringParameter = null;
        this.intParameter = null;
        this.floatParameter = null;
        this.booleanParameter = null;
        break;
      default:
        throw new IllegalStateException("Unknown ParameterType: " + type);
      }
      parameter.addListener(this);
    }

    private ParameterDefinition(String name, String label, String description, String value, String defaultStr) {
      this(name, label, description, ParameterType.STRING,
        new StringParameter(label, defaultStr)
        .setValue(value)
      );
    }

    private ParameterDefinition(String name, String label, String description, int value, int defaultInt, int minInt, int maxInt) {
      this(name, label, description, ParameterType.INT,
        (DiscreteParameter) new DiscreteParameter(label, defaultInt, minInt, maxInt + 1)
        .setValue(value)
      );
    }

    private ParameterDefinition(String name, String label, String description, float value, float defaultFloat, float minFloat, float maxFloat) {
      this(name, label, description, ParameterType.FLOAT,
        (BoundedParameter) new BoundedParameter(label, defaultFloat, minFloat, maxFloat)
        .setValue(value)
      );
    }

    private ParameterDefinition(String name, String label, String description, boolean value, boolean defaultBoolean) {
      this(name, label, description, ParameterType.BOOLEAN,
        new BooleanParameter(label, defaultBoolean)
        .setValue(value)
      );
    }

    private ParameterDefinition(String name, String label, String description, String value, String defaultStr, List<String> stringOptions) {
      this(name, label, description, ParameterType.STRING_SELECT,
        new ObjectParameter<String>(label, stringOptions.toArray(new String[0]), defaultStr)
        .setValue(value)
      );
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

    public String getValueAsString() {
      switch (this.type) {
      case BOOLEAN:
        return String.valueOf(this.booleanParameter.isOn());
      case FLOAT:
        return String.valueOf(this.floatParameter.getValue());
      case INT:
        return String.valueOf(this.intParameter.getValuei());
      case STRING:
        return this.stringParameter.getString();
      case STRING_SELECT:
        return this.stringSelectParameter.getObject();
      default:
        return "";
      }
    }

  }

  private class JsonOutputDefinition {

    private static final int ALL_POINTS = -1;
    private static final int DEFAULT_PORT = -1;

    private final LXFixture fixture;
    private final JsonProtocolDefinition protocol;
    private final JsonTransportDefinition transport;
    private final JsonByteEncoderDefinition byteEncoder;
    private final InetAddress address;
    private final int port;
    private final int universe;
    private final int channel;
    private final int priority;
    private final boolean sequenceEnabled;
    private final KinetDatagram.Version kinetVersion;
    private final float fps;
    private final List<JsonSegmentDefinition> segments;

    private JsonOutputDefinition(LXFixture fixture, JsonProtocolDefinition protocol, JsonTransportDefinition transport, JsonByteEncoderDefinition byteOrder, InetAddress address, int port, int universe, int channel, int priority, boolean sequenceEnabled, KinetDatagram.Version kinetVersion, float fps, List<JsonSegmentDefinition> segments) {
      this.fixture = fixture;
      this.protocol = protocol;
      this.transport = transport;
      this.byteEncoder = byteOrder;
      this.address = address;
      this.port = port;
      this.universe = universe;
      this.channel = channel;
      this.priority = priority;
      this.sequenceEnabled = sequenceEnabled;
      this.kinetVersion = kinetVersion;
      this.fps = fps;
      this.segments = segments;
    }

  }

  private class JsonSegmentDefinition {
    private final int start;
    private final int num;
    private final int stride;
    private final int repeat;
    private final int padPre;
    private final int padPost;
    private final byte[] headerBytes;
    private final byte[] footerBytes;
    private final boolean reverse;

    // May or may not be specified, if null then the parent output definition is used
    private final JsonByteEncoderDefinition byteEncoder;

    private JsonSegmentDefinition(int start, int num, int stride, int repeat, int padPre, int padPost, boolean reverse, JsonByteEncoderDefinition byteEncoder, byte[] headerBytes, byte[] footerBytes) {
      this.start = start;
      this.num = num;
      this.stride = stride;
      this.repeat = repeat;
      this.reverse = reverse;
      this.padPre = padPre;
      this.padPost = padPost;
      this.byteEncoder = byteEncoder;
      this.headerBytes = headerBytes;
      this.footerBytes = footerBytes;
    }
  }

  /**
   * Fixture type parameter stores the file name, without the .lxf suffix
   */
  private final StringParameter fixtureType =
    new StringParameter("Fixture File")
    .setDescription("Fixture definition path");

  public final BooleanParameter error =
    new BooleanParameter("Error", false)
    .setDescription("Whether there was an error loading this fixture");

  public final StringParameter errorMessage =
    new StringParameter("Error Message", "")
    .setDescription("Message describing the error from loading");

  public final BooleanParameter warning =
    new BooleanParameter("Warning", false)
    .setDescription("Whether there are warnings from the loading of the JSON file");

  public final MutableParameter parametersDisposed = (MutableParameter)
    new MutableParameter("Dispose")
    .setDescription("Monitor for when fixture parameters are disposed");

  public final MutableParameter parametersReloaded = (MutableParameter)
    new MutableParameter("Reload")
    .setDescription("Monitor for when fixture parameters are reloaded");

  public final List<String> warnings = new CopyOnWriteArrayList<String>();

  private final List<JsonOutputDefinition> definedOutputs = new ArrayList<JsonOutputDefinition>();

  private final Map<String, List<LXFixture>> componentsById = new HashMap<String, List<LXFixture>>();
  private final List<List<LXFixture>> componentsByIndex = new ArrayList<List<LXFixture>>();

  private final LinkedHashMap<String, ParameterDefinition> definedParameters = new LinkedHashMap<String, ParameterDefinition>();
  private final LinkedHashMap<String, ParameterDefinition> reloadParameterValues = new LinkedHashMap<String, ParameterDefinition>();

  // Context in which parameter values are looked up. Typically this is from the fixture itself, but in the case of
  // a sub-fixture, parameter values may come from our parent
  private final JsonFixture jsonParameterContext;

  // Flag to indicate if this is a subfixture of a parent JSON fixture
  private final boolean isJsonSubfixture;

  // Dictionary of values for local parameters (not the parent)
  private JsonObject jsonParameterValues = new JsonObject();

  private int currentNumInstances = -1;
  private int currentChildInstance = -1;

  public JsonFixture(LX lx) {
    this(lx, null);
  }

  public JsonFixture(LX lx, String fixtureType) {
    super(lx, LABEL_PLACEHOLDER);
    this.isJsonSubfixture = false;
    this.jsonParameterContext = this;
    addParameter("fixtureType", this.fixtureType);
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
    this.fixtureType.setValue(fixtureType);
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

  public String getFixturePath() {
    String name = this.fixtureType.getString();
    if (!LXUtils.isEmpty(name)) {
      return name + ".lxf";
    }
    return "";
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
    this.parametersDisposed.bang();

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

    this.definedOutputs.clear();

    // Clear metadata
    this.metaData.clear();

    // Clear the children
    for (LXFixture child : this.children) {
      LX.dispose(child);
    }
    this.mutableChildren.clear();
    this.componentsById.clear();
    this.componentsByIndex.clear();

    // Clear transforms
    clearTransforms();

    this.isLoaded = false;
    loadFixture(reloadParameters);
    regenerate();
  }

  private File getFixtureFile(String fixtureType) {
    return this.lx.getMediaFile(LX.Media.FIXTURES, fixtureType.replace(PATH_SEPARATOR, File.separator) + ".lxf", false);
  }

  private void loadFixture(boolean loadParameters) {
    if (this.isLoaded) {
      LX.error(new Exception(), "Trying to load JsonFixture twice, why?");
      return;
    }
    this.isLoaded = true;

    final String fixtureType = this.fixtureType.getString();
    final File fixtureFile = getFixtureFile(fixtureType);
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
        loadTags(this, obj, true, true, false);
        loadParameters(obj);
        this.parametersReloaded.bang();
      }

      // Load transforms
      loadTransforms(this, obj);

      // Keeping around for legacy support, but these should all now be a part of
      // the components loading flow
      loadLegacyPoints(obj);
      loadLegacyStrips(obj);
      loadLegacyArcs(obj);
      loadLegacyChildren(obj);

      // Load children of all dynamic types!
      loadComponents(obj);

      // Metadata for this entire fixture
      loadMetaData(obj, this.metaData);

      // Top level outputs on the entire fixture
      loadOutputs(this, obj);

    } catch (JsonParseException jpx) {
      String message = jpx.getLocalizedMessage();
      Throwable cause = jpx.getCause();
      if (cause instanceof MalformedJsonException) {
        message = "Invalid JSON in " + fixtureFile.getName() + ": " + ((MalformedJsonException)cause).getLocalizedMessage();
      }
      setError(jpx, message);
      setErrorLabel(fixtureType);
    } catch (Exception x) {
      setError(x, "Error loading fixture from " + fixtureFile.getName() + ": " + x.getLocalizedMessage());
      setErrorLabel(fixtureType);
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
    if (warning == null) {
      return;
    }
    this.warnings.add(warning);
    if (this.warning.isOn()) {
      this.warning.bang();
    } else {
      this.warning.setValue(true);
    }
    LX.error("Fixture " + this.fixtureType.getString() + ".lxf: " + warning);
  }

  private void warnDuplicateKeys(JsonObject obj, String ... keys) {
    String found = null;
    for (String key : keys) {
      if (obj.has(key)) {
        if (found != null) {
          addWarning("Should use only one of " + found + " or " + key + " - " + found + " will be ignored.");
        }
        found = key;
      }
    }
  }

  private static final Pattern parameterPattern = Pattern.compile("\\$\\{?([a-zA-Z0-9]+)\\}?");

  private String replaceVariables(String key, String expression, ParameterType returnType) {
    StringBuilder result = new StringBuilder();
    int index = 0;
    Matcher matcher = parameterPattern.matcher(expression);
    while (matcher.find()) {
      String parameterName = matcher.group(1);
      String parameterValue = "";

      if (KEY_INSTANCE.equals(parameterName)) {
        if (returnType == ParameterType.BOOLEAN) {
          addWarning("Cannot load non-boolean parameter $" + parameterName + " into a boolean type: " + key);
          return null;
        }
        if (this.currentChildInstance < 0) {
          addWarning("Cannot reference variable $" + parameterName + " when \"" + KEY_INSTANCES + "\" has not been declared");
          return null;
        }
        parameterValue = String.valueOf(this.currentChildInstance);
      } else if (KEY_INSTANCES.equals(parameterName)) {
        parameterValue = String.valueOf(this.currentNumInstances);
        if (returnType == ParameterType.BOOLEAN) {
          addWarning("Cannot load non-boolean parameter $" + parameterName + " into a boolean type: " + key);
          return null;
        }
      } else {
        ParameterDefinition parameter = this.definedParameters.get(parameterName);
        if (parameter == null) {
          addWarning("Illegal reference in " + key + ", there is no parameter: " + parameterName);
          return null;
        }
        parameter.isReferenced = true;

        switch (returnType) {
        case FLOAT:
          if (parameter.type == ParameterType.FLOAT || parameter.type == ParameterType.INT) {
            parameterValue = String.valueOf(parameter.parameter.getValue());
          } else {
            addWarning("Cannot load non-numeric parameter $" + parameterName + " into a float type: " + key);
            return null;
          }
          break;
        case INT:
          if (parameter.type == ParameterType.INT) {
            parameterValue = String.valueOf(parameter.intParameter.getValuei());
          } else if (parameter.type == ParameterType.FLOAT) {
            parameterValue = String.valueOf(parameter.floatParameter.getValue());
          } else {
            addWarning("Cannot load non-numeric parameter $" + parameterName + " into an integer type: " + key);
            return null;
          }
          break;
        case STRING:
        case STRING_SELECT:
          parameterValue = parameter.getValueAsString();
          break;
        case BOOLEAN:
          if (parameter.type == ParameterType.BOOLEAN) {
            parameterValue = String.valueOf(parameter.booleanParameter.isOn());
          } else {
            addWarning("Cannot load non-boolean parameter $" + parameterName + " into a boolean type: " + key);
            return null;
          }
          break;
        }
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
      float value = _evaluateSimpleExpression(obj, key, substitutedExpression.replaceAll("\\s", ""));
      if (Float.isNaN(value)) {
        addWarning("Variable expression produces NaN: " + expression);
        return 0;
      }
      if (Float.isInfinite(value)) {
        addWarning("Variable expression produces infinite value: " + expression);
        return 0;
      }
      return value;
    } catch (Exception nfx) {
      addWarning("Bad formatting in variable expression: " + expression);
      nfx.printStackTrace();
      return 0;
    }
  }

  // 2D array of operators by precedence (low to high)
  private final static char[][] SIMPLE_EXPRESSION_OPERATORS = {
    { '+', '-' },
    { '*', '/', '%' },
    { '^' }
  };


  private enum SimpleFunction {
    sin(f -> { return (float) Math.sin(Math.toRadians(f)); }),
    cos(f -> { return (float) Math.cos(Math.toRadians(f)); }),
    tan(f -> { return (float) Math.tan(Math.toRadians(f)); }),
    asin(f -> { return (float) Math.toDegrees(Math.asin(f)); }),
    acos(f -> { return (float) Math.toDegrees(Math.acos(f)); }),
    atan(f -> { return (float) Math.toDegrees(Math.atan(f)); }),
    deg(f -> { return (float) Math.toDegrees(f); }),
    rad(f -> { return (float) Math.toRadians(f); }),
    abs(f -> { return Math.abs(f); }),
    sqrt(f -> { return (float) Math.sqrt(f); });

    private interface Compute {
      public float compute(float f);
    }

    private final Compute compute;

    private SimpleFunction(Compute compute) {
      this.compute = compute;
    }

  }

  private static boolean isOperator(char ch, char[] operators) {
    for (char operator : operators) {
      if (ch == operator) {
        return true;
      }
    }
    return false;
  }

  private static boolean isSimpleOperator(char ch) {
    for (char[] operators : SIMPLE_EXPRESSION_OPERATORS) {
      if (isOperator(ch, operators)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isUnaryMinus(char[] chars, int index) {
    // Check it's actually a minus
    if (chars[index] != '-') {
      return false;
    }

    // Check if preceded by another simple operator, e.g. 4+-4
    if (isSimpleOperator(chars[index-1])) {
      return true;
    }
    // Check if preceded by a simple function token, which will no longer have
    // parentheses, e.g. sin(-4) will have become sin-4 after parenthetical resolution
    for (SimpleFunction function : SimpleFunction.values()) {
      final String name = function.name();
      final int len = name.length();
      if ((index >= len) && new String(chars, index-len, len).equals(name)) {
        return true;
      }
    }
    return false;
  }

  // Super-trivial hacked up implementation of *very* basic math expressions, which has now
  // got some functions tacked on. If this slippery slope keeps sliding will need to get a
  // real expression parsing + evaluation library involved at some point...
  private float _evaluateSimpleExpression(JsonObject obj, String key, String expression) {
    char[] chars = expression.toCharArray();

    // Parentheses pass
    int openParen = -1;
    for (int i = 0; i < chars.length; ++i) {
      if (chars[i] == '(') {
        openParen = i;
      } else if (chars[i] == ')') {
        if (openParen < 0) {
          throw new IllegalArgumentException("Mismatched parentheses in expression: " + expression);
        }

        // Whenever we find a closed paren, evaluate just this one parenthetical.
        // This will naturally work from in->out on nesting, since every closed-paren
        // catches the open-paren that was closest to it.
        String substitutedExpression =
          // Expression to the left of parens (maybe empty)
          expression.substring(0, openParen) +
          // Evaluation of what's inside the parens
          _evaluateSimpleExpression(obj, key, expression.substring(openParen+1, i)) +
          // Expression to right of parens (maybe empty)
          expression.substring(i + 1);

        return _evaluateSimpleExpression(obj, key, substitutedExpression);
      }
    }

    // All parentheses have now been cleared!

    // Operator pass - these are prioritized by precedence and are left-to-right associative
    for (char[] operators : SIMPLE_EXPRESSION_OPERATORS) {
      for (int index = chars.length - 2; index > 0; --index) {
        if (isOperator(chars[index], operators)) {

          // Skip over the tricky unary minus operator! If preceded by another operator,
          // then it's actually just a negative sign which will be handled below. Do not
          // process it now as subtraction.
          if (isUnaryMinus(chars, index)) {
            continue;
          }

          final float left = _evaluateSimpleExpression(obj, key, expression.substring(0, index));
          final float right = _evaluateSimpleExpression(obj, key, expression.substring(index + 1));

          switch (chars[index]) {
          case '+': return left + right;
          case '-': return left - right;
          case '*': return left * right;
          case '/': return left / right;
          case '%': return left % right;
          case '^': return (float) Math.pow(left, right);
          }
        }
      }
    }

    // The dreaded nasty unary minus operator!
    if (chars[0] == '-') {
      // Float.parseFloat() would handle one of these fine, but it won't handle
      // them potentially stacking up at the front, e.g. if multiple expression
      // resolutions have resulted in something like ---4, so do the negations
      // manually one by one
      return -_evaluateSimpleExpression(obj, key, expression.substring(1));
    }

    // Check for simple function operators
    for (SimpleFunction function : SimpleFunction.values()) {
      final String name = function.name();
      if (expression.startsWith(name)) {
        return function.compute.compute(_evaluateSimpleExpression(obj, key, expression.substring(name.length())));
      }
    }

    // All clear, this *should* just be a number now (if not, syntax was bad)
    return Float.parseFloat(expression);
  }

  private final static char[] SIMPLE_BOOLEAN_OPERATORS = { '|', '&' };

  private boolean evaluateBooleanExpression(JsonObject obj, String key, String expression) {
    String substitutedExpression = replaceVariables(key, expression, ParameterType.BOOLEAN);
    if (substitutedExpression == null) {
      return false;
    }
    try {
      return _evaluateBooleanExpression(obj, key, substitutedExpression);
    } catch (Exception x) {
      addWarning("Bad formatting in boolean expression: " + expression);
      x.printStackTrace();
      return false;
    }
  }

  // Super-trivial implementation of *very* basic boolean expressions
  private boolean _evaluateBooleanExpression(JsonObject obj, String key, String expression) {
    // Parentheses pass
    char[] chars = expression.toCharArray();
    int openParen = -1;
    for (int i = 0; i < chars.length; ++i) {
      if (chars[i] == '(') {
        openParen = i;
      } else if (chars[i] == ')') {
        if (openParen < 0) {
          throw new IllegalArgumentException("Mismatched parentheses in expression: " + expression);
        }

        // Whenever we find a closed paren, evaluate just this one parenthetical.
        // This will naturally work from in->out on nesting, since every closed-paren
        // catches the open-paren that was closest to it.
        String substitutedExpression =
          // Expression to the left of parens (maybe empty)
          expression.substring(0, openParen) +
          // Evaluation of what's inside the parens
          _evaluateBooleanExpression(obj, key, expression.substring(openParen+1, i)) +
          // Expression to right of parens (maybe empty)
          expression.substring(i + 1);

        return _evaluateBooleanExpression(obj, key, substitutedExpression);
      }
    }

    // Operator pass - these are prioritized so that & takes precedence over |
    for (char operator : SIMPLE_BOOLEAN_OPERATORS) {
      int index = expression.indexOf(operator);
      if ((index > 0) && (index < expression.length() - 1)) {
        boolean left = _evaluateBooleanExpression(obj, key, expression.substring(0, index));
        boolean right = _evaluateBooleanExpression(obj, key, expression.substring(index + 1));
        switch (operator) {
        case '&': return left && right;
        case '|': return left || right;
        }
      }
    }

    // Check for any '!' operators!
    String trimmed = expression.trim();
    if (!trimmed.isEmpty() && (trimmed.charAt(0) == '!')) {
      return !_evaluateBooleanExpression(obj, key, trimmed.substring(1));
    }

    // Okay just parse it!
    return Boolean.parseBoolean(trimmed);
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
          return evaluateBooleanExpression(obj, key, boolPrimitive.getAsString());
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

  private void loadGeometry(LXFixture fixture, JsonObject obj) {
    loadTransforms(fixture, obj);
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

  private void loadTransforms(LXFixture fixture, JsonObject obj) {
    final JsonArray transformsArr = loadArray(obj, KEY_TRANSFORMS, KEY_TRANSFORMS + " must be an array");
    if (transformsArr == null) {
      return;
    }
    for (JsonElement transformElem : transformsArr) {
      if (transformElem.isJsonObject()) {
        loadTransform(fixture, transformElem.getAsJsonObject());
      } else if (!transformElem.isJsonNull()) {
        addWarning(KEY_TRANSFORMS + " should only contain transform elements in JSON object format, found invalid: " + transformElem);
      }
    }
  }

  private static final String[] TRANSFORM_TRANSLATE = { KEY_X, KEY_Y, KEY_Z };
  private static final String[] TRANSFORM_ROTATE = { KEY_YAW, KEY_PITCH, KEY_ROLL, KEY_ROTATE_X, KEY_ROTATE_Y, KEY_ROTATE_Z };
  private static final String[] TRANSFORM_SCALE = { KEY_SCALE, KEY_SCALE_X, KEY_SCALE_Y, KEY_SCALE_Z };

  private static final boolean isTransform(JsonObject obj, String[] keys) {
    for (String key : keys) {
      if (obj.has(key)) {
        return true;
      }
    }
    return false;
  }

  private void loadTransform(LXFixture fixture, JsonObject obj) {
    if (obj.has(KEY_ENABLED)) {
      boolean enabled = loadBoolean(obj, KEY_ENABLED, true, "Transform must specify boolean expression for " + KEY_ENABLED);
      if (!enabled) {
        return;
      }
    }

    // Check there are not multiple rotations specified
    int rotateCount = 0;
    for (String key : TRANSFORM_ROTATE) {
      if (obj.has(key)) {
        ++rotateCount;
      }
    }
    if (rotateCount > 1) {
      addWarning("Transform may not contain multiple rotations: " + obj);
      return;
    }

    final boolean isTranslate = isTransform(obj, TRANSFORM_TRANSLATE);
    final boolean isScale = isTransform(obj, TRANSFORM_SCALE);
    final boolean isRotate = rotateCount > 0;

    if (isTranslate) {
      if (isRotate) {
        addWarning("Transform may not contain both translation and rotation: " + obj);
        return;
      }
      if (isScale) {
        addWarning("Transform may not contain both translation and scaling: " + obj);
        return;
      }
    } else if (isRotate) {
      if (isScale) {
        addWarning("Transform may not contain both rotation and scaling: " + obj);
        return;
      }
    }

    // All clear at this point, read the values
    if (obj.has(KEY_X)) {
      fixture.addTransform(new Transform(Transform.Type.TRANSLATE_X, loadFloat(obj, KEY_X, true)));
    }
    if (obj.has(KEY_Y)) {
      fixture.addTransform(new Transform(Transform.Type.TRANSLATE_Y, loadFloat(obj, KEY_Y, true)));
    }
    if (obj.has(KEY_Z)) {
      fixture.addTransform(new Transform(Transform.Type.TRANSLATE_Z, loadFloat(obj, KEY_Z, true)));
    }

    if (obj.has(KEY_YAW)) {
      fixture.addTransform(new Transform(Transform.Type.ROTATE_Y, loadFloat(obj, KEY_YAW, true)));
    }
    if (obj.has(KEY_PITCH)) {
      fixture.addTransform(new Transform(Transform.Type.ROTATE_X, loadFloat(obj, KEY_PITCH, true)));
    }
    if (obj.has(KEY_ROLL)) {
      fixture.addTransform(new Transform(Transform.Type.ROTATE_Z, loadFloat(obj, KEY_ROLL, true)));
    }
    if (obj.has(KEY_ROTATE_X)) {
      fixture.addTransform(new Transform(Transform.Type.ROTATE_X, loadFloat(obj, KEY_ROTATE_X, true)));
    }
    if (obj.has(KEY_ROTATE_Y)) {
      fixture.addTransform(new Transform(Transform.Type.ROTATE_Y, loadFloat(obj, KEY_ROTATE_Y, true)));
    }
    if (obj.has(KEY_ROTATE_Z)) {
      fixture.addTransform(new Transform(Transform.Type.ROTATE_Z, loadFloat(obj, KEY_ROTATE_Z, true)));
    }

    if (obj.has(KEY_SCALE)) {
      JsonElement scaleElem = obj.get(KEY_SCALE);
      if (scaleElem.isJsonObject()) {
        JsonObject scale = scaleElem.getAsJsonObject();
        if (scale.has(KEY_X)) {
          fixture.addTransform(new Transform(Transform.Type.SCALE_X, loadFloat(scale, KEY_X, true)));
        }
        if (scale.has(KEY_Y)) {
          fixture.addTransform(new Transform(Transform.Type.SCALE_Y, loadFloat(scale, KEY_Y, true)));
        }
        if (scale.has(KEY_Z)) {
          fixture.addTransform(new Transform(Transform.Type.SCALE_Z, loadFloat(scale, KEY_Z, true)));
        }
      } else if (scaleElem.isJsonPrimitive()) {
        final float scale = loadFloat(obj, KEY_SCALE, true);
        fixture.addTransform(new Transform(Transform.Type.SCALE, scale));
      } else {
        addWarning("Transform element " + KEY_SCALE + " must be a float or JSON object: " + scaleElem);
        return;
      }
    }
    if (obj.has(KEY_SCALE_X)) {
      fixture.addTransform(new Transform(Transform.Type.SCALE_X, loadFloat(obj, KEY_SCALE_X, true)));
    }
    if (obj.has(KEY_SCALE_Y)) {
      fixture.addTransform(new Transform(Transform.Type.SCALE_Y, loadFloat(obj, KEY_SCALE_Y, true)));
    }
    if (obj.has(KEY_SCALE_Z)) {
      fixture.addTransform(new Transform(Transform.Type.SCALE_Z, loadFloat(obj, KEY_SCALE_Z, true)));
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

  private void setErrorLabel(String fixtureType) {
    if (this.label.getString().equals(LABEL_PLACEHOLDER)) {
      int lastSeparator = fixtureType.lastIndexOf(PATH_SEPARATOR);
      if (lastSeparator >= 0) {
        fixtureType = fixtureType.substring(lastSeparator+1);
      }
      this.label.setValue(fixtureType);
    }
  }

  private void loadTags(LXFixture fixture, JsonObject obj, boolean required, boolean includeParent, boolean replaceVariables) {
    List<String> validTags = _loadTags(obj, required, replaceVariables, this);
    if (includeParent) {
      for (String tag : _loadTags(this.jsonParameterValues, false, true, this.jsonParameterContext)) {
        if (validTags.contains(tag)) {
          addWarning("Parent JSON fixture redundantly specifies tag: " + tag);
        } else {
          validTags.add(tag);
        }
      }
    }
    fixture.setTags(validTags);
  }

  private List<String> _loadTags(JsonObject obj, boolean required, boolean replaceVariables, JsonFixture variableContext) {
    warnDuplicateKeys(obj, KEY_MODEL_KEY, KEY_MODEL_KEYS, KEY_TAG, KEY_TAGS);
    String keyTags = obj.has(KEY_TAGS) ? KEY_TAGS : KEY_MODEL_KEYS;
    String keyTag = obj.has(KEY_TAG) ? KEY_TAG : KEY_MODEL_KEY;
    List<String> validTags = new ArrayList<String>();

    if (obj.has(KEY_MODEL_KEY) || obj.has(KEY_MODEL_KEYS)) {
      addWarning(KEY_MODEL_KEY + "/" + KEY_MODEL_KEYS + " are deprecated, please update to " + KEY_TAG + "/" + KEY_TAGS);
    }

    if (obj.has(keyTags)) {
      JsonArray tagsArr = loadArray(obj, keyTags);
      for (JsonElement tagElem : tagsArr) {
        if (!tagElem.isJsonPrimitive() || !tagElem.getAsJsonPrimitive().isString()) {
          addWarning(keyTags + " may only contain strings");
        } else {
          String tag = tagElem.getAsString().trim();
          if (replaceVariables) {
            tag = variableContext.replaceVariables(keyTags, tag, ParameterType.STRING);
          }
          if (tag == null || tag.isEmpty()) {
            addWarning(keyTags + " should not contain empty string values");
          } else if (!LXModel.Tag.isValid(tag)) {
            addWarning("Ignoring invalid tag, should only contain [A-Za-z0-9_.-]: " + tag);
          } else {
            validTags.add(tag);
          }
        }
      }
    } else if (obj.has(keyTag)) {
      String tag = loadString(obj, keyTag, false, keyTag + " should contain a single string value");
      if (tag != null) {
        tag = tag.trim();
        if (replaceVariables) {
          tag = variableContext.replaceVariables(keyTag, tag, ParameterType.STRING);
        }
        if (tag == null || tag.isEmpty()) {
          addWarning(keyTag + " must contain a non-empty string value");
        } else if (!LXModel.Tag.isValid(tag)) {
          addWarning("Ignoring invalid tag, should only contain [A-Za-z0-9_.-]: " + tag);
        } else {
          validTags.add(tag);
        }
      }
    } else if (required) {
      addWarning("Fixture definition must specify one of " + KEY_TAG + "/" + KEY_TAGS);
    }

    return validTags;
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
      if (parameterName.equals(KEY_INSTANCES) || parameterName.equals(KEY_INSTANCE)) {
        addWarning("Invalid parameter name, keyword is reserved: " + parameterName);
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

      // Ensure default value is present
      if (!parameterObj.has(KEY_PARAMETER_DEFAULT)) {
        addWarning("Parameter " + parameterName + " must specify " + KEY_PARAMETER_DEFAULT);
        continue;
      }
      JsonElement defaultElem = parameterObj.get(KEY_PARAMETER_DEFAULT);
      if (!defaultElem.isJsonPrimitive()) {
        addWarning("Parameter " + parameterName + " must specify primitive value for " + KEY_PARAMETER_DEFAULT);
        continue;
      }

      String typeStr = loadString(parameterObj, KEY_PARAMETER_TYPE, false, "Parameter " + parameterName + " must specify valid type string");;
      ParameterType type = ParameterType.get(typeStr);
      if (type == null) {
        addWarning("Parameter " + parameterName + " must specify valid type string");
        continue;
      }
      if (type == ParameterType.STRING) {
        // Check for the string select type when options are present
        if (parameterObj.has(KEY_PARAMETER_OPTIONS)) {
          type = ParameterType.STRING_SELECT;
        }
      }

      ParameterDefinition reloadDefinition = this.reloadParameterValues.get(parameterName);
      if ((reloadDefinition != null) && (reloadDefinition.type != type)) {
        reloadDefinition = null;
      }

      switch (type) {
      case FLOAT:
        final float defaultFloat = defaultElem.getAsFloat();
        float floatValue = defaultFloat;
        if (this.jsonParameterValues.has(parameterName)) {
          floatValue = this.jsonParameterContext.loadFloat(this.jsonParameterValues, parameterName, true);
        } else if (reloadDefinition != null) {
          floatValue = reloadDefinition.floatParameter.getValuef();
        }
        float minFloat = -Float.MAX_VALUE;
        float maxFloat = Float.MAX_VALUE;
        if (parameterObj.has(KEY_PARAMETER_MIN)) {
          minFloat = loadFloat (parameterObj, KEY_PARAMETER_MIN, false, "Parameter min value must be a float");
        }
        if (parameterObj.has(KEY_PARAMETER_MAX)) {
          maxFloat = loadFloat(parameterObj, KEY_PARAMETER_MAX, false, "Parameter min value must be a float");
        }
        if (minFloat > maxFloat) {
          addWarning("Parameter minimum may not be greater than maximum: " + minFloat + ">" + maxFloat);
          break;
        }
        addJsonParameter(new ParameterDefinition(parameterName, parameterLabel, parameterDescription, floatValue, defaultFloat, minFloat, maxFloat));
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
        final int defaultInt = defaultElem.getAsInt();
        int intValue = defaultInt;
        if (minInt > maxInt) {
          addWarning("Parameter minimum may not be greater than maximum: " + minInt + ">" + maxInt);
          break;
        }
        if (this.jsonParameterValues.has(parameterName)) {
          intValue = this.jsonParameterContext.loadInt(this.jsonParameterValues, parameterName, true, "Child parameter should be an int: " + parameterName);
        } else if (reloadDefinition != null) {
          intValue = LXUtils.constrain(reloadDefinition.intParameter.getValuei(), minInt, maxInt);
        }
        addJsonParameter(new ParameterDefinition(parameterName, parameterLabel, parameterDescription, intValue, defaultInt, minInt, maxInt));
        break;
      case STRING:
        final String defaultString = defaultElem.getAsString();
        String stringValue = defaultString;
        if (this.jsonParameterValues.has(parameterName)) {
          stringValue = this.jsonParameterContext.loadString(this.jsonParameterValues, parameterName, true, "Child parameter should be an string: " + parameterName);
        } else if (reloadDefinition != null) {
          stringValue = reloadDefinition.stringParameter.getString();
        }
        addJsonParameter(new ParameterDefinition(parameterName, parameterLabel, parameterDescription, stringValue, defaultString));
        break;
      case STRING_SELECT:
        final String defaultStringSelect = defaultElem.getAsString();
        String stringSelectValue = defaultStringSelect;
        if (this.jsonParameterValues.has(parameterName)) {
          stringSelectValue = this.jsonParameterContext.loadString(this.jsonParameterValues, parameterName, true, "Child parameter should be an string: " + parameterName);
        } else if (reloadDefinition != null) {
          stringSelectValue = reloadDefinition.stringSelectParameter.getObject();
        }
        List<String> stringOptions = new ArrayList<String>();
        JsonArray optionsArray = loadArray(parameterObj, KEY_PARAMETER_OPTIONS);
        for (JsonElement optionElem : optionsArray) {
          if (optionElem.isJsonPrimitive()) {
            stringOptions.add(optionElem.getAsString());
          } else {
            addWarning(KEY_PARAMETER_OPTIONS + " should only string options");
          }
        }
        if (stringOptions.isEmpty()) {
          addWarning(KEY_PARAMETER_OPTIONS + " must not be empty");
          break;
        } else if (!stringOptions.contains(stringSelectValue)) {
          addWarning(KEY_PARAMETER_OPTIONS + " must contain default value " + stringSelectValue);
          stringValue = stringOptions.get(0);
        }
        addJsonParameter(new ParameterDefinition(parameterName, parameterLabel, parameterDescription, stringSelectValue, defaultStringSelect, stringOptions));
        break;
      case BOOLEAN:
        final boolean defaultBoolean = defaultElem.getAsBoolean();
        boolean booleanValue = defaultBoolean;
        if (this.jsonParameterValues.has(parameterName)) {
          booleanValue = this.jsonParameterContext.loadBoolean(this.jsonParameterValues, parameterName, true, "Child parameter should be a boolean: " + parameterName);
        } else if (reloadDefinition != null) {
          booleanValue = reloadDefinition.booleanParameter.isOn();
        }
        addJsonParameter(new ParameterDefinition(parameterName, parameterLabel, parameterDescription, booleanValue, defaultBoolean));
        break;
      }

    }
  }

  @Deprecated
  private void loadLegacyPoints(JsonObject obj) {
    JsonArray pointsArr = loadArray(obj, KEY_POINTS);
    if (pointsArr == null) {
      return;
    }
    addWarning(KEY_POINTS + " is deprecated. Define an element of type " + TYPE_POINTS + " in the " + KEY_COMPONENTS + " array");
    for (JsonElement pointElem : pointsArr) {
      if (pointElem.isJsonObject()) {
        loadChild(pointElem.getAsJsonObject(), ChildType.POINT, null);
      } else if (!pointElem.isJsonNull()) {
        addWarning(KEY_POINTS + " should only contain point elements in JSON object format, found invalid: " + pointElem);
      }
    }
  }

  @Deprecated
  private void loadLegacyStrips(JsonObject obj) {
    JsonArray stripsArr = loadArray(obj, KEY_STRIPS);
    if (stripsArr == null) {
      return;
    }
    addWarning(KEY_STRIPS + " is deprecated. Define elements of type " + TYPE_STRIP +" in the " + KEY_COMPONENTS + " array");
    for (JsonElement stripElem : stripsArr) {
      if (stripElem.isJsonObject()) {
        loadChild(stripElem.getAsJsonObject(), ChildType.STRIP, null);
      } else if (!stripElem.isJsonNull()) {
        addWarning(KEY_STRIPS + " should only contain strip elements in JSON object format, found invalid: " + stripElem);
      }
    }
  }

  @Deprecated
  private void loadLegacyArcs(JsonObject obj) {
    JsonArray arcsArr = loadArray(obj, KEY_ARCS);
    if (arcsArr == null) {
      return;
    }
    addWarning(KEY_ARCS + " is deprecated. Define elements of type " + TYPE_ARC + " in the " + KEY_COMPONENTS + " array");
    for (JsonElement arcElem : arcsArr) {
      if (arcElem.isJsonObject()) {
        loadChild(arcElem.getAsJsonObject(), ChildType.ARC, null);
      } else if (!arcElem.isJsonNull()) {
        addWarning(KEY_ARCS + " should only contain arc elements in JSON object format, found invalid: " + arcElem);
      }
    }
  }

  @Deprecated
  private void loadLegacyChildren(JsonObject obj) {
    JsonArray childrenArr = loadArray(obj, KEY_CHILDREN);
    if (childrenArr == null) {
      return;
    }
    addWarning(KEY_CHILDREN + " is deprecated. Define elements of specific type in the " + KEY_COMPONENTS + " array");
    for (JsonElement childElem : childrenArr) {
      if (childElem.isJsonObject()) {
        loadChild(childElem.getAsJsonObject());
      } else if (!childElem.isJsonNull()) {
        addWarning(KEY_CHILDREN + " should only contain child elements in JSON object format, found invalid: " + childElem);
      }
    }
  }

  private PointListFixture loadPoints(JsonObject pointsObj) {
    JsonArray coordsArr = loadArray(pointsObj, KEY_COORDINATES);
    if (coordsArr == null) {
      addWarning("Points must specify " + KEY_COORDINATES);
      return null;
    }
    List<LXVector> coords = new ArrayList<LXVector>();
    for (JsonElement coordElem : coordsArr) {
      if (coordElem.isJsonObject()) {
        coords.add(loadVector(coordElem.getAsJsonObject(), "Coordinate should specify at least one x/y/z value"));
      } else if (!coordElem.isJsonNull()) {
        addWarning(KEY_COORDINATES + " should only contain point elements in JSON object format, found invalid: " + coordElem);
      }
    }
    if (coords.isEmpty()) {
      addWarning("Points must specify non-empty array of " + KEY_COORDINATES);
      return null;
    }

    return new PointListFixture(this.lx, coords);
  }

  private StripFixture loadStrip(JsonObject stripObj) {
    if (!stripObj.has(KEY_NUM_POINTS)) {
      addWarning("Strip must specify " + KEY_NUM_POINTS);
      return null;
    }
    int numPoints = loadInt(stripObj, KEY_NUM_POINTS, true, "Strip must specify a positive integer for " + KEY_NUM_POINTS);
    if (numPoints <= 0) {
      addWarning("Strip must specify positive integer value for " + KEY_NUM_POINTS);
      return null;
    }
    if (numPoints > StripFixture.MAX_POINTS) {
      addWarning("Single strip may not define more than " + StripFixture.MAX_POINTS + " points, tried to define " + numPoints);
      return null;
    }

    // Make strip with number of points
    StripFixture strip = new StripFixture(this.lx);
    strip.numPoints.setValue(numPoints);

    // Strip spacing default to 1
    float spacing = 1f;

    // Load the strip direction if specified (which implies a spacing, but spacing can be overridden explicitly)
    if (stripObj.has(KEY_DIRECTION)) {
      if (stripObj.has(KEY_END)) {
        addWarning("Strip object should not specify both " + KEY_DIRECTION + " and " + KEY_END + ", ignoring " + KEY_DIRECTION);
      } else if (stripObj.has(KEY_YAW) || stripObj.has(KEY_PITCH) || stripObj.has(KEY_ROLL)) {
        addWarning("Strip object should not specify both " + KEY_DIRECTION + " and yaw/pitch/roll, ignoring " + KEY_DIRECTION);
      } else {
        JsonObject directionObj = loadObject(stripObj, KEY_DIRECTION, "Strip direction should be a vector object");
        if (directionObj != null) {
          LXVector direction = loadVector(directionObj, "Strip direction should specify at least one x/y/z value");
          if (direction.isZero()) {
            addWarning("Strip direction vector should not be all 0");
          } else {
            spacing = direction.mag();
            strip.yaw.setValue(Math.toDegrees(Math.atan2(-direction.z, direction.x)));
            strip.roll.setValue(Math.toDegrees(Math.asin(direction.y / spacing)));
            strip.pitch.setValue(0);
          }
        }
      }
    }

    // Explicit spacing specified?
    if (stripObj.has(KEY_SPACING)) {
      if (stripObj.has(KEY_END)) {
        addWarning("Strip object should not specify both " + KEY_SPACING + " and " + KEY_END + ", ignoring " + KEY_SPACING);
      } else {
        float testSpacing = loadFloat(stripObj, KEY_SPACING, true, "Strip must specify a positive " + KEY_SPACING);
        if (testSpacing >= 0) {
          spacing = testSpacing;
        } else {
          addWarning("Strip may not specify a negative spacing");
        }
      }
    }

    // End-point specified
    if (stripObj.has(KEY_END)) {
      if (stripObj.has(KEY_YAW) || stripObj.has(KEY_PITCH) || stripObj.has(KEY_ROLL)) {
        addWarning("Strip object should not specify both " + KEY_END + " and yaw/pitch/roll, ignoring " + KEY_END);
      } else {
        JsonObject endObj = loadObject(stripObj, KEY_END, "Strip " + KEY_END + " should be a vector object");
        if (endObj != null) {
          LXVector direction =
            loadVector(endObj, "Strip " + KEY_END + " should specify at least one x/y/z value")
            .sub(loadVector(stripObj, null));
          if (direction.isZero()) {
            addWarning("Strip " + KEY_END + " vector cannot match strip origin");
          } else {
            float magnitude = direction.mag();
            if (numPoints > 1) {
              spacing = magnitude / (numPoints-1);
            }
            strip.yaw.setValue(Math.toDegrees(Math.atan2(-direction.z, direction.x)));
            strip.roll.setValue(Math.toDegrees(Math.asin(direction.y / magnitude)));
            strip.pitch.setValue(0);
          }
        }
      }
    }

    strip.spacing.setValue(spacing);

    return strip;
  }

  private ArcFixture loadArc(JsonObject arcObj) {
    if (!arcObj.has(KEY_NUM_POINTS)) {
      addWarning("Arc must specify " + KEY_NUM_POINTS + ", key was not found");
      return null;
    }
    int numPoints = loadInt(arcObj, KEY_NUM_POINTS, true, "Arc must specify a positive integer for " + KEY_NUM_POINTS);
    if (numPoints <= 0) {
      addWarning("Arc must specify positive integer value for " + KEY_NUM_POINTS);
      return null;
    }
    if (numPoints > ArcFixture.MAX_POINTS) {
      addWarning("Single arc may not define more than " + ArcFixture.MAX_POINTS + " points");
      return null;
    }

    float radius = loadFloat(arcObj, KEY_RADIUS, true, "Arc must specify radius");
    if (radius <= 0) {
      addWarning("Arc must specify positive value for " + KEY_RADIUS);
      return null;
    }

    float degrees = loadFloat(arcObj, KEY_DEGREES, true, "Arc must specify number of degrees to cover");
    if (degrees <= 0) {
      addWarning("Arc must specify positive value for " + KEY_DEGREES);
      return null;
    }

    ArcFixture arc = new ArcFixture(this.lx);
    arc.numPoints.setValue(numPoints);
    arc.radius.setValue(radius);
    arc.degrees.setValue(degrees);

    // Arc position mode
    ArcFixture.PositionMode positionMode = ArcFixture.PositionMode.ORIGIN;
    if (arcObj.has(KEY_ARC_MODE)) {
      String arcMode = loadString(arcObj, KEY_ARC_MODE, true, "Arc " + KEY_ARC_MODE + " must be a string");
      if (VALUE_ARC_MODE_CENTER.equals(arcMode)) {
        positionMode = ArcFixture.PositionMode.CENTER;
      } else if (VALUE_ARC_MODE_ORIGIN.equals(arcMode)) {
        positionMode = ArcFixture.PositionMode.ORIGIN;
      } else if (arcMode != null) {
        addWarning("Arc " + KEY_ARC_MODE + " must be one of " + VALUE_ARC_MODE_CENTER + " or " + VALUE_ARC_MODE_ORIGIN + " - invalid value " + arcMode);
      }
    }
    arc.positionMode.setValue(positionMode);

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
          arc.yaw.setValue(Math.toDegrees(Math.atan2(normal.x, normal.z))); // yaw
          arc.pitch.setValue(Math.toDegrees(Math.asin(normal.y / normal.mag()))); // pitch
          arc.roll.setValue(Math.toRadians(loadFloat(arcObj, KEY_ROLL, true)));
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
          arc.yaw.setValue(Math.toDegrees(Math.atan2(-direction.z, direction.x))); // yaw
          arc.pitch.setValue(Math.toDegrees(Math.toRadians(loadFloat(arcObj, KEY_PITCH, true)))); // pitch
          arc.roll.setValue(Math.toDegrees(Math.asin(direction.y / direction.mag()))); // roll
        }
      }
    }

    return arc;
  }

  private void loadComponents(JsonObject obj) {
    JsonArray componentsArr = loadArray(obj, KEY_COMPONENTS);
    if (componentsArr == null) {
      return;
    }
    for (JsonElement componentElem : componentsArr) {
      if (componentElem.isJsonObject()) {
        // Push an element onto the index array
        this.componentsByIndex.add(null);
        loadChild(componentElem.getAsJsonObject());
      } else if (!componentElem.isJsonNull()) {
        addWarning(KEY_COMPONENTS + " should only contain child elements in JSON object format, found invalid: " + componentElem);
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

    if (childObj.has(KEY_ENABLED)) {
      boolean enabled = loadBoolean(childObj, KEY_ENABLED, true, "Child object must specify boolean expression for " + KEY_ENABLED);
      if (!enabled) {
        return;
      }
    }

    if (childObj.has(KEY_INSTANCES)) {

      int numInstances = loadInt(childObj, KEY_INSTANCES, true, "Child object must specify positive number of instances");
      if (numInstances <= 0) {
        addWarning("Child object specifies illegal number of instances: " + numInstances);
        return;
      }
      if (numInstances >= MAX_INSTANCES) {
        addWarning("Child object specifies too many instances: " + numInstances + " >= " + MAX_INSTANCES);
        return;
      }

      // Load this child N times with an instance variable set
      this.currentNumInstances = numInstances;
      for (int i = 0; i < numInstances; ++i) {
        this.currentChildInstance = i;
        JsonObject instanceObj = childObj.deepCopy();
        instanceObj.remove(KEY_INSTANCES);
        loadChild(instanceObj);
      }
      this.currentNumInstances = -1;
      this.currentChildInstance = -1;

    } else {

      if (TYPE_POINT.equals(type)) {
        loadChild(childObj, ChildType.POINT, null);
      } else if (TYPE_POINTS.equals(type)) {
        loadChild(childObj, ChildType.POINTS, null);
      } else if (TYPE_STRIP.equals(type)) {
        loadChild(childObj, ChildType.STRIP, null);
      } else if (TYPE_ARC.equals(type)) {
        loadChild(childObj, ChildType.ARC, null);
      } else {
        loadChild(childObj, ChildType.JSON, type);
      }
    }
  }

  private String getChildPrefix() {
    String fixtureType = this.fixtureType.getString();
    if (fixtureType == null || fixtureType.isEmpty()) {
      return null;
    }
    int pathIndex = fixtureType.lastIndexOf(PATH_SEPARATOR_CHAR);
    if (pathIndex > 0) {
      return fixtureType.substring(0, pathIndex);
    }
    return null;
  }

  private void loadChild(JsonObject childObj, ChildType type, String jsonType) {
    LXFixture child = null;
    switch (type) {
    case POINT:
      child = new PointFixture(this.lx);
      break;
    case POINTS:
      child = loadPoints(childObj);
      break;
    case STRIP:
      child = loadStrip(childObj);
      break;
    case ARC:
      child = loadArc(childObj);
      break;
    case JSON:
      if ((jsonType == null) || jsonType.isEmpty() || jsonType.equals(PATH_SEPARATOR)) {
        throw new IllegalArgumentException("May not create JsonFixture with null or empty type");
      }
      if (jsonType.charAt(0) == PATH_SEPARATOR_CHAR) {
        // If the provided fixture type is absolute, always go absolute
        jsonType = jsonType.substring(0);
      } else {
        // Otherwise, check first relative to the folder that we are currently in
        final String prefix = getChildPrefix();
        if (prefix != null) {
          String prefixedType = prefix + PATH_SEPARATOR + jsonType;
          File file = getFixtureFile(prefixedType);
          if (file.exists()) {
            jsonType = prefixedType;
          }
        }
      }
      JsonFixture jsonChild = new JsonFixture(this.lx, this, childObj, jsonType);
      child = jsonChild;
      if (jsonChild.error.isOn()) {
        setError(jsonChild.errorMessage.getString());
        return;
      }
      if (jsonChild.warning.isOn() ) {
        this.warnings.addAll(jsonChild.warnings);
        if (this.warning.isOn()) {
          this.warning.bang();
        } else {
          this.warning.setValue(true);
        }
      }
      break;
    }

    if (child != null) {
      // Do this for all child types
      loadGeometry(child, childObj);

      // Load child brightness
      loadBrightness(child, childObj);

      // Load tags for non-JSON child types
      if (type != ChildType.JSON) {
        loadTags(child, childObj, false, false, true);
      }

      // Load meta-data fields for the child
      loadMetaData(childObj, child.metaData);

      // Load outputs for the child
      loadOutputs(child, childObj);

      // Set child enabled
      child.enabled.setValue(this.enabled.isOn());

      // Check for an ID by which outputs can reference this
      String childId = loadString(childObj, KEY_ID, true, "Component ID must be a valid string");
      if (childId != null) {
        boolean duplicated = false;
        if (this.currentChildInstance <= 0) {
          if (this.componentsById.containsKey(childId)) {
            addWarning("Cannot duplicate component ID already in use: " + childId);
            duplicated = true;
          } else {
            // Non-instanced children are tracked here, as well as master instance 0
            List<LXFixture> children = new ArrayList<LXFixture>();
            children.add(child);
            this.componentsById.put(childId, children);
          }
        }
        // This is an instanced child, add to the list of children
        if (!duplicated && this.currentChildInstance >= 0) {
          // NB: instance 0 was added above to prime the list, only add later instances here
          if (this.currentChildInstance > 0) {
            this.componentsById.get(childId).add(child);
          }
          // Also add tracking to individual child instances
          this.componentsById.putIfAbsent(childId + "[" + this.currentChildInstance + "]", Arrays.asList(new LXFixture[] { child }));
        }
      }

      // Keep track of fixture in component list
      List<LXFixture> list = this.componentsByIndex.get(this.componentsByIndex.size() - 1);
      if (list == null) {
        list = new ArrayList<LXFixture>();
        this.componentsByIndex.set(this.componentsByIndex.size() - 1, list);
      }
      list.add(child);

      // Add child to the tree
      addChild(child, true);
    }
  }

  private void loadOutputs(LXFixture fixture, JsonObject obj) {
    if (obj.has(KEY_OUTPUT) && obj.has(KEY_OUTPUTS)) {
      addWarning("Should not have both " + KEY_OUTPUT + " and " + KEY_OUTPUTS);
    }
    JsonObject outputObj = loadObject(obj, KEY_OUTPUT, KEY_OUTPUT + " must be an output object");
    if (outputObj != null) {
      loadOutput(fixture, outputObj);
    }
    JsonArray outputsArr = loadArray(obj, KEY_OUTPUTS, KEY_OUTPUTS + " must be an array of outputs");
    if (outputsArr != null) {
      for (JsonElement outputElem : outputsArr) {
        if (outputElem.isJsonObject()) {
          loadOutput(fixture, outputElem.getAsJsonObject());
        } else if (!outputElem.isJsonNull()) {
          addWarning(KEY_OUTPUTS + " should only contain output elements in JSON object format, found invalid: " + outputElem);
        }
      }
    }
  }

  private void loadOutput(LXFixture fixture, JsonObject outputObj) {
    if (outputObj.has(KEY_ENABLED)) {
      boolean enabled = loadBoolean(outputObj, KEY_ENABLED, true, "Output field '" + KEY_ENABLED + "' must be a valid boolean expression");
      if (!enabled) {
        return;
      }
    }

    float fps = OutputDefinition.FPS_UNSPECIFIED;
    if (outputObj.has(KEY_FPS)) {
      fps = loadFloat(outputObj, KEY_FPS, true, "Output should specify valid FPS limit");
      if (fps < 0 || fps > LXOutput.MAX_FRAMES_PER_SECOND) {
        addWarning("Output FPS must be between 0-" + LXOutput.MAX_FRAMES_PER_SECOND);
        fps = OutputDefinition.FPS_UNSPECIFIED;
      }
    }

    JsonProtocolDefinition protocol = JsonProtocolDefinition.get(loadString(outputObj, KEY_PROTOCOL, true, "Output must specify a valid " + KEY_PROTOCOL));
    if (protocol == null) {
      addWarning("Output definition must define a valid protocol");
      return;
    }

    JsonTransportDefinition transport = JsonTransportDefinition.UDP;
    if (outputObj.has(KEY_TRANSPORT)) {
      if (protocol == JsonProtocolDefinition.OPC) {
        transport = JsonTransportDefinition.get(loadString(outputObj, KEY_TRANSPORT, true, "Output must specify valid transport"));
        if (transport == null) {
          transport = JsonTransportDefinition.UDP;
          addWarning("Output should define a valid transport");
        }
      } else {
        addWarning("Output transport may only be defined for OPC protocol, not " + protocol);
      }
    }

    String host = loadString(outputObj, KEY_HOST, true, "Output must specify a valid host");
    if ((host == null) || host.isEmpty()) {
      addWarning("Output must define a valid, non-empty " + KEY_HOST);
      return;
    }
    InetAddress address;
    try {
      address = InetAddress.getByName(host);
    } catch (UnknownHostException uhx) {
      addWarning("Cannot send output to invalid host: " + host);
      return;
    }

    int port = JsonOutputDefinition.DEFAULT_PORT;
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

    String universeKey = protocol.universeKey;
    int universe = loadInt(outputObj, universeKey, true, "Output " + universeKey + " must be a valid integer");
    if (universe < 0) {
      addWarning("Output " + universeKey + " may not be negative");
      return;
    }

    String channelKey = protocol.channelKey;
    int channel = loadInt(outputObj, channelKey, true, "Output " + channelKey + " must be a valid integer");
    if (channel < 0) {
      addWarning("Output " + channelKey + " may not be negative");
      return;
    } else if (channel >= protocol.protocol.maxChannels) {
      addWarning("Output " + channelKey + " may not be greater than " + protocol.protocol + " limit " + channel + " > " + protocol.protocol.maxChannels);
      return;
    }

    int priority = StreamingACNDatagram.DEFAULT_PRIORITY;
    if (outputObj.has(KEY_PRIORITY)) {
      final int jsonPriority = loadInt(outputObj, KEY_PRIORITY, true, "Output " + KEY_PRIORITY + " must be a valid integer");
      if (jsonPriority < 0 || jsonPriority > StreamingACNDatagram.MAX_PRIORITY) {
        addWarning("Output " + KEY_PRIORITY + " must be within range [0-200], ignoring value: " + jsonPriority);
      } else {
        priority = jsonPriority;
      }
    }

    KinetDatagram.Version kinetVersion = KinetDatagram.Version.PORTOUT;
    if (protocol == JsonProtocolDefinition.KINET) {
      if (outputObj.has(KEY_KINET_VERSION)) {
        String key = loadString(outputObj, KEY_KINET_VERSION, true, "Output must specify valid KiNET version of PORTOUT or DMXOUT");
        if (key != null) {
          try {
            kinetVersion = KinetDatagram.Version.valueOf(key.toUpperCase());
          } catch (Exception x) {
            addWarning("Output specifies an invalid KiNET version: " + key);
          }
        }
      }
    }

    final boolean sequenceEnabled = loadBoolean(outputObj, KEY_SEQUENCE_ENABLED, true, "Output " + KEY_SEQUENCE_ENABLED + " must be a valid boolean");

    // Top level output byte-order
    JsonByteEncoderDefinition byteOrder = loadByteOrder(outputObj, JsonByteEncoderDefinition.RGB);

    // Load up the segment definitions
    List<JsonSegmentDefinition> segments = new ArrayList<JsonSegmentDefinition>();
    loadSegments(fixture, segments, outputObj, byteOrder);

    this.definedOutputs.add(new JsonOutputDefinition(fixture, protocol, transport, byteOrder, address, port, universe, channel, priority, sequenceEnabled, kinetVersion, fps, segments));
  }

  private void loadBrightness(LXFixture child, JsonObject childObj) {
    if (childObj.has(KEY_BRIGHTNESS)) {
      float brightness = loadFloat(childObj, KEY_BRIGHTNESS, true);
      if (brightness < 0f || brightness > 1f) {
        addWarning("Component " + KEY_BRIGHTNESS + " must be in the range 0-1, invalid: " + brightness);
      } else {
        child.brightness.setValue(brightness);
      }
    }
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

  private static final String[] SEGMENT_KEYS = { KEY_NUM, KEY_START, KEY_COMPONENT_INDEX, KEY_COMPONENT_ID, KEY_STRIDE, KEY_REVERSE, KEY_PAD_PRE, KEY_PAD_POST, KEY_HEADER_BYTES, KEY_FOOTER_BYTES };

  private void loadSegments(LXFixture fixture, List<JsonSegmentDefinition> segments, JsonObject outputObj, JsonByteEncoderDefinition defaultByteOrder) {
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
            loadSegment(fixture, segments, segmentElem.getAsJsonObject(), defaultByteOrder, false);
          } else if (!segmentElem.isJsonNull()) {
            addWarning(KEY_SEGMENTS + " should only contain segment elements in JSON object format, found invalid: " + segmentElem);
          }
        }
      }
    } else {
      // Just specifying one, no need for segments key, defined directly in output
      loadSegment(fixture, segments, outputObj, defaultByteOrder, true);
    }
  }

  private void loadSegment(LXFixture fixture, List<JsonSegmentDefinition> segments, JsonObject segmentObj, JsonByteEncoderDefinition outputByteOrder, boolean isOutput) {
    int num = JsonOutputDefinition.ALL_POINTS;

    int start = loadInt(segmentObj, KEY_START, true, "Output " + KEY_START + " must be a valid integer");
    if (start < 0) {
      addWarning("Output " + KEY_START + " may not be negative");
      return;
    }

    if (segmentObj.has(KEY_COMPONENT_ID)) {
      if (!(fixture instanceof JsonFixture)) {
        addWarning("Output " + KEY_COMPONENT_ID + " may only be used on custom fixtures");
        return;
      }
      String componentId = loadString(segmentObj, KEY_COMPONENT_ID, true, "Output " + KEY_COMPONENT_ID + " must be a valid string");
      if (componentId == null) {
        addWarning("Output " + KEY_COMPONENT_ID + " may not be empty");
        return;
      }
      List<LXFixture> childComponents = ((JsonFixture) fixture).componentsById.get(componentId);
      if (childComponents == null) {
        addWarning("Output " + KEY_COMPONENT_ID + " does not exist: " + componentId);
        return;
      }

      final int offset = start;
      start = ((JsonFixture) fixture).getFixtureOffset(childComponents.get(0));
      num = 0;
      for (LXFixture childFixture : childComponents) {
        num += childFixture.totalSize();
      }
      if (offset >= num) {
        addWarning("Output " + KEY_COMPONENT_INDEX + " start value " + offset + " exceeds size " + num);
        return;
      }
      start += offset;
      num -= offset;

    } else if (segmentObj.has(KEY_COMPONENT_INDEX)) {
      if (!(fixture instanceof JsonFixture)) {
        addWarning("Output " + KEY_COMPONENT_INDEX + " may only be used on custom fixtures");
        return;
      }
      int componentIndex = loadInt(segmentObj, KEY_COMPONENT_INDEX, true, "Output " + KEY_COMPONENT_INDEX + " must be a valid integer");
      if (componentIndex < 0) {
        addWarning("Output " + KEY_COMPONENT_INDEX + " may not be negative (" + componentIndex + ")");
        return;
      }
      if (componentIndex >= ((JsonFixture) fixture).componentsByIndex.size()) {
        addWarning("Output " + KEY_COMPONENT_INDEX + " is out of fixture range (" + componentIndex + ")");
        return;
      }
      List<LXFixture> childComponents = ((JsonFixture) fixture).componentsByIndex.get(componentIndex);
      if (childComponents == null) {
        addWarning("Output " + KEY_COMPONENT_INDEX + " in invalid or disabled (" + componentIndex + ")");
        return;
      }
      final int offset = start;
      start = ((JsonFixture) fixture).getFixtureOffset(childComponents.get(0));
      num = 0;
      for (LXFixture childFixture : childComponents) {
        num += childFixture.totalSize();
      }
      if (offset >= num) {
        addWarning("Output " + KEY_COMPONENT_INDEX + " start value " + offset + " exceeds size " + num);
        return;
      }
      start += offset;
      num -= offset;
    }

    if (segmentObj.has(KEY_NUM)) {
      num = loadInt(segmentObj, KEY_NUM, true, "Output " + KEY_NUM + " must be a valid integer");
      if (num < 0) {
        addWarning("Output " + KEY_NUM + " may not be negative");
        return;
      }
    }

    int stride = DEFAULT_OUTPUT_STRIDE;
    if (segmentObj.has(KEY_STRIDE)) {
      stride = loadInt(segmentObj, KEY_STRIDE, true, "Output " + KEY_STRIDE + " must be a valid integer");
      if (stride <= 0) {
        addWarning("Output stride must be a positive value, use 'reverse: true' to invert pixel order");
        return;
      }
    }

    int repeat = DEFAULT_OUTPUT_REPEAT;
    if (segmentObj.has(KEY_REPEAT)) {
      repeat = loadInt(segmentObj, KEY_REPEAT, true, "Output " + KEY_REPEAT + " must be a valid integer");
      if (repeat <= 0) {
        addWarning("Output repeat must be a positive value");
        return;
      }
    }

    int padPre = 0, padPost = 0;
    if (segmentObj.has(KEY_PAD_PRE)) {
      padPre = loadInt(segmentObj, KEY_PAD_PRE, true, "Output " + KEY_PAD_PRE + " must be a valid integer");
    }
    if (segmentObj.has(KEY_PAD_POST)) {
      padPost = loadInt(segmentObj, KEY_PAD_POST, true, "Output " + KEY_PAD_POST+ " must be a valid integer");
    }
    if (padPre < 0 || padPost < 0) {
      addWarning("Output padding must be a non-negative value");
      return;
    }

    boolean reverse = loadBoolean(segmentObj, KEY_REVERSE, true, "Output " + KEY_REVERSE + " must be a valid boolean");

    JsonByteEncoderDefinition segmentByteOrder = null;
    if (!isOutput) {
      segmentByteOrder = loadByteOrder(segmentObj, null);
    }

    // The entire segment may be repeated - note that this is different from the basic
    // repeat option which repeats every *pixel* inline. The segment repeat option duplicates
    // output of the entire segment N times.
    int duplicate = 1;
    if (segmentObj.has(KEY_DUPLICATE)) {
      duplicate = loadInt(segmentObj, KEY_DUPLICATE, true, "Output " + KEY_DUPLICATE + " must be a valid integer");
      if (duplicate <= 0) {
        addWarning("Output duplicate must be a positive value");
        return;
      }
    }

    // Static header / footer bytes
    byte[] headerBytes = loadStaticBytes(segmentObj, KEY_HEADER_BYTES);
    byte[] footerBytes = loadStaticBytes(segmentObj, KEY_FOOTER_BYTES);

    // Duplicate the definition N times (typically 1)
    final JsonSegmentDefinition segment = new JsonSegmentDefinition(start, num, stride, repeat, padPre, padPost, reverse, segmentByteOrder, headerBytes, footerBytes);
    for (int i = 0; i < duplicate; ++i) {
      segments.add(segment);
    }
  }

  private byte[] loadStaticBytes(JsonObject segmentObj, String key) {
    if (segmentObj.has(key)) {
      final JsonElement elem = segmentObj.get(key);

      if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isString()) {
        // Static bytes supplied as a hex string
        final String hex = elem.getAsJsonPrimitive().getAsString();
        if (hex.isEmpty() || (hex.length() % 2) != 0) {
          addWarning("Byte hex string " + key + " must have positive, even length");
          return null;
        }
        final byte[] bytes = new byte[hex.length() / 2];
        try {
          int b = 0;
          for (int offset = 0; offset < hex.length(); offset += 2) {
            bytes[b++] = (byte) Integer.parseInt(hex.substring(offset, offset+2), 16);
          }
        } catch (NumberFormatException nfx) {
          addWarning("Bad hex byte string " + key + ": " + hex);
          return null;
        }
        return bytes;
      } else if (elem.isJsonArray()) {
        // Static bytes as an array of numeric values
        final JsonArray arr = elem.getAsJsonArray();
        if (arr.size() == 0) {
          addWarning("Byte array for " + key + " is empty");
          return null;
        }
        final byte[] bytes = new byte[arr.size()];
        for (int i = 0; i < arr.size(); ++i) {
          final JsonElement byteElem = arr.get(i);
          try {
            // GSON doesn't handle 0x prefixed hex numbers natively, they come back as strings
            if (byteElem.getAsJsonPrimitive().isString() && byteElem.getAsString().toLowerCase().startsWith("0x")) {
              bytes[i] = (byte) Integer.parseInt(byteElem.getAsString().substring(2), 16);
            } else {
              bytes[i] = (byte) byteElem.getAsJsonPrimitive().getAsInt();
            }
          } catch (Exception x) {
            addWarning("Bad byte value " + key + "[" + i + "] = " + byteElem);
          }
        }
        return bytes;
      } else {
        addWarning("Bad static byte data for " + key + ": " + elem);
      }
    }
    return null;
  }

  private JsonByteEncoderDefinition loadByteOrder(JsonObject obj, JsonByteEncoderDefinition defaultByteOrder) {
    JsonByteEncoderDefinition byteOrder = defaultByteOrder;
    String byteOrderStr = loadString(obj, KEY_BYTE_ORDER, true, "Output must specify a valid string " + KEY_BYTE_ORDER);
    if (byteOrderStr != null) {
      if (byteOrderStr.isEmpty()) {
        addWarning("Output must specify non-empty string value for " + KEY_BYTE_ORDER);
      } else {
        JsonByteEncoderDefinition definedByteOrder = JsonByteEncoderDefinition.get(this.lx, byteOrderStr);
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
  protected void buildOutputs() {
    for (JsonOutputDefinition output : this.definedOutputs) {
      buildOutput(output);
    }
  }

  private int getFixtureOffset(LXFixture child) {
    int offset = size();
    for (LXFixture fixture : this.children) {
      if (child == fixture) {
        return offset;
      }
      offset += fixture.totalSize();
    }
    return 0;
  }

  private void buildOutput(JsonOutputDefinition output) {
    // Special case! Add an art-sync datagram directly
    if (output.protocol == JsonProtocolDefinition.ARTSYNC) {
      buildArtSyncDatagram(output);
      return;
    }

    // Use this helper, as firstPointIndex values may not necessarily set at this point, if we are in an
    // initial file load and the outputs are being regenerated by a parameter change. Later the
    // LXStructure will rebuild the model and set indices appropriately
    int fixtureOffset = getFixtureOffset(output.fixture);
    int fixtureSize = output.fixture.totalSize();

    List<Segment> segments = new ArrayList<Segment>();
    for (JsonSegmentDefinition segment : output.segments) {

      if (segment.start < 0 || (segment.start >= fixtureSize)) {
        addWarning("Output specifies invalid start position: " + segment.start + " should be between [0, " + (fixtureSize-1) + "]");
        return;
      }
      int num = (segment.num == JsonOutputDefinition.ALL_POINTS) ? fixtureSize : segment.num;

      if (segment.start + segment.stride * (num-1) >= fixtureSize) {
        addWarning("Output specifies excessive size beyond fixture limits: start=" + segment.start + " num=" + num + " stride=" + segment.stride + " fixtureSize=" + fixtureSize);
        return;
      }

      segments.add(new Segment(
        segment.start + fixtureOffset,
        num,
        segment.stride,
        segment.repeat,
        segment.padPre,
        segment.padPost,
        segment.reverse,
        (segment.byteEncoder != null) ? segment.byteEncoder.byteEncoder : output.byteEncoder.byteEncoder,
        segment.headerBytes,
        segment.footerBytes
       ));
    }

    // Add an output definition!
    addOutputDefinition(new OutputDefinition(
      output.protocol.protocol,
      output.transport.transport,
      output.address,
      (output.port == JsonOutputDefinition.DEFAULT_PORT) ? output.protocol.protocol.defaultPort : output.port,
      output.universe,
      output.channel,
      output.priority,
      output.sequenceEnabled,
      output.kinetVersion,
      output.fps,
      segments.toArray(new Segment[0])
    ));

  }

  private void buildArtSyncDatagram(JsonOutputDefinition output) {
    LXDatagram artSync = new ArtSyncDatagram(this.lx);
    if (output.port != JsonOutputDefinition.DEFAULT_PORT) {
      artSync.setPort(output.port);
    }
    artSync.setAddress(output.address);
    artSync.framesPerSecond.setValue(output.fps);
    addOutputDirect(artSync);
  }

  @Override
  protected int size() {
    // No points of our own, all points are managed by children
    return 0;
  }

  @Override
  protected void computePointGeometry(LXMatrix matrix, List<LXPoint> points) {
    // Nothing needs doing here, all points are held in children
  }

  @Override
  protected void addModelMetaData(Map<String, String> metaData) {
    for (ParameterDefinition parameter : this.definedParameters.values()) {
      metaData.put(parameter.name, parameter.getValueAsString());
    }
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
      case STRING_SELECT:
        jsonParameters.addProperty(parameter.name, parameter.stringSelectParameter.getObject());
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
