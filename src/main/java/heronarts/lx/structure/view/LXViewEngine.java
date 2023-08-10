/**
 * Copyright 2023- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.structure.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.model.LXModel;
import heronarts.lx.parameter.ObjectParameter;

public class LXViewEngine extends LXComponent implements LX.Listener {

  public interface Listener {
    public void viewAdded(LXViewEngine viewEngine, LXViewDefinition view);
    public void viewRemoved(LXViewEngine viewEngine, LXViewDefinition view);
    public void viewMoved(LXViewEngine viewEngine, LXViewDefinition view);
  }

  private final List<Listener> listeners = new ArrayList<Listener>();

  private final List<LXViewDefinition> mutableViews = new ArrayList<LXViewDefinition>();

  public final List<LXViewDefinition> views = Collections.unmodifiableList(this.mutableViews);

  public LXViewEngine(LX lx) {
    super(lx);
    addArray("view", this.views);
    lx.addListener(this);
  }

  private void rebuildViews() {
    for (LXViewDefinition view : this.views) {
      view.rebuild();
    }
  }

  @Override
  public void modelChanged(LX lx, LXModel model) {
    rebuildViews();
  }

  public void modelGenerationChanged(LX lx, LXModel model) {
    rebuildViews();
  }

  /**
   * Registers a listener to the view engine
   *
   * @param listener View listener
   * @return this
   */
  public LXViewEngine addListener(Listener listener) {
    Objects.requireNonNull(listener);
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate LXViewEngine.Listener: " + listener);
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
  public LXViewEngine removeListener(Listener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("May not remove non-registered LXViewEngine.Listener: " + listener);
    }
    this.listeners.remove(listener);
    return this;
  }

  /**
   * Add a new, uninitialized view
   *
   * @return View that's added
   */
  public LXViewDefinition addView() {
    LXViewDefinition view = new LXViewDefinition(this.lx);
    view.label.setValue("View-" + (views.size() + 1));
    return addView(view, -1);
  }

  /**
   * Adds a view at the given index
   *
   * @param viewObj Saved view object
   * @param index Index to save at
   * @return View object
   */
  public LXViewDefinition addView(JsonObject viewObj, int index) {
    LXViewDefinition saved = new LXViewDefinition(this.lx);
    saved.load(this.lx, viewObj);
    return addView(saved, index);
  }

  /**
   * Add a new view definition to the list
   *
   * @param view View definition
   * @param index List index
   * @return View that was added
   */
  private LXViewDefinition addView(LXViewDefinition view, int index) {
    if (index < 0) {
      this.mutableViews.add(view);
    } else {
      this.mutableViews.add(index, view);
    }
    _reindexViews();
    for (Listener listener : this.listeners) {
      listener.viewAdded(this, view);
    }
    updateSelectors();
    return view;
  }

  /**
   * Removes a view from the view engine's list
   *
   * @param view View to remove
   * @return this
   */
  public LXViewEngine removeView(LXViewDefinition view) {
    if (!this.views.contains(view)) {
      throw new IllegalStateException("Cannot remove view not in LXViewEngine: " + view);
    }
    this.mutableViews.remove(view);
    _reindexViews();
    for (Listener listener : this.listeners) {
      listener.viewRemoved(this, view);
    }
    updateSelectors();
    view.dispose();
    return this;
  }

  /**
   * Moves a saved view to a different position in the list
   *
   * @param view Saved view
   * @param index New index for that view
   * @return this
   */
  public LXViewEngine moveView(LXViewDefinition view, int index) {
    if (index < 0 || index >= this.mutableViews.size()) {
      throw new IllegalArgumentException("Cannot move view to invalid index: " + index);
    }
    this.mutableViews.remove(view);
    this.mutableViews.add(index, view);
    _reindexViews();
    for (Listener listener : this.listeners) {
      listener.viewMoved(this, view);
    }
    updateSelectors();
    return this;
  }

  private void _reindexViews() {
    int i = 0;
    for (LXViewDefinition view : this.views) {
      view.setIndex(i++);
    }
  }

  public void reset() {
    for (int i = this.views.size() - 1; i >= 0; --i) {
      removeView(this.views.get(i));
    }
  }

  private final String DEFAULT_VIEW = "Default";
  private LXViewDefinition[] selectorObjects = { null };
  private String[] selectorOptions = { DEFAULT_VIEW };

  private final List<Selector> selectors = new ArrayList<Selector>();

  void updateSelectors() {
    int numOptions = 1 + this.views.size();
    this.selectorObjects = new LXViewDefinition[numOptions];
    this.selectorOptions = new String[numOptions];
    this.selectorObjects[0] = null;
    this.selectorOptions[0] = DEFAULT_VIEW;

    int i = 1;
    for (LXViewDefinition view : this.views) {
      this.selectorObjects[i] = view;
      this.selectorOptions[i] = view.getLabel();
      ++i;
    }

    // Update all of the selectors to have new range/options
    for (Selector selector : selectors) {
      // Check if a selector had a non-null selection, if so
      // it should be restored in the case of renaming/reordering
      // where it is still in the list but its index may be different
      final LXViewDefinition selected = selector.getObject();
      selector.setObjects(this.selectorObjects, this.selectorOptions);
      if ((selected != null) && this.views.contains(selected)) {
        selector.setValue(selected);
      } else {
        selector.bang();
      }
    }
  }

  public class Selector extends ObjectParameter<LXViewDefinition> {
    public Selector(String label) {
      super(label, selectorObjects, selectorOptions);
      selectors.add(this);
    }

    @Override
    public void dispose() {
      selectors.remove(this);
      super.dispose();
    }
  }

  @Override
  public void dispose() {
    this.lx.removeListener(this);
    super.dispose();
  }

  private static final String KEY_VIEWS = "views";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_VIEWS, LXSerializable.Utils.toArray(lx, this.views));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    reset();
    if (obj.has(KEY_VIEWS)) {
      JsonArray viewArr = obj.get(KEY_VIEWS).getAsJsonArray();
      for (JsonElement swatchElem : viewArr) {
        addView(swatchElem.getAsJsonObject(), -1);
      }
    }
    super.load(lx, obj);
  }
}
