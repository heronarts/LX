package heronarts.lx.clip;

import java.util.Comparator;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXSerializable;

/**
 * A cursor can keep track of time in two ways - either raw wall-clock milliseconds or
 * a tempo-based beat count plus position within the beat.
 */
public class Cursor implements Comparable<Cursor>, LXSerializable {

  public static final Cursor ZERO = new Cursor.Immutable(0);

  public static final Cursor MIN_LOOP = new Cursor.Immutable(
    125,
    0,
    1 / 8. // or 1/32nd note (eighth of a single beat, 125ms at 60bpm)
  );

  public final static Comparator<Cursor> COMPARATOR = new Comparator<Cursor>() {
    @Override
    public int compare(Cursor o1, Cursor o2) {
      if (o1.millis < o2.millis) {
        return -1;
      } else if (o1.millis > o2.millis) {
        return 1;
      }
      return 0;
    }

  };

  public enum Mode {
    ABSOLUTE,
    TEMPO
  };

  private double millis = 0;
  private int beatCount = 0;
  private double beatBasis = 0;

  private void assertMutable() {
    if (this instanceof Immutable) {
      throw new UnsupportedOperationException();
    }
  }

  public Cursor() {}

  public Cursor(double millis) {
    this.millis = millis;
    // TODO(clips): prime the beat fields
  }

  public Cursor(double millis, int beatCount, double beatBasis) {
    this.millis = millis;
    this.beatCount = beatCount;
    this.beatBasis = beatBasis;
  }

  private Cursor(Cursor that) {
    set(that);
  }

  @Override
  public Cursor clone() {
    return new Cursor(this);
  }

  public boolean isNegative() {
    return this.millis < 0;
  }

  public double getMillis() {
    return this.millis;
  }

  public int getBeatCount() {
    return this.beatCount;
  }

  public double getBeatBasis() {
    return this.beatBasis;
  }

  public boolean isZero() {
    return this.millis == 0;
  }

  public boolean isBefore(Cursor that) {
    return this.millis < that.millis;
  }

  public boolean isBeforeOrEqual(Cursor that) {
    return this.millis <= that.millis;
  }

  public boolean isAfter(Cursor that) {
    return this.millis > that.millis;
  }

  public boolean isAfterOrEqual(Cursor that) {
    return this.millis >= that.millis;
  }

  public boolean isInRange(Cursor before, Cursor after) {
    return this.millis >= before.millis && this.millis <= after.millis;
  }

  public void reset() {
    this.millis = 0;
    this.beatCount = 0;
    this.beatBasis = 0;
  }

  public Cursor setScaledDifference(Cursor from, Cursor to, double scale) {
    setMillis(from.millis + (to.millis - from.millis) * scale);
    return this;
  }

  public Cursor set(Cursor that) {
    assertMutable();
    this.millis = that.millis;
    this.beatCount = that.beatCount;
    this.beatBasis = that.beatBasis;
    return this;
  }

  /**
   * Creates a new cursor that sums the two
   *
   * @param that Cursor to add
   * @return New cursor representing sum of this cursor with the other
   */
  public Cursor add(Cursor that) {
    return clone()._add(that);
  }

  private Cursor _add(Cursor that) {
    assertMutable();
    this.millis += that.millis;
    this.beatCount += that.beatCount;
    this.beatBasis += that.beatBasis;
    if (this.beatBasis > 1) {
      ++this.beatCount;
      this.beatBasis = this.beatBasis % 1.;
    }
    return this;
  }

  /**
   * Increments a new cursor that adds or bustract the two
   *
   * @param that Cursor to add or subtract
   * @param add True to add, if false then subtraction
   * @return New cursor representing the result of the operation
   */
  public Cursor inc(Cursor that, boolean add) {
    return add ? add(that) : subtract(that);
  }

  public Cursor subtract(Cursor that) {
    return clone()._subtract(that);
  }

  Cursor _subtract(Cursor that) {
    assertMutable();
    this.millis -= that.millis;
    this.beatCount -= that.beatCount;
    this.beatBasis -= that.beatBasis;
    if (this.beatBasis < 0) {
      --this.beatCount;
      this.beatBasis = this.beatBasis + 1;
    }
    return this;
  }

  public Cursor snap(Cursor snapSize) {
    assertMutable();
    // TODO(clips): implement in timing-context aware way
    this.millis = Math.round(this.millis / snapSize.millis) * snapSize.millis;
    return this;
  }

  // NOTE: this applies to the scenario where a previously non-grid-snapped Cursor move
  // left something *extremely* close to a grid marker, such that the user wouldn't really
  // notice the non-equality.
  private static final double SNAP_THRESHOLD = .01;

  public Cursor snapUp(Cursor snapSize) {
    assertMutable();
    // TODO(clips): implement in timing-context aware way
    double snapUnits = this.millis / snapSize.millis;
    double decimal = snapUnits - ((long) snapUnits);
    if ((decimal < SNAP_THRESHOLD) || ((1 - decimal) < SNAP_THRESHOLD)) {
      // It's at the top
      this.millis = (1 + Math.ceil(snapUnits)) * snapSize.millis;
    } else {
      this.millis = Math.ceil(snapUnits) * snapSize.millis;
    }
    return this;
  }

  public Cursor snapDown(Cursor snapSize) {
    assertMutable();
    double snapUnits = this.millis / snapSize.millis;
    double decimal = snapUnits - ((long) snapUnits);
    if (decimal < SNAP_THRESHOLD) {
      // It's at the bottom
      this.millis = (Math.floor(snapUnits) - 1) * snapSize.millis;
    } else {
      this.millis = Math.floor(snapUnits) * snapSize.millis;
    }
    return this;
  }

  public static Cursor min(Cursor c1, Cursor c2) {
    return c1.isBeforeOrEqual(c2) ? c1 : c2;
  }

  public static Cursor max(Cursor c1, Cursor c2) {
    return c1.isAfter(c2) ? c1 : c2;
  }

  /**
   * Constrain the cursor's value to the bounds of this clip
   *
   * @param clip Clip to constrain cursor bounds to
   * @return Cursor
   */
  public Cursor constrain(LXClip clip) {
    return constrain(clip.length.cursor);
  }

  /**
   * Constrain the cursor to the range from ZERO to the target cursor
   *
   * @param max Maximum acceptable cursor value
   * @return Cursor with modification applied
   */
  public Cursor constrain(Cursor max) {
    return constrain(ZERO, max);
  }

  /**
   * Constrain the cursor to the range
   *
   * @param min Minimum acceptable cursor value
   * @param max Maximum acceptable cursor value
   * @return
   */
  public Cursor constrain(Cursor min, Cursor max) {
    assertMutable();
    if (isBefore(min)) {
      set(min);
    }
    if (isAfter(max)) {
      set(max);
    }
    return this;
  }

  /**
   * Non-destructive equivalent of constrain. Returns
   * ZERO if this cursor is before the ZERO-max range,
   * and max if this cursor is after the ZERO-max range.
   * Otherwise the cursor itself is returned
   *
   * @param clip Clip whose length to bound by
   * @return Cursor if within bounds, otherwise ZERO/clip.length.cursor
   */
  public Cursor bound(LXClip clip) {
    return bound(Cursor.ZERO, clip.length.cursor);
  }

  /**
   * Non-destructive equivalent of constrain. Returns
   * ZERO if this cursor is before the ZERO-max range,
   * and max if this cursor is after the ZERO-max range.
   * Otherwise the cursor itself is returned
   *
   * @param max Maximum bound
   * @return Cursor if within bounds, otherwise ZERO/max are returned
   */
  public Cursor bound(Cursor max) {
    return bound(Cursor.ZERO, max);
  }

  /**
   * Non-destructive equivalent of constrain. Returns
   * min if this cursor is before the min-max range,
   * and max if this cursor is after the min-max range.
   * Otherwise the cursor itself is returned
   *
   * @param min Minimum bound
   * @param max Maximum bound
   * @return Cursor if within bounds, otherwise min/max are returned
   */
  public Cursor bound(Cursor min, Cursor max) {
    if (isBefore(min)) {
      return min;
    }
    if (isAfter(max)) {
      return max;
    }
    return this;
  }

  @Deprecated
  public double getDeltaMillis(Cursor that) {
    return this.millis - that.millis;
  }

  /**
   * Gets the lerp factor of this cursor, as a normalized
   * value with 0-1 representing the range between pre and post
   *
   * @param pre Reference start cursor
   * @param post Reference end cursor
   * @return Normalized value from 0-1 assuming this Cursor is between pre and post
   */
  public double getLerpFactor(Cursor pre, Cursor post) {
    // TODO(clips): implement tempo-version
    if (pre.millis == post.millis) {
      return 0;
    }
    return (this.millis - pre.millis) / (post.millis - pre.millis);
  }

  /**
   * Gets the ratio of the current cursor value to the difference
   * between pre and post. Note that the cursor doesn't have to be
   * contained by pre and post, it's treated as a magnitude
   *
   * @param pre Reference starting point
   * @param post Reference ending point
   * @return Ratio of the magnitude of this cursor to the difference between pre and post
   */
  public double getLerpRatio(Cursor pre, Cursor post) {
    // TODO(clips): implement tempo-version
    if (pre.millis == post.millis) {
      return 1;
    }
    return this.millis / (post.millis - pre.millis);
  }

  /**
   * Gets the ratio of the length of this cursor to the given argument.
   *
   * @param that Other cursor
   * @return Ratio of this cursor's length to the other
   */
  public double getRatio(Cursor that) {
    // TODO(clips): implement tempo-version
    return this.millis / that.millis;
  }

  @Deprecated
  public Cursor setMillis(double millis) {
    // TODO(clips): infer the correct beatCount and beatBasis
    this.millis = millis;
    return this;
  }

  @Override
  public String toString() {
    return String.format("%.2f/%d:%.2f", this.millis, this.beatCount, this.beatBasis);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof Cursor) {
      Cursor that = (Cursor) o;
      return
        this.millis == that.millis &&
        this.beatCount == that.beatCount &&
        this.beatBasis == that.beatBasis;
    }
    return false;
  }

  @Override
  public int compareTo(Cursor that) {
    return COMPARATOR.compare(this, that);
  }

  void _next(Cursor cursor, double deltaMs) {
    set(cursor);
    this.millis += deltaMs;
    // TODO(clips): next cursor should set beatCount and beatBasis
    // from global Tempo relative to start time of the clip
  }

  private static final String KEY_MILLIS = "millis";
  private static final String KEY_BEAT_COUNT = "beatCount";
  private static final String KEY_BEAT_BASIS = "beatBasis";

  @Override
  public void save(LX lx, JsonObject obj) {
    obj.addProperty(KEY_MILLIS, this.millis);
    obj.addProperty(KEY_BEAT_COUNT, this.beatCount);
    obj.addProperty(KEY_BEAT_BASIS, this.beatBasis);

  }

  @Override
  public void load(LX lx, JsonObject obj) {
    if (obj.has(KEY_MILLIS)) {
      this.millis = obj.get(KEY_MILLIS).getAsDouble();
    }
    if (obj.has(KEY_BEAT_COUNT)) {
      this.beatCount = obj.get(KEY_BEAT_COUNT).getAsInt();
    }
    if (obj.has(KEY_BEAT_BASIS)) {
      this.beatBasis = obj.get(KEY_BEAT_BASIS).getAsDouble();
    }
  }

  private static class Immutable extends Cursor {
    private Immutable(double millis) {
      super(millis);
    }

    private Immutable(double millis, int beatCount, double beatBasis) {
      super(millis, beatCount, beatBasis);
    }
  }

}