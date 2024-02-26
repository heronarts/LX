/**
 * Copyright 2020- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXLoopTask;
import heronarts.lx.LXSerializable;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.TriggerParameter;

/**
 * A swatch is a set of up to 5 dynamic colors that can be referenced by patterns and effects.
 */
public class LXSwatch extends LXComponent implements LXLoopTask, LXOscComponent, LXComponent.Renamable {

  public interface Listener {
    public void colorAdded(LXSwatch swatch, LXDynamicColor color);
    public void colorRemoved(LXSwatch swatch, LXDynamicColor color);
  }

  private final List<Listener> listeners = new ArrayList<Listener>();

  public static final int MAX_COLORS = 5;

  private final List<LXDynamicColor> mutableColors = new ArrayList<LXDynamicColor>();

  public final List<LXDynamicColor> colors = Collections.unmodifiableList(this.mutableColors);

  public final TriggerParameter recall =
    new TriggerParameter("Recall", () -> { this.lx.engine.palette.setSwatch(this); })
    .setDescription("Restores the values of this swatch");

  public final BooleanParameter autoCycleEligible =
    new BooleanParameter("Cycle", true)
    .setDescription("Whether the swatch is eligible for auto-cycle");

  private int index = 0;

  public LXSwatch(LX lx) {
    super(lx, "Swatch");
    this.mutableColors.add(new LXDynamicColor(this));
    addArray("color", this.colors);
    addParameter("recall", this.recall);
    addParameter("autoCycleEligible", this.autoCycleEligible);
  }

  LXSwatch(LXPalette palette, boolean setParent) {
    this(palette.getLX());
    if (setParent) {
      setParent(palette);
    }
  }

  static LXSwatch staticCopy(LXSwatch that) {
    LXSwatch swatch = new LXSwatch(that.lx);
    swatch.mutableColors.clear();
    for (LXDynamicColor color : that.colors) {
      LXDynamicColor c2 = new LXDynamicColor(swatch);
      c2.primary.setColor(color.getColor());
      swatch.mutableColors.add(c2);
    }
    return swatch;
  }

  void setIndex(int index) {
    this.index = index;
  }

  public int getIndex() {
    return this.index;
  }

  @Override
  public String getPath() {
    String path = super.getPath();
    if (path != null) {
      return path;
    }
    return "swatches/" + (this.index + 1);
  }

  private void _reindexColors() {
    int i = 0;
    for (LXDynamicColor color : this.colors) {
      color.setIndex(i++);
    }
  }

  public void loop(double deltaMs) {
    for (LXDynamicColor color : this.colors) {
      color.loop(deltaMs);
    }
  }

  /**
   * Retrieves the color at a given index in the swatch.
   * If this swatch does not specify a color at that index,
   * then the last valid color is returned instead.
   *
   * @param index Index
   * @return Dynamic color at that index
   */
  public LXDynamicColor getColor(int index) {
    if (index >= this.colors.size()) {
      return this.colors.get(this.colors.size() - 1);
    }
    return this.colors.get(index);
  }

  /**
   * Adds a new dynamic color to the swatch
   *
   * @return The newly added color
   */
  public LXDynamicColor addColor() {
    final int initialColor = this.colors.get(this.colors.size() - 1).getColor();
    return addColor(new LXDynamicColor(this, initialColor), -1);
  }

  public LXDynamicColor addColor(int index, JsonObject colorObj) {
    LXDynamicColor newColor = new LXDynamicColor(this);
    newColor.load(this.lx, colorObj);
    return addColor(newColor, index);
  }

  private LXDynamicColor addColor(LXDynamicColor color, int index) {
    if (index == 0 || index >= MAX_COLORS) {
      throw new IllegalArgumentException("Cannot add color at invalid index: " + index);
    }
    if (this.mutableColors.size() >= MAX_COLORS) {
      throw new IllegalStateException("Cannot add more than " + MAX_COLORS + " to a swatch.");
    }
    if (index < 0) {
      this.mutableColors.add(color);
    } else {
      this.mutableColors.add(index, color);
    }
    _reindexColors();
    for (Listener listener : this.listeners) {
      listener.colorAdded(this, color);
    }
    return color;
  }

  /**
   * Removes the last color from the swatch
   *
   * @return The removed color
   */
  public LXDynamicColor removeColor() {
    if (this.mutableColors.isEmpty()) {
      throw new IllegalStateException("Cannot remove color from empty swatch");
    }
    return removeColor(this.mutableColors.size() - 1);
  }

  /**
   * Removes a specific color from the swatch
   *
   * @param color The color to remove
   * @return The removed color
   */
  public LXDynamicColor removeColor(LXDynamicColor color) {
    Objects.requireNonNull(color, "Cannot LXSwatch.removeColor(null)");
    int index = this.colors.indexOf(color);
    if (index < 0) {
      throw new IllegalStateException("Cannot remove color that does not exist in swatch");
    }
    return removeColor(index);
  }

  /**
   * Removes the color at a specific index from the swatch
   *
   * @param index Index to remove
   * @return Color removed
   */
  public LXDynamicColor removeColor(int index) {
    if (index < 0 || index >= this.colors.size()) {
      throw new IllegalStateException("Cannot remove color at invalid index: " + index);
    }
    if (index == 0) {
      throw new IllegalStateException("Cannot remove first color from a swatch");
    }
    LXDynamicColor color = this.mutableColors.remove(index);
    _reindexColors();
    for (Listener listener : this.listeners) {
      listener.colorRemoved(this, color);
    }
    LX.dispose(color);
    return color;
  }

  /**
   * Registers a listener to the swatch
   *
   * @param listener Swatch listener
   * @return this
   */
  public LXSwatch addListener(Listener listener) {
    Objects.requireNonNull(listener);
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate LXSwatch.Listener: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  /**
   * Unregisters a listener to the swatch
   *
   * @param listener Swatch listener
   * @return this
   */
  public LXSwatch removeListener(Listener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("May not remove non-registered LXSwatch.Listener: " + listener);
    }
    this.listeners.remove(listener);
    return this;
  }

  private static final String KEY_COLORS = "colors";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_COLORS, LXSerializable.Utils.toArray(lx, this.colors));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    super.load(lx, obj);
    int ci = 0;
    if (obj.has(KEY_COLORS)) {
      JsonArray colorsArr = obj.get(KEY_COLORS).getAsJsonArray();
      for (JsonElement colorElem : colorsArr) {
        JsonObject colorObj = colorElem.getAsJsonObject();
        if (ci >= this.colors.size()) {
          LXDynamicColor color = new LXDynamicColor(this);
          color.load(lx, colorObj);
          addColor(color, -1);
        } else {
          // Just load into existing color object
          this.colors.get(ci).load(lx, colorObj);
        }
        ++ci;
      }
    } else {
      ci = 1;
    }
    while (this.colors.size() > ci) {
      removeColor();
    }
    for (LXDynamicColor color : this.colors) {
      color.trigger();
    }
  }

  @Override
  public void dispose() {
    for (LXDynamicColor color : this.colors) {
      LX.dispose(color);
    }
    this.mutableColors.clear();
    super.dispose();
  }

}
