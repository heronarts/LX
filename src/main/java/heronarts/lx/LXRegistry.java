/**
 * Copyright 2020- Mark C. Slee, Heron Arts LLC
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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import heronarts.lx.blend.AddBlend;
import heronarts.lx.blend.DarkestBlend;
import heronarts.lx.blend.DifferenceBlend;
import heronarts.lx.blend.DissolveBlend;
import heronarts.lx.blend.LXBlend;
import heronarts.lx.blend.LightestBlend;
import heronarts.lx.blend.MultiplyBlend;
import heronarts.lx.blend.NormalBlend;
import heronarts.lx.blend.SubtractBlend;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.structure.LXFixture;

/**
 * Registry container for content classes used by the LX implementation
 */
public class LXRegistry implements LXSerializable {

  public interface Listener {
    /**
     * Invoked when available pattern/effect/model has been updated
     *
     * @param lx LX instance
     */
    default public void contentChanged(LX lx) {}

    /**
     * Invoked when the available channel blend implementations are changed
     *
     * @param lx LX instance
     */
    default public void channelBlendsChanged(LX lx) {}

    /**
     * Invoked when the available transition blend implementations are changed
     *
     * @param lx instance
     */
    default public void transitionBlendsChanged(LX lx) {}

    /**
     * Invoked when the available crossfader blend implementations are changed
     *
     * @param lx instance
     */
    default public void crossfaderBlendsChanged(LX lx) {}

    /**
     * Invoked when the state of an available plugin has changed
     *
     * @param lx LX instance
     * @param plugin Plugin wrapper
     */
    default public void pluginChanged(LX lx, Plugin plugin) {}
  }

  private final List<Listener> listeners = new ArrayList<Listener>();

  public LXRegistry addListener(Listener listener) {
    Objects.requireNonNull(listener, "May not add null LXRegistry.Listener");
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot add same LXRegistry.Listener twice: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  public LXRegistry removeListener(Listener listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("Trying to remove non-registered LXRegistry.Listener: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  private static final List<Class<? extends LXPattern>> DEFAULT_PATTERNS;
  static {
    DEFAULT_PATTERNS = new ArrayList<Class<? extends LXPattern>>();
    DEFAULT_PATTERNS.add(heronarts.lx.pattern.color.GradientPattern.class);
    DEFAULT_PATTERNS.add(heronarts.lx.pattern.color.SolidPattern.class);
    DEFAULT_PATTERNS.add(heronarts.lx.pattern.form.PlanesPattern.class);
    DEFAULT_PATTERNS.add(heronarts.lx.pattern.texture.NoisePattern.class);
    DEFAULT_PATTERNS.add(heronarts.lx.pattern.texture.SparklePattern.class);
    DEFAULT_PATTERNS.add(heronarts.lx.pattern.test.TestPattern.class);
  };

  private static final List<Class<? extends LXEffect>> DEFAULT_EFFECTS;
  static {
    DEFAULT_EFFECTS = new ArrayList<Class<? extends LXEffect>>();
    DEFAULT_EFFECTS.add(heronarts.lx.effect.BlurEffect.class);
    DEFAULT_EFFECTS.add(heronarts.lx.effect.color.ColorizeEffect.class);
    DEFAULT_EFFECTS.add(heronarts.lx.effect.DynamicsEffect.class);
    DEFAULT_EFFECTS.add(heronarts.lx.effect.InvertEffect.class);
    DEFAULT_EFFECTS.add(heronarts.lx.effect.HueSaturationEffect.class);
    DEFAULT_EFFECTS.add(heronarts.lx.effect.SparkleEffect.class);
    DEFAULT_EFFECTS.add(heronarts.lx.effect.StrobeEffect.class);
    DEFAULT_EFFECTS.add(heronarts.lx.effect.midi.GateEffect.class);
  };

  private static final List<Class<? extends LXBlend>> DEFAULT_CHANNEL_BLENDS;
  static {
    DEFAULT_CHANNEL_BLENDS = new ArrayList<Class<? extends LXBlend>>();
    DEFAULT_CHANNEL_BLENDS.add(AddBlend.class);
    DEFAULT_CHANNEL_BLENDS.add(MultiplyBlend.class);
    DEFAULT_CHANNEL_BLENDS.add(SubtractBlend.class);
    DEFAULT_CHANNEL_BLENDS.add(DifferenceBlend.class);
    DEFAULT_CHANNEL_BLENDS.add(NormalBlend.class);
  }

  private static final List<Class<? extends LXBlend>> DEFAULT_TRANSITION_BLENDS;
  static {
    DEFAULT_TRANSITION_BLENDS = new ArrayList<Class<? extends LXBlend>>();
    DEFAULT_TRANSITION_BLENDS.add(DissolveBlend.class);
    DEFAULT_TRANSITION_BLENDS.add(AddBlend.class);
    DEFAULT_TRANSITION_BLENDS.add(MultiplyBlend.class);
    DEFAULT_TRANSITION_BLENDS.add(LightestBlend.class);
    DEFAULT_TRANSITION_BLENDS.add(DarkestBlend.class);
    DEFAULT_TRANSITION_BLENDS.add(DifferenceBlend.class);
  }

  private static final List<Class<? extends LXBlend>> DEFAULT_CROSSFADER_BLENDS;
  static {
    DEFAULT_CROSSFADER_BLENDS = new ArrayList<Class<? extends LXBlend>>();
    DEFAULT_CROSSFADER_BLENDS.add(DissolveBlend.class);
    DEFAULT_CROSSFADER_BLENDS.add(AddBlend.class);
    DEFAULT_CROSSFADER_BLENDS.add(MultiplyBlend.class);
    DEFAULT_CROSSFADER_BLENDS.add(LightestBlend.class);
    DEFAULT_CROSSFADER_BLENDS.add(DarkestBlend.class);
    DEFAULT_CROSSFADER_BLENDS.add(DifferenceBlend.class);
  }

  private static final List<Class<? extends LXFixture>> DEFAULT_FIXTURES;
  static {
    DEFAULT_FIXTURES = new ArrayList<Class<? extends LXFixture>>();
    DEFAULT_FIXTURES.add(heronarts.lx.structure.ArcFixture.class);
    DEFAULT_FIXTURES.add(heronarts.lx.structure.GridFixture.class);
    DEFAULT_FIXTURES.add(heronarts.lx.structure.PointFixture.class);
    DEFAULT_FIXTURES.add(heronarts.lx.structure.StripFixture.class);
  };

  /**
   * The list of globally registered pattern classes
   */
  private final List<Class<? extends LXPattern>> mutablePatterns =
    new ArrayList<Class<? extends LXPattern>>(DEFAULT_PATTERNS);
  public final List<Class<? extends LXPattern>> patterns =
    Collections.unmodifiableList(this.mutablePatterns);

  private final List<Class<? extends LXEffect>> mutableEffects =
    new ArrayList<Class<? extends LXEffect>>(DEFAULT_EFFECTS);

  /**
   * The list of globally registered effects
   */
  public final List<Class<? extends LXEffect>> effects =
    Collections.unmodifiableList(this.mutableEffects);

  private final List<Class<? extends LXBlend>> mutableChannelBlends =
    new ArrayList<Class<? extends LXBlend>>(DEFAULT_CHANNEL_BLENDS);

  /**
   * The list of globally registered channel blend classes
   */
  public final List<Class<? extends LXBlend>> channelBlends =
    Collections.unmodifiableList(this.mutableChannelBlends);

  private final List<Class<? extends LXBlend>> mutableTransitionBlends =
    new ArrayList<Class<? extends LXBlend>>(DEFAULT_TRANSITION_BLENDS);

  /**
   * The list of globally registered transition blend classes
   */
  public final List<Class<? extends LXBlend>> transitionBlends =
    Collections.unmodifiableList(this.mutableTransitionBlends);

  private final List<Class<? extends LXBlend>> mutableCrossfaderBlends =
    new ArrayList<Class<? extends LXBlend>>(DEFAULT_CROSSFADER_BLENDS);

  /**
   * The list of globally registered crossfader blend classes
   */
  public final List<Class<? extends LXBlend>> crossfaderBlends =
    Collections.unmodifiableList(this.mutableCrossfaderBlends);

  private final List<Class<? extends LXFixture>> mutableFixtures =
    new ArrayList<Class<? extends LXFixture>>(DEFAULT_FIXTURES);

  /**
   * List of globally registered fixtures.
   */
  public final List<Class<? extends LXFixture>> fixtures =
    Collections.unmodifiableList(this.mutableFixtures);

  /**
   * JSON fixture type
   */
  public class JsonFixture {

    public final String type;
    public final boolean isVisible;

    private static final String KEY_IS_VISIBLE = "isVisible";

    public JsonFixture(File file, String prefix) {
      String fileName = prefix + file.getName();
      boolean isVisible = false;

      try (FileReader fr = new FileReader(file)) {
        JsonObject obj = new Gson().fromJson(fr, JsonObject.class);
        isVisible = !obj.has(KEY_IS_VISIBLE) || obj.get(KEY_IS_VISIBLE).getAsBoolean();
      } catch (JsonParseException jpx) {
        LX.error(jpx, "JSON fixture file is not valid JSON: " + file.getAbsolutePath());
      } catch (FileNotFoundException fnfx) {
        LX.error(fnfx, "JSON fixture file does not exist: " + file.getAbsolutePath());
      } catch (IOException iox) {
        LX.error(iox, "Error reading JSON fixture file: " + file.getAbsolutePath());
      }

      this.type = fileName.substring(0, fileName.length() - ".lxf".length());
      this.isVisible = isVisible;
    }
  }

  private final List<JsonFixture> mutableJsonFixtures = new ArrayList<JsonFixture>();

  /**
   * The list of globally registered JSON fixture types
   */
  public final List<JsonFixture> jsonFixtures =
   Collections.unmodifiableList(this.mutableJsonFixtures);

  private final List<LXClassLoader.Package> mutablePackages = new ArrayList<LXClassLoader.Package>();

  /**
   * Registered packages
   */
  public final List<LXClassLoader.Package> packages = Collections.unmodifiableList(this.mutablePackages);


  private final List<Plugin> mutablePlugins = new ArrayList<Plugin>();

  /**
   * Registered plugins
   */
  public final List<Plugin> plugins =
    Collections.unmodifiableList(this.mutablePlugins);

  public class Plugin implements LXSerializable {
    public final Class<? extends LXPlugin> clazz;
    public LXPlugin instance;
    private boolean hasError = false;
    private boolean isEnabled = false;
    private Exception exception = null;

    private Plugin(Class<? extends LXPlugin> clazz) {
      this.clazz = clazz;
    }

    public LXPlugin getInstance() {
      return this.instance;
    }

    public boolean hasInstance() {
      return (this.instance != null);
    }

    public boolean isEnabled() {
      return this.isEnabled;
    }

    public Plugin setEnabled(boolean enabled) {
      this.isEnabled = enabled;
      for (Listener listener : listeners) {
        listener.pluginChanged(lx, this);
      }
      return this;
    }

    private void initialize(LX lx) {
      if (!this.isEnabled) {
        return;
      }
      try {
        this.instance = clazz.getConstructor().newInstance();
        this.instance.initialize(lx);
      } catch (Exception x) {
        LX.error(x, "Unhandled exception in plugin initialize: " + clazz.getName());
        lx.pushError(x, "Error on initialization of plugin " + clazz.getSimpleName() + "\n" + x.getLocalizedMessage());
        setException(x);
      }
    }

    public Plugin setException(Exception x) {
      this.hasError = true;
      this.exception = x;
      for (Listener listener : listeners) {
        listener.pluginChanged(lx, this);
      }
      return this;
    }

    public Exception getException() {
      return this.exception;
    }

    public boolean hasError() {
      return this.hasError;
    }

    private static final String KEY_CLASS = "class";
    private static final String KEY_ENABLED = "enabled";

    @Override
    public void save(LX lx, JsonObject object) {
      object.addProperty(KEY_CLASS, this.clazz.getName());
      object.addProperty(KEY_ENABLED, this.isEnabled);
    }

    @Override
    public void load(LX lx, JsonObject object) {
      if (object.has(KEY_ENABLED)) {
        this.isEnabled = object.get(KEY_ENABLED).getAsBoolean();
      }
    }
  }

  public final LX lx;

  protected LXClassLoader classLoader;

  private boolean contentReloading = false;

  public LXRegistry(LX lx) {
    this.lx = lx;
    this.classLoader = new LXClassLoader(lx);
  }

  protected void initialize() {
    this.contentReloading = true;
    this.classLoader.load();

    // TODO(mcslee): should get fixtures in the reload cycle as well?
    addJsonFixtures(lx.getMediaFolder(LX.Media.FIXTURES, false));

    this.contentReloading = false;
  }

  public void checkRegistration() {
    if (!this.contentReloading && this.lx.engine.hasStarted) {
      throw new IllegalStateException("May not register components outside of initialize() callback");
    }
  }

  public void reloadContent() {
    LX.log("Reloading custom content folders");
    this.classLoader.dispose();
    this.mutablePackages.clear();
    this.contentReloading = true;

    // The previous classLoader is now disposed. Note that the classes it defined
    // may still be in use, e.g. via live patterns or effects. But we've released our
    // handle to it. Those classes will be garbage collected when they have no more
    // references. And all our new instantiations will use the new version of the Class
    // objects defined by a new instance of the LXClassLoader.
    this.classLoader = new LXClassLoader(this.lx);
    this.classLoader.load();

    // We are done reloading
    this.contentReloading = false;

    // Notify listeners of change
    for (Listener listener : this.listeners) {
      listener.contentChanged(this.lx);
    }
  }

  void addPackage(LXClassLoader.Package pack) {
    this.mutablePackages.add(pack);
  }

  public void installPackage(File file) {
    if (!file.exists() || file.isDirectory()) {
      this.lx.pushError(null, "Package file does not exist or is a directory: " + file);
      return;
    }
    File destinationFile = lx.getMediaFile(LX.Media.CONTENT, file.getName(), true);
    if (destinationFile.exists()) {
      this.lx.pushError(null, "Package file already exists: " + destinationFile.getName());
      return;
    }
    try {
      Files.copy(file.toPath(), destinationFile.toPath());
      reloadContent();
    } catch (IOException iox) {
      this.lx.pushError(iox, "Could not copy package file " + file.getName() + " to the content folder: " + iox.getLocalizedMessage());
    }
  }

  public void uninstallPackage(LXClassLoader.Package pack) {
    File destinationFile = lx.getMediaFile(LX.Media.DELETED, pack.jarFile.getName(), true);
    try {
      if (destinationFile.exists()) {
        destinationFile = lx.getMediaFile(LX.Media.DELETED, pack.jarFile.getName() + "-" + java.time.Instant.now().getEpochSecond(), true);
      }
      Files.move(pack.jarFile.toPath(), destinationFile.toPath());
      reloadContent();
    } catch (IOException iox) {
      this.lx.pushError(iox, "Could not remove package file " + pack.jarFile.getName());
    }
  }

  protected void addClass(Class<?> clz) {
    if (LXPattern.class.isAssignableFrom(clz)) {
      addPattern(clz.asSubclass(LXPattern.class));
    }
    if (LXEffect.class.isAssignableFrom(clz)) {
      addEffect(clz.asSubclass(LXEffect.class));
    }
    if (LXFixture.class.isAssignableFrom(clz)) {
      addFixture(clz.asSubclass(LXFixture.class));
    }
    if (LXPlugin.class.isAssignableFrom(clz)) {
      addPlugin(clz.asSubclass(LXPlugin.class));
    }
  }

  protected void removeClass(Class<?> clz) {
    if (LXPattern.class.isAssignableFrom(clz)) {
      removePattern(clz.asSubclass(LXPattern.class));
    }
    if (LXEffect.class.isAssignableFrom(clz)) {
      removeEffect(clz.asSubclass(LXEffect.class));
    }
    if (LXFixture.class.isAssignableFrom(clz)) {
      removeFixture(clz.asSubclass(LXFixture.class));
    }
    // NOTE: plugin classes are not removed, they can only be dealt with once at initialization
    // and if already running you can not "undo" their work until the next restart, so they should
    // remain visible
  }

  /**
   * Register a pattern class with the engine
   *
   * @param pattern Pattern class
   * @return this
   */
  public LXRegistry addPattern(Class<? extends LXPattern> pattern) {
    Objects.requireNonNull(pattern, "May not add null LXRegistry.addPattern");
    checkRegistration();
    if (this.mutablePatterns.contains(pattern)) {
      throw new IllegalStateException("Attemping to register pattern twice: " + pattern);
    }
    this.mutablePatterns.add(pattern);
    return this;
  }

  /**
   * Register a pattern class with the engine
   *
   * @param patterns List of pattern classes
   * @return this
   */
  public LXRegistry addPatterns(Class<? extends LXPattern>[] patterns) {
    checkRegistration();
    for (Class<? extends LXPattern> pattern : patterns) {
      addPattern(pattern);
    }
    return this;
  }

  /**
   * Unregister pattern class with the engine
   *
   * @param pattern Pattern class
   * @return this
   */
  public LXRegistry removePattern(Class<? extends LXPattern> pattern) {
    if (!this.mutablePatterns.contains(pattern)) {
      throw new IllegalStateException("Attemping to unregister pattern that does not exist: " + pattern);
    }
    this.mutablePatterns.remove(pattern);
    return this;
  }

  /**
   * Unregister pattern classes with the engine
   *
   * @param patterns Pattern classes
   * @return this
   */
  public LXRegistry removePatterns(List<Class<? extends LXPattern>> patterns) {
    for (Class<? extends LXPattern> pattern : patterns) {
      if (!this.mutablePatterns.contains(pattern)) {
        throw new IllegalStateException("Attemping to unregister pattern that does not exist: " + pattern);
      }
      this.mutablePatterns.remove(pattern);
    }
    return this;
  }

  /**
   * Register an effect class with the engine
   *
   * @param effect Effect class
   * @return this
   */
  public LXRegistry addEffect(Class<? extends LXEffect> effect) {
    Objects.requireNonNull(effect, "May not add null LXRegistry.addEffect");
    checkRegistration();
    if (this.mutableEffects.contains(effect)) {
      throw new IllegalStateException("Attemping to register effect twice: " + effect);
    }
    this.mutableEffects.add(effect);
    return this;
  }

  /**
   * Register an effect class with the engine
   *
   * @param effects List of effect classes
   * @return this
   */
  public LXRegistry addEffects(Class<? extends LXEffect>[] effects) {
    checkRegistration();
    for (Class<? extends LXEffect> effect : effects) {
      addEffect(effect);
    }
    return this;
  }

  /**
   * Unregister effect class with the engine
   *
   * @param effect Effect class
   * @return this
   */
  public LXRegistry removeEffect(Class<? extends LXEffect> effect) {
    if (!this.mutableEffects.contains(effect)) {
      throw new IllegalStateException("Attemping to unregister effect that does not exist: " + effect);
    }
    this.mutableEffects.remove(effect);
    return this;
  }

  /**
   * Unregister effect classes with the engine
   *
   * @param effects Effect classes
   * @return this
   */
  public LXRegistry removeEffects(List<Class<? extends LXEffect>> effects) {
    for (Class<? extends LXEffect> effect : effects) {
      if (!this.mutableEffects.contains(effect)) {
        throw new IllegalStateException("Attemping to unregister effect that does not exist: " + effect);
      }
      this.mutableEffects.remove(effect);
    }
    return this;
  }

  /**
   * Register a fixture class with the engine
   *
   * @param fixture Fixture class
   * @return this
   */
  public LXRegistry addFixture(Class<? extends LXFixture> fixture) {
    Objects.requireNonNull(fixture, "May not add null LXRegistry.addFixture");
    checkRegistration();
    if (this.mutableFixtures.contains(fixture)) {
      throw new IllegalStateException("Cannot double-register fixture: " + fixture);
    }
    this.mutableFixtures.add(fixture);
    return this;
  }

  /**
   * Register a set of fixture classes with the engine
   *
   * @param fixtures List of fixture classes
   * @return this
   */
  public LXRegistry addFixtures(List<Class<? extends LXFixture>> fixtures) {
    checkRegistration();
    for (Class<? extends LXFixture> fixture : fixtures) {
      addFixture(fixture);
    }
    return this;
  }

  /**
   * Unregister fixture class with the engine
   *
   * @param fixture Fixture class
   * @return this
   */
  public LXRegistry removeFixture(Class<? extends LXFixture> fixture) {
    if (!this.mutableFixtures.contains(fixture)) {
      throw new IllegalStateException("Attemping to unregister fixture that does not exist: " + fixture);
    }
    this.mutableFixtures.remove(fixture);
    return this;
  }

  /**
   * Unregister fixture classes with the engine
   *
   * @param fixtures Fixture classes
   * @return this
   */
  public LXRegistry removeFixtures(List<Class<? extends LXFixture>> fixtures) {
    for (Class<? extends LXFixture> fixture : fixtures) {
      if (!this.mutableFixtures.contains(fixture)) {
        throw new IllegalStateException("Attemping to unregister fixture that does not exist: " + fixture);
      }
      this.mutableFixtures.remove(fixture);
    }
    return this;
  }

  private LXRegistry addJsonFixture(File fixture, String prefix) {
    Objects.requireNonNull(fixture, "May not add null LXRegistry.addJsonFixture");
    checkRegistration();
    this.mutableJsonFixtures.add(new JsonFixture(fixture, prefix));
    return this;
  }

  private void addJsonFixtures(File fixtureDir) {
    addJsonFixtures(fixtureDir, "");
  }

  private void addJsonFixtures(File fixtureDir, String prefix) {
    if (fixtureDir.exists() && fixtureDir.isDirectory()) {
      for (File fixture : fixtureDir.listFiles()) {
        if (fixture.isDirectory()) {
          addJsonFixtures(fixture, prefix + fixture.getName() + "/");
        } else if (fixture.getName().endsWith(".lxf")) {
          addJsonFixture(fixture, prefix);
        }
      }
    }
  }

  /**
   * Register a [channel and crossfader] blend class with the engine
   *
   * @param blend Blend class
   * @return this
   */
  public LXRegistry addBlend(Class<? extends LXBlend> blend) {
    Objects.requireNonNull(blend, "May not add null LXRegistry.addBlend");
    checkRegistration();
    addChannelBlend(blend);
    addTransitionBlend(blend);
    addCrossfaderBlend(blend);
    return this;
  }

  /**
   * Register multiple [channel and crossfader] blend classes with the engine
   *
   * @param blends List of blend classes
   * @return this
   */
  public LXRegistry addBlends(Class<LXBlend>[] blends) {
    checkRegistration();
    addChannelBlends(blends);
    addTransitionBlends(blends);
    addCrossfaderBlends(blends);
    return this;
  }

  /**
   * Register a channel blend class with the engine
   *
   * @param blend Blend class
   * @return this
   */
  public LXRegistry addChannelBlend(Class<? extends LXBlend> blend) {
    Objects.requireNonNull(blend, "May not add null LXRegistry.addChannelBlend");
    checkRegistration();
    if (this.mutableChannelBlends.contains(blend)) {
      throw new IllegalStateException("Attemping to register channel blend twice: " + blend);
    }
    this.mutableChannelBlends.add(blend);
    for (Listener listener : this.listeners) {
      listener.channelBlendsChanged(this.lx);
    }
    return this;
  }

  /**
   * Register multiple channel blend classes with the engine
   *
   * @param blends List of blend classes
   * @return this
   */
  public LXRegistry addChannelBlends(Class<LXBlend>[] blends) {
    checkRegistration();
    for (Class<LXBlend> blend : blends) {
      if (this.mutableChannelBlends.contains(blend)) {
        throw new IllegalStateException("Attemping to register channel blend twice: " + blend);
      }
      this.mutableChannelBlends.add(blend);
    }
    for (Listener listener : this.listeners) {
      listener.channelBlendsChanged(this.lx);
    }
    return this;
  }

  /**
   * Register a transition blend class with the engine
   *
   * @param blend Blend class
   * @return this
   */
  public LXRegistry addTransitionBlend(Class<? extends LXBlend> blend) {
    Objects.requireNonNull(blend, "May not add null LXRegistry.addTransitionBlend");
    checkRegistration();
    if (this.mutableTransitionBlends.contains(blend)) {
      throw new IllegalStateException("Attemping to register transition blend twice: " + blend);
    }
    this.mutableTransitionBlends.add(blend);
    for (Listener listener : this.listeners) {
      listener.transitionBlendsChanged(this.lx);
    }
    return this;
  }

  /**
   * Register multiple channel blend classes with the engine
   *
   * @param blends List of blend classes
   * @return this
   */
  public LXRegistry addTransitionBlends(Class<LXBlend>[] blends) {
    checkRegistration();
    for (Class<LXBlend> blend : blends) {
      if (this.mutableTransitionBlends.contains(blend)) {
        throw new IllegalStateException("Attemping to register transition blend twice: " + blend);
      }
      this.mutableTransitionBlends.add(blend);
    }
    for (Listener listener : this.listeners) {
      listener.transitionBlendsChanged(this.lx);
    }
    return this;
  }

  /**
   * Register a crossfader blend class with the engine
   *
   * @param blend Blend class
   * @return this
   */
  public LXRegistry addCrossfaderBlend(Class<? extends LXBlend> blend) {
    Objects.requireNonNull(blend, "May not add null LXRegistry.addCrossfaderBlend");
    checkRegistration();
    if (this.mutableCrossfaderBlends.contains(blend)) {
      throw new IllegalStateException("Attemping to register crossfader blend twice: " + blend);
    }
    this.mutableCrossfaderBlends.add(blend);
    for (Listener listener : this.listeners) {
      listener.crossfaderBlendsChanged(this.lx);
    }
    return this;
  }


  /**
   * Register multiple crossfader blend classes with the engine
   *
   * @param blends List of blend classes
   * @return this
   */
  public LXRegistry addCrossfaderBlends(Class<LXBlend>[] blends) {
    checkRegistration();
    for (Class<LXBlend> blend : blends) {
      if (this.mutableCrossfaderBlends.contains(blend)) {
        throw new IllegalStateException("Attemping to register crossfader blend twice: " + blend);
      }
      this.mutableCrossfaderBlends.add(blend);
    }
    for (Listener listener : this.listeners) {
      listener.crossfaderBlendsChanged(this.lx);
    }
    return this;
  }

  protected void addPlugin(Class<? extends LXPlugin> plugin) {
    Objects.requireNonNull(plugin, "May not add null LXRegistry.addPlugin");
    this.mutablePlugins.add(new Plugin(plugin));
  }

  protected void initializePlugins() {
    for (Plugin plugin : this.plugins) {
      plugin.initialize(this.lx);
    }
  }

  private Plugin findPlugin(String clazz) {
    for (Plugin plugin : this.plugins) {
      if (plugin.clazz.getName().equals(clazz)) {
        return plugin;
      }
    }
    return null;
  }

  private static final String KEY_PLUGINS = "plugins";

  @Override
  public void save(LX lx, JsonObject object) {
    object.add(KEY_PLUGINS, LXSerializable.Utils.toArray(lx, this.plugins));
  }

  @Override
  public void load(LX lx, JsonObject object) {
    if (object.has(KEY_PLUGINS)) {
      for (JsonElement pluginElement : object.get(KEY_PLUGINS).getAsJsonArray()) {
        JsonObject pluginObj = pluginElement.getAsJsonObject();
        Plugin plugin = findPlugin(pluginObj.get(Plugin.KEY_CLASS).getAsString());
        if (plugin != null) {
          plugin.load(lx, pluginObj);
        }
      }
    }
  }

}
