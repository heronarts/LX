package heronarts.lx.clip;

import java.util.Comparator;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXSerializable;
import heronarts.lx.utils.LXUtils;

/**
 * A cursor can keep track of time in two ways - either raw wall-clock milliseconds or
 * a tempo-based beat count plus position within the beat.
 */
public class Cursor implements LXSerializable {

  // NOTE: this applies to the scenario where a previously non-grid-snapped Cursor move
  // left something *extremely* close to a grid marker, such that the user wouldn't really
  // notice the non-equality.
  private static final double SNAP_THRESHOLD = .01;

  public static final Cursor.Immutable ZERO = new Cursor.Immutable("ZERO", 0);

  public static final Cursor.Immutable MIN_LOOP = new Cursor.Immutable("MIN_LOOP",
    125,
    0,
    1 / 8. // or 1/32nd note (eighth of a single beat, 125ms at 60bpm)
  );

  public static final Cursor.Immutable MIN_VIEW = MIN_LOOP;

  public static final Cursor.Immutable MIN_GRID_SPACING = new Cursor.Immutable(
    "MIN_GRID_SPACING",
    1000 / 64., // ~1 frame at 64fps ~16ms
    0,
    1 / 8.
  );

  public static final Cursor.Immutable MIN_LABEL_SPACING = new Cursor.Immutable(
    "MIN_LABEL_SPACING",
    1000 / 64., // ~1 frame at 64fps ~16ms
    0,
    1 / 4. // 16th notes
  );

  /**
   * The Cursor.Operator interface specifies all the operations that can be
   * performed on Cursors which depend upon the TimeBase setting. Clients
   * of the Cursor API will generally retrieve the appropriate Operator before
   * working with cursor operations - typically this comes from the context
   * of an LXClip.
   */
  public interface Operator extends Comparator<Cursor> {

    /**
     * Return <code>true</code> if the cursor is at position 0
     *
     * @param cursor Cursor
     * @return <code>true</code> if the cursor is at position 0
     */
    public boolean isZero(Cursor cursor);

    /**
     * Returns <code>true</code> if c1 is strictly before c2
     *
     * @param c1 First cursor
     * @param c2 Second cursor
     * @return <code>true</code> if c1 is strictly before c2
     */
    public boolean isBefore(Cursor c1, Cursor c2);

    /**
     * Returns <code>true</code> if c1 is  before or at the same time as c2
     *
     * @param c1 First cursor
     * @param c2 Second cursor
     * @return <code>true</code> if c1 is  before or at the same time as c2
     */
    public boolean isBeforeOrEqual(Cursor c1, Cursor c2);

    /**
     * Returns <code>true</code> if c1 is strictly after c2
     *
     * @param c1 First cursor
     * @param c2 Second cursor
     * @return <code>true</code> if c1 is strictly after c2
     */
    public boolean isAfter(Cursor c1, Cursor c2);

    /**
     * Returns <code>true</code> if c1 is after or at the same time as c2
     *
     * @param c1 First cursor
     * @param c2 Second cursor
     * @return <code>true</code> if c1 is after or at the same time as c2
     */
    public boolean isAfterOrEqual(Cursor c1, Cursor c2);

    /**
     * Returns <code>true</code> if c1 is at the same time as c2
     *
     * @param c1 First cursor
     * @param c2 Second cursor
     * @return <code>true</code> if c1 is at the same time as c2
     */
    public boolean isEqual(Cursor c1, Cursor c2);

    /**
     * Returns <code>true</code> if the cursor falls in the given range, inclusive
     *
     * @param cursor Cursor to check
     * @param before Lower bound
     * @param after Upper bound
     * @return <code>true</code> if the cursor falls in the given range, inclusive
     */
    public boolean isInRange(Cursor cursor, Cursor before, Cursor after);

    /**
     * Returns a proportional ratio between the two Cursor positions
     *
     * @param c1 First cursor
     * @param c2 Second cursor
     * @return Ratio of c1 position to c2, or 0 if c2.isZero()
     */
    public double getRatio(Cursor c1, Cursor c2);

    /**
     * Gets the lerp factor of a cursor, as a normalized
     * value with 0-1 representing the range between pre and post
     *
     * @param cursor Cursor value
     * @param pre Reference start cursor
     * @param post Reference end cursor
     * @return Normalized value from 0-1 assuming this Cursor is between pre and post
     */
    public double getLerpFactor(Cursor cursor, Cursor pre, Cursor post);

    /**
     * Gets the ratio of a cursor value to the difference
     * between pre and post. Note that the cursor doesn't have to be
     * contained by pre and post, it's treated as a magnitude
     *
     * @param cursor Cursor value
     * @param pre Reference starting point
     * @param post Reference ending point
     * @return Ratio of the magnitude of this cursor to the difference between pre and post
     */
    public double getLerpRatio(Cursor cursor, Cursor pre, Cursor post);

    /**
     * Returns the cursor that takes place first, or c1 if equal
     *
     * @param c1 First cursor
     * @param c2 Second cursor
     * @return Whichever cursor is earlier, or c1 if equal
     */
    public default Cursor min(Cursor c1, Cursor c2) {
      return isBeforeOrEqual(c1, c2) ? c1 : c2;
    }

    /**
     * Returns the cursor that takes place later, or c2 if equal
     *
     * @param c1 First cursor
     * @param c2 Second cursor
     * @return Whichever cursor is later, or c2 if equal
     */
    public default Cursor max(Cursor c1, Cursor c2) {
      return isBeforeOrEqual(c1, c2) ? c2 : c1;
    }

    /**
     * Constrain the cursor's value to the bounds of this clip
     *
     * @param cursor Cursor to constrain
     * @param clip Clip to constrain cursor bounds to
     * @return Cursor
     */
    public default Cursor constrain(Cursor cursor, LXClip clip) {
      return constrain(cursor, ZERO, clip.length.cursor);
    }

    /**
     * Constrain the cursor to the range from ZERO to the target cursor
     *
     * @param cursor Cursor to constrain
     * @param max Maximum acceptable cursor value
     * @return Cursor with modification applied
     */
    public default Cursor constrain(Cursor cursor, Cursor max) {
      return constrain(cursor, ZERO, max);
    }

    /**
     * Constrain the cursor to the range
     *
     * @param cursor Cursor to constrain
     * @param min Minimum acceptable cursor value
     * @param max Maximum acceptable cursor value
     * @return
     */
    public default Cursor constrain(Cursor cursor, Cursor min, Cursor max) {
      if (isBefore(cursor, min)) {
        cursor.set(min);
      }
      if (isAfter(cursor, max)) {
        cursor.set(max);
      }
      return cursor;
    }

    /**
     * Non-destructive equivalent of constrain. Returns
     * ZERO if this cursor is before the ZERO-max range,
     * and max if this cursor is after the ZERO-max range.
     * Otherwise the cursor itself is returned
     *
     * @param cursor Cursor to bound
     * @param clip Clip whose length to bound by
     * @return Cursor if within bounds, otherwise ZERO/clip.length.cursor
     */
    public default Cursor bound(Cursor cursor, LXClip clip) {
      return bound(cursor, ZERO, clip.length.cursor);
    }

    /**
     * Non-destructive equivalent of constrain. Returns
     * ZERO if this cursor is before the ZERO-max range,
     * and max if this cursor is after the ZERO-max range.
     * Otherwise the cursor itself is returned
     *
     * @param cursor Cursor to bound
     * @param max Maximum bound
     * @return Cursor if within bounds, otherwise ZERO/max are returned
     */
    public default Cursor bound(Cursor cursor, Cursor max) {
      return bound(cursor, ZERO, max);
    }

    /**
     * Non-destructive equivalent of constrain. Returns
     * min if this cursor is before the min-max range,
     * and max if this cursor is after the min-max range.
     * Otherwise the cursor itself is returned
     *
     * @param cursor Cursor to bound
     * @param min Minimum bound
     * @param max Maximum bound
     * @return Cursor if within bounds, otherwise min/max are returned
     */
    public default Cursor bound(Cursor cursor, Cursor min, Cursor max) {
      if (isBefore(cursor, min)) {
        return min;
      }
      if (isAfter(cursor, max)) {
        return max;
      }
      return cursor;
    }

    /**
     * Snap this cursor's value to the nearest interval
     *
     * @param clip Clip context
     * @param snapSize Snap interval
     * @return Cursor, updated
     */
    public Cursor snap(Cursor cursor, LXClip clip, Cursor snapSize);

    /**
     * Snap this cursor's value to the nearest interval, strictly at this point or before
     *
     * @param clip Clip context
     * @param snapSize Snap interval
     * @return Cursor, updated
     */
    public Cursor snapFloor(Cursor cursor, LXClip clip, Cursor snapSize);

    /**
     * Snap this cursor's value up to the next interval
     *
     * @param clip Clip context
     * @param snapSize Snap interval
     * @return Cursor, updated
     */
    public Cursor snapUp(Cursor cursor, LXClip clip, Cursor snapSize);

    /**
     * Snap this cursor's value down to the previous interval
     *
     * @param clip Clip context
     * @param snapSize Snap interval
     * @return Cursor, updated
     */
    public Cursor snapDown(Cursor cursor, LXClip clip, Cursor snapSize);

    /**
     * Apply a delta to the given cursor, limiting the result to not go below 0
     *
     * @param cursor Input cursor
     * @param delta Delta to apply
     * @param add True to add, false to subtract
     * @return New cursor with operation applied, non-negative
     */
    public default Cursor applyDelta(Cursor cursor, Cursor delta, boolean add) {
      if (!add) {
        delta = bound(delta, cursor);
      }
      return cursor.inc(delta, add);
    }

    /**
     * Format a cursor to a user-displayable string
     *
     * @param clip Clip
     * @param cursor Cursor
     * @param labelSpacing Label spacing
     * @return User-displayable string
     */
    public String formatLabel(LXClip clip, Cursor cursor, Cursor labelSpacing);
  }

  private static final Operator ABSOLUTE_OPERATOR = new Operator() {

    @Override
    public boolean isZero(Cursor c) {
      return c.millis == 0;
    }

    @Override
    public boolean isBefore(Cursor c1, Cursor c2) {
      return c1.millis < c2.millis;
    }

    @Override
    public boolean isBeforeOrEqual(Cursor c1, Cursor c2) {
      return c1.millis <= c2.millis;
    }

    @Override
    public boolean isAfter(Cursor c1, Cursor c2) {
      return c1.millis > c2.millis;
    }

    @Override
    public boolean isAfterOrEqual(Cursor c1, Cursor c2) {
      return c1.millis >= c2.millis;
    }

    @Override
    public boolean isEqual(Cursor c1, Cursor c2) {
      return c1.millis == c2.millis;
    }

    @Override
    public boolean isInRange(Cursor c, Cursor before, Cursor after) {
      return LXUtils.inRange(c.millis, before.millis, after.millis);
    }

    @Override
    public double getRatio(Cursor c1, Cursor c2) {
      if (isZero(c2)) {
        return 0;
      }
      return c1.millis / c2.millis;
    }

    @Override
    public double getLerpFactor(Cursor cursor, Cursor pre, Cursor post) {
      if (pre.millis == post.millis) {
        return 0;
      }
      return (cursor.millis - pre.millis) / (post.millis - pre.millis);
    }

    @Override
    public double getLerpRatio(Cursor cursor, Cursor pre, Cursor post) {
      if (pre.millis == post.millis) {
        return 1;
      }
      return cursor.millis / (post.millis - pre.millis);
    }

    @Override
    public int compare(Cursor o1, Cursor o2) {
      if (o1.millis == o2.millis) {
        return 0;
      }
      return (o1.millis < o2.millis) ? -1 : 1;
    }

    private void _setBeatCountBasisFromMillis(Cursor cursor, LXClip clip) {
      cursor._setBeatCountBasis(cursor.millis * clip.referenceBpm.getValue() / 60000);
    }

    @Override
    public Cursor snap(Cursor cursor, LXClip clip, Cursor snapSize) {
      double multiple = Math.round(cursor.millis / snapSize.millis);
      cursor.millis = multiple * snapSize.millis;
      _setBeatCountBasisFromMillis(cursor, clip);
      return cursor;
    }

    @Override
    public Cursor snapFloor(Cursor cursor, LXClip clip, Cursor snapSize) {
      double multiple = Math.floor(cursor.millis / snapSize.millis);
      cursor.millis = multiple * snapSize.millis;
      _setBeatCountBasisFromMillis(cursor, clip);
      return cursor;
    }

    @Override
    public Cursor snapUp(Cursor cursor, LXClip clip, Cursor snapSize) {
      double snapUnits = cursor.millis / snapSize.millis;
      double decimal = snapUnits - (long) snapUnits;
      if ((decimal < SNAP_THRESHOLD) || ((1 - decimal) < SNAP_THRESHOLD)) {
        // It's at the top
        cursor.millis = (Math.ceil(snapUnits) + 1) * snapSize.millis;
      } else {
        cursor.millis = Math.ceil(snapUnits) * snapSize.millis;
      }
      _setBeatCountBasisFromMillis(cursor, clip);
      return cursor;
    }

    @Override
    public Cursor snapDown(Cursor cursor, LXClip clip, Cursor snapSize) {
      double snapUnits = cursor.millis / snapSize.millis;
      double decimal = snapUnits - (long) snapUnits;
      if (decimal < SNAP_THRESHOLD) {
        // It's at the bottom
        double multiple = LXUtils.max(0, Math.floor(snapUnits) - 1);
        cursor.millis = multiple * snapSize.millis;
      } else {
        cursor.millis = Math.floor(snapUnits) * snapSize.millis;
      }
      _setBeatCountBasisFromMillis(cursor, clip);
      return cursor;
    }

    @Override
    public String formatLabel(LXClip clip, Cursor cursor, Cursor labelSpacing) {
      int seconds = (int) (cursor.millis / 1000.);
      int minutes = seconds / 60;
      seconds -= 60*minutes;
      if (labelSpacing.getMillis() < 1000) {
        return String.format("%d:%02d:%03d", minutes, seconds, (int) (cursor.millis % 1000));
      } else {
        return String.format("%d:%02d", minutes, seconds);
      }
    }

  };

  private static final Operator TEMPO_OPERATOR = new Operator() {

    @Override
    public boolean isZero(Cursor c) {
      return (c.beatCount == 0) && (c.beatBasis == 0);
    }

    @Override
    public boolean isBefore(Cursor c1, Cursor c2) {
      return (c1.beatCount + c1.beatBasis) < (c2.beatCount + c2.beatBasis);
    }

    @Override
    public boolean isBeforeOrEqual(Cursor c1, Cursor c2) {
      return (c1.beatCount + c1.beatBasis) <= (c2.beatCount + c2.beatBasis);
    }

    @Override
    public boolean isAfter(Cursor c1, Cursor c2) {
      return (c1.beatCount + c1.beatBasis) > (c2.beatCount + c2.beatBasis);
    }

    @Override
    public boolean isAfterOrEqual(Cursor c1, Cursor c2) {
      return (c1.beatCount + c1.beatBasis) >= (c2.beatCount + c2.beatBasis);
    }

    @Override
    public boolean isEqual(Cursor c1, Cursor c2) {
      return (c1.beatCount + c1.beatBasis) == (c2.beatCount + c2.beatBasis);
    }

    @Override
    public boolean isInRange(Cursor c, Cursor before, Cursor after) {
      return LXUtils.inRange(c.beatCount + c.beatBasis, before.beatCount + before.beatBasis, after.beatCount + after.beatBasis);
    }

    @Override
    public double getRatio(Cursor c1, Cursor c2) {
      if (isZero(c2)) {
        return 0;
      }
      return (c1.beatCount + c1.beatBasis) / (c2.beatCount + c2.beatBasis);
    }

    @Override
    public double getLerpFactor(Cursor cursor, Cursor pre, Cursor post) {
      final double preBeatBasis = pre.beatCount + pre.beatBasis;
      final double postBeatBasis = post.beatCount + post.beatBasis;
      if (preBeatBasis == postBeatBasis) {
        return 0;
      }
      return (cursor.beatCount + cursor.beatBasis - preBeatBasis) / (postBeatBasis - preBeatBasis);
    }

    @Override
    public double getLerpRatio(Cursor cursor, Cursor pre, Cursor post) {
      final double preBeatBasis = pre.beatCount + pre.beatBasis;
      final double postBeatBasis = post.beatCount + post.beatBasis;
      if (preBeatBasis == postBeatBasis) {
        return 1;
      }
      return (cursor.beatCount + cursor.beatBasis) / (postBeatBasis - preBeatBasis);
    }

    @Override
    public int compare(Cursor c1, Cursor c2) {
      double basis1 = c1.beatCount + c1.beatBasis;
      double basis2 = c2.beatCount + c2.beatBasis;
      if (basis1 == basis2) {
        return 0;
      }
      return (basis1 < basis2) ? -1 : 1;
    }

    private void _setMillisFromTempo(Cursor cursor, LXClip clip) {
      double millisPerBeat = 60000 / clip.referenceBpm.getValue();
      cursor.millis = (cursor.beatCount + cursor.beatBasis) * millisPerBeat;
    }

    @Override
    public Cursor snap(Cursor cursor, LXClip clip, Cursor snapSize) {
      final double snapBeatBasis = (snapSize.beatCount + snapSize.beatBasis);
      final double multiple = Math.round((cursor.beatCount + cursor.beatBasis) / snapBeatBasis);
      cursor._setBeatCountBasis(multiple * snapBeatBasis);
      _setMillisFromTempo(cursor, clip);
      return cursor;
    }

    @Override
    public Cursor snapFloor(Cursor cursor, LXClip clip, Cursor snapSize) {
      final double snapBeatBasis = (snapSize.beatCount + snapSize.beatBasis);
      final double multiple = Math.floor((cursor.beatCount + cursor.beatBasis) / snapBeatBasis);
      cursor._setBeatCountBasis(multiple * snapBeatBasis);
      _setMillisFromTempo(cursor, clip);
      return cursor;
    }

    @Override
    public Cursor snapUp(Cursor cursor, LXClip clip, Cursor snapSize) {
      final double snapBeatBasis = (snapSize.beatCount + snapSize.beatBasis);
      final double snapUnits = (cursor.beatCount + cursor.beatBasis) / snapBeatBasis;
      final double decimal = snapUnits - (long) snapUnits;
      double multiple = Math.ceil(snapUnits);
      if ((decimal < SNAP_THRESHOLD) || ((1 - decimal) < SNAP_THRESHOLD)) {
        // It's at the top
        multiple += 1;
      }
      cursor._setBeatCountBasis(multiple * snapBeatBasis);
      _setMillisFromTempo(cursor, clip);
      return cursor;
    }

    @Override
    public Cursor snapDown(Cursor cursor, LXClip clip, Cursor snapSize) {
      final double snapBeatBasis = (snapSize.beatCount + snapSize.beatBasis);
      final double snapUnits = (cursor.beatCount + cursor.beatBasis) / snapBeatBasis;
      final double decimal = snapUnits - (long) snapUnits;
      double multiple = Math.floor(snapUnits);
      if (decimal < SNAP_THRESHOLD) {
        // It's at the bottom
        multiple = LXUtils.max(0, multiple - 1);
      }
      cursor._setBeatCountBasis(multiple * snapBeatBasis);
      _setMillisFromTempo(cursor, clip);
      return cursor;
    }

    @Override
    public String formatLabel(LXClip clip, Cursor cursor, Cursor labelSpacing) {
      final int beatsPerBar = clip.getLX().engine.tempo.beatsPerBar.getValuei();
      final int bars = 1 + (cursor.beatCount / beatsPerBar);
      if (labelSpacing.getBeatCount() < beatsPerBar) {
        final int beats = 1 + (cursor.beatCount % beatsPerBar);
        final int sixteenths = 1 + (int) (cursor.beatBasis / .25);
        if (sixteenths > 1) {
          return String.format("%d.%d.%d", bars, beats, sixteenths);
        } else {
          return String.format("%d.%d", bars, beats);
        }
      } else {
        return String.format("%d", bars);
      }
    }

  };

  /**
   * A reference time-base used for cursor timestamps
   */
  public enum TimeBase {
    /**
     * Absolute time, measured in wall-clock milliseconds
     */
    ABSOLUTE("Absolute", ABSOLUTE_OPERATOR),

    /**
     * Tempo-based time, measured in a discrete beat count and
     * a sub-beat division normalized to the range [0,1)
     */
    TEMPO("Tempo", TEMPO_OPERATOR);

    public final String label;

    /**
     * The Cursor.Operator implementation to be used for this TimeBase
     */
    public final Operator operator;

    private TimeBase(String label, Operator operations) {
      this.label = label;
      this.operator = operations;
    }

    @Override
    public String toString() {
      return this.label;
    }
  };

  private double millis = 0;
  private int beatCount = 0;
  private double beatBasis = 0;

  protected void assertMutable() {}

  public Cursor() {}

  protected Cursor(double millis, int beatCount, double beatBasis) {
    _set(millis, beatCount, beatBasis);
  }

  private Cursor(Cursor that) {
    set(that);
  }

  @Override
  public Cursor clone() {
    return new Cursor(this);
  }

  /**
   * Return an immutable version of this cursor, typically a copy unless the
   * cursor is already itself immutable.
   *
   * @return Immutable version of this cursor
   */
  public Cursor.Immutable immutable() {
    return (this instanceof Immutable) ? (Immutable) this : new Immutable(null, this.millis, this.beatCount, this.beatBasis);
  }

  /**
   * Gets the absolute time millisecond timestamp of this cursor
   *
   * @return Absolute time of this cursor in milliseconds
   */
  public double getMillis() {
    return this.millis;
  }

  /**
   * Gets the discrete beat-count of this cursor, greater than or equal to 0
   *
   * @return Beat count, non-negative
   */
  public int getBeatCount() {
    return this.beatCount;
  }

  /**
   * Gets the position of the cursor within a beat, bounded in the range [0,1)
   *
   * @return Position of the cursor within the beat
   */
  public double getBeatBasis() {
    return this.beatBasis;
  }

  /**
   * Constrain the position of this cursor to the length of the clip. This is
   * a potentially destructive operation which may modify the value if this
   * cursor.
   *
   * @param clip Clip to constrain the cursor to
   * @return This cursor, for method chaining
   */
  public Cursor constrain(LXClip clip) {
    return clip.CursorOp().constrain(this, clip);
  }

  /**
   * Non-destructive version of constrain, bounds the cursor by the length
   * of the given clip. If this cursor is out of bounds, then either Cursor.ZERO
   * or clip.length.cursor will be returned instead.
   *
   * @param clip Clip to bound the cursor to
   * @return This cursor if in bounds, otherwise clip.length.cursor
   */
  public Cursor bound(LXClip clip) {
    return clip.CursorOp().bound(this, clip);
  }

  /**
   * Reset the cursor to zero
   */
  public void reset() {
    this.millis = 0;
    this.beatCount = 0;
    this.beatBasis = 0;
  }

  private void _setBeatCountBasis(double beatCountBasis) {
    this.beatCount = (int) beatCountBasis;
    this.beatBasis = beatCountBasis % 1.;
  }

  private void _set(double millis, int beatCount, double beatBasis) {
    if (millis < 0 || beatCount < 0 || beatBasis < 0) {
      throw new IllegalArgumentException("May not set Cursor with negative value: " + millis + "/" + beatCount + "/" + beatBasis);
    }
    this.millis = millis;
    this.beatCount = beatCount;
    this.beatBasis = beatBasis;
  }

  /**
   * Set the value of the cursor to the value held by another
   *
   * @param that Other cursor
   * @return This cursor, updated
   */
  public Cursor set(Cursor that) {
    assertMutable();
    this.millis = that.millis;
    this.beatCount = that.beatCount;
    this.beatBasis = that.beatBasis;
    return this;
  }

  /**
   * Set the value of this cursor to the interpolation of two cursors
   *
   * @param from Source cursor
   * @param to Target cursor
   * @param scale Interpolation factor
   * @return This cursor, modified, for method chaining
   */
  public Cursor setLerp(Cursor from, Cursor to, double scale) {
    assertMutable();
    this.millis = LXUtils.lerp(from.millis, to.millis, scale);
    _setBeatCountBasis(LXUtils.lerp(from.beatCount + from.beatBasis, to.beatCount + to.beatBasis, scale));
    return this;
  }

  /**
   * Creates a new cursor that's a copy of this cursor scaled by the given factor
   *
   * @param factor Time scaling factor
   * @return New cursor scaled by the given amount
   */
  public Cursor scale(double factor) {
    if (factor < 0) {
      throw new IllegalArgumentException("May not scale Cursor by negative factor");
    }
    Cursor that = clone();
    that.millis = this.millis * factor;
    that._setBeatCountBasis((this.beatCount + this.beatBasis) * factor);
    return that;
  }

  /**
   * Creates a cursor that is an integer set of multiples of this cursor
   *
   * @param factor Multiples of this cursor
   * @return New cursor scaled by the number of multiples
   */
  public Cursor scaleInt(int factor) {
    if (factor < 0) {
      throw new IllegalArgumentException("May not scale Cursor by negative factor");
    }
    Cursor that = clone();
    that.millis = this.millis * factor;
    that.beatCount = this.beatCount * factor;
    double beatBasis = this.beatBasis * factor;
    that.beatCount += (int) beatBasis;
    that.beatBasis = beatBasis % 1.;
    return that;
  }

  /**
   * Destructively modifies this cursor, advancing it by the amount specified
   *
   * @param that Cursor specifying amount to advance
   * @return This cursor, modified
   */
  public Cursor advance(Cursor that) {
    return _add(that);
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
    if (this.beatBasis >= 1) {
      ++this.beatCount;
      this.beatBasis = this.beatBasis % 1.;
    }
    return this;
  }

  /**
   * Increments a new cursor that adds or subtracts the two, bounded to zero minimum
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
    if (this.millis < 0) {
      LX.error(new Exception("Bogus Cursor subtraction producing a negative: " + this + " - " + that));
      this.millis = 0;
    }
    if (this.beatCount < 0 || this.beatBasis < 0) {
      LX.error(new Exception("Bogus Cursor subtraction producing a negative: " + this + " - " + that));
      this.beatCount = 0;
      this.beatBasis = 0;
    }
    return this;
  }

  /**
   * Returns the absolute time difference between these cursors
   *
   * @param that Other cursor
   * @return Millisecond difference between these two cursors
   */
  public double getDeltaMillis(Cursor that) {
    return this.millis - that.millis;
  }

  @Deprecated
  Cursor setMillis(double millis) {
    // TODO(clips): infer the correct beatCount and beatBasis
    this.millis = millis;
    return this;
  }

  @Override
  public String toString() {
    return String.format("%.2f/%d/%.2f", this.millis, this.beatCount, this.beatBasis);
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

  public static class Immutable extends Cursor {

    private final String name;

    private Immutable(String name, double millis) {
      this(name, millis, 0, 0);
    }

    private Immutable(String name, double millis, int beatCount, double beatBasis) {
      super(millis, beatCount, beatBasis);
      this.name = name;
    }

    @Override
    protected void assertMutable() {
      throw new UnsupportedOperationException("Cannot modify Immutable cursor: " + this.name);
    }
  }

}