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

import heronarts.lx.effect.LXEffect;
import heronarts.lx.model.LXModel;
import heronarts.lx.pattern.LXPattern;

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
    for (File jarFile : this.jarFiles) {
      loadJarFile(jarFile);
    }
  }

  protected void dispose() {
    this.lx.registry.removePatterns(this.patterns);
    this.lx.registry.removeEffects(this.effects);
    this.lx.registry.removeModels(this.models);
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
      LX.error(iox, "Exception unpacking JAR file " + file);
    } catch (Exception | Error e) {
      LX.error(e, "Unhandled exception loading JAR file " + file);
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
          this.lx.registry.addPattern(patternClz);
        }
        if (LXEffect.class.isAssignableFrom(clz)) {
          Class<? extends LXEffect> effectClz = clz.asSubclass(LXEffect.class);
          this.effects.add(effectClz);
          this.lx.registry.addEffect(effectClz);
        }
        if (LXModel.class.isAssignableFrom(clz)) {
          Class<? extends LXModel> modelClz = clz.asSubclass(LXModel.class);
          this.models.add(modelClz);
          this.lx.registry.addModel(modelClz);
        }
        if (LXPlugin.class.isAssignableFrom(clz)) {
          this.lx.registry.addPlugin(clz.asSubclass(LXPlugin.class));
        }
      }
    } catch (ClassNotFoundException cnfx) {
      LX.error(cnfx, "Class not actually found, expected in JAR file: " + className + " " + jarFile.getName());
    } catch (Exception x) {
      LX.error(x, "Unhandled exception in class loading: " + className);
    }
  }

}
