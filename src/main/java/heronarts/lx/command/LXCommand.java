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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXDeviceComponent;
import heronarts.lx.LXPath;
import heronarts.lx.LXPresetComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.clip.Cursor;
import heronarts.lx.clip.LXChannelClip;
import heronarts.lx.clip.LXClip;
import heronarts.lx.clip.LXClipEvent;
import heronarts.lx.clip.LXClipLane;
import heronarts.lx.clip.MidiNoteClipEvent;
import heronarts.lx.clip.MidiNoteClipLane;
import heronarts.lx.clip.ParameterClipEvent;
import heronarts.lx.clip.ParameterClipLane;
import heronarts.lx.clip.PatternClipEvent;
import heronarts.lx.clip.PatternClipLane;
import heronarts.lx.color.ColorParameter;
import heronarts.lx.color.LXDynamicColor;
import heronarts.lx.color.LXPalette;
import heronarts.lx.color.LXSwatch;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.midi.LXMidiEngine;
import heronarts.lx.midi.LXMidiMapping;
import heronarts.lx.midi.LXShortMessage;
import heronarts.lx.midi.MidiNote;
import heronarts.lx.midi.template.LXMidiTemplate;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXGroup;
import heronarts.lx.mixer.LXMixerEngine;
import heronarts.lx.mixer.LXPatternEngine;
import heronarts.lx.modulation.LXCompoundModulation;
import heronarts.lx.modulation.LXModulationContainer;
import heronarts.lx.modulation.LXModulationEngine;
import heronarts.lx.modulation.LXParameterModulation;
import heronarts.lx.modulation.LXTriggerModulation;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.osc.LXOscConnection;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.pattern.PatternRack;
import heronarts.lx.snapshot.LXSnapshot;
import heronarts.lx.snapshot.LXClipSnapshot;
import heronarts.lx.snapshot.LXGlobalSnapshot;
import heronarts.lx.snapshot.LXSnapshotEngine;
import heronarts.lx.structure.JsonFixture;
import heronarts.lx.structure.LXFixture;
import heronarts.lx.structure.LXStructure;
import heronarts.lx.structure.view.LXViewDefinition;
import heronarts.lx.utils.LXUtils;

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

    /**
     * Events that remove multiple components in coordinated fashion (e.g. GroupPattern) may
     * detect the same modulations from multiple sub-actions, say for instance there is a
     * modulation between PatternA.param -> PatternB.param. The RemovePattern() action for
     * A and B will *both* pick up that this modulation needs removing. But we only want
     * that to actually happen once, so we track modulations by ID in this situation.
     */
    static class ModulationContext {
      private Set<Integer> uniqueModulations = new HashSet<>();
    }

    private final ModulationContext modulationContext;

    final List<Modulation.RemoveModulation> removeModulations = new ArrayList<Modulation.RemoveModulation>();
    final List<Modulation.RemoveTrigger> removeTriggers = new ArrayList<Modulation.RemoveTrigger>();
    final List<Midi.RemoveMapping> removeMidiMappings = new ArrayList<Midi.RemoveMapping>();
    final List<Snapshots.RemoveView> removeSnapshotViews = new ArrayList<Snapshots.RemoveView>();
    final List<Clip.RemoveClipLane> removeClipLanes = new ArrayList<>();
    final List<Clip.Event.Pattern.RemoveReferences> removePatternClipEvents = new ArrayList<>();
    final List<Device.SetRemoteControls> removeRemoteControls = new ArrayList<>();

    private boolean shouldRemoveModulation(LXParameterModulation modulation) {
      if (this.modulationContext == null) {
        return true;
      }
      if (this.modulationContext.uniqueModulations.contains(modulation.getId())) {
        return false;
      }
      this.modulationContext.uniqueModulations.add(modulation.getId());
      return true;
    }

    private void _removeModulations(LXModulationEngine modulation, LXComponent component) {
      List<LXCompoundModulation> compounds = modulation.findModulations(component, modulation.modulations);
      if (compounds != null) {
        for (LXCompoundModulation compound : compounds) {
          if (shouldRemoveModulation(compound)) {
            this.removeModulations.add(new Modulation.RemoveModulation(modulation, compound));
          }
        }
      }
    }

    private void _removeTriggers(LXModulationEngine modulation, LXComponent component) {
      List<LXTriggerModulation> triggers = modulation.findModulations(component, modulation.triggers);
      if (triggers != null) {
        for (LXTriggerModulation trigger : triggers) {
          if (shouldRemoveModulation(trigger)) {
            this.removeTriggers.add(new Modulation.RemoveTrigger(modulation, trigger));
          }
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
      if (views != null) {
        for (LXSnapshot.View view : views) {
          this.removeSnapshotViews.add(new Snapshots.RemoveView(view));
        }
      }
    }

    protected void removeRemoteControls(LXComponent component) {
      _removeRemoteControls(component.getParent(), component);
    }

    private void _removeRemoteControls(LXComponent container, LXComponent component) {
      if ((container == null) || (container instanceof LXBus)) {
        return;
      }
      if (container instanceof LXDeviceComponent device) {
        final LXListenableNormalizedParameter[] customRemoteControls = device.getCustomRemoteControls();
        if (customRemoteControls != null) {
          boolean removed = false;
          for (LXListenableNormalizedParameter parameter : customRemoteControls) {
            if ((parameter != null) && parameter.isDescendant(component)) {
              removed = true;
              break;
            }
          }
          if (removed) {
            // NOTE: only gonna undo() this, don't need actual changes
            this.removeRemoteControls.add(new Device.SetRemoteControls(device, null));
          }
        }
      }
      _removeRemoteControls(container.getParent(), component);
    }

    protected void removeClipLanes(LXBus bus, LXComponent component) {
      for (LXClip clip : bus.clips) {
        if (clip != null) {
          List<LXClipLane<?>> lanes = clip.findClipLanes(component);
          if (lanes != null) {
            for (LXClipLane<?> lane : lanes) {
              this.removeClipLanes.add(new Clip.RemoveClipLane(lane));
            }
          }
        }
      }
    }

    protected void removePatternClipEvents(LXPattern pattern) {
      for (LXClip clip : pattern.getMixerChannel().clips) {
        if (clip != null) {
          if (clip instanceof LXChannelClip channelClip) {
            for (LXClipLane<?> clipLane : channelClip.lanes) {
              if (clipLane instanceof PatternClipLane patternLane) {
                if (pattern.getEngine() == patternLane.engine) {
                  removePatternClipLaneEvents(patternLane, pattern);
                }
              }
            }
          }
        }
      }
    }

    protected void removePatternClipLaneEvents(PatternClipLane lane, LXPattern pattern) {
      List<Integer> eventIndices = lane.findEventIndices(pattern);
      if (eventIndices != null) {
        this.removePatternClipEvents.add(new Clip.Event.Pattern.RemoveReferences(lane, eventIndices));
      }
    }

    protected RemoveComponent(LXComponent component) {
      this(component, null);
    }

    protected RemoveComponent(LXComponent component, ModulationContext modulationContext) {
      this.modulationContext = modulationContext;

      // Tally up all the modulations and triggers that relate to this component and must be restored!
      LXComponent parent = component.getParent();
      while (parent != null) {
        if (parent instanceof LXModulationContainer modulationContainer) {
          removeModulationMappings(modulationContainer.getModulationEngine(), component);
        }
        parent = parent.getParent();
      }

      // Also top level mappings, snapshot views, remote controls
      removeMidiMappings(component.getLX().engine.midi, component);
      removeSnapshotViews(component.getLX().engine.snapshots, component);
      removeRemoteControls(component);

      // Type-specific removals
      switch (component) {
        case LXPattern pattern -> {
          removeClipLanes(pattern.getMixerChannel(), pattern);
          removePatternClipEvents(pattern);
        }
        case LXEffect effect -> {
          if (effect.isBusEffect()) {
            removeClipLanes(effect.getBus(), effect);
          } else if (effect.isPatternEffect()) {
            removeClipLanes(effect.getPattern().getMixerChannel(), effect);
          }
        }
        default -> {}
      }
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
      for (Device.SetRemoteControls controls : this.removeRemoteControls) {
        controls.undo(lx);
      }
      for (Clip.RemoveClipLane lane : this.removeClipLanes) {
        lane.undo(lx);
      }
      for (Clip.Event.Pattern.RemoveReferences patternReferences : this.removePatternClipEvents) {
        patternReferences.undo(lx);
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

    protected static class MultiSetValue extends LXCommand {

      private final List<Parameter.SetValue> setValues = new ArrayList<>();

      private final String description;

      protected MultiSetValue(String description) {
        this.description = description;
      }

      public void add(LXParameter parameter, double value) {
        this.setValues.add(new Parameter.SetValue(parameter, value));
      }

      public void add(BooleanParameter parameter, boolean value) {
        add(parameter, value ? 1 : 0);
      }

      @Override
      public String getDescription() {
        return this.description;
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        this.setValues.forEach(setValue -> setValue.perform(lx));

      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        this.setValues.forEach(setValue -> setValue.undo(lx));
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

      private final ComponentReference<LXComponent> component;
      private final Class<? extends LXPattern> patternClass;
      private ComponentReference<LXPattern> pattern = null;
      private JsonObject patternObj;
      private int patternIndex;

      public AddPattern(LXPatternEngine.Container container, Class<? extends LXPattern> patternClass) {
        this(container.getPatternEngine(), patternClass);
      }

      public AddPattern(LXPatternEngine engine, Class<? extends LXPattern> patternClass) {
        this(engine, patternClass, null);
      }

      public AddPattern(LXPatternEngine engine, Class<? extends LXPattern> patternClass, int patternIndex) {
        this(engine, patternClass, null, patternIndex);
      }

      public AddPattern(LXPatternEngine.Container container, Class<? extends LXPattern> patternClass, JsonObject patternObject) {
        this(container.getPatternEngine(), patternClass, patternObject);
      }

      public AddPattern(LXPatternEngine engine, Class<? extends LXPattern> patternClass, JsonObject patternObject) {
        this(engine, patternClass, patternObject, -1);
      }

      public AddPattern(LXPatternEngine.Container container, Class<? extends LXPattern> patternClass, JsonObject patternObject, int patternIndex) {
        this(container.getPatternEngine(), patternClass, patternObject, patternIndex);
      }

      public AddPattern(LXPatternEngine engine, Class<? extends LXPattern> patternClass, JsonObject patternObject, int patternIndex) {
        this.component = new ComponentReference<LXComponent>(engine.component);
        this.patternClass = patternClass;
        this.patternObj = patternObject;
        this.patternIndex = patternIndex;
      }

      private LXPatternEngine getPatternEngine() {
        return ((LXPatternEngine.Container) this.component.get()).getPatternEngine();
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
          getPatternEngine().addPattern(instance, this.patternIndex);
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
        getPatternEngine().removePattern(this.pattern.get());
      }

      ComponentReference<LXPattern> getPattern() {
        return this.pattern;
      }

    }

    public static class RemovePattern extends RemoveComponent {

      private final String path;
      private final ComponentReference<LXComponent> component;
      private final ComponentReference<LXPattern> pattern;
      private final JsonObject patternObj;
      private final int patternIndex;
      private final boolean isActive;
      private final boolean isFocused;

      public RemovePattern(LXPatternEngine.Container container, LXPattern pattern) {
        this(container.getPatternEngine(), pattern);
      }

      public RemovePattern(LXPatternEngine engine, LXPattern pattern) {
        this(engine, pattern, null);
      }

      private RemovePattern(LXPatternEngine engine, LXPattern pattern, ModulationContext context) {
        super(pattern, context);
        if (!engine.patterns.contains(pattern)) {
          throw new IllegalArgumentException("Cannot remove pattern not present in engine: " + pattern + " !! " + engine.component);
        }
        this.path = pattern.getCanonicalPath();
        this.component = new ComponentReference<LXComponent>(engine.component);
        this.pattern = new ComponentReference<LXPattern>(pattern);
        this.patternObj = LXSerializable.Utils.toObject(pattern);
        this.patternIndex = pattern.getIndex();
        this.isActive = engine.getActivePattern() == pattern;
        this.isFocused = engine.getFocusedPattern() == pattern;
      }

      @Override
      public String getDescription() {
        return "Delete Pattern";
      }

      private LXPatternEngine getPatternEngine() {
        return ((LXPatternEngine.Container) this.component.get()).getPatternEngine();
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        getPatternEngine().removePattern(this.pattern.get());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        undoPattern(lx);
        undoReferences(lx);
      }

      private void undoPattern(LX lx) {
        LXPatternEngine engine = getPatternEngine();
        LXPattern pattern = engine.loadPattern(this.patternObj, this.patternIndex);
        if (this.isActive) {
          engine.goPattern(pattern, true);
        }
        if (this.isFocused) {
          engine.focusedPattern.setValue(pattern.getIndex());
        }
      }

      private void undoReferences(LX lx) throws InvalidCommandException {
        super.undo(lx);
      }
    }

    public static class RemovePatterns extends LXCommand {

      private final List<RemovePattern> removePatterns = new ArrayList<>();

      public RemovePatterns(LXPatternEngine patternEngine, List<LXPattern> patterns) {
        final RemoveComponent.ModulationContext context = new RemoveComponent.ModulationContext();
        for (LXPattern pattern : patterns) {
          this.removePatterns.add(new RemovePattern(patternEngine, pattern, context));
        }
      }

      @Override
      public String getDescription() {
        return "Delete Patterns";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        for (int i = this.removePatterns.size() - 1; i >=0; --i) {
          this.removePatterns.get(i).perform(lx);
        }
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        for (RemovePattern removePattern : this.removePatterns) {
          removePattern.undoPattern(lx);
        }
        for (RemovePattern removePattern : this.removePatterns) {
          removePattern.undoReferences(lx);
        }
      }

    }

    public static class GroupPatterns extends LXCommand {

      private final ComponentReference<LXComponent> component;
      private final RemovePatterns removePatterns;
      private final AddPattern addRack;
      private final int focusedIndex;
      private final int targetIndex;
      private final int engineTargetIndex;
      private ComponentReference<LXPattern> rack;
      private final Map<String, String> pathChanges = new HashMap<>();

      public GroupPatterns(LXPatternEngine patternEngine, List<LXPattern> patterns) {
        this.component = new ComponentReference<>(patternEngine.component);
        final LXPattern targetPattern = patternEngine.getTargetPattern();
        if (targetPattern != null) {
          this.targetIndex = patterns.indexOf(targetPattern);
          this.engineTargetIndex = patternEngine.patterns.indexOf(targetPattern);
        } else {
          this.targetIndex = this.engineTargetIndex = -1;
        }
        this.focusedIndex = patternEngine.focusedPattern.getValuei();
        this.removePatterns = new RemovePatterns(patternEngine, patterns);
        this.addRack = new AddPattern(patternEngine, PatternRack.class, patterns.get(0).getIndex());
      }

      private LXPatternEngine getPatternEngine() {
        return ((LXPatternEngine.Container) this.component.get()).getPatternEngine();
      }

      @Override
      public String getDescription() {
        return "Group Patterns to Rack";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        final LXPatternEngine engine = getPatternEngine();

        final Map<LXPattern, String> patternPath = new HashMap<>();
        engine.patterns.forEach(pattern -> patternPath.put(pattern, pattern.getCanonicalPath()));

        this.removePatterns.perform(lx);
        this.addRack.perform(lx);
        this.rack = this.addRack.getPattern();
        final PatternRack rack = (PatternRack) this.rack.get();

        // Path may have updated since # of patterns may have changed
        engine.patterns.forEach(pattern -> {
          if (pattern != rack) {
            String originalPath = patternPath.get(pattern);
            String newPath = pattern.getCanonicalPath();
            if (!newPath.equals(originalPath)) {
              this.pathChanges.put(originalPath, newPath);
            }
          }
        });

        // Copy pattern engine parameters
        final LXPatternEngine rackEngine = rack.getPatternEngine();
        rackEngine.compositeMode.setValue(engine.compositeMode.getEnum());
        rackEngine.compositeDampingEnabled.setValue(engine.compositeDampingEnabled.isOn());
        rackEngine.compositeDampingTimeSecs.setValue(engine.compositeDampingTimeSecs.getValue());
        rackEngine.autoCycleEnabled.setValue(engine.autoCycleEnabled.isOn());
        rackEngine.autoCycleMode.setValue(engine.autoCycleMode.getEnum());
        rackEngine.autoCycleTimeSecs.setValue(engine.autoCycleTimeSecs.getValue());
        rackEngine.transitionTimeSecs.setValue(engine.transitionTimeSecs.getValue());
        rackEngine.transitionEnabled.setValue(engine.transitionEnabled.isOn());
        rackEngine.transitionBlendMode.setIndex(engine.transitionBlendMode.getIndex());

        // And duplicate midi filter settings
        final LXComponent source = this.component.get();
        if (source instanceof LXChannel channel) {
          rack.midiFilter.set(channel.midiFilter);
        } else if (source instanceof LXPattern pattern) {
          rack.midiFilter.set(pattern.midiFilter);
        }

        movePatterns(lx, rackEngine, rack);
        if (this.targetIndex >= 0) {
          rackEngine.goPattern(rack.patterns.get(this.targetIndex), true);
          engine.goPattern(rack, true);
        }
        if (engine.focusedPattern.getValuei() != rack.getIndex()) {
          engine.focusedPattern.setValue(rack.getIndex());
        } else {
          engine.focusedPattern.bang();
        }
      }

      private void movePatterns(LX lx, LXPatternEngine engine, PatternRack rack) throws InvalidCommandException {
        int patternIndex = 0;
        for (RemovePattern removePattern : this.removePatterns.removePatterns) {
          final LXPattern moved = engine.loadPattern(removePattern.patternObj, patternIndex++);

          // Modulations may have references between *multiple* moved patterns as both
          // source and target, so we need to build a full map of all the path changes
          this.pathChanges.put(removePattern.path, moved.getCanonicalPath());
        }

        // Now that all patterns are moved, update references to all of them
        for (RemovePattern removePattern : this.removePatterns.removePatterns) {
          final String toPath = this.pathChanges.get(removePattern.path);

          for (Modulation.RemoveModulation modulation : removePattern.removeModulations) {
            modulation.move(lx, this.pathChanges);
          }
          for (Modulation.RemoveTrigger trigger : removePattern.removeTriggers) {
            trigger.move(lx, this.pathChanges);
          }
          for (Midi.RemoveMapping mapping : removePattern.removeMidiMappings) {
            mapping.move(lx, removePattern.path, toPath);
          }
          for (Snapshots.RemoveView view : removePattern.removeSnapshotViews) {
            view.move(lx, removePattern.path, toPath, rack);
          }
          for (Device.SetRemoteControls controls : removePattern.removeRemoteControls) {
            controls.move(lx, removePattern.path, toPath);
          }
          for (Clip.RemoveClipLane lane : removePattern.removeClipLanes) {
            lane.move(lx, removePattern.path, toPath);
          }
          for (Clip.Event.Pattern.RemoveReferences patternReferences : removePattern.removePatternClipEvents) {
            patternReferences.move(lx, rack);
          }
        }
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        this.addRack.undo(lx);
        this.removePatterns.undo(lx);
        final LXPatternEngine engine = getPatternEngine();
        if (this.engineTargetIndex >= 0) {
          engine.goPattern(engine.patterns.get(this.engineTargetIndex), true);
        }
        if (engine.focusedPattern.getValuei() != this.focusedIndex) {
          engine.focusedPattern.setValue(this.focusedIndex);
        } else {
          engine.focusedPattern.bang();
        }
      }
    }

    public static class ReloadPattern extends RemovePattern {

      public ReloadPattern(LXPatternEngine.Container container, LXPattern pattern) {
        this(container.getPatternEngine(), pattern);
      }

      public ReloadPattern(LXPatternEngine engine, LXPattern pattern) {
        super(engine, pattern);
      }

      @Override
      public boolean isIgnored() {
        return true;
      }

      @Override
      public String getDescription() {
        return "Reload Pattern";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        super.perform(lx);

        // Immediately undo! Creates a new instance of the pattern in the same place and
        // restores all modulation, automation, whatever else
        super.undo(lx);
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        throw new IllegalStateException("May not explicitly undo ReloadPattern command");
      }

    }

    public static class MovePattern extends LXCommand {

      private final ComponentReference<LXComponent> component;
      private final ComponentReference<LXPattern> pattern;
      private final int fromIndex;
      private final int toIndex;

      public MovePattern(LXPatternEngine.Container container, LXPattern pattern, int toIndex) {
        this(container.getPatternEngine(), pattern, toIndex);
      }

      public MovePattern(LXPatternEngine engine, LXPattern pattern, int toIndex) {
        this.component = new ComponentReference<LXComponent>(engine.component);
        this.pattern = new ComponentReference<LXPattern>(pattern);
        this.fromIndex = pattern.getIndex();
        this.toIndex = toIndex;
      }

      @Override
      public String getDescription() {
        return "Move Pattern";
      }

      private LXPatternEngine getPatternEngine() {
        return ((LXPatternEngine.Container) this.component.get()).getPatternEngine();
      }

      @Override
      public void perform(LX lx) {
        getPatternEngine().movePattern(this.pattern.get(), this.toIndex);
      }

      @Override
      public void undo(LX lx) {
        getPatternEngine().movePattern(this.pattern.get(), this.fromIndex);
      }
    }

    public static class GoPattern extends LXCommand {

      private final ComponentReference<LXComponent> component;
      private final ComponentReference<LXPattern> prevPattern;
      private final ComponentReference<LXPattern> nextPattern;

      public GoPattern(LXPatternEngine.Container container, LXPattern nextPattern) {
        this(container.getPatternEngine(), nextPattern);
      }

      public GoPattern(LXPatternEngine engine, LXPattern nextPattern) {
        this.component = new ComponentReference<LXComponent>(engine.component);
        this.prevPattern = new ComponentReference<LXPattern>(engine.getActivePattern());
        this.nextPattern = new ComponentReference<LXPattern>(nextPattern);
      }

      @Override
      public String getDescription() {
        return "Change Pattern";
      }

      private LXPatternEngine getPatternEngine() {
        return ((LXPatternEngine.Container) this.component.get()).getPatternEngine();
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        getPatternEngine().goPattern(this.nextPattern.get());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        getPatternEngine().goPattern(this.prevPattern.get());
      }
    }

    public static class PatternCycle extends LXCommand {

      private final ComponentReference<LXComponent> component;
      private final ComponentReference<LXPattern> prevPattern;
      private ComponentReference<LXPattern> targetPattern;

      public PatternCycle(LXPatternEngine patternEngine) {
        this.component = new ComponentReference<LXComponent>(patternEngine.component);
        if (patternEngine.isPlaylist() && !patternEngine.isInTransition()) {
          final LXPattern prev = patternEngine.getActivePattern();
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

      private LXPatternEngine getPatternEngine() {
        return ((LXPatternEngine.Container) this.component.get()).getPatternEngine();
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        final LXPatternEngine engine = getPatternEngine();
        if (this.targetPattern == null) {
          engine.triggerPatternCycle.trigger();
          this.targetPattern = new ComponentReference<LXPattern>(engine.getTargetPattern());
        } else {
          engine.goPattern(this.targetPattern.get());
        }
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        if (this.prevPattern != null) {
          getPatternEngine().goPattern(this.prevPattern.get());
        }
      }
    }

    private static ComponentReference<LXComponent> validateEffectContainer(LXComponent container) {
      if (!(container instanceof LXEffect.Container)) {
        throw new IllegalArgumentException("Parent of an LXEffect must be an LXEffect.Container");
      }
      return new ComponentReference<LXComponent>(container);
    }

    public static class AddEffect extends LXCommand {

      private final ComponentReference<LXComponent> container;
      private final Class<? extends LXEffect> effectClass;
      private ComponentReference<LXEffect> effect = null;
      private JsonObject effectObj = null;

      public AddEffect(LXComponent parent, Class<? extends LXEffect> effectClass) {
        this(parent, effectClass, null);
      }

      public AddEffect(LXComponent parent, Class<? extends LXEffect> effectClass, JsonObject effectObj) {
        this.container = validateEffectContainer(parent);
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
          ((LXEffect.Container) this.container.get()).addEffect(instance);
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
        LXEffect effect = this.effect.get();
        effect.getContainer().removeEffect(effect);
      }
    }

    public static class RemoveEffect extends RemoveComponent {

      private final ComponentReference<LXComponent> container;
      private final ComponentReference<LXEffect> effect;
      final JsonObject effectObj;
      private final int effectIndex;

      public RemoveEffect(LXComponent container, LXEffect effect) {
        super(effect);
        this.container = validateEffectContainer(container);
        this.effect = new ComponentReference<LXEffect>(effect);
        this.effectObj = LXSerializable.Utils.toObject(effect);
        this.effectIndex = effect.getIndex();
      }

      @Override
      public String getDescription() {
        return "Remove Effect";
      }

      protected void checkLocked() {
        if (this.effect.get().locked.isOn()) {
          throw new IllegalStateException("Locked effects cannot be removed, UI should disallow this");
        }
      }

      protected LXEffect.Container getEffectContainer() {
        return (LXEffect.Container) this.container.get();
      }

      protected LXEffect getEffect() {
        return this.effect.get();
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        checkLocked();
        getEffectContainer().removeEffect(this.effect.get());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        LXEffect.Container container = (LXEffect.Container) this.container.get();
        container.loadEffect(lx, this.effectObj, this.effectIndex);
        super.undo(lx);
      }
    }

    public static class ReloadEffect extends RemoveEffect {
      public ReloadEffect(LXComponent container, LXEffect effect) {
        super(container, effect);
      }

      @Override
      protected void checkLocked() {
        // It's acceptable to reload a locked effect
      }

      @Override
      public boolean isIgnored() {
        return true;
      }

      @Override
      public String getDescription() {
        return "Reload Effect";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        super.perform(lx);

        // Immediately undo! Creates a new instance of the effect in the same place and
        // restores all modulation, automation, whatever else
        super.undo(lx);
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        throw new IllegalStateException("May not explicitly undo ReloadEffect command");
      }


    }

    public static class MoveEffect extends LXCommand {

      private final ComponentReference<LXComponent> parent;
      private final ComponentReference<LXEffect> effect;
      private final int fromIndex;
      private final int toIndex;

      public MoveEffect(LXComponent parent, LXEffect effect, int toIndex) {
        this.parent = validateEffectContainer(parent);
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

    public static class RelocateEffect extends RemoveEffect {

      private final String fromPath;
      private final ComponentReference<LXComponent> target;
      private final int effectIndex;
      private ComponentReference<LXEffect> moved;
      private final Map<String, String> pathChanges = new HashMap<>();

      public RelocateEffect(LXEffect effect, LXEffect.Container target, int effectIndex) {
        super(effect.getParent(), effect);
        this.target = new ComponentReference<>((LXComponent) target);
        this.fromPath = effect.getCanonicalPath();
        this.effectIndex = effectIndex;
      }

      @Override
      public String getDescription() {
        return "Relocate Effect";
      }

      private LXEffect.Container getTargetContainer() {
        return (LXEffect.Container) this.target.get();
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        final LXEffect.Container originalContainer = getEffectContainer();
        final LXEffect.Container targetContainer = getTargetContainer();

        // Cache original effect paths
        final Map<LXEffect, String> effectPath = new HashMap<>();
        originalContainer.getEffects().forEach(effect -> effectPath.put(effect, effect.getCanonicalPath()));

        // Remove it
        super.perform(lx);

        final LXEffect moved = targetContainer.loadEffect(lx, this.effectObj, this.effectIndex);
        this.moved = new ComponentReference<>(moved);

        // Path may have updated since # of patterns may have changed
        originalContainer.getEffects().forEach(effect -> {
          String originalPath = effectPath.get(effect);
          String newPath = effect.getCanonicalPath();
          if (!newPath.equals(originalPath)) {
            this.pathChanges.put(originalPath, newPath);
          }
        });

        final String toPath = moved.getCanonicalPath();
        this.pathChanges.put(this.fromPath, toPath);

        final Map<String, String> pathChanges = new HashMap<>();
        pathChanges.put(this.fromPath, toPath);

        // Restore references to the effect in a new position
        for (Modulation.RemoveModulation modulation : this.removeModulations) {
          modulation.move(lx, pathChanges, moved);
        }
        for (Modulation.RemoveTrigger trigger : this.removeTriggers) {
          trigger.move(lx, pathChanges, moved);
        }
        for (Midi.RemoveMapping mapping : this.removeMidiMappings) {
          mapping.move(lx, this.fromPath, toPath);
        }
        for (Snapshots.RemoveView view : this.removeSnapshotViews) {
          view.move(lx, this.fromPath, toPath, null);
        }
        for (Clip.RemoveClipLane lane : this.removeClipLanes) {
          lane.move(lx, this.fromPath, toPath);
        }

        // TODO(relocate): this is more subtle, the effect could have moved *out* of a
        // context where it's a valid remote control, would need to check these...
        // for (Device.SetRemoteControls controls : this.removeRemoteControls) {
        //   controls.move(lx, this.fromPath, toPath);
        // }
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        getTargetContainer().removeEffect(this.moved.get());
        this.moved = null;
        super.undo(lx);
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

      private void move(LX lx, String fromPath, String toPath) {
        if (this.oldCustomControls != null) {
          final String prefix = this.device.get().getCanonicalPath();
          fromPath = LXPath.stripPrefix(fromPath, prefix);
          toPath = LXPath.stripPrefix(toPath, prefix);
          int i = 0;
          final String[] moveCustomControls = new String[this.oldCustomControls.length];
          for (String str : this.oldCustomControls) {
            if (str != null) {
              moveCustomControls[i] = LXPath.replacePrefix(str, fromPath, toPath);
            }
            ++i;
          }
          this.device.get().setCustomRemoteControls(toControls(moveCustomControls));
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
            channel = lx.engine.mixer.addChannel(this.index, new LXPattern[]{lx.instantiatePattern(this.patternClass)});
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

    public static class AutoMute extends Parameter.MultiSetValue {

      public AutoMute(LXPatternEngine patternEngine, boolean autoMute) {
        super("Auto-Mute " + (autoMute ? "All Patterns" : " No Patterns"));
        for (LXPattern pattern : patternEngine.patterns) {
          add(pattern.autoMute, autoMute);
        }
      }

      public AutoMute(LXMixerEngine mixer, boolean autoMute) {
        super("Auto-Mute " + (autoMute ? "All Channels" : " No Channels"));
        for (LXAbstractChannel bus : mixer.channels) {
          add(bus.autoMute, autoMute);
        }
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

    public static class RemoveModulation extends RemoveComponent {

      private final ComponentReference<LXModulationEngine> engine;
      private ComponentReference<LXCompoundModulation> modulation;
      private final JsonObject modulationObj;

      public RemoveModulation(LXModulationEngine engine, LXCompoundModulation modulation) {
        super(modulation);
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
          final LXModulationEngine engine = this.engine.get();
          final LXCompoundModulation modulation = new LXCompoundModulation(lx, engine, this.modulationObj);
          engine.addModulation(modulation);
          modulation.load(lx, this.modulationObj);
          this.modulation = new ComponentReference<>(modulation);
          super.undo(lx);
        } catch (LXParameterModulation.ModulationException mx) {
          throw new InvalidCommandException(mx);
        }
      }

      private void move(LX lx, Map<String, String> pathChanges) throws InvalidCommandException {
        move(lx, pathChanges, null);
      }

      private void move(LX lx, Map<String, String> pathChanges, LXComponent moved) throws InvalidCommandException {
        try {
          final LXModulationEngine engine = this.engine.get();
          JsonObject moveObj = LXParameterModulation.move(this.modulationObj, engine, pathChanges, moved);
          if (moveObj != null) {
            final LXCompoundModulation modulation = new LXCompoundModulation(lx, engine, moveObj);
            engine.addModulation(modulation);
            modulation.load(lx, moveObj);
          }
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

    public static class RemoveTrigger extends RemoveComponent {

      private final ComponentReference<LXModulationEngine> engine;
      private ComponentReference<LXTriggerModulation> trigger;
      private final JsonObject triggerObj;

      public RemoveTrigger(LXModulationEngine engine, LXTriggerModulation trigger) {
        super(trigger);
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
          super.undo(lx);
        } catch (LXParameterModulation.ModulationException mx) {
          throw new InvalidCommandException(mx);
        }
      }

      private void move(LX lx, Map<String, String> pathChanges) throws InvalidCommandException {
        move(lx, pathChanges, null);
      }

      private void move(LX lx, Map<String, String> pathChanges, LXComponent moved) throws InvalidCommandException {
        try {
          final LXModulationEngine engine = this.engine.get();
          JsonObject moveObj = LXParameterModulation.move(this.triggerObj, engine, pathChanges, moved);
          if (moveObj != null) {
            final LXTriggerModulation trigger = new LXTriggerModulation(lx, engine, moveObj);
            engine.addTrigger(trigger);
            trigger.load(lx, moveObj);
          }
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

      public SaveSwatch() {
      }

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
          this.importedSwatches = new ArrayList<ImportedSwatch>();
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

    public static class RecallImmediate extends LXCommand {
      private final ComponentReference<LXClipSnapshot> snapshot;
      private final List<LXCommand> commands = new ArrayList<LXCommand>();

      public RecallImmediate(LXClipSnapshot snapshot) {
        this.snapshot = new ComponentReference<LXClipSnapshot>(snapshot);
      }

      @Override
      public void perform(LX lx) {
        this.commands.clear();
        this.snapshot.get().recallImmediate(this.commands);
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        for (LXCommand command : this.commands) {
          command.undo(lx);
        }
      }

      @Override
      public String getDescription() {
        return "Recall Clip Snapshot";
      }
    }

    public static class RemoveView extends LXCommand {

      private final ComponentReference<LXSnapshot> snapshot;
      private final String viewPath;
      private final JsonObject viewObj;
      private final String label;

      public RemoveView(LXSnapshot.View view) {
        this.snapshot = new ComponentReference<LXSnapshot>(view.getSnapshot());
        this.viewPath = view.getViewPath();
        this.viewObj = LXSerializable.Utils.toObject(view.getSnapshot().getLX(), view);
        this.label = view.getLabel();
      }

      @Override
      public String getDescription() {
        return "Delete Snapshot View " + this.label;
      }

      @Override
      public void perform(LX lx) {
        final LXSnapshot snapshot = this.snapshot.get();
        snapshot.removeView(snapshot.getView(this.viewPath));
      }

      @Override
      public void undo(LX lx) {
        this.snapshot.get().addView(this.viewObj);
      }

      private void move(LX lx, String fromPath, String toPath, PatternRack rack) throws InvalidCommandException {
        this.snapshot.get().moveView(this.viewObj, fromPath, toPath, rack);
      }
    }

    public static class RemoveViews extends LXCommand {

      private final List<RemoveView> removeViews = new ArrayList<>();
      private final String label;

      public RemoveViews(String label, List<LXSnapshot.View> views) {
        this.label = label;
        views.forEach(view -> this.removeViews.add(new RemoveView(view)));
      }

      @Override
      public String getDescription() {
        return "Remove Snapshot Views " + this.label;
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        this.removeViews.forEach(removeView -> removeView.perform(lx));
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        this.removeViews.forEach(removeView -> removeView.undo(lx));
      }
    }

    public static class UpdateView extends LXCommand {

      private final ComponentReference<LXSnapshot> snapshot;
      private final String viewPath;
      private final String label;

      private boolean discrete = false, string = false;

      private int fromInt;
      private double fromValue;
      private double fromNormalized;
      private String fromString;

      private int toInt;
      private double toValue;
      private double toNormalized;
      private String toString;

      private UpdateView(LXSnapshot.ParameterView view) {
        this.snapshot = new ComponentReference<LXSnapshot>(view.getSnapshot());
        this.viewPath = view.getViewPath();
        this.label = view.getLabel();
      }

      public UpdateView(LXSnapshot.ParameterView view, boolean toogle) {
        this(view);
        this.toValue = this.toNormalized = (view.getParameterValue() > 0) ? 0 : 1;
      }

      public UpdateView(LXSnapshot.ParameterView view, BoundedParameter replacement) {
        this(view);
        this.toValue = replacement.getValue();
        this.toNormalized = (view.parameter instanceof BoundedParameter bounded) ? bounded.getNormalized(this.toValue) : LXUtils.clamp(this.toValue, 0, 1);
      }

      public UpdateView(LXSnapshot.ParameterView view, DiscreteParameter replacement) {
        this(view);
        this.toInt = replacement.getValuei();
        this.toNormalized = replacement.getNormalized();
        this.discrete = true;
      }

      public UpdateView(LXSnapshot.ParameterView view, StringParameter replacement) {
        this(view);
        this.toString = replacement.getString();
        this.string = true;
      }

      @Override
      public String getDescription() {
        return "Update Snapshot View " + this.label;
      }

      private LXSnapshot.ParameterView getParameterView() {
        return (LXSnapshot.ParameterView) this.snapshot.get().getView(this.viewPath);
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        try {
          final LXSnapshot.ParameterView view = getParameterView();

          this.fromInt = view.getParameterDiscreteValue();
          this.fromString = view.getParameterStringValue();
          this.fromValue = view.getParameterValue();
          this.fromNormalized = view.getParameterNormalizedValue();

          if (this.discrete) {
            view.updateDiscrete(this.toInt, this.toNormalized);
          } else if (this.string) {
            view.updateString(this.toString);
          } else {
            view.updateNormalized(this.toValue, this.toNormalized);
          }
        } catch (Exception x) {
          throw new InvalidCommandException(x);
        }

      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        try {
          final LXSnapshot.ParameterView view = getParameterView();
          if (this.discrete) {
            view.updateDiscrete(this.fromInt, this.fromNormalized);
          } else if (this.string) {
            view.updateString(this.fromString);
          } else {
            view.updateNormalized(this.fromValue, this.fromNormalized);
          }
        } catch (Exception x) {
          throw new InvalidCommandException(x);
        }
      }
    }

    public static class UpdateChannelFaderView extends LXCommand {

      private final ComponentReference<LXSnapshot> snapshot;
      private final String viewPath;

      private double fromValue;
      private final double toValue;

      public UpdateChannelFaderView(LXSnapshot.ChannelFaderView view, BoundedParameter replacement) {
        this.snapshot = new ComponentReference<LXSnapshot>(view.getSnapshot());
        this.viewPath = view.getViewPath();
        this.toValue = replacement.getValue();
      }

      @Override
      public String getDescription() {
        return "Update Snapshot Channel Fader View";
      }

      private LXSnapshot.ChannelFaderView getChannelFaderView() {
        return (LXSnapshot.ChannelFaderView) this.snapshot.get().getView(this.viewPath);
      }

      private void updateValue(double value) throws InvalidCommandException {
        try {
          getChannelFaderView().update(value);
        } catch (Exception x) {
          throw new InvalidCommandException(x);
        }
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        updateValue(this.toValue);
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        updateValue(this.fromValue);
      }
    }

    public static class UpdatePatternView extends LXCommand {

      private final ComponentReference<LXSnapshot> snapshot;
      private final String viewPath;
      private int fromIndex;
      private final int toIndex;

      public UpdatePatternView(LXSnapshot.View view, int patternIndex) {
        this.snapshot = new ComponentReference<LXSnapshot>(view.getSnapshot());
        this.viewPath = view.getViewPath();
        this.toIndex = patternIndex;
      }

      @Override
      public String getDescription() {
        return "Update Snapshot Active Pattern";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        try {
          switch (this.snapshot.get().getView(this.viewPath)) {
          case LXSnapshot.ActivePatternView active -> {
            this.fromIndex = active.getPattern().getIndex();
            active.update(this.toIndex);
          }
          case LXSnapshot.RackPatternView rack -> {
            this.fromIndex = rack.getPattern().getIndex();
            rack.update(this.toIndex);
          }
          default -> throw new InvalidCommandException("UpdatePatternView can only operate ActivePatternView or RackPatternView");
          }
        } catch (Exception x) {
          throw new InvalidCommandException(x);
        }
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        try {
          switch (this.snapshot.get().getView(this.viewPath)) {
          case LXSnapshot.ActivePatternView active -> active.update(this.fromIndex);
          case LXSnapshot.RackPatternView rack -> rack.update(this.fromIndex);
          default -> throw new InvalidCommandException("UpdatePatternView can only operate ActivePatternView or RackPatternView");
          }
        } catch (Exception x) {
          throw new InvalidCommandException(x);
        }
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

    public static class ArrangeFixtures extends Parameter.MultiSetValue {

      public ArrangeFixtures() {
        super("Arrange Fixtures");
      }

    }

    public static class ModifyFixturePositions extends LXCommand {

      private final Map<String, LXCommand.Parameter.SetValue> setValues =
        new HashMap<String, LXCommand.Parameter.SetValue>();

      public ModifyFixturePositions() {
      }

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

      public AddView() {
      }

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
      private boolean enableSnapshot;

      public Add(LXBus bus, int index, boolean enableSnapshot) {
        this(bus, index, null, enableSnapshot);
      }

      public Add(LXBus bus, int index, JsonObject clipObj) {
        this(bus, index, clipObj, false);
      }

      private Add(LXBus bus, int index, JsonObject clipObj, boolean enableSnapshot) {
        this.bus = new ComponentReference<LXBus>(bus);
        this.index = index;
        this.clipObj = clipObj;
        this.enableSnapshot = enableSnapshot;
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
        LXClip clip = (this.clipObj != null) ?
          bus.addClip(this.clipObj, this.index) :
          bus.addClip(this.index, this.enableSnapshot);
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

    public enum Marker {

      LOOP_START("Loop Start"),
      LOOP_BRACE("Loop"),
      LOOP_END("Loop End"),
      PLAY_START("Start"),
      PLAY_END("End");

      public Cursor getCursor(LXClip clip) {
        switch (this) {
        case LOOP_BRACE:
        case LOOP_START:
          return clip.loopStart.cursor;
        case LOOP_END:
          return clip.loopEnd.cursor;
        case PLAY_END:
          return clip.playEnd.cursor;
        case PLAY_START:
          return clip.playStart.cursor;
        }
        return null;
      }

      public void setCursor(LXClip clip, Cursor cursor) {
        switch (this) {
        case LOOP_BRACE:
          clip.setLoopBrace(cursor);
          break;
        case LOOP_END:
          clip.setLoopEnd(cursor);
          break;
        case LOOP_START:
          clip.setLoopStart(cursor);
          break;
        case PLAY_END:
          clip.setPlayEnd(cursor);
          break;
        case PLAY_START:
          clip.setPlayStart(cursor);
          break;
        }
      }

      private final String label;

      private Marker(String label) {
        this.label = label;
      }
    }

    public static class SetMarker extends LXCommand {

      private final ComponentReference<LXClip> clip;
      public final Marker marker;
      private final Cursor fromCursor;
      private Cursor toCursor;

      /**
       * Move clip marker to a new value (in time units)
       */
      public SetMarker(LXClip clip, Marker marker, Cursor toCursor) {
        this.clip = new ComponentReference<LXClip>(clip);
        this.marker = marker;
        this.fromCursor = this.marker.getCursor(clip).clone();
        this.toCursor = toCursor.clone();
      }

      @Override
      public String getDescription() {
        return "Move Clip " + this.marker.label;
      }

      public SetMarker update(Cursor toCursor) {
        this.toCursor.set(toCursor);
        return this;
      }

      @Override
      public void perform(LX lx) {
        this.marker.setCursor(this.clip.get(), this.toCursor);
      }

      @Override
      public void undo(LX lx) {
        LXClip clip = this.clip.get();
        this.marker.setCursor(clip, this.fromCursor);
      }
    }

    public static class MoveMarker extends SetMarker {

      public enum Operation {
        ADD,
        SUBTRACT;

        private Cursor perform(Cursor cursor, Cursor increment) {
          switch (this) {
          case SUBTRACT: return cursor.subtract(increment);
          default: case ADD: return cursor.add(increment);
          }
        }
      }

      /**
       * Move clip marker by a given amount
       */
      public MoveMarker(LXClip clip, Marker marker, Cursor increment) {
        this(clip, marker, increment, Operation.ADD);
      }

      public MoveMarker(LXClip clip, Marker marker, Cursor increment, Operation op) {
        super(clip, marker, op.perform(marker.getCursor(clip), increment));
      }
    }

    public static class MoveLane extends LXCommand {

      private final ComponentReference<LXClipLane<?>> lane;
      private final int fromIndex, toIndex;

      public MoveLane(LXClipLane<?> lane, int index) {
        this.lane = new ComponentReference<>(lane);
        this.fromIndex = lane.getIndex();
        this.toIndex = index;
      }

      @Override
      public String getDescription() {
        return "Move Clip Lane";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        LXClipLane<?> lane = this.lane.get();
        lane.clip.moveClipLane(lane, this.toIndex);
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        LXClipLane<?> lane = this.lane.get();
        lane.clip.moveClipLane(lane, this.fromIndex);
      }
    }

    public static class RemoveClipLane extends RemoveComponent {

      private final ComponentReference<LXClip> clip;
      private final ComponentReference<LXClipLane<?>> parameterLane;
      private final int laneIndex;
      private final JsonObject laneObj;

      public RemoveClipLane(LXClipLane<?> parameterLane) {
        super(parameterLane);
        this.clip = new ComponentReference<>(parameterLane.clip);
        this.parameterLane = new ComponentReference<>(parameterLane);
        this.laneIndex = parameterLane.getIndex();
        this.laneObj = LXSerializable.Utils.toObject(parameterLane.getLX(), parameterLane);
      }

      @Override
      public String getDescription() {
        return "Remove Clip Lane";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        final LXClipLane<?> clipLane = this.parameterLane.get();
        clipLane.clip.removeClipLane(clipLane);
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        final LXClipLane<?> lane = this.clip.get().loadLane(lx, this.laneObj, this.laneIndex);
        if (lane != null) {
          super.undo(lx);
        }
      }

      private void move(LX lx, String fromPath, String toPath) {
        this.clip.get().moveLane(lx, this.laneObj, this.laneIndex, fromPath, toPath);
      }

    }

    public static class Event {

      public static class Remove<T extends LXClipEvent<T>> extends LXCommand {

        private final ComponentReference<LXClipLane<T>> clipLane;
        private final int eventIndex;
        private final JsonObject preState;

        public Remove(LXClipLane<T> clipLane, LXClipEvent<T> clipEvent) {
          this.clipLane = new ComponentReference<>(clipLane);
          this.eventIndex = clipLane.events.indexOf(clipEvent);
          this.preState = LXSerializable.Utils.toObject(clipLane, true);
        }

        @Override
        public String getDescription() {
          return "Delete Event";
        }

        @Override
        public void perform(LX lx) throws InvalidCommandException {
          LXClipLane<T> clipLane = this.clipLane.get();
          try {
            clipLane.removeEvent(clipLane.events.get(this.eventIndex));
          } catch (Exception x) {
            throw new InvalidCommandException(x);
          }
        }

        @Override
        public void undo(LX lx) throws InvalidCommandException {
          this.clipLane.get().load(lx, this.preState);
        }

      }

      public static class RemoveRange extends LXCommand {

        private final ComponentReference<LXClipLane<?>> clipLane;
        private final Cursor from, to;
        private boolean didRemove = false;
        private JsonObject preState = null;

        public RemoveRange(LXClipLane<?> clipLane, Cursor from, Cursor to) {
          this.clipLane = new ComponentReference<>(clipLane);
          this.from = from.clone();
          this.to = to.clone();
        }

        @Override
        public String getDescription() {
          return "Delete Range";
        }

        @Override
        public boolean isIgnored() {
          return !this.didRemove;
        }

        @Override
        public void perform(LX lx) throws InvalidCommandException {
          LXClipLane<?> clipLane = this.clipLane.get();
          this.preState = LXSerializable.Utils.toObject(clipLane, true);
          this.didRemove = clipLane.removeRange(this.from, this.to);
        }

        @Override
        public void undo(LX lx) throws InvalidCommandException {
          this.clipLane.get().load(lx, this.preState);
        }

      }

      public static class SetCursors<T extends LXClipEvent<?>> extends LXCommand {

        public enum Operation {
          NONE,

          // Performed with the left handle
          STRETCH_TO_LEFT,
          SHORTEN_FROM_LEFT,
          CLEAR_FROM_LEFT,
          REVERSE_LEFT_TO_RIGHT,

          // Performed with the right handle
          STRETCH_TO_RIGHT,
          SHORTEN_FROM_RIGHT,
          CLEAR_FROM_RIGHT,
          REVERSE_RIGHT_TO_LEFT,

          // Performed by move-dragging
          MOVE_LEFT,
          MOVE_RIGHT;

          public boolean isClear() {
            return switch (this) {
              case CLEAR_FROM_LEFT, CLEAR_FROM_RIGHT -> true;
              default -> false;
            };
          }

          public boolean isReverse() {
            return switch (this) {
              case REVERSE_LEFT_TO_RIGHT, REVERSE_RIGHT_TO_LEFT -> true;
              default -> false;
            };
          }
        }

        private final ComponentReference<LXClipLane<T>> clipLane;

        private JsonObject preState = null;
        private JsonObject postState = null;
        private final Cursor fromSelectionMin;
        private final Cursor fromSelectionMax;
        private final Cursor toSelectionMin;
        private final Cursor toSelectionMax;

        private final Map<T, Double> fromValues;
        private final Map<T, Cursor> fromCursors;
        private final Map<T, Cursor> toCursors;
        private final Runnable undoHook;
        private ArrayList<T> originalEvents = null;
        private Operation operation;

        public SetCursors(LXClipLane<T> clipLane, Cursor fromSelectionMin, Cursor fromSelectionMax, Map<T, Double> fromValues, Map<T, Cursor> fromCursors, Map<T, Cursor> toCursors) {
          this(clipLane, fromSelectionMin, fromSelectionMax, fromValues, fromCursors, toCursors, null);
        }

        public SetCursors(LXClipLane<T> clipLane, Cursor fromSelectionMin, Cursor fromSelectionMax, Map<T, Double> fromValues, Map<T, Cursor> fromCursors, Map<T, Cursor> toCursors, Runnable undoHook) {
          this.clipLane = new ComponentReference<>(clipLane);
          this.fromValues = fromValues;
          this.fromCursors = fromCursors;
          this.toCursors = toCursors;
          this.undoHook = undoHook;
          this.fromSelectionMin = fromSelectionMin.immutable();
          this.fromSelectionMax = fromSelectionMax.immutable();
          this.toSelectionMin = fromSelectionMin.clone();
          this.toSelectionMax = fromSelectionMax.clone();
        }

        @Override
        public String getDescription() {
          return "Set Event Cursors";
        }

        public SetCursors<T> update(Cursor selectionMin, Cursor selectionMax, Operation operation) {
          this.postState = null;
          this.operation = operation;
          this.toSelectionMin.set(selectionMin);
          this.toSelectionMax.set(selectionMax);
          return this;
        }

        @Override
        public void perform(LX lx) throws InvalidCommandException {
          LXClipLane<T> clipLane = this.clipLane.get();

          if (this.originalEvents == null) {
            // Take a snapshot of the state of clip events at the beginning of this operation, which
            // may have update() called a bunch of times, and we always want to apply changes relative
            // to the state of the list at the beginning of a click-drag operation, for example
            this.originalEvents = new ArrayList<>(clipLane.events);
          }

          if (this.preState == null) {
            this.preState = LXSerializable.Utils.toObject(clipLane, true);
          }
          if (this.postState != null) {
            clipLane.load(lx, this.postState);
          } else {
            clipLane.setEventsCursors(this.originalEvents, this.fromSelectionMin, this.fromSelectionMax, this.toSelectionMin, this.toSelectionMax, this.fromValues, this.fromCursors, this.toCursors, this.operation);
            this.postState = LXSerializable.Utils.toObject(clipLane, true);
          }
        }

        @Override
        public void undo(LX lx) throws InvalidCommandException {
          this.clipLane.get().load(lx, this.preState);
          this.originalEvents = null;
          if (this.undoHook != null) {
            this.undoHook.run();
          }
        }
      }

      public static class Midi {

        public static class RemoveNote extends LXCommand {

          private final ComponentReference<MidiNoteClipLane> clipLane;
          private final int noteOnIndex;
          private final JsonObject preState;

          public RemoveNote(MidiNoteClipLane clipLane, MidiNoteClipEvent midiNote) {
            if (!midiNote.isNoteOn()) {
              throw new IllegalArgumentException("Must pass NOTE ON to Clip.Event.Midi.RemoveNote");
            }
            this.clipLane = new ComponentReference<>(clipLane);
            this.noteOnIndex = clipLane.events.indexOf(midiNote);
            this.preState = LXSerializable.Utils.toObject(clipLane, true);
          }

          @Override
          public String getDescription() {
            return "Delete Note";
          }

          @Override
          public void perform(LX lx) throws InvalidCommandException {
            try {
              final MidiNoteClipLane clipLane = this.clipLane.get();
              clipLane.removeNote(clipLane.events.get(this.noteOnIndex));
            } catch (Exception x) {
              throw new InvalidCommandException(x);
            }
          }

          @Override
          public void undo(LX lx) throws InvalidCommandException {
            this.clipLane.get().load(lx, this.preState);
          }

        }

        public static class SetVelocity extends LXCommand {
          private final ComponentReference<MidiNoteClipLane> clipLane;
          private final int noteOnIndex;
          private final int fromVelocity;
          private int toVelocity;

          public SetVelocity(MidiNoteClipLane clipLane, MidiNoteClipEvent midiNote) {
            if (!midiNote.isNoteOn()) {
              throw new IllegalArgumentException("Must pass NOTE ON to Clip.Event.Midi.SetVelocity");
            }
            this.clipLane = new ComponentReference<>(clipLane);
            this.noteOnIndex = clipLane.events.indexOf(midiNote);
            this.fromVelocity = midiNote.midiNote.getVelocity();
            this.toVelocity = this.fromVelocity;
          }

          @Override
          public String getDescription() {
            return "Change Velocity";
          }

          private void setVelocity(int velocity) throws InvalidCommandException {
            try {
              MidiNoteClipLane clipLane = this.clipLane.get();
              clipLane.events.get(this.noteOnIndex).midiNote.setVelocity(velocity);
              clipLane.onChange.bang();
            } catch (Exception x) {
              throw new InvalidCommandException(x);
            }
          }

          public SetVelocity update(int toVelocity) {
            this.toVelocity = LXUtils.constrain(toVelocity, 1, MidiNote.MAX_VELOCITY);
            return this;
          }

          @Override
          public void perform(LX lx) throws InvalidCommandException {
            setVelocity(this.toVelocity);
          }

          @Override
          public void undo(LX lx) throws InvalidCommandException {
            setVelocity(this.fromVelocity);
          }
        }

        public static class SetChannel extends LXCommand {
          private final ComponentReference<MidiNoteClipLane> clipLane;
          private final int noteOnIndex;
          private final int fromChannel;
          private int toChannel;

          public SetChannel(MidiNoteClipLane clipLane, MidiNoteClipEvent midiNote) {
            if (!midiNote.isNoteOn()) {
              throw new IllegalArgumentException("Must pass NOTE ON to Clip.Event.Midi.SetChannel");
            }
            this.clipLane = new ComponentReference<>(clipLane);
            this.noteOnIndex = clipLane.events.indexOf(midiNote);
            this.fromChannel= midiNote.midiNote.getChannel();
            this.toChannel = this.fromChannel;
          }

          @Override
          public String getDescription() {
            return "Change Channel";
          }

          private void setChannel(int channel) throws InvalidCommandException {
            try {
              MidiNoteClipLane clipLane = this.clipLane.get();
              MidiNoteClipEvent noteOn = clipLane.events.get(this.noteOnIndex);
              noteOn.midiNote.setChannel(channel);
              MidiNoteClipEvent noteOff = noteOn.getNoteOff();
              if (noteOff != null) {
                noteOff.midiNote.setChannel(channel);
              }
              clipLane.onChange.bang();
            } catch (Exception x) {
              throw new InvalidCommandException(x);
            }
          }

          public SetChannel update(int toChannel) {
            this.toChannel = LXUtils.constrain(toChannel, 0, MidiNote.NUM_CHANNELS - 1);
            return this;
          }

          @Override
          public void perform(LX lx) throws InvalidCommandException {
            setChannel(this.toChannel);
          }

          @Override
          public void undo(LX lx) throws InvalidCommandException {
            setChannel(this.fromChannel);
          }
        }

        public static class EditNote extends LXCommand {
          protected final ComponentReference<MidiNoteClipLane> clipLane;
          protected int noteOnIndex = -1;

          protected List<MidiNoteClipEvent> originalEvents;
          protected final Cursor fromStart;
          protected final Cursor fromEnd;
          protected final int fromPitch;
          protected final int fromVelocity;

          private final Cursor toStart;
          private final Cursor toEnd;
          private int toPitch;
          private int toVelocity;

          public EditNote(MidiNoteClipLane clipLane, int pitch, int velocity, Cursor start, Cursor end) {
            this.clipLane = new ComponentReference<>(clipLane);
            this.fromPitch = pitch;
            this.fromVelocity = velocity;
            this.fromStart = start.clone();
            this.fromEnd = end.clone();

            this.toPitch = pitch;
            this.toVelocity = velocity;
            this.toStart = start.clone();
            this.toEnd = end.clone();
          }

          public EditNote(MidiNoteClipLane clipLane, MidiNoteClipEvent noteOn) {
            this(clipLane,
              noteOn.midiNote.getPitch(),
              noteOn.midiNote.getVelocity(),
              noteOn.cursor,
              noteOn.getNoteOff().cursor
            );
            setNote(noteOn);
          }

          protected void setNote(MidiNoteClipEvent midiNote) {
            if (!midiNote.isNoteOn()) {
              throw new IllegalArgumentException("Must pass NOTE ON to Clip.Event.Midi.EditNote");
            }
            if (midiNote.getNoteOff() == null) {
              throw new IllegalArgumentException("EditNote must have valid note-off pair");
            }
            this.noteOnIndex = this.clipLane.get().events.indexOf(midiNote);
          }

          @Override
          public String getDescription() {
            return "Edit Note";
          }

          public EditNote updatePitch(int pitch) {
            this.toPitch = pitch;
            return this;
          }

          public EditNote updateVelocity(int velocity) {
            this.toVelocity = LXUtils.constrain(velocity, 1, MidiNote.MAX_VELOCITY);
            return this;
          }

          public EditNote updateCursor(Cursor start, Cursor end) {
            this.toStart.set(start);
            this.toEnd.set(end);
            return this;
          }

          public EditNote update(int pitch, Cursor start, Cursor end) {
            updatePitch(pitch);
            updateCursor(start, end);
            return this;
          }

          public EditNote update(int pitch, int velocity, Cursor start, Cursor end) {
            updatePitch(pitch);
            updateVelocity(velocity);
            updateCursor(start, end);
            return this;
          }

          @Override
          public void perform(LX lx) throws InvalidCommandException {
            final MidiNoteClipLane clipLane = this.clipLane.get();
            if (this.originalEvents == null) {
              this.originalEvents = new ArrayList<>(clipLane.events);
            }
            Cursor.Operator CursorOp = clipLane.clip.CursorOp();
            boolean cursorMoved =
              !CursorOp.isEqual(this.fromStart, this.toStart) ||
              !CursorOp.isEqual(this.fromEnd, this.toEnd);
            clipLane.editNote(this.originalEvents.get(this.noteOnIndex), this.toPitch, this.toVelocity, this.toStart, this.toEnd, this.originalEvents, true, cursorMoved);
          }

          @Override
          public void undo(LX lx) throws InvalidCommandException {
            if (this.originalEvents == null) {
              throw new InvalidCommandException(new IllegalStateException("Cannot undo Clip.Event.Midi.EditNote that was not performed (or double-undo?)"));
            }
            MidiNoteClipLane clipLane = this.clipLane.get();
            MidiNoteClipEvent noteOn = this.originalEvents.get(this.noteOnIndex);
            clipLane.editNote(noteOn, this.fromPitch, this.fromVelocity, this.fromStart, this.fromEnd, this.originalEvents, false, false);
            this.originalEvents = null;
          }
        }

        public static class InsertNote extends EditNote {

          private MidiNoteClipEvent noteOn = null;

          public InsertNote(MidiNoteClipLane clipLane, int pitch, int velocity, Cursor start, Cursor end) {
            super(clipLane, pitch, velocity, start, end);
          }

          @Override
          public String getDescription() {
            return "Add Note";
          }

          @Override
          public boolean isIgnored() {
            return (this.noteOn == null);
          }

          @Override
          public void perform(LX lx) throws InvalidCommandException {
            if (this.noteOn == null) {
              MidiNoteClipLane clipLane = this.clipLane.get();
              this.noteOn = clipLane.insertNote(this.fromPitch, this.fromVelocity, this.fromStart, this.fromEnd);
              if (this.noteOn != null) {
                setNote(this.noteOn);
                // NOTE: This is crucial in the redo() case, the toValues may have been updated!
                super.perform(lx);
              }
            } else {
              super.perform(lx);
            }
          }

          public MidiNoteClipEvent getNote() {
            return this.noteOn;
          }

          @Override
          public void undo(LX lx) throws InvalidCommandException {
            if (this.noteOn != null) {
              super.undo(lx);
              MidiNoteClipLane clipLane = this.clipLane.get();
              MidiNoteClipEvent event = clipLane.events.get(this.noteOnIndex);
              clipLane.removeNote(event);
              this.noteOn = null;
            }
          }
        }

      }

      public static class Pattern {

        public static class RemoveReferences extends LXCommand {
          private final ComponentReference<PatternClipLane> clipLane;
          private final List<Integer> eventIndices;
          private final JsonObject preState;

          public RemoveReferences(PatternClipLane clipLane, List<Integer> eventIndices) {
            this.clipLane = new ComponentReference<PatternClipLane>(clipLane);
            this.eventIndices = new ArrayList<>(eventIndices);
            this.preState = LXSerializable.Utils.toObject(clipLane, true);
          }

          @Override
          public String getDescription() {
            return "Remove Pattern";
          }

          @Override
          public void perform(LX lx) throws InvalidCommandException {
            PatternClipLane clipLane = this.clipLane.get();
            try {
              clipLane.removeEvents(this.eventIndices);
            } catch (Exception x) {
              throw new InvalidCommandException(x);
            }
          }

          @Override
          public void undo(LX lx) throws InvalidCommandException {
            this.clipLane.get().load(lx, this.preState);
          }

          private void move(LX lx, PatternRack rack) {
            this.clipLane.get().update(lx, this.preState, rack);
          }
        }

        public static class Increment extends LXCommand {

          private final ComponentReference<PatternClipLane> clipLane;
          private final int eventIndex;
          private final int fromPatternIndex;
          private final int increment;

          public Increment(PatternClipLane lane, PatternClipEvent clipEvent, int increment) {
            this.clipLane = new ComponentReference<>(lane);
            this.eventIndex = lane.events.indexOf(clipEvent);
            this.increment = increment;
            this.fromPatternIndex = clipEvent.getPattern().getIndex();
          }

          @Override
          public String getDescription() {
            return "Modify Pattern Event";
          }

          @Override
          public void perform(LX lx) throws InvalidCommandException {
            PatternClipLane lane = this.clipLane.get();
            try {
              PatternClipEvent event = lane.events.get(this.eventIndex);
              LXPattern pattern = event.getPattern();
              int index = pattern.getIndex();
              int newIndex = LXUtils.constrain(index + increment, 0, pattern.getEngine().patterns.size() - 1);
              if (newIndex != index) {
                event.setPattern(pattern.getEngine().patterns.get(newIndex));
              }
            } catch (Exception x) {
              throw new InvalidCommandException(x);
            }

          }

          @Override
          public void undo(LX lx) throws InvalidCommandException {
            PatternClipLane lane = this.clipLane.get();
            try {
              PatternClipEvent event = lane.events.get(this.eventIndex);
              event.setPattern(event.getPattern().getEngine().patterns.get(this.fromPatternIndex));
            } catch (Exception x) {
              throw new InvalidCommandException(x);
            }
          }

        }

        public static class MoveEvent extends LXCommand {

          protected final ComponentReference<PatternClipLane> clipLane;
          protected int eventIndex;
          protected final Cursor fromCursor;
          protected final Cursor toCursor;

          protected int fromPatternIndex;
          protected int toPatternIndex;

          protected MoveEvent(PatternClipLane lane, Cursor cursor) {
            this.clipLane = new ComponentReference<>(lane);
            this.fromCursor = cursor.clone();
            this.toCursor = cursor.clone();
          }

          public MoveEvent(PatternClipLane lane, PatternClipEvent clipEvent) {
            this(lane, clipEvent, clipEvent.cursor);
          }

          public MoveEvent(PatternClipLane lane, PatternClipEvent clipEvent, Cursor cursor) {
            this(lane, clipEvent.cursor);
            setEvent(clipEvent);
            this.toCursor.set(cursor);
            this.toPatternIndex = clipEvent.getPattern().getIndex();
          }

          protected void setEvent(PatternClipEvent clipEvent) {
            this.eventIndex = this.clipLane.get().events.indexOf(clipEvent);
            this.fromPatternIndex = clipEvent.getPattern().getIndex();
          }

          public MoveEvent update(Cursor cursor, int toPatternIndex) {
            this.toCursor.set(cursor);
            this.toPatternIndex = toPatternIndex;
            return this;
          }

          @Override
          public String getDescription() {
            return "Move Pattern Event";
          }

          private void moveTo(Cursor cursor, int patternIndex) throws InvalidCommandException {
            PatternClipLane clipLane = this.clipLane.get();
            try {
              PatternClipEvent clipEvent = clipLane.events.get(this.eventIndex);
              clipLane.moveEvent(clipEvent, cursor);
              clipEvent.setPattern(clipEvent.getPattern().getEngine().patterns.get(patternIndex));
            } catch (Exception x) {
              throw new InvalidCommandException(x);
            }
          }

          @Override
          public void perform(LX lx) throws InvalidCommandException {
            moveTo(this.toCursor, this.toPatternIndex);
          }

          @Override
          public void undo(LX lx) throws InvalidCommandException {
            moveTo(this.fromCursor, this.fromPatternIndex);
          }

        }

        public static class InsertEvent extends MoveEvent {

          private PatternClipEvent event = null;

          public InsertEvent(PatternClipLane clipLane, Cursor cursor, int patternIndex) {
            super(clipLane, cursor);
            this.fromPatternIndex = patternIndex;
            this.toPatternIndex = patternIndex;
          }

          @Override
          public String getDescription() {
            return "Add Pattern Change";
          }

          @Override
          public void perform(LX lx) throws InvalidCommandException {
            if (this.event == null) {
              PatternClipLane clipLane = this.clipLane.get();
              this.event = new PatternClipEvent(clipLane, this.fromCursor, this.fromPatternIndex);
              clipLane.insertEvent(this.event);
              setEvent(this.event);
            }
            super.perform(lx);
          }

          public PatternClipEvent getEvent() {
            return this.event;
          }

          @Override
          public void undo(LX lx) throws InvalidCommandException {
            PatternClipLane clipLane = this.clipLane.get();
            clipLane.removeEvent(clipLane.events.get(this.eventIndex));
            this.event = null;
          }

        }
      }

      public static class Parameter {

        public static class InsertEvent extends LXCommand {

          private final ComponentReference<ParameterClipLane> clipLane;
          private final Cursor cursor;
          private final double normalized;
          private int undoIndex;
          private ParameterClipEvent insertEvent;

          public InsertEvent(ParameterClipLane lane, Cursor cursor, double normalized) {
            this.clipLane = new ComponentReference<>(lane);
            this.cursor = cursor.clone();
            this.normalized = normalized;
          }

          @Override
          public String getDescription() {
            return "Insert Clip Event";
          }

          @Override
          public void perform(LX lx) throws InvalidCommandException {
            ParameterClipLane clipLane = this.clipLane.get();
            this.insertEvent = clipLane.insertEvent(this.cursor, this.normalized);
            this.undoIndex = clipLane.events.indexOf(this.insertEvent);
          }

          public ParameterClipEvent getEvent() {
            return this.insertEvent;
          }

          @Override
          public void undo(LX lx) throws InvalidCommandException {
            ParameterClipLane clipLane = this.clipLane.get();
            try {
              clipLane.removeEvent(clipLane.events.get(this.undoIndex));
            } catch (Exception x) {
              throw new InvalidCommandException(x);
            }
          }

        }

        public static class MoveEvent extends LXCommand {

          private final ComponentReference<ParameterClipLane> clipLane;
          private final int eventIndex;
          private final Cursor fromCursor;
          private Cursor toCursor;
          private final double fromNormalized;
          private double toNormalized;

          public MoveEvent(ParameterClipLane lane, ParameterClipEvent clipEvent) {
            this.clipLane = new ComponentReference<>(lane);
            this.fromCursor = clipEvent.cursor.clone();
            this.toCursor = clipEvent.cursor.clone();
            this.toNormalized = this.fromNormalized = clipEvent.getNormalized();
            this.eventIndex = lane.events.indexOf(clipEvent);
          }

          public MoveEvent update(Cursor cursor, double normalized) {
            this.toCursor.set(cursor);
            this.toNormalized = normalized;
            return this;
          }

          @Override
          public String getDescription() {
            return "Move Clip Event";
          }

          private void moveTo(Cursor cursor, double normalized) throws InvalidCommandException {
            ParameterClipLane clipLane = this.clipLane.get();
            try {
              ParameterClipEvent clipEvent = clipLane.events.get(this.eventIndex);
              clipEvent.setNormalized(normalized);
              clipLane.moveEvent(clipEvent, cursor);
            } catch (Exception x) {
              throw new InvalidCommandException(x);
            }
          }

          @Override
          public void perform(LX lx) throws InvalidCommandException {
            moveTo(this.toCursor, this.toNormalized);

          }

          @Override
          public void undo(LX lx) throws InvalidCommandException {
            moveTo(this.fromCursor, this.fromNormalized);
          }

        }

        public static class SetValues extends LXCommand {

          private final ComponentReference<ParameterClipLane> clipLane;
          private final Map<ParameterClipEvent, Double> toValues;
          private JsonObject preState = null;
          private JsonObject postState = null;

          public SetValues(ParameterClipLane clipLane, Map<ParameterClipEvent, Double> toValues) {
            this.clipLane = new ComponentReference<>(clipLane);
            this.toValues = toValues;
          }

          @Override
          public String getDescription() {
            return "Set Event Values";
          }

          public SetValues update() {
            this.postState = null;
            return this;
          }

          @Override
          public void perform(LX lx) throws InvalidCommandException {
            ParameterClipLane clipLane = this.clipLane.get();
            if (this.preState == null) {
              this.preState = LXSerializable.Utils.toObject(clipLane, true);
            }
            if (this.postState != null) {
              clipLane.load(lx, this.postState);
            } else {
              clipLane.setEventsNormalized(this.toValues);
              this.postState = LXSerializable.Utils.toObject(clipLane, true);
            }
          }

          @Override
          public void undo(LX lx) throws InvalidCommandException {
            this.clipLane.get().load(lx, this.preState);
          }
        }
      }
    }
  }

  public static class Osc {

    public static class AddInput extends LXCommand {

      private LXOscConnection.Input input;

      public AddInput() {
      }

      @Override
      public String getDescription() {
        return "Add OSC input";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        this.input = lx.engine.osc.addInput();
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        lx.engine.osc.removeInput(this.input);
      }
    }

    public static class RemoveInput extends RemoveComponent {

      private final ComponentReference<LXOscConnection.Input> input;
      private final JsonObject inputObj;

      public RemoveInput(LXOscConnection.Input input) {
        super(input);
        this.input = new ComponentReference<LXOscConnection.Input>(input);
        this.inputObj = LXSerializable.Utils.toObject(input);
      }

      @Override
      public String getDescription() {
        return "Delete OSC input";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        lx.engine.osc.removeInput(this.input.get());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        lx.engine.osc.addInput(this.inputObj, -1);
        super.undo(lx);
      }
    }

    public static class AddOutput extends LXCommand {

      private LXOscConnection.Output output;

      public AddOutput() {
      }

      @Override
      public String getDescription() {
        return "Add OSC output";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        this.output = lx.engine.osc.addOutput();
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        lx.engine.osc.removeOutput(this.output);
      }
    }

    public static class RemoveOutput extends RemoveComponent {

      private final ComponentReference<LXOscConnection.Output> output;
      private final JsonObject outputObj;

      public RemoveOutput(LXOscConnection.Output output) {
        super(output);
        this.output = new ComponentReference<LXOscConnection.Output>(output);
        this.outputObj = LXSerializable.Utils.toObject(output);
      }

      @Override
      public String getDescription() {
        return "Delete OSC output";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        lx.engine.osc.removeOutput(this.output.get());
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        lx.engine.osc.addOutput(this.outputObj, -1);
        super.undo(lx);
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

      private LXMidiMapping mapping;
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
        lx.engine.midi.addMapping(this.mapping = LXMidiMapping.create(lx, this.mappingObj));
      }

      private void move(LX lx, String fromPath, String toPath)  throws InvalidCommandException {
        lx.engine.midi.addMapping(LXMidiMapping.move(lx, this.mappingObj, fromPath, toPath));
      }
    }

    public static class AddTemplate extends LXCommand {

      private ComponentReference<LXMidiTemplate> template = null;
      private final Class<? extends LXMidiTemplate> templateClass;
      private JsonObject templateObj = null;

      public AddTemplate(Class<? extends LXMidiTemplate> templateClass) {
        this.templateClass = templateClass;
      }

      @Override
      public String getDescription() {
        return "Add MIDI Template";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        try {
          final LXMidiTemplate template = lx.instantiateComponent(this.templateClass, LXMidiTemplate.class);
          this.template = new ComponentReference<LXMidiTemplate>(template);
          if (this.templateObj != null) {
            template.load(lx, this.templateObj);
          } else {
            template.initializeDefaultIO();
          }
          lx.engine.midi.addTemplate(template);
        } catch (LX.InstantiationException x) {
          throw new InvalidCommandException(x);
        }
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        if (this.template == null) {
          throw new IllegalStateException("Template was not successfully added, cannot undo");
        }
        final LXMidiTemplate template = this.template.get();
        this.templateObj = LXSerializable.Utils.toObject(template);
        lx.engine.midi.removeTemplate(template);
      }

    }

    public static class RemoveTemplate extends RemoveComponent {

      private final ComponentReference<LXMidiTemplate> midiTemplate;
      private JsonObject templateObj;
      private int fromIndex;

      public RemoveTemplate(LXMidiTemplate midiTemplate) {
        super(midiTemplate);
        this.midiTemplate = new ComponentReference<LXMidiTemplate>(midiTemplate);
      }

      @Override
      public String getDescription() {
        return "Delete MIDI Template";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        final LXMidiTemplate midiTemplate = this.midiTemplate.get();
        this.fromIndex = midiTemplate.getIndex();
        this.templateObj = LXSerializable.Utils.toObject(midiTemplate);
        lx.engine.midi.removeTemplate(midiTemplate);
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        try {
          final LXMidiTemplate template = lx.instantiateComponent(this.templateObj.get(LXComponent.KEY_CLASS).getAsString(), LXMidiTemplate.class);
          template.load(lx, this.templateObj);
          lx.engine.midi.addTemplate(template);
          lx.engine.midi.moveTemplate(template, this.fromIndex);
          super.undo(lx);
        } catch (LX.InstantiationException x) {
          throw new InvalidCommandException(x);
        }
      }
    }

    public static class MoveTemplate extends LXCommand {

      private final ComponentReference<LXMidiTemplate> midiTemplate;
      private final int fromIndex;
      private final int toIndex;

      public MoveTemplate(LXMidiTemplate midiTemplate, int toIndex) {
        this.midiTemplate = new ComponentReference<LXMidiTemplate>(midiTemplate);
        this.fromIndex = midiTemplate.getIndex();
        this.toIndex = toIndex;
      }

      @Override
      public String getDescription() {
        return "Move MIDI Template";
      }

      @Override
      public void perform(LX lx) throws InvalidCommandException {
        lx.engine.midi.moveTemplate(this.midiTemplate.get(), this.toIndex);
      }

      @Override
      public void undo(LX lx) throws InvalidCommandException {
        lx.engine.midi.moveTemplate(this.midiTemplate.get(), this.fromIndex);
      }

    }
  }
}
