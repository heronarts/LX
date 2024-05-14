/**
 * Copyright 2024- Justin Belcher, Mark C. Slee, Heron Arts LLC
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
 * @author Justin K. Belcher <justin@jkb.studio>
 */

package heronarts.lx.midi.surface;

import heronarts.lx.midi.MidiControlChange;
import heronarts.lx.parameter.LXListenableNormalizedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.utils.LXUtils;

/**
 * An adapter for MIDI control messages being directed to a normalized
 * parameter with varying modes.
 */
public class LXMidiParameterControl {

  /**
   * How incoming control messages influence the value of the target
   * parameter.
   */
  public enum Mode {
    /**
     * Always set the target value directly, may cause jumps
     */
    DIRECT("Direct"),

    /**
     * Influence the target value only once the control has
     * passed over its initial value
     */
    PICKUP("Pickup"),

    /**
     * Scale the target value in the direction of the control
     * change to eventually average onto the same value
     */
    SCALE("Scale");

    public final String label;

    private Mode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  }

  private Mode mode = Mode.DIRECT;

  private LXListenableNormalizedParameter target = null;

  private int lastControlValue = -1;
  private boolean isPickedUp = false;
  private int pickupDirection = 0;

  private boolean internalUpdate = false;

  private final LXParameterListener listener = this::onTargetChanged;

  public LXMidiParameterControl() {
    this(null);
  }

  public LXMidiParameterControl(LXListenableNormalizedParameter target) {
    setTarget(target);
  }

  public LXMidiParameterControl setMode(Mode mode) {
    this.mode = mode;
    if (this.mode == Mode.PICKUP) {
      resetPickup();
    }
    return this;
  }

  public void setValue(MidiControlChange cc) {
    this.internalUpdate = true;
    final int ccValue = cc.getValue();
    if (this.target != null) {
      switch (this.mode) {
      case DIRECT:
        this.target.setNormalized(cc.getNormalized());
        break;
      case PICKUP:
        if (!this.isPickedUp) {
          final int newPickupDirection = Integer.compare(getTargetMidiValue(), ccValue);
          if (this.lastControlValue < 0) {
            // This is the first time the control's been moved... pick up
            // only if it's equal, otherwise now we know what side to cross
            // over from
            this.pickupDirection = newPickupDirection;
            this.isPickedUp = (newPickupDirection == 0);
          } else {
            // We pick up by matching or crossing over
            this.isPickedUp =
              (newPickupDirection == 0) ||
              (newPickupDirection != this.pickupDirection);
          }
        }
        if (this.isPickedUp) {
          this.target.setNormalized(cc.getNormalized());
        }
        break;
      case SCALE:
        if (this.lastControlValue >= 0) {
          // We're moving positively or negatively - scale the existing value towards the end
          // in the direction of motion by the amount the CC input has moved in that direction
          // as a function of its remaining space.
          //
          // If the real value has further to go, it moves faster, if it has less far to go then
          // it moves slower - but it always moves in the same direction of the knob turn (e.g.
          // there's no turning a MIDI knob down and making a real value bigger)
          final double targetNormalized = this.target.getBaseNormalized();
          if (ccValue > this.lastControlValue) {
            this.target.setNormalized(LXUtils.lerp(
              targetNormalized, 1.,
              (ccValue - this.lastControlValue) / (double) (MidiControlChange.MAX_CC_VALUE - this.lastControlValue)
            ));
          } else if (ccValue < this.lastControlValue) {
            this.target.setNormalized(LXUtils.lerp(
              targetNormalized, 0.,
              (this.lastControlValue - ccValue) / (double) this.lastControlValue
            ));
          }
        } else if (ccValue == 0) {
          // If we just got the first movement, handle the extremes
          this.target.setNormalized(0);
        } else if (ccValue == MidiControlChange.MAX_CC_VALUE) {
          // If we just got the first movement, handle the extremes
          this.target.setNormalized(1);
        }
        break;
      }

    }
    this.lastControlValue = ccValue;
    this.internalUpdate = false;
  }

  private int getTargetMidiValue() {
    if (this.target == null) {
      return -1;
    }
    return (int) Math.round(this.target.getBaseNormalized() * MidiControlChange.MAX_CC_VALUE);
  }

  public void setTarget(LXListenableNormalizedParameter parameter) {
    if (this.target != null) {
      this.target.removeListener(this.listener);
    }
    this.target = parameter;
    this.isPickedUp = false;
    this.pickupDirection = 0;
    if (this.target != null) {
      resetPickup();
      this.target.addListener(this.listener);
    }
  }

  private void resetPickup() {
    this.pickupDirection = Integer.compare(getTargetMidiValue(), this.lastControlValue);
    this.isPickedUp = (this.pickupDirection == 0);
  }

  private void onTargetChanged(LXParameter p) {
    if (!this.internalUpdate) {
      this.pickupDirection = Integer.compare(getTargetMidiValue(), this.lastControlValue);
      this.isPickedUp = (this.pickupDirection == 0);
    }
  }

  public void dispose() {
    setTarget(null);
    this.lastControlValue = -1;
  }

}
