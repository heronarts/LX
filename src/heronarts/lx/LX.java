/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
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

import heronarts.lx.clipboard.LXClipboard;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXPalette;
import heronarts.lx.model.GridModel;
import heronarts.lx.model.LXModel;
import heronarts.lx.output.LXOutput;
import heronarts.lx.pattern.IteratorTestPattern;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

/**
 * Core controller for a LX instance. Each instance drives a grid of nodes with
 * a fixed width and height. The nodes are indexed using typical computer
 * graphics coordinates, with the x-axis going from left to right, y-axis from
 * top to bottom.
 *
 * <pre>
 *    x
 *  y 0 1 2 .
 *    1 . . .
 *    2 . . .
 *    . . . .
 * </pre>
 *
 * Note that the grid layout is just a helper. The node buffer is actually a 1-d
 * array and can be used to represent any type of layout. The library just
 * provides helpful accessors for grid layouts.
 *
 * The instance manages rotation amongst a set of patterns. There may be
 * multiple channels, each with its own list of patterns. These channels are then
 * blended together.
 *
 * The color-space used is HSB, with H ranging from 0-360, S from 0-100, and B
 * from 0-100.
 */
public class LX {

  public static final double HALF_PI = Math.PI / 2.;
  public static final double TWO_PI = Math.PI * 2.;

  public static class InitTimer {
    private long lastTime;

    protected void init() {
      this.lastTime = System.nanoTime();
    }

    public void log(String label) {
      long thisTime = System.nanoTime();
      if (LX.LOG_INIT_TIMING) {
        System.out.println(String.format("[LX init: %s: %.2fms]", label, (thisTime - lastTime) / 1000000.));
      }
      this.lastTime = thisTime;
    }
  }

  public static final InitTimer initTimer = new InitTimer();

  private static boolean LOG_INIT_TIMING = false;

  public static void logInitTiming() {
    LX.LOG_INIT_TIMING = true;
  }

  /**
   * Listener for top-level events
   */
  public interface Listener {
    public void modelChanged(LX lx, LXModel model);
  }

  private final List<Listener> listeners = new ArrayList<Listener>();

  public interface ProjectListener {

    enum Change {
      NEW,
      SAVE,
      OPEN
    };

    public void projectChanged(File file, Change change);
  }

  private final List<ProjectListener> projectListeners = new ArrayList<ProjectListener>();

  final LXComponent.Registry componentRegistry = new LXComponent.Registry();

  /**
   * The width of the grid, immutable.
   */
  public final int width;

  /**
   * The height of the grid, immutable.
   */
  public final int height;

  /**
   * The midpoint of the x-space.
   */
  public final float cx;

  /**
   * This midpoint of the y-space.
   */
  public final float cy;

  /**
   * The pixel model.
   */
  public final LXModel model;

  /**
   * The total number of pixels in the grid, immutable.
   */
  public final int total;

  /**
   * Clipboard for copy/paste
   */
  public final LXClipboard clipboard = new LXClipboard();

  /**
   * The default palette.
   */
  public final LXPalette palette;

  /**
   * The animation engine.
   */
  public final LXEngine engine;

  /**
   * The global tempo object.
   */
  public final Tempo tempo;

  /**
   * The list of globally registered pattern classes
   */
  private final List<Class<? extends LXPattern>> registeredPatterns =
    new ArrayList<Class<? extends LXPattern>>();

  /**
   * The list of globally registered effects
   */
  private final List<Class<? extends LXEffect>> registeredEffects =
    new ArrayList<Class<? extends LXEffect>>();

  /**
   * Creates an LX instance with no nodes.
   */
  public LX() {
    this(null);
  }

  /**
   * Creates an LX instance. This instance will run patterns for a grid of the
   * specified size.
   *
   * @param total Number of nodes
   */
  public LX(int total) {
    this(total, 1);
  }

  /**
   * Creates a LX instance. This instance will run patterns for a grid of the
   * specified size.
   *
   * @param width Width
   * @param height Height
   */
  public LX(int width, int height) {
    this(new GridModel(width, height));
  }

  /**
   * Constructs an LX instance with the given pixel model
   *
   * @param model Pixel model
   */
  public LX(LXModel model) {
    LX.initTimer.init();
    this.model = model;
    if (model == null) {
      this.total = this.width = this.height = 0;
      this.cx = this.cy = 0;
    } else {
      this.total = model.points.length;
      this.cx = model.cx;
      this.cy = model.cy;
      if (model instanceof GridModel) {
        GridModel grid = (GridModel) model;
        this.width = grid.width;
        this.height = grid.height;
      } else {
        this.width = this.height = 0;
      }
    }
    model.normalize();
    LX.initTimer.log("Model");

    // Color palette
    this.palette = new LXPalette(this);
    LX.initTimer.log("Palette");

    // Construct the engine
    this.engine = new LXEngine(this);
    LX.initTimer.log("Engine");

    // Midi
    this.engine.midi.initialize();

    // Tempo
    this.tempo = new Tempo(this);
    LX.initTimer.log("Tempo");

    // Add a default channel
    this.engine.addChannel(new LXPattern[] { new IteratorTestPattern(this) }).fader.setValue(1);
    LX.initTimer.log("Default Channel");

  }

  public LX addListener(Listener listener) {
    this.listeners.add(listener);
    return this;
  }

  public LX removeListener(Listener listener) {
    this.listeners.add(listener);
    return this;
  }

  public LX addProjectListener(ProjectListener listener) {
    this.projectListeners.add(listener);
    return this;
  }

  public LX removeProjectListener(ProjectListener listener) {
    this.projectListeners.remove(listener);
    return this;
  }

  public LXComponent getProjectComponent(int projectId) {
    return this.componentRegistry.getProjectComponent(projectId);
  }

  /**
   * Shut down resources of the LX instance.
   */
  public void dispose() {
    this.engine.audio.dispose();
  }

  /**
   * Utility function to return the row of a given index
   *
   * @param i Index into colors array
   * @return Which row this index is in
   */
  public int row(int i) {
    return (this.width == 0) ? 0 : (i / this.width);
  }

  /**
   * Utility function to return the column of a given index
   *
   * @param i Index into colors array
   * @return Which column this index is in
   */
  public int column(int i) {
    return (this.width == 0) ? 0 : (i % this.width);
  }

  /**
   * Utility function to get the x-coordinate of a pixel
   *
   * @param i Node index
   * @return x coordinate
   */
  public int x(int i) {
    return (this.width == 0) ? 0 : (i % this.width);
  }

  /**
   * Utility function to return the position of an index in x coordinate space
   * normalized from 0 to 1.
   *
   * @param i Node index
   * @return Position of this node in x space, from 0 to 1
   */
  public double xn(int i) {
    return (this.width == 0) ? 0 : ((i % this.width) / (double) (this.width - 1));
  }

  /**
   * Utility function to return the position of an index in x coordinate space
   * normalized from 0 to 1, as a floating point.
   *
   * @param i Node index
   * @return Position of this node in x space, from 0 to 1
   */
  public float xnf(int i) {
    return (float) this.xn(i);
  }

  /**
   * Utility function to get the y-coordinate of a pixel
   *
   * @param i Node index
   * @return y coordinate
   */
  public int y(int i) {
    return (this.width == 0) ? 0 : (i / this.width);
  }

  /**
   * Utility function to return the position of an index in y coordinate space
   * normalized from 0 to 1.
   *
   * @param i Node index
   * @return Position of this node in y space, from 0 to 1
   */
  public double yn(int i) {
    return (this.width == 0) ? 0 : ((i / this.width) / (double) (this.height - 1));
  }

  /**
   * Utility function to return the position of an index in y coordinate space
   * normalized from 0 to 1, as a floating point.
   *
   * @param i Node index
   * @return Position of this node in y space, from 0 to 1
   */
  public float ynf(int i) {
    return (float) this.yn(i);
  }

  /**
   * Shorthand for LXColor.hsb()
   *
   * @param h Hue 0-360
   * @param s Saturation 0-100
   * @param b Brightness 0-100
   * @return Color
   */
  public static int hsb(float h, float s, float b) {
    return LXColor.hsb(h, s, b);
  }

  /**
   * Shorthand for LXColor.hsa()
   *
   * @param h Hue 0-360
   * @param s Saturation 0-100
   * @param a Alpha 0-1
   * @return Color
   */
  public static int hsa(float h, float s, float a) {
    return LXColor.hsba(h, s, 100, a);
  }

  /**
   * Shorthand for LXColor.rgb()
   *
   * @param r Red 0-255
   * @param g Green 0-255
   * @param b Blue 0-255
   * @return color
   */
  public static int rgb(int r, int g, int b) {
    return LXColor.rgb(r, g, b);
  }

  /**
   * Sets the speed of the entire system. Default is 1.0, any modification will
   * mutate deltaMs values system-wide.
   *
   * @param speed Coefficient, 1 is normal speed
   * @return this
   */
  public LX setSpeed(double speed) {
    this.engine.setSpeed(speed);
    return this;
  }

  /**
   * Add multiple effects to the chain
   *
   * @param effects Array of effects
   * @return this
   */
  public LX addEffects(LXEffect[] effects) {
    for (LXEffect effect : effects) {
      addEffect(effect);
    }
    return this;
  }

  /**
   * Add an effect to the FX chain.
   *
   * @param effect Effect
   * @return this
   */
  public LX addEffect(LXEffect effect) {
    this.engine.masterChannel.addEffect(effect);
    return this;
  }

  /**
   * Remove an effect from the chain
   *
   * @param effect Effect
   * @return this
   */
  public LX removeEffect(LXEffect effect) {
    this.engine.masterChannel.removeEffect(effect);
    return this;
  }

  /**
   * Pause the engine from running
   *
   * @param paused Whether to pause the engine to pause
   * @return this
   */
  public LX setPaused(boolean paused) {
    this.engine.setPaused(paused);
    return this;
  }

  /**
   * Whether the engine is currently running.
   *
   * @return State of the engine
   */
  public boolean isPaused() {
    return this.engine.isPaused();
  }

  /**
   * Toggles the running state of the engine.
   *
   * @return this
   */
  public LX togglePaused() {
    return setPaused(!this.engine.isPaused());
  }

  /**
   * Sets the main channel to the previous pattern.
   *
   * @return this
   */
  public LX goPrev() {
    this.engine.goPrev();
    return this;
  }

  /**
   * Sets the main channel to the next pattern.
   *
   * @return this
   */
  public LX goNext() {
    this.engine.goNext();
    return this;
  }

  /**
   * Sets the main channel to a given pattern instance.
   *
   * @param pattern The pattern instance to run
   * @return this
   */
  public LX goPattern(LXPattern pattern) {
    this.engine.goPattern(pattern);
    return this;
  }

  /**
   * Sets the main channel to a pattern of the given index
   *
   * @param i Index of the pattern to run
   * @return this
   */
  public LX goIndex(int i) {
    this.engine.goIndex(i);
    return this;
  }

  /**
   * Stops patterns from automatically rotating
   *
   * @return this
   */
  public LX disableAutoCycle() {
    this.engine.disableAutoCycle();
    return this;
  }

  /**
   * Sets the patterns to rotate automatically
   *
   * @param autoCycleThreshold Number of milliseconds after which to cycle
   * @return this
   */
  public LX enableAutoCycle(int autoCycleThreshold) {
    this.engine.enableAutoCycle(autoCycleThreshold);
    return this;
  }

  /**
   * Adds an output driver
   *
   * @param output Output
   * @return this
   */
  public LX addOutput(LXOutput output) {
    this.engine.addOutput(output);
    return this;
  }

  /**
   * Specifies the set of patterns to be run.
   *
   * @param patterns Array of patterns
   * @return this
   */
  public LX setPatterns(LXPattern[] patterns) {
    this.engine.setPatterns(patterns);
    return this;
  }

  /**
   * Gets the current set of patterns on the main channel.
   *
   * @return The list of patters
   */
  public List<LXPattern> getPatterns() {
    return this.engine.getPatterns();
  }

  /**
   * Register a pattern class with the engine
   *
   * @param pattern Pattern class
   * @return this
   */
  public LX registerPattern(Class<? extends LXPattern> pattern) {
    this.registeredPatterns.add(pattern);
    return this;
  }

  /**
   * Register a pattern class with the engine
   *
   * @param patterns List of pattern classes
   * @return this
   */
  public LX registerPatterns(Class<LXPattern>[] patterns) {
    for (Class<LXPattern> pattern : patterns) {
      registerPattern(pattern);
    }
    return this;
  }

  /**
   * Gets the list of registered pattern classes
   *
   * @return Pattern classes
   */
  public List<Class<? extends LXPattern>> getRegisteredPatterns() {
    return this.registeredPatterns;
  }

  /**
   * Register an effect class with the engine
   *
   * @param effect Effect class
   * @return this
   */
  public LX registerEffect(Class<? extends LXEffect> effect) {
    this.registeredEffects.add(effect);
    return this;
  }

  /**
   * Register an effect class with the engine
   *
   * @param effects List of effect classes
   * @return this
   */
  public LX registerEffects(Class<? extends LXEffect>[] effects) {
    for (Class<? extends LXEffect> effect : effects) {
      registerEffect(effect);
    }
    return this;
  }

  /**
   * Gets the list of registered effect classes
   *
   * @return Effect classes
   */
  public List<Class<? extends LXEffect>> getRegisteredEffects() {
    return this.registeredEffects;
  }

  private final Map<String, LXSerializable> externals = new HashMap<String, LXSerializable>();

  private final static String KEY_VERSION = "version";
  private final static String KEY_TIMESTAMP = "timestamp";
  private final static String KEY_ENGINE = "engine";
  private final static String KEY_EXTERNALS = "externals";

  private File file;

  protected void setProject(File file, ProjectListener.Change change) {
    this.file = file;
    for (ProjectListener projectListener : this.projectListeners) {
      projectListener.projectChanged(file, change);
    }
  }

  public File getProject() {
    return this.file;
  }

  public void saveProject() {
    if (this.file != null) {
      saveProject(this.file);
    }
  }

  public void saveProject(File file) {
    JsonObject obj = new JsonObject();
    obj.addProperty(KEY_VERSION, "0.1");
    obj.addProperty(KEY_TIMESTAMP, System.currentTimeMillis());
    obj.add(KEY_ENGINE, LXSerializable.Utils.toObject(this, this.engine));
    JsonObject externalsObj = new JsonObject();
    for (String key : this.externals.keySet()) {
      externalsObj.add(key, LXSerializable.Utils.toObject(this, this.externals.get(key)));
    }
    obj.add(KEY_EXTERNALS, externalsObj);
    try {
      JsonWriter writer = new JsonWriter(new FileWriter(file));
      writer.setIndent("  ");
      new GsonBuilder().create().toJson(obj, writer);
      writer.close();
      System.out.println("Project saved successfully to " + file.toString());
      this.componentRegistry.resetProject();
      setProject(file, ProjectListener.Change.SAVE);
    } catch (IOException iox) {
      System.err.println(iox.getLocalizedMessage());
    }
  }

  public void newProject() {
    this.componentRegistry.resetProject();
    this.engine.load(this, new JsonObject());
    setProject(null, ProjectListener.Change.NEW);
  }

  public LX registerExternal(String key, LXSerializable serializable) {
    if (this.externals.containsKey(key)) {
      throw new IllegalStateException("Duplicate external for key: " + key + " (already: " + serializable + ")");
    }
    this.externals.put(key,  serializable);
    return this;
  }

  private int getMaxId(JsonObject obj, int max) {
    for (Entry<String, JsonElement> entry : obj.entrySet()) {
      if (entry.getKey().equals(LXComponent.KEY_ID)) {
        int id = entry.getValue().getAsInt();
        if (id > max) {
          max = id;
        }
      } else if (entry.getValue().isJsonArray()) {
        for (JsonElement arrElement : entry.getValue().getAsJsonArray()) {
          if (arrElement.isJsonObject()) {
            max = getMaxId(arrElement.getAsJsonObject(), max);
          }
        }
      } else if (entry.getValue().isJsonObject()) {
        max = getMaxId(entry.getValue().getAsJsonObject(), max);
      }
    }
    return max;
  }

  public void openProject(File file) {
    try {
      FileReader fr = null;
      try {
        fr = new FileReader(file);
        JsonObject obj = new Gson().fromJson(fr, JsonObject.class);
        this.componentRegistry.resetProject();
        this.componentRegistry.setIdCounter(getMaxId(obj, this.componentRegistry.getIdCounter()) + 1);
        this.engine.load(this, obj.getAsJsonObject(KEY_ENGINE));
        if (obj.has(KEY_EXTERNALS)) {
          JsonObject externalsObj = obj.getAsJsonObject(KEY_EXTERNALS);
          for (String key : this.externals.keySet()) {
            if (externalsObj.has(key)) {
              this.externals.get(key).load(this, externalsObj.getAsJsonObject(key));
            }
          }
        }
        setProject(file, ProjectListener.Change.OPEN);
        System.out.println("Project loaded successfully from " + file.toString());
      } catch (IOException iox) {
        System.err.println("Could not load project file: " + iox.getLocalizedMessage());
      } finally {
        if (fr != null) {
          try {
            fr.close();
          } catch (IOException ignored) {}
        }
      }
    } catch (Exception x) {
      System.err.println("Exception in loadProject: " + x.getLocalizedMessage());
      x.printStackTrace(System.err);
    }
  }

  protected <T extends LXComponent> T instantiateComponent(String className, Class<T> type) {
    try {
      Class<? extends T> cls = Class.forName(className).asSubclass(type);
      return instantiateComponent(cls, type);
    } catch (Exception x) {
      System.err.println("Exception in instantiateComponent: " + x.getLocalizedMessage());
    }
    return null;
  }

  protected <T extends LXComponent> T instantiateComponent(Class<? extends T> cls, Class<T> type) {
    try {
      return cls.getConstructor(LX.class).newInstance(this);
    } catch (Exception x) {
      System.err.println("Exception in instantiateComponent: " + x.getLocalizedMessage());
    }
    return null;
  }

  protected LXPattern instantiatePattern(String className) {
    return instantiateComponent(className, LXPattern.class);
  }

  protected LXPattern instantiatePattern(Class<? extends LXPattern> cls) {
    return instantiateComponent(cls, LXPattern.class);
  }

  protected LXEffect instantiateEffect(String className) {
    return instantiateComponent(className, LXEffect.class);
  }

  protected LXEffect instantiateEffect(Class<? extends LXEffect> cls) {
    return instantiateComponent(cls, LXEffect.class);
  }

}

