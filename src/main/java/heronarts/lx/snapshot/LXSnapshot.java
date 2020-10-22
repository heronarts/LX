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
 * @author Justin K. Blecher <jkbelcher@gmail.com>
 */

package heronarts.lx.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXPath;
import heronarts.lx.LXSerializable;
import heronarts.lx.color.ColorParameter;
import heronarts.lx.command.LXCommand;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.utils.LXUtils;

/**
 * A snapshot holds a memory of the state of the program at a point in time.
 * The snapshot contains a collection of "views" which are memories of a piece
 * of state in the program at some time. Typically this is a parameter value,
 * but some special cases exist, like the active pattern on a channel.
 */
public class LXSnapshot extends LXComponent implements LXComponent.Renamable, LXOscComponent {

  private int index;

  private final List<View> mutableViews = new ArrayList<View>();

  /**
   * Public immutable list of all the views this snapshot comtains.
   */
  public final List<View> views = Collections.unmodifiableList(this.mutableViews);

  public static enum ViewScope {
    MIXER,
    PATTERNS,
    EFFECTS,
    OUTPUT,
    MODULATION;
  }

  /**
   * Type of snapshot view
   */
  public static enum ViewType {
    /**
     * A parameter with a value
     */
    PARAMETER,

    /**
     * Channel fader state
     */
    CHANNEL_FADER,

    /**
     * The pattern which is active on a channel
     */
    ACTIVE_PATTERN
  };

  /**
   * A view is a component of a snapshot, it's a single piece of the snapshot that
   * is "looking at" one piece of state.
   */
  public abstract class View implements LXSerializable {

    public final ViewScope scope;
    private final ViewType type;

    // Whether this view is active in a transition
    boolean activeFlag = false;

    /**
     * Whether this view is enabled for recall or not.
     */
    public final BooleanParameter enabled = (BooleanParameter)
      new BooleanParameter("Enabled", true)
      .setMappable(false)
      .setDescription("Whether this view is enabled in the snapshot");

    private View(ViewScope scope, ViewType type) {
      this.scope = scope;
      this.type = type;
    }

    private View(LX lx, JsonObject obj) {
      LXSerializable.Utils.loadBoolean(this.enabled, obj, KEY_ENABLED);
      this.scope = ViewScope.valueOf(obj.get(KEY_SCOPE).getAsString());
      this.type = ViewType.valueOf(obj.get(KEY_TYPE).getAsString());
    }

    /**
     * Returns the snapshot that this view belongs to
     *
     * @return Snapshot that this view belongs to
     */
    public LXSnapshot getSnapshot() {
      return LXSnapshot.this;
    }

    /**
     * Gets a command version of this view's operation, needed to make
     * this action undoable.
     *
     * @return Command implementation of this view
     */
    public abstract LXCommand getCommand();

    /**
     * Subclasses must implement, determines whether the given view is dependent upon
     * the specified component, and whether this view should be removed if the
     * component is disposed
     *
     * @param component Component to test
     * @return <code>true</code> if this view depends upon that component's existence, <code>false</code> otherwise
     */
    protected abstract boolean isDependentOf(LXComponent component);

    /**
     * Subclasses must implement, should reapply the state of the view immediately
     */
    protected abstract void recall();

    /**
     * Subclasses may override, indicates the beginning of a transition
     */
    protected void startTransition() {
      recall();
    }

    /**
     * Subclasses may override, indicates the progress of a transition
     *
     * @param amount Amount of interpolation to apply
     */
    protected void interpolate(double amount) {}

    /**
     * Subclasses may override, indicates the completion of a transition
     */
    protected void finishTransition() {}

    private static final String KEY_SCOPE = "scope";
    private static final String KEY_TYPE = "type";
    private static final String KEY_ENABLED = "enabled";

    @Override
    public void save(LX lx, JsonObject obj) {
      obj.addProperty(KEY_SCOPE, this.scope.name());
      obj.addProperty(KEY_TYPE, this.type.name());
      obj.addProperty(KEY_ENABLED, this.enabled.isOn());
    }

    @Override
    public void load(LX lx, JsonObject obj) {
      throw new UnsupportedOperationException("LXSnapshot.View classes do not support loading");
    }

    public void dispose() {
      // Just in case some future type needs to do some cleanup...
    }
  }

  /**
   * Class for the recall of a simple parameter value
   */
  public class ParameterView extends View {

    private final LXComponent component;
    private final LXParameter parameter;
    private final double value;
    private final int intValue;
    private final String stringValue;
    private final double normalizedValue;

    private ParameterView(ViewScope scope, LXParameter parameter) {
      super(scope, ViewType.PARAMETER);
      this.component = parameter.getParent();
      if (this.component == null) {
        throw new IllegalStateException("Cannot store a snapshot view of a parameter with no parent");
      }
      this.parameter = parameter;
      this.value = getBaseValue();
      if (parameter instanceof DiscreteParameter) {
        this.intValue = ((DiscreteParameter) parameter).getValuei();
        this.stringValue = null;
      } else if (parameter instanceof ColorParameter) {
        this.intValue = ((ColorParameter) parameter).getColor();
        this.stringValue = null;
      } else if (parameter instanceof StringParameter) {
        this.intValue = 0;
        this.stringValue = ((StringParameter) parameter).getString();
      } else {
        this.intValue = 0;
        this.stringValue = null;
      }
      if (parameter instanceof LXNormalizedParameter) {
        this.normalizedValue = getBaseNormalized();
      } else {
        this.normalizedValue = 0;
      }
    }

    private ParameterView(LX lx, JsonObject obj) {
      super(lx, obj);
      this.parameter = (LXParameter) LXPath.get(lx, obj.get(KEY_PARAMETER_PATH).getAsString());
      this.component = this.parameter.getParent();
      if (this.component == null) {
        throw new IllegalStateException("Cannot store a snapshot view of a parameter with no parent");
      }
      if (this.parameter instanceof DiscreteParameter) {
        this.value = this.intValue = obj.get(KEY_VALUE).getAsInt();
        this.stringValue = null;
        obj.addProperty(KEY_VALUE, this.intValue);
      } else if (parameter instanceof ColorParameter) {
        this.intValue = obj.get(KEY_VALUE).getAsInt();
        this.value = Double.longBitsToDouble(this.intValue);
        this.stringValue = null;
      } else if (parameter instanceof StringParameter) {
        this.stringValue = obj.get(KEY_VALUE).getAsString();
        this.intValue = 0;
        this.value = 0;
      } else {
        this.value = obj.get(KEY_VALUE).getAsDouble();
        this.intValue = 0;
        this.stringValue = null;
      }
      this.normalizedValue = obj.get(KEY_NORMALIZED_VALUE).getAsDouble();
    }

    @Override
    public LXCommand getCommand() {
      if (this.parameter instanceof DiscreteParameter) {
        return new LXCommand.Parameter.SetValue((DiscreteParameter) this.parameter, this.intValue);
      } else if (this.parameter instanceof StringParameter) {
        return new LXCommand.Parameter.SetString((StringParameter) this.parameter, this.stringValue);
      } else {
        return new LXCommand.Parameter.SetValue(this.parameter, this.value);
      }
    }

    @Override
    protected boolean isDependentOf(LXComponent component) {
      return component.contains(this.parameter);
    }

    @Override
    protected void recall() {
      if (this.parameter instanceof DiscreteParameter) {
        ((DiscreteParameter) this.parameter).setValue(this.intValue);
      } else if (this.parameter instanceof ColorParameter) {
        ((ColorParameter) this.parameter).setColor(this.intValue);
      } else if (parameter instanceof StringParameter) {
        ((StringParameter) this.parameter).setValue(this.stringValue);
      } else {
        this.parameter.setValue(this.value);
      }
    }

    private double fromNormalized;
    private double fromValue;
    private int fromInt;

    @Override
    protected void startTransition() {
      if (this.parameter instanceof StringParameter) {
        recall();
      } else if (this.parameter instanceof LXNormalizedParameter) {
        this.fromNormalized = getBaseNormalized();
      } else if (this.parameter instanceof DiscreteParameter) {
        this.fromInt = ((DiscreteParameter) this.parameter).getValuei();
      } else {
        this.fromValue = this.parameter.getValue();
      }
    }

    @Override
    protected void interpolate(double amount) {
      if (this.parameter instanceof StringParameter) {
        // No interpolating strings
      } else if (this.parameter instanceof LXNormalizedParameter) {
        ((LXNormalizedParameter) this.parameter).setNormalized(LXUtils.lerp(this.fromNormalized, this.normalizedValue, amount));
      } else if (this.parameter instanceof DiscreteParameter) {
        ((DiscreteParameter) this.parameter).setValue(LXUtils.lerpi(this.fromInt, this.intValue, (float) amount));
      } else {
        this.parameter.setValue(LXUtils.lerp(this.fromValue, this.value, amount));
      }
    }

    @Override
    protected void finishTransition() {
      recall();
    }

    private double getBaseNormalized() {
      if (this.parameter instanceof CompoundParameter) {
        return ((CompoundParameter) this.parameter).getBaseNormalized();
      }
      return ((LXNormalizedParameter) this.parameter).getNormalized();
    }

    private double getBaseValue() {
      if (this.parameter instanceof CompoundParameter) {
        return ((CompoundParameter) this.parameter).getBaseValue();
      }
      return this.parameter.getValue();
    }

    private static final String KEY_PARAMETER_PATH = "parameterPath";
    private static final String KEY_VALUE = "value";
    private static final String KEY_NORMALIZED_VALUE = "normalizedValue";

    @Override
    public void save(LX lx, JsonObject obj) {
      super.save(lx, obj);
      obj.addProperty(KEY_PARAMETER_PATH, this.parameter.getCanonicalPath());
      if (parameter instanceof DiscreteParameter) {
        obj.addProperty(KEY_VALUE, this.intValue);
      } else if (parameter instanceof ColorParameter) {
        obj.addProperty(KEY_VALUE, this.intValue);
      } else if (parameter instanceof StringParameter) {
        obj.addProperty(KEY_VALUE, this.stringValue);
      } else {
        obj.addProperty(KEY_VALUE, this.value);
      }
      obj.addProperty(KEY_NORMALIZED_VALUE, this.normalizedValue);
    }

  }

  public class ChannelFaderView extends View {

    private final LXAbstractChannel channel;
    private final boolean enabled;
    private final double fader;

    private double fromFader, toFader;
    private boolean wasEnabled;

    private ChannelFaderView(LXAbstractChannel channel) {
      this(channel, channel.enabled.isOn(), channel.fader.getValue());
    }

    protected ChannelFaderView(LXAbstractChannel channel, boolean enabled, double fader) {
      super(ViewScope.MIXER, ViewType.CHANNEL_FADER);
      this.channel = channel;
      this.enabled = enabled;
      this.fader = fader;
    }

    private ChannelFaderView(LX lx, JsonObject obj) {
      super(ViewScope.MIXER, ViewType.CHANNEL_FADER);
      this.channel = (LXAbstractChannel) LXPath.get(lx, obj.get(KEY_CHANNEL_PATH).getAsString());
      this.enabled = obj.get(KEY_CHANNEL_ENABLED).getAsBoolean();
      this.fader = obj.get(KEY_CHANNEL_FADER).getAsDouble();
    }

    @Override
    public LXCommand getCommand() {
      return new LXCommand.Channel.SetFader(this.channel, this.enabled, this.fader);
    }

    @Override
    protected boolean isDependentOf(LXComponent component) {
      return component.contains(this.channel);
    }

    @Override
    protected void recall() {
      this.channel.enabled.setValue(this.enabled);
      this.channel.fader.setValue(this.fader);
    }

    @Override
    protected void startTransition() {
      this.wasEnabled = this.channel.enabled.isOn();
      if ((this.wasEnabled != this.enabled) && (lx.engine.snapshots.channelMode.getEnum() == LXSnapshotEngine.ChannelMode.FADE)) {
        if (this.enabled) {
          this.channel.fader.setValue(this.fromFader = 0);
          this.toFader = this.fader;
          this.channel.enabled.setValue(true);
        } else {
          this.fromFader = this.channel.fader.getValue();
          this.toFader = 0;
        }
      } else {
        this.channel.enabled.setValue(this.enabled);
        this.fromFader = this.channel.fader.getValue();
        this.toFader = this.fader;
      }
    }

    @Override
    protected void interpolate(double amount) {
      this.channel.fader.setValue(LXUtils.lerp(this.fromFader, this.toFader, amount));
    }

    @Override
    protected void finishTransition() {
      recall();
    }

    private static final String KEY_CHANNEL_PATH = "channelPath";
    private static final String KEY_CHANNEL_ENABLED = "channelEnabled";
    private static final String KEY_CHANNEL_FADER = "channelFader";

    @Override
    public void save(LX lx, JsonObject obj) {
      super.save(lx, obj);
      obj.addProperty(KEY_CHANNEL_PATH, this.channel.getCanonicalPath());
      obj.addProperty(KEY_CHANNEL_ENABLED, this.enabled);
      obj.addProperty(KEY_CHANNEL_FADER, this.fader);
    }

  }

  /**
   * View for which pattern is active on a channel
   */
  public class ActivePatternView extends View {

    private final LXChannel channel;
    private final int activePatternIndex;

    private ActivePatternView(LXChannel channel) {
      super(ViewScope.PATTERNS, ViewType.ACTIVE_PATTERN);
      this.channel = channel;
      this.activePatternIndex = channel.getActivePatternIndex();
    }

    private ActivePatternView(LX lx, JsonObject obj) {
      super(lx, obj);
      this.channel = (LXChannel) LXPath.get(lx, obj.get(KEY_CHANNEL_PATH).getAsString());
      this.activePatternIndex = obj.get(KEY_ACTIVE_PATTERN_INDEX).getAsInt();
    }

    @Override
    public LXCommand getCommand() {
      return new LXCommand.Channel.GoPattern(this.channel, channel.getPattern(this.activePatternIndex));
    }

    @Override
    protected boolean isDependentOf(LXComponent component) {
      return component.contains(this.channel);
    }

    @Override
    protected void recall() {
      this.channel.goPatternIndex(this.activePatternIndex);
    }

    private static final String KEY_CHANNEL_PATH = "channelPath";
    private static final String KEY_ACTIVE_PATTERN_INDEX = "activePatternIndex";

    @Override
    public void save(LX lx, JsonObject obj) {
      super.save(lx, obj);
      obj.addProperty(KEY_CHANNEL_PATH, this.channel.getCanonicalPath());
      obj.addProperty(KEY_ACTIVE_PATTERN_INDEX, this.activePatternIndex);
    }
  }

  public final BooleanParameter recall =
    new BooleanParameter("Recall", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Restores the values of this snapshot");

  public final BooleanParameter autoCycleEligible =
    new BooleanParameter("Cycle", true)
    .setDescription("Whether the snapshot is eligible for auto-cycle");


  public LXSnapshot(LX lx) {
    super(lx, "Snapshot");
    setParent(lx.engine.snapshots);
    addParameter("recall", this.recall);
    addParameter("autoCycleEligible", this.autoCycleEligible);
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (this.recall == p) {
      if (this.recall.isOn()) {
        this.recall.setValue(false);
        this.lx.engine.snapshots.recall(this);
      }
    }
  }

  // Package-only method for LXSnapshotEngine to update indices
  void setIndex(int index) {
    this.index = index;
  }

  @Override
  public String getPath() {
    return "snapshot/" + (this.index+1);
  }

  /**
   * Public accessor for the index of this snapshot in the list
   *
   * @return This snapshot's position in the global list
   */
  public int getIndex() {
    return this.index;
  }

  // Internal engine-only call, initializes a new snapshot with views of everything
  // relevant in the project scope. It's a bit of an arbitrary selection at the moment
  void initialize() {
    LX lx = getLX();
    addParameterView(ViewScope.OUTPUT, lx.engine.output.brightness);
    addParameterView(ViewScope.MIXER, lx.engine.mixer.crossfader);
    for (LXAbstractChannel bus : lx.engine.mixer.channels) {
      // Top-level bus settings
      addView(new ChannelFaderView(bus));
      addParameterView(ViewScope.MIXER, bus.crossfadeGroup);

      // Active pattern settings
      if (bus instanceof LXChannel) {
        LXChannel channel = (LXChannel) bus;
        LXPattern pattern = channel.getActivePattern();
        if (pattern != null) {
          addView(new ActivePatternView(channel));
          for (LXParameter p : pattern.getParameters()) {
            if (p != pattern.label) {
              addParameterView(ViewScope.PATTERNS, p);
            }
          }
        }
      }

      // Effect settings
      for (LXEffect effect : bus.effects) {
        if (effect.enabled.isOn()) {
          // If the effect is on, store all its parameters (including that it's enabled)
          for (LXParameter p : effect.getParameters()) {
            if (p != effect.label) {
              addParameterView(ViewScope.EFFECTS, p);
            }
          }
        } else {
          // If the effect is off, then we only recall that it is off
          addParameterView(ViewScope.EFFECTS, effect.enabled);
        }
      }
    }

    // Modulator settings
    for (LXModulator modulator : lx.engine.modulation.getModulators()) {
      for (LXParameter p : modulator.getParameters()) {
        addParameterView(ViewScope.MODULATION, p);
      }
    }
  }

  private void addParameterView(ViewScope scope, LXParameter p) {
    if (p instanceof ColorParameter) {
      // Don't add ColorParameter directly, let the sub-hue/sat/bright values do it
      return;
    }
    addView(new ParameterView(scope, p));
  }

  /**
   * Adds a view to this snapshot from prior saved state
   *
   * @param viewObj JSON serialized view
   * @return The new view object
   */
  public View addView(JsonObject viewObj) {
    ViewType type = ViewType.valueOf(viewObj.get(View.KEY_TYPE).getAsString());
    View view = null;
    switch (type) {
    case PARAMETER:
      view = new ParameterView(getLX(), viewObj);
      break;
    case ACTIVE_PATTERN:
      view = new ActivePatternView(getLX(), viewObj);
      break;
    case CHANNEL_FADER:
      view = new ChannelFaderView(getLX(), viewObj);
      break;
    }
    if (view == null) {
      LX.error("Invalid serialized LXSnapshot.View type: " + viewObj.get(View.KEY_TYPE).getAsString());
    } else {
      addView(view);
    }
    return view;
  }

  /**
   * Add a view to this snapshot
   *
   * @param view View
   */
  public void addView(View view) {
    Objects.requireNonNull(view, "May not LXShapshot.addView(null)");
    if (this.views.contains(view)) {
      throw new IllegalStateException("May not add same view instance twice: " + this + " " + view);
    }
    this.mutableViews.add(view);
  }

  /**
   * Remove a view from this snapshot
   *
   * @param view View to remove
   */
  public void removeView(LXSnapshot.View view) {
    if (!this.views.contains(view)) {
      throw new IllegalStateException("Cannot remove View that doesn't belong to snapshot: " + view);
    }
    this.mutableViews.remove(view);
    view.dispose();
  }

  boolean hasChannelFaderView(LXAbstractChannel channel) {
    for (View view : this.views) {
      if (view instanceof ChannelFaderView) {
        if (((ChannelFaderView) view).channel == channel) {
          return true;
        }
      }
    }
    return false;
  }

  ChannelFaderView getMissingChannelView(LXAbstractChannel channel) {
    return new LXSnapshot.ChannelFaderView(channel, false, 0);
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

  @Override
  public void dispose() {
    for (View view : this.views) {
      view.dispose();
    }
    this.mutableViews.clear();
    super.dispose();
  }

  private static final String KEY_VIEWS = "views";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_VIEWS, LXSerializable.Utils.toArray(lx, this.views));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    if (obj.has(KEY_VIEWS)) {
      JsonArray viewsArray = obj.getAsJsonArray(KEY_VIEWS);
      for (JsonElement viewElement : viewsArray) {
        addView(viewElement.getAsJsonObject());
      }
    }
  }

}
