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
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

import heronarts.lx.blend.AddBlend;
import heronarts.lx.blend.BurnBlend;
import heronarts.lx.blend.DarkestBlend;
import heronarts.lx.blend.DifferenceBlend;
import heronarts.lx.blend.DissolveBlend;
import heronarts.lx.blend.DodgeBlend;
import heronarts.lx.blend.HighlightBlend;
import heronarts.lx.blend.LXBlend;
import heronarts.lx.blend.LightestBlend;
import heronarts.lx.blend.MultiplyBlend;
import heronarts.lx.blend.NormalBlend;
import heronarts.lx.blend.SpotlightBlend;
import heronarts.lx.blend.SubtractBlend;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.modulator.LXModulator;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.pattern.PatternRack;
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
     * Invoked when available LXF fixtures have changed
     *
     * @param lx LX instance
     */
    default public void fixturesChanged(LX lx) {}

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
    this.listeners.remove(listener);
    return this;
  }

  private static final Class<?>[] DEFAULT_PATTERNS = {
    heronarts.lx.dmx.DmxPattern.class,
    heronarts.lx.pattern.PatternRack.class,
    heronarts.lx.pattern.audio.SoundObjectPattern.class,
    heronarts.lx.pattern.color.GradientPattern.class,
    heronarts.lx.pattern.color.SolidPattern.class,
    heronarts.lx.pattern.form.PlanesPattern.class,
    heronarts.lx.pattern.strip.ChasePattern.class,
    heronarts.lx.pattern.texture.NoisePattern.class,
    heronarts.lx.pattern.texture.SparklePattern.class,
    heronarts.lx.pattern.test.TestPattern.class,
  };

  private static final Class<?>[] DEFAULT_EFFECTS = {
    heronarts.lx.effect.audio.SoundObjectEffect.class,
    heronarts.lx.effect.BlurEffect.class,
    heronarts.lx.effect.FreezeEffect.class,
    heronarts.lx.effect.color.ColorizeEffect.class,
    heronarts.lx.effect.color.ColorMaskEffect.class,
    heronarts.lx.effect.color.GradientMaskEffect.class,
    heronarts.lx.effect.color.TransparifyEffect.class,
    heronarts.lx.effect.DynamicsEffect.class,
    heronarts.lx.effect.InvertEffect.class,
    heronarts.lx.effect.HueSaturationEffect.class,
    heronarts.lx.effect.LinearMaskEffect.class,
    heronarts.lx.effect.SparkleEffect.class,
    heronarts.lx.effect.StrobeEffect.class,
    heronarts.lx.effect.midi.GateEffect.class,
  };

  private static final Class<?>[] DEFAULT_MODULATORS = {
    heronarts.lx.audio.BandFilter.class,
    heronarts.lx.audio.BandGate.class,
    heronarts.lx.audio.SoundObject.class,
    heronarts.lx.dmx.DmxModulator.class,
    heronarts.lx.dmx.DmxColorModulator.class,
    heronarts.lx.modulator.BooleanLogic.class,
    heronarts.lx.modulator.ComparatorModulator.class,
    heronarts.lx.modulator.CycleModulator.class,
    heronarts.lx.modulator.Damper.class,
    heronarts.lx.modulator.Interval.class,
    heronarts.lx.modulator.MacroKnobs.class,
    heronarts.lx.modulator.MacroSwitches.class,
    heronarts.lx.modulator.MacroTriggers.class,
    heronarts.lx.modulator.MidiNoteTrigger.class,
    heronarts.lx.modulator.MultiStageEnvelope.class,
    heronarts.lx.modulator.MultiModeEnvelope.class,
    heronarts.lx.modulator.MultiTrig.class,
    heronarts.lx.modulator.NoiseModulator.class,
    heronarts.lx.modulator.OperatorModulator.class,
    heronarts.lx.modulator.Quantizer.class,
    heronarts.lx.modulator.Randomizer.class,
    heronarts.lx.modulator.Scaler.class,
    heronarts.lx.modulator.Smoother.class,
    heronarts.lx.modulator.Spring.class,
    heronarts.lx.modulator.Stepper.class,
    heronarts.lx.modulator.StepSequencer.class,
    heronarts.lx.modulator.Timer.class,
    heronarts.lx.modulator.VariableLFO.class,
  };

  private static final Class<?>[] DEFAULT_FIXTURES = {
    heronarts.lx.structure.ArcFixture.class,
    heronarts.lx.structure.GridFixture.class,
    heronarts.lx.structure.PointFixture.class,
    heronarts.lx.structure.SpiralFixture.class,
    heronarts.lx.structure.StripFixture.class,
  };

  private static final List<Class<? extends LXBlend>> DEFAULT_CHANNEL_BLENDS;
  static {
    DEFAULT_CHANNEL_BLENDS = new ArrayList<Class<? extends LXBlend>>();
    DEFAULT_CHANNEL_BLENDS.add(AddBlend.class);
    DEFAULT_CHANNEL_BLENDS.add(MultiplyBlend.class);
    DEFAULT_CHANNEL_BLENDS.add(SubtractBlend.class);
    DEFAULT_CHANNEL_BLENDS.add(DifferenceBlend.class);
    DEFAULT_CHANNEL_BLENDS.add(NormalBlend.class);
    DEFAULT_CHANNEL_BLENDS.add(DodgeBlend.class);
    DEFAULT_CHANNEL_BLENDS.add(BurnBlend.class);
    DEFAULT_CHANNEL_BLENDS.add(HighlightBlend.class);
    DEFAULT_CHANNEL_BLENDS.add(SpotlightBlend.class);
    DEFAULT_CHANNEL_BLENDS.add(LightestBlend.class);
    DEFAULT_CHANNEL_BLENDS.add(DarkestBlend.class);
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

  /**
   * The list of globally registered pattern classes
   */
  private final List<Class<? extends LXPattern>> mutablePatterns = new ArrayList<>(DEFAULT_PATTERNS.length);

  public final List<Class<? extends LXPattern>> patterns =
    Collections.unmodifiableList(this.mutablePatterns);

  private final List<Class<? extends LXEffect>> mutableEffects = new ArrayList<>(DEFAULT_EFFECTS.length);

  /**
   * The list of globally registered effects
   */
  public final List<Class<? extends LXEffect>> effects =
    Collections.unmodifiableList(this.mutableEffects);

  private final List<Class<? extends LXModulator>> mutableModulators = new ArrayList<>(DEFAULT_MODULATORS.length);

  /**
   * The list of globally registered effects
   */
  public final List<Class<? extends LXModulator>> modulators =
    Collections.unmodifiableList(this.mutableModulators);

  private final List<Class<? extends LXFixture>> mutableFixtures = new ArrayList<>(DEFAULT_FIXTURES.length);

  /**
   * List of globally registered fixtures.
   */
  public final List<Class<? extends LXFixture>> fixtures =
    Collections.unmodifiableList(this.mutableFixtures);

  private final Map<Class<? extends LXComponent>, List<String>> mutableTags = new HashMap<>();

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

  /**
   * JSON fixture type
   */
  public class JsonFixture {

    public class Error {
      public final String path;
      public final String type;
      public final Exception exception;

      Error(String prefix, File file, String type, Exception exception) {
        this.path = prefix + file.getName();
        this.type = type;
        this.exception = exception;
      }
    }

    public final String type;
    public final boolean isVisible;

    private static final String KEY_IS_VISIBLE = "isVisible";

    private JsonFixture(File file, String prefix) {
      String fileName = prefix + file.getName();
      boolean isVisible = false;

      try (FileReader fr = new FileReader(file)) {
        JsonObject obj = new Gson().fromJson(fr, JsonObject.class);
        if (obj == null) {
          LX.error("JSON fixture file is empty: " + file.getAbsolutePath());
          mutableJsonFixtureErrors.add(new Error(prefix, file, "Syntax error", new Exception("File is empty")));
        } else {
          isVisible = !obj.has(KEY_IS_VISIBLE) || obj.get(KEY_IS_VISIBLE).getAsBoolean();
        }
      } catch (JsonSyntaxException jsx) {
        LX.error(jsx, "JSON fixture file has invalid syntax: " + file.getAbsolutePath());
        mutableJsonFixtureErrors.add(new Error(prefix, file, "Syntax error", jsx));
      } catch (JsonParseException jpx) {
        LX.error(jpx, "JSON fixture file is not valid JSON: " + file.getAbsolutePath());
        mutableJsonFixtureErrors.add(new Error(prefix, file, "Parse error", jpx));
      } catch (FileNotFoundException fnfx) {
        LX.error(fnfx, "JSON fixture file does not exist: " + file.getAbsolutePath());
      } catch (Exception x) {
        LX.error(x, "Error reading JSON fixture file: " + file.getAbsolutePath());
        mutableJsonFixtureErrors.add(new Error(prefix, file, "I/O error", x));
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

  private final List<JsonFixture.Error> mutableJsonFixtureErrors = new ArrayList<JsonFixture.Error>();

  public final List<JsonFixture.Error> jsonFixtureErrors = Collections.unmodifiableList(this.mutableJsonFixtureErrors);

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
    public LXPlugin instance = null;
    private boolean hasError = false;
    private boolean isEnabled = false;
    private Throwable exception = null;
    private final boolean cliEnabled;
    private final boolean trusted;

    private Plugin(Class<? extends LXPlugin> clazz) {
      this(clazz, false);
    }

    private Plugin(Class<? extends LXPlugin> clazz, boolean trusted) {
      this.clazz = clazz;
      this.cliEnabled = isPluginCliEnabled(clazz);
      this.isEnabled = restorePluginEnabled(clazz);
      this.trusted = trusted;
    }

    public boolean isTrusted() {
      return this.trusted;
    }

    private boolean isPluginCliEnabled(Class<? extends LXPlugin> clazz) {
      return
        lx.flags.enabledPlugins.contains(clazz.getName()) ||
        lx.flags.classpathPlugins.contains(clazz.getName());
    }

    private boolean restorePluginEnabled(Class<? extends LXPlugin> clazz) {
      if (this.cliEnabled) {
        return true;
      }
      try {
        for (JsonElement elem : pluginState) {
          final JsonObject plugin = elem.getAsJsonObject();
          if (plugin.get(KEY_CLASS).getAsString().equals(clazz.getName())) {
            return plugin.get(KEY_ENABLED).getAsBoolean();
          }
        }
      } catch (Exception x) {
        LX.error(x, "Error parsing saved plugin state: " + pluginState);
      }
      return false;
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
      if (!lx.permissions.canRunPlugins()) {
        return;
      }
      if (!this.isEnabled) {
        return;
      }
      try {
        try {
          this.instance = clazz.getConstructor(LX.class).newInstance(lx);
        } catch (NoSuchMethodException nsmx) {
          this.instance = clazz.getConstructor().newInstance();
        }
        this.instance.initialize(lx);
      } catch (Throwable x) {
        LX.error(x, "Unhandled error in plugin initialize: " + clazz.getName());
        lx.pushError(x, "Error on initialization of plugin " + clazz.getSimpleName() + "\n" + x.getLocalizedMessage());
        setException(x);
      }
    }

    public Plugin setException(Throwable x) {
      this.hasError = true;
      this.exception = x;
      for (Listener listener : listeners) {
        listener.pluginChanged(lx, this);
      }
      return this;
    }

    public Throwable getException() {
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
      if (!this.cliEnabled) {
        if (object.has(KEY_ENABLED)) {
          this.isEnabled = object.get(KEY_ENABLED).getAsBoolean();
        }
      }
    }

    public void dispose() {
      if (this.instance != null) {
        try {
          this.instance.dispose();
        } catch (Exception x) {
          LX.error(x, "Unhandled exception in plugin dispose: " + clazz.getName());
          lx.pushError(x, "Error on plugin dispose " + clazz.getSimpleName() + "\n" + x.getLocalizedMessage());
          setException(x);
        }
      }
    }
  }

  public final LX lx;

  protected LXClassLoader classLoader;

  private boolean contentReloading = false;

  private WatchService watchService = null;

  public LXRegistry(LX lx) {
    this.lx = lx;
    this.classLoader = new LXClassLoader(lx);

    this.contentReloading = true;
    for (Class<?> pattern : DEFAULT_PATTERNS) {
      addPattern(pattern.asSubclass(LXPattern.class));
    }
    for (Class<?> effect : DEFAULT_EFFECTS) {
      addEffect(effect.asSubclass(LXEffect.class));
    }
    for (Class<?> modulator : DEFAULT_MODULATORS) {
      addModulator(modulator.asSubclass(LXModulator.class));
    }
    for (Class<?> fixture : DEFAULT_FIXTURES) {
      addFixture(fixture.asSubclass(LXFixture.class));
    }
    this.contentReloading = false;
  }

  public LXClassLoader getClassLoader() {
    return this.classLoader;
  }

  public Class<?> getClass(String className) throws ClassNotFoundException {
    return Class.forName(className, true, this.classLoader);
  }

  protected void initialize() {
    this.contentReloading = true;
    this.classLoader.load();
    loadClasspathPlugins();
    addJsonFixtures(lx.getMediaFolder(LX.Media.FIXTURES, false));
    this.contentReloading = false;
  }

  private void loadClasspathPlugins() {
    for (String className : this.lx.flags.classpathPlugins) {
      try {
        Class<?> clz = Class.forName(className);
        if (LXPlugin.class.isAssignableFrom(clz)) {
          addPlugin(clz.asSubclass(LXPlugin.class));
        } else {
          LX.error("Classpath plugin is not an LXPlugin subclass: " + className);
        }
      } catch (ClassNotFoundException cnfx) {
        LX.error(cnfx, "Classpath plugin class does not exist: " + className);
      }
    }
  }

  public void checkRegistration() {
    if (!this.contentReloading && this.lx.engine.hasStarted) {
      throw new IllegalStateException("May not register components outside of initialize() callback");
    }
  }

  void enableWatchService(boolean enabled) {
    if (enabled) {
      if (this.watchService == null) {
        final Path path = this.lx.getMediaFolder(LX.Media.PACKAGES).toPath();
        LX.debug("Registering package directory with LXRegistry.WatchService: " + path);
        try {
          this.watchService = FileSystems.getDefault().newWatchService();
          path.register(this.watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
        } catch (IOException iox) {
          LX.error(iox, "Failed to register LXRegistry.WatchService");
          if (this.watchService != null) {
            try {
              this.watchService.close();
            } catch (IOException iox2) {
              LX.error(iox2, "Error closing LXRegistry.WatchService in error handler");
            }
          }
          this.watchService = null;
        }
      }
    } else {
      if (this.watchService != null) {
        LX.debug("Closing package directory LXRegistry.WatchService");
        try {
          this.watchService.close();
        } catch (IOException iox) {
          LX.error(iox, "Error closing LXRegistry.WatchService");
        }
        this.watchService = null;
      }
    }
  }

  public void runWatchService() {
    if (this.watchService == null) {
      return;
    }

    final boolean autoReload = this.lx.preferences.autoReloadPackages.isOn();
    boolean changed = false;
    WatchKey watchKey = null;
    List<LXClassLoader.Package> modifiedPackages = null;
    while ((watchKey = this.watchService.poll()) != null) {
      for (WatchEvent<?> event : watchKey.pollEvents()) {
        final Path path = (Path) event.context();
        LX.log("Detected change " + event.kind() + " to package file: " + path);
        changed = true;
        if (autoReload && (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY)) {
          final LXClassLoader.Package pkg = getPackage(path);
          if (pkg != null) {
            if (modifiedPackages == null) {
              modifiedPackages = new ArrayList<>();
            }
            modifiedPackages.add(pkg);
          }
        }
      }
      watchKey.reset();
    }
    if (changed && autoReload) {
      reloadContent();
      if (modifiedPackages != null) {
        for (LXClassLoader.Package pkg : modifiedPackages) {
          reloadPackageDevices(pkg);
        }
      }
    }
  }

  void closeWatchService() {
    if (this.watchService != null) {
      try {
        this.watchService.close();
      } catch (IOException iox) {
        LX.error(iox, "Could not close LXRegistry.WatchService");
      }
      this.watchService = null;
    }
  }

  private LXClassLoader.Package getPackage(Path path) {
    for (LXClassLoader.Package pkg : this.packages) {
      if (pkg.jarFile.equals(this.lx.getMediaFile(LX.Media.PACKAGES, path.toString(), false))) {
        return pkg;
      }
    }
    LX.error("Could not find LXClassLoader.Package for modified path: " + path);
    return null;
  }

  private void reloadPackageDevices(LXClassLoader.Package pkg) {
    for (LXAbstractChannel bus : this.lx.engine.mixer.channels) {
      if (bus instanceof LXChannel channel) {
        reloadPackagePatterns(pkg, channel.patterns);
      }
      reloadPackageEffects(pkg, bus.effects);
    }
    reloadPackageEffects(pkg, lx.engine.mixer.masterBus.effects);
  }

  private void reloadPackagePatterns(LXClassLoader.Package pkg, List<LXPattern> patterns) {
    if (!patterns.isEmpty()) {
      new ArrayList<LXPattern>(patterns).forEach(pattern -> {
        if (pkg.hasClass(pattern.getClass())) {
          LX.debug("Reloading pattern: " + pattern);
          pattern.reload();
        } else {
          if (pattern instanceof PatternRack rack) {
            reloadPackagePatterns(pkg, rack.patterns);
          }
          reloadPackageEffects(pkg, pattern.effects);
        }
      });
    }
  }

  private void reloadPackageEffects(LXClassLoader.Package pkg, List<LXEffect> effects) {
    if (!effects.isEmpty()) {
      new ArrayList<LXEffect>(effects).forEach(effect -> {
        if (pkg.hasClass(effect.getClass())) {
          LX.debug("Reloading effect: " + effect);
          effect.reload();
        }
      });
    }
  }

  public void reloadContent() {
    reloadContent(true);
  }

  private void reloadContent(boolean disposeClassLoader) {
    LX.log("Reloading custom content folders");
    if (disposeClassLoader) {
      this.classLoader.dispose();
    }
    this.mutablePackages.clear();
    this.mutablePlugins.clear();

    this.contentReloading = true;

    // The previous classLoader is now disposed. Note that the classes it defined
    // may still be in use, e.g. via live patterns or effects. But we've released our
    // handle to it. Those classes will be garbage collected when they have no more
    // references. And all our new instantiations will use the new version of the Class
    // objects defined by a new instance of the LXClassLoader.
    this.classLoader = new LXClassLoader(this.lx);
    this.classLoader.load();

    loadClasspathPlugins();

    // Reload the available JSON fixture list
    reloadJsonFixtures();

    // We are done reloading
    this.contentReloading = false;

    // Notify listeners of change
    for (Listener listener : this.listeners) {
      listener.contentChanged(this.lx);
    }

    this.lx.pushStatusMessage("Package content reloaded.");
  }

  public void reloadJsonFixtures() {
    final boolean wasReloading = this.contentReloading;
    this.contentReloading = true;
    this.mutableJsonFixtures.clear();
    this.mutableJsonFixtureErrors.clear();
    addJsonFixtures(lx.getMediaFolder(LX.Media.FIXTURES, false));
    this.contentReloading = wasReloading;
    for (Listener listener : this.listeners) {
      listener.fixturesChanged(this.lx);
    }
  }

  void addPackage(LXClassLoader.Package pack) {
    this.mutablePackages.add(pack);
  }

  public boolean installPackage(File file) {
    return installPackage(file, false);
  }

  public void reinstallPackageMedia(LXClassLoader.Package pack) {
    try {
      installPackageMedia(pack.jarFile);
      reloadContent();
    } catch (Throwable x) {
      this.lx.pushError(x, "Error re-installing package media " + pack.jarFile.getName() + ": " + x.getLocalizedMessage());
    }
  }

  public boolean installPackage(File file, boolean overwrite) {
    if (!file.exists() || file.isDirectory()) {
      this.lx.pushError(null, "Package file does not exist or is a directory: " + file);
      return false;
    }
    File destinationFile = lx.getMediaFile(LX.Media.PACKAGES, file.getName(), true);
    if (destinationFile.exists() && !overwrite) {
      this.lx.pushError(null, "Package file already exists: " + destinationFile.getName());
      return false;
    }
    try {
      // Close the classloader first, otherwise on Windows it may hold handles to
      // destinationFile and bork the Files.copy() call
      this.classLoader.dispose();
      Files.copy(file.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      installPackageMedia(destinationFile);
      reloadContent(false);
    } catch (Throwable x) {
      LX.error(x, "Error installing package file " + file.getName() + ": " + x.getLocalizedMessage());
      this.lx.pushError(x, "Error installing package file " + file.getName() + ": " + x.getLocalizedMessage());
      return false;
    }
    return true;
  }

  private static final String PACKAGE_MEDIA_DIR = "mediaDir";

  private boolean packageMediaConflicts = false;

  private void installPackageMedia(File file) {
    this.packageMediaConflicts = false;
    try (JarFile jarFile = new JarFile(file)) {
      JarEntry packageEntry = jarFile.getJarEntry(LXClassLoader.PACKAGE_DESCRIPTOR_FILE_NAME);
      if (packageEntry == null) {
        this.lx.pushError("Package is missing lx.package entry, cannot install media: " + jarFile.getName());
        return;
      }
      JsonObject obj = new Gson().fromJson(new InputStreamReader(jarFile.getInputStream(packageEntry)), JsonObject.class);
      if (!obj.has(PACKAGE_MEDIA_DIR)) {
        this.lx.pushError("Package does not specify \"" + PACKAGE_MEDIA_DIR + "\", cannot install media: " + jarFile.getName());
        return;
      }
      String packageDir = obj.get(PACKAGE_MEDIA_DIR).getAsString();

      Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        final JarEntry entry = entries.nextElement();
        final String fileName = entry.getName();
        if (fileName.startsWith("fixtures/") && !entry.isDirectory()) {
          copyPackageMedia(packageDir, LX.Media.FIXTURES, jarFile, entry);
        } else if (fileName.startsWith("models/") && fileName.endsWith(".lxm")) {
          copyPackageMedia(packageDir, LX.Media.MODELS, jarFile, entry);
        } else if (fileName.startsWith("projects/") && fileName.endsWith(".lxp")) {
          copyPackageMedia(packageDir, LX.Media.PROJECTS, jarFile, entry);
        } else if (fileName.startsWith("scripts/") && fileName.endsWith(".js")) {
          copyPackageMedia(packageDir, LX.Media.SCRIPTS, jarFile, entry);
        } else if (fileName.startsWith("colors/") && fileName.endsWith(".lxc")) {
          copyPackageMedia(packageDir, LX.Media.COLORS, jarFile, entry);
        } else if (fileName.startsWith("views/") && fileName.endsWith(".lxv")) {
          copyPackageMedia(packageDir, LX.Media.VIEWS, jarFile, entry);
        } else if (fileName.startsWith("presets/") && fileName.endsWith(".lxd")) {
          copyPackageMedia(packageDir, LX.Media.PRESETS, jarFile, entry);
        } else if (fileName.startsWith("data/") && !entry.isDirectory()) {
          copyPackageMedia(packageDir, LX.Media.DATA, jarFile, entry);
        }
      }
    } catch (Throwable throwable) {
      LX.error(throwable, "Error loading JAR file " + file + " - " + throwable.getLocalizedMessage());
    }
    if (this.packageMediaConflicts) {
      this.lx.pushError("Package media files conflict with existing files, backups were made. See log for details.");
    }
  }

  private static final DateFormat BACKUP_DATE_FORMAT = new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss");

  private void copyPackageMedia(String packageDirName, LX.Media media, JarFile jarFile, JarEntry entry) throws IOException {
    // Lop off the first package media type name
    String entryName = entry.getName();
    entryName = entryName.substring(entryName.indexOf('/') + 1);

    // Make a directory for the package media of this type
    File packageDir = (media == LX.Media.PRESETS) ?
      this.lx.getMediaFolder(media, true) :
      this.lx.getMediaFile(media, packageDirName, true);

    // For presets, fork package name after device
    if (media == LX.Media.PRESETS) {
      int firstSlash = entryName.indexOf('/');
      if (firstSlash < 0) {
        entryName = packageDirName + '/' + entryName;
      } else {
        entryName =
          entryName.substring(0, firstSlash) +
          '/' + packageDirName +
          entryName.substring(firstSlash);
      }
    }

    // Are their subdirs within this package's content? Break up if so...
    int lastSlash = entryName.lastIndexOf('/');
    if (lastSlash >= 0) {
      String subdir = entryName.substring(0, lastSlash);
      packageDir = new File(packageDir, subdir.replaceAll("/", File.separator));
      entryName = entryName.substring(lastSlash + 1);
    }

    // Ensure package subdirs exist
    packageDir.mkdirs();

    // Make backups if clobbering existing content
    final File destinationFile = new File(packageDir, entryName);

    if (!destinationFile.exists()) {
      // Just copy the file over
      Files.copy(
        jarFile.getInputStream(entry),
        destinationFile.toPath(),
        StandardCopyOption.REPLACE_EXISTING
      );
    } else {

      final Path destinationFilePath = destinationFile.toPath();
      final Path tmpFilePath = new File(packageDir, entryName + ".tmp").toPath();

      // Write to a tmp file, check for changes
      Files.copy(
        jarFile.getInputStream(entry),
        tmpFilePath,
        StandardCopyOption.REPLACE_EXISTING
      );
      if (Files.mismatch(destinationFilePath, tmpFilePath) < 0) {
        // No changes, nuke this tmp file
        Files.delete(tmpFilePath);
      } else {
        // Note the conflict, backup the existing file, put the temp file into its place
        this.packageMediaConflicts = true;
        final String timestamp = BACKUP_DATE_FORMAT.format(Calendar.getInstance().getTime());
        final Path backupFilePath = new File(packageDir, entryName + "-" + timestamp + ".backup").toPath();
        Files.move(
          destinationFilePath,
          backupFilePath,
          StandardCopyOption.REPLACE_EXISTING
        );
        Files.move(
          tmpFilePath,
          destinationFilePath,
          StandardCopyOption.REPLACE_EXISTING
        );
        LX.error("Package media file conflict, backed up to: " + backupFilePath.toString());
      }
    }
  }

  public void uninstallPackage(LXClassLoader.Package pack) {
    uninstallPackage(pack, true);
  }

  public void uninstallPackage(LXClassLoader.Package pack, boolean reload) {
    File destinationFile = lx.getMediaFile(LX.Media.DELETED, pack.jarFile.getName(), true);
    try {
      if (destinationFile.exists()) {
        final String suffix =
          new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss")
          .format(Calendar.getInstance().getTime());
        destinationFile = lx.getMediaFile(LX.Media.DELETED, pack.jarFile.getName() + "-" + suffix, true);
      }
      Files.move(pack.jarFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      if (reload) {
        reloadContent();
      }
    } catch (IOException iox) {
      LX.error(iox, "Could not remove package file " + pack.jarFile.getName());
      this.lx.pushError(iox, "Could not remove package file " + pack.jarFile.getName());
    }
  }

  /**
   * Find a package that matches the package name in the given file
   *
   * @param file Package file
   * @return Existing package which matches, or null if none exists
   */
  public LXClassLoader.Package findPackage(File file) {
    final String packageName = this.classLoader.loadPackageName(file);
    if (packageName != null) {
      for (LXClassLoader.Package pkg : this.packages) {
        if (packageName.equals(pkg.getName())) {
          return pkg;
        }
      }
    }
    return null;
  }

  public enum ComponentType {
    PATTERN(LXPattern.class, "pattern"),
    EFFECT(LXEffect.class, "effect"),
    MODULATOR(LXModulator.class, "modulator"),
    FIXTURE(LXFixture.class, "fixture"),
    PLUGIN(LXPlugin.class, "plugin");

    public final Class<?> componentClass;
    public final String label;

    private ComponentType(Class<?> componentClass, String label) {
      this.componentClass = componentClass;
      this.label = label;
    }
  }

  protected ComponentType getInstantiableComponentType(Class<?> clz) {
    for (ComponentType componentType : ComponentType.values()) {
      if (componentType.componentClass.isAssignableFrom(clz)) {
        return componentType;
      }
    }
    return null;
  }

  protected void addClass(Class<?> clz, LXClassLoader.Package pack) {
    if (LXPattern.class.isAssignableFrom(clz)) {
      addPattern(clz.asSubclass(LXPattern.class));
    }
    if (LXEffect.class.isAssignableFrom(clz)) {
      addEffect(clz.asSubclass(LXEffect.class));
    }
    if (LXModulator.class.isAssignableFrom(clz)) {
      addModulator(clz.asSubclass(LXModulator.class));
    }
    if (LXFixture.class.isAssignableFrom(clz)) {
      addFixture(clz.asSubclass(LXFixture.class));
    }
    if (LXPlugin.class.isAssignableFrom(clz)) {
      addPlugin(clz.asSubclass(LXPlugin.class), pack.trusted);
    }
  }

  protected void removeClass(Class<?> clz) {
    if (LXPattern.class.isAssignableFrom(clz)) {
      removePattern(clz.asSubclass(LXPattern.class));
    }
    if (LXEffect.class.isAssignableFrom(clz)) {
      removeEffect(clz.asSubclass(LXEffect.class));
    }
    if (LXModulator.class.isAssignableFrom(clz)) {
      removeModulator(clz.asSubclass(LXModulator.class));
    }
    if (LXFixture.class.isAssignableFrom(clz)) {
      removeFixture(clz.asSubclass(LXFixture.class));
    }
    this.mutableTags.remove(clz);

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
    addDefaultTags(pattern);
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
    addDefaultTags(effect);
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

  private void addDefaultTags(Class<? extends LXComponent> component) {
    final LXComponent.Tags tags = component.getAnnotation(LXComponent.Tags.class);
    if (tags != null) {
      for (String tag : tags.value()) {
        addTag(component, tag);
      }
    }
  }

  /**
   * Add a tag to the given component type
   *
   * @param component Component class
   * @param tag Tag
   * @return this
   */
  public LXRegistry addTag(Class<? extends LXComponent> component, String tag) {
    if (tag == null) {
      throw new IllegalArgumentException("Tag may not be null for class " + component.getName());
    } else if (!tag.matches("^[a-zA-Z0-9/]+$")) {
      throw new IllegalArgumentException("Tag '" + tag + "' for class " + component.getName() + " contains illegal characters - only alphanumerics and slashes allowed");
    }
    List<String> tags = this.mutableTags.get(component);
    if (tags == null) {
      tags = new ArrayList<String>();
      this.mutableTags.put(component, tags);
    }
    if (tags.contains(tag)) {
      LX.error("Cannot add duplicate tag \"" + tag + "\" to class " + component.getName());
    } else {
      tags.add(tag);
    }
    return this;
  }

  /**
   * Remove a tag from the given component type
   *
   * @param component Component type
   * @param tag Tag
   * @return this
   */
  public LXRegistry removeTag(Class<? extends LXComponent> component, String tag) {
    final List<String> tags = this.mutableTags.get(component);
    if (tags == null || !tags.contains(tag)) {
      LX.error("Cannot remove non-existent tag \"" + tag + "\" from class " + component.getName());
      return this;
    }
    tags.remove(tag);
    if (tags.isEmpty()) {
      this.mutableTags.remove(component);
    }
    return this;
  }

  /**
   * Get the set of tags for a given component class
   *
   * @param component Component type
   * @return An unmodifiable list of tags, or null if no tags exist
   */
  public List<String> getTags(Class<? extends LXComponent> component) {
    final List<String> tags = this.mutableTags.get(component);
    return (tags == null) ? null : Collections.unmodifiableList(tags);
  }

  /**
   * Whether a given tag is a default tag for the component type
   *
   * @param component Component type
   * @param tag Tag
   * @return Whether this tag is a default annotation-tag for the component type
   */
  public boolean isDefaultTag(Class<? extends LXComponent> component, String tag) {
    final LXComponent.Tags tags = component.getAnnotation(LXComponent.Tags.class);
    if (tags != null) {
      for (String candidate : tags.value()) {
        if (candidate.equals(tag)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Register a modulator class with the engine
   *
   * @param modulator Modulator class
   * @return this
   */
  public LXRegistry addModulator(Class<? extends LXModulator> modulator) {
    Objects.requireNonNull(modulator, "May not add null LXRegistry.addModulator");
    checkRegistration();
    if (this.mutableModulators.contains(modulator)) {
      throw new IllegalStateException("Attemping to register modulator twice: " + modulator);
    }
    this.mutableModulators.add(modulator);
    addDefaultTags(modulator);
    return this;
  }

  /**
   * Register an array of modulator classes with the engine
   *
   * @param modulators List of modulator classes
   * @return this
   * @deprecated Use addModulators without typo
   */
  @Deprecated
  public LXRegistry addModulataors(Class<? extends LXModulator>[] modulators) {
    return addModulators(modulators);
  }

  /**
   * Register an array of modulator classes with the engine
   *
   * @param modulators List of modulator classes
   * @return this
   */
  public LXRegistry addModulators(Class<? extends LXModulator>[] modulators) {
    checkRegistration();
    for (Class<? extends LXModulator> modulator : modulators) {
      addModulator(modulator);
    }
    return this;
  }

  /**
   * Unregister modulator class with the engine
   *
   * @param modulator Modulator class
   * @return this
   */
  public LXRegistry removeModulator(Class<? extends LXModulator> modulator) {
    if (!this.mutableModulators.contains(modulator)) {
      throw new IllegalStateException("Attemping to unregister modulator that does not exist: " + modulator);
    }
    this.mutableModulators.remove(modulator);
    return this;
  }

  /**
   * Unregister modulators classes with the engine
   *
   * @param modulators Modulators classes
   * @return this
   */
  public LXRegistry removeModulators(List<Class<? extends LXModulator>> modulators) {
    for (Class<? extends LXModulator> modulator : modulators) {
      if (!this.mutableModulators.contains(modulator)) {
        throw new IllegalStateException("Attemping to unregister modulator that does not exist: " + modulator);
      }
      this.mutableModulators.remove(modulator);
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
    addDefaultTags(fixture);
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
          addJsonFixtures(fixture, prefix + fixture.getName() + heronarts.lx.structure.JsonFixture.PATH_SEPARATOR);
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
    addPlugin(plugin, false);
  }

  protected void addPlugin(Class<? extends LXPlugin> plugin, boolean trusted) {
    Objects.requireNonNull(plugin, "May not add null LXRegistry.addPlugin");
    this.mutablePlugins.add(new Plugin(plugin, trusted));
  }

  protected void initializePlugins() {
    for (Plugin plugin : this.plugins) {
      plugin.initialize(this.lx);
    }
  }

  protected void disposePlugins() {
    for (Plugin plugin : this.plugins) {
      plugin.dispose();
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

  public boolean isPluginClassEnabled(Class<? extends LXPlugin> pluginClass) {
    for (Plugin plugin : this.plugins) {
      if (plugin.clazz.equals(pluginClass) && plugin.isEnabled && !plugin.hasError) {
        return true;
      }
    }
    return false;
  }

  private JsonArray pluginState = new JsonArray();

  private static final String KEY_PLUGINS = "plugins";

  @Override
  public void save(LX lx, JsonObject object) {
    object.add(KEY_PLUGINS, this.pluginState = LXSerializable.Utils.toArray(lx, this.plugins));
  }

  @Override
  public void load(LX lx, JsonObject object) {
    if (object.has(KEY_PLUGINS)) {
      this.pluginState = object.get(KEY_PLUGINS).getAsJsonArray().deepCopy();
      for (JsonElement pluginElement : this.pluginState) {
        JsonObject pluginObj = pluginElement.getAsJsonObject();
        Plugin plugin = findPlugin(pluginObj.get(Plugin.KEY_CLASS).getAsString());
        if (plugin != null) {
          plugin.load(lx, pluginObj);
        }
      }
    } else {
      this.pluginState = new JsonArray();
    }
  }
}
