package heronarts.lx;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.modulator.LXModulator;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXParameter;

public class LXSnapshot extends LXComponent {

  public Integer snapshotID;

  public final List<LXSnapshotValue> snapshotValues = new ArrayList<LXSnapshotValue>();
  public final List<LXSnapshotActivePattern> activePatterns = new ArrayList<LXSnapshotActivePattern>();

  public LXSnapshot(LX lx) {
    super(lx);
  }

  public LXSnapshot(LX lx, int id) {
    super(lx);

    this.snapshotID = id;
    this.label.setValue(LocalDateTime.now().toString());
  }

  public void initialize() {
    // Mixer values
    addSnapshotValue(lx.engine.crossfader);
    addSnapshotValue(lx.engine.output.brightness);

    // Channel values
    for (LXChannelBus bus : lx.engine.channels) {
      addSnapshotValue(bus.enabled);
      addSnapshotValue(bus.fader);
      addSnapshotValue(bus.crossfadeGroup);

      if (bus instanceof LXChannel) {
        // Selected pattern indexes
        LXChannel channel = (LXChannel)bus;
        LXPattern pattern = channel.getActivePattern();
        LXSnapshotActivePattern activePattern = new LXSnapshotActivePattern(this.getLX(), channel, pattern);
        addActivePattern(activePattern);

        // Current Pattern values
        for (LXParameter patternParameter : pattern.getParameters()) {
          addSnapshotValue(patternParameter);
        }
      }
    }

    // Modulations
    for (LXModulator modulator : lx.engine.modulation.getModulators()) {
      addSnapshotValue(modulator.running);
    }
  }

  protected void addSnapshotValue(LXParameter parameter) {
    addSnapshotValue(createSnapshotValue(parameter));
  }

  protected void addSnapshotValue(LXSnapshotValue snapshotValue) {
    this.snapshotValues.add(snapshotValue);
  }

  protected LXSnapshotValue createSnapshotValue(LXParameter parameter) {
    LXSnapshotValue snapshotValue = new LXSnapshotValue(this.getLX());
    snapshotValue.parameter = parameter;
    snapshotValue.value = getBaseValue(parameter);
    snapshotValue.isEnabled = true;
    return snapshotValue;
  }

  protected double getBaseValue(LXParameter parameter) {
    if (parameter instanceof CompoundParameter) {
      return ((CompoundParameter) parameter).getBaseValue();  // Gets value without modulation
    }
    return parameter.getValue();  // Everything is a double underneath
  }

  protected void removeSnapshotValue(LXSnapshotValue snapshotValue) {
    this.snapshotValues.remove(snapshotValue);
    snapshotValue.dispose();
  }

  protected void addActivePattern(LXSnapshotActivePattern activePattern) {
    this.activePatterns.add(activePattern);
  }

  protected void removeActivePattern(LXSnapshotActivePattern activePattern) {
    this.activePatterns.remove(activePattern);
    activePattern.dispose();
  }

  public void run() {
    System.out.println("Running snapshot " + this.getLabel());

    // Apply parameter values
    for (LXSnapshotValue ssValue : this.snapshotValues) {
      if (ssValue.isEnabled) {
        ssValue.parameter.setValue(ssValue.value);
      }
    }

    // Change active patterns
    for (LXSnapshotActivePattern activePattern : this.activePatterns) {
      activePattern.channel.goPattern(activePattern.pattern);
    }
  }

  @Override
  public String getLabel() {
    return this.snapshotID + ".  " + super.getLabel();
  }

  @Override
  public String getPath() {
    return "snapshot/" + this.snapshotID;
  }

  public LXSnapshot removeLinks(LXComponent component) {
    List<LXSnapshotValue> remove = findLinks(component);
    if (remove != null) {
      for (LXSnapshotValue snapshotValue : remove) {
        removeSnapshotValue(snapshotValue);
      }
    }
    List<LXSnapshotActivePattern> removeActivePatterns = findActivePatterns(component);
    if (removeActivePatterns != null) {
      for (LXSnapshotActivePattern snapshotActivePattern : removeActivePatterns) {
        removeActivePattern(snapshotActivePattern);
      }
    }
    return this;
  }

  public List<LXSnapshotValue> findLinks(LXComponent component) {
    List<LXSnapshotValue> found = null;
    for (LXSnapshotValue ssValue : this.snapshotValues) {
      if (component.contains(ssValue.parameter)) {
        if (found == null) {
          found = new ArrayList<LXSnapshotValue>();
        }
        found.add(ssValue);
      }
    }
    return found;
  }

  public List<LXSnapshotActivePattern> findActivePatterns(LXComponent component) {
    List<LXSnapshotActivePattern> found = null;
    for (LXSnapshotActivePattern ssActivePattern : this.activePatterns) {
      if (ssActivePattern.channel == component || ssActivePattern.pattern == component) {
        if (found == null) {
          found = new ArrayList<LXSnapshotActivePattern>();
        }
        found.add(ssActivePattern);
      }
    }
    return found;
  }

  @Override
  public void dispose() {
    for (LXSnapshotValue ssValue : this.snapshotValues) {
      ssValue.dispose();
    }
    for (LXSnapshotActivePattern ssActivePattern : this.activePatterns) {
      ssActivePattern.dispose();
    }
    this.snapshotValues.clear();
    this.activePatterns.clear();
    super.dispose();
  }

  private static final String KEY_SNAPSHOTID = "snapshotID";
  private static final String KEY_PARAMETERVALUES = "parameterValues";
  private static final String KEY_ACTIVEPATTERNS = "activePatterns";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_SNAPSHOTID, this.snapshotID);
    obj.add(KEY_PARAMETERVALUES, LXSerializable.Utils.toArray(lx, this.snapshotValues));
    obj.add(KEY_ACTIVEPATTERNS, LXSerializable.Utils.toArray(lx, this.activePatterns));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);

    // SnapshotID
    if (obj.has(KEY_SNAPSHOTID)) {
      this.snapshotID = obj.get(KEY_SNAPSHOTID).getAsInt();
    }

    // Label?? - Fix me! This should load from LXComponent, not sure why it is not working.
    if (obj.has("parameters")) {
      JsonObject parameterObj = obj.getAsJsonObject("parameters");
      if (parameterObj.has("label")) {
        this.label.setValue(parameterObj.get("label").getAsString());
      }
    }

    // Parameter Values
    if (obj.has(KEY_PARAMETERVALUES)) {
      JsonArray parameterValuesArray = obj.getAsJsonArray(KEY_PARAMETERVALUES);
      for (JsonElement parameterValueElement : parameterValuesArray) {
        JsonObject parameterValueObj = parameterValueElement.getAsJsonObject();
        try {
          LXSnapshotValue ssValue = new LXSnapshotValue(this.lx, this.lx.engine, parameterValueObj);
          ssValue.load(lx, parameterValueObj);
          addSnapshotValue(ssValue);
        } catch (Exception x) {
          System.err.println("Could not load snapshot value");
          x.printStackTrace();
        }
      }
    }

    // Active Patterns
    if (obj.has(KEY_ACTIVEPATTERNS)) {
      JsonArray activePatternsArray = obj.getAsJsonArray(KEY_ACTIVEPATTERNS);
      for (JsonElement activePatternElement : activePatternsArray) {
        JsonObject activePatternObj = activePatternElement.getAsJsonObject();
        try {
          LXSnapshotActivePattern activePattern = new LXSnapshotActivePattern(this.lx, activePatternObj);
          activePattern.load(lx, activePatternObj);
          addActivePattern(activePattern);
        } catch (Exception x) {
          System.err.println("Could not load snapshot's active pattern");
          x.printStackTrace();
        }
      }
    }
  }
}
