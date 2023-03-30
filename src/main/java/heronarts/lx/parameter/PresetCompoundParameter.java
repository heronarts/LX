/**
 * Copyright 2023- Justin Belcher, Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.parameter;

import java.util.ArrayList;
import java.util.List;

import heronarts.lx.LX;
import heronarts.lx.utils.LXUtils;

/**
 * PresetCompoundParameter adds a "snap-to" functionality to CompoundParameter.
 *
 * While the associated BooleanParameter.isOn() is True, the incrementNormalized()
 * behavior of this parameter is modified to accumulate ticks rather than
 * modifying the underlying value immediately.  After a certain number of ticks
 * have accumulated the parameter value will be moved to the next preset in
 * the list.
 *
 * For example this can be used in combination with a MidiFighterTwister:
 * -Create a momentary BooleanParameter and pass it to the constructor here
 *  or assign it with setPresetParameter().
 * -Pass a double[] of preset values to setPresetValues().
 * -Add both parameters to the LXDeviceComponent (pattern/effect)
 * -Hold the boolean MFT knob while turning the knob for this parameter.  This parameter will
 *  jump between presets with no messy side effects, ie no listeners notified
 *  for in-between values.
 */
public class PresetCompoundParameter extends CompoundParameter {

  public PresetCompoundParameter(String label) {
    super(label);
  }

  public PresetCompoundParameter(String label, double value) {
    super(label, value);
  }

  public PresetCompoundParameter(String label, double value, double max) {
    super(label, value, max);
  }

  public PresetCompoundParameter(String label, double value, double v0, double v1) {
    super(label, value, v0, v1);
  }

  public PresetCompoundParameter(LXListenableParameter underlying, double v0, double v1) {
    super(underlying, v0, v1);
  }

  public PresetCompoundParameter(String label, BooleanParameter presetParameter) {
    super(label);
    this.presetParameter = presetParameter;
  }

  public PresetCompoundParameter(String label, double value, BooleanParameter presetParameter) {
    super(label, value);
    this.presetParameter = presetParameter;
  }

  public PresetCompoundParameter(String label, double value, double max, BooleanParameter presetParameter) {
    super(label, value, max);
    this.presetParameter = presetParameter;
  }

  public PresetCompoundParameter(String label, double value, double v0, double v1, BooleanParameter presetParameter) {
    super(label, value, v0, v1);
    this.presetParameter = presetParameter;
  }

  public PresetCompoundParameter(LXListenableParameter underlying, double v0, double v1, BooleanParameter presetParameter) {
    super(underlying, v0, v1);
    this.presetParameter = presetParameter;
  }

  private BooleanParameter presetParameter;

  public PresetCompoundParameter setPresetParameter(BooleanParameter presetParameter) {
    this.presetParameter = presetParameter;
    return this;
  }

  public BooleanParameter getPresetParameter() {
    return this.presetParameter;
  }

  public boolean isPresetMode() {
    return this.presetParameter != null && this.presetParameter.isOn();
  }

  private double[] presets = null;
  private double[] normalizedPresets = null;
  private static final int TICKS_PER_PRESET = 12;
  private int ticks = 0;
  private boolean valueIsPreset = false;
  private int iPreset = -1;

  public PresetCompoundParameter setPresetValues(double[] values) {
    this.presets = sanitizeValues(values);
    this.normalizedPresets = null;
    this.valueIsPreset = false;
    this.iPreset = -1;
    return this;
  }

  public PresetCompoundParameter setPresetValuesNormalized(double[] normalizedValues) {
    this.normalizedPresets = sanitizeValues(normalizedValues, 0, 1);
    this.presets = null;
    this.valueIsPreset = false;
    this.iPreset = -1;
    return this;
  }

  private double[] sanitizeValues(double[] values) {
    return sanitizeValues(values, this.range.min, this.range.max);
  }

  private double[] sanitizeValues(double[] values, double min, double max) {
    List<Double> filtered = new ArrayList<Double>();

    for (double value : values) {
      if (min <= value && value <= max) {
        filtered.add(value);
      } else {
        LX.error("Invalid preset value: " + Double.toString(value));
      }
    }

    double[] r = new double[filtered.size()];
    for (int i = 0; i < filtered.size(); i++) {
      r[i] = filtered.get(i);
    }

    return r;
  }

  @Override
  public LXListenableNormalizedParameter incrementNormalized(double amount, boolean wrap) {
    return incrementNormalized(amount, wrap, isPresetMode());
  }

  public LXListenableNormalizedParameter incrementNormalized(double amount, boolean wrap, boolean preset) {
    if (preset) {
      // Swallow increments and convert to ticks
      if (amount > 0) {
        ticks = LXUtils.max(this.ticks, 0) + 1;
        if (this.ticks == TICKS_PER_PRESET) {
          nextPreset(wrap);
        }
      } else if (amount < 0) {
        this.ticks = LXUtils.min(this.ticks, 0) - 1;
        if (this.ticks == 0-TICKS_PER_PRESET) {
          previousPreset(wrap);
        }
      }
      return this;
    } else {
      this.ticks = 0;
      this.valueIsPreset = false;
      return super.incrementNormalized(amount, wrap);
    }
  }

  public PresetCompoundParameter nextPreset() {
    return nextPreset(isWrappable());
  }

  public final PresetCompoundParameter nextPreset(boolean wrap) {
    this.ticks = 0;
    if (this.valueIsPreset) {
      if (this.presets != null && this.presets.length > 0) {
        if (this.iPreset == this.presets.length-1) {
          // Wrap if allowed
          if (wrap) {
            this.iPreset = 0;
            super.setValue(this.presets[this.iPreset]);
          }
        } else {
          this.iPreset++;
          super.setValue(this.presets[this.iPreset]);
        }
      } else if (this.normalizedPresets != null && this.normalizedPresets.length > 0) {
        if (this.iPreset == this.normalizedPresets.length-1) {
          // Wrap if allowed
          if (wrap) {
            this.iPreset = 0;
            super.setValue(this.normalizedPresets[this.iPreset]);
          }
        } else {
          this.iPreset++;
          super.setValue(this.normalizedPresets[this.iPreset]);
        }
      }
    } else {
      if (this.presets != null) {
        // Presets are values
        double value = getValue();
        // Locate next preset above current value
        for (int i=0; i<this.presets.length; i++) {
          if (this.presets[i] > value) {
            this.valueIsPreset = true;
            this.iPreset = i;
            super.setValue(this.presets[this.iPreset]);
            return this;
          }
        }
        // All presets were less than current value. Wrap if allowed.
        if (wrap) {
          if (this.presets.length > 0) {
            this.valueIsPreset = true;
            this.iPreset = 0;
            super.setValue(this.presets[this.iPreset]);
          }
        }
      } else if (this.normalizedPresets != null) {
        // Presets are normalized values
        double normalizedValue = getNormalized();
        // Locate next preset above current value
        for (int i=0; i<this.normalizedPresets.length; i++) {
          if (this.normalizedPresets[i] > normalizedValue) {
            this.valueIsPreset = true;
            this.iPreset = i;
            super.setValue(this.normalizedPresets[this.iPreset]);
            return this;
          }
        }
        // All presets were less than current value. Wrap if allowed.
        if (wrap) {
          if (this.normalizedPresets.length > 0) {
            this.valueIsPreset = true;
            this.iPreset = 0;
            super.setValue(this.normalizedPresets[this.iPreset]);
          }
        }
      }
    }
    return this;
  }

  public PresetCompoundParameter previousPreset() {
    return previousPreset(isWrappable());
  }

  public final PresetCompoundParameter previousPreset(boolean wrap) {
    this.ticks = 0;
    if (this.valueIsPreset) {
      if (this.presets != null && this.presets.length > 0) {
        if (this.iPreset <= 0) {
          // Wrap if allowed
          if (wrap) {
            this.iPreset = this.presets.length - 1;
            super.setValue(this.presets[this.iPreset]);
          }
        } else {
          this.iPreset--;
          super.setValue(this.presets[this.iPreset]);
        }
      } else if (this.normalizedPresets != null && this.normalizedPresets.length > 0) {
        if (this.iPreset <= 0) {
          // Wrap if allowed
          if (wrap) {
            this.iPreset = this.normalizedPresets.length - 1;
            super.setValue(this.normalizedPresets[this.iPreset]);
          }
        } else {
          this.iPreset--;
          super.setValue(this.normalizedPresets[this.iPreset]);
        }
      }
    } else {
      if (this.presets != null) {
        // Presets are values
        double value = getValue();
        // Locate next preset below current value
        for (int i=this.presets.length-1; i>=0; i--) {
          if (this.presets[i] < value) {
            this.valueIsPreset = true;
            this.iPreset = i;
            super.setValue(this.presets[this.iPreset]);
            return this;
          }
        }
        // All presets were above current value. Wrap if allowed.
        if (wrap) {
          if (this.presets.length > 0) {
            this.valueIsPreset = true;
            this.iPreset = this.presets.length - 1;
            super.setValue(this.presets[this.iPreset]);
          }
        }
      } else if (this.normalizedPresets != null) {
        // Presets are normalized values
        double normalizedValue = getNormalized();
        // Locate next preset below current value
        for (int i=this.normalizedPresets.length-1; i>=0; i--) {
          if (this.normalizedPresets[i] < normalizedValue) {
            this.valueIsPreset = true;
            this.iPreset = i;
            super.setValue(this.normalizedPresets[this.iPreset]);
            return this;
          }
        }
        // All presets were above current value. Wrap if allowed.
        if (wrap) {
          if (this.normalizedPresets.length > 0) {
            this.valueIsPreset = true;
            this.iPreset = this.normalizedPresets.length - 1;
            super.setValue(this.normalizedPresets[this.iPreset]);
          }
        }
      }
    }
    return this;
  }

  @Override
  public void dispose() {
    this.presetParameter = null;
    super.dispose();
  }

}
