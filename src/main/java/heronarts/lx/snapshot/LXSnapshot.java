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
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.osc.OscMessage;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.pattern.LXPattern;

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

  /**
   * Type of snapshot view
   */
  public static enum ViewType {
    /**
     * A parameter with a value
     */
    PARAMETER,

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

    private final ViewType type;

    /**
     * Whether this view is enabled for recall or not.
     */
    public final BooleanParameter enabled = (BooleanParameter)
      new BooleanParameter("Enabled", true)
      .setMappable(false)
      .setDescription("Whether this view is enabled in the snapshot");

    private View(ViewType type) {
      this.type = type;
    }

    private View(LX lx, JsonObject obj) {
      LXSerializable.Utils.loadBoolean(this.enabled, obj, KEY_ENABLED);
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

    private static final String KEY_TYPE = "type";
    private static final String KEY_ENABLED = "enabled";

    @Override
    public void save(LX lx, JsonObject obj) {
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

    private ParameterView(LXParameter parameter) {
      super(ViewType.PARAMETER);
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

    private double getBaseValue() {
      if (this.parameter instanceof CompoundParameter) {
        return ((CompoundParameter) this.parameter).getBaseValue();
      }
      return this.parameter.getValue();
    }

    private static final String KEY_PARAMETER_PATH = "parameterPath";
    private static final String KEY_VALUE = "value";

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
    }

  }

  /**
   * View for which pattern is active on a channel
   */
  public class ActivePatternView extends View {

    private final LXChannel channel;
    private final int activePatternIndex;

    private ActivePatternView(LXChannel channel) {
      super(ViewType.ACTIVE_PATTERN);
      this.channel = channel;
      this.activePatternIndex = channel.getActivePatternIndex();
    }

    private ActivePatternView(LX lx, JsonObject obj) {
      super(lx, obj);
      this.channel = (LXChannel) LXPath.get(lx, obj.get(KEY_CHANNEL_PATH).getAsString());
      this.activePatternIndex = obj.get(KEY_ACTIVE_PATTERN_INDEX).getAsInt();
    }

    @Override
    protected boolean isDependentOf(LXComponent component) {
      return component.contains(this.channel);
    }

    @Override
    protected void recall() {
      this.channel.goIndex(this.activePatternIndex);
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

  public LXSnapshot(LX lx) {
    super(lx, "Snapshot");
    setParent(lx.engine.snapshots);
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
    addParameterView(lx.engine.output.brightness);
    addParameterView(lx.engine.mixer.crossfader);
    for (LXAbstractChannel bus : lx.engine.mixer.channels) {
      // Top-level bus settings
      addParameterView(bus.enabled);
      addParameterView(bus.fader);
      addParameterView(bus.crossfadeGroup);

      // Active pattern settings
      if (bus instanceof LXChannel) {
        LXChannel channel = (LXChannel) bus;
        LXPattern pattern = channel.getActivePattern();
        if (pattern != null) {
          addView(new ActivePatternView(channel));
          for (LXParameter p : pattern.getParameters()) {
            if (p != pattern.label) {
              addParameterView(p);
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
              addParameterView(p);
            }
          }
        } else {
          // If the effect is off, then we only recall that it is off
          addParameterView(effect.enabled);
        }
      }
    }

    // Modulator settings
    for (LXModulator modulator : lx.engine.modulation.getModulators()) {
      for (LXParameter p : modulator.getParameters()) {
        addParameterView(p);
      }
    }
  }

  private void addParameterView(LXParameter p) {
    addView(new ParameterView(p));
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

  /**
   * Recall this snapshot, apply all of its values
   */
  public void recall() {
    for (View view : this.views) {
      if (view.enabled.isOn()) {
        view.recall();
      }
    }
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
  public boolean handleOscMessage(OscMessage message, String[] parts, int index) {
    if (index >= parts.length) {
      if (message.size() > 0) {
        // Any argument at all is fine for now
        recall();
      }
    }
    return super.handleOscMessage(message, parts, index);
  }

  @Override
  public void dispose() {
    for (View view : this.views) {
      view.dispose();
    }
    this.mutableViews.clear();
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
