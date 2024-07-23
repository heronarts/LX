/**
 * Copyright 2017- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.command;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.LXPath;
import heronarts.lx.LXPresetComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.clip.LXClip;
import heronarts.lx.color.ColorParameter;
import heronarts.lx.color.LXDynamicColor;
import heronarts.lx.color.LXPalette;
import heronarts.lx.color.LXSwatch;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.midi.LXMidiEngine;
import heronarts.lx.midi.LXMidiMapping;
import heronarts.lx.midi.LXShortMessage;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXGroup;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.modulation.LXModulationContainer;
import heronarts.lx.modulation.LXModulationEngine;
import heronarts.lx.modulation.LXParameterModulation;
import heronarts.lx.modulation.LXTriggerModulation;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.snapshot.LXSnapshot;
import heronarts.lx.snapshot.LXGlobalSnapshot;
import heronarts.lx.snapshot.LXSnapshotEngine;
import heronarts.lx.structure.JsonFixture;
import heronarts.lx.structure.LXFixture;
import heronarts.lx.structure.LXStructure;
import heronarts.lx.structure.view.LXViewDefinition;

/**
 * An LXCommand is an operation that may be performed by the engine, potentially
 * in two directions, supporting and Undo operation. If you are working directly
 * with the LX API, you are free to ignore this interface. However, if you are
 * building a higher-level UI that you would like to integrate with the undo
 * system, it is best to invoke operations via calls to lx.command.perform().
 */
public abstract class LXCommand {

  public static class InvalidCommandException extends Exception {

    private static final long serialVersionUID = 1L;

    protected InvalidCommandException(Exception cause) {
      super(cause.getMessage(), cause);
    }

  }

  /**
   * This reference class is used because the LXCommand engine might have
   * actions in it that refer to components which have been deleted by
   * subsequent operations. If those operations are since un-done, a new object
   * will have been re-created with the same ID. When it comes time to undo
   * *this* action, we need to refer to the appropriate object.
   *
   * @param <T> Type of component being referenced
   */
  public static class ComponentReference<T extends LXComponent> {

    private final LX lx;
    private final int componentId;

    public ComponentReference(T component) {
      this.lx = component.getLX();
      this.componentId = component.getId();
    }

    @SuppressWarnings("unchecked")
    public T get() {
      return (T) this.lx.getComponent(this.componentId);
    }
  }

  public static class ParameterReference<T extends LXParameter> {

    private final T rawParameter;
    private final Class<? extends LXComponent> componentCls;
    private final ComponentReference<LXComponent> component;
    private final String parameterPath;

    public ParameterReference(T parameter) {
      LXComponent component = parameter.getParent();
      if (component != null) {
        // If a parameter is registered to a component, then we keep its
        // location
        // by reference. This way, if a series of other undo or redo actions
        // destroys
        // and restores the object, we'll still point to the correct place
        this.component = new ComponentReference<LXComponent>(component);
        this.componentCls = component.getClass();
        this.parameterPath = parameter.getPath();
        this.rawParameter = null;
      } else {
        // For unregistered parameters, store a raw handle
        this.rawParameter = parameter;
        this.component = null;
        this.componentCls = null;
        this.parameterPath = null;
      }
    }

    @SuppressWarnings("unchecked")
    public T get() {
      if (this.rawParameter != null) {
        return this.rawParameter;
      }
      LXComponent component = this.component.get();
      if (component == null) {
        LX.error("Bad internal state, component " + this.component.componentId + " of type " + componentCls.getName() + " does not exist, cannot get parameter: " + this.parameterPath);
        return null;
      }
      return (T) component.getParameter(this.parameterPath);
    }
  }

  /**
   * Short description of a command, to explain it to the user
   *
   * @return short description of command
   */
  public abstract String getDescription();

  final String getName() {
    try {
      return getDescription();
    } catch (Exception x) {
      String className = getClass().getName();
      int subIndex = className.indexOf(".LXCommand.");
      return className.substring(subIndex + ".LXCommand.".length());
    }
  }

  /**
   * Perform the given command
   *
   * @param lx LX instance
   * @throws InvalidCommandException if the command is invalid
   */
  public abstract void perform(LX lx) throws InvalidCommandException;

  /**
   * Undo the command, after it has been performed
   *
   * @param lx LX instance
   * @throws InvalidCommandException if the command is invalid
   */
  public abstract void undo(LX lx) throws InvalidCommandException;

  /**
   * May return true if a command should be ignore for the purposes of undo
   *
   * @return Whether to ignore command for purposes of undo
   */
  public boolean isIgnored() {
    return false;
  }


  public static abstract class RemoveComponent extends LXCommand {

    private final List<Modulation.RemoveModulation> removeModulations = new ArrayList<Modulation.RemoveModulation>();
    private final List<Modulation.RemoveTrigger> removeTriggers = new ArrayList<Modulation.RemoveTrigger>();
    private final List<Midi.RemoveMapping> removeMidiMappings = new ArrayList<Midi.RemoveMapping>();
    private final List<Snapshots.RemoveView> removeSnapshotViews = new ArrayList<Snapshots.RemoveView>();

    private void _removeModulations(LXModulationEngine modulation, LXComponent component) {
      List<LXCompoundModulation> compounds = modulation.findModulations(component, modulation.modulations);
      if (compounds != null) {
        for (LXCompoundModulation compound : compounds) {
          this.removeModulations.add(new Modulation.RemoveModulation(modulation, compound));
        }
      }
    }

    private void _removeTriggers(LXModulationEngine modulation, LXComponent component) {
      List<LXTriggerModulation> triggers = modulation.findModulations(component, modulation.triggers);
      if (triggers != null) {
        for (LXTriggerModulation trigger : triggers) {
          this.removeTriggers.add(new Modulation.RemoveTrigger(modulation, trigger));
        }
      }
    }

    private void removeMidiMappings(LXMidiEngine midi, LXComponent component) {
      List<LXMidiMapping> mappings = midi.findMappings(component);
      if (mappings != null) {
        for (LXMidiMapping mapping : mappings) {
          this.removeMidiMappings.add(new Midi.RemoveMapping(midi.getLX(), mapping));
        }
      }
    }

    protected void removeModulationMappings(LXModulationEngine modulation, LXComponent component) {
      _removeModulations(modulation, component);
      _removeTriggers(modulation, component);
    }

    protected void removeSnapshotViews(LXSnapshotEngine snapshots, LXComponent component) {
      List<LXSnapshot.View> views = snapshots.findSnapshotViews(component);
      if (views  != null) {
        for (LXSnapshot.View view : views) {
          this.removeSnapshotViews.add(new Snapshots.RemoveView(view));
        }
      }
    }

    protected RemoveComponent(LXComponent component) {
      // Tally up all the modulations and triggers that relate to this component and must be restored!
      LXComponent parent = component.getParent();
      while (parent != null) {
        if (parent instanceof LXModulationContainer) {
          removeModulationMappings(((LXModulationContainer) parent).getModulationEngine(), component);
        }
        parent = parent.getParent();
      }

      // Also top level mappings and snapshot views
      removeMidiMappings(component.getLX().engine.midi, component);
      removeSnapshotViews(component.getLX().engine.snapshots, component);
    }

    @Override
    public void undo(LX lx) throws InvalidCommandException {
      for (Modulation.RemoveModulation modulation : this.removeModulations) {
        modulation.undo(lx);
      }
      for (Modulation.RemoveTrigger trigger : this.removeTriggers) {
        trigger.undo(lx);
      }
      for (Midi.RemoveMapping mapping : this.removeMidiMappings) {
        mapping.undo(lx);
      }
      for (Snapshots.RemoveView view : this.removeSnapshotViews) {
        view.undo(lx);
      }
    }
  }

  /**
   * Name space for parameter commands
   */
  public static class Parameter {

    public static class Reset extends LXCommand {
      private final ParameterReference<LXParameter> parameter;
      private final double originalValue;
      private final String originalString;

      public Reset(LXParameter parameter) {
        this.parameter = new ParameterReference<LXParameter>(parameter);
        this.originalValue = parameter.getBaseValue();
        this.originalString = (parameter instanceof StringParameter) ? ((StringParameter) parameter).getString() : null;
      }

      @Override
      public String getDescription() {
        return "Reset " + this.parameter.get().getLabel();
      }

      @Override
      public void perform(LX lx) {
        this.parameter.get().reset();
      }

      @Override
      public void undo(LX lx) {
        LXParameter parameter = this.parameter.get();
        if (parameter instanceof StringParameter) {
          ((StringParameter) parameter).setValue(this.originalString);
        }
        parameter.setValue(this.originalValue);
      }
    }

    public static class SetValue extends LXCommand {

      private final boolean isDiscrete;

      private final ParameterReference<DiscreteParameter> discreteParameter;
      private final int originalDiscreteValue;
      private int newDiscreteValue;

      private final ParameterReference<LXParameter> genericParameter;
      private final double originalGenericValue;
      private double newGenericValue;

      public SetValue(DiscreteParameter parameter, int value) {
        this.isDiscrete = true;
        this.discreteParameter = new ParameterReference<DiscreteParameter>(parameter);
        this.originalDiscreteValue = parameter.getBaseValuei();
        this.newDiscreteValue = value;

        this.genericParameter = null;
        this.originalGenericValue = 0;
        this.newGenericValue = 0;
      }

      public SetValue(LXParameter parameter, double value) {
        if (parameter instanceof DiscreteParameter) {
          this.isDiscrete = true;
          this.discreteParameter = new ParameterReference<DiscreteParameter>((DiscreteParameter) parameter);
          this.originalDiscreteValue = ((DiscreteParameter) parameter).getBaseValuei();
          this.newDiscreteValue = (int) value;
          this.genericParameter = null;
          this.originalGenericValue = 0;
          this.newGenericValue = 0;
        } else {
          this.isDiscrete = false;
          this.genericParameter = new ParameterReference<LXParameter>(parameter);
          this.originalGenericValue = parameter.getBaseValue();
          this.newGenericValue = value;
          this.discreteParameter = null;
          this.originalDiscreteValue = 0;
          this.newDiscreteValue = 0;
        }
      }

      public LXParameter getParameter() {
        return this.isDiscrete ? this.discreteParameter.get()
          : this.genericParameter.get();
      }

      public SetValue updateDiscrete(int value) {
        if (!this.isDiscrete) {
          throw new IllegalStateException("Cannot update non-discrete parameter with integer value");
        }
        this.newDiscreteValue = value;
        return this;
      }

      public SetValue update(double value) {
        if (this.isDiscrete) {
          throw new IllegalStateException("Cannot update discrete parameter with double value");
        }
        this.newGenericValue = value;
        return this;
      }

      @Override
      public String getDescription() {
        return "Change " + getParameter().getLabel();
      }

      @Override
      public void perform(LX lx) {
        if (this.isDiscrete) {
          this.discreteParameter.get().setValue(this.newDiscreteValue);
        } else {
          this.genericParameter.get().setValue(this.newGenericValue);
        }
      }

      @Override
      public void undo(LX lx) {
        if (this.isDiscrete) {
          this.discreteParameter.get().setValue(this.originalDiscreteValue);
        } else {
          this.genericParameter.get().setValue(this.originalGenericValue);
        }
      }

    }

    public static class SetIndex extends SetValue {
      public SetIndex(DiscreteParameter parameter, int index) {
        super(parameter, index + parameter.getMinValue());
      }
    }

    public static class SetColor extends LXCommand {

      private final ParameterReference<ColorParameter> colorParameter;
      private final double originalHue;
      private final double originalSaturation;

      private double updateHue;
      private double updateSaturation;

      public SetColor(ColorParameter colorParameter) {
        this(colorParameter, colorParameter.hue.getBaseValue(), colorParameter.saturation.getBaseValue());
      }

      public SetColor(ColorParameter colorParameter, double hue, double saturation) {
        this.colorParameter = new ParameterReference<ColorParameter>(colorParameter);
        this.originalHue = colorParameter.hue.getBaseValue();
        this.originalSaturation = colorParameter.saturation.getBaseValue();
        this.updateHue = hue;
        this.updateSaturation = saturation;
      }

      @Override
      public String getDescription() {
        return "Update Color";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        this.colorParameter.get().hue.setValue(this.updateHue);
        this.colorParameter.get().saturation.setValue(this.updateSaturation);
      }

      public SetColor update(double hue, double saturation) {
        this.updateHue = hue;
        this.updateSaturation = saturation;
        return this;
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        this.colorParameter.get().hue.setValue(this.originalHue);
        this.colorParameter.get().saturation.setValue(this.originalSaturation);
      }

    }

    public static class Increment extends LXCommand {

      private final ParameterReference<DiscreteParameter> parameter;
      private final int originalValue;
      private final int amount;
      private boolean alwaysWrap;

      public Increment(DiscreteParameter parameter) {
        this(parameter, 1);
      }

      public Increment(DiscreteParameter parameter, boolean alwaysWrap) {
        this(parameter, 1, alwaysWrap);
      }

      public Increment(DiscreteParameter parameter, int amount) {
        this(parameter, amount, false);
      }

      public Increment(DiscreteParameter parameter, int amount, boolean alwaysWrap) {
        this.parameter = new ParameterReference<DiscreteParameter>(parameter);
        this.originalValue = parameter.getBaseValuei();
        this.amount = amount;
        this.alwaysWrap = alwaysWrap;
      }

      @Override
      public String getDescription() {
        return "Change " + this.parameter.get().getLabel();
      }

      @Override
      public void perform(LX lx) {
        if (this.alwaysWrap) {
          this.parameter.get().increment(this.amount, true);
        } else {
          this.parameter.get().increment(this.amount);
        }
      }

      @Override
      public void undo(LX lx) {
        this.parameter.get().setValue(this.originalValue);
      }

    }

    public static class Decrement extends LXCommand {

      private final ParameterReference<DiscreteParameter> parameter;
      private final int originalValue;
      private final int amount;
      private final boolean alwaysWrap;

      public Decrement(DiscreteParameter parameter) {
        this(parameter, 1, false);
      }

      public Decrement(DiscreteParameter parameter, int amount) {
        this(parameter, amount, false);
      }

      public Decrement(DiscreteParameter parameter, boolean alwaysWrap) {
        this(parameter, 1, alwaysWrap);
      }

      public Decrement(DiscreteParameter parameter, int amount, boolean alwaysWrap) {
        this.parameter = new ParameterReference<DiscreteParameter>(parameter);
        this.originalValue = parameter.getBaseValuei();
        this.amount = amount;
        this.alwaysWrap = alwaysWrap;
      }

      @Override
      public String getDescription() {
        return "Change " + this.parameter.get().getLabel();
      }

      @Override
      public void perform(LX lx) {
        if (this.alwaysWrap) {
          this.parameter.get().decrement(this.amount, true);
        } else {
          this.parameter.get().decrement(this.amount);
        }
      }

      @Override
      public void undo(LX lx) {
        this.parameter.get().setValue(this.originalValue);
      }

    }

    public static class Toggle extends LXCommand {

      private final ParameterReference<BooleanParameter> parameter;
      private final boolean originalValue;

      public Toggle(BooleanParameter parameter) {
        this.parameter = new ParameterReference<BooleanParameter>(parameter);
        this.originalValue = parameter.isOn();
      }

      @Override
      public String getDescription() {
        return "Toggle " + this.parameter.get().getLabel();
      }

      @Override
      public void perform(LX lx) {
        this.parameter.get().toggle();
      }

      @Override
      public void undo(LX lx) {
        this.parameter.get().setValue(this.originalValue);
      }

    }

    public static class SetNormalized extends LXCommand {

      private final ParameterReference<LXNormalizedParameter> parameter;
      private final double originalValue;
      private double newValue;

      public SetNormalized(LXNormalizedParameter parameter) {
        this(parameter, parameter.getBaseNormalized());
      }

      public SetNormalized(BooleanParameter parameter, boolean value) {
        this(parameter, value ? 1 : 0);
      }

      public SetNormalized(LXNormalizedParameter parameter, double newValue) {
        this.parameter = new ParameterReference<LXNormalizedParameter>(parameter);
        this.originalValue = parameter.getBaseNormalized();
        this.newValue = newValue;
      }

      public LXNormalizedParameter getParameter() {
        return this.parameter.get();
      }

      @Override
      public String getDescription() {
        return "Change " + this.parameter.get().getLabel();
      }

      @Override
      public void undo(LX lx) {
        this.parameter.get().setNormalized(this.originalValue);
      }

      @Override
      public void perform(LX lx) {
        this.parameter.get().setNormalized(this.newValue);
      }

      public SetNormalized update(double newValue) {
        this.newValue = newValue;
        return this;
      }
    }

    public static class SetString extends LXCommand {
      private final ParameterReference<StringParameter> parameter;
      private final String originalValue;
      private final String newValue;

      public SetString(StringParameter parameter, String value) {
        this.parameter = new ParameterReference<StringParameter>(parameter);
        this.originalValue = parameter.getString();
        this.newValue = value;
      }

      @Override
      public void undo(LX lx) {
        this.parameter.get().setValue(this.originalValue);
      }

      @Override
      public String getDescription() {
        return "Change " + this.parameter.get().getLabel();
      }

      @Override
      public void perform(LX lx) {
        this.parameter.get().setValue(this.newValue);
      }
    }
  }

  public static class Channel {

    public static class SetFader extends LXCommand {

      private final Parameter.SetNormalized setEnabled;
      private final Parameter.SetValue setFader;

      public SetFader(LXAbstractChannel channel, boolean enabled, double fader) {
        this.setEnabled = new Parameter.SetNormalized(channel.enabled, enabled);
        this.setFader = new Parameter.SetValue(channel.fader, fader);
      }

      @Override
      public String getDescription() {
        return "Set Channel Fader";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        this.setEnabled.perform(lx);
        this.setFader.perform(lx);
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        this.setEnabled.undo(lx);
        this.setFader.undo(lx);
      }
    }

    public static class AddPattern extends LXCommand {

      private final ComponentReference<LXChannel> channel;
      private final Class<? extends LXPattern> patternClass;
      private ComponentReference<LXPattern> pattern = null;
      private JsonObject patternObj;

      public AddPattern(LXChannel channel, Class<? extends LXPattern> patternClass) {
        this(channel, patternClass, null);
      }

      public AddPattern(LXChannel channel, Class<? extends LXPattern> patternClass, JsonObject patternObject) {
        this.channel = new ComponentReference<LXChannel>(channel);
        this.patternClass = patternClass;
        this.patternObj = patternObject;
      }

      @Override
      public String getDescription() {
        return "Add Pattern";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        try {
          LXPattern instance = lx.instantiatePattern(this.patternClass);
          if (this.patternObj != null) {
            instance.load(lx, this.patternObj);
          }
          // New pattern, we need to store its ID for future redo operations...
          this.patternObj = LXSerializable.Utils.toObject(instance);
          this.channel.get().addPattern(instance);
          this.pattern = new ComponentReference<LXPattern>(instance);
        } catch (LX.InstantiationException x) {
          throw new InvalidCommandException(x);
        }
      }

      @Override
      public void undo(LX lx) {
        if (this.pattern == null) {
          throw new IllegalStateException("Pattern was not successfully added, cannot undo");
        }
        this.channel.get().removePattern(this.pattern.get());
      }

    }

    public static class RemovePattern extends RemoveComponent {

      private final ComponentReference<LXChannel> channel;
      private final ComponentReference<LXPattern> pattern;
      private final JsonObject patternObj;
      private final int patternIndex;
      private final boolean isActive;
      private final boolean isFocused;

      public RemovePattern(LXChannel channel, LXPattern pattern) {
        super(pattern);
        this.channel = new ComponentReference<LXChannel>(channel);
        this.pattern = new ComponentReference<LXPattern>(pattern);
        this.patternObj = LXSerializable.Utils.toObject(pattern);
        this.patternIndex = pattern.getIndex();
        this.isActive = channel.getActivePattern() == pattern;
        this.isFocused = channel.getFocusedPattern() == pattern;
      }

      @Override
      public String getDescription() {
        return "Delete Pattern";
      }

      @Override
      public void perform(LX lx) {
        this.channel.get().removePattern(this.pattern.get());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        LXChannel channel = this.channel.get();
        try {
          LXPattern pattern = lx.instantiatePattern(this.patternObj.get(LXComponent.KEY_CLASS).getAsString());
          pattern.load(lx, this.patternObj);
          channel.addPattern(pattern, this.patternIndex);
          if (this.isActive) {
            channel.goPattern(pattern);
          }
          if (this.isFocused) {
            channel.focusedPattern.setValue(pattern.getIndex());
          }
          super.undo(lx);
        } catch (LX.InstantiationException x) {
          throw new InvalidCommandException(x);
        }
      }
    }

    public static class MovePattern extends LXCommand {

      private final ComponentReference<LXChannel> channel;
      private final ComponentReference<LXPattern> pattern;
      private final int fromIndex;
      private final int toIndex;

      public MovePattern(LXChannel channel, LXPattern pattern, int toIndex) {
        this.channel = new ComponentReference<LXChannel>(channel);
        this.pattern = new ComponentReference<LXPattern>(pattern);
        this.fromIndex = pattern.getIndex();
        this.toIndex = toIndex;
      }

      @Override
      public String getDescription() {
        return "Move Pattern";
      }

      @Override
      public void perform(LX lx) {
        this.channel.get().movePattern(this.pattern.get(), this.toIndex);
      }

      @Override
      public void undo(LX lx) {
        this.channel.get().movePattern(this.pattern.get(), this.fromIndex);
      }
    }

    public static class GoPattern extends LXCommand {

      private final ComponentReference<LXChannel> channel;
      private final ComponentReference<LXPattern> prevPattern;
      private final ComponentReference<LXPattern> nextPattern;

      public GoPattern(LXChannel channel, LXPattern nextPattern) {
        this.channel = new ComponentReference<LXChannel>(channel);
        this.prevPattern = new ComponentReference<LXPattern>(channel.getActivePattern());
        this.nextPattern = new ComponentReference<LXPattern>(nextPattern);
      }

      @Override
      public String getDescription() {
        return "Change Pattern";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        this.channel.get().goPattern(this.nextPattern.get());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        this.channel.get().goPattern(this.prevPattern.get());
      }
    }

    public static class PatternCycle extends LXCommand {

      private final ComponentReference<LXChannel> channel;
      private final ComponentReference<LXPattern> prevPattern;
      private ComponentReference<LXPattern> targetPattern;

      public PatternCycle(LXChannel channel) {
        this.channel = new ComponentReference<LXChannel>(channel);
        if (channel.isPlaylist() && !channel.isInTransition()) {
          final LXPattern prev = channel.getActivePattern();
          this.prevPattern = (prev != null) ? new ComponentReference<LXPattern>(prev) : null;
        } else {
          this.prevPattern = null;
        }
      }

      @Override
      public String getDescription() {
        return "Pattern Cycle";
      }

      @Override
      public boolean isIgnored() {
        return (this.prevPattern == null);
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        final LXChannel channel = this.channel.get();
        if (this.targetPattern == null) {
          channel.triggerPatternCycle.trigger();
          this.targetPattern = new ComponentReference<LXPattern>(channel.getTargetPattern());
        } else {
          this.channel.get().goPattern(this.targetPattern.get());
        }
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        if (this.prevPattern != null) {
          this.channel.get().goPattern(this.prevPattern.get());
        }
      }
    }

    private static ComponentReference<LXComponent> validateEffectParent(LXComponent parent) {
      if (! ((parent instanceof LXBus) || (parent instanceof LXPattern))) {
        throw new IllegalArgumentException("Parent of an LXEffect must be an LXBus or LXPattern");
      }
      return new ComponentReference<LXComponent>(parent);
    }

    public static class AddEffect extends LXCommand {

      private final ComponentReference<LXComponent> parent;
      private final Class<? extends LXEffect> effectClass;
      private ComponentReference<LXEffect> effect = null;
      private JsonObject effectObj = null;

      public AddEffect(LXComponent parent, Class<? extends LXEffect> effectClass) {
        this(parent, effectClass, null);
      }

      public AddEffect(LXComponent parent, Class<? extends LXEffect> effectClass, JsonObject effectObj) {
        this.parent = validateEffectParent(parent);
        this.effectClass = effectClass;
        this.effectObj = effectObj;
      }

      @Override
      public String getDescription() {
        return "Add Effect";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        try {
          LXEffect instance = lx.instantiateEffect(this.effectClass);
          if (this.effectObj != null) {
            instance.load(lx, this.effectObj);
          }
          this.effectObj = LXSerializable.Utils.toObject(instance);
          LXComponent parent = this.parent.get();
          if (parent instanceof LXBus) {
            ((LXBus) parent).addEffect(instance);
          } else if (parent instanceof LXPattern) {
            ((LXPattern) parent).addEffect(instance);
          }
          this.effect = new ComponentReference<LXEffect>(instance);
        } catch (LX.InstantiationException x) {
          throw new InvalidCommandException(x);
        }
      }

      @Override
      public void undo(LX lx) {
        if (this.effect == null) {
          throw new IllegalStateException("Effect was not successfully added, cannot undo");
        }
        LXComponent parent = this.parent.get();
        if (parent instanceof LXBus) {
          ((LXBus) parent).removeEffect(this.effect.get());
        } else if (parent instanceof LXPattern) {
          ((LXPattern) parent).removeEffect(this.effect.get());
        }
      }
    }

    public static class RemoveEffect extends RemoveComponent {

      private final ComponentReference<LXComponent> parent;
      private final ComponentReference<LXEffect> effect;
      private final JsonObject effectObj;
      private final int effectIndex;

      public RemoveEffect(LXComponent parent, LXEffect effect) {
        super(effect);
        this.parent = validateEffectParent(parent);
        this.effect = new ComponentReference<LXEffect>(effect);
        this.effectObj = LXSerializable.Utils.toObject(effect);
        this.effectIndex = effect.getIndex();
      }

      @Override
      public String getDescription() {
        return "Remove Effect";
      }

      @Override
      public void perform(LX lx) {
        if (this.effect.get().locked.isOn()) {
          throw new IllegalStateException("Locked effects cannot be removed, UI should disallow this");
        }
        LXComponent parent = this.parent.get();
        if (parent instanceof LXBus) {
          ((LXBus) parent).removeEffect(this.effect.get());
        } else if (parent instanceof LXPattern) {
          ((LXPattern) parent).removeEffect(this.effect.get());
        }
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        try {
          LXEffect effect = lx.instantiateEffect(this.effectObj.get(LXComponent.KEY_CLASS).getAsString());
          effect.load(lx, effectObj);
          LXComponent parent = this.parent.get();
          if (parent instanceof LXBus) {
            ((LXBus) parent).addEffect(effect, this.effectIndex);
          } else if (parent instanceof LXPattern) {
            ((LXPattern) parent).addEffect(effect, this.effectIndex);
          }
          super.undo(lx);
        } catch (LX.InstantiationException x) {
          throw new InvalidCommandException(x);
        }
      }
    }

    public static class MoveEffect extends LXCommand {

      private final ComponentReference<LXComponent> parent;
      private final ComponentReference<LXEffect> effect;
      private final int fromIndex;
      private final int toIndex;

      public MoveEffect(LXComponent parent, LXEffect effect, int toIndex) {
        this.parent = validateEffectParent(parent);
        this.effect = new ComponentReference<LXEffect>(effect);
        this.fromIndex = effect.getIndex();
        this.toIndex = toIndex;
      }

      @Override
      public String getDescription() {
        return "Move Effect";
      }

      @Override
      public void perform(LX lx) {
        LXComponent parent = this.parent.get();
        if (parent instanceof LXBus) {
          ((LXBus) parent).moveEffect(this.effect.get(), this.toIndex);
        } else if (parent instanceof LXPattern) {
          ((LXPattern) parent).moveEffect(this.effect.get(), this.toIndex);
        }
      }

      @Override
      public void undo(LX lx) {
        LXComponent parent = this.parent.get();
        if (parent instanceof LXBus) {
          ((LXBus) parent).moveEffect(this.effect.get(), this.fromIndex);
        } else if (parent instanceof LXPattern) {
          ((LXPattern) parent).moveEffect(this.effect.get(), this.fromIndex);
        }
      }
    }

  }

  public static class Device {

    public static class LoadPreset extends LXCommand {

      private final ComponentReference<LXComponent> device;
      private final JsonObject deviceObj;
      private final File file;

      public LoadPreset(LXComponent device, File file) {
        if (!(device instanceof LXPresetComponent)) {
          throw new IllegalArgumentException("Cannot load a preset for a non-preset device: " + device.getClass().getName());
        }
        this.device = new ComponentReference<LXComponent>(device);
        this.deviceObj = LXSerializable.Utils.toObject(device);
        this.file = file;
      }

      @Override
      public String getDescription() {
        return "Load Preset " + this.file.getName();
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        this.device.get().loadPreset(this.file);
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        this.device.get().load(lx, this.deviceObj);
      }
    }

    private static abstract class RemoteControls extends LXCommand {
      protected final ComponentReference<LXDeviceComponent> device;
      protected final String[] oldCustomControls;

      protected String[] toPaths(LXDeviceComponent device, LXListenableNormalizedParameter[] remoteControls) {
        if (remoteControls == null) {
          return null;
        }
        String[] paths = new String[remoteControls.length];
        for (int i = 0; i < remoteControls.length; ++i) {
          paths[i] = remoteControls[i] == null ? null : remoteControls[i].getCanonicalPath(device);
        }
        return paths;
      }

      protected LXListenableNormalizedParameter[] toControls(String[] paths) {
        LXDeviceComponent device = this.device.get();
        LXListenableNormalizedParameter[] remoteControls = new LXListenableNormalizedParameter[paths.length];
        for (int i = 0; i < paths.length; ++i) {
          remoteControls[i] = (paths[i] == null) ? null : (LXListenableNormalizedParameter) LXPath.getParameter(device, paths[i]);
        }
        return remoteControls;
      }

      protected RemoteControls(LXDeviceComponent device) {
        this.device = new ComponentReference<LXDeviceComponent>(device);
        this.oldCustomControls = toPaths(device, device.getCustomRemoteControls());
      }
    }

    public static class SetRemoteControls extends RemoteControls {

      private final String[] newCustomControls;

      public SetRemoteControls(LXDeviceComponent device, LXListenableNormalizedParameter[] remoteControls) {
        super(device);
        this.newCustomControls = toPaths(device, remoteControls);
      }

      @Override
      public String getDescription() {
        return "Update Remote Controls";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        this.device.get().setCustomRemoteControls(toControls(this.newCustomControls));
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        if (this.oldCustomControls == null) {
          this.device.get().clearCustomRemoteControls();
        } else {
          this.device.get().setCustomRemoteControls(toControls(this.oldCustomControls));
        }
      }
    }

    public static class ClearRemoteControls extends RemoteControls {

      public ClearRemoteControls(LXDeviceComponent device) {
        super(device);
      }

      @Override
      public String getDescription() {
        return "Clear Remote Controls";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        this.device.get().clearCustomRemoteControls();
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        this.device.get().setCustomRemoteControls(toControls(this.oldCustomControls));
      }
    }
  }

  public static class Mixer {

    public static class AddChannel extends LXCommand {

      private final Class<? extends LXPattern> patternClass;
      private ComponentReference<LXChannel> channel;
      private JsonObject channelObj = null;
      private final int index;

      public AddChannel() {
        this(null, null, -1);
      }

      public AddChannel(JsonObject channelObj) {
        this(channelObj, null, -1);
      }

      public AddChannel(JsonObject channelObj, int index) {
        this(channelObj, null, index);
      }

      public AddChannel(Class<? extends LXPattern> patternClass) {
        this(null, patternClass, -1);
      }

      public AddChannel(JsonObject channelObj, Class<? extends LXPattern> patternClass) {
        this(channelObj, patternClass, -1);
      }

      public AddChannel(JsonObject channelObj, Class<? extends LXPattern> patternClass, int index) {
        this.index = index;
        this.channelObj = channelObj;
        this.patternClass = patternClass;
      }

      @Override
      public String getDescription() {
        return "Add Channel";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        LXChannel channel;
        if (this.patternClass != null) {
          try {
            channel = lx.engine.mixer.addChannel(this.index, new LXPattern[] { lx.instantiatePattern(this.patternClass) });
          } catch (LX.InstantiationException x) {
            throw new InvalidCommandException(x);
          }
        } else {
          channel = lx.engine.mixer.addChannel(this.index, this.channelObj);
        }
        this.channelObj = LXSerializable.Utils.toObject(channel);
        this.channel = new ComponentReference<LXChannel>(channel);
        lx.engine.mixer.setFocusedChannel(channel);
        lx.engine.mixer.selectChannel(channel);
      }

      @Override
      public void undo(LX lx) {
        if (this.channel == null) {
          throw new IllegalStateException(
            "Channel was not successfully added, cannot undo");
        }
        lx.engine.mixer.removeChannel(this.channel.get());
      }

    }

    public static class MoveChannel extends LXCommand {

      private final ComponentReference<LXAbstractChannel> channel;
      private final int delta;

      public MoveChannel(LXAbstractChannel channel, int delta) {
        this.channel = new ComponentReference<LXAbstractChannel>(channel);
        this.delta = delta;
      }

      @Override
      public String getDescription() {
        return "Move Channel";
      }

      @Override
      public void perform(LX lx) {
        lx.engine.mixer.moveChannel(this.channel.get(), this.delta);
      }

      @Override
      public void undo(LX lx) {
        lx.engine.mixer.moveChannel(this.channel.get(), -this.delta);
      }

    }

    public static class DropChannel extends LXCommand {

      private final ComponentReference<LXAbstractChannel> channel;
      private final ComponentReference<LXGroup> toGroup;
      private final ComponentReference<LXGroup> fromGroup;
      private final int fromIndex;
      private final int toIndex;

      public DropChannel(LXAbstractChannel channel, int index, LXGroup group) {
        this.channel = new ComponentReference<LXAbstractChannel>(channel);
        this.fromGroup = channel.isInGroup() ? new ComponentReference<LXGroup>(channel.getGroup()) : null;
        this.toGroup = (group != null) ? new ComponentReference<LXGroup>(group) : null;
        this.toIndex = index;

        // Leftward group moves are tricky, when we move
        // back to the right, we need to additionally move
        // over all the group channels
        int fromIndex = channel.getIndex();
        if (channel.isGroup() && (fromIndex > index)) {
          fromIndex += ((LXGroup) channel).channels.size();
        }
        this.fromIndex = fromIndex;

      }

      @Override
      public String getDescription() {
        return "Move Channel";
      }

      @Override
      public void perform(LX lx) {
        lx.engine.mixer.moveChannel(this.channel.get(), this.toIndex, (this.toGroup != null) ? this.toGroup.get() : null);
      }

      @Override
      public void undo(LX lx) {
        lx.engine.mixer.moveChannel(this.channel.get(), this.fromIndex, (this.fromGroup != null) ? this.fromGroup.get() : null);
      }

    }

    public static class RemoveChannel extends RemoveComponent {
      private final ComponentReference<LXAbstractChannel> channel;
      private final JsonObject channelObj;
      private final int index;

      private Parameter.SetNormalized focusedChannel;
      private final List<RemoveChannel> groupChildren = new ArrayList<RemoveChannel>();

      public RemoveChannel(LXAbstractChannel channel) {
        super(channel);
        this.channel = new ComponentReference<LXAbstractChannel>(channel);
        this.channelObj = LXSerializable.Utils.toObject(channel);
        this.index = channel.getIndex();
        this.focusedChannel = new Parameter.SetNormalized(channel.getLX().engine.mixer.focusedChannel);

        // Are we a group? We'll need to bring our children back as well...
        if (channel instanceof LXGroup) {
          for (LXChannel child : ((LXGroup) channel).channels) {
            this.groupChildren.add(new RemoveChannel(child));
          }
        }
      }

      @Override
      public String getDescription() {
        return "Delete Channel";
      }

      @Override
      public void perform(LX lx) {
        // Note: this automatically removes group children as well
        lx.engine.mixer.removeChannel(this.channel.get());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        undo(lx, false);
      }

      public void undo(LX lx, boolean multiRemove) throws InvalidCommandException {
        // Re-load the removed channel
        lx.engine.mixer.loadChannel(this.channelObj, this.index);

        // Restore all the group children
        for (RemoveChannel child : this.groupChildren) {
          child.undo(lx);
        }

        // Restore channel focus
        this.focusedChannel.undo(lx);

        if (!multiRemove) {
          lx.engine.mixer.selectChannel(this.channel.get());
        }

        // Bring back modulations on all patterns and effects
        super.undo(lx);
      }

    }

    public static class RemoveSelectedChannels extends LXCommand {

      private final List<RemoveChannel> removedChannels = new ArrayList<RemoveChannel>();

      public RemoveSelectedChannels(LX lx) {
        // Serialize a list of all the channels that will end up removed, so we
        // can undo properly
        List<LXAbstractChannel> removeChannels = new ArrayList<LXAbstractChannel>();
        for (LXAbstractChannel channel : lx.engine.mixer.channels) {
          if (channel.selected.isOn() && !removeChannels.contains(channel.getGroup())) {
            removeChannels.add(channel);
          }
        }
        for (LXAbstractChannel removeChannel : removeChannels) {
          this.removedChannels.add(new RemoveChannel(removeChannel));
        }
      }

      @Override
      public String getDescription() {
        return "Delete Channels";
      }

      @Override
      public void perform(LX lx) {
        for (RemoveChannel remove : this.removedChannels) {
          remove.perform(lx);
        }
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        for (LXBus bus : lx.engine.mixer.channels) {
          bus.selected.setValue(false);
        }
        lx.engine.mixer.masterBus.selected.setValue(false);

        for (RemoveChannel removedChannel : this.removedChannels) {
          removedChannel.undo(lx, true);
        }
      }

    }

    public static class Ungroup extends LXCommand {
      private final ComponentReference<LXGroup> group;
      private final JsonObject groupObj;
      private final int index;

      private final List<ComponentReference<LXChannel>> groupChannels = new ArrayList<ComponentReference<LXChannel>>();

      public Ungroup(LXGroup group) {
        this.group = new ComponentReference<LXGroup>(group);
        this.groupObj = LXSerializable.Utils.toObject(group);
        this.index = group.getIndex();
      }

      @Override
      public String getDescription() {
        return "Ungroup Channels";
      }

      @Override
      public void perform(LX lx) {
        for (LXChannel channel : this.group.get().channels) {
          this.groupChannels.add(new ComponentReference<LXChannel>(channel));
        }
        this.group.get().ungroup();
      }

      @Override
      public void undo(LX lx) {
        LXGroup group = lx.engine.mixer.addGroup(this.index);
        group.load(lx, this.groupObj);
        for (ComponentReference<LXChannel> channel : this.groupChannels) {
          group.addChannel(channel.get());
        }
      }
    }

    public static class UngroupChannel extends LXCommand {

      private final ComponentReference<LXGroup> group;
      private final ComponentReference<LXChannel> channel;
      private final int index;

      public UngroupChannel(LXChannel channel) {
        this.group = new ComponentReference<LXGroup>(channel.getGroup());
        this.channel = new ComponentReference<LXChannel>(channel);
        this.index = channel.getIndex();
      }

      @Override
      public String getDescription() {
        return "Ungroup Channel";
      }

      @Override
      public void perform(LX lx) {
        lx.engine.mixer.ungroup(this.channel.get());

      }

      @Override
      public void undo(LX lx) {
        lx.engine.mixer.group(this.group.get(), this.channel.get(), this.index);
      }

    }

    public static class GroupSelectedChannels extends LXCommand {

      private final List<ComponentReference<LXChannel>> groupChannels =
        new ArrayList<ComponentReference<LXChannel>>();

      private ComponentReference<LXGroup> group;

      public GroupSelectedChannels(LX lx) {
        for (LXChannel channel : lx.engine.mixer.getSelectedChannelsForGroup()) {
          this.groupChannels.add(new ComponentReference<LXChannel>(channel));
        }
      }

      @Override
      public String getDescription() {
        return "Add Group";
      }

      @Override
      public void perform(LX lx) {
        if (!isIgnored()) {

          final List<LXChannel> channels = new ArrayList<LXChannel>(this.groupChannels.size());
          for (ComponentReference<LXChannel> channel : this.groupChannels) {
            channels.add(channel.get());
          }
          this.group = new ComponentReference<LXGroup>(lx.engine.mixer.addGroup(channels));
        }
      }

      @Override
      public void undo(LX lx) {
        if (this.group != null) {
          this.group.get().ungroup();
        }
      }

      @Override
      public boolean isIgnored() {
        return this.groupChannels.isEmpty();
      }

    }

  }

  public static class Modulation {

    public static class AddModulator extends LXCommand {

      private final ComponentReference<LXModulationEngine> modulation;
      private final Class<? extends LXModulator> modulatorClass;
      private final int modulationColor;
      private JsonObject modulatorObj;
      private ComponentReference<LXModulator> modulator;

      public AddModulator(LXModulationEngine modulation, Class<? extends LXModulator> modulatorClass) {
        this(modulation, modulatorClass, null);
      }

      public AddModulator(LXModulationEngine modulation, Class<? extends LXModulator> modulatorClass, int modulationColor) {
        this(modulation, modulatorClass, null, modulationColor);
      }

      public AddModulator(LXModulationEngine modulation, Class<? extends LXModulator> modulatorClass, JsonObject modulatorObj) {
        this(modulation, modulatorClass, modulatorObj, -1);
      }

      public AddModulator(LXModulationEngine modulation, Class<? extends LXModulator> modulatorClass, JsonObject modulatorObj, int modulationColor) {
        this.modulation = new ComponentReference<LXModulationEngine>(modulation);
        this.modulatorClass = modulatorClass;
        this.modulatorObj = modulatorObj;
        this.modulationColor = modulationColor;
      }

      @Override
      public String getDescription() {
        return "Add " + LXComponent.getComponentName(this.modulatorClass);
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        try {
          LXModulator instance = lx.instantiateModulator(this.modulatorClass);
          if (this.modulationColor >= 0) {
            instance.modulationColor.setValue(this.modulationColor);
          }
          if (this.modulatorObj == null) {
            int count = this.modulation.get().getModulatorCount(this.modulatorClass);
            if (count > 0) {
              instance.label.setValue(instance.getLabel() + " " + (count + 1));
            }
          }
          this.modulation.get().addModulator(instance, this.modulatorObj);
          if (this.modulatorObj == null) {
            this.modulatorObj = LXSerializable.Utils.toObject(instance);
          }
          instance.autostart();
          this.modulator = new ComponentReference<LXModulator>(instance);
        } catch (LX.InstantiationException x) {
          throw new InvalidCommandException(x);
        }
      }

      @Override
      public void undo(LX lx) {
        this.modulation.get().removeModulator(this.modulator.get());
      }
    }

    public static class MoveModulator extends LXCommand {

      private final ComponentReference<LXModulationEngine> modulation;
      private final ComponentReference<LXModulator> modulator;
      private final int fromIndex;
      private final int toIndex;

      public MoveModulator(LXModulationEngine modulation, LXModulator modulator,
        int index) {
        this.modulation = new ComponentReference<LXModulationEngine>(
          modulation);
        this.modulator = new ComponentReference<LXModulator>(modulator);
        this.fromIndex = modulator.getIndex();
        this.toIndex = index;
      }

      @Override
      public String getDescription() {
        return "Move Modulator";
      }

      @Override
      public void perform(LX lx) {
        this.modulation.get().moveModulator(this.modulator.get(), this.toIndex);
      }

      @Override
      public void undo(LX lx) {
        this.modulation.get().moveModulator(this.modulator.get(),
          this.fromIndex);
      }
    }

    public static class RemoveModulator extends RemoveComponent {

      private final ComponentReference<LXModulationEngine> modulation;
      private final ComponentReference<LXModulator> modulator;
      private final JsonObject modulatorObj;
      private final int index;

      public RemoveModulator(LXModulationEngine modulation, LXModulator modulator) {
        super(modulator);
        this.modulation = new ComponentReference<LXModulationEngine>(modulation);
        this.modulator = new ComponentReference<LXModulator>(modulator);
        this.index = modulator.getIndex();
        this.modulatorObj = LXSerializable.Utils.toObject(modulator);
      }

      @Override
      public String getDescription() {
        return "Delete Modulator";
      }

      @Override
      public void perform(LX lx) {
        this.modulation.get().removeModulator(this.modulator.get());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        try {
          LXModulator instance = lx.instantiateModulator(this.modulatorObj.get(LXComponent.KEY_CLASS).getAsString());
          instance.load(lx, this.modulatorObj);
          this.modulation.get().addModulator(instance, this.index);
          instance.start();

          // Restore all the modulations...
          super.undo(lx);
        } catch (LX.InstantiationException x) {
          throw new InvalidCommandException(x);
        }
      }
    }

    public static class AddModulation extends LXCommand {

      class ModulationSourceReference {

        private final ParameterReference<LXNormalizedParameter> parameter;
        private final ComponentReference<LXComponent> component;
        private final boolean isComponent;

        public ModulationSourceReference(LXNormalizedParameter source) {
          if (source instanceof LXComponent) {
            this.isComponent = true;
            this.component = new ComponentReference<LXComponent>((LXComponent) source);
            this.parameter = null;
          } else {
            this.isComponent = false;
            this.component = null;
            this.parameter = new ParameterReference<LXNormalizedParameter>(source);
          }
        }

        public LXNormalizedParameter get() {
          return this.isComponent
            ? ((LXNormalizedParameter) this.component.get())
            : this.parameter.get();
        }

      }

      private final ComponentReference<LXModulationEngine> engine;

      private final ModulationSourceReference source;
      private final ParameterReference<LXCompoundModulation.Target> target;

      private ComponentReference<LXCompoundModulation> modulation;

      private JsonObject modulationObj = null;

      public AddModulation(LXModulationEngine engine, LXNormalizedParameter source, LXCompoundModulation.Target target) {
        this.engine = new ComponentReference<LXModulationEngine>(engine);
        this.source = new ModulationSourceReference(source);
        this.target = new ParameterReference<LXCompoundModulation.Target>(target);
      }


      @Override
      public String getDescription() {
        return "Add Modulation";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        try {
          LXCompoundModulation.Target target = this.target.get();
          LXCompoundModulation modulation = new LXCompoundModulation(
            this.engine.get(),
            this.source.get(),
            target
          );
          if (this.modulationObj != null) {
            modulation.load(lx, this.modulationObj);
          } else {
            this.modulationObj = LXSerializable.Utils.toObject(lx, modulation);
          }
          this.engine.get().addModulation(modulation);
          this.modulation = new ComponentReference<LXCompoundModulation>(modulation);
        } catch (LXParameterModulation.ModulationException mx) {
          throw new InvalidCommandException(mx);
        }
      }

      @Override
      public void undo(LX lx) {
        this.engine.get().removeModulation(this.modulation.get());
      }

    }

    public static class RemoveModulation extends LXCommand {

      private final ComponentReference<LXModulationEngine> engine;
      private ComponentReference<LXCompoundModulation> modulation;
      private final JsonObject modulationObj;

      public RemoveModulation(LXModulationEngine engine, LXCompoundModulation modulation) {
        this.engine = new ComponentReference<LXModulationEngine>(engine);
        this.modulation = new ComponentReference<LXCompoundModulation>(modulation);
        this.modulationObj = LXSerializable.Utils.toObject(modulation);
      }

      @Override
      public String getDescription() {
        return "Delete Modulation";
      }

      @Override
      public void perform(LX lx) {
        this.engine.get().removeModulation(this.modulation.get());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        try {
          LXCompoundModulation modulation = new LXCompoundModulation(lx, this.engine.get(), this.modulationObj);
          this.engine.get().addModulation(modulation);
          modulation.load(lx, this.modulationObj);
          this.modulation = new ComponentReference<LXCompoundModulation>(modulation);
        } catch (LXParameterModulation.ModulationException mx) {
          throw new InvalidCommandException(mx);
        }
      }
    }

    public static class RemoveModulations extends LXCommand {

      private final List<RemoveModulation> removeModulations = new ArrayList<RemoveModulation>();

      public RemoveModulations(LXCompoundModulation.Target parameter) {
        for (LXCompoundModulation modulation : parameter.getModulations()) {
          this.removeModulations.add(new RemoveModulation(modulation.scope, modulation));
        }
      }

      @Override
      public void perform(LX lx) {
        for (RemoveModulation remove : this.removeModulations) {
          remove.perform(lx);
        }
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        for (RemoveModulation remove : this.removeModulations) {
          remove.undo(lx);
        }
      }

      @Override
      public String getDescription() {
        return "Remove Modulations";
      }
    }

    public static class AddTrigger extends LXCommand {

      private final ComponentReference<LXModulationEngine> engine;
      private final ParameterReference<BooleanParameter> source;
      private final ParameterReference<BooleanParameter> target;
      private ComponentReference<LXTriggerModulation> trigger;

      public AddTrigger(LXModulationEngine engine, BooleanParameter source, BooleanParameter target) {
        this.engine = new ComponentReference<LXModulationEngine>(engine);
        this.source = new ParameterReference<BooleanParameter>(source);
        this.target = new ParameterReference<BooleanParameter>(target);
      }

      @Override
      public String getDescription() {
        return "Add Trigger";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        try {
          LXTriggerModulation trigger = new LXTriggerModulation(
            this.engine.get(), this.source.get(), this.target.get());
          this.engine.get().addTrigger(trigger);
          this.trigger = new ComponentReference<LXTriggerModulation>(trigger);
        } catch (LXParameterModulation.ModulationException mx) {
          throw new InvalidCommandException(mx);
        }
      }

      @Override
      public void undo(LX lx) {
        this.engine.get().removeTrigger(this.trigger.get());
      }

    }

    public static class RemoveTrigger extends LXCommand {

      private final ComponentReference<LXModulationEngine> engine;
      private ComponentReference<LXTriggerModulation> trigger;
      private final JsonObject triggerObj;

      public RemoveTrigger(LXModulationEngine engine, LXTriggerModulation trigger) {
        this.engine = new ComponentReference<LXModulationEngine>(engine);
        this.trigger = new ComponentReference<LXTriggerModulation>(trigger);
        this.triggerObj = LXSerializable.Utils.toObject(trigger);
      }

      @Override
      public String getDescription() {
        return "Delete Trigger";
      }

      @Override
      public void perform(LX lx) {
        this.engine.get().removeTrigger(this.trigger.get());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        try {
          LXTriggerModulation trigger = new LXTriggerModulation(lx, this.engine.get(), this.triggerObj);
          this.engine.get().addTrigger(trigger);
          trigger.load(lx, this.triggerObj);
          this.trigger = new ComponentReference<LXTriggerModulation>(trigger);
        } catch (LXParameterModulation.ModulationException mx) {
          throw new InvalidCommandException(mx);
        }
      }
    }

    public static class Remove extends LXCommand {

      private final List<LXCommand> remove = new ArrayList<LXCommand>();

      public Remove(LXModulationEngine engine, List<LXParameterModulation> modulations) {
        for (LXParameterModulation modulation : modulations) {
          if (modulation instanceof LXCompoundModulation) {
            this.remove.add(new RemoveModulation(engine, (LXCompoundModulation) modulation));
          } else if (modulation instanceof LXTriggerModulation) {
            this.remove.add(new RemoveTrigger(engine, (LXTriggerModulation) modulation));
          }
        }
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        for (LXCommand remove : this.remove) {
          remove.perform(lx);
        }
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        for (LXCommand remove : this.remove) {
          remove.undo(lx);
        }
      }

      @Override
      public String getDescription() {
        return "Remove Modulations";
      }
    }
  }

  public static class Palette {
    public static class AddColor extends LXCommand {

      private final ComponentReference<LXSwatch> swatch;
      private ComponentReference<LXDynamicColor> color;
      private JsonObject colorObj;

      public AddColor(LXSwatch swatch) {
        this.swatch = new ComponentReference<LXSwatch>(swatch);
      }

      @Override
      public String getDescription() {
        return "Add Color";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        if (this.colorObj == null) {
          this.color = new ComponentReference<LXDynamicColor>(this.swatch.get().addColor());
          this.colorObj = LXSerializable.Utils.toObject(lx, this.color.get());
        } else {
          this.swatch.get().addColor(-1, this.colorObj);
        }
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        this.swatch.get().removeColor(this.color.get());
      }
    }

    public static class RemoveColor extends RemoveComponent {
      private final ComponentReference<LXSwatch> swatch;
      private final ComponentReference<LXDynamicColor> color;
      private final int index;
      private final JsonObject colorObj;

      public RemoveColor(LXDynamicColor color) {
        super(color);
        this.swatch = new ComponentReference<LXSwatch>(color.getSwatch());
        this.color = new ComponentReference<LXDynamicColor>(color);
        this.colorObj = LXSerializable.Utils.toObject(this.color.get());
        this.index = color.getIndex();
      }

      @Override
      public String getDescription() {
        return "Delete Color";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        this.swatch.get().removeColor(this.color.get());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        this.swatch.get().addColor(this.index, this.colorObj);
        super.undo(lx);
      }
    }

    public static class SaveSwatch extends LXCommand {

      private ComponentReference<LXSwatch> swatch;
      private JsonObject swatchObj;
      private int index = -1;
      private JsonObject initialObj;

      public SaveSwatch() {}

      public SaveSwatch(JsonObject initialObj, int index) {
        this.index = index;
        this.initialObj = initialObj;
      }

      @Override
      public String getDescription() {
        return "Save Color Swatch";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        if (this.swatch != null) {
          lx.engine.palette.addSwatch(this.swatchObj, this.index);
        } else {
          LXSwatch swatch;
          if (this.initialObj != null) {
            swatch = lx.engine.palette.addSwatch(this.initialObj, this.index);
          } else {
            swatch = lx.engine.palette.saveSwatch();
          }
          this.index = swatch.getIndex();
          this.swatchObj = LXSerializable.Utils.toObject(swatch);
          this.swatch = new ComponentReference<LXSwatch>(swatch);
        }
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        lx.engine.palette.removeSwatch(this.swatch.get());
      }

    }

    public static class RemoveSwatch extends RemoveComponent {

      private final ComponentReference<LXSwatch> swatch;
      private final JsonObject swatchObj;
      private final int swatchIndex;

      public RemoveSwatch(LXSwatch swatch) {
        super(swatch);
        this.swatch = new ComponentReference<LXSwatch>(swatch);
        this.swatchObj = LXSerializable.Utils.toObject(swatch);
        this.swatchIndex = swatch.getIndex();
      }

      @Override
      public String getDescription() {
        return "Delete Swatch";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        lx.engine.palette.removeSwatch(this.swatch.get());

      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        lx.engine.palette.addSwatch(this.swatchObj, this.swatchIndex);
        super.undo(lx);
      }

    }

    public static class MoveSwatch extends LXCommand {

      private final ComponentReference<LXSwatch> swatch;
      private final int fromIndex;
      private final int toIndex;

      public MoveSwatch(LXSwatch swatch, int toIndex) {
        this.swatch = new ComponentReference<LXSwatch>(swatch);
        this.fromIndex = swatch.getIndex();
        this.toIndex = toIndex;
      }

      @Override
      public String getDescription() {
        return "Move Swatch";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        lx.engine.palette.moveSwatch(this.swatch.get(), this.toIndex);

      }

      @Override
      public void undo(LX lx) {
        lx.engine.palette.moveSwatch(this.swatch.get(), this.fromIndex);
      }

    }


    public static class SetSwatch extends LXCommand {

      private final ComponentReference<LXSwatch> swatch;
      private JsonObject originalSwatch;
      private boolean set = false;

      public SetSwatch(LXSwatch swatch) {
        this.swatch = new ComponentReference<LXSwatch>(swatch);
      }

      @Override
      public String getDescription() {
        return "Set Swatch Colors";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        this.originalSwatch = LXSerializable.Utils.toObject(lx.engine.palette.swatch, true);
        this.set = lx.engine.palette.setSwatch(this.swatch.get());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        lx.engine.palette.swatch.load(lx, this.originalSwatch);
      }

      @Override
      public boolean isIgnored() {
        return !this.set;
      }

    }

    public static class ImportSwatches extends LXCommand {

      private static class ImportedSwatch {
        private final ComponentReference<LXSwatch> swatch;
        private final JsonObject swatchObj;

        private ImportedSwatch(LXSwatch swatch) {
          this.swatch = new ComponentReference<LXSwatch>(swatch);
          this.swatchObj = LXSerializable.Utils.toObject(swatch.getLX(), swatch);
        }
      }

      private final File file;
      private List<ImportedSwatch> importedSwatches;

      public ImportSwatches(LXPalette palette, File file) {
        this.file = file;
      }

      @Override
      public String getDescription() {
        return "Import Swatches " + this.file.getName();
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        if (this.importedSwatches == null) {
          this.importedSwatches= new ArrayList<ImportedSwatch>();
          final List<LXSwatch> imported = lx.engine.palette.importSwatches(this.file);
          if (imported != null) {
            for (LXSwatch swatch : imported) {
              this.importedSwatches.add(new ImportedSwatch(swatch));
            }
          }
        } else {
          // We've imported already, this is a redo and we need to
          // preserve the swatch IDs and restore whatever was on disk
          // the first time... the file may have changed underneath us
          // but there could be "Redo" operations ahead of us in the
          // queue
          for (ImportedSwatch swatch : this.importedSwatches) {
            lx.engine.palette.addSwatch(swatch.swatchObj, -1);
          }
        }
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        for (int i = this.importedSwatches.size() - 1; i >= 0; --i) {
          lx.engine.palette.removeSwatch(this.importedSwatches.get(i).swatch.get());
        }
      }
    }
  }

  public static class Snapshots {

    public static class AddSnapshot extends LXCommand {

      private ComponentReference<LXGlobalSnapshot> snapshot;
      private JsonObject initialObj = null;
      private JsonObject snapshotObj = null;
      private final int index;

      public AddSnapshot() {
        this.index = -1;
      }

      public AddSnapshot(JsonObject snapshotObj, int index) {
        this.initialObj = snapshotObj;
        this.index = index;
      }

      @Override
      public String getDescription() {
        return "Add Snapshot";
      }

      @Override
      public void perform(LX lx) {
        if (this.snapshotObj == null) {
          LXGlobalSnapshot instance;
          if (this.initialObj != null) {
            instance = new LXGlobalSnapshot(lx);
            instance.load(lx, this.initialObj);
            lx.engine.snapshots.addSnapshot(instance, this.index);
          } else {
            instance = lx.engine.snapshots.addSnapshot();
          }
          this.snapshot = new ComponentReference<LXGlobalSnapshot>(instance);
          this.snapshotObj = LXSerializable.Utils.toObject(lx, instance);
        } else {
          LXGlobalSnapshot instance = new LXGlobalSnapshot(lx);
          instance.load(lx, this.snapshotObj);
          this.snapshot = new ComponentReference<LXGlobalSnapshot>(instance);
          lx.engine.snapshots.addSnapshot(instance);
        }
      }

      @Override
      public void undo(LX lx) {
        lx.engine.snapshots.removeSnapshot(this.snapshot.get());
      }
    }

    public static class MoveSnapshot extends LXCommand {

      private final ComponentReference<LXGlobalSnapshot> snapshot;
      private final int fromIndex;
      private final int toIndex;

      public MoveSnapshot(LXGlobalSnapshot snapshot, int toIndex) {
        this.snapshot = new ComponentReference<LXGlobalSnapshot>(snapshot);
        this.fromIndex = snapshot.getIndex();
        this.toIndex = toIndex;
      }

      @Override
      public String getDescription() {
        return "Move Snapshot";
      }

      @Override
      public void perform(LX lx) {
        lx.engine.snapshots.moveSnapshot(this.snapshot.get(), this.toIndex);
      }

      @Override
      public void undo(LX lx) {
        lx.engine.snapshots.moveSnapshot(this.snapshot.get(), this.fromIndex);
      }
    }

    public static class RemoveSnapshot extends RemoveComponent {

      private final ComponentReference<LXGlobalSnapshot> snapshot;
      private final JsonObject snapshotObj;
      private final int snapshotIndex;

      public RemoveSnapshot(LXGlobalSnapshot snapshot) {
        super(snapshot);
        this.snapshot = new ComponentReference<LXGlobalSnapshot>(snapshot);
        this.snapshotObj = LXSerializable.Utils.toObject(snapshot);
        this.snapshotIndex = snapshot.getIndex();
      }

      @Override
      public String getDescription() {
        return "Delete Snapshot";
      }

      @Override
      public void perform(LX lx) {
        lx.engine.snapshots.removeSnapshot(this.snapshot.get());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        LXGlobalSnapshot snapshot = new LXGlobalSnapshot(lx);
        snapshot.load(lx, this.snapshotObj);
        lx.engine.snapshots.addSnapshot(snapshot, this.snapshotIndex);
        super.undo(lx);
      }
    }

    public static class Update extends LXCommand {

      private final ComponentReference<LXSnapshot> snapshot;
      private JsonObject previousState;

      public Update(LXSnapshot snapshot) {
        this.snapshot = new ComponentReference<LXSnapshot>(snapshot);
      }

      @Override
      public String getDescription() {
        return "Update Snapshot";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        this.previousState = LXSerializable.Utils.toObject(lx, this.snapshot.get());
        this.snapshot.get().update();
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        this.snapshot.get().load(lx, this.previousState);
      }

    }

    public static class Recall extends LXCommand {

      private final ComponentReference<LXGlobalSnapshot> snapshot;
      private final List<LXCommand> commands = new ArrayList<LXCommand>();
      private boolean recalled = false;

      public Recall(LXGlobalSnapshot snapshot) {
        this.snapshot = new ComponentReference<LXGlobalSnapshot>(snapshot);
      }

      @Override
      public void perform(LX lx) {
        this.commands.clear();
        this.recalled = lx.engine.snapshots.recall(this.snapshot.get(), this.commands);
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        for (LXCommand command : this.commands) {
          command.undo(lx);
        }
      }

      @Override
      public boolean isIgnored() {
        return !this.recalled;
      }

      @Override
      public String getDescription() {
        return "Recall Snapshot";
      }
    }

    private static class RemoveView extends LXCommand {

      private ComponentReference<LXSnapshot> snapshot;
      private LXSnapshot.View view;
      private final JsonObject viewObj;

      public RemoveView(LXSnapshot.View view) {
        this.snapshot = new ComponentReference<LXSnapshot>(view.getSnapshot());
        this.view = view;
        this.viewObj = LXSerializable.Utils.toObject(view.getSnapshot().getLX(), view);
      }

      @Override
      public String getDescription() {
        return "Delete Snapshot View";
      }

      @Override
      public void perform(LX lx) {
        this.snapshot.get().removeView(this.view);
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        this.view = this.snapshot.get().addView(this.viewObj);
      }
    }
  }

  public static class Structure {

    public static class AddFixture extends LXCommand {

      private ComponentReference<LXFixture> fixture;
      private final Class<? extends LXFixture> fixtureClass;
      private JsonObject fixtureObj;
      private final String fixtureType;
      private final int index;

      public AddFixture(Class<? extends LXFixture> fixtureClass) {
        this(fixtureClass, null, -1);
      }

      public AddFixture(Class<? extends LXFixture> fixtureClass, int index) {
        this(fixtureClass, null, index);
      }

      public AddFixture(Class<? extends LXFixture> fixtureClass, JsonObject fixtureObj) {
        this(fixtureClass, fixtureObj, -1);
      }

      public AddFixture(Class<? extends LXFixture> fixtureClass, JsonObject fixtureObj, int index) {
        this.fixtureClass = fixtureClass;
        this.fixtureObj = fixtureObj;
        this.fixtureType = null;
        this.index = index;
      }

      public AddFixture(String fixtureType) {
        this.fixtureClass = null;
        this.fixtureType = fixtureType;
        this.index = -1;
      }

      @Override
      public String getDescription() {
        return "Add Fixture";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        LXFixture fixture;
        if (this.fixtureClass != null) {
          try {
            fixture = lx.instantiateFixture(this.fixtureClass);
            fixture.label.setValue(fixture.label.getString() + " " + (lx.structure.fixtures.size() + 1));
          } catch (LX.InstantiationException x) {
            throw new InvalidCommandException(x);
          }
        } else if (this.fixtureType != null) {
          fixture = new JsonFixture(lx, this.fixtureType);
          fixture.label.setValue(fixture.label.getString() + " " + (lx.structure.fixtures.size() + 1));
        } else {
          throw new IllegalStateException("AddFixture action has neither fixtureClass nor fixtureType");
        }
        if (this.fixtureObj != null) {
          fixture.load(lx, this.fixtureObj);
          fixture.selected.setValue(false);
        }
        this.fixtureObj = LXSerializable.Utils.toObject(fixture);
        lx.structure.addFixture(fixture, this.index);
        this.fixture = new ComponentReference<LXFixture>(fixture);
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        lx.structure.removeFixture(this.fixture.get());
      }

    }

    public static class RemoveFixture extends RemoveComponent {

      private ComponentReference<LXFixture> fixture;
      private final int index;
      private final JsonObject fixtureObj;

      public RemoveFixture(LXFixture fixture) {
        super(fixture);
        this.fixture = new ComponentReference<LXFixture>(fixture);
        this.fixtureObj = LXSerializable.Utils.toObject(fixture);
        this.index = fixture.getIndex();
      }

      @Override
      public String getDescription() {
        return "Delete Fixture";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        lx.structure.removeFixture(this.fixture.get());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        try {
          LXFixture fixture = lx.instantiateFixture(this.fixtureObj.get(LXComponent.KEY_CLASS).getAsString());
          fixture.load(lx, this.fixtureObj);
          lx.structure.addFixture(fixture, this.index);
        } catch (LX.InstantiationException x) {
          throw new InvalidCommandException(x);
        }
        super.undo(lx);
      }
    }

    public static class RemoveSelectedFixtures extends LXCommand {

      private final List<RemoveFixture> removeFixtures =
        new ArrayList<RemoveFixture>();

      public RemoveSelectedFixtures(LXStructure structure) {
        for (LXFixture fixture : structure.getSelectedFixtures()) {
          this.removeFixtures.add(new RemoveFixture(fixture));
        }
      }

      @Override
      public String getDescription() {
        return "Delete Fixtures";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        List<LXFixture> selectedFixtures = new ArrayList<LXFixture>();
        for (RemoveFixture remove : this.removeFixtures) {
          selectedFixtures.add(remove.fixture.get());
        }
        lx.structure.removeFixtures(selectedFixtures);
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        for (RemoveFixture remove : this.removeFixtures) {
          remove.undo(lx);
        }
        for (RemoveFixture remove : this.removeFixtures) {
          remove.fixture.get().selected.setValue(true);
        }
      }

    }

    public static class MoveFixture extends LXCommand {

      private final ComponentReference<LXFixture> fixture;
      private final int originalIndex;
      private final int index;

      public MoveFixture(LXFixture fixture, int index) {
        this.fixture = new ComponentReference<LXFixture>(fixture);
        this.originalIndex = fixture.getIndex();
        this.index = index;
      }

      @Override
      public String getDescription() {
        return "Move Fixture";
      }

      @Override
      public void perform(LX lx) {
        lx.structure.moveFixture(this.fixture.get(), this.index);
      }

      @Override
      public void undo(LX lx) {
        lx.structure.moveFixture(this.fixture.get(), this.originalIndex);
      }
    }

    public static class NewModel extends LXCommand {

      private final List<RemoveFixture> removeFixtures =
        new ArrayList<RemoveFixture>();

      public NewModel(LXStructure structure) {
        for (LXFixture fixture : structure.fixtures) {
          this.removeFixtures.add(new RemoveFixture(fixture));
        }
      }

      @Override
      public String getDescription() {
        return "New Model";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        lx.structure.newDynamicModel();
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        for (RemoveFixture remove : this.removeFixtures) {
          remove.undo(lx);
        }
      }
    }

    public static class ModifyFixturePositions extends LXCommand {

      private final Map<String, LXCommand.Parameter.SetValue> setValues =
        new HashMap<String, LXCommand.Parameter.SetValue>();

      public ModifyFixturePositions() {}

      @Override
      public String getDescription() {
        return "Modify Fixture Position";
      }

      private boolean inUpdate = false;

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        if (this.inUpdate) {
          return;
        }
        for (LXCommand.Parameter.SetValue setValue : this.setValues.values()) {
          setValue.perform(lx);
        }
      }

      public void update(LX lx, LXParameter parameter, double delta) {
        this.inUpdate = true;
        String path = parameter.getCanonicalPath();
        LXCommand.Parameter.SetValue setValue = null;
        if (this.setValues.containsKey(path)) {
          setValue = this.setValues.get(path);
          setValue.update(parameter.getValue() + delta);
        } else {
          setValue = new LXCommand.Parameter.SetValue(parameter, parameter.getValue() + delta);
          this.setValues.put(path, setValue);
        }

        // Set the value
        setValue.perform(lx);
        lx.command.perform(this);
        this.inUpdate = false;
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        for (LXCommand.Parameter.SetValue setValue : this.setValues.values()) {
          setValue.undo(lx);
        }
      }

    }

    public static class AddView extends LXCommand {

      private ComponentReference<LXViewDefinition> view;
      private JsonObject viewObj;
      private int index = -1;
      private JsonObject initialObj;

      public AddView() {}

      public AddView(JsonObject initialObj, int index) {
        this.index = index;
        this.initialObj = initialObj;
      }

      @Override
      public String getDescription() {
        return "Add View";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        if (this.view != null) {
          lx.structure.views.addView(this.viewObj, this.index);
        } else {
          LXViewDefinition view;
          if (this.initialObj != null) {
            view = lx.structure.views.addView(this.initialObj, this.index);
          } else {
            view = lx.structure.views.addView();
          }
          this.index = view.getIndex();
          this.viewObj = LXSerializable.Utils.toObject(view);
          this.view = new ComponentReference<LXViewDefinition>(view);
        }
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        lx.structure.views.removeView(this.view.get());
      }

    }

    public static class RemoveView extends RemoveComponent {

      private final ComponentReference<LXViewDefinition> view;
      private final JsonObject viewObj;
      private final int viewIndex;

      public RemoveView(LXViewDefinition view) {
        super(view);
        this.view = new ComponentReference<LXViewDefinition>(view);
        this.viewObj = LXSerializable.Utils.toObject(view);
        this.viewIndex = view.getIndex();
      }

      @Override
      public String getDescription() {
        return "Delete View";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        lx.structure.views.removeView(this.view.get());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        lx.structure.views.addView(this.viewObj, this.viewIndex);
        super.undo(lx);
      }

    }

    public static class MoveView extends LXCommand {

      private final ComponentReference<LXViewDefinition> view;
      private final int fromIndex;
      private final int toIndex;

      public MoveView(LXViewDefinition view, int toIndex) {
        this.view = new ComponentReference<LXViewDefinition>(view);
        this.fromIndex = view.getIndex();
        this.toIndex = toIndex;
      }

      @Override
      public String getDescription() {
        return "Move View";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        lx.structure.views.moveView(this.view.get(), this.toIndex);

      }

      @Override
      public void undo(LX lx) {
        lx.structure.views.moveView(this.view.get(), this.fromIndex);
      }
    }

    public static class ImportViews extends LXCommand {

      private static class ImportedView {
        private final ComponentReference<LXViewDefinition> view;
        private final JsonObject viewObj;

        private ImportedView(LXViewDefinition view) {
          this.view = new ComponentReference<LXViewDefinition>(view);
          this.viewObj = LXSerializable.Utils.toObject(view.getLX(), view);
        }
      }

      private final File file;
      private List<ImportedView> importedViews;

      public ImportViews(File file) {
        this.file = file;
      }

      @Override
      public String getDescription() {
        return "Import Views " + this.file.getName();
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        if (this.importedViews == null) {
          this.importedViews = new ArrayList<ImportedView>();
          final List<LXViewDefinition> imported = lx.structure.importViews(this.file);
          if (imported != null) {
            for (LXViewDefinition view : imported) {
              this.importedViews.add(new ImportedView(view));
            }
          }
        } else {
          // We've imported already, this is a redo and we need to
          // preserve the view IDs and restore whatever was on disk
          // the first time... the file may have changed underneath us
          // but there could be "Redo" operations ahead of us in the
          // queue
          for (ImportedView view : this.importedViews) {
            lx.structure.views.addView(view.viewObj, -1);
          }
        }
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        for (int i = this.importedViews.size() - 1; i >= 0; --i) {
          lx.structure.views.removeView(this.importedViews.get(i).view.get());
        }
      }
    }
  }

  public static class Clip {

    public static class Add extends LXCommand {

      private final ComponentReference<LXBus> bus;
      private final int index;
      private JsonObject clipObj;
      private JsonObject oldClipObj;

      public Add(LXBus bus, int index) {
        this(bus, index, null);
      }

      public Add(LXBus bus, int index, JsonObject clipObj) {
        this.bus = new ComponentReference<LXBus>(bus);
        this.index = index;
        this.clipObj = clipObj;
      }

      @Override
      public String getDescription() {
        return "Add Clip";
      }

      @Override
      public void perform(LX lx) {
        LXBus bus = this.bus.get();
        LXClip existing = bus.getClip(this.index);
        this.oldClipObj = null;
        if (existing != null) {
          this.oldClipObj = LXSerializable.Utils.toObject(lx, existing);
          bus.removeClip(this.index);
        }
        LXClip clip = bus.addClip(this.clipObj, this.index);
        this.clipObj = LXSerializable.Utils.toObject(lx, clip);
      }

      @Override
      public void undo(LX lx) {
        LXBus bus = this.bus.get();
        bus.removeClip(this.index);
        if (this.oldClipObj != null) {
          bus.addClip(this.oldClipObj, this.index);
        }
      }

    }

    public static class Remove extends LXCommand {

      private final ComponentReference<LXBus> bus;
      private final int index;
      private final JsonObject clipObj;

      public Remove(LXClip clip) {
        this.bus = new ComponentReference<LXBus>(clip.bus);
        this.clipObj = LXSerializable.Utils.toObject(clip);
        this.index = clip.getIndex();
      }

      @Override
      public String getDescription() {
        return "Remove Clip";
      }

      @Override
      public void perform(LX lx) {
        this.bus.get().removeClip(this.index);
      }

      @Override
      public void undo(LX lx) {
        this.bus.get().addClip(this.clipObj, this.index);
      }

    }

    public static class Trigger extends LXCommand {

      private final ComponentReference<LXClip> clip;
      private final List<LXCommand> commands = new ArrayList<LXCommand>();
      private boolean ignore = false;

      public Trigger(LXClip clip) {
        this.clip = new ComponentReference<LXClip>(clip);
      }

      @Override
      public String getDescription() {
        return "Trigger Clip";
      }

      @Override
      public void perform(LX lx){
        this.commands.clear();
        LXClip clip = this.clip.get();
        // Were we recording, or automation was not enabled?
        this.ignore = clip.bus.arm.isOn() || !clip.snapshotEnabled.isOn();
        if (!this.ignore) {
          clip.snapshot.getCommands(this.commands);
        }
        clip.trigger();
      }

      @Override
      public boolean isIgnored() {
        return this.ignore;
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        for (LXCommand command : this.commands) {
          command.undo(lx);
        }
      }

    }

    public static class Record extends LXCommand {

      private final ComponentReference<LXClip> clip;
      private final JsonObject clipObjPre;
      private JsonObject clipObjPost = null;

      public Record(LXClip clip) {
        this.clip = new ComponentReference<LXClip>(clip);
        this.clipObjPre = LXSerializable.Utils.toObject(clip.getLX(), clip);
      }

      @Override
      public String getDescription() {
        return "Record Clip";
      }

      @Override
      public void perform(LX lx) {
        LXClip clip = this.clip.get();
        if (this.clipObjPost == null) {
          this.clipObjPost = LXSerializable.Utils.toObject(lx, clip);
        } else {
          clip.load(lx, this.clipObjPost);
        }
      }

      @Override
      public void undo(LX lx) {
        this.clip.get().load(lx, this.clipObjPre);
      }

    }
  }

  public static class Midi {

    public static class AddMapping extends LXCommand {

      private final LXShortMessage message;
      private final ParameterReference<LXNormalizedParameter> parameter;
      private LXMidiMapping mapping;

      public AddMapping(LXShortMessage message, LXNormalizedParameter parameter) {
        this.message = message;
        this.parameter = new ParameterReference<LXNormalizedParameter>(parameter);
      }

      @Override
      public String getDescription() {
        return "Add MIDI Mapping";
      }

      @Override
      public void perform(LX lx) {
        lx.engine.midi.addMapping(this.mapping = LXMidiMapping.create(lx, message, this.parameter.get()));
      }

      @Override
      public void undo(LX lx) {
        lx.engine.midi.removeMapping(this.mapping);
      }

    }

    public static class RemoveMapping extends LXCommand {
      private final LXMidiMapping mapping;
      private final JsonObject mappingObj;

      public RemoveMapping(LX lx, LXMidiMapping mapping) {
        this.mapping = mapping;
        this.mappingObj = LXSerializable.Utils.toObject(lx, mapping);
      }

      @Override
      public String getDescription() {
        return "Delete MIDI Mapping";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        lx.engine.midi.removeMapping(this.mapping);
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        lx.engine.midi.addMapping(LXMidiMapping.create(lx, this.mappingObj));
      }
    }
  }
}
