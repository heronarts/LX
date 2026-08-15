package heronarts.lx.clip;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.utils.LXUtils;

public class ParameterClipEvent extends LXClipEvent<ParameterClipEvent> {

  public static final double MAX_POWER_EXPONENT = 18;

  public enum Curve {
    POWER_EASE("Power Ease"),
    POWER_S_CURVE("S-Curve"),
    SMOOTHSTEP("Smoothstep"),
    SINUSOIDAL("Sinusoidal");

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
  private int wraps = 0;

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

  boolean _setWraps(int wraps) {
    if (this.wraps != wraps) {
      this.wraps = wraps;
      return true;
    }
    return false;
  }

  public ParameterClipEvent setWraps(int wraps) {
    if (_setWraps(wraps)) {
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

  /**
   * Number of wraps since prior event
   *
   * @return Number of wraps to this point
   */
  public int getWraps() {
    return this.wraps;
  }

  public double interpolateFrom(ParameterClipEvent from, double lerpFactor) {
    return interpolateFrom(from.normalized, lerpFactor);
  }

  public double interpolateFrom(double from, double lerpFactor) {
    return (this.wraps == 0) ?
      interpolate(from, this.normalized, lerpFactor) :
      LXUtils.wrapn(interpolate(from, this.normalized + this.wraps, lerpFactor));
  }

  double interpolateUnwrapped(ParameterClipEvent from, double lerpFactor) {
    return interpolate(from.normalized, this.normalized + this.wraps, lerpFactor);
  }

  /**
   * Interpolates the segment arriving at this event *without* wrapping the result.
   * When wraps are set, the interpolation target is offset by the wrap count
   * and the result may fall outside of [0,1].
   *
   * @param from Value interpolating from
   * @param to Value interpolating to
   * @param lerpFactor Interpolation amount
   * @return Interpolated value in unwrapped space
   */
  private double interpolate(double from, double to, double lerpFactor) {
    return switch (this.curve) {
    case POWER_EASE -> interpolatePowerEase(from, to, lerpFactor);
    case POWER_S_CURVE -> interpolateSCurve(from, to, lerpFactor);
    case SMOOTHSTEP -> interpolateSmoothstep(from, to, lerpFactor);
    case SINUSOIDAL -> interpolateSinusoidal(from, to, lerpFactor);
    };
  }

  private double interpolatePowerEase(double from, double to, double lerpFactor) {
    return _interpolatePowerEase(from, to, this.shape, lerpFactor);
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

  private double interpolateSCurve(double from, double to, double lerpFactor) {
    double midpoint = LXUtils.lerp(from, to, .5);
    return (lerpFactor <= 0.5) ?
      _interpolatePowerEase(from, midpoint, -this.shape, 2*lerpFactor) :
      _interpolatePowerEase(midpoint, to, this.shape, 2*(lerpFactor-.5));
  }

  private static double shapeLerpFactor(double lerpFactor, double shape) {
    return (shape == 0) ? lerpFactor :
      (lerpFactor <= 0.5) ?
        _interpolatePowerEase(0, 0.5, -shape, 2*lerpFactor) :
        _interpolatePowerEase(0.5, 1, shape, 2*(lerpFactor-.5));
  }

  private double interpolateSmoothstep(double from, double to, double lerpFactor) {
    lerpFactor = shapeLerpFactor(lerpFactor, this.shape);
    return LXUtils.lerp(from, to, lerpFactor * lerpFactor * (3. - 2. * lerpFactor));
  }

  private double interpolateSinusoidal(double from, double to, double lerpFactor) {
    lerpFactor = shapeLerpFactor(lerpFactor, this.shape);
    return LXUtils.lerp(from, to, .5 - .5 * Math.cos(lerpFactor*Math.PI));
  }

  public boolean isLinear() {
    if (this.wraps != 0) {
      return false;
    }
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
  protected static final String KEY_WRAPS = "wraps";

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
    this.wraps = obj.has(KEY_WRAPS) ? obj.get(KEY_WRAPS).getAsInt() : 0;
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
    if (this.wraps != 0) {
      obj.addProperty(KEY_WRAPS, this.wraps);
    }
  }
}
