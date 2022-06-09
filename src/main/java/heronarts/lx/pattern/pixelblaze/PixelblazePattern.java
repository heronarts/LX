/**
 * Copyright 2022- Ben Hencke
 * Copyright 2022- Mark C. Slee, Heron Arts LLC
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
 * @author Ben Hencke <hencke@gmail.com>
 * @author Mark C. Slee <mark@heronarts.com>
 */

package heronarts.lx.pattern.pixelblaze;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.pattern.LXPattern;

public class PixelblazePattern extends LXPattern {

  private static final String KEY_SCRIPT_NAME = "scriptName";

  public static final int RENDER_ERROR_LOG_INTERVAL_MS = 5_000;

  private Wrapper wrapper;

  private long lastLogMs = 0; // to prevent spamming the logs with script errors

  private final LinkedHashMap<String, CompoundParameter> mutableSliders = new LinkedHashMap<String, CompoundParameter>();

  /**
   * Immutable public view of the JS slider parameters
   */
  public final Collection<CompoundParameter> sliders = Collections.unmodifiableCollection(mutableSliders.values());

  public final StringParameter scriptName = new StringParameter("Script Name", "test.js").setDescription("Path to the Pixelblaze script");

  public final BooleanParameter reset =
    new BooleanParameter("Reset", false)
    .setMode(BooleanParameter.Mode.MOMENTARY)
    .setDescription("Resets the Pixelblaze JS engine for this script");

  public final MutableParameter onReload = new MutableParameter("Reload");
  public final StringParameter error = new StringParameter("Error", null);

  public PixelblazePattern(LX lx) {
    super(lx);
    addParameter("reset", this.reset);
    addParameter(KEY_SCRIPT_NAME, this.scriptName);
    reloadWrapper();
  }

  private void reloadWrapper() {
    this.error.setValue(null);
    this.wrapper = null;
    clearSliders();

    String scriptName = getScriptName();
    if (scriptName.isEmpty()) {
      // Nothing to do here...
      this.onReload.bang();
    } else {
      try {
        this.wrapper = new Wrapper(scriptName, getPixelblazePoints(model));
        this.wrapper.load();
      } catch (Exception x) {
        LX.error(x, "Error loading Pixelblaze script " + getScriptName() + ":" + x.getMessage());
        this.error.setValue(x.getMessage());
      }
    }
  }

  @Override
  public void onModelChanged(LXModel model) {
    super.onModelChanged(model);
    if (this.wrapper != null) {
      try {
        setColors(0);
        this.wrapper.setPoints(getPixelblazePoints(model));
      } catch (Exception x) {
        LX.error(x, "Error updating points:" + x.getMessage());
      }
    }
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    if (p == this.scriptName || p == this.reset) {
      reloadWrapper();
    }
  }

  /**
   * Subclasses may override to filter out the model points if there is custom
   * pixelblaze model functionality, or if geometry is to be mutated, etc. By
   * default this just returns the model points array as-is.
   *
   * @param model Model as input
   * @return Array of points to be rendered by Pixelblaze JS
   */
  protected LXPoint[] getPixelblazePoints(LXModel model) {
    return model.points;
  }

  private String getScriptName() {
    return this.scriptName.getString();
  }

  private final List<String> newSliderKeys = new ArrayList<String>();
  private final List<String> removeSliderKeys = new ArrayList<String>();

  private void clearSliders() {
    for (String key : this.mutableSliders.keySet()) {
      removeParameter(key);
    }
    this.mutableSliders.clear();
  }

  /**
   * Used by the glue to register discovered slider controls
   *
   * @param key
   * @param label
   */
  public void addSlider(String key, String label) {
    this.newSliderKeys.add(key);
    if (this.parameters.containsKey(key)) {
      return;
    }
    CompoundParameter param = new CompoundParameter(label, .5, 0, 1);
    addParameter(key, param);
    this.mutableSliders.put(key, param);
  }

  /**
   * Used by the glue to invoke slider controls
   *
   * @param key Parameter key
   * @return Value of slider
   */
  public double getSlider(String key) {
    LXParameter parameter = this.parameters.get(key);
    if (parameter != null) {
      return parameter.getValue();
    }
    return 0;
  }

  @Override
  public void run(double deltaMs) {
    if (this.wrapper == null) {
      return;
    }
    try {
      this.wrapper.reloadIfNecessary();
      if (!this.wrapper.hasError) {
        this.wrapper.render(deltaMs);
        this.error.setValue(null);
      }
    } catch (ScriptException | NoSuchMethodException sx) {
      // The show must go on, and we don't want to spam the logs.
      if (this.lx.engine.nowMillis - this.lastLogMs > RENDER_ERROR_LOG_INTERVAL_MS) {
        LX.error("Error rendering Pixelblaze script: " + sx.getMessage());
        this.error.setValue(sx.getMessage());
        this.lastLogMs = this.lx.engine.nowMillis;
      }
    } catch (Exception x) {
      LX.error(x, "Unknown error running Pixelblaze script " + getScriptName() + ": " + x.getMessage());
    }
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    // Force-load the script name first so that slider parameter values can come after!
    if (obj.has(LXComponent.KEY_PARAMETERS)) {
      JsonObject params = obj.getAsJsonObject(LXComponent.KEY_PARAMETERS);
      if (params.has(KEY_SCRIPT_NAME)) {
        this.scriptName.setValue(params.get(KEY_SCRIPT_NAME).getAsString());
      }
    }
    super.load(lx, obj);
  }

  private class Wrapper {

    //NOTE these are thread-safe, if used with separate bindings
    //https://stackoverflow.com/a/30159424/910094
    //    static final ScriptEngine engine;
    //    static final Invocable invocable;
    //    static final Compilable compilingEngine;
    //    static HashMap<String, CompiledScript> scripts = new HashMap<>();
    //    static {
    //      NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
    //      engine = factory.getScriptEngine("--language=es6");
    //      invocable = (Invocable)engine;
    //      compilingEngine = (Compilable) engine;
    //    }
      //
    //    static synchronized CompiledScript compile(String pbClass) {
    //      File file = new File("resources/pixelblaze/" + pbClass + ".js");
    //      String js = Files.readString(file.toPath());
    //      lastModified = file.lastModified();
    //      js = js.replaceAll("\\bexport\\b", "");
    //      return compilingEngine.compile(js);
    //    }


    private final File file;
    private long lastModified;
    private ScriptEngine engine;
    private Invocable invocable;
    private LXPoint[] points;
    private boolean hasError = false;

    private Wrapper(String path, LXPoint[] points) throws Exception {
      this(lx.getMediaFile(LX.Media.PIXELBLAZE, path, false), points);
    }

    private Wrapper(File file, LXPoint[] points) throws ScriptException, IOException {
      this.file = file;
      this.points = points;
    }

    private void reloadIfNecessary() throws ScriptException, IOException, NoSuchMethodException, URISyntaxException {
      if (this.file.lastModified() != this.lastModified) {
        load();
      }
    }

    private String getJsFromResource(String pbClass) throws IOException, URISyntaxException {
      return Files.readString(Paths.get(getClass().getResource("/pixelblaze/" + pbClass + ".js").toURI()));
    }

    private void load() throws IOException, ScriptException, NoSuchMethodException, URISyntaxException {
      try {
        String js = Files.readString(this.file.toPath());
        js = js.replaceAll("\\bexport\\b", "");
        this.lastModified = this.file.lastModified();

        this.engine = new NashornScriptEngineFactory().getScriptEngine("--language=es6");
        this.invocable = (Invocable) engine;

        this.engine.put("pixelCount", this.points.length);
        this.engine.put("__pattern", PixelblazePattern.this);
        this.engine.put("__now", lx.engine.nowMillis);
        this.engine.eval(getJsFromResource("glue"));

        // TODO(mcslee): Make it possible for a subclass to add some additional
        // project-specific glue?

        this.engine.eval(js);

        newSliderKeys.clear();
        removeSliderKeys.clear();
        this.invocable.invokeFunction("glueRegisterControls");

        // Remove sliders that don't exist anymore!
        for (String key : mutableSliders.keySet()) {
          if (!newSliderKeys.contains(key)) {
            // Use two passes for clarity and to avoid ConcurrentModificationException...
            removeSliderKeys.add(key);
          }
        }
        for (String key : removeSliderKeys) {
          mutableSliders.remove(key);
          removeParameter(key);
        }

        this.hasError = false;
      } catch (Throwable t) {
        this.hasError = true;
        onReload.bang();
        throw t;
      }

      onReload.bang();
    }

    private void render(double deltaMs) throws ScriptException, NoSuchMethodException {
      // NOTE(mcslee): is this redundant with passing values via invokeFunction to glueBeforeRender?
      this.engine.put("__now", lx.engine.nowMillis);
      this.engine.put("__points", this.points);
      this.engine.put("__colors", colors);

      this.invocable.invokeFunction("glueBeforeRender", deltaMs, lx.engine.nowMillis, this.points, colors);
      this.invocable.invokeFunction("glueRender");
    }

    /**
     * Updates the points that the pattern will operate on, reloading if necessary.
     *
     * @param points
     * @throws ScriptException
     * @throws IOException
     * @throws NoSuchMethodException
     */
    private void setPoints(LXPoint[] points) throws ScriptException, IOException, NoSuchMethodException, URISyntaxException {
      if (this.points != points) {
        this.points = points;

        // NOTE(mcslee): do we really need to load here? Seems like a safe idea, but the
        // points seem to be dynamically bound on every frame regardless... in theory I suppose
        // glueRegisterControls could look at the points?
        load();
      }
    }
  }


}