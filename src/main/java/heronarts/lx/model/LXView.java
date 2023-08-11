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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import heronarts.lx.LX;

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

  private static final String GROUP_SEPARATOR = "\\s*;\\s*";
  private static final String SELECTOR_SEPARATOR = "\\s*,\\s*";

  private static class ParseState {

    private final LXModel model;
    private final List<List<LXModel>> groups = new ArrayList<List<LXModel>>();
    private final List<LXModel> uniqueSubmodels = new ArrayList<LXModel>();

    private ParseState(LXModel model) {
      this.model = model;
    }
  }

  /**
   * Constructs a view of the given model object
   *
   * @param model Model
   * @param viewSelector View selection string
   * @param normalization What normalization mode to use for this view
   * @return A view of the model that selects the elements in the selector string
   */
  public static LXView create(LXModel model, String viewSelector, Normalization normalization) {
    ParseState state = new ParseState(model);

    // Split at top-level by groups, separated by ;
    for (String groupSelector : viewSelector.trim().split(GROUP_SEPARATOR)) {
      List<LXModel> group = parseGroup(state, groupSelector);
      if (group != null) {
        state.groups.add(group);
      }
    }

    // We now have a set of unique submodels, organized by group. Each submodel
    // belongs strictly to one group.
    //
    // Construct a new list of a copy of all the points from all the models
    // in the view. We need a copy because these will all be re-normalized
    // with xn/yn/zn values relative to this view
    Map<Integer, LXPoint> clonedPoints = new HashMap<Integer, LXPoint>();
    LXView[] views = new LXView[state.groups.size()];
    List<LXPoint> allPoints = new ArrayList<LXPoint>();
    int g = 0;
    for (List<LXModel> group : state.groups) {
      List<LXPoint> groupPoints = new ArrayList<LXPoint>();
      LXModel[] groupChildren = new LXModel[group.size()];
      int c = 0;
      for (LXModel sub : group) {
        // Replicate all the points from each group submodel
        for (LXPoint p : sub.points) {
          if (!clonedPoints.containsKey(p.index)) {
            LXPoint copy = new LXPoint(p);
            clonedPoints.put(p.index, copy);
            groupPoints.add(copy);
            allPoints.add(copy);
          }

        }
        // Clone the submodel of this group
        groupChildren[c++] = cloneModel(clonedPoints, sub);
      }
      views[g++] = new LXView(model, normalization, clonedPoints, groupPoints, groupChildren);
    }

    if (views.length == 0) {
      // Empty view!
      return new LXView(model, normalization, clonedPoints, new ArrayList<LXPoint>(), new LXModel[0]);
    } else if (views.length == 1) {
      // Just a single view, that'll do it!
      return views[0];
    } else {
      // Return a container-view with the group views as children, holding all of the points. We set
      // the normalization mode to absolute here no matter what, as this container view shouldn't do any
      // re-normalization
      return new LXView(model, Normalization.ABSOLUTE, clonedPoints, allPoints, views);
    }

  }

  private static List<LXModel> parseGroup(ParseState state, String groupSelector) {
    groupSelector = groupSelector.trim();
    if (groupSelector.isEmpty()) {
      return null;
    }

    // Within a group, multiple CSS-esque selectors are separated by ,
    // the union of these selectors forms the group
    List<LXModel> group = new ArrayList<LXModel>();
    for (String selector : groupSelector.split(SELECTOR_SEPARATOR)) {
      parseSelector(state, group, selector);
    }
    return group.isEmpty() ? null : group;
  }

  private static void parseSelector(ParseState state, List<LXModel> group, String selector) {
    // Is the selector empty? skip it.
    selector = selector.trim();
    if (selector.isEmpty()) {
      return;
    }

    // Set of candidates for addition to this group, by default we have the initial model
    final List<LXModel> candidates = new ArrayList<LXModel>();
    candidates.add(state.model);

    final List<LXModel> searchSpace = new ArrayList<LXModel>();

    final List<LXModel> intersect = new ArrayList<LXModel>();

    boolean directChildMode = false;
    boolean andMode = false;

    // Selectors are of the form "a b c" - meaning tags "c" contained by "b" contained by "a"
    for (String part : selector.split("\\s+")) {
      String tag = part.trim();

      // Check for special operators, set a flag for next encountered tag
      if (">".equals(tag)) {
        directChildMode = true;
        continue;
      } else if ("&".equals(tag)) {
        andMode = true;
        continue;
      }

      if (andMode) {
        // In andMode, we will keep the same search space as before, now run a new sub-query
        // and intersect it against the previous candidates
        intersect.clear();
        intersect.addAll(candidates);
      } else {
        // Clear the search space, add previously matched candidates to search space - on this
        // pass of the loop we will be searching the descendants of the previous match
        searchSpace.clear();
        searchSpace.addAll(candidates);
      }

      // We're going to select new candidates on this pass
      candidates.clear();

      // If this index selection syntax gets more complex, should clean it up to use regex matching
      int rangeStart = tag.indexOf('[');
      int startIndex = 0, endIndex = -1;
      int increment = 1;
      if (rangeStart >= 0) {
        tag = selector.substring(0, rangeStart);
        int rangeEnd = selector.indexOf(']');
        if ((rangeEnd < 0) || (rangeEnd <= rangeStart)) {
          LX.error("Poorly formatted view selection range: " + selector);
        } else {
          // Range can be specified either as [n] or [n-m] (inclusive)
          String range = selector.substring(rangeStart+1, rangeEnd);
          if ("even".equals(range)) {
            increment = 2;
          } else if ("odd".equals(range)) {
            startIndex = 1;
            increment = 2;
          } else {
            int dash = range.indexOf('-');
            if (dash < 0) {
              try {
                startIndex = endIndex = Integer.parseInt(range.trim());
              } catch (NumberFormatException nfx) {
                LX.error("Bad number in view selection range: " + selector);
              }
            } else {
              try {
                startIndex = Integer.parseInt(range.substring(0, dash).trim());
                endIndex = Integer.parseInt(range.substring(dash+1).trim());
              } catch (NumberFormatException nfx) {
                LX.error("Bad value in view selection range: " + selector);
              }
            }
          }
        }
      }

      // Iterate over all searchSpace parents, to find sub-tags of appropriate type
      for (LXModel search : searchSpace) {
        List<LXModel> subs;
        if (andMode) {
          subs = search.sub(tag);
        } else if (directChildMode) {
          subs = search.children(tag);
        } else {
          subs = search.sub(tag);
        }
        if (startIndex < 0) {
          startIndex = 0;
        }
        if ((endIndex < 0) || (endIndex >= subs.size())) {
          endIndex = subs.size() - 1;
        }
        for (int i = startIndex; i <= endIndex; i += increment) {
          LXModel sub = subs.get(i);

          // Has this candidate *already* been found? Then there is no point
          // including it or searching below in the tree, we'll only be keeping
          // the ancestor in any case
          if (!state.uniqueSubmodels.contains(sub)) {
            candidates.add(subs.get(i));
          }
        }
      }

      // If this was the and query, filter candidates for presence in
      if (andMode) {
        Iterator<LXModel> iter = candidates.iterator();
        while (iter.hasNext()) {
          LXModel candidate = iter.next();
          if (!intersect.contains(candidate)) {
            iter.remove();
          }
        }
      }

      // Clear special mode flags as we move on
      directChildMode = false;
      andMode = false;
    }

    // Check that candidates are valid/unique, and add to the group
    addGroupCandidates(state, group, candidates);
  }

  private static void addGroupCandidates(ParseState state, List<LXModel> group, List<LXModel> candidates) {

    // Now we have all the candidates matched by this selector, but we need to see
    // if they were already matched in this or another group!
    for (LXModel candidate : candidates) {
      // If the submodel is already directly contained, either by this query
      // or by a previous group, skip it and give precedence to first selector
      if (state.uniqueSubmodels.contains(candidate)) {
        continue;
      }

      // Now we need to check for two scenarios... one is that the candidate
      // is an ancestor of one or more already-contained submodels. In which case,
      // those need to be removed. The candidate will be added instead, implicitly
      // containing the submodels.
      //
      // Alternately, if the candidate is a descendant of one of the existing
      // submodels, then we can skip it as it is already contained
      boolean isDescendant = false;
      Iterator<LXModel> iter = state.uniqueSubmodels.iterator();
      while (!isDescendant && iter.hasNext()) {
        LXModel submodel = iter.next();
        if (submodel.contains(candidate)) {
          isDescendant = true;
        } else if (candidate.contains(submodel)) {
          // We're subsuming this thing, remove it!
          iter.remove();

          // Remove this from any group which contained it previously! We now
          // have a broader selection that contains the submodel, this will takes
          // priority
          for (List<LXModel> existingGroup : state.groups) {
            // NOTE(mcslee): Should we push a user-facing warning here explaining
            // that one submodel can't be in two separate groups?
            existingGroup.remove(submodel);
          }
        }
      }
      if (!isDescendant) {
        state.uniqueSubmodels.add(candidate);
        group.add(candidate);
      }
    }
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
