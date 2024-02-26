/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.color;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXLoopTask;
import heronarts.lx.LXSerializable;
import heronarts.lx.modulator.LinearEnvelope;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.FunctionalParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

/**
 * A palette is an object that is used to keep track of top-level color values and
 * set modes of color computation. Though its use is not required, it is very useful for
 * creating coherent color schemes across patterns.
 */
public class LXPalette extends LXComponent implements LXLoopTask, LXOscComponent {

  private static final int NO_SWATCH_INDEX = -1;

  public interface Listener {
    public void swatchAdded(LXPalette palette, LXSwatch swatch);
    public void swatchRemoved(LXPalette palette, LXSwatch swatch);
    public void swatchMoved(LXPalette palette, LXSwatch swatch);
  }

  private final List<Listener> listeners = new ArrayList<Listener>();

  private final List<LXSwatch> mutableSwatches = new ArrayList<LXSwatch>();

  /**
   * A read-only list of all the saved color swatches
   */
  public final List<LXSwatch> swatches = Collections.unmodifiableList(this.mutableSwatches);

  /**
   * The active color swatch
   */
  public final LXSwatch swatch;

  /**
   * The primary active color value
   */
  public final LXDynamicColor color;

  public final BooleanParameter autoCycleEnabled =
    new BooleanParameter("Auto-Cycle", false)
    .setDescription("When enabled, the engine will automatically cycle through color swatches");

  /**
   * Auto-cycle to a random palette, not the next one
   */
  public final EnumParameter<AutoCycleMode> autoCycleMode =
    new EnumParameter<AutoCycleMode>("Auto-Cycle Mode", AutoCycleMode.NEXT)
    .setDescription("Mode of auto cycling");

  /**
   * Time in seconds after which transition thru the pattern set is automatically initiated.
   */
  public final BoundedParameter autoCycleTimeSecs =
    new BoundedParameter("Cycle Time", 60, .1, 60*60*4)
    .setDescription("Sets the number of seconds after which the engine cycles to the next snapshot")
    .setUnits(LXParameter.Units.SECONDS);


  private double autoCycleProgress = 0;

  public final DiscreteParameter autoCycleCursor =
    new DiscreteParameter("Auto-Cycle", NO_SWATCH_INDEX, NO_SWATCH_INDEX, 0)
    .setDescription("Index for the auto-cycle parameter");

  public final TriggerParameter triggerSwatchCycle =
    new TriggerParameter("Trigger Cycle", this::onTriggerSwatchCycle)
    .setDescription("Triggers a swatch change");

  /**
   * Amount of time taken in seconds to transition into a new snapshot view
   */
  public final BoundedParameter transitionTimeSecs =
    new BoundedParameter("Transition Time", 5, .1, 180)
    .setDescription("Sets the duration of interpolated transitions between color palettes")
    .setUnits(LXParameter.Units.SECONDS);

  public final BooleanParameter transitionEnabled =
    new BooleanParameter("Transitions", false)
    .setDescription("When enabled, transitions between color palettes use interpolation");

  public final EnumParameter<GradientUtils.BlendMode> transitionMode =
    new EnumParameter<GradientUtils.BlendMode>("Transition Mode", GradientUtils.BlendMode.RGB)
    .setDescription("Which color blending mode transitions should use.");

  public final BooleanParameter expandedPerformance =
    new BooleanParameter("Enabled", true)
    .setDescription("Whether the palette pane is expanded in performance mode");

  public final StringParameter label1 =
    new StringParameter("Label 1", "1")
    .setDescription("Label for Palette Color 1");

  public final StringParameter label2 =
    new StringParameter("Label 2", "2")
    .setDescription("Label for Palette Color 2");

  public final StringParameter label3 =
    new StringParameter("Label 3", "3")
    .setDescription("Label for Palette Color 3");

  public final StringParameter label4 =
    new StringParameter("Label 4", "4")
    .setDescription("Label for Palette Color 4");

  public final StringParameter label5 =
    new StringParameter("Label 5", "5")
    .setDescription("Label for Palette Color 5");

  public final StringParameter[] labels = { this.label1, this.label2, this.label3, this.label4, this.label5 };

  private LXSwatch inTransition = null;
  private LXSwatch transitionFrom = null;
  private LXSwatch transitionTo = null;
  private JsonObject transitionTarget = null;

  private LinearEnvelope transition = new LinearEnvelope(0, 1, new FunctionalParameter() {
    @Override
    public double getValue() {
      return 1000 * transitionTimeSecs.getValue();
    }
  });

  public enum AutoCycleMode {
    NEXT,
    RANDOM;

    @Override
    public String toString() {
      switch (this) {
      case NEXT:
        return "Next";
      default:
      case RANDOM:
        return "Random";
      }
    }
  };

  private static final List<IndexSelector> selectors = new ArrayList<IndexSelector>();
  private static final String[] selectorOptions = new String[] { "1", "2", "3", "4", "5" };

  public static class IndexSelector extends DiscreteParameter {

    public IndexSelector(String name) {
      super(name, 1, LXSwatch.MAX_COLORS + 1);
      setDescription("Which color index in the palette to use");
      setOptions(selectorOptions, false);
      selectors.add(this);
    }

    @Override
    public void dispose() {
      super.dispose();
      selectors.remove(this);
    }
  }

  public LXPalette(LX lx) {
    super(lx, "Color Palette");
    addChild("swatch", this.swatch = new LXSwatch(this, false));
    addArray("swatches", this.swatches);
    addParameter("transitionEnabled", this.transitionEnabled);
    addParameter("transitionTimeSecs", this.transitionTimeSecs);
    addParameter("transitionMode", this.transitionMode);
    addParameter("autoCycleEnabled", this.autoCycleEnabled);
    addParameter("autoCycleMode", this.autoCycleMode);
    addParameter("autoCycleTimeSecs", this.autoCycleTimeSecs);
    addParameter("autoCycleCursor", this.autoCycleCursor);
    addParameter("triggerSwatchCycle", this.triggerSwatchCycle);
    addParameter("expandedPerformance", this.expandedPerformance);
    addParameter("label1", this.label1);
    addParameter("label2", this.label2);
    addParameter("label3", this.label3);
    addParameter("label4", this.label4);
    addParameter("label5", this.label5);
    this.color = swatch.colors.get(0);
  }

  private void updateSelectors() {
    int i = 0;
    for (StringParameter label : this.labels) {
      final String option = label.getString();
      selectorOptions[i] = option.isEmpty() ? String.valueOf(i+1) : option;
      ++i;
    }
    for (IndexSelector selector : selectors) {
      selector.optionsChanged.bang();
    }
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.autoCycleEnabled) {
      this.autoCycleProgress = 0;
    } else if (p == this.transitionEnabled) {
      if (!this.transitionEnabled.isOn()) {
        finishTransition();
      }
    } else if (p == this.label1 || p == this.label2 || p == this.label3 || p == this.label4 || p == this.label5) {
      updateSelectors();
    }
  }

  private void onTriggerSwatchCycle() {
    if (this.transitionTarget != null) {
      finishTransition();
    } else {
      doSwatchCycle();
    }
  }

  /**
   * Gets the primary color of the currently active swatch
   *
   * @return Primary color of active swatch
   */
  public int getColor() {
    return this.color.getColor();
  }

  /**
   * Gets the color in the active swatch at the given index.
   * If the swatch doesn't have that many colors, the last
   * available color is returned.
   *
   * @param index Index in swatch
   * @return Color
   */
  public LXDynamicColor getSwatchColor(int index) {
    return this.swatch.getColor(index);
  }

  /**
   * Gets the hue of the primary color in active swatch
   *
   * @return Hue of primary color
   */
  public float getHuef() {
    return this.color.getHuef();
  }

  /**
   * Gets the hue of the primary color in active swatch
   *
   * @return Hue of primary color
   */
  public double getHue() {
    return this.color.getHue();
  }

  /**
   * Gets the saturation of the primary color in active swatch
   *
   * @return Saturation of primary color
   */
  public float getSaturationf() {
    return (float) getSaturation();
  }

  /**
   * Gets the saturation of the primary color in active swatch
   *
   * @return Saturation of primary color
   */
  public double getSaturation() {
    return LXColor.s(getColor());
  }


  /**
   * Gets the brightness of the primary color in active swatch
   *
   * @return Brightness of primary color
   */
  public float getBrightnessf() {
    return (float) getBrightness();
  }

  /**
   * Gets the brightness of the primary color in active swatch
   *
   * @return Brightness of primary color
   */
  public double getBrightness() {
    return LXColor.b(getColor());
  }

  private void _reindexSwatches() {
    int i = 0;
    for (LXSwatch swatch: this.swatches) {
      swatch.setIndex(i++);
    }
  }

  /**
   * Makes a JSON copy of the given swatch, but with the recall flag cleared
   * and all IDs stripped, suitable for copying into a new swatch.
   *
   * @param swatch Swatch to serialize
   * @return Serialized swatch
   */
  private JsonObject toJsonSwatch(LXSwatch swatch) {
    JsonObject obj = LXSerializable.Utils.toObject(this.lx, swatch, true);
    LXSerializable.Utils.stripParameter(obj, "recall");
    return obj;
  }

  /**
   * Saves the current swatch to the list of saved swatches
   *
   * @return Saved swatch, added to swatch list
   */
  public LXSwatch saveSwatch() {
    LXSwatch saved = new LXSwatch(this, true);
    saved.load(this.lx, toJsonSwatch(this.swatch));
    saved.label.setValue("Swatch-" + (this.swatches.size() + 1));
    return addSwatch(saved, -1);
  }

  /**
   * Adds a swatch at the given index
   *
   * @param swatchObj Saved swatch object
   * @param index Index to save at
   * @return Swatch object
   */
  public LXSwatch addSwatch(JsonObject swatchObj, int index) {
    LXSwatch saved = new LXSwatch(this, true);
    saved.load(getLX(), swatchObj);
    return addSwatch(saved, index);
  }

  private LXSwatch addSwatch(LXSwatch swatch, int index) {
    if (index < 0) {
      this.mutableSwatches.add(swatch);
    } else {
      this.mutableSwatches.add(index, swatch);
    }
    _reindexSwatches();
    this.autoCycleCursor.setRange(-1, this.swatches.size());
    if (index >= 0 && index <= this.autoCycleCursor.getValuei()) {
      this.autoCycleCursor.increment();
    }
    for (Listener listener : this.listeners) {
      listener.swatchAdded(this, swatch);
    }
    return swatch;
  }

  /**
   * Removes a swatch from the color palette's saved swatch list
   *
   * @param swatch Swatch to remove
   * @return this
   */
  public LXPalette removeSwatch(LXSwatch swatch) {
    if (!this.swatches.contains(swatch)) {
      throw new IllegalStateException("Cannot remove swatch not in palette: " + swatch);
    }
    int index = this.mutableSwatches.indexOf(swatch);
    this.mutableSwatches.remove(swatch);
    _reindexSwatches();
    for (Listener listener : this.listeners) {
      listener.swatchRemoved(this, swatch);
    }
    if (index <= this.autoCycleCursor.getValuei()) {
      this.autoCycleCursor.decrement();
    }
    this.autoCycleCursor.setRange(NO_SWATCH_INDEX, this.swatches.size());
    LX.dispose(swatch);
    return this;
  }

  /**
   * Set the palette to a saved swatch
   *
   * @param swatch Swatch to transition to
   * @return Whether swatch was set, or false if already in transition
   */
  public boolean setSwatch(LXSwatch swatch) {
    if (this.inTransition == swatch) {
      finishTransition();
      return false;
    }

    JsonObject swatchObj = toJsonSwatch(swatch);

    this.autoCycleCursor.setValue(swatch.getIndex());
    this.autoCycleProgress = 0;
    if (this.transitionEnabled.isOn()) {
      this.inTransition = swatch;
      this.transitionFrom = LXSwatch.staticCopy(this.swatch);
      this.transitionTo = LXSwatch.staticCopy(swatch);
      this.transitionTarget = swatchObj;
      this.transition.trigger();

      // Set everything to fixed color mode while mid-transition
      for (LXDynamicColor color : this.swatch.colors) {
        int colorNow = color.getColor();
        color.mode.setValue(LXDynamicColor.Mode.FIXED);
        color.primary.setColor(colorNow);
      }

      // Make sure we have same number of colors as the max of from/to during transition
      int nColors = Math.max(this.transitionFrom.colors.size(), this.transitionTo.colors.size());
      while (this.swatch.colors.size() < nColors) {
        this.swatch.addColor();
      }
      while (this.swatch.colors.size() > nColors) {
        this.swatch.removeColor();
      }

    } else {
      this.swatch.load(this.lx, swatchObj);
    }
    return true;
  }

  public double getTransitionProgress() {
    return (this.transitionTarget != null) ? this.transition.getValue() : 0;
  }

  public double getAutoCycleProgress() {
    return this.autoCycleProgress;
  }

  private void finishTransition() {
    if (this.transitionTarget != null) {
      this.swatch.load(this.lx, this.transitionTarget);
      this.inTransition = null;
      this.transitionFrom = null;
      this.transitionTo = null;
      this.transitionTarget = null;
    }
  }

  public void loop(double deltaMs) {
    if (this.transitionTarget != null) {
      this.transition.loop(deltaMs);
      if (this.transition.finished()) {
        finishTransition();
      } else {
        float lerp = this.transition.getBasisf();
        int i = 0;
        for (LXDynamicColor color : this.swatch.colors) {
          _transitionColor(color, i++, lerp);
        }
      }
      this.autoCycleProgress = 0;
    } else {
      this.swatch.loop(deltaMs);
      if (this.autoCycleEnabled.isOn()) {
        this.autoCycleProgress += deltaMs / (1000 * this.autoCycleTimeSecs.getValue());
        if (this.autoCycleProgress >= 1) {
          this.autoCycleProgress = 0;
          doSwatchCycle();
        }
      }
    }
  }

  private void doSwatchCycle() {
    switch (this.autoCycleMode.getEnum()) {
    case NEXT:
      goNextSwatch();
      break;
    case RANDOM:
      goRandomSwatch();
      break;
    }
  }

  private void goNextSwatch() {
    if (this.swatches.size() <= 1) {
      return;
    }
    int startIndex = this.autoCycleCursor.getValuei();
    int nextIndex = (startIndex + 1) % this.swatches.size();
    while (nextIndex != startIndex) {
      if (startIndex < 0) {
        startIndex = 0;
      }
      LXSwatch next = this.swatches.get(nextIndex);
      if (next.autoCycleEligible.isOn()) {
        setSwatch(next);
        return;
      }
      nextIndex = (nextIndex + 1) % this.swatches.size();
    }
  }

  private void goRandomSwatch() {
    if (this.swatches.size() <= 1) {
      return;
    }
    List<LXSwatch> eligible = new ArrayList<LXSwatch>();
    int autoIndex = this.autoCycleCursor.getValuei();
    for (int i = 0; i < this.swatches.size(); ++i) {
      if (i != autoIndex) {
        LXSwatch test = this.swatches.get(i);
        if (test.autoCycleEligible.isOn()) {
          eligible.add(test);
        }
      }
    }
    int numEligible = eligible.size();
    if (numEligible > 0) {
      LXSwatch random = eligible.get(LXUtils.constrain((int) LXUtils.random(0, numEligible), 0, numEligible - 1));
      setSwatch(random);
    }
  }

  private void _transitionColor(LXDynamicColor color, int i, float lerp) {
    color.primary.setColor(this.transitionMode.getEnum().function.blend(
      this.transitionFrom.getColor(i).primary,
      this.transitionTo.getColor(i).primary,
      lerp
    ));
  }

  /**
   * Moves a saved swatch to a different position in the list
   *
   * @param swatch Saved swatch
   * @param index New index for that swatch
   * @return this
   */
  public LXPalette moveSwatch(LXSwatch swatch, int index) {
    if (index < 0 || index >= this.mutableSwatches.size()) {
      throw new IllegalArgumentException("Cannot move swatch to invalid index: " + index);
    }
    LXSwatch autoCycleSwatch = null;
    int auto = this.autoCycleCursor.getValuei();
    if (auto >= 0 && auto < this.swatches.size()) {
      autoCycleSwatch = this.swatches.get(auto);
    }
    this.mutableSwatches.remove(swatch);
    this.mutableSwatches.add(index, swatch);
    _reindexSwatches();
    for (Listener listener : this.listeners) {
      listener.swatchMoved(this, swatch);
    }
    if (autoCycleSwatch != null) {
      this.autoCycleCursor.setValue(this.swatches.indexOf(autoCycleSwatch));
    }
    return this;
  }

  /**
   * Registers a listener to the palette
   *
   * @param listener Palette listener
   * @return this
   */
  public LXPalette addListener(Listener listener) {
    Objects.requireNonNull(listener);
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate LXPalette.Listener: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  /**
   * Unregisters a listener to the palette
   *
   * @param listener Palette listener
   * @return this
   */
  public LXPalette removeListener(Listener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("May not remove non-registered LXPalette.Listener: " + listener);
    }
    this.listeners.remove(listener);
    return this;
  }

  @Override
  public boolean handleOscMessage(OscMessage message, String[] parts, int index) {
    String path = parts[index];
    if (path.equals("swatches") && (parts.length > index + 1)) {
      path = parts[index+1];
      for (LXSwatch swatch : this.swatches) {
        if (path.equals(swatch.getOscLabel())) {
          return swatch.handleOscMessage(message, parts, index+2);
        }
      }
    }
    return super.handleOscMessage(message, parts, index);
  }

  @Override
  public void dispose() {
    for (LXSwatch swatch : this.swatches) {
      LX.dispose(swatch);
    }
    this.mutableSwatches.clear();
    LX.dispose(this.swatch);
    super.dispose();
  }

  private static final String KEY_SWATCHES = "swatches";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_SWATCHES, LXSerializable.Utils.toArray(lx, this.swatches));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    // Kill any transition
    finishTransition();

    while (!this.swatches.isEmpty()) {
      removeSwatch(this.swatches.get(this.swatches.size() - 1));
    }
    if (obj.has(KEY_SWATCHES)) {
      JsonArray swatchArr = obj.get(KEY_SWATCHES).getAsJsonArray();
      for (JsonElement swatchElem : swatchArr) {
        addSwatch(swatchElem.getAsJsonObject(), -1);
      }
    }
    super.load(lx, obj);
  }

  public void exportSwatches(File file) {
    final JsonObject obj = new JsonObject();
    obj.add(KEY_SWATCHES, LXSerializable.Utils.toArray(this.lx, this.swatches, true));
    try (JsonWriter writer = new JsonWriter(new FileWriter(file))) {
      writer.setIndent("  ");
      new GsonBuilder().create().toJson(obj, writer);
      LX.log("Swatches saved successfully to " + file.toString());
    } catch (IOException iox) {
      LX.error(iox, "Could not export color swatches to file: " + file.toString());
    }
  }

  public List<LXSwatch> importSwatches(File file) {
    final List<LXSwatch> imported = new ArrayList<LXSwatch>();
    try (FileReader fr = new FileReader(file)) {
      JsonObject obj = new Gson().fromJson(fr, JsonObject.class);
      if (obj.has(KEY_SWATCHES)) {
        JsonArray swatchArr = obj.get(KEY_SWATCHES).getAsJsonArray();
        for (JsonElement swatchElem : swatchArr) {
          imported.add(addSwatch(swatchElem.getAsJsonObject(), -1));
        }
      }
    } catch (IOException iox) {
      LX.error(iox, "Could not import color swatches from file: " + file.toString());
    }
    return imported;
  }

}
