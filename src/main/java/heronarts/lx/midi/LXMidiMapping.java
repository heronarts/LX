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

package heronarts.lx.midi;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXPath;
import heronarts.lx.LXSerializable;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.utils.LXUtils;

public abstract class LXMidiMapping implements LXSerializable {

  public enum Type {
    NOTE,
    CONTROL_CHANGE
  };

  public final int channel;

  public final Type type;

  public final LXNormalizedParameter parameter;

  public final boolean isDiscrete;
  public final DiscreteParameter discreteParameter;

  public final boolean isBoolean;
  public final BooleanParameter booleanParameter;

  private static int getChannel(JsonObject obj) {
    return obj.get(KEY_CHANNEL).getAsInt();
  }

  private static LXNormalizedParameter getParameter(LX lx, JsonObject obj) {
    if (obj.has(LXComponent.KEY_PATH)) {
      LXPath parameter = LXPath.get(lx, obj.get(LXComponent.KEY_PATH).getAsString());
      if (parameter instanceof LXNormalizedParameter) {
        return (LXNormalizedParameter) parameter;
      }
    }
    return (LXNormalizedParameter) lx
      .getProjectComponent(obj.get(LXComponent.KEY_COMPONENT_ID).getAsInt())
      .getParameter(obj.get(LXComponent.KEY_PARAMETER_PATH).getAsString());
  }

  protected LXMidiMapping(LX lx, int channel, Type type, LXNormalizedParameter parameter) {
    if (parameter == null) {
      throw new IllegalArgumentException("Cannot map null parameter");
    }
    if (parameter.getParent() == null) {
      throw new IllegalStateException("Cannot map parameter with no component: " + parameter);
    }
    this.channel = channel;
    this.type = type;
    this.parameter = parameter;
    this.isBoolean = parameter instanceof BooleanParameter;
    this.booleanParameter = this.isBoolean ? (BooleanParameter) parameter : null;
    this.isDiscrete = parameter instanceof DiscreteParameter;
    this.discreteParameter = this.isDiscrete ? (DiscreteParameter) parameter : null;
  }

  protected LXMidiMapping(LX lx, JsonObject object, Type type) {
    this(
      lx,
      object.get(KEY_CHANNEL).getAsInt(),
      type,
      getParameter(lx, object)
    );
  }

  static boolean isValidMessageType(LXShortMessage message) {
    return (message instanceof MidiNote) || (message instanceof MidiControlChange);
  }

  public static LXMidiMapping create(LX lx, LXShortMessage message, LXNormalizedParameter parameter) {
    if (message instanceof MidiNote) {
      return new Note(lx, (MidiNote) message, parameter);
    } else if (message instanceof MidiControlChange) {
      return new ControlChange(lx, (MidiControlChange) message, parameter);
    }
    throw new IllegalArgumentException("Not a valid message type for a MIDI mapping: " + message);
  }

  public static LXMidiMapping create(LX lx, JsonObject object) {
    Type type = Type.valueOf(object.get(KEY_TYPE).getAsString());
    switch (type) {
    case NOTE: return new Note(lx, object);
    case CONTROL_CHANGE: return new ControlChange(lx, object);
    }
    throw new IllegalArgumentException("Not a valid MidiMapping type: " + object);
  }

  abstract boolean matches(LXShortMessage message);
  abstract void apply(LXShortMessage message);

  public abstract String getDescription();

  protected void setValue(boolean value) {
    if (this.parameter instanceof BooleanParameter) {
      ((BooleanParameter) this.parameter).setValue(value);
    } else {
      this.parameter.setNormalized(value ? 1 : 0);
    }
  }

  private static final String KEY_CHANNEL = "channel";
  private static final String KEY_TYPE = "type";

  @Override
  public void save(LX lx, JsonObject object) {
    object.addProperty(KEY_CHANNEL, this.channel);
    object.addProperty(KEY_TYPE, this.type.name());
    object.addProperty(LXComponent.KEY_PATH, this.parameter.getCanonicalPath());

    // Path should take precedence, but keeping this around just in case.
    object.addProperty(LXComponent.KEY_COMPONENT_ID, this.parameter.getParent().getId());
    object.addProperty(LXComponent.KEY_PARAMETER_PATH, this.parameter.getPath());
  }

  @Override
  public void load(LX lx, JsonObject object) {
    throw new UnsupportedOperationException("Use LXMidiMapping.create() to load from JsonObject");
  }

  protected static DiscreteParameter makeDiscreteRangeParameter(DiscreteParameter parameter, boolean on, String label, String description) {
    final int min = parameter.getMinValue();
    final int max = parameter.getMaxValue();

    return (DiscreteParameter)
      new DiscreteParameter(label, on ? max : min, min, max+1)
      .setUnits(parameter.getUnits())
      .setOptions(parameter.getOptions(), false)
      .setFormatter(v -> {
        int index = (int) v - min;
        String[] options = parameter.getOptions();
        return (options != null && index < options.length) ? options[index] : parameter.getFormatter().format(v);
      })
      .setDescription(description)
      .setMappable(false);
  }

  protected static BoundedParameter makeBoundedRangeParameter(LXNormalizedParameter parameter, boolean on, String label, String description) {
    double v0 = 0, v1 = 1;
    BoundedParameter.NormalizationCurve normalizationCurve = BoundedParameter.NormalizationCurve.NORMAL;
    if (parameter instanceof BoundedParameter) {
      BoundedParameter bounded = (BoundedParameter) parameter;
      v0 = bounded.range.v0;
      v1 = bounded.range.v1;
    }
    return (BoundedParameter)
      new BoundedParameter(label, on ? v1 : v0, v0, v1)
      .setNormalizationCurve(normalizationCurve)
      .setExponent(parameter.getExponent())
      .setPolarity(parameter.getPolarity())
      .setUnits(parameter.getUnits())
      .setFormatter(parameter.getFormatter())
      .setDescription(parameter.getDescription())
      .setMappable(false);
  }

  public static class Note extends LXMidiMapping {

    public enum Mode {
      TOGGLE("Toggle"),
      MOMENTARY("Momentary"),
      ON("On"),
      OFF("Off");

      public final String label;

      private Mode(String label) {
        this.label = label;
      }

      @Override
      public String toString() {
        return this.label;
      }

      public static Mode getDefault(BooleanParameter parameter) {
        switch (parameter.getMode()) {
        case MOMENTARY:
          return MOMENTARY;
        default:
        case TOGGLE:
          return TOGGLE;
        }
      }
    }

    public enum DiscreteMode {
      INCREMENT("Increment"),
      DECREMENT("Decrement"),
      FIXED("Fixed"),
      RANDOM("Random");

      public final String label;

      private DiscreteMode(String label) {
        this.label = label;
      }

      @Override
      public String toString() {
        return this.label;
      }
    }

    public final int pitch;

    public final EnumParameter<Mode> mode =
      new EnumParameter<Mode>("Mode", Mode.TOGGLE)
      .setDescription("How to process note on and off events");

    public final EnumParameter<DiscreteMode> discreteMode =
      new EnumParameter<DiscreteMode>("Discrete Mode", DiscreteMode.INCREMENT)
      .setDescription("How to process note events for a parameter with a fixed set of options");

    public final BoundedParameter offValue;
    public final BoundedParameter onValue;

    public final DiscreteParameter fixedValue;

    private LXParameter.Collection parameters = new LXParameter.Collection();

    private boolean toggleState = false;

    private Note(LX lx, int channel, int pitch, LXNormalizedParameter parameter) {
      super(lx, channel, Type.NOTE, parameter);
      this.pitch = pitch;
      if (this.isDiscrete) {
        this.fixedValue = makeDiscreteRangeParameter(this.discreteParameter, false, "Fixed", "Value set for a fixed note trigger");
        this.parameters.add("discreteMode", this.discreteMode);
        this.parameters.add("fixedValue", this.fixedValue);
        this.onValue = this.offValue = null;
      } else if (this.isBoolean) {
        this.parameters.add("mode", this.mode);
        this.mode.setValue(Mode.getDefault(this.booleanParameter));
        this.fixedValue = null;
        this.onValue = this.offValue = null;
      } else {
        this.fixedValue = null;
        this.offValue = makeBoundedRangeParameter(parameter, false, "Off", "Value when the MIDI note trigger is Off");
        this.onValue = makeBoundedRangeParameter(parameter, true, "On", "Value when the MIDI note trigger is On");
        this.parameters.add("mode", this.mode);
        this.parameters.add("offValue", this.offValue);
        this.parameters.add("onValue", this.onValue);
      }
    }

    private Note(LX lx, MidiNote note, LXNormalizedParameter parameter) {
      this(lx, note.getChannel(), note.getPitch(), parameter);
    }

    private Note(LX lx, JsonObject object) {
      this(lx, getChannel(object), object.get(KEY_PITCH).getAsInt(), getParameter(lx, object));
      LXSerializable.Utils.loadParameters(object, this.parameters);
    }

    @Override
    boolean matches(LXShortMessage message) {
      if (message instanceof MidiNote) {
        final MidiNote note = (MidiNote) message;
        return
          (note.getChannel() == this.channel) &&
          (note.getPitch() == this.pitch);
      }
      return false;
    }

    @Override
    void apply(LXShortMessage message) {
      final MidiNote note = (MidiNote) message;
      final boolean noteOn = note.isNoteOn();

      if (this.parameter instanceof BooleanParameter) {
        final BooleanParameter bool = (BooleanParameter) this.parameter;
        if (noteOn) {
          switch (this.mode.getEnum()) {
          case MOMENTARY:
          case ON:
            bool.setValue(true);
            break;
          case OFF:
            bool.setValue(false);
            break;
          default:
          case TOGGLE:
            bool.toggle();
            break;
          }
        } else if (this.mode.getEnum() == Mode.MOMENTARY) {
          bool.setValue(false);
        }
      } else if (this.parameter instanceof DiscreteParameter) {
        final DiscreteParameter discrete = ((DiscreteParameter) this.parameter);
        if (noteOn) {
          switch (this.discreteMode.getEnum()) {
          case DECREMENT:
            discrete.decrement(true);
            break;
          case RANDOM:
            discrete.setNormalized(Math.random());
            break;
          case FIXED:
            discrete.setIndex(this.fixedValue.getIndex());
            break;
          default:
          case INCREMENT:
            discrete.increment(true);
            break;
          }
        }
      } else {
        if (noteOn) {
          this.toggleState = !this.toggleState;
        }
        switch (this.mode.getEnum()) {
        case MOMENTARY:
          this.parameter.setNormalized(noteOn ? this.onValue.getValue() : this.offValue.getValue());
          break;
        case TOGGLE:
          if (noteOn) {
            this.parameter.setNormalized(this.toggleState ? this.onValue.getValue() : this.offValue.getValue());
          }
          break;
        case ON:
          if (noteOn) {
            this.parameter.setNormalized(this.onValue.getValue());
          }
          break;
        case OFF:
          if (noteOn) {
            this.parameter.setNormalized(this.offValue.getValue());
          }
          break;
        }
      }

    }

    @Override
    public String getDescription() {
      return MidiNote.getPitchString(this.pitch);
    }

    private static final String KEY_PITCH = "pitch";

    @Override
    public void save(LX lx, JsonObject object) {
      super.save(lx, object);
      object.addProperty(KEY_PITCH, this.pitch);
      LXSerializable.Utils.saveParameters(object, this.parameters);
    }
  }

  public static class ControlChange extends LXMidiMapping {

    public final int cc;

    public final DiscreteParameter minDiscrete;
    public final DiscreteParameter maxDiscrete;
    public final BoundedParameter minValue;
    public final BoundedParameter maxValue;

    private LXParameter.Collection parameters = new LXParameter.Collection();

    private ControlChange(LX lx, int channel, int cc, LXNormalizedParameter parameter) {
      super(lx, channel, Type.CONTROL_CHANGE, parameter);
      this.cc = cc;

      if (this.isDiscrete) {
        this.minValue = this.maxValue = null;
        this.minDiscrete = makeDiscreteRangeParameter(this.discreteParameter, false, "Min", "Minimum mapped value");
        this.maxDiscrete = makeDiscreteRangeParameter(this.discreteParameter, true, "Max", "Maximum mapped value");
        this.parameters.add("minDiscrete", this.minDiscrete);
        this.parameters.add("maxDiscrete", this.maxDiscrete);
      } else if (this.isBoolean) {
        this.minDiscrete = this.maxDiscrete = null;
        this.minValue =
          new BoundedParameter("Min", .5)
          .setUnits(BoundedParameter.Units.PERCENT_NORMALIZED)
          .setDescription("Minimum value for the parameter to be switched on");
        this.maxValue =
          new BoundedParameter("Max", 1)
          .setUnits(BoundedParameter.Units.PERCENT_NORMALIZED)
          .setDescription("Maximum value for the parameter to be switched on");
        this.parameters.add("minValue", this.minValue);
        this.parameters.add("maxValue", this.maxValue);
      } else {
        this.minDiscrete = this.maxDiscrete = null;
        this.minValue = makeBoundedRangeParameter(parameter, false, "Min", "Minimum mapped value");
        this.maxValue = makeBoundedRangeParameter(parameter, true, "Max", "Maximum mapped value");
        this.parameters.add("minValue", this.minValue);
        this.parameters.add("maxValue", this.maxValue);
      }
    }

    private ControlChange(LX lx, MidiControlChange controlChange, LXNormalizedParameter parameter) {
      this(lx, controlChange.getChannel(), controlChange.getCC(), parameter);
    }

    private ControlChange(LX lx, JsonObject object) {
      this(lx, getChannel(object), object.get(KEY_CC).getAsInt(), getParameter(lx, object));
      LXSerializable.Utils.loadParameters(object, this.parameters);
    }

    @Override
    boolean matches(LXShortMessage message) {
      if (message instanceof MidiControlChange) {
        final MidiControlChange controlChange = (MidiControlChange) message;
        return
          (controlChange.getChannel() == this.channel) &&
          (controlChange.getCC() == this.cc);
      }
      return false;
    }

    @Override
    void apply(LXShortMessage message) {
      final MidiControlChange controlChange = (MidiControlChange) message;
      double normalized = controlChange.getNormalized();
      if (this.isDiscrete) {
        int min = this.minDiscrete.getValuei();
        int max = this.maxDiscrete.getValuei();
        if (min > max) {
          int tmp = min;
          min = max;
          max = tmp;
          normalized = 1-normalized;
        }
        this.discreteParameter.setValue(LXUtils.min(min + (max - min + 1) * normalized, max));
      } else if (this.isBoolean) {
        this.booleanParameter.setValue(
          (normalized >= this.minValue.getNormalized()) &&
          (normalized <= this.maxValue.getNormalized())
        );
      } else {
        this.parameter.setNormalized(LXUtils.lerp(
          this.minValue.getNormalized(),
          this.maxValue.getNormalized(),
          normalized
        ));
      }
    }

    @Override
    public String getDescription() {
      return "CC" + this.cc;
    }

    private static final String KEY_CC = "cc";

    @Override
    public void save(LX lx, JsonObject object) {
      super.save(lx, object);
      object.addProperty(KEY_CC, this.cc);
      LXSerializable.Utils.saveParameters(object, this.parameters);
    }
  }

}
