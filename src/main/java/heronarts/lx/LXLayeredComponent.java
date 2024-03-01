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

package heronarts.lx;

import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXPalette;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for system components that run in the engine, which have common
 * attributes, such as parameters, modulators, and layers. For instance,
 * patterns, transitions, and effects are all LXComponents.
 */
public abstract class LXLayeredComponent extends LXModelComponent implements LXLoopTask {

  private LXBuffer buffer = null;

  protected int[] colors = null;

  private final List<LXLayer> mutableLayers = new ArrayList<LXLayer>();

  private final List<LXLayer> removeLayers = new ArrayList<LXLayer>();

  public final List<LXLayer> layers = Collections.unmodifiableList(mutableLayers);

  protected final LXPalette palette;

  protected LXLayeredComponent(LX lx) {
    this(lx, null, (LXBuffer) null);
  }

  protected LXLayeredComponent(LX lx, String label) {
    this(lx, label, (LXBuffer) null);
  }

  protected LXLayeredComponent(LX lx, LXDeviceComponent component) {
    this(lx, null, component.getBuffer());
  }

  protected LXLayeredComponent(LX lx, LXBuffer buffer) {
    this(lx, null, buffer);
  }

  protected LXLayeredComponent(LX lx, String label, LXBuffer buffer) {
    super(lx, label);
    this.palette = lx.engine.palette;
    if (buffer != null) {
      this.buffer = buffer;
      this.colors = buffer.getArray();
    }
    addArray("layer", this.layers);
  }

  protected LXBuffer getBuffer() {
    return this.buffer;
  }

  public int[] getColors() {
    return getBuffer().getArray();
  }

  protected LXLayeredComponent setBuffer(LXDeviceComponent component) {
    return setBuffer(component.getBuffer());
  }

  public LXLayeredComponent setBuffer(LXBuffer buffer) {
    this.buffer = buffer;
    this.colors = buffer.getArray();
    return this;
  }

  private LXLayer loopingLayer = null;

  @Override
  public void loop(double deltaMs) {
    long loopStart = System.nanoTime();

    // This protects against subclasses from inappropriately nuking the colors buffer
    // reference. Even if a doofus assigns colors to something else, we'll reset it
    // here on each pass of the loop. Better than subclasses having to call getColors()
    // all the time.
    this.colors = this.buffer.getArray();

    super.loop(deltaMs);
    onLoop(deltaMs);

    // Run the layers
    try {
      for (LXLayer layer : this.mutableLayers) {
        this.loopingLayer = layer;
        layer.setBuffer(this.buffer);
        layer.setModel(this.model);
        layer.loop(deltaMs);
      }
      this.loopingLayer = null;
    } catch (Throwable x) {
      // NOTE(mcslee): Need to defend against hanging loopingLayer state...
      // otherwise we'll fail to later dispose() this crashed-out object
      this.loopingLayer = null;
      throw x;
    }
    // Remove layers scheduled for deletion
    for (LXLayer layer : this.removeLayers) {
      removeLayer(layer);
    }
    this.removeLayers.clear();

    afterLayers(deltaMs);
    applyEffects(deltaMs);

    this.profiler.loopNanos = System.nanoTime() - loopStart;
  }

  protected /* abstract */ void onLoop(double deltaMs) {}

  protected /* abstract */ void afterLayers(double deltaMs) {}

  protected /* abstract */ void applyEffects(double deltaMs) {}

  private void checkForReentrancy(LXLayer target, String operation) {
    if (this.loopingLayer != null) {
      throw new IllegalStateException(
        "LXLayeredComponent may not modify layers while looping," +
        " component: " + toString() +
        " looping: " + this.loopingLayer.toString(this) +
        " " + operation + ": " + (target != null ? target.toString() : "null")
      );
    }
  }

  private void _reindexLayers() {
    int i = 0;
    for (LXLayer layer : this.layers) {
      layer.setIndex(i++);
    }
  }

  protected final LXLayer addLayer(LXLayer layer) {
    if (layer == null) {
      throw new IllegalArgumentException("Cannot add null layer");
    }
    checkForReentrancy(layer, "add");
    if (this.mutableLayers.contains(layer)) {
      throw new IllegalStateException("Cannot add layer twice: " + this + " " + layer);
    }
    layer.setParent(this);
    this.mutableLayers.add(layer);
    _reindexLayers();
    return layer;
  }

  protected final LXLayer removeLayer(LXLayer layer) {
    if (!this.layers.contains(layer)) {
      throw new IllegalStateException("Cannot remove layer not in component: " + layer);
    }
    if (this.loopingLayer != null) {
      this.removeLayers.add(layer);
      return layer;
    }
    this.mutableLayers.remove(layer);
    _reindexLayers();
    LX.dispose(layer);
    return layer;
  }

  public final List<LXLayer> getLayers() {
    return this.layers;
  }

  @Override
  public void dispose() {
    checkForReentrancy(null, "dispose");
    for (LXLayer layer : this.mutableLayers) {
      LX.dispose(layer);
    }
    this.mutableLayers.clear();
    this.removeLayers.clear();
    super.dispose();
  }

  /**
   * Retrieves the color at index i. This is provided mainly as documentation,
   * but you should not generally use this method. You're better of just indexing directly
   * to this.colors[i] and saving function call overhead.
   *
   * @param i Color index
   * @return Color at that buffer index
   */
  protected int getColor(int i) {
    return this.colors[i];
  }

  /**
   * Retrieves the color at a given point. This is provided mainly as documentation,
   * you should not generally use this method. You're better off just indexing
   * directly by calling this.colors[p.index] and saving the function call overhead
   * for a single point.
   *
   * @param p Point
   * @return Color at that point
   */
  protected int getColor(LXPoint p) {
    return this.colors[p.index];
  }

  /**
   * Sets the color of point i
   *
   * @param i Point index
   * @param c color
   * @return this
   */
  protected final LXLayeredComponent setColor(int i, int c) {
    this.colors[i] = c;
    return this;
  }

  /**
   * Sets the color of a point. This is provided for clarity, but if you're working
   * with large pixel counts you may just skip calling this method and set
   * colors[p.index] to avoid per-pixel function call overhead.
   *
   * @param p Point
   * @param c color
   * @return this
   */
  protected final LXLayeredComponent setColor(LXPoint p, int c) {
    this.colors[p.index] = c;
    return this;
  }

  /**
   * Blend the color at index i with its existing value
   *
   * @param i Index
   * @param c New color
   * @param blendMode blending mode
   *
   * @return this
   */
  protected final LXLayeredComponent blendColor(int i, int c, LXColor.Blend blendMode) {
    this.colors[i] = LXColor.blend(this.colors[i], c, blendMode);
    return this;
  }

  protected final LXLayeredComponent blendColor(LXModel model, int c, LXColor.Blend blendMode) {
    for (LXPoint p : model.points) {
      this.colors[p.index] = LXColor.blend(this.colors[p.index], c, blendMode);
    }
    return this;
  }

  /**
   * Adds to the color of point i, using blendColor with ADD
   *
   * @param i Point index
   * @param c color
   * @return this
   */
  protected final LXLayeredComponent addColor(int i, int c) {
    this.colors[i] = LXColor.add(this.colors[i], c);
    return this;
  }

  /**
   * Adds the color to the fixture
   *
   * @param model model
   * @param c New color
   * @return this
   */
  protected final LXLayeredComponent addColor(LXModel model, int c) {
    for (LXPoint p : model.points) {
      this.colors[p.index] = LXColor.add(this.colors[p.index], c);
    }
    return this;
  }

  /**
   * Subtracts from the color of point i, using blendColor with SUBTRACT
   *
   * @param i Point index
   * @param c color
   * @return this
   */
  protected final LXLayeredComponent subtractColor(int i, int c) {
    this.colors[i] = LXColor.subtract(this.colors[i], c);
    return this;
  }

  /**
   * Sets all points to one color
   *
   * @param c Color
   * @return this
   */
  protected final LXLayeredComponent setColors(int c) {
    for (LXPoint p : model.points) {
      this.colors[p.index] = c;
    }
    return this;
  }

  /**
   * Sets the color of all points in a fixture
   *
   * @param model Model
   * @param c color
   * @return this
   */
  protected final LXLayeredComponent setColor(LXModel model, int c) {
    for (LXPoint p : model.points) {
      this.colors[p.index] = c;
    }
    return this;
  }

  /**
   * Clears all colors
   *
   * @return this
   */
  protected final LXLayeredComponent clearColors() {
    return setColors(0);
  }

}
