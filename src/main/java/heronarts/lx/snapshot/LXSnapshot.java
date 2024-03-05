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
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXMasterBus;
import heronarts.lx.parameter.AggregateParameter;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
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
public abstract class LXSnapshot extends LXComponent {

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
    MODULATION,
    GLOBAL,
    MASTER;
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
    public final BooleanParameter enabled = new BooleanParameter("Enabled", true)
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

    public LXParameter getParameter() {
      return this.parameter;
    }

    @Override
    protected boolean isDependentOf(LXComponent component) {
      return component.contains(this.parameter);
    }

    @Override
    protected void recall() {
      if (this.parameter instanceof DiscreteParameter) {
        ((DiscreteParameter) this.parameter).setValue(this.intValue);
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
        this.fromNormalized = ((LXNormalizedParameter) this.parameter).getBaseNormalized();
      } else if (this.parameter instanceof DiscreteParameter) {
        this.fromInt = ((DiscreteParameter) this.parameter).getBaseValuei();
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

    private static final String KEY_PARAMETER_PATH = "parameterPath";
    private static final String KEY_VALUE = "value";
    private static final String KEY_NORMALIZED_VALUE = "normalizedValue";

    @Override
    public void save(LX lx, JsonObject obj) {
      super.save(lx, obj);
      obj.addProperty(KEY_PARAMETER_PATH, this.parameter.getCanonicalPath(snapshotParameterScope));
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

  public class ChannelFaderView extends View {

    private final LXAbstractChannel channel;
    private final boolean enabled;
    private final double fader;

    private double fromFader, toFader;
    private boolean wasEnabled;

    private ChannelFaderView(LXAbstractChannel channel) {
      this(channel, channel.enabled.isOn(), channel.fader.getBaseValue());
    }

    protected ChannelFaderView(LXAbstractChannel channel, boolean enabled, double fader) {
      super(ViewScope.MIXER, ViewType.CHANNEL_FADER);
      this.channel = channel;
      this.enabled = enabled;
      this.fader = fader;
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
          this.fromFader = this.channel.fader.getBaseValue();
          this.toFader = 0;
        }
      } else {
        this.channel.enabled.setValue(this.enabled);
        this.fromFader = this.channel.fader.getBaseValue();
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
      if (isGlobalSnapshot()) {
        obj.addProperty(KEY_CHANNEL_PATH, this.channel.getCanonicalPath());
      }
      obj.addProperty(KEY_CHANNEL_ENABLED, this.enabled);
      obj.addProperty(KEY_CHANNEL_FADER, this.fader);
    }

  }

  /**
   * View for which pattern is active on a channel
   */
  public class ActivePatternView extends View {

    private final LXChannel channel;
    private final LXPattern pattern;

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
   * Scope that parameters are serialized within, or null. For global snapshots
   * this is null since the snapshot can refer to anything, but clip snapshots
   * are scoped to the bus that they live on and only refer to parameters within
   * that prefix.
   */
  private final LXComponent snapshotParameterScope;

  public final BoundedParameter transitionTimeSecs =
    new BoundedParameter("Transition Time", 1, .1, 180)
    .setDescription("Sets the duration of interpolated transitions between snapshots")
    .setUnits(LXParameter.Units.SECONDS);

  protected LXSnapshot(LX lx, LXComponent snapshotParameterScope) {
    super(lx, "Snapshot");
    this.snapshotParameterScope = snapshotParameterScope;
    addParameter("transitionTimeSecs", this.transitionTimeSecs);
  }

  public boolean isGlobalSnapshot() {
    return (this instanceof LXGlobalSnapshot);
  }

  public boolean isClipSnapshot() {
    return (this instanceof LXClipSnapshot);
  }

  public LXChannel getClipChannel() {
    return null;
  }

  /**
   * Update this snapshot to reflect the current program state
   */
  public void update() {
    clearViews();
    initialize();
  }

  public abstract void initialize();

  protected void initializeGlobalBus(LXBus bus) {
    if (bus instanceof LXMasterBus) {
      // The master bus fader is just like a normal parameter, but it lives
      // in the MASTER section
      addParameterView(ViewScope.MASTER, bus.fader);
    }

    if (bus instanceof LXAbstractChannel) {
      // But channel faders work in conjunction with the channel
      // enabled state, which is more complex, so we use the
      // special ChannelFaderView here
      LXAbstractChannel channel = (LXAbstractChannel) bus;
      addView(new ChannelFaderView(channel));
      addParameterView(ViewScope.MIXER, channel.crossfadeGroup);
    }

    if (bus instanceof LXChannel) {
      addParameterView(ViewScope.PATTERNS, ((LXChannel) bus).compositeMode);
    }

    initializeClipBus(bus);
  }

  protected void initializeClipBus(LXBus bus) {

    if (bus instanceof LXChannel) {
      final LXChannel channel = (LXChannel) bus;
      if (channel.compositeMode.getEnum() == LXChannel.CompositeMode.PLAYLIST) {
        // Only need to add settings for the active pattern
        LXPattern pattern = channel.getActivePattern();
        if (pattern != null) {
          addView(new ActivePatternView(channel));
          addPatternView(pattern);
        }
      } else {
        for (LXPattern pattern : channel.patterns) {
          if (pattern.enabled.isOn()) {
            // Store all settings for any pattern that is active, explicitly including enabled state
            addParameterView(ViewScope.PATTERNS, pattern.enabled);
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
    for (LXEffect effect : pattern.effects) {
      addEffectView(effect);
    }
  }

  protected void addDeviceView(ViewScope scope, LXDeviceComponent device) {
    for (LXParameter p : device.getParameters()) {
      AggregateParameter ap = p.getParentParameter();
      if (ap != null) {
        p = ap;
      }
      if (device.isSnapshotControl(p)) {
        addParameterView(scope, p);
      }
    }

    for (LXLayer layer : device.getLayers()) {
      addLayeredView(scope, layer);
    }
  }

  protected void addLayeredView(ViewScope scope, LXLayeredComponent component) {
    for (LXParameter p : component.getParameters()) {
      if (p != component.label) {
        addParameterView(scope, p);
      }
    }
    for (LXLayer layer : component.getLayers()) {
      addLayeredView(scope, layer);
    }
  }

  protected void addParameterView(ViewScope scope, LXParameter p) {
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

  private void clearViews() {
    // Remove all the existing views
    for (int i = this.views.size() - 1; i >= 0; --i) {
      removeView(this.views.get(i));
    }
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
    return new LXGlobalSnapshot.ChannelFaderView(channel, false, 0);
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
  }

}
