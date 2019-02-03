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
import heronarts.lx.LXPattern;
import heronarts.lx.LXSerializable;
import heronarts.lx.clipboard.LXNormalizedValue;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;

/**
 * An LXCommand is an operation that may be performed by the engine, potentially
 * in two directions, supporting and Undo operation. If you are working directly
 * with the LX API, you are free to ignore this interface. However, if you are
 * building a higher-level UI that you would like to integrate with the undo system,
 * it is best to invoke operations via calls to lx.command.perform().
 */
public abstract class LXCommand {

  /**
   * This reference class is used because the LXCommand engine might have actions in it
   * that refer to components which have been deleted by subsequent operations. If
   * those operations are since un-done, a new object will have been re-created with
   * the same ID. When it comes time to undo *this* action, we need to refer to
   * the appropriate object.
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
    private final ComponentReference<LXComponent> component;
    private final String parameterPath;

    protected ParameterReference(T parameter) {
      LXComponent component = parameter.getComponent();
      if (component != null) {
        // If a parameter is registered to a component, then we keep its location
        // by reference. This way, if a series of other undo or redo actions destroys
        // and restores the object, we'll still point to the correct place
        this.component = new ComponentReference<LXComponent>(component);
        this.parameterPath = parameter.getPath();
        this.rawParameter = null;
      } else {
        // For unregistered parameters, store a raw handle
        this.rawParameter = parameter;
        this.component = null;
        this.parameterPath = null;
      }
    }

    @SuppressWarnings("unchecked")
    public T get() {
      return (this.rawParameter != null) ? this.rawParameter : (T) this.component.get().getParameter(this.parameterPath);
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
   */
  public abstract void perform(LX lx);

  /**
   * Undo the command, after it has been performed
   *
   * @param lx LX instance
   */
  public abstract void undo(LX lx);

  /**
   * Name space for parameter commands
   */
  public static class Parameter {

    public static class Reset extends LXCommand {
      private final ParameterReference<LXParameter> parameter;
      private final double originalValue;

      public Reset(LXParameter parameter) {
        this.parameter = new ParameterReference<LXParameter>(parameter);
        this.originalValue = (parameter instanceof CompoundParameter) ?
          ((CompoundParameter) parameter).getBaseValue() : parameter.getValue();
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
      private final double newGenericValue;

      public SetValue(DiscreteParameter parameter, int value) {
        this.isDiscrete = true;
        this.discreteParameter = new ParameterReference<DiscreteParameter>(parameter);
        this.originalDiscreteValue = parameter.getValuei();
        this.newDiscreteValue = value;

        this.genericParameter = null;
        this.originalGenericValue = 0;
        this.newGenericValue = 0;
      }

      public SetValue(LXParameter parameter, double value) {
        if (parameter instanceof DiscreteParameter) {
          this.isDiscrete = true;
          this.discreteParameter = new ParameterReference<DiscreteParameter>((DiscreteParameter) parameter);
          this.originalDiscreteValue = ((DiscreteParameter) parameter).getValuei();
          this.newDiscreteValue = (int) value;
          this.genericParameter = null;
          this.originalGenericValue = 0;
          this.newGenericValue = 0;
        } else {
          this.isDiscrete = false;
          this.genericParameter = new ParameterReference<LXParameter>(parameter);
          this.originalGenericValue =
            (parameter instanceof CompoundParameter) ?
              ((CompoundParameter) parameter).getBaseValue() : parameter.getValue();
          this.newGenericValue = value;
          this.discreteParameter = null;
          this.originalDiscreteValue = 0;
          this.newDiscreteValue = 0;
        }
      }

      private LXParameter getParameter() {
        return this.isDiscrete ? this.discreteParameter.get() : this.genericParameter.get();
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

      public AddPattern(LXChannel channel, Class<? extends LXPattern> patternClass) {
        this.channel = new ComponentReference<LXChannel>(channel);
        this.patternClass = patternClass;
      }

      @Override
      public String getDescription() {
        return "Add Pattern";
      }

      @Override
      public void perform(LX lx) {
        LXPattern instance = lx.instantiatePattern(this.patternClass);
        if (instance != null) {
          this.channel.get().addPattern(instance);
          this.pattern = new ComponentReference<LXPattern>(instance);
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

    public static class RemovePattern extends LXCommand {

      private final ComponentReference<LXChannel> channel;
      private final ComponentReference<LXPattern> pattern;
      private final JsonObject patternObj;
      private final int patternIndex;
      private final boolean isActive;
      private final boolean isFocused;

      public RemovePattern(LXChannel channel, LXPattern pattern) {
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
      public void undo(LX lx) {
        LXChannel channel = this.channel.get();
        LXPattern pattern = lx.instantiatePattern(this.patternObj.get(LXComponent.KEY_CLASS).getAsString());
        if (pattern != null) {
          pattern.load(lx, patternObj);
          channel.addPattern(pattern, this.patternIndex);
          if (this.isActive) {
            channel.goPattern(pattern);
          }
          if (this.isFocused) {
            channel.focusedPattern.setValue(pattern.getIndex());
          }
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

    public static class AddEffect extends LXCommand {

      private final ComponentReference<LXBus> channel;
      private final Class<? extends LXEffect> effectClass;
      private ComponentReference<LXEffect> effect = null;

      public AddEffect(LXBus channel, Class<? extends LXEffect> effectClass) {
        this.channel = new ComponentReference<LXBus>(channel);
        this.effectClass = effectClass;
      }

      @Override
      public String getDescription() {
        return "Add Effect";
      }

      @Override
      public void perform(LX lx) {
        LXEffect instance = lx.instantiateEffect(this.effectClass);
        if (instance != null) {
          this.channel.get().addEffect(instance);
          this.effect = new ComponentReference<LXEffect>(instance);
        }
      }

      @Override
      public void undo(LX lx) {
        if (this.effect== null) {
          throw new IllegalStateException("Effect was not successfully added, cannot undo");
        }
        this.channel.get().removeEffect(this.effect.get());
      }
    }

    public static class RemoveEffect extends LXCommand {

      private final ComponentReference<LXBus> channel;
      private final ComponentReference<LXEffect> effect;
      private final JsonObject effectObj;
      private final int effectIndex;

      public RemoveEffect(LXBus channel, LXEffect effect) {
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
      public void undo(LX lx) {
        LXBus channel = this.channel.get();
        LXEffect effect = lx.instantiateEffect(this.effectObj.get(LXComponent.KEY_CLASS).getAsString());
        if (effect != null) {
          effect.load(lx, effectObj);
          channel.addEffect(effect, this.effectIndex);
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

      public AddChannel() {
        this(null);
      }

      public AddChannel(Class<? extends LXPattern> patternClass) {
        this.patternClass = patternClass;
      }

      @Override
      public String getDescription() {
        return "Add Channel";
      }

      public LXChannel getChannel() {
        return (this.channel == null ? null : this.channel.get());
      }

      @Override
      public void perform(LX lx) {
        LXChannel channel;
        if (this.patternClass != null) {
          channel = lx.engine.addChannel(new LXPattern[] { lx.instantiatePattern(this.patternClass) });
        } else {
          channel = lx.engine.addChannel();
        }
        this.channel = new ComponentReference<LXChannel>(channel);
        lx.engine.setFocusedChannel(channel);
        lx.engine.selectChannel(channel);
      }

      @Override
      public void undo(LX lx) {
        if (this.channel == null) {
          throw new IllegalStateException("Channel was not successfully added, cannot undo");
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

    public static class RemoveChannel extends LXCommand {
      private final ComponentReference<LXChannelBus> channel;
      private final JsonObject channelObj;
      private final int index;

      private final List<RemoveChannel> groupChildren =
        new ArrayList<RemoveChannel>();

      public RemoveChannel(LXChannelBus channel) {
        this.channel = new ComponentReference<LXChannelBus>(channel);
        this.channelObj = LXSerializable.Utils.toObject(channel);
        this.index = channel.getIndex();

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
        lx.engine.removeChannel(this.channel.get());
      }

      @Override
      public void undo(LX lx) {
        lx.engine.loadChannel(this.channelObj, this.index);

        // Restore all the group children
        for (RemoveChannel child : this.groupChildren) {
          child.undo(lx);
        }
      }

    }

    public static class RemoveSelectedChannels extends LXCommand {

      private final List<RemoveChannel> removedChannels =
        new ArrayList<RemoveChannel>();

      @Override
      public String getDescription() {
        return "Delete Channels";
      }

      @Override
      public void perform(LX lx) {
        // Serialize a list of all the channels that will end up removed, so we can undo properly
        List<LXChannelBus> removeChannels = new ArrayList<LXChannelBus>();
        for (LXChannelBus channel : lx.engine.channels) {
          if (channel.selected.isOn() && !removeChannels.contains(channel.getGroup())) {
            removeChannels.add(channel);
          }
        }
        for (LXChannelBus removeChannel : removeChannels) {
          this.removedChannels.add(new RemoveChannel(removeChannel));
        }
        lx.engine.removeSelectedChannels();
      }

      @Override
      public void undo(LX lx) {
        for (RemoveChannel removedChannel : this.removedChannels) {
          removedChannel.undo(lx);
        }
      }

    }

    public static class Ungroup extends LXCommand {
      private final ComponentReference<LXGroup> group;
      private final int index;

      private final List<ComponentReference<LXChannel>> groupChannels =
        new ArrayList<ComponentReference<LXChannel>>();

      public Ungroup(LXGroup group) {
        this.group = new ComponentReference<LXGroup>(group);
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


}
