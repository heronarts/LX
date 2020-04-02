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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
import heronarts.lx.model.LXModel;
import heronarts.lx.pattern.LXPattern;

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
     * @param lx instance
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
    DEFAULT_PATTERNS.add(heronarts.lx.pattern.GradientPattern.class);
    DEFAULT_PATTERNS.add(heronarts.lx.pattern.IteratorPattern.class);
  };

  private static final List<Class<? extends LXEffect>> DEFAULT_EFFECTS;
  static {
    DEFAULT_EFFECTS = new ArrayList<Class<? extends LXEffect>>();
    DEFAULT_EFFECTS.add(heronarts.lx.effect.BlurEffect.class);
    DEFAULT_EFFECTS.add(heronarts.lx.effect.DesaturationEffect.class);
    DEFAULT_EFFECTS.add(heronarts.lx.effect.InvertEffect.class);
    DEFAULT_EFFECTS.add(heronarts.lx.effect.StrobeEffect.class);
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

  private final List<Class<? extends LXModel>> mutableModels =
    new ArrayList<Class<? extends LXModel>>();

  /**
   * List of globally registered models.
   */
  public final List<Class<? extends LXModel>> models =
    Collections.unmodifiableList(this.mutableModels);

  private final List<String> mutableFixtures = new ArrayList<String>();

  /**
   * The list of globally registered fixture types
   */
  public final List<String> fixtures =
    Collections.unmodifiableList(this.mutableFixtures);

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
    private boolean isEnabled = true;
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
    this.contentReloading = true;
    this.classLoader = new LXClassLoader(lx);
    addFixtures(lx.getMediaFolder(LX.Media.FIXTURES, false));
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
    this.contentReloading = true;

    // The previous contentLoader is now disposed. Note that the classes it defined
    // may still be in use, e.g. via live patterns or effects. But we've released our
    // handle to it. Those classes will be garbage collected when they have no more
    // references. And all our new instantiations will use the new version of the Class
    // objects defined by a new instance of the LXClassLoader.
    this.classLoader = new LXClassLoader(this.lx);

    // We are done reloading
    this.contentReloading = false;

    // Notify listeners of change
    for (Listener listener : this.listeners) {
      listener.contentChanged(this.lx);
    }
  }

  /**
   * Register a pattern class with the engine
   *
   * @param pattern Pattern class
   * @return this
   */
  public LXRegistry addPattern(Class<? extends LXPattern> pattern) {
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
   * Register a pattern class with the engine
   *
   * @param pattern Pattern class
   * @return this
   */
  public LXRegistry addModel(Class<? extends LXModel> model) {
    checkRegistration();
    if (this.mutableModels.contains(model)) {
      throw new IllegalStateException("Attemping to register model twice: " + model);
    }
    this.mutableModels.add(model);
    return this;
  }

  /**
   * Register a pattern class with the engine
   *
   * @param patterns List of pattern classes
   * @return this
   */
  public LXRegistry addModels(Class<? extends LXModel>[] models) {
    checkRegistration();
    for (Class<? extends LXModel> model : models) {
      addModel(model);
    }
    return this;
  }

  /**
   * Unregister pattern classes with the engine
   *
   * @param patterns Pattern classes
   * @return this
   */
  public LXRegistry removeModels(List<Class<? extends LXModel>> models) {
    for (Class<? extends LXModel> model : models) {
      if (!this.mutableModels.contains(model)) {
        throw new IllegalStateException("Attemping to unregister model that does not exist: " + model);
      }
      this.mutableModels.remove(model);
    }
    return this;
  }

  public LXRegistry addFixture(String fixtureName) {
    checkRegistration();
    if (this.mutableFixtures.contains(fixtureName)) {
      throw new IllegalStateException("Cannot double-register fixture: " + fixtureName);
    }
    this.mutableFixtures.add(fixtureName);
    return this;
  }

  /**
   * Register a [channel and crossfader] blend class with the engine
   *
   * @param blend Blend class
   * @return this
   */
  public LXRegistry addBlend(Class<? extends LXBlend> blend) {
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

  public void addFixtures(File fixtureDir) {
    if (fixtureDir.exists() && fixtureDir.isDirectory()) {
      for (String fixture : fixtureDir.list()) {
        if (fixture.endsWith(".lxf")) {
          addFixture(fixture.substring(0, fixture.length() - ".lxf".length()));
        }
      }
    }
  }

  protected void addPlugin(Class<? extends LXPlugin> plugin) {
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
