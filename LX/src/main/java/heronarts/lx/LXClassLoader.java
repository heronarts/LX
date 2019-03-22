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

import heronarts.lx.model.LXModel;

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

  private final List<Class<? extends LXPattern>> patterns = new ArrayList<Class<? extends LXPattern>>();
  private final List<Class<? extends LXEffect>> effects = new ArrayList<Class<? extends LXEffect>>();
  private final List<Class<? extends LXModel>> models = new ArrayList<Class<? extends LXModel>>();

  private final List<Class<? extends LXPlugin>> pluginClasses = new ArrayList<Class<? extends LXPlugin>>();
  private final List<LXPlugin> plugins = new ArrayList<LXPlugin>();

  public static LXClassLoader createNew(LX lx) {
    List<File> jarFiles = new ArrayList<File>();
    collectJarFiles(jarFiles, lx.getMediaFolder(LX.Media.CONTENT));
    return new LXClassLoader(lx, jarFiles);
  }

  private static void collectJarFiles(List<File> jarFiles, File folder) {
    try {
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
    } catch (Exception x) {
      System.err.println("Unhandled exception loading custom content dir");
      x.printStackTrace();
    }
  }

  private static URL[] fileListToURLArray(List<File> files) {
    List<URL> urls = new ArrayList<URL>();
    for (File file : files) {
      // Intentional URI -> URL here! See the javadoc on File.toURL being deprecated!
      try {
        urls.add(file.toURI().toURL());
      } catch (MalformedURLException e) {
        e.printStackTrace();
      }
    }
    return urls.toArray(new URL[0]);
  }

  private final List<File> jarFiles;

  private LXClassLoader(LX lx, List<File> jarFiles) {
    super(fileListToURLArray(jarFiles), lx.getClass().getClassLoader());
    this.lx = lx;
    this.jarFiles = jarFiles;
    for (File jarFile : this.jarFiles) {
      loadJarFile(jarFile);
    }
  }

  protected void dispose() {
    this.lx.unregisterPatterns(this.patterns);
    this.lx.unregisterEffects(this.effects);
  }

  private void loadJarFile(File file) {
    System.out.println("Loading custom content from: " + file);
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
      System.err.println("Exception unpacking JAR file " + file);
      iox.printStackTrace();
    } catch (Exception | Error e) {
      System.err.println("Unhandled exception loading JAR file " + file);
      e.printStackTrace();
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
        if (LXPattern.class.isAssignableFrom(clz)) {
          Class<? extends LXPattern> patternClz = clz.asSubclass(LXPattern.class);
          this.patterns.add(patternClz);
          this.lx.registerPattern(patternClz);
        }
        if (LXEffect.class.isAssignableFrom(clz)) {
          Class<? extends LXEffect> effectClz = clz.asSubclass(LXEffect.class);
          this.effects.add(effectClz);
          this.lx.registerEffect(effectClz);
        }
        if (LXModel.class.isAssignableFrom(clz)) {
          this.models.add(clz.asSubclass(LXModel.class));
        }
        if (LXPlugin.class.isAssignableFrom(clz)) {
          this.pluginClasses.add(clz.asSubclass(LXPlugin.class));
        }
      }
    } catch (ClassNotFoundException cnfx) {
      System.err.println("Class not actually found, expected in JAR file: " + className + " " + jarFile.getName());
      cnfx.printStackTrace();
    } catch (Exception x) {
      System.err.println("Unhandled exception in class loading: " + className);
      x.printStackTrace();
    }
  }

  protected List<Class<? extends LXModel>> getRegisteredModels() {
    return this.models;
  }

  protected void initializePlugins() {
    for (Class<? extends LXPlugin> pluginClass : this.pluginClasses) {
      try {
        LXPlugin plugin = pluginClass.getConstructor().newInstance();
        plugin.initialize(this.lx);
        this.plugins.add(plugin);
      } catch (Exception x) {
        System.err.println("Unhandled exception in plugin initialize: " + pluginClass.getName());
        x.printStackTrace();
      }
    }
  }

  public List<LXPlugin> getPlugins() {
    return this.plugins;
  }
}
