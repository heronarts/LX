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
 * @author Justin K. Belcher <jkbelcher@gmail.com>
 */

package heronarts.lx.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.LXLayer;
import heronarts.lx.LXLayeredComponent;
import heronarts.lx.LXPath;
import heronarts.lx.LXSerializable;
import heronarts.lx.command.LXCommand;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.midi.MidiFilterParameter;
import heronarts.lx.midi.MidiSelector;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXMasterBus;
import heronarts.lx.mixer.LXPatternEngine;
import heronarts.lx.parameter.AggregateParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.pattern.PatternRack;
import heronarts.lx.utils.LXUtils;

/**
 * A snapshot holds a memory of the state of the program at a point in time.
 * The snapshot contains a collection of "views" which are memories of a piece
 * of state in the program at some time. Typically this is a parameter value,
 * but some special cases exist, like the active pattern on a channel.
 */
public abstract class LXSnapshot extends LXComponent {

  public interface Listener {
    public void snapshotDisposed(LXSnapshot snapshot);
    public void viewAdded(LXSnapshot snapshot, View view);
    public void viewRemoved(LXSnapshot snapshot, View view);
  }

  private final Map<String, View> viewPaths = new HashMap<>();

  private final List<View> mutableViews = new ArrayList<View>();

  /**
   * Public immutable list of all the views this snapshot comtains.
   */
  public final List<View> views = Collections.unmodifiableList(this.mutableViews);

  public static enum ViewScope {
    MIXER("Mixer"),
    PATTERNS("Pattern"),
    EFFECTS("Effect"),
    OUTPUT("Output"),
    MODULATION("Modulation"),
    GLOBAL("Global"),
    MASTER("Master");

    public final String label;

    private ViewScope(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
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
    ACTIVE_PATTERN,

    /**
     * The pattern which is active on a rack
     */
    RACK_PATTERN
  };

  /**
   * A view is a component of a snapshot, it's a single piece of the snapshot that
   * is "looking at" one piece of state.
   */
  public abstract sealed class View implements LXSerializable permits ParameterView, ChannelFaderView, ActivePatternView, RackPatternView {

    public final ViewScope scope;
    private final ViewType type;

    // Whether this view is active in a transition
    boolean activeFlag = false;

    /**
     * Whether this view is enabled for recall or not.
     */
    public final BooleanParameter enabled =
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
     * Gets a descriptive label for the parameter or field represented by the view
     *
     * @return Descriptive label for the parameter or field represented by the view
     */
    public abstract String getLabel();

    /**
     * Gets a description of the behavior of the parameter or field represented by the view
     *
     * @return Description of the behavior of the parameter or field represented by the view
     */
    public abstract String getDescription();

    /**
     * Gets the component that owns the parameter/field referenced by the view
     *
     * @return Component that owns the parameter/field referenced by the view
     */
    public abstract LXComponent getViewComponent();

    /**
     * Gets a unique path identifier for this view in the context of its snapshot
     *
     * @return Unique path identifier for this view in the context of its snapshot
     */
    public abstract String getViewPath();

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
  public final class ParameterView extends View {

    public final LXComponent component;
    public final LXParameter parameter;
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
      if (parameter instanceof AggregateParameter) {
        throw new IllegalStateException("Cannot store a snapshot view of an AggregateParameter");
      }
      this.parameter = parameter;
      this.value = this.parameter.getBaseValue();
      if (parameter instanceof DiscreteParameter) {
        this.intValue = ((DiscreteParameter) parameter).getBaseValuei();
        this.stringValue = null;
      } else if (parameter instanceof StringParameter) {
        this.intValue = 0;
        this.stringValue = ((StringParameter) parameter).getString();
      } else {
        this.intValue = 0;
        this.stringValue = null;
      }
      if (parameter instanceof LXNormalizedParameter) {
        this.normalizedValue = ((LXNormalizedParameter) parameter).getBaseNormalized();
      } else {
        this.normalizedValue = 0;
      }
    }

    private ParameterView(LX lx, JsonObject obj) {
      super(lx, obj);
      final String path = obj.get(KEY_PARAMETER_PATH).getAsString();

      if (isGlobalSnapshot() || path.startsWith(LXPath.ROOT_PREFIX)) {
        this.parameter = (LXParameter) LXPath.get(lx, path);
      } else {
        this.parameter = (LXParameter) LXPath.get(snapshotParameterScope, path);
      }
      if (this.parameter == null) {
        throw new IllegalStateException("Cannot create snapshot view of non-existent parameter: " + path);
      }
      this.component = this.parameter.getParent();
      if (this.component == null) {
        throw new IllegalStateException("Cannot restore a snapshot view of a parameter with no parent");
      }
      if (this.parameter instanceof AggregateParameter) {
        throw new IllegalStateException("Cannot restore a snapshot view of an AggregateParameter");
      }
      if (this.parameter instanceof DiscreteParameter) {
        this.value = this.intValue = obj.get(KEY_VALUE).getAsInt();
        this.stringValue = null;
        obj.addProperty(KEY_VALUE, this.intValue);
      } else if (this.parameter instanceof StringParameter) {
        final JsonElement value = obj.get(KEY_VALUE);
        this.stringValue = ((value == null) || (value instanceof JsonNull)) ? null : value.getAsString();
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
    public String getLabel() {
      String label = this.parameter.getLabel();
      AggregateParameter parent = this.parameter.getParentParameter();
      while (parent != null) {
        label = parent.getLabel() + " | " + label;
        parent = parent.getParentParameter();
      }
      return label;
    }

    @Override
    public String getDescription() {
      final String description = this.parameter.getDescription();
      return LXUtils.isEmpty(description) ? "<No Description>" : description;
    }

    @Override
    public LXComponent getViewComponent() {
      return this.component;
    }

    @Override
    public String getViewPath() {
      return this.parameter.getCanonicalPath(snapshotParameterScope);
    }

    public String getValueLabel() {
      return switch (this.parameter) {
        case StringParameter str -> (this.stringValue != null) ? this.stringValue : "<null>";
        case BooleanParameter bool -> this.normalizedValue > 0 ? "On": "Off";
        case DiscreteParameter discrete -> discrete.getOption(this.intValue);
        default -> this.parameter.getFormatter().format(this.value);
      };
    }

    @Override
    public LXCommand getCommand() {
      if (this.parameter instanceof DiscreteParameter discreteParameter) {
        return new LXCommand.Parameter.SetValue(discreteParameter, this.intValue);
      } else if (this.parameter instanceof StringParameter stringParameter) {
        return new LXCommand.Parameter.SetString(stringParameter, this.stringValue);
      } else {
        return new LXCommand.Parameter.SetValue(this.parameter, this.value);
      }
    }

    public LXParameter getParameter() {
      return this.parameter;
    }

    @Override
    protected boolean isDependentOf(LXComponent component) {
      return component.contains(this.parameter);
    }

    @Override
    protected void recall() {
      if (this.parameter instanceof DiscreteParameter discreteParameter) {
        discreteParameter.setValue(this.intValue);
      } else if (parameter instanceof StringParameter stringParameter) {
        stringParameter.setValue(this.stringValue);
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
      } else if (this.parameter instanceof LXNormalizedParameter normalizedParameter) {
        this.fromNormalized = normalizedParameter.getBaseNormalized();
      } else if (this.parameter instanceof DiscreteParameter discreteParameter) {
        this.fromInt = discreteParameter.getBaseValuei();
      } else {
        this.fromValue = this.parameter.getValue();
      }
    }

    @Override
    protected void interpolate(double amount) {
      if (this.parameter instanceof StringParameter) {
        // No interpolating strings
      } else if (this.parameter instanceof LXNormalizedParameter normalizedParameter) {
        normalizedParameter.setNormalized(LXUtils.lerp(this.fromNormalized, this.normalizedValue, amount));
      } else if (this.parameter instanceof DiscreteParameter discreteParameter) {
        discreteParameter.setValue(LXUtils.lerpi(this.fromInt, this.intValue, (float) amount));
      } else {
        this.parameter.setValue(LXUtils.lerp(this.fromValue, this.value, amount));
      }
    }

    @Override
    protected void finishTransition() {
      recall();
    }

    private static final String KEY_PARAMETER_PATH = "parameterPath";
    private static final String KEY_VALUE = "value";
    private static final String KEY_NORMALIZED_VALUE = "normalizedValue";

    @Override
    public void save(LX lx, JsonObject obj) {
      super.save(lx, obj);
      obj.addProperty(KEY_PARAMETER_PATH, getViewPath());
      if (this.parameter instanceof DiscreteParameter) {
        obj.addProperty(KEY_VALUE, this.intValue);
      } else if (this.parameter instanceof StringParameter) {
        obj.addProperty(KEY_VALUE, this.stringValue);
      } else {
        obj.addProperty(KEY_VALUE, this.value);
      }
      obj.addProperty(KEY_NORMALIZED_VALUE, this.normalizedValue);
    }

  }

  public final class ChannelFaderView extends View {

    public final LXAbstractChannel channel;
    private final boolean enabled;
    private final double faderValue;

    private double fromFaderValue, toFaderValue;
    private boolean wasEnabled;

    private ChannelFaderView(LXAbstractChannel channel) {
      this(channel, channel.enabled.isOn(), channel.fader.getBaseValue());
    }

    protected ChannelFaderView(LXAbstractChannel channel, boolean enabled, double fader) {
      super(ViewScope.MIXER, ViewType.CHANNEL_FADER);
      this.channel = channel;
      this.enabled = enabled;
      this.faderValue = fader;
    }

    private ChannelFaderView(LX lx, JsonObject obj) {
      super(ViewScope.MIXER, ViewType.CHANNEL_FADER);
      String channelPath = "";
      if (isClipSnapshot()) {
        this.channel = getClipChannel();
      } else {
        channelPath = obj.get(KEY_CHANNEL_PATH).getAsString();
        this.channel = (LXAbstractChannel) LXPath.get(lx, channelPath);
      }
      if (this.channel == null) {
        throw new IllegalStateException("Cannot create ChannelFaderView of non-existent channel: " + channelPath);
      }
      this.enabled = obj.get(KEY_CHANNEL_ENABLED).getAsBoolean();
      this.faderValue = obj.get(KEY_CHANNEL_FADER).getAsDouble();
    }

    @Override
    public String getLabel() {
      return this.channel.fader.getLabel();
    }

    @Override
    public String getDescription() {
      return this.channel.fader.getDescription();
    }

    @Override
    public LXComponent getViewComponent() {
      return this.channel;
    }

    @Override
    public String getViewPath() {
      return this.channel.fader.getCanonicalPath(snapshotParameterScope);
    }

    public double getFaderValue() {
      return this.faderValue;
    }

    @Override
    public LXCommand getCommand() {
      return new LXCommand.Channel.SetFader(this.channel, this.enabled, this.faderValue);
    }

    @Override
    protected boolean isDependentOf(LXComponent component) {
      return component.contains(this.channel);
    }

    @Override
    protected void recall() {
      this.channel.enabled.setValue(this.enabled);
      this.channel.fader.setValue(this.faderValue);
    }

    @Override
    protected void startTransition() {
      this.wasEnabled = this.channel.enabled.isOn();
      if ((this.wasEnabled != this.enabled) && (lx.engine.snapshots.channelMode.getEnum() == LXSnapshotEngine.ChannelMode.FADE)) {
        if (this.enabled) {
          this.channel.fader.setValue(this.fromFaderValue = 0);
          this.toFaderValue = this.faderValue;
          this.channel.enabled.setValue(true);
        } else {
          this.fromFaderValue = this.channel.fader.getBaseValue();
          this.toFaderValue = 0;
        }
      } else {
        this.channel.enabled.setValue(this.enabled);
        this.fromFaderValue = this.channel.fader.getBaseValue();
        this.toFaderValue = this.faderValue;
      }
    }

    @Override
    protected void interpolate(double amount) {
      this.channel.fader.setValue(LXUtils.lerp(this.fromFaderValue, this.toFaderValue, amount));
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
      if (isGlobalSnapshot()) {
        obj.addProperty(KEY_CHANNEL_PATH, this.channel.getCanonicalPath());
      }
      obj.addProperty(KEY_CHANNEL_ENABLED, this.enabled);
      obj.addProperty(KEY_CHANNEL_FADER, this.faderValue);
    }

  }

  /**
   * View for which pattern is active on a channel
   */
  public final class ActivePatternView extends View {

    public final LXChannel channel;
    public final LXPattern pattern;

    private ActivePatternView(LXChannel channel) {
      super(ViewScope.PATTERNS, ViewType.ACTIVE_PATTERN);
      this.channel = channel;
      this.pattern = channel.getActivePattern();
    }

    private ActivePatternView(LX lx, JsonObject obj) {
      super(lx, obj);
      String channelPath = "";
      if (isClipSnapshot()) {
        this.channel = getClipChannel();
      } else {
        channelPath = obj.get(KEY_CHANNEL_PATH).getAsString();
        this.channel = (LXChannel) LXPath.get(lx, channelPath);
      }
      if (this.channel == null) {
        throw new IllegalStateException("Cannot restore ActivePatternView for non-existent channel: " + channelPath);
      }
      final int patternIndex = obj.get(KEY_ACTIVE_PATTERN_INDEX).getAsInt();
      this.pattern = this.channel.patterns.get(patternIndex);
      if (this.pattern == null) {
        throw new IllegalStateException("Cannot restore ActivePatternView for missing pattern index: " + channelPath + "/pattern/" + patternIndex);
      }
    }

    @Override
    public String getLabel() {
      return "Active Pattern";
    }

    @Override
    public String getDescription() {
      return "Specifies which pattern is active on the channel.";
    }

    @Override
    public LXComponent getViewComponent() {
      return this.channel;
    }

    @Override
    public String getViewPath() {
      final String prefix = (this.channel == snapshotParameterScope) ? "" :
        this.channel.getCanonicalPath(snapshotParameterScope);
      return prefix + "/" + LXPatternEngine.PATH_ACTIVE_PATTERN;
    }

    @Override
    public LXCommand getCommand() {
      return new LXCommand.Channel.GoPattern(this.channel, this.pattern);
    }

    @Override
    protected boolean isDependentOf(LXComponent component) {
      return component.contains(this.pattern);
    }

    @Override
    protected void recall() {
      this.channel.goPattern(this.pattern);
    }

    private static final String KEY_CHANNEL_PATH = "channelPath";
    private static final String KEY_ACTIVE_PATTERN_INDEX = "activePatternIndex";

    @Override
    public void save(LX lx, JsonObject obj) {
      super.save(lx, obj);
      if (isGlobalSnapshot()) {
        obj.addProperty(KEY_CHANNEL_PATH, this.channel.getCanonicalPath(snapshotParameterScope));
      }
      obj.addProperty(KEY_ACTIVE_PATTERN_INDEX, this.pattern.getIndex());
    }
  }

  /**
   * View for which pattern is active on a rack. Painfully redundant with ActivePatternView
   * but easier to keep stored data format clear for backwards compatibility with a dedicated
   * type here.
   */
  public final class RackPatternView extends View {

    public final PatternRack rack;
    public final LXPattern pattern;

    private RackPatternView(PatternRack rack) {
      super(ViewScope.PATTERNS, ViewType.RACK_PATTERN);
      this.rack = rack;
      this.pattern = rack.patternEngine.getActivePattern();
    }

    private RackPatternView(LX lx, JsonObject obj) {
      super(lx, obj);
      final String rackPath = obj.get(KEY_RACK_PATH).getAsString();
      if (isClipSnapshot()) {
        this.rack = (PatternRack) LXPath.get(getClipChannel(), rackPath);
      } else {
        this.rack = (PatternRack) LXPath.get(lx, rackPath);
      }
      if (this.rack == null) {
        throw new IllegalStateException("Cannot restore RackPatternView for non-existent rack: " + rackPath);
      }
      final int patternIndex = obj.get(KEY_ACTIVE_PATTERN_INDEX).getAsInt();
      this.pattern = this.rack.patterns.get(patternIndex);
      if (this.pattern == null) {
        throw new IllegalStateException("Cannot restore RackPatternView for missing pattern index: " + rackPath + "/pattern/" + patternIndex);
      }
    }

    @Override
    public String getLabel() {
      return "Active Pattern";
    }

    @Override
    public String getDescription() {
      return "Specifies which pattern is active in the rack.";
    }

    @Override
    public LXComponent getViewComponent() {
      return this.rack;
    }

    @Override
    public String getViewPath() {
      return this.rack.getCanonicalPath(snapshotParameterScope) + "/" + LXPatternEngine.PATH_ACTIVE_PATTERN;
    }

    @Override
    public LXCommand getCommand() {
      return new LXCommand.Channel.GoPattern(this.rack, this.pattern);
    }

    @Override
    protected boolean isDependentOf(LXComponent component) {
      return component.contains(this.pattern);
    }

    @Override
    protected void recall() {
      this.rack.patternEngine.goPattern(this.pattern);
    }

    private static final String KEY_RACK_PATH = "rackPath";
    private static final String KEY_ACTIVE_PATTERN_INDEX = "activePatternIndex";

    @Override
    public void save(LX lx, JsonObject obj) {
      super.save(lx, obj);
      obj.addProperty(KEY_RACK_PATH, this.rack.getCanonicalPath(snapshotParameterScope));
      obj.addProperty(KEY_ACTIVE_PATTERN_INDEX, this.pattern.getIndex());
    }
  }

  /**
   * Scope that parameters are serialized within, or null. For global snapshots
   * this is null since the snapshot can refer to anything, but clip snapshots
   * are scoped to the bus that they live on and only refer to parameters within
   * that prefix.
   */
  private final LXComponent snapshotParameterScope;

  private boolean isInitialized = false;

  public final BoundedParameter transitionTimeSecs =
    new BoundedParameter("Transition Time", 1, .1, 180)
    .setDescription("Sets the duration of interpolated transitions between snapshots")
    .setUnits(LXParameter.Units.SECONDS);

  protected LXSnapshot(LX lx, LXComponent snapshotParameterScope) {
    super(lx, "Snapshot");
    this.snapshotParameterScope = snapshotParameterScope;
    addParameter("transitionTimeSecs", this.transitionTimeSecs);
  }

  private final List<Listener> listeners = new ArrayList<Listener>();

  public final void addListener(Listener listener) {
    Objects.requireNonNull(listener, "May not add null LXSnapshot.Listener");
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("May not add duplicate LXSnapshot.Listener: " + listener);
    }
    this.listeners.add(listener);
  }

  public final void removeListener(Listener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("May not remove non-registered LXSnapshot.Listener: " + listener);
    }
    if (this.inDispose) {
      this.disposeListeners.add(listener);
    } else {
      this.listeners.remove(listener);
    }
  }

  public boolean isGlobalSnapshot() {
    return (this instanceof LXGlobalSnapshot);
  }

  public boolean isClipSnapshot() {
    return (this instanceof LXClipSnapshot);
  }

  public StringParameter getLabelParameter() {
    return this.label;
  }

  public LXChannel getClipChannel() {
    return null;
  }

  public final void initialize() {
    if (!this.isInitialized) {
      initializeViews();
      this.isInitialized = true;
    }
  }

  /**
   * Update this snapshot to reflect the current program state
   */
  public void update() {
    clearViews();
    initializeViews();
    this.isInitialized = true;
  }

  protected abstract void initializeViews();

  protected void initializeGlobalBus(LXBus bus) {
    if (bus instanceof LXMasterBus) {
      // The master bus fader is just like a normal parameter, but it lives
      // in the MASTER section
      addParameterView(ViewScope.MASTER, bus.fader);
    }

    if (bus instanceof LXAbstractChannel channel) {
      // But channel faders work in conjunction with the channel
      // enabled state, which is more complex, so we use the
      // special ChannelFaderView here
      addView(new ChannelFaderView(channel));
      addParameterView(ViewScope.MIXER, channel.crossfadeGroup);
    }

    if (bus instanceof LXChannel channel) {
      addParameterView(ViewScope.PATTERNS, channel.patternEngine.compositeMode);
    }

    initializeClipBus(bus);
  }

  protected void initializeClipBus(LXBus bus) {
    if (bus instanceof LXChannel channel) {
      if (channel.patternEngine.compositeMode.getEnum() == LXPatternEngine.CompositeMode.PLAYLIST) {
        // Only need to add settings for the active pattern
        LXPattern pattern = channel.getActivePattern();
        if (pattern != null) {
          addView(new ActivePatternView(channel));
          addPatternView(pattern);
        }
      } else {
        for (LXPattern pattern : channel.patterns) {
          if (pattern.enabled.isOn()) {
            // Store all settings for any pattern that is active
            addPatternView(pattern);
          } else {
            // Just store enabled (disabled) state for a pattern that's off
            addParameterView(ViewScope.PATTERNS, pattern.enabled);
          }
        }
      }
    }

    // Effect settings
    for (LXEffect effect : bus.effects) {
      addEffectView(effect);
    }
  }

  protected void addEffectView(LXEffect effect) {
    if (effect.enabled.isOn()) {
      // If the effect is on, store all its parameters (including that it's enabled)
      addDeviceView(ViewScope.EFFECTS, effect);
    } else {
      // If the effect is off, then we only recall that it is off
      addParameterView(ViewScope.EFFECTS, effect.enabled);
    }
  }

  protected void addPatternView(LXPattern pattern) {
    addDeviceView(ViewScope.PATTERNS, pattern);
    if (pattern instanceof PatternRack rack) {
      switch (rack.patternEngine.compositeMode.getEnum()) {
        case PLAYLIST -> {
          LXPattern activePattern = rack.patternEngine.getActivePattern();
          if (activePattern != null) {
            addView(new RackPatternView(rack));
            addPatternView(activePattern);
          }
        }
        case BLEND ->{
          for (LXPattern rackPattern : rack.patterns) {
            if (rackPattern.enabled.isOn()) {
              // Store all settings for any pattern that is active (implicitly includes enabled state)
              addPatternView(rackPattern);
            } else {
              // Just store enabled (disabled) state for a pattern that's off
              addParameterView(ViewScope.PATTERNS, rackPattern.enabled);
            }
          }
        }
      }
    }
    for (LXEffect effect : pattern.effects) {
      addEffectView(effect);
    }
  }

  protected LXParameter _checkParameter(LXParameter p) {
    AggregateParameter ap = p.getParentParameter();
    return (ap != null) ? ap : p;
  }

  protected void addDeviceView(ViewScope scope, LXDeviceComponent device) {
    for (LXParameter p : device.getParameters()) {
      if (device.isSnapshotControl(_checkParameter(p))) {
        addParameterView(scope, p);
      }
    }
    for (LXLayer layer : device.getLayers()) {
      addLayeredView(scope, layer);
    }
    for (LXComponent child : device.automationChildren.values()) {
      addDeviceChildView(scope, child);
    }
  }

  protected void addLayeredView(ViewScope scope, LXLayeredComponent component) {
    for (LXParameter p : component.getParameters()) {
      if (component.isSnapshotControl(_checkParameter(p))) {
        addParameterView(scope, p);
      }
    }
    for (LXLayer layer : component.getLayers()) {
      addLayeredView(scope, layer);
    }
  }

  protected void addDeviceChildView(ViewScope scope, LXComponent component) {
    for (LXParameter p : component.getParameters()) {
      if (component.isSnapshotControl(_checkParameter(p))) {
        addParameterView(scope, p);
      }
    }
  }

  protected void addParameterView(ViewScope scope, LXParameter p) {
    final AggregateParameter parent = p.getParentParameter();
    if (parent instanceof MidiSelector || parent instanceof MidiFilterParameter) {
      // Do not store MIDI settings in snapshots
      return;
    }
    if (p instanceof TriggerParameter) {
      // Do not store Trigger parameters in snapshots
      return;
    }
    if (p instanceof AggregateParameter) {
      // Don't add AggregateParameters directly, let the sub-values restore
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
    final ViewType type = ViewType.valueOf(viewObj.get(View.KEY_TYPE).getAsString());
    final View view = switch (type) {
    case PARAMETER -> new ParameterView(getLX(), viewObj);
    case ACTIVE_PATTERN -> new ActivePatternView(getLX(), viewObj);
    case RACK_PATTERN -> new RackPatternView(getLX(), viewObj);
    case CHANNEL_FADER -> new ChannelFaderView(getLX(), viewObj);
    default -> null;
    };
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
    final String viewPath = view.getViewPath();
    if (this.viewPaths.containsKey(viewPath)) {
      LX.error("Attempting to register two Snapshot views to the same path " + viewPath + ": " + view.getClass().getName());
    }
    this.viewPaths.put(viewPath, view);
    this.mutableViews.add(view);
    this.listeners.forEach(listener -> listener.viewAdded(this, view));
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
    this.viewPaths.remove(view.getViewPath());
    this.mutableViews.remove(view);
    this.listeners.forEach(listener -> listener.viewRemoved(this, view));
    view.dispose();
  }

  /**
   * Get a view by its scoped path in this snapshot
   *
   * @param viewPath Path to the view in this snapshot (matching view.getViewPath())
   * @return View
   */
  public View getView(String viewPath) {
    return this.viewPaths.get(viewPath);
  }

  private void clearViews() {
    // Remove all the existing views
    for (int i = this.views.size() - 1; i >= 0; --i) {
      removeView(this.views.get(i));
    }
  }

  boolean hasChannelFaderView(LXAbstractChannel channel) {
    for (View view : this.views) {
      if (view instanceof ChannelFaderView channelFaderView) {
        if (channelFaderView.channel == channel) {
          return true;
        }
      }
    }
    return false;
  }

  ChannelFaderView getMissingChannelView(LXAbstractChannel channel) {
    return new LXGlobalSnapshot.ChannelFaderView(channel, false, 0);
  }

  private boolean inDispose = false;
  private final List<Listener> disposeListeners = new ArrayList<>();

  @Override
  public void dispose() {
    clearViews();
    this.inDispose = true;
    this.listeners.forEach(listener -> listener.snapshotDisposed(this));
    this.inDispose = false;
    this.listeners.removeAll(this.disposeListeners);
    this.disposeListeners.clear();
    super.dispose();
    this.listeners.forEach(listener -> LX.warning("Stranded LXSnapshot.Listener: " + listener));
    this.listeners.clear();
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

    clearViews();
    if (obj.has(KEY_VIEWS)) {
      JsonArray viewsArray = obj.getAsJsonArray(KEY_VIEWS);
      for (JsonElement viewElement : viewsArray) {
        try {
          addView(viewElement.getAsJsonObject());
        } catch (Exception x) {
          LX.error(x, "Invalid view in snapshot: " + viewElement.toString());
        }
      }
    }

    this.isInitialized = true;
  }

}
