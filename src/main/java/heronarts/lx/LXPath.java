/**
 * Copyright 2019- Mark C. Slee, Heron Arts LLC
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

import heronarts.lx.parameter.LXParameter;

/**
 * Interface for any object in the LX system that can be represented by an abstract path.
 * Typcially this is an LXComponent or an LXParameter
 */
public interface LXPath {

  public static final String ROOT = "lx";
  public static final String ROOT_PREFIX = "/lx/";
  public static final String ROOT_SLASH = "lx/";

  /**
   * Returns the immediate path of this component, relative to its parent
   *
   * @return Path of this object relative to its parent component
   */
  public String getPath();

  /**
   * Returns the user-facing label of this component
   *
   * @return User-facing label for this component
   */
  public String getLabel();

  /**
   * Returns a contextual help message explaining the purpose of this parameter or component
   * to the user, or null if none is available.
   *
   * @return Contextual help string explaining purpose of the element
   */
  public String getDescription();

  /**
   * Returns the component that this object belongs to
   *
   * @return Parent component of this path object, or null if it is unowned
   */
  public LXComponent getParent();

  /**
   * Determines whether this path object is a descendant of a root component
   *
   * @param root Root component
   * @return Whether this object descends from the root
   */
  public default boolean isDescendant(LXComponent root) {
    LXComponent parent = getParent();
    while (parent != null) {
      if (parent == root) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  /**
   * Gets the canonical path of a Path object up to a given root
   *
   * @param root Root component
   * @return Canonical path
   */
  public default String getCanonicalPath(LXComponent root) {
    String pathStr = "/" + getPath();
    LXComponent parent = getParent();
    while ((parent != null) && (parent != root)) {
      pathStr = "/" + parent.getPath() + pathStr;
      parent = parent.getParent();
    }
    return pathStr;
  }

  /**
   * Gets the canonical path of a Path object all the way up its chain
   *
   * @return Canonical path
   */
  public default String getCanonicalPath() {
    return getCanonicalPath(null);
  }

  /**
   * Returns the canonical user-facing label of this object. The label
   * is different from the path, it's a human-readable name that takes
   * into account how the user may have re-labeled components.
   *
   * @return Canonical label for this component
   */
  public default String getCanonicalLabel() {
    return getCanonicalLabel(null);
  }

  /**
   * Returns the canonical user-facing label of this component. The label
   * is different from the path, it's a human-readable name that takes
   * into account how the user may have re-labeled components.
   *
   * @param root Root object to get label relative to
   * @return Canonical label for this component
   */
  public default String getCanonicalLabel(LXComponent root) {
    String label = getLabel();
    LXComponent parent = getParent();
    while ((parent != null) && (parent != root) && !(parent instanceof LXEngine)) {
      label = parent.getLabel() + " \u2022 " + label;
      parent = parent.getParent();
    }
    return label;
  }

  /**
   * Globally retrieves an LX object with a path in the hierarchy
   *
   * @param lx LX instance
   * @param path Canonical path of object
   * @return Object at the given canonical path
   */
  public static LXPath get(LX lx, String path) {
    if (path.charAt(0) == '/') {
      path = path.substring(1);
    }
    if (path.equals(ROOT)) {
      return lx.engine;
    }
    if (path.startsWith(ROOT_SLASH)) {
      path = path.substring(3);
      String[] parts = path.split("/");
      return lx.engine.path(parts, 0);
    }
    return null;
  }

  /**
   * Globally retrieves an LX component with a path in the hierarchy
   *
   * @param lx LX instance
   * @param path Canonical path of object
   * @return Object at the given canonical path, if it is an LXComponent
   */
  public static LXComponent getComponent(LX lx, String path) {
    LXPath component = get(lx, path);
    if (component instanceof LXComponent) {
      return (LXComponent) component;
    }
    return null;
  }

  /**
   * Globally retrieves an LX parameter with a path in the hierarchy
   *
   * @param lx LX instance
   * @param path Canonical path of object
   * @return Object at the given canonical path, if it is an LXParameter
   */
  public static LXParameter getParameter(LX lx, String path) {
    LXPath parameter = get(lx, path);
    if (parameter instanceof LXParameter) {
      return (LXParameter) parameter;
    }
    return null;
  }

  /**
   * Globally retrieves an LX parameter at a certain scope in the LX hierarchy
   *
   * @param root LX root scope
   * @param path Canonical path of object
   * @return Object at the given canonical path
   */
  public static LXPath get(LXComponent root, String path) {
    if (path.charAt(0) == '/') {
      path = path.substring(1);
    }
    return root.path(path.split("/"), 0);
  }

  /**
   * Globally retrieves an LX component at a certain scope in the LX hierarchy
   *
   * @param root LX root scope
   * @param path Canonical path of object
   * @return Object at the given canonical path, if an LXComponent
   */
  public static LXComponent getComponent(LXComponent root, String path) {
    LXPath component = get(root, path);
    if (component instanceof LXComponent) {
      return (LXComponent) component;
    }
    return null;
  }

  /**
   * Globally retrieves an LX parameter at a certain scope in the LX hierarchy
   *
   * @param root LX root scope
   * @param path Canonical path of object
   * @return Object at the given canonical path, if an LXParameter
   */
  public static LXParameter getParameter(LXComponent root, String path) {
    LXPath parameter = get(root, path);
    if (parameter instanceof LXParameter) {
      return (LXParameter) parameter;
    }
    return null;
  }

}
