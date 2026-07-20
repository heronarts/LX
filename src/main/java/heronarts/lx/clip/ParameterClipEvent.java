package heronarts.lx.clip;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.utils.LXUtils;

public class ParameterClipEvent extends LXClipEvent<ParameterClipEvent> {

  public static final double MAX_POWER_EXPONENT = 18;

  public enum Curve {
    POWER_EASE("Power Ease"),
    POWER_S_CURVE("S-Curve"),
    SINUSOIDAL("Sinusoidal"),
    SMOOTHSTEP("Smoothstep");

    public final String label;

    private Curve(String label) {
      this.label = label;
    }

    @Override
    public final String toString() {
      return this.label;
    }
  }

  public final ParameterClipLane lane;
  private double normalized;
  private Curve curve = Curve.POWER_EASE;
  private double shape = 0;

  ParameterClipEvent(ParameterClipLane lane) {
    this(lane, lane.parameter.getBaseNormalized());
  }

  ParameterClipEvent(ParameterClipLane lane, Cursor cursor) {
    this(lane, cursor, lane.parameter.getBaseNormalized());
  }

  ParameterClipEvent(ParameterClipLane lane, double normalized) {
    super(lane, lane.parameter.getParent());
    this.lane = lane;
    this.normalized = normalizeEventValue(normalized);
  }

  ParameterClipEvent(ParameterClipLane lane, Cursor cursor, double normalized) {
    this(lane, normalized);
    setCursor(cursor);
  }

  private double normalizeEventValue(double normalized) {
    if (this.lane instanceof ParameterClipLane.Boolean) {
      normalized = (normalized > .5f) ? 1 : 0;
    } else {
      normalized = LXUtils.constrain(normalized, 0, 1);
    }
    return normalized;
  }

  boolean _setNormalized(double normalized) {
    normalized = normalizeEventValue(normalized);
    if (this.normalized != normalized) {
      this.normalized = normalized;
      return true;
    }
    return false;
  }

  public ParameterClipEvent setNormalized(double normalized) {
    if (_setNormalized(normalized)) {
      this.lane.onChange.bang();
    }
    return this;
  }

  public double getNormalized() {
    return this.normalized;
  }

  public float getNormalizedf() {
    return (float) this.normalized;
  }

  public ParameterClipEvent setCurve(Curve curve) {
    if (this.curve != curve) {
      this.curve = curve;
      this.lane.onChange.bang();
    }
    return this;
  }

  public ParameterClipEvent setShape(double shape) {
    shape = LXUtils.constrain(shape, -1, 1);
    if (this.shape != shape) {
      this.shape = shape;
      this.lane.onChange.bang();
    }
    return this;
  }

  /**
   * Curve type to this point
   *
   * @return Curve type
   */
  public Curve getCurve() {
    return this.curve;
  }

  /**
   * Curve shaping factor, from -1 to 1
   *
   * @return Curve shaping factor
   */
  public double getShape() {
    return this.shape;
  }

  public double interpolateFrom(ParameterClipEvent from, double lerpFactor) {
    return interpolateFrom(from.normalized, lerpFactor);
  }

  public double interpolateFrom(double from, double lerpFactor) {
    return switch (this.curve) {
    case POWER_EASE -> interpolatePowerEase(from, lerpFactor);
    case POWER_S_CURVE -> interpolateSCurve(from, lerpFactor);
    case SMOOTHSTEP -> interpolateSmoothstep(from, lerpFactor);
    case SINUSOIDAL -> interpolateSinusoidal(from, lerpFactor);
    };
  }

  private double interpolatePowerEase(double from, double lerpFactor) {
    return _interpolatePowerEase(from, this.normalized, this.shape, lerpFactor);
  }

  private static double _interpolatePowerEase(double from, double to, double shape, double lerpFactor) {
    if (shape < 0) {
      final double exponent = LXUtils.lerp(1, MAX_POWER_EXPONENT, shape*shape);
      return LXUtils.lerp(to, from, Math.pow(1-lerpFactor, exponent));
    } else if (shape > 0) {
      final double exponent = LXUtils.lerp(1, MAX_POWER_EXPONENT, shape*shape);
      return LXUtils.lerp(from, to, Math.pow(lerpFactor, exponent));
    }
    return LXUtils.lerp(from, to, lerpFactor);
  }

  private double interpolateSCurve(double from, double lerpFactor) {
    double midpoint = LXUtils.lerp(from, this.normalized, .5);
    return (lerpFactor <= 0.5) ?
      _interpolatePowerEase(from, midpoint, -this.shape, 2*lerpFactor) :
      _interpolatePowerEase(midpoint, this.normalized, this.shape, 2*(lerpFactor-.5));
  }

  private static double shapeLerpFactor(double lerpFactor, double shape) {
    return (shape == 0) ? lerpFactor :
      (lerpFactor <= 0.5) ?
        _interpolatePowerEase(0, 0.5, -shape, 2*lerpFactor) :
        _interpolatePowerEase(0.5, 1, shape, 2*(lerpFactor-.5));
  }

  private double interpolateSmoothstep(double from, double lerpFactor) {
    lerpFactor = shapeLerpFactor(lerpFactor, this.shape);
    return LXUtils.lerp(from, this.normalized, lerpFactor * lerpFactor * (3. - 2. * lerpFactor));
  }

  private double interpolateSinusoidal(double from, double lerpFactor) {
    lerpFactor = shapeLerpFactor(lerpFactor, this.shape);
    return LXUtils.lerp(from, this.normalized, .5 - .5 * Math.cos(lerpFactor*Math.PI));
  }

  public boolean isLinear() {
    return switch (this.curve) {
    case POWER_EASE, POWER_S_CURVE -> (this.shape == 0);
    default -> false;
    };
  }

  @Override
  public void execute() {
    this.lane.parameter.setNormalized(this.normalized);
  }

  @Override
  public String toString() {
    return this.cursor.toString() + " -> " + this.lane.parameter.getLabel() + "=" + getNormalized();
  }

  protected static final String KEY_NORMALIZED = "normalized";
  protected static final String KEY_CURVE = "curve";
  protected static final String KEY_SHAPE = "shape";

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    this.normalized = obj.has(KEY_NORMALIZED) ? obj.get(KEY_NORMALIZED).getAsDouble() : 0;
    if (obj.has(KEY_CURVE)) {
      final String curveStr = obj.get(KEY_CURVE).getAsString();
      try {
        this.curve = Enum.valueOf(Curve.class, curveStr);
      } catch (Exception x) {
        LX.error("Invalid ParameterClipEvent.Shape: " + curveStr);
      }
    } else {
      this.curve = Curve.POWER_EASE;
    }
    this.shape = obj.has(KEY_SHAPE) ? obj.get(KEY_SHAPE).getAsDouble() : 0;
  }

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_NORMALIZED, this.normalized);
    if (this.curve != Curve.POWER_EASE) {
      obj.addProperty(KEY_CURVE, this.curve.name());
    }
    if (this.shape != 0) {
      obj.addProperty(KEY_SHAPE, this.shape);
    }
  }
}
