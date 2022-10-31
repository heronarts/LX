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

package heronarts.lx.pattern;

import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.LXTime;
import heronarts.lx.blend.LXBlend;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.ObjectParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;

/**
 * A pattern is the core object that the animation engine uses to generate
 * colors for all the points.
 */
public abstract class LXPattern extends LXDeviceComponent implements LXComponent.Renamable, LXOscComponent {

  /**
   * Placeholder pattern for when a class is missing
   */
  public static class Placeholder extends LXPattern implements LXComponent.Placeholder {

    private final LX.InstantiationException instantiationException;
    private String placeholderClassName;
    private JsonObject patternObj = null;

    public Placeholder(LX lx, LX.InstantiationException instantiationException) {
      super(lx);
      this.instantiationException = instantiationException;
    }

    @Override
    public String getPlaceholderTypeName() {
      return "Pattern";
    }

    @Override
    public String getPlaceholderClassName() {
      return this.placeholderClassName;
    }

    @Override
    public LX.InstantiationException getInstantiationException() {
      return this.instantiationException;
    }

    @Override
    public void save(LX lx, JsonObject object) {
      super.save(lx, object);

      // Just re-save exactly what was loaded
      if (this.patternObj != null) {
        for (Map.Entry<String, JsonElement> entry : this.patternObj.entrySet()) {
          object.add(entry.getKey(), entry.getValue());
        }
      }
    }

    @Override
    public void load(LX lx, JsonObject object) {
      super.load(lx, object);
      this.placeholderClassName = object.get(LXComponent.KEY_CLASS).getAsString();
      this.patternObj = object;
    }

    @Override
    protected void run(double deltaMs) {
    }

  }

  private int index = -1;

  private int intervalBegin = -1;

  private int intervalEnd = -1;

  private double compositeDampingLevel = 1;

  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", true)
    .setDescription("Whether the pattern is eligible for playlist cycling or compositing");

  public final TriggerParameter recall =
    new TriggerParameter("Recall", () -> { getChannel().goPattern(this); })
    .setDescription("Recalls this pattern to become active on the channel");

  public final ObjectParameter<LXBlend> compositeMode =
    new ObjectParameter<LXBlend>("Composite Blend", new LXBlend[1])
    .setDescription("Specifies the blending function used for blending of patterns on the channel");

  private LXBlend activeCompositeBlend;

  public final CompoundParameter compositeLevel =
    new CompoundParameter("Composite Level", 1)
    .setDescription("Alpha level to composite pattern at when in channel blend mode");

  private final LXParameterListener onEnabled = p -> {
    final boolean isEnabled = this.enabled.isOn();
    final LXChannel channel = getChannel();
    if ((channel != null) && (channel.compositeMode.getEnum() == LXChannel.CompositeMode.BLEND)) {
      if (isEnabled) {
        channel.onPatternEnabled(this);
      }
      if (!channel.compositeDampingEnabled.isOn()) {
        if (isEnabled) {
          onActive();
        } else {
          onInactive();
        }
      }
    }
  };

  private final LXParameterListener onCompositeMode = p -> {
    this.activeCompositeBlend.onInactive();
    this.activeCompositeBlend = this.compositeMode.getObject();
    this.activeCompositeBlend.onActive();
  };

  protected double runMs = 0;

  public final Profiler profiler = new Profiler();

  public class Profiler {
    public long runNanos = 0;
  }

  protected LXPattern(LX lx) {
    super(lx);
    this.label.setDescription("The name of this pattern");

    // NOTE: this used to be internal, but it's not anymore...
    addLegacyInternalParameter("autoCycleEligible", this.enabled);
    addParameter("enabled", this.enabled);

    addParameter("recall", this.recall);
    addParameter("compositeMode", this.compositeMode);
    addParameter("compositeLevel", this.compositeLevel);

    updateCompositeBlendOptions();
    this.compositeMode.addListener(this.onCompositeMode);

    this.enabled.addListener(this.onEnabled);

  }

  @Override
  public boolean isHiddenControl(LXParameter parameter) {
    return
      parameter == this.recall ||
      parameter == this.compositeMode ||
      parameter == this.compositeLevel ||
      parameter == this.enabled;
  }

  @Override
  public String getPath() {
    return LXChannel.PATH_PATTERN + "/" + (this.index + 1);
  }

  public void updateCompositeBlendOptions() {
    for (LXBlend blend : this.compositeMode.getObjects()) {
      if (blend != null) {
        blend.dispose();
      }
    }
    this.compositeMode.setObjects(this.lx.engine.mixer.instantiateChannelBlends());
    this.activeCompositeBlend = this.compositeMode.getObject();
    this.activeCompositeBlend.onActive();
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public int getIndex() {
    return this.index;
  }

  /**
   * Gets the channel that this pattern is loaded in. May be null if the pattern is
   * not yet loaded onto any channel.
   *
   * @return Channel pattern is loaded onto
   */
  public final LXChannel getChannel() {
    return (LXChannel) getParent();
  }

  /**
   * Called by the engine when pattern is loaded onto a channel. This may only be
   * called once, by the engine. Do not call directly.
   *
   * @param channel Channel pattern is loaded onto
   * @return this
   */
  public final LXPattern setChannel(LXChannel channel) {
    setParent(channel);
    return this;
  }

  /**
   * Set an interval during which this pattern is allowed to run. Begin and end
   * times are specified in minutes of the daytime. So midnight corresponds to
   * the value of 0, 360 would be 6:00am, 1080 would be 18:00 (or 6:00pm)
   *
   * @param begin Interval start time
   * @param end Interval end time
   * @return this
   */
  public LXPattern setInterval(int begin, int end) {
    this.intervalBegin = begin;
    this.intervalEnd = end;
    return this;
  }

  /**
   * Clears a timer interval set to this pattern.
   *
   * @return this
   */
  public LXPattern clearInterval() {
    this.intervalBegin = this.intervalEnd = -1;
    return this;
  }

  /**
   * Tests whether there is an interval for this pattern.
   *
   * @return true if there is an interval
   */
  public final boolean hasInterval() {
    return (this.intervalBegin >= 0) && (this.intervalEnd >= 0);
  }

  /**
   * Tests whether this pattern is in an eligible interval.
   *
   * @return true if the pattern has an interval, and is currently in it.
   */
  public final boolean isInInterval() {
    if (!this.hasInterval()) {
      return false;
    }
    int now = LXTime.hour() * 60 + LXTime.minute();
    if (this.intervalBegin < this.intervalEnd) {
      // Normal daytime interval
      return (now >= this.intervalBegin) && (now < this.intervalEnd);
    } else {
      // Wrapping around midnight
      return (now >= this.intervalBegin) || (now < this.intervalEnd);
    }
  }

  /**
   * Sets whether this pattern is eligible for automatic selection.
   *
   * @param eligible Whether eligible for auto-rotation
   * @return this
   */
  public final LXPattern setAutoCycleEligible(boolean eligible) {
    this.enabled.setValue(eligible);
    return this;
  }

  /**
   * Toggles the eligibility state of this pattern.
   *
   * @return this
   */
  public final LXPattern toggleAutoCycleEligible() {
    this.enabled.toggle();
    return this;
  }

  /**
   * Determines whether this pattern is eligible to be run at the moment. A
   * pattern is eligible if its eligibility flag has not been set to false, and
   * if it either has no interval, or is currently in its interval.
   *
   * @return True if pattern is eligible to run now
   */
  public final boolean isAutoCycleEligible() {
    return this.enabled.isOn() && (!this.hasInterval() || this.isInInterval());
  }

  public void initCompositeDamping(boolean wasActivePattern) {
    final boolean isEnabled = this.enabled.isOn();
    this.compositeDampingLevel = isEnabled ? 1 : 0;
    if (isEnabled && !wasActivePattern) {
      onActive();
    } else if (!isEnabled && wasActivePattern) {
      onInactive();
    }
  }

  public void updateCompositeDamping(double deltaMs, boolean dampingOn, double dampingTimeSecs) {
    final boolean isEnabled = this.enabled.isOn();
    if (!dampingOn) {
      this.compositeDampingLevel = isEnabled ? 1 : 0;
    } else if (isEnabled) {
      if (this.compositeDampingLevel < 1) {
        if (this.compositeDampingLevel == 0) {
          onActive();
        }
        this.compositeDampingLevel = LXUtils.min(1, this.compositeDampingLevel + deltaMs / (dampingTimeSecs * 1000));
      }
    } else {
      if (this.compositeDampingLevel > 0) {
        this.compositeDampingLevel = LXUtils.max(0, this.compositeDampingLevel - deltaMs / (dampingTimeSecs * 1000));
        if (this.compositeDampingLevel == 0) {
          onInactive();
        }
      }
    }
  }

  public double getCompositeDampingLevel() {
    return this.compositeDampingLevel;
  }

  @Override
  protected final void onLoop(double deltaMs) {
    long runStart = System.nanoTime();
    this.runMs += deltaMs;
    this.run(deltaMs);
    this.profiler.runNanos = System.nanoTime() - runStart;
  }

  /**
   * Main pattern loop function. Invoked in a render loop. Subclasses must
   * implement this function.
   *
   * @param deltaMs Number of milliseconds elapsed since last invocation
   */
  protected abstract void run(double deltaMs);

  /**
   * Subclasses may override this method. It will be invoked when the pattern is
   * about to become active. Patterns may take care of any initialization needed
   * or reset parameters if desired.
   */
  public/* abstract */void onActive() {
  }

  /**
   * Subclasses may override this method. It will be invoked when the pattern is
   * no longer active. Resources may be freed if desired.
   */
  public/* abstract */void onInactive() {
  }

  /**
   * Subclasses may override this method. It will be invoked if a transition
   * into this pattern is taking place. This will be called after onActive. This
   * is not invoked on an already-running pattern. It is only called on the new
   * pattern.
   */
  public/* abstract */void onTransitionStart() {
  }

  /**
   * Subclasses may override this method. It will be invoked when the transition
   * into this pattern is complete.
   */
  public/* abstract */void onTransitionEnd() {
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    this.compositeDampingLevel = this.enabled.isOn() ? 1 : 0;
  }

  @Override
  public void dispose() {
    this.enabled.removeListener(this.onEnabled);
    this.compositeMode.removeListener(this.onCompositeMode);
    super.dispose();
  }

}
