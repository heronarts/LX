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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class LXClassLoader extends ClassLoader {

  private final LX lx;

  private final List<Class<? extends LXPattern>> patterns = new ArrayList<Class<? extends LXPattern>>();
  private final List<Class<? extends LXEffect>> effects = new ArrayList<Class<? extends LXEffect>>();

  public LXClassLoader(LX lx) {
    super(lx.getClass().getClassLoader());
    this.lx = lx;
    loadContent();
  }

  protected void dispose() {
    this.lx.unregisterPatterns(this.patterns);
    this.lx.unregisterEffects(this.effects);
  }

  private void loadContent() {
    try {
      File contentDir = new File(this.lx.getMediaPath(), "Content");
      if (contentDir.exists() && contentDir.isDirectory()) {
        for (File file : contentDir.listFiles()) {
          if (file.isHidden()) {
            // This will only cause great pain...
            continue;
          }
          String fileName = file.getName();
          if (file.isFile()) {
            if (fileName.endsWith(".jar")) {
              loadJarFile(file);
            } else if (fileName.endsWith(".class")) {
              loadClassFile(className(fileName), file);
            } else {
              System.err.println("Unknown file type in Content directory: " + fileName);
            }
          } else {
            System.err.println("Content directory does not currently support nested directories: " + fileName);
          }
        }
      }
    } catch (Exception x) {
      System.err.println("Unhandled exception reading the custom content directory");
      x.printStackTrace();
    }
  }

  private void loadJarFile(File file) {
    System.out.println("Loading custom content from: " + file.getName());
    try (JarFile jarFile = new JarFile(file)) {
      Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String fileName = entry.getName();
        if (fileName.endsWith(".class")) {
          loadClassFile(className(fileName).replaceAll("/", "\\."), jarFile, entry);
        }
      }

    } catch (IOException iox) {
      System.err.println("Exception unpacking JAR file " + file.getName());
      iox.printStackTrace();
    }
  }

  private static String className(String fileName) {
    return fileName.substring(0, fileName.length() - ".class".length());
  }

  private void loadClassFile(String className, JarFile jarFile, JarEntry entry) {
    long jarSize = entry.getSize();
    int jarBufferSize = 4096;
    if (jarSize > 0 || jarSize < Integer.MAX_VALUE) {
      jarBufferSize = (int) jarSize;
    }
    try (InputStream is = jarFile.getInputStream(entry);
         ByteArrayOutputStream baos = new ByteArrayOutputStream(jarBufferSize)) {
      byte[] buffer = new byte[jarBufferSize];
      int read = -1;
      while ((read = is.read(buffer)) != -1) {
        baos.write(buffer, 0, read);
      }
      baos.flush();
      loadClassFile(className, baos.toByteArray());
    } catch (IOException iox) {
      iox.printStackTrace();
    }
  }

  private void loadClassFile(String className, File file) {
    try {
      loadClassFile(className, Files.readAllBytes(file.toPath()));
    } catch (IOException iox) {
      iox.printStackTrace();
    }
  }

  private void loadClassFile(String className, byte[] bytes) {
    try {
      Class<?> clz = defineClass(className, bytes, 0, bytes.length);

      // Register non-abstract patterns and effects
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
      }

      try {
        clz.getMethod("initialize").invoke(null);
      } catch (NoSuchMethodException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (SecurityException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (IllegalAccessException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (IllegalArgumentException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (InvocationTargetException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }

    } catch (ClassFormatError cfe) {
      cfe.printStackTrace();
    } catch (Exception | Error e) {
      e.printStackTrace();
    }
  }
}
