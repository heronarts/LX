package heronarts.lx;

import com.google.gson.JsonObject;

/**
 * Helper class for storing position (x,y) and size (width,height) of a window or monitor.
 */
public class DisplaySettings implements LXSerializable {

  private static final int DEFAULT = -1;

  private int x = DEFAULT;
  private int y = DEFAULT;
  private int width = DEFAULT;
  private int height = DEFAULT;

  private boolean hasPosition = false;
  private boolean hasSize = false;

  public DisplaySettings() {
  }

  public DisplaySettings(int width, int height) {
    setSize(width, height);
  }

  public DisplaySettings(int x, int y, int width, int height) {
    setPosition(x, y);
    setSize(width, height);
  }

  public DisplaySettings setPosition(int x, int y) {
    this.x = x;
    this.y = y;
    this.hasPosition = true;
    return this;
  }

  public DisplaySettings setSize(int width, int height) {
    this.width = width;
    this.height = height;
    this.hasSize = true;
    return this;
  }

  /**
   * Whether the current settings include a position.
   */
  public boolean hasPosition() {
    return this.hasPosition;
  }

  /**
   * Whether the current settings include a size.
   */
  public boolean hasSize() {
    return this.hasSize;
  }

  /**
   * Position (x) of the left edge of the display, in screen pixels.
   */
  public int getX() {
    return this.x;
  }

  /**
   * Position (y) of the top edge of the display, in screen pixels.
   */
  public int getY() {
    return this.y;
  }

  /**
   * Width in screen pixels. Does not include UI scaling.
   */
  public int getWidth() {
    return this.width;
  }

  /**
   * Height in screen pixels. Does not include UI scaling.
   */
  public int getHeight() {
    return this.height;
  }

  /**
   * Position (x) of the right edge of the display, in screen pixels.
   */
  public int getXmax() {
    return this.x + this.width;
  }

  /**
   * Position (y) of the bottom edge of the display, in screen pixels.
   */
  public int getYmax() {
    return this.y + this.height;
  }

  /**
   * Clear position and size settings.
   */
  public DisplaySettings reset() {
    resetPosition();
    resetSize();
    return this;
  }

  /**
   * Clear position setting.
   */
  public DisplaySettings resetPosition() {
    this.x = -1;
    this.y = -1;
    this.hasPosition = false;
    return this;
  }

  /**
   * Clear size setting.
   */
  public DisplaySettings resetSize() {
    this.width = -1;
    this.height = -1;
    this.hasSize = false;
    return this;
  }

  public boolean equals(DisplaySettings that) {
    if (this.hasPosition != that.hasPosition) {
      return false;
    }
    if (this.hasPosition) {
      if (this.x != that.x || this.y != that.y) {
        return false;
      }
    }
    if (this.hasSize != that.hasSize) {
      return false;
    }
    if (this.hasSize) {
      if (this.width != that.width || this.height != that.height) {
        return false;
      }
    }
    return true;
  }

  /**
   * Determines if a set of (x,y) coordinates are within the display bounds.
   * @param x X-value to check
   * @param y Y-value to check
   * @return True if the coordinates are within the display bounds
   */
  public boolean contains(int x, int y) {
    if (!this.hasPosition || !this.hasSize) {
      return false;
    }
    return x >= this.x && x < (this.x + this.width) &&
      y >= this.y && y < (this.y + this.height);
  }

  @Override
  public String toString() {
    return toSizeString() + ", " + toPositionString();
  }

  public String toPositionString() {
    return this.hasPosition ? "pos(" + this.x + "," + this.y + ")" : "pos(n/a)";
  }

  public String toSizeString() {
    return this.hasSize ? "size(" + this.width + "x" + this.height + ")" : "size(n/a)";
  }

  private static final String KEY_X = "x";
  private static final String KEY_Y = "y";
  private static final String KEY_WIDTH = "width";
  private static final String KEY_HEIGHT = "height";

  @Override
  public void load(LX lx, JsonObject object) {
    if (object.has(KEY_WIDTH) && object.has(KEY_HEIGHT)) {
      int width = object.get(KEY_WIDTH).getAsInt();
      int height = object.get(KEY_HEIGHT).getAsInt();
      setSize(width, height);
    } else {
      resetSize();
    }
    if (object.has(KEY_X) && object.has(KEY_Y)) {
      int x = object.get(KEY_X).getAsInt();
      int y = object.get(KEY_Y).getAsInt();
      setPosition(x, y);
    } else {
      resetPosition();
    }
  }

  @Override
  public void save(LX lx, JsonObject object) {
    if (hasPosition()) {
      object.addProperty(KEY_X, this.x);
      object.addProperty(KEY_Y, this.y);
    }
    if (hasSize()) {
      object.addProperty(KEY_WIDTH, this.width);
      object.addProperty(KEY_HEIGHT, this.height);
    }
  }
}
