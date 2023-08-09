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

package heronarts.lx.modulator;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXRunnableComponent;
import heronarts.lx.midi.MidiFilterParameter;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.LXParameter;

/**
 * A Modulator is an abstraction for a variable with a value that varies over
 * time, such as an envelope or a low frequency oscillator. Some modulators run
 * continuously, others may halt after they reach a certain value.
 */
public abstract class LXModulator extends LXRunnableComponent implements LXComponent.Renamable, LXParameter {

  /**
   * A market annotation which enables a registered modulator to appear in the global section
   */
  @Documented
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Global {
    String value();
  }

  /**
   * A market annotation which enables a registered modulator to appear in the device section
   */
  @Documented
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Device {
    String value();
  }

  /**
   * Placeholder pattern for when a class is missing
   */
  public static class Placeholder extends LXModulator implements LXComponent.Placeholder {

    private final LX.InstantiationException instantiationException;
    private String placeholderClassName;
    private JsonObject modulatorObj;

    public Placeholder(LX lx, LX.InstantiationException instantiationException, JsonObject modulatorObj) {
      super("Placeholder");
      this.instantiationException = instantiationException;
      this.placeholderClassName = modulatorObj.get(LXComponent.KEY_CLASS).getAsString();
      this.modulatorObj = modulatorObj;
    }

    @Override
    public String getPlaceholderTypeName() {
      return "Modulator";
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
      if (this.modulatorObj != null) {
        for (Map.Entry<String, JsonElement> entry : this.modulatorObj.entrySet()) {
          object.add(entry.getKey(), entry.getValue());
        }
      }
    }

    @Override
    public void load(LX lx, JsonObject object) {
      super.load(lx, object);
      this.placeholderClassName = object.get(LXComponent.KEY_CLASS).getAsString();
      this.modulatorObj = object;
    }

    @Override
    protected double computeValue(double deltaMs) {
      return 0;
    }

  }

  private Formatter formatter = null;

  private Units units = Units.NONE;

  private Polarity polarity = Polarity.UNIPOLAR;

  private boolean isMappingSource = true;

  private int index = 0;

  private boolean autoStart = true;

  /**
   * The current computed value of this modulator.
   */
  private double value = 0;

  private Throwable crash = null;

  public final BooleanParameter crashed =
    new BooleanParameter("Crashed", false)
    .setDescription("Set to true by the engine if this component fails in an unexpected way");

  public final MidiFilterParameter midiFilter =
    new MidiFilterParameter("MIDI Filter", true)
    .setDescription("MIDI filter settings for this modulator");

  /**
   * Utility default constructor
   *
   * @param label Label
   */
  protected LXModulator(String label) {
    super(label);
    addInternalParameter("midiFilter", this.midiFilter);
  }

  public Throwable getCrash() {
    return this.crash;
  }

  public String getCrashStackTrace() {
    try (StringWriter sw = new StringWriter()){
      this.crash.printStackTrace(new PrintWriter(sw));
      return sw.toString();
    } catch (IOException iox) {
      // This is fucked
      return null;
    }
  }

  protected LXModulator disableAutoStart() {
    this.autoStart = false;
    return this;
  }

  /**
   * Sets the index of this modulator in its parent list
   *
   * @param index Modulator index
   * @return this
   */
  public LXModulator setIndex(int index) {
    this.index = index;
    return this;
  }

  /**
   * Returns the ordering index of this modulator in its parent
   *
   * @return Modulator index
   */
  public int getIndex() {
    return this.index;
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
  public LXParameter setComponent(LXComponent component, String path) {
    if (path != null) {
      throw new UnsupportedOperationException("setComponent() path not supported for LXModulator");
    }
    setParent(component);
    return this;
  }

  @Override
  public String getPath() {
    return "modulator/" + (this.index + 1);
  }

  public LXModulator setFormatter(Formatter formatter) {
    this.formatter = formatter;
    return this;
  }

  public Formatter getFormatter() {
    return (this.formatter != null) ? this.formatter : getUnits();
  }

  public LXModulator setUnits(Units units) {
    this.units = units;
    return this;
  }

  public Units getUnits() {
    return this.units;
  }

  public LXModulator setPolarity(Polarity polarity) {
    this.polarity = polarity;
    return this;
  }

  public Polarity getPolarity() {
    return this.polarity;
  }

  public LXModulator setMappingSource(boolean isMappingSource) {
    this.isMappingSource = isMappingSource;
    return this;
  }

  public boolean isMappingSource() {
    return this.isMappingSource;
  }

  /**
   * Retrieves the current value of the modulator in full precision
   *
   * @return Current value of the modulator
   */
  public final double getValue() {
    return this.value;
  }

  /**
   * Retrieves the current value of the modulator in floating point precision.
   *
   * @return Current value of the modulator, cast to float
   */
  public final float getValuef() {
    return (float) this.getValue();
  }

  /**
   * Set the modulator to a certain value in its cycle.
   *
   * @param value The value to apply
   * @return This modulator, for method chaining
   */
  public final LXModulator setValue(double value) {
    return setValue(value, true);
  }

  protected final LXModulator setValue(double value, boolean notify) {
    this.value = value;
    if (notify) {
      this.onSetValue(value);
    }
    return this;
  }

  /**
   * Subclasses may override when actions are necessary on value change.
   *
   * @param value New value
   */
  protected/* abstract */void onSetValue(double value) {
  }

  /**
   * Helper for subclasses to update value in situations where it needs to be
   * recomputed. This cannot be overriden, and subclasses may assume that it
   * ONLY updates the internal value without triggering any other
   * recomputations.
   *
   * @param value Value for modulator
   * @return this, for method chaining
   */
  protected final LXModulator updateValue(double value) {
    this.value = value;
    return this;
  }

  /**
   * Auto start a newly created modulator. Can be overriden by subclasses
   */
  public void autostart() {
    if (this.autoStart) {
      start();
    }
  }

  @Override
  public void loop(double deltaMs) {
    if (!this.crashed.isOn()) {
      try {
        super.loop(deltaMs);
      } catch (Throwable x) {
        LX.error(x, "Unexpected error in modulator loop " + getClass().getName() + ": " + x.getLocalizedMessage());
        this.lx.pushError(x, "Modulator " + LXComponent.getComponentName(getClass()) + " crashed due to an unexpected error.\n" + x.getLocalizedMessage());
        this.crash = x;
        this.crashed.setValue(true);
      }
    }
  }

  /**
   * Applies updates to the modulator for the specified number of milliseconds.
   * This method is invoked by the core engine.
   *
   * @param deltaMs Milliseconds to advance by
   */
  @Override
  protected final void run(double deltaMs) {
    this.value = this.computeValue(deltaMs);
  }

  /**
   * Implementation method to advance the modulator's internal state. Subclasses
   * must provide and update value appropriately.
   *
   * @param deltaMs Number of milliseconds to advance by
   * @return Computed value
   */
  protected abstract double computeValue(double deltaMs);

}
