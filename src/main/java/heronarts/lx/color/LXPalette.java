/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
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

/**
 * A palette is an object that is used to keep track of top-level color values and
 * set modes of color computation. Though its use is not required, it is very useful for
 * creating coherent color schemes across patterns.
 */
public class LXPalette extends LXComponent implements LXLoopTask, LXOscComponent {

  public interface Listener {
    public void swatchAdded(LXPalette palette, LXSwatch swatch);
    public void swatchRemoved(LXPalette palette, LXSwatch swatch);
    public void swatchMoved(LXPalette palette, LXSwatch swatch);
  }

  private final List<Listener> listeners = new ArrayList<Listener>();

  private final List<LXSwatch> mutableSwatches = new ArrayList<LXSwatch>();

  /**
   * A read-only list of all the saved color swatches
   */
  public final List<LXSwatch> swatches = Collections.unmodifiableList(this.mutableSwatches);

  /**
   * The active color swatch
   */
  public final LXSwatch swatch;

  public final LXDynamicColor color;

  public LXPalette(LX lx) {
    super(lx, "Color Palette");
    addChild("swatch", this.swatch = new LXSwatch(this, false));
    addArray("swatches", this.swatches);
    this.color = swatch.colors.get(0);
  }

  /**
   * Gets the primary color of the currently active swatch
   *
   * @return Primary color of active swatch
   */
  public int getColor() {
    return this.color.getColor();
  }

  /**
   * Gets the hue of the primary color in active swatch
   *
   * @return Hue of primary color
   */
  public float getHuef() {
    return (float) getHue();
  }

  /**
   * Gets the hue of the primary color in active swatch
   *
   * @return Hue of primary color
   */
  public double getHue() {
    return LXColor.h(getColor());
  }

  /**
   * Gets the saturation of the primary color in active swatch
   *
   * @return Saturation of primary color
   */
  public float getSaturationf() {
    return (float) getSaturation();
  }

  /**
   * Gets the saturation of the primary color in active swatch
   *
   * @return Saturation of primary color
   */
  public double getSaturation() {
    return LXColor.s(getColor());
  }


  /**
   * Gets the brightness of the primary color in active swatch
   *
   * @return Brightness of primary color
   */
  public float getBrightnessf() {
    return (float) getBrightness();
  }

  /**
   * Gets the brightness of the primary color in active swatch
   *
   * @return Brightness of primary color
   */
  public double getBrightness() {
    return LXColor.b(getColor());
  }

  private void _reindexSwatches() {
    int i = 0;
    for (LXSwatch swatch: this.swatches) {
      swatch.setIndex(i++);
    }
  }

  /**
   * Saves the current swatch to the list of saved swatches
   *
   * @return Saved swatch, added to swatch list
   */
  public LXSwatch saveSwatch() {
    LXSwatch saved = new LXSwatch(this, true);
    JsonObject savedObj = LXSerializable.Utils.toObject(this.lx, this.swatch);
    saved.load(this.lx, LXSerializable.Utils.stripIds(savedObj));
    saved.label.setValue("Swatch-" + (this.swatches.size() + 1));
    return addSwatch(saved, -1);
  }

  /**
   * Adds a swatch at the given index
   *
   * @param swatchObj Saved swatch object
   * @param index Index to save at
   * @return Swatch object
   */
  public LXSwatch addSwatch(JsonObject swatchObj, int index) {
    LXSwatch saved = new LXSwatch(this, true);
    saved.load(getLX(), swatchObj);
    return addSwatch(saved, index);
  }

  private LXSwatch addSwatch(LXSwatch swatch, int index) {
    if (index < 0) {
      this.mutableSwatches.add(swatch);
    } else {
      this.mutableSwatches.add(index, swatch);
    }
    _reindexSwatches();
    for (Listener listener : this.listeners) {
      listener.swatchAdded(this, swatch);
    }
    return swatch;
  }

  /**
   * Removes a swatch from the color palette's saved swatch list
   *
   * @param swatch Swatch to remove
   * @return this
   */
  public LXPalette removeSwatch(LXSwatch swatch) {
    if (!this.swatches.contains(swatch)) {
      throw new IllegalStateException("Cannot remove swatch not in palette: " + swatch);
    }
    this.mutableSwatches.remove(swatch);
    _reindexSwatches();
    for (Listener listener : this.listeners) {
      listener.swatchRemoved(this, swatch);
    }
    swatch.dispose();
    return this;
  }

  public LXPalette setSwatch(LXSwatch swatch) {
    this.swatch.load(this.lx, LXSerializable.Utils.stripIds(LXSerializable.Utils.toObject(swatch)));
    return this;
  }

  public void loop(double deltaMs) {
    this.swatch.loop(deltaMs);
    // NOTE(mcslee): do we want to run the saved swatches here? maybe if
    // one is selected/focused and being worked on?
  }

  /**
   * Moves a saved swatch to a different position in the list
   *
   * @param swatch Saved swatch
   * @param index New index for that swatch
   * @return this
   */
  public LXPalette moveSwatch(LXSwatch swatch, int index) {
    if (index < 0 || index >= this.mutableSwatches.size()) {
      throw new IllegalArgumentException("Cannot move swatch to invalid index: " + index);
    }
    this.mutableSwatches.remove(swatch);
    this.mutableSwatches.add(index, swatch);
    _reindexSwatches();
    for (Listener listener : this.listeners) {
      listener.swatchMoved(this, swatch);
    }
    return this;
  }

  /**
   * Registers a listener to the palette
   *
   * @param listener Palette listener
   * @return this
   */
  public LXPalette addListener(Listener listener) {
    Objects.requireNonNull(listener);
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate LXPalette.Listener: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  /**
   * Unregisters a listener to the palette
   *
   * @param listener Palette listener
   * @return this
   */
  public LXPalette removeListener(Listener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("May not remove non-registered LXPalette.Listener: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  @Override
  public void dispose() {
    for (LXSwatch swatch : this.swatches) {
      swatch.dispose();
    }
    this.mutableSwatches.clear();
    this.swatch.dispose();
    super.dispose();
  }

  private static final String KEY_SWATCHES = "swatches";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_SWATCHES, LXSerializable.Utils.toArray(lx, this.swatches));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    while (!this.swatches.isEmpty()) {
      removeSwatch(this.swatches.get(this.swatches.size() - 1));
    }
    if (obj.has(KEY_SWATCHES)) {
      JsonArray swatchArr = obj.get(KEY_SWATCHES).getAsJsonArray();
      for (JsonElement swatchElem : swatchArr) {
        addSwatch(swatchElem.getAsJsonObject(), -1);
      }
    }
    super.load(lx, obj);
  }

}
