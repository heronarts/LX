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

/**
 * Interface for any object in the LX system that can be represented by an abstract path.
 * Typcially this is an LXComponent or an LXParameter
 */
public interface LXPath {
  /**
   * Returns the immediate path of this component, relative to its parent
   *
   * @return Path of this object relative to its parent component
   */
  public String getPath();

  /**
   * Returns the component that this object belongs to
   *
   * @return Parent component of this path object, or null if it is unowned
   */
  public LXComponent getParent();

  /**
   * Gets the canonical path of a Path object up to a given root
   *
   * @param root Root component
   * @param path Path object
   * @return Canonical path
   */
  public static String getCanonicalPath(LXComponent root, LXPath path) {
    String pathStr = "/" + path.getPath();
    LXComponent parent = path.getParent();
    while (parent != null && parent != root) {
      pathStr = "/" + parent.getPath() + pathStr;
      parent = parent.getParent();
    }
    return pathStr;
  }

  /**
   * Gets the canonical path of a Path object all the way up its chain
   *
   * @param path
   * @return Canonical path
   */
  public static String getCanonicalPath(LXPath path) {
    return getCanonicalPath(null, path);
  }

  public static LXPath get(LX lx, String path) {
    if (path.charAt(0) == '/') {
      path = path.substring(1);
    }
    if (path.equals("lx")) {
      return lx.engine;
    }
    if (path.startsWith("lx/")) {
      path = path.substring(3);
      String[] parts = path.split("/");
      return lx.engine.path(parts, 0);
    }
    return null;
  }

  public static LXPath get(LXComponent root, String path) {
    if (path.charAt(0) == '/') {
      path = path.substring(1);
    }
    return root.path(path.split("/"), 0);
  }
}
