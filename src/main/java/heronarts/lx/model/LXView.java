/**
 * Copyright 2021- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LXView extends LXModel {

  public enum Normalization {
    RELATIVE("Normalize points to view"),
    ABSOLUTE("Preserve absolute normalization");

    public final String description;

    private Normalization(String description) {
      this.description = description;
    }

    @Override
    public String toString() {
      return this.description;
    }
  }

  /**
   * Constructs a view of the given model object
   *
   * @param model Model
   * @param selector View selection string
   * @return A view of the model that selects the elements in the selector string
   */
  public static LXView create(LXModel model, String selector, Normalization normalization) {
    String[] tags = selector.split(" ");
    List<LXModel> submodels = new ArrayList<LXModel>();
    for (String tag : tags) {
      for (LXModel sub : model.sub(tag)) {
        if (!submodels.contains(sub)) {
          submodels.add(sub);
        }
      }
    }

    // Construct a new list of a copy of all the points from all the models
    // in the view. We need a copy because these will all be re-normalized
    // with xn/yn/zn values relative to this view
    Map<Integer, LXPoint> clonedPoints = new HashMap<Integer, LXPoint>();
    List<LXPoint> points = new ArrayList<LXPoint>();
    for (LXModel sub : submodels) {
      for (LXPoint p : sub.points) {
        if (!clonedPoints.containsKey(p.index)) {
          LXPoint copy = new LXPoint(p);
          clonedPoints.put(p.index, copy);
          points.add(copy);
        }
      }
    }

    // Clone all children to reference these unique points
    LXModel[] children = new LXModel[submodels.size()];
    for (int i = 0; i < children.length; ++i) {
      children[i] = cloneModel(clonedPoints, submodels.get(i));
    }

    // Construct a new view which will be normalized against this model scope
    return new LXView(model, normalization, clonedPoints, points, children);
  }

  private static LXModel cloneModel(Map<Integer, LXPoint> clonedPoints, LXModel model) {
    // Re-map points onto new ones
    List<LXPoint> points = new ArrayList<LXPoint>(model.points.length);
    for (LXPoint p : model.points) {
      points.add(clonedPoints.get(p.index));
    }

    // Recursively clone children with new points
    LXModel[] children = new LXModel[model.children.length];
    for (int i = 0; i < children.length; ++i) {
      children[i] = cloneModel(clonedPoints, model.children[i]);
    }

    return new LXModel(points, children, model.metaData, model.tags);
  }

  private final LXModel model;

  final Normalization normalization;

  final Map<Integer, LXPoint> clonedPoints;

  private LXView(LXModel model, Normalization normalization, Map<Integer, LXPoint> clonedPoints, List<LXPoint> points, LXModel[] children) {
    super(points, children, LXModel.Tag.VIEW);
    this.model = model;
    this.normalization = normalization;
    this.clonedPoints = java.util.Collections.unmodifiableMap(clonedPoints);
    model.derivedViews.add(this);
    if (normalization == Normalization.RELATIVE) {
      normalizePoints();
    }
  }

  @Override
  public void dispose() {
    this.model.derivedViews.remove(this);
    super.dispose();
  }

}
