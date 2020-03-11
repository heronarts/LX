package heronarts.lx.snapshot;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXChannel;
import heronarts.lx.LXChannelBus;
import heronarts.lx.LXComponent;
import heronarts.lx.LXEffect;
import heronarts.lx.LXPattern;
import heronarts.lx.LXSerializable;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXParameter;

public class LXSnapshot extends LXComponent implements LXComponent.Renamable {

  private int index = 0;

  public final List<LXSnapshotValue> snapshotValues = new ArrayList<LXSnapshotValue>();
  public final List<LXSnapshotActivePattern> activePatterns = new ArrayList<LXSnapshotActivePattern>();

  public interface Listener {
    public void onLoadStart(LXSnapshot snapshot);
    public void onLoadEnd(LXSnapshot snapshot);
  }

  private final List<Listener> listeners = new ArrayList<Listener>();

  public LXSnapshot(LX lx) {
    super(lx);
  }

  public LXSnapshot(LX lx, String label) {
    super(lx, label);
  }

  /**
   * Sets the index of this snapshot in its parent list
   *
   * @param index Snapshot index
   * @return this
   */
  public LXSnapshot setIndex(int index) {
    this.index = index;
    return this;
  }

  @Override
  public String getOscPath() {
    String path = super.getOscPath();
    if (path != null) {
      return path;
    }
    return getOscLabel();
  }

  @Override
  public String getOscAddress() {
    LXComponent parent = getParent();
    if (parent instanceof LXOscComponent) {
      return parent.getOscAddress() + "/" + getOscPath();
    }
    return null;
  }

  /**
   * Returns the ordering index of this snapshot in its parent
   *
   * @return Modulator index
   */
  public int getIndex() {
    return this.index;
  }

  /**
   * Captures the current LX state into this snapshot.  Can be called to create the initial snapshot or to overwrite an existing snapshot.
   */
  public void capture() {
    // Clear previous snapshot values
    for (LXSnapshotValue ssValue : this.snapshotValues) {
      ssValue.dispose();
    }
    for (LXSnapshotActivePattern ssActivePattern : this.activePatterns) {
      ssActivePattern.dispose();
    }
    this.snapshotValues.clear();
    this.activePatterns.clear();

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

        // Effects
        for (LXEffect effect : channel.getEffects()) {
          for (LXParameter effectParameter : effect.getParameters()) {
            addSnapshotValue(effectParameter);
          }
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
    double value = getBaseValue(parameter);
    LXSnapshotValue snapshotValue = new LXSnapshotValue(this.getLX(), parameter, value);
    snapshotValue.isEnabled.setValue(true);
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

  public void load() {
    System.out.println("Running snapshot " + this.getLabel());

    for (Listener listener : listeners) {
      listener.onLoadStart(this);
    }

    // Apply parameter values
    for (LXSnapshotValue ssValue : this.snapshotValues) {
      if (ssValue.isEnabled.getValueb()) {
        ssValue.applyValue();
      }
    }

    // Change active patterns
    for (LXSnapshotActivePattern activePattern : this.activePatterns) {
      activePattern.channel.goPattern(activePattern.pattern);
    }

    for (Listener listener : listeners) {
      listener.onLoadEnd(this);
    }
  }

  @Override
  public String getPath() {
    return "snapshots/" + (this.index + 1);
  }

  public LXSnapshot addListener(Listener listener) {
    this.listeners.add(listener);
    return this;
  }

  public LXSnapshot removeListener(Listener listener) {
    this.listeners.remove(listener);
    return this;
  }

  public LXSnapshot removeLinks(LXComponent component) {
    List<LXSnapshotValue> remove = findReferences(component);
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

  public List<LXSnapshotValue> findReferences(LXComponent component) {
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

  private static final String KEY_PARAMETERVALUES = "parameterValues";
  private static final String KEY_ACTIVEPATTERNS = "activePatterns";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_PARAMETERVALUES, LXSerializable.Utils.toArray(lx, this.snapshotValues));
    obj.add(KEY_ACTIVEPATTERNS, LXSerializable.Utils.toArray(lx, this.activePatterns));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);

    // Parameter Values
    if (obj.has(KEY_PARAMETERVALUES)) {
      JsonArray parameterValuesArray = obj.getAsJsonArray(KEY_PARAMETERVALUES);
      for (JsonElement parameterValueElement : parameterValuesArray) {
        JsonObject parameterValueObj = parameterValueElement.getAsJsonObject();
        try {
          LXSnapshotValue ssValue = new LXSnapshotValue(this.lx, this.lx.engine, parameterValueObj);
          ssValue.load(lx, parameterValueObj);
          if (ssValue.parameter != null)
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
