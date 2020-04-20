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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * The LX class loader parses JAR files in the LX content directory. Any
 * valid extension components are automatically registered with the engine as
 * eligible to be instantiated. Note that this requires an active, upfront definition
 * of all the classes contained, since any JAR file in this path is considered to
 * potentially hold LX content.
 *
 * We subclass from URLClassLoader because the JAR files may contain more than
 * just class files. They may also contain bundled resource files and using this
 * classloader ensures that the loaded classes will get proper behavior from methods
 * like getResourceAsStream().
 */
public class LXClassLoader extends URLClassLoader {

  private final LX lx;

  private final List<Class<?>> classes = new ArrayList<Class<?>>();

  private static List<File> defaultJarFiles(LX lx) {
    List<File> jarFiles = new ArrayList<File>();
    collectJarFiles(jarFiles, lx.getMediaFolder(LX.Media.CONTENT, false));
    return jarFiles;
  }

  private static void collectJarFiles(List<File> jarFiles, File folder) {
    try {
      if (folder.exists() && folder.isDirectory()) {
        for (File file : folder.listFiles()) {
          if (file.isHidden()) {
            // I mean, god bless anyone who tried this, but it will only cause great pain...
            continue;
          }
          if (file.isDirectory()) {
            collectJarFiles(jarFiles, file);
          } else if (file.isFile() && file.getName().endsWith(".jar")) {
            jarFiles.add(file);
          }
        }
      }
    } catch (Exception x) {
      LX.error(x, "Unhandled exception loading custom content dir: " + folder);
    }
  }

  private static URL[] fileListToURLArray(List<File> files) {
    List<URL> urls = new ArrayList<URL>();
    for (File file : files) {
      // Intentional URI -> URL here! See the javadoc on File.toURL being deprecated!
      try {
        urls.add(file.toURI().toURL());
      } catch (MalformedURLException e) {
        LX.error(e, "Bad URL in file list: " + file);
      }
    }
    return urls.toArray(new URL[0]);
  }

  private final List<File> jarFiles;

  protected LXClassLoader(LX lx) {
    this(lx, defaultJarFiles(lx));
  }

  protected LXClassLoader(LX lx, List<File> jarFiles) {
    super(fileListToURLArray(jarFiles), lx.getClass().getClassLoader());
    this.lx = lx;
    this.jarFiles = jarFiles;
  }

  protected void load() {
    for (File jarFile : this.jarFiles) {
      loadJarFile(jarFile);
    }
  }

  protected void dispose() {
    for (Class<?> clz : this.classes) {
      this.lx.registry.removeClass(clz);
    }
    this.classes.clear();
  }

  private void loadJarFile(File file) {
    LX.log("Loading custom content from: " + file);
    try (JarFile jarFile = new JarFile(file)) {
      Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String fileName = entry.getName();
        if (fileName.endsWith(".class")) {
          loadClassEntry(jarFile, className(fileName).replaceAll("/", "\\."));
        }
      }
    } catch (IOException iox) {
      LX.error(iox, "IOException unpacking JAR file " + file + " - " + iox.getLocalizedMessage());
    } catch (Exception | Error e) {
      LX.error(e, "Unhandled exception loading JAR file " + file + " - " + e.getLocalizedMessage());
    }
  }

  private static String className(String fileName) {
    return fileName.substring(0, fileName.length() - ".class".length());
  }

  private void loadClassEntry(JarFile jarFile, String className) {
    try {
      // This might be slightly slower, but just let URL loader find it...
      // Let's not re-invent the wheel on parsing JAR files and all that
      Class<?> clz = loadClass(className, false);

      // Register all non-abstract components that we discover!
      if (!Modifier.isAbstract(clz.getModifiers())) {
        this.classes.add(clz);
        this.lx.registry.addClass(clz);
      }
    } catch (ClassNotFoundException cnfx) {
      LX.error(cnfx, "Class not actually found, expected in JAR file: " + className + " " + jarFile.getName());
    } catch (Exception x) {
      LX.error(x, "Unhandled exception in class loading: " + className);
    }
  }

}
