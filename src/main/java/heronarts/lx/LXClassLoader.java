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
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

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

  static final String PACKAGE_DESCRIPTOR_FILE_NAME = "lx.package";

  public static class Package {

    final File jarFile;

    private String name;
    private String author = "Unknown Author";

    private Throwable error = null;
    private int numClasses = 0;
    private int numFailedClasses = 0;

    private Package(File jarFile) {
      this.jarFile = jarFile;
      this.name = jarFile.getName();
      if (name.endsWith(".jar")) {
        this.name = name.substring(0, name.length() - ".jar".length());
      }
    }

    public String getFileName() {
      return this.jarFile.getName();
    }

    public String getName() {
      return this.name;
    }

    public String getAuthor() {
      return this.author;
    }

    private void setError(Throwable error) {
      this.error = error;
    }

    public int getNumClasses() {
      return this.numClasses;
    }

    public int getNumFailedClasses() {
      return this.numFailedClasses;
    }

    public boolean hasError() {
      return this.error != null;
    }

    public Throwable getError() {
      return this.error;
    }

  }

  private final LX lx;

  private final List<Class<?>> classes = new ArrayList<Class<?>>();

  private static List<File> defaultJarFiles(LX lx) {
    List<File> jarFiles = new ArrayList<File>();
    collectJarFiles(jarFiles, lx.getMediaFolder(LX.Media.PACKAGES, false));
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
    LX.log("Loading package content from: " + file);
    final Package pack = new Package(file);
    try (JarFile jarFile = new JarFile(file)) {
      final Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String fileName = entry.getName();
        if (fileName.equals(PACKAGE_DESCRIPTOR_FILE_NAME)) {
          try {
            JsonObject obj = new Gson().fromJson(new InputStreamReader(jarFile.getInputStream(entry)), JsonObject.class);
            if (obj.has("name")) {
              pack.name = obj.get("name").getAsString();
            }
            if (obj.has("author")) {
              pack.author = obj.get("author").getAsString();
            }
          } catch (Throwable x) {
            LX.error(x, "Exception reading lx.package contents for: " + jarFile);
          }
        } else if (fileName.endsWith(".class")) {
          loadClassEntry(pack, jarFile, className(fileName).replaceAll("/", "\\."));
        }
      }
    } catch (IOException iox) {
      LX.error(iox, "IOException unpacking JAR file " + file + " - " + iox.getLocalizedMessage());
      pack.setError(iox);
    } catch (Throwable e) {
      LX.error(e, "Unhandled exception loading JAR file " + file + " - " + e.getLocalizedMessage());
      pack.setError(e);
    }

    this.lx.registry.addPackage(pack);
  }

  private static String className(String fileName) {
    return fileName.substring(0, fileName.length() - ".class".length());
  }

  private void loadClassEntry(Package pack, JarFile jarFile, String className) {
    try {
      // This might be slightly slower, but just let URLClassLoader find it...
      // Let's not re-invent the wheel on parsing JAR files and all that.
      final Class<?> clz = loadClass(className, false);

      // Try to register public non-abstract classes
      final int modifiers = clz.getModifiers();
      if (Modifier.isPublic(modifiers) && !Modifier.isAbstract(modifiers)) {

        if (this.lx.registry.isInstantiableComponent(clz)) {
          // Okay, we loaded the class. But can we actually operate on it? Let's try
          // to get the name of this class to ensure it's not going to bork things
          // later due to unresolved dependencies that could throw an
          // Error (e.g. NoClassDefFoundError)

          // TODO(mcslee): there must be some better way of checking this explicitly,
          // without instantiating the class, but more clear than getSimpleName()??
          clz.getSimpleName();
        }

        // Register all public, non-abstract components that we discover
        ++pack.numClasses;
        this.classes.add(clz);
        this.lx.registry.addClass(clz);
      }
    } catch (ClassNotFoundException | NoClassDefFoundError cnfx) {
      LX.error(cnfx, "Dependency class not found, required by JAR file: " + className + " " + jarFile.getName());
      ++pack.numFailedClasses;
    } catch (Throwable x) {
      LX.error(x, "Unhandled exception in class loading: " + className);
    }
  }

}
