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

package heronarts.lx.modulation;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameterListener;

public class LXTriggerModulation extends LXParameterModulation {

  public enum ToggleMode {
    DIRECT("Direct"),
    INVERT("Invert"),
    ALWAYS_ON("Any Change → On"),
    ALWAYS_OFF("Any Change → Off"),
    ALWAYS_TOGGLE("Any Change → Toggle"),
    ON_ON("On → On"),
    ON_OFF("On → Off"),
    ON_TOGGLE("On → Toggle"),
    OFF_ON("Off → On"),
    OFF_OFF("Off → Off"),
    OFF_TOGGLE("Off → Toggle");

    public final String label;

    ToggleMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }

    private void onToggle(BooleanParameter source, BooleanParameter target) {
      switch (this) {
      case DIRECT:
        target.setValue(source.isOn());
        break;
      case INVERT:
        target.setValue(!source.isOn());
        break;
      case ALWAYS_ON:
        target.setValue(true);
        break;
      case ALWAYS_OFF:
        target.setValue(false);
        break;
      case ALWAYS_TOGGLE:
        target.toggle();
        break;
      case ON_ON:
        if (source.isOn()) {
          target.setValue(true);
        }
        break;
      case ON_OFF:
        if (source.isOn()) {
          target.setValue(false);
        }
        break;
      case ON_TOGGLE:
        if (source.isOn()) {
          target.toggle();
        }
        break;
      case OFF_ON:
        if (!source.isOn()) {
          target.setValue(true);
        }
        break;
      case OFF_OFF:
        if (!source.isOn()) {
          target.setValue(false);
        }
        break;
      case OFF_TOGGLE:
        if (!source.isOn()) {
          target.toggle();
        }
        break;
      }
    }
  };

  public enum MomentaryToggleMode {
    TOGGLE("Trigger → Toggle"),
    ON("Trigger → On"),
    OFF("Trigger → Off"),
    DIRECT("Trigger → Direct"),
    INVERT("Trigger → Invert");

    public final String label;

    MomentaryToggleMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }

    private void onMomentary(BooleanParameter target) {
      switch (this) {
      case TOGGLE -> target.toggle();
      case ON, DIRECT -> target.setValue(true);
      case OFF, INVERT -> target.setValue(false);
      }
    }

    private void onRelease(BooleanParameter target) {
      switch (this) {
      case DIRECT -> target.setValue(false);
      case INVERT -> target.setValue(true);
      default -> {}
      }
    }
  };

  public enum ToggleMomentaryMode {
    ON("On → Trigger"),
    OFF("Off → Trigger"),
    ALWAYS("Any Change → Trigger"),
    DIRECT("Direct"),
    INVERT("Invert");

    public final String label;

    ToggleMomentaryMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }

    private boolean shouldTrigger(BooleanParameter source) {
      return switch (this) {
      case ALWAYS -> true;
      case ON, DIRECT -> source.isOn();
      case OFF, INVERT -> !source.isOn();
      };
    }

    private boolean shouldClear(BooleanParameter source) {
      return switch (this) {
      case DIRECT -> !source.isOn();
      case INVERT -> source.isOn();
      default -> false;
      };
    }
  };

  public final BooleanParameter source;
  public final BooleanParameter target;

  private final BooleanParameter triggerSource;

  private final boolean sourceMomentary;
  private final boolean targetMomentary;

  public final EnumParameter<ToggleMode> toggleMode =
    new EnumParameter<ToggleMode>("Toggle Mode", ToggleMode.DIRECT)
    .setDescription("How toggle to toggle actions are mapped");

  public final EnumParameter<MomentaryToggleMode> momentaryToggleMode =
    new EnumParameter<MomentaryToggleMode>("Momentary → Toggle Mode", MomentaryToggleMode.TOGGLE)
    .setDescription("How momentary to toggle actions are mapped");

  public final EnumParameter<ToggleMomentaryMode> toggleMomentaryMode =
    new EnumParameter<ToggleMomentaryMode>("Toggle → Momentary Mode", ToggleMomentaryMode.ON)
    .setDescription("How toggle to momentary actions are mapped");

  private final LXParameterListener sourceListener;

  public LXTriggerModulation(LX lx, LXModulationEngine scope, JsonObject obj) throws ModulationException {
    this(
      scope,
      (BooleanParameter) getParameter(lx, scope, obj.getAsJsonObject(KEY_SOURCE)),
      (BooleanParameter) getParameter(lx, scope, obj.getAsJsonObject(KEY_TARGET))
    );
  }

  public LXTriggerModulation(LXModulationEngine scope, BooleanParameter source, BooleanParameter target) throws ModulationException {
    super(scope, source, target);
    this.source = source;
    this.target = target;

    this.sourceMomentary = (source.getMode() == BooleanParameter.Mode.MOMENTARY);
    this.targetMomentary = (target.getMode() == BooleanParameter.Mode.MOMENTARY);

    setParent(scope);

    addParameter("toggleMode", this.toggleMode);
    addParameter("momentaryToggleMode", this.momentaryToggleMode);
    addParameter("toggleMomentaryMode", this.toggleMomentaryMode);

    this.triggerSource = this.source.getTriggerSource();

    this.triggerSource.addListener(this.sourceListener = p -> {
      if (!this.enabled.isOn()) {
        return;
      }
      if (this.sourceMomentary) {
        if (this.targetMomentary) {
          // Momentary -> Momentary
          this.target.setValue(this.source.isOn());
        } else {
          // Momentary -> Toggle
          if (this.source.isOn()) {
            this.momentaryToggleMode.getEnum().onMomentary(this.target);
          } else {
            this.momentaryToggleMode.getEnum().onRelease(this.target);
          }
        }
      } else {
        if (this.targetMomentary) {
          // Toggle -> Momentary
          final ToggleMomentaryMode momentary = this.toggleMomentaryMode.getEnum();
          if (momentary.shouldTrigger(this.source)) {
            this.target.setValue(true);
          } else if (momentary.shouldClear(this.source)) {
            this.target.setValue(false);
          }
        } else {
          // Toggle -> Toggle
          this.toggleMode.getEnum().onToggle(this.source, this.target);
        }
      }
    });

  }

  public EnumParameter<?> getModeParameter() {
    if (this.sourceMomentary) {
      if (this.targetMomentary) {
        return null;
      } else {
        return this.momentaryToggleMode;
      }
    } else {
      if (this.targetMomentary) {
        return this.toggleMomentaryMode;
      } else {
        return this.toggleMode;
      }
    }
  }

  @Override
  public String getPath() {
    return "trigger/" + (this.index + 1);
  }

  @Override
  public void dispose() {
    this.triggerSource.removeListener(this.sourceListener);
    super.dispose();
  }
}
