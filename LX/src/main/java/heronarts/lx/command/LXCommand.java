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
 * ##library.name##
 * ##library.sentence##
 * ##library.url##
 *
 * @author      ##author##
 * @modified    ##date##
 * @version     ##library.prettyVersion## (##library.version##)
 */

package heronarts.lx.command;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXBus;
import heronarts.lx.LXChannel;
import heronarts.lx.LXChannelBus;
import heronarts.lx.LXComponent;
import heronarts.lx.LXEffect;
import heronarts.lx.LXGroup;
import heronarts.lx.LXModulationEngine;
import heronarts.lx.LXPattern;
import heronarts.lx.LXSerializable;
import heronarts.lx.LXUtils;
import heronarts.lx.clipboard.LXNormalizedValue;
import heronarts.lx.midi.LXMidiEngine;
import heronarts.lx.midi.LXMidiMapping;
import heronarts.lx.midi.LXShortMessage;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXCompoundModulation;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterModulation;
import heronarts.lx.parameter.LXTriggerModulation;
import heronarts.lx.parameter.StringParameter;

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

    protected InvalidCommandException(String message) {
      super(message);
    }

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
  protected static class ComponentReference<T extends LXComponent> {

    private final LX lx;
    private final int componentId;

    protected ComponentReference(T component) {
      this.lx = component.getLX();
      this.componentId = component.getId();
    }

    @SuppressWarnings("unchecked")
    public T get() {
      return (T) this.lx.getProjectComponent(this.componentId);
    }
  }

  protected static class ParameterReference<T extends LXParameter> {

    private final T rawParameter;
    private final Class<? extends LXComponent> componentCls;
    private final ComponentReference<LXComponent> component;
    private final String parameterPath;

    protected ParameterReference(T parameter) {
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
        System.err.println("Bad internal state, component " + this.component.componentId + " of type " + componentCls.getName() + " does not exist, cannot get parameter: " + this.parameterPath);
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


  private static abstract class RemoveComponent extends LXCommand {

    private final List<Modulation.RemoveModulation> removeModulations = new ArrayList<Modulation.RemoveModulation>();
    private final List<Modulation.RemoveTrigger> removeTriggers = new ArrayList<Modulation.RemoveTrigger>();
    private final List<Midi.RemoveMapping> removeMidiMappings = new ArrayList<Midi.RemoveMapping>();

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
      if (mappings  != null) {
        for (LXMidiMapping mapping : mappings) {
          this.removeMidiMappings.add(new Midi.RemoveMapping(midi.getLX(), mapping));
        }
      }
    }

    protected void removeMappings(LXModulationEngine modulation, LXComponent component) {
      _removeModulations(modulation, component);
      _removeTriggers(modulation, component);
    }

    protected RemoveComponent(LXComponent component) {
      // Tally up all the modulations and triggers that relate to this component and must be restored!
      removeMappings(component.getLX().engine.modulation, component);
      removeMidiMappings(component.getLX().engine.midi, component);
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
    }
  }

  /**
   * Name space for parameter commands
   */
  public static class Parameter {

    public static class Reset extends LXCommand {
      private final ParameterReference<LXParameter> parameter;
      private final double originalValue;

      public Reset(LXParameter parameter) {
        this.parameter = new ParameterReference<LXParameter>(parameter);
        this.originalValue = (parameter instanceof CompoundParameter)
          ? ((CompoundParameter) parameter).getBaseValue()
          : parameter.getValue();
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
        this.parameter.get().setValue(this.originalValue);
      }
    }

    public static class SetValue extends LXCommand {

      private final boolean isDiscrete;

      private final ParameterReference<DiscreteParameter> discreteParameter;
      private final int originalDiscreteValue;
      private final int newDiscreteValue;

      private final ParameterReference<LXParameter> genericParameter;
      private final double originalGenericValue;
      private double newGenericValue;

      public SetValue(DiscreteParameter parameter, int value) {
        this.isDiscrete = true;
        this.discreteParameter = new ParameterReference<DiscreteParameter>(
          parameter);
        this.originalDiscreteValue = parameter.getValuei();
        this.newDiscreteValue = value;

        this.genericParameter = null;
        this.originalGenericValue = 0;
        this.newGenericValue = 0;
      }

      public SetValue(LXParameter parameter, double value) {
        if (parameter instanceof DiscreteParameter) {
          this.isDiscrete = true;
          this.discreteParameter = new ParameterReference<DiscreteParameter>(
            (DiscreteParameter) parameter);
          this.originalDiscreteValue = ((DiscreteParameter) parameter)
            .getValuei();
          this.newDiscreteValue = (int) value;
          this.genericParameter = null;
          this.originalGenericValue = 0;
          this.newGenericValue = 0;
        } else {
          this.isDiscrete = false;
          this.genericParameter = new ParameterReference<LXParameter>(
            parameter);
          this.originalGenericValue = (parameter instanceof CompoundParameter)
            ? ((CompoundParameter) parameter).getBaseValue()
            : parameter.getValue();
          this.newGenericValue = value;
          this.discreteParameter = null;
          this.originalDiscreteValue = 0;
          this.newDiscreteValue = 0;
        }
      }

      private LXParameter getParameter() {
        return this.isDiscrete ? this.discreteParameter.get()
          : this.genericParameter.get();
      }

      public SetValue update(double value) {
        if (this.isDiscrete) {
          throw new IllegalStateException(
            "Cannot update discrete parameter with double value");
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

    public static class Increment extends LXCommand {

      private final ParameterReference<DiscreteParameter> parameter;
      private final int originalValue;
      private final int amount;

      public Increment(DiscreteParameter parameter) {
        this(parameter, 1);
      }

      public Increment(DiscreteParameter parameter, int amount) {
        this.parameter = new ParameterReference<DiscreteParameter>(parameter);
        this.originalValue = parameter.getValuei();
        this.amount = amount;
      }

      @Override
      public String getDescription() {
        return "Change " + this.parameter.get().getLabel();
      }

      @Override
      public void perform(LX lx) {
        this.parameter.get().increment(this.amount);
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

      public Decrement(DiscreteParameter parameter) {
        this(parameter, 1);
      }

      public Decrement(DiscreteParameter parameter, int amount) {
        this.parameter = new ParameterReference<DiscreteParameter>(parameter);
        this.originalValue = parameter.getValuei();
        this.amount = amount;
      }

      @Override
      public String getDescription() {
        return "Change " + this.parameter.get().getLabel();
      }

      @Override
      public void perform(LX lx) {
        this.parameter.get().decrement(this.amount);
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
      private final LXNormalizedValue originalValue;
      private double newValue;

      public SetNormalized(LXNormalizedParameter parameter) {
        this(parameter, new LXNormalizedValue(parameter).getValue());
      }

      public SetNormalized(BooleanParameter parameter, boolean value) {
        this(parameter, value ? 1 : 0);
      }

      public SetNormalized(LXNormalizedParameter parameter, double newValue) {
        this.parameter = new ParameterReference<LXNormalizedParameter>(parameter);
        this.originalValue = new LXNormalizedValue(parameter);
        this.newValue = newValue;
      }

      @Override
      public String getDescription() {
        return "Change " + this.parameter.get().getLabel();
      }

      @Override
      public void undo(LX lx) {
        this.parameter.get().setNormalized(this.originalValue.getValue());
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
        return "Remove Pattern";
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

    public static class AddEffect extends LXCommand {

      private final ComponentReference<LXBus> channel;
      private final Class<? extends LXEffect> effectClass;
      private ComponentReference<LXEffect> effect = null;
      private JsonObject effectObj = null;

      public AddEffect(LXBus channel, Class<? extends LXEffect> effectClass) {
        this(channel, effectClass, null);
      }

      public AddEffect(LXBus channel, Class<? extends LXEffect> effectClass, JsonObject effectObj) {
        this.channel = new ComponentReference<LXBus>(channel);
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
          this.channel.get().addEffect(instance);
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
        this.channel.get().removeEffect(this.effect.get());
      }
    }

    public static class RemoveEffect extends RemoveComponent {

      private final ComponentReference<LXBus> channel;
      private final ComponentReference<LXEffect> effect;
      private final JsonObject effectObj;
      private final int effectIndex;

      public RemoveEffect(LXBus channel, LXEffect effect) {
        super(effect);
        this.channel = new ComponentReference<LXBus>(channel);
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
        this.channel.get().removeEffect(this.effect.get());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        LXBus channel = this.channel.get();
        try {
          LXEffect effect = lx.instantiateEffect(this.effectObj.get(LXComponent.KEY_CLASS).getAsString());
          effect.load(lx, effectObj);
          channel.addEffect(effect, this.effectIndex);
          super.undo(lx);
        } catch (LX.InstantiationException x) {
          throw new InvalidCommandException(x);
        }
      }
    }

    public static class MoveEffect extends LXCommand {

      private final ComponentReference<LXBus> channel;
      private final ComponentReference<LXEffect> effect;
      private final int fromIndex;
      private final int toIndex;

      public MoveEffect(LXBus channel, LXEffect effect, int toIndex) {
        this.channel = new ComponentReference<LXBus>(channel);
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
        this.channel.get().moveEffect(this.effect.get(), this.toIndex);
      }

      @Override
      public void undo(LX lx) {
        this.channel.get().moveEffect(this.effect.get(), this.fromIndex);
      }
    }

  }

  public static class Mixer {

    public static class AddChannel extends LXCommand {

      private final Class<? extends LXPattern> patternClass;
      private ComponentReference<LXChannel> channel;
      private JsonObject channelObj = null;

      public AddChannel() {
        this(null, null);
      }

      public AddChannel(JsonObject channelObj) {
        this(channelObj, null);
      }

      public AddChannel(Class<? extends LXPattern> patternClass) {
        this(null, patternClass);
      }

      public AddChannel(JsonObject channelObj, Class<? extends LXPattern> patternClass) {
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
            channel = lx.engine.addChannel(new LXPattern[] { lx.instantiatePattern(this.patternClass) });
          } catch (LX.InstantiationException x) {
            throw new InvalidCommandException(x);
          }
        } else {
          channel = lx.engine.addChannel();
        }
        if (this.channelObj != null) {
          channel.load(lx, this.channelObj);
        }
        this.channelObj = LXSerializable.Utils.toObject(channel);
        this.channel = new ComponentReference<LXChannel>(channel);
        lx.engine.setFocusedChannel(channel);
        lx.engine.selectChannel(channel);
      }

      @Override
      public void undo(LX lx) {
        if (this.channel == null) {
          throw new IllegalStateException(
            "Channel was not successfully added, cannot undo");
        }
        lx.engine.removeChannel(this.channel.get());
      }

    }

    public static class MoveChannel extends LXCommand {

      private final ComponentReference<LXChannelBus> channel;
      private final int delta;

      public MoveChannel(LXChannelBus channel, int delta) {
        this.channel = new ComponentReference<LXChannelBus>(channel);
        this.delta = delta;
      }

      @Override
      public String getDescription() {
        return "Move Channel";
      }

      @Override
      public void perform(LX lx) {
        lx.engine.moveChannel(this.channel.get(), delta);
      }

      @Override
      public void undo(LX lx) {
        lx.engine.moveChannel(this.channel.get(), -delta);
      }

    }

    public static class RemoveChannel extends RemoveComponent {
      private final ComponentReference<LXChannelBus> channel;
      private final JsonObject channelObj;
      private final int index;

      private Parameter.SetNormalized focusedChannel;
      private final List<RemoveChannel> groupChildren = new ArrayList<RemoveChannel>();

      public RemoveChannel(LXChannelBus channel) {
        super(channel);
        this.channel = new ComponentReference<LXChannelBus>(channel);
        this.channelObj = LXSerializable.Utils.toObject(channel);
        this.index = channel.getIndex();
        this.focusedChannel = new Parameter.SetNormalized(channel.getLX().engine.focusedChannel);

        // Are we a group? We'll need to bring our children back as well...
        if (channel instanceof LXGroup) {
          for (LXChannel child : ((LXGroup) channel).channels) {
            this.groupChildren.add(new RemoveChannel(child));
          }
        }

        LXModulationEngine modulation = channel.getLX().engine.modulation;
        for (LXEffect effect : channel.effects) {
          removeMappings(modulation, effect);
        }

        if (channel instanceof LXChannel) {
          for (LXPattern pattern : ((LXChannel)channel).patterns) {
            removeMappings(modulation, pattern);
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
        lx.engine.removeChannel(this.channel.get());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        undo(lx, false);
      }

      public void undo(LX lx, boolean multiRemove) throws InvalidCommandException {
        // Re-load the removed channel
        lx.engine.loadChannel(this.channelObj, this.index);

        // Restore all the group children
        for (RemoveChannel child : this.groupChildren) {
          child.undo(lx);
        }

        // Restore channel focus
        this.focusedChannel.undo(lx);

        if (!multiRemove) {
          lx.engine.selectChannel(this.channel.get());
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
        List<LXChannelBus> removeChannels = new ArrayList<LXChannelBus>();
        for (LXChannelBus channel : lx.engine.channels) {
          if (channel.selected.isOn() && !removeChannels.contains(channel.getGroup())) {
            removeChannels.add(channel);
          }
        }
        for (LXChannelBus removeChannel : removeChannels) {
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
        for (LXBus bus : lx.engine.channels) {
          bus.selected.setValue(false);
        }
        lx.engine.masterChannel.selected.setValue(false);

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
        LXGroup group = lx.engine.addGroup(this.index, false);
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
        lx.engine.ungroup(this.channel.get());

      }

      @Override
      public void undo(LX lx) {
        lx.engine.group(this.group.get(), this.channel.get(), this.index);
      }

    }

    public static class GroupSelectedChannels extends LXCommand {

      private ComponentReference<LXGroup> group;

      @Override
      public String getDescription() {
        return "Add Group";
      }

      @Override
      public void perform(LX lx) {
        this.group = new ComponentReference<LXGroup>(lx.engine.addGroup());
      }

      @Override
      public void undo(LX lx) {
        this.group.get().ungroup();
      }

    }

  }

  public static class Modulation {

    public static class AddModulator extends LXCommand {

      private final ComponentReference<LXModulationEngine> modulation;
      private final Class<? extends LXModulator> modulatorClass;
      private JsonObject modulatorObj;
      private ComponentReference<LXModulator> modulator;

      public AddModulator(LXModulationEngine modulation, Class<? extends LXModulator> modulatorClass) {
        this(modulation, modulatorClass, null);
      }

      public AddModulator(LXModulationEngine modulation, Class<? extends LXModulator> modulatorClass, JsonObject modulatorObj) {
        this.modulation = new ComponentReference<LXModulationEngine>(modulation);
        this.modulatorClass = modulatorClass;
        this.modulatorObj = modulatorObj;
      }

      @Override
      public String getDescription() {
        return "Add " + LXUtils.getComponentName(this.modulatorClass);
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        try {
          LXModulator instance = lx.instantiateModulator(this.modulatorClass);
          if (this.modulatorObj != null) {
            instance.load(lx, this.modulatorObj);
          } else {
            int count = this.modulation.get().getModulatorCount(this.modulatorClass);
            if (count > 0) {
              instance.label.setValue(instance.getLabel() + " " + (count + 1));
            }
          }
          this.modulation.get().addModulator(instance);
          if (this.modulatorObj == null) {
            this.modulatorObj = LXSerializable.Utils.toObject(instance);
          }
          instance.start();
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

        // Not the global modulation engine? Remove from ours as well!
        if (modulation != modulator.getLX().engine.modulation) {
          removeMappings(modulation, modulator);
        }
      }

      @Override
      public String getDescription() {
        return "Remove Modulator";
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
      private final ParameterReference<CompoundParameter> target;

      private ComponentReference<LXCompoundModulation> modulation;

      public AddModulation(LXModulationEngine engine,
        LXNormalizedParameter source, CompoundParameter target) {
        this.engine = new ComponentReference<LXModulationEngine>(engine);
        this.source = new ModulationSourceReference(source);
        this.target = new ParameterReference<CompoundParameter>(target);
      }

      @Override
      public String getDescription() {
        return "Add Modulation";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        try {
          LXCompoundModulation modulation = new LXCompoundModulation(
            this.engine.get(),  this.source.get(), this.target.get());
          this.engine.get().addModulation(modulation);
          this.modulation = new ComponentReference<LXCompoundModulation>(
            modulation);
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

      public RemoveModulation(LXModulationEngine engine,
        LXCompoundModulation modulation) {
        this.engine = new ComponentReference<LXModulationEngine>(engine);
        this.modulation = new ComponentReference<LXCompoundModulation>(
          modulation);
        this.modulationObj = LXSerializable.Utils.toObject(modulation);
      }

      @Override
      public String getDescription() {
        return "Remove Modulation";
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
          this.modulation = new ComponentReference<LXCompoundModulation>(
            modulation);
        } catch (LXParameterModulation.ModulationException mx) {
          throw new InvalidCommandException(mx);
        }
      }
    }

    public static class AddTrigger extends LXCommand {

      private final ComponentReference<LXModulationEngine> engine;
      private final ParameterReference<BooleanParameter> source;
      private final ParameterReference<BooleanParameter> target;
      private ComponentReference<LXTriggerModulation> trigger;

      public AddTrigger(LXModulationEngine engine, BooleanParameter source,
        BooleanParameter target) {
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

      public RemoveTrigger(LXModulationEngine engine,
        LXTriggerModulation trigger) {
        this.engine = new ComponentReference<LXModulationEngine>(engine);
        this.trigger = new ComponentReference<LXTriggerModulation>(trigger);
        this.triggerObj = LXSerializable.Utils.toObject(trigger);
      }

      @Override
      public String getDescription() {
        return "Remove Trigger";
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
  }

  public static class Midi {

    public static class AddMapping extends LXCommand {

      private final LXShortMessage message;
      private final ParameterReference<LXParameter> parameter;
      private LXMidiMapping mapping;

      public AddMapping(LXShortMessage message, LXParameter parameter) {
        this.message = message;
        this.parameter = new ParameterReference<LXParameter>(parameter);
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
        return "Remove MIDI Mapping";
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
