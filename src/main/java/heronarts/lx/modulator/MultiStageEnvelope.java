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

package heronarts.lx.modulator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXCategory;
import heronarts.lx.LXSerializable;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.FixedParameter;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.utils.LXUtils;

@LXCategory(LXCategory.CORE)
@LXModulator.Global("Envelope")
@LXModulator.Device("Envelope")
public class MultiStageEnvelope extends LXVariablePeriodModulator implements LXWaveshape, LXOscComponent {

  public class Stage implements LXSerializable {
    private double basis;
    private double value;
    private double shape;
    private Stage previous = null;
    private Stage next = null;

    public final boolean initial;
    public final boolean last;

    private Stage(double basis, double value) {
      this(basis, value, 1, false, false);
    }

    private Stage(JsonObject obj) {
      this(
        obj.get(KEY_BASIS).getAsDouble(),
        obj.get(KEY_VALUE).getAsDouble(),
        obj.get(KEY_SHAPE).getAsDouble(),
        false,
        false
      );
    }

    private Stage(double basis, double value, double shape, boolean initial, boolean last) {
      this.basis = basis;
      this.value = value;
      this.shape = shape;
      this.initial = initial;
      this.last = last;
    }

    public void setPosition(double basis, double value) {
      if (!this.initial && !this.last) {
        this.basis = LXUtils.constrain(basis, this.previous.basis, this.next.basis);
      }
      this.value = value;
      monitor.bang();
    }

    public void setShape(double shape) {
      this.shape = shape;
      monitor.bang();
    }

    public double getBasis() {
      return this.basis;
    }

    public double getValue() {
      return this.value;
    }

    public double getShape() {
      return this.shape;
    }

    @Override
    public String toString() {
      return String.format("Basis: %.2f Value: %.2f", this.basis, this.value);
    }

    private static final String KEY_BASIS = "basis";
    private static final String KEY_VALUE = "value";
    private static final String KEY_SHAPE = "shape";

    @Override
    public void save(LX lx, JsonObject object) {
      object.addProperty(KEY_BASIS, this.basis);
      object.addProperty(KEY_VALUE, this.value);
      object.addProperty(KEY_SHAPE, this.shape);
    }

    @Override
    public void load(LX lx, JsonObject object) {
      if (object.has(KEY_BASIS)) {
        this.basis = object.get(KEY_BASIS).getAsDouble();
      }
      if (object.has(KEY_VALUE)) {
        this.value = object.get(KEY_VALUE).getAsDouble();
      }
      if (object.has(KEY_SHAPE)) {
        this.shape = object.get(KEY_SHAPE).getAsDouble();
      }
    }
  }

  private final List<Stage> mutableStages = new ArrayList<Stage>();

  public final List<Stage> stages = Collections.unmodifiableList(mutableStages);

  public final MutableParameter monitor = new MutableParameter("Monitor");

  public MultiStageEnvelope() {
    this("Env");
  }

  public MultiStageEnvelope(String label) {
    this(label, 0, 1);
  }

  public MultiStageEnvelope(String label, float initialValue, float endValue) {
    super(label, new FixedParameter(0), new FixedParameter(1), new FixedParameter(1000));
    setPeriod(this.periodFast);
    setLooping(false);
    this.tempoLock.setValue(false);

    addLegacyParameter("period", this.periodFast);

    this.mutableStages.add(new Stage(0, initialValue, 1, true, false));
    this.mutableStages.add(new Stage(1, endValue, 1, false, true));
    updateStages();
  }

  private void updateStages() {
    Stage previous = null;
    for (Stage stage : this.mutableStages) {
      stage.previous = previous;
      stage.next = null;
      if (previous != null) {
        previous.next = stage;
      }
      previous = stage;
    }
  }

  public MultiStageEnvelope removeStage(Stage stage) {
    if (!stage.initial && !stage.last) {
      this.mutableStages.remove(stage);
      updateStages();
      this.monitor.bang();
    }
    return this;
  }

  public Stage addStage(Stage stage) {
    for (int i = 1; i < this.mutableStages.size(); ++i) {
      if (stage.basis <= this.mutableStages.get(i).basis) {
        this.mutableStages.add(i, stage);
        updateStages();
        this.monitor.bang();
        break;
      }
    }
    return stage;
  }

  public Stage addStage(double basis, double value) {
    basis = LXUtils.constrain(basis, 0, 1);
    value = LXUtils.constrain(value, 0, 1);
    return addStage(new Stage(basis, value));
  }

  @Override
  protected double computeNormalizedValue(double deltaMs, double basis) {
    return compute(basis);
  }

  @Override
  protected double computeNormalizedBasis(double basis, double normalizedValue) {
    throw new UnsupportedOperationException("Cannot invert MultiStageEnvelope");
  }

  @Override
  public double compute(double basis) {
    double prevValue = 0;
    double prevBasis = 0;
    for (Stage stage : this.mutableStages) {
      if (basis < stage.basis) {
        double relativeBasis = (basis - prevBasis) / (stage.basis - prevBasis);
        return LXUtils.lerp(prevValue, stage.value, Math.pow(relativeBasis, stage.shape));
      } else if (basis == stage.basis) {
        return stage.value;
      }
      prevBasis = stage.basis;
      prevValue = stage.value;
    }
    return 0;
  }

  @Override
  public double invert(double value, double basisHint) {
    throw new UnsupportedOperationException("Custom staged envelopes are not invertable");
  }

  private static final String KEY_STAGES = "stages";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_STAGES, LXSerializable.Utils.toArray(lx, this.mutableStages));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    if (obj.has(KEY_STAGES)) {
      JsonArray stageArr = obj.getAsJsonArray(KEY_STAGES);
      int index = 0;
      for (JsonElement stageElem : stageArr) {
        JsonObject stageObj = stageElem.getAsJsonObject();
        if (index == 0) {
          this.mutableStages.get(0).load(lx, stageObj);
        } else if (index == stageArr.size() - 1) {
          this.mutableStages.get(this.mutableStages.size()-1).load(lx, stageObj);
        } else {
          addStage(new Stage(stageObj));
        }
        ++index;
      }
    }
    super.load(lx, obj);
    this.monitor.bang();
  }

}
