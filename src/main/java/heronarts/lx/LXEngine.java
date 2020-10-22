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

import heronarts.lx.audio.LXAudioEngine;
import heronarts.lx.clip.LXClipEngine;
import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXPalette;
import heronarts.lx.midi.LXMidiEngine;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXMixerEngine;
import heronarts.lx.model.LXModel;
import heronarts.lx.modulation.LXModulationContainer;
import heronarts.lx.modulation.LXModulationEngine;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.osc.LXOscEngine;
import heronarts.lx.output.LXOutput;
import heronarts.lx.output.LXOutputGroup;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.pattern.LXPattern;
import heronarts.lx.snapshot.LXSnapshotEngine;
import heronarts.lx.structure.LXFixture;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.google.gson.JsonObject;

/**
 * The engine is the core class that runs the internal animations. An engine is
 * comprised of top-level modulators, then a number of channels, each of which
 * has a set of patterns that it may transition between. These channels are
 * blended together, and effects are then applied.
 */
public class LXEngine extends LXComponent implements LXOscComponent, LXModulationContainer {

  public final LXPalette palette;

  public final Tempo tempo;

  public final LXClipEngine clips;

  public final LXMixerEngine mixer;

  public final LXMidiEngine midi;

  public final LXAudioEngine audio;

  public final LXMappingEngine mapping;

  public final LXOscEngine osc;

  private Dispatch inputDispatch = null;

  private final List<LXLoopTask> loopTasks = new ArrayList<LXLoopTask>();
  private final List<Runnable> threadSafeTaskQueue = Collections.synchronizedList(new ArrayList<Runnable>());
  private final List<Runnable> engineThreadTaskQueue = new ArrayList<Runnable>();

  public final Output output;

  public final BoundedParameter framesPerSecond = (BoundedParameter)
    new BoundedParameter("FPS", 60, 0, 300)
    .setMappable(false)
    .setDescription("Number of frames per second the engine runs at");

  public final BoundedParameter speed =
    new BoundedParameter("Speed", 1, 0, 2)
    .setDescription("Overall speed adjustement to the entire engine (does not apply to master tempo and audio)");

  public final LXModulationEngine modulation;

  public final LXSnapshotEngine snapshots;

  private boolean logProfiler = false;

  private boolean hasFailed = false;

  private float actualFrameRate = 0;
  private float cpuLoad = 0;

  public class Output extends LXOutputGroup implements LXOscComponent {

    public final BooleanParameter restricted =
      new BooleanParameter("Restricted", false)
      .setDescription("Whether output is restricted due to license restrictions");

    /**
     * This ModelOutput helper is used for sending dynamic datagrams that are
     * specified in the model. Any time the model is changed, this set will be
     * updated.
     */
    private class ModelOutput extends LXOutputGroup implements LX.Listener {
      ModelOutput(LX lx) throws SocketException {
        super(lx);
        setModel(lx.model);
        lx.addListener(this);
      }

      @Override
      public void modelChanged(LX lx, LXModel model) {
        // We have a new model, use that instead...
        setModel(model);
      }

      private void setModel(LXModel model) {
        // Clear out all the outputs from the old model
        clearChildren();

        // Recursively add all dynamic outputs attached to this model
        if (model != null) {
          addOutputs(model);
        }
      }

      private void addOutputs(LXModel model) {
        // Depth-first, a model's children are sent before its own output
        for (LXModel child : model.children) {
          addOutputs(child);
        }

        // Then send the outputs  for the model itself. For instance, this makes it possible
        // for a parent to send an ArtSync or something after all children send ArtDmx
        for (LXOutput output : model.outputs) {
          addChild(output);
        }
      }
    }

    Output(LX lx) {
      super(lx);
      this.restricted.addListener((p) -> {
        if (this.restricted.isOn()) {
          int myPoints = lx.model.size;
          int limitPoints = lx.permissions.getMaxPoints();
          lx.pushError(null, "You have exceeded the maximum number of points allowed by your license (" + myPoints + " > " + limitPoints + "). Output will be disabled.");
        }
      });

      try {
        addChild(new ModelOutput(lx));
      } catch (SocketException sx) {
        lx.pushError(sx, "Serious network error, could not create output socket. Program will continue with no network output.\n" + sx.getLocalizedMessage());
        LXOutput.error("Could not create output datagram socket, model will not be able to send");
      }
    }

    @Override
    public LXOutput send(int[] colors) {
      if (!this.restricted.isOn()) {
        super.send(colors);
      }
      return this;
    }
  }

  public interface Dispatch {
    public void dispatch();
  }

  public class Profiler {
    public long runNanos = 0;
    public long channelNanos = 0;
    public long inputNanos = 0;
    public long midiNanos = 0;
    public long oscNanos = 0;
    public long outputNanos = 0;
  }

  public final Profiler profiler = new Profiler();

  // Buffer for a single frame, which was rendered with
  // a particular model state, has a main and a cue view,
  // and a cue state
  public static class Frame implements LXBuffer {

    private LXModel model;
    private int[] main = null;
    private int[] cue = null;
    private boolean cueOn = false;

    public Frame(LX lx) {
      setModel(lx.getModel());
    }

    public void setModel(LXModel model) {
      this.model = model;
      if ((this.main == null) || (this.main.length != model.size)) {
        this.main = new int[model.size];
        this.cue = new int[model.size];
      }
    }

    public void setCueOn(boolean cueOn) {
      this.cueOn = cueOn;
    }

    public void copyFrom(Frame that) {
      setModel(that.model);
      this.cueOn = that.cueOn;
      System.arraycopy(that.main, 0, this.main, 0, this.main.length);
      System.arraycopy(that.cue, 0, this.cue, 0, this.cue.length);
    }

    public int[] getColors() {
      return this.cueOn ? this.cue : this.main;
    }

    public LXModel getModel() {
      return this.model;
    }

    @Override
    public int[] getArray() {
      return this.main;
    }

    public int[] getMain() {
      return this.main;
    }

    public int[] getCue() {
      return this.cue;
    }
  }

  // A double buffer that holds two frames which are flipped back and forth such that
  // the engine thread may render into one of them while UI or networking threads may
  // copy off the contents of another
  class DoubleBuffer {

    // Frame buffer that is currently used by the engine to render
    Frame render;

    // Complete buffer that may be copied off for UI or networking while engine
    // works on the other buffer.
    Frame copy;

    DoubleBuffer(LX lx) {
      this.render = new Frame(lx);
      this.copy = new Frame(lx);
    }

    synchronized void sync() {
      this.copy.copyFrom(this.render);
    }

    synchronized void flip() {
      Frame tmp = this.render;
      this.render = this.copy;
      this.copy = tmp;
    }

    synchronized void copyTo(Frame that) {
      that.copyFrom(this.copy);
    }
  }

  private final DoubleBuffer buffer;

  public final BooleanParameter isMultithreaded = (BooleanParameter)
    new BooleanParameter("Threaded", false)
    .setMappable(false)
    .setDescription("Whether the engine and UI are on separate threads");

  public final BooleanParameter isChannelMultithreaded = (BooleanParameter)
    new BooleanParameter("Channel Threaded", false)
    .setMappable(false)
    .setDescription("Whether the engine is multi-threaded per channel");

  public final BooleanParameter isNetworkMultithreaded = (BooleanParameter)
    new BooleanParameter("Network Threaded", false)
    .setMappable(false)
    .setDescription("Whether the network output is on a separate thread");

  private EngineThread engineThread = null;

  private boolean isNetworkThreadStarted = false;
  public final NetworkThread networkThread;

  boolean hasStarted = false;

  private boolean paused = false;

  private static final long INIT_RUN = -1;
  private long lastMillis = INIT_RUN;

  private double fixedDeltaMs = 0;

  /**
   * Globally accessible counter of the current millisecond clock
   */
  public long nowMillis = System.currentTimeMillis();

  LXEngine(final LX lx) {
    super(lx, LXComponent.ID_ENGINE, "Engine");
    LX.initProfiler.log("Engine: Init");

    // Initialize double-buffer of frame contents
    this.buffer = new DoubleBuffer(lx);

    // Initialize network thread (don't start it yet)
    this.networkThread = new NetworkThread(lx);

    // Mapping engine
    this.mapping = new LXMappingEngine();

    // Color palette
    addChild("palette", this.palette = new LXPalette(lx));
    LX.initProfiler.log("Engine: Palette");

    // Tempo engine
    addChild("tempo", this.tempo = new Tempo(lx));
    LX.initProfiler.log("Engine: Tempo");

    // Clip engine
    addChild("clips", this.clips = new LXClipEngine(lx));
    LX.initProfiler.log("Engine: Clips");

    // Mixer engine
    addChild("mixer", this.mixer = new LXMixerEngine(lx));
    LX.initProfiler.log("Engine: Mixer");

    // Modulation matrix
    addChild("modulation", this.modulation = new LXModulationEngine(lx));
    LX.initProfiler.log("Engine: Modulation");

    // Master output
    addChild("output", this.output = new Output(lx));
    if (lx.structure.output != null) {
      this.output.addChild(lx.structure.output);
    }
    LX.initProfiler.log("Engine: Output");

    // Snapshot engine
    addChild("snapshots", this.snapshots = new LXSnapshotEngine(lx));
    LX.initProfiler.log("Engine: Snapshots");

    // Midi engine
    addChild("midi", this.midi = new LXMidiEngine(lx));
    LX.initProfiler.log("Engine: Midi");

    // Audio engine
    addChild("audio", this.audio = new LXAudioEngine(lx));
    LX.initProfiler.log("Engine: Audio");

    // OSC engine
    addChild("osc", this.osc = new LXOscEngine(lx));
    LX.initProfiler.log("Engine: Osc");

    // Register parameters
    addParameter("multithreaded", this.isMultithreaded);
    addParameter("channelMultithreaded", this.isChannelMultithreaded);
    addParameter("networkMultithreaded", this.isNetworkMultithreaded);
    addParameter("framesPerSecond", this.framesPerSecond);
    addParameter("speed", this.speed);
  }

  public void logProfiler() {
    this.logProfiler = true;
  }

  @Override
  public String getPath() {
    return "lx";
  }

  public LXEngine setInputDispatch(Dispatch inputDispatch) {
    this.inputDispatch = inputDispatch;
    return this;
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.isNetworkMultithreaded) {
      if (this.isNetworkMultithreaded.isOn()) {
        this.buffer.sync();
        if (!this.isNetworkThreadStarted) {
          this.isNetworkThreadStarted = true;
          this.networkThread.start();
        }
      }
    } else if (p == this.framesPerSecond) {
      EngineThread engineThread = this.engineThread;
      if (engineThread != null) {
        engineThread.updateFramerate();
      }
    }
  }

  /**
   * Gets the active frame rate of the engine when in threaded mode
   *
   * @return How many FPS the engine is running
   */
  public float getActualFrameRate() {
    return this.actualFrameRate;
  }

  /**
   * Gets a very rough estimate of the CPU load the engine is using
   * before maxing out.
   *
   * @return Estimated CPU load
   */
  public float getCpuLoad() {
    return this.cpuLoad;
  }

  /**
   * Utility for when rendering offline videos. Ignore how much real-time has passed between
   * frames and compute animations based upon the given deltaMs
   *
   * @param deltaMs Fixed deltaMs between rendered frames
   * @return this
   */
  public LXEngine setFixedDeltaMs(double deltaMs) {
    this.fixedDeltaMs = deltaMs;
    return this;
  }

  /**
   * Starts the engine thread.
   */
  public void start() {
    if (this.lx.flags.isP3LX) {
      throw new IllegalStateException("LXEngine start() may not be used from P3LX, call setThreaded() instead");
    }
    this.isMultithreaded.setValue(true);
    _setThreaded(true);
  }

  /**
   * Stops the engine thread.
   */
  public void stop() {
    if (this.lx.flags.isP3LX) {
      throw new IllegalStateException("LXEngine stop() may not be used from P3LX, call setThreaded() instead");
    }
    this.isMultithreaded.setValue(false);
    _setThreaded(false);
  }

  /**
   * Returns whether the engine is actively threaded
   *
   * @return Whether engine is threaded
   */
  public boolean isThreaded() {
    return (this.engineThread != null);
  }

  /**
   * Sets the engine to threaded or non-threaded mode. Should only be called
   * from the Processing animation thread.
   *
   * @param threaded Whether engine should run on its own thread
   * @return this
   */
  public LXEngine setThreaded(boolean threaded) {
    if (!this.lx.flags.isP3LX) {
      throw new IllegalStateException("LXEngine.setThreaded() should not be used outside P3LX, call start() / stop() instead");
    }
    this.isMultithreaded.setValue(threaded);
    return this;
  }

  /**
   * Utility method for P3LX mode, invoked from the Processing draw thread to give
   * a chance to change the threading state before the draw loop.
   */
  public void beforeP3LXDraw() {
    if (isThreaded() != this.isMultithreaded.isOn()) {
      _setThreaded(this.isMultithreaded.isOn());

      // Clear out any lingering key/mouse events on the queue
      if (!this.isMultithreaded.isOn()) {
        processInputEvents();
      }
    }
  }

  private synchronized void _setThreaded(boolean threaded) {
    if (threaded == isThreaded()) {
      throw new IllegalStateException("Cannot set thread state to current state: " + threaded);
    }
    if (!threaded) {
      if (Thread.currentThread() == this.engineThread.thread) {
        throw new IllegalStateException("Cannot call to stop engine thread from itself");
      }

      // Tell the engine thread to stop
      this.engineThread.stop();
      try {
        // Wait for it to finish
        this.engineThread.thread.join();
      } catch (InterruptedException ix) {
        throw new IllegalThreadStateException("Interrupted waiting to join LXEngine thread");
      }
      this.engineThread = null;

    } else {
      // Synchronize the two buffers, flip so that the engine thread doesn't start
      // rendering over the top of the buffer that the UI thread might be currently
      // working on drawing.
      this.buffer.sync();
      this.buffer.flip();
      this.engineThread = new EngineThread();
      this.engineThread.start();
    }
  }

  private class EngineThread extends Timer {

    public static final String THREAD_NAME = "LXEngine Core Thread";

    private class InitSemaphore {
      boolean hasThread = false;
    }

    private final InitSemaphore initSemaphore = new InitSemaphore();

    private Thread thread;
    private RenderTask renderTask;

    private EngineThread() {
      super(THREAD_NAME);
      schedule(new TimerTask() {
        @Override
        public void run() {
          thread = Thread.currentThread();
          thread.setPriority(lx.flags.engineThreadPriority);
          synchronized (initSemaphore) {
            initSemaphore.hasThread = true;
            initSemaphore.notify();
          }
        }
      }, 0);
      waitForThreadStart();
    }

    private void waitForThreadStart() {
      synchronized (this.initSemaphore) {
        if (!this.initSemaphore.hasThread) {
          try {
            this.initSemaphore.wait();
          } catch (InterruptedException ix) {
            // Why the fuck? Can't happen...
            throw new RuntimeException(ix);
          }
        }
      }
    }

    private boolean isRunning = false;

    private void updateFramerate() {
      if (this.isRunning) {
        this.renderTask.cancel();
        long period = (long) (1000.f / framesPerSecond.getValuef());
        scheduleAtFixedRate(this.renderTask = new RenderTask(), period, period);
      }
    }

    private void start() {
      if (this.isRunning) {
        throw new IllegalStateException("May not start already running EngineThread");
      }
      this.isRunning = true;
      LX.log(this.thread.getName() + " starting.");
      scheduleAtFixedRate(this.renderTask = new RenderTask(), 0, (long) (1000.f / framesPerSecond.getValuef()));
      scheduleAtFixedRate(new TimerTask() {
        @Override
        public void run() {
          processInputEvents();
        }
      }, 0, 16);
    }

    private void stop() {
      if (!this.isRunning) {
        throw new IllegalStateException("Can not stop a non-running EngineThread");
      }
      cancel();
      this.isRunning = false;
      LX.log(this.thread.getName() + " stopped.");
    }

    private class RenderTask extends TimerTask {

      private int sampleCount = 0;
      private long lastSampleTime = System.currentTimeMillis();
      private long cpuTime = 0;

      @Override
      public void run() {
        long frameStart = System.currentTimeMillis();
        LXEngine.this.run(true);
        long frameEnd = System.currentTimeMillis();
        this.cpuTime += (frameEnd - frameStart);
        ++this.sampleCount;


        // Sample the real performance of the engine every 500ms
        if (frameEnd - this.lastSampleTime > 500) {
          cpuLoad = this.cpuTime * framesPerSecond.getValuef() / 1000f / this.sampleCount;
          actualFrameRate = 1000f * this.sampleCount / (frameEnd - this.lastSampleTime);
          this.sampleCount = 0;
          this.cpuTime = 0;
          this.lastSampleTime = frameEnd;
        }
      }
    };
  }

  /**
   * Sets a global speed factor on the core animation engine.
   * This does not impact the tempo object.
   *
   * @param speed Global speed multiplier
   * @return this
   */
  public LXEngine setSpeed(double speed) {
    this.speed.setValue(speed);
    return this;
  }

  /**
   * Pause the engine from running
   *
   * @param paused Whether to pause the engine to pause
   * @return this
   */
  public LXEngine setPaused(boolean paused) {
    this.paused = paused;
    return this;
  }

  /*
   * Whether execution of the engine is paused.
   */
  public boolean isPaused() {
    return this.paused;
  }

  /**
   * Register a component with the engine. It will be saved and loaded.
   *
   * @param path Unique path key for saving and loading component
   * @param component Component
   * @return this
   */
  public LXEngine registerComponent(String path, LXComponent component) {
    addChild(path, component);
    return this;
  }

  /**
   * Add a task to be run once on the engine thread.
   *
   * @param runnable Task to run
   * @return this
   */
  public LXEngine addTask(Runnable runnable) {
    this.threadSafeTaskQueue.add(runnable);
    return this;
  }

  /**
   * Add a task to be run on every loop of the engine thread.
   *
   * @param loopTask Task to run on each engine loop
   * @return this
   */
  public LXEngine addLoopTask(LXLoopTask loopTask) {
    if (this.loopTasks.contains(loopTask)) {
      throw new IllegalStateException("Cannot add task to engine twice: " + loopTask);
    }
    this.loopTasks.add(loopTask);
    return this;
  }

  /**
   * Remove a task from the list run on every loop invocation
   *
   * @param loopTask Task to stop running on every loop
   * @return this
   */
  public LXEngine removeLoopTask(LXLoopTask loopTask) {
    this.loopTasks.remove(loopTask);
    return this;
  }

  /**
   * Sets the output driver
   *
   * @param output Output driver, or null for no output
   * @return this
   */
  public LXEngine addOutput(LXOutput output) {
    this.output.addChild(output);
    return this;
  }

  @Override
  public LXModulationEngine getModulationEngine() {
    return this.modulation;
  }

  /**
   * This is the core run loop of the LXEngine. It can be invoked from various places, such
   * as the EngineThread when running in multi-threaded mode, or from a Processing sketch
   * when in P3LX, or from another application framework. Unless you are writing your own
   * new application framework using LX (this is not recommended), you should never call
   * this method directly. It is only public to make it accessible to these other frameworks.
   */
  public void run() {
    run(false);
  }

  private void run(boolean fromEngineThread) {
    if (this.hasFailed) {
      return;
    }
    try {
      _run(fromEngineThread);
    } catch (Exception x) {
      this.hasFailed = true;
      LX.error(x, "FATAL TOP-LEVEL EXCEPTION IN ENGINE: " + x.getLocalizedMessage());
      setThreaded(false);
      lx.fail(x);
    }
  }

  private void processInputEvents() {
    // Process MIDI events
    long midiStart = System.nanoTime();
    this.midi.dispatch();
    this.profiler.midiNanos = System.nanoTime() - midiStart;

    // Process OSC events
    long oscStart = System.nanoTime();
    this.osc.dispatch();
    this.profiler.oscNanos = System.nanoTime() - oscStart;

    // Process UI input events
    if (this.inputDispatch == null) {
      this.profiler.inputNanos = 0;
    } else {
      long inputStart = System.nanoTime();
      this.inputDispatch.dispatch();
      this.profiler.inputNanos = System.nanoTime() - inputStart;
    }
  }

  private void _run(boolean fromEngineThread) {

    this.hasStarted = true;

    long runStart = System.nanoTime();

    // Compute elapsed time
    this.nowMillis = System.currentTimeMillis();
    if (this.lastMillis == INIT_RUN) {
      // Initial frame is arbitrarily 16 milliseconds (~60 fps)
      this.lastMillis = this.nowMillis - 16;
    }
    double deltaMs = this.nowMillis - this.lastMillis;
    this.lastMillis = this.nowMillis;

    // Override deltaMs if in fixed render mode
    if (this.fixedDeltaMs > 0) {
      deltaMs = this.fixedDeltaMs;
    }

    // Paused? Reset timers and kill the loop...
    if (this.paused) {
      this.profiler.channelNanos = 0;
      ((LXBus.Profiler) this.mixer.masterBus.profiler).effectNanos = 0;
      this.profiler.runNanos = System.nanoTime() - runStart;
      return;
    }

    // Process input events, unless we're running on the engine thread
    // which uses a separate timer task to do this
    if (!fromEngineThread) {
      processInputEvents();
    }

    // Run-once scheduled tasks
    if (this.threadSafeTaskQueue.size() > 0) {
      this.engineThreadTaskQueue.clear();
      synchronized (this.threadSafeTaskQueue) {
        this.engineThreadTaskQueue.addAll(this.threadSafeTaskQueue);
        this.threadSafeTaskQueue.clear();
      }
      for (Runnable runnable : this.engineThreadTaskQueue) {
        runnable.run();
      }
    }

    // Run the project scheduler
    this.lx.scheduler.loop(deltaMs);

    // Initialize the model context for this render frame
    this.buffer.render.setModel(this.lx.model);

    // Run tempo and audio, always using real-time
    this.lx.engine.tempo.loop(deltaMs);
    this.audio.loop(deltaMs);

    // Mutate by master speed for everything else
    deltaMs *= this.speed.getValue();

    // Run the modulation and snapshot engines
    this.modulation.loop(deltaMs);
    this.snapshots.loop(deltaMs);

    // Run the color control
    this.lx.engine.palette.loop(deltaMs);

    // Run top-level loop tasks
    for (LXLoopTask loopTask : this.loopTasks) {
      loopTask.loop(deltaMs);
    }

    // Okay, time for the real work, to run and blend all of our channels
    // First, set up a bunch of state to keep track of which buffers we
    // are rendering into.
    this.mixer.loop(buffer.render, deltaMs);

    // Add fixture identification very last
    int identifyColor = LXColor.hsb(0, 100, Math.abs(-100 + (runStart / 8000000) % 200));
    for (LXFixture fixture : this.lx.structure.fixtures) {
      if (fixture.mute.isOn()) {
        int start = fixture.getIndexBufferOffset();
        int end = start + fixture.totalSize();
        if (end > start) {
          for (int i = start; i < end; ++i) {
            this.buffer.render.main[i] = LXColor.BLACK;
            this.buffer.render.cue[i] = LXColor.BLACK;
          }
        }
      } else if (fixture.identify.isOn()) {
        int start = fixture.getIndexBufferOffset();
        int end = start + fixture.totalSize();
        if (end > start) {
          for (int i = start; i < end; ++i) {
            this.buffer.render.main[i] = identifyColor;
            this.buffer.render.cue[i] = identifyColor;
          }
        }
      }
      if (fixture.solo.isOn()) {
        int start = fixture.getIndexBufferOffset();
        int end = start + fixture.totalSize();
        if (end > start) {
          for (int i = 0; i < this.buffer.render.main.length; ++i) {
            if (i < start || i >= end) {
              this.buffer.render.main[i] = LXColor.BLACK;
              this.buffer.render.cue[i] = LXColor.BLACK;
            }
          }
        }
      }
    }

    // Step 5: our cue and render frames are ready! Let's get them output
    boolean isNetworkMultithreaded = this.isNetworkMultithreaded.isOn();
    boolean isDoubleBuffering = isThreaded()|| isNetworkMultithreaded;
    if (isDoubleBuffering) {
      // We are multi-threading, lock the double buffer and flip it
      this.buffer.flip();
    }

    int maxPoints = this.lx.permissions.getMaxPoints();
    this.output.restricted.setValue(maxPoints >= 0 && this.buffer.copy.main.length > maxPoints);

    if (!this.output.restricted.isOn()) {
      if (isNetworkMultithreaded) {
        // Notify the network thread of new work to do!
        synchronized (this.networkThread) {
          this.networkThread.notify();
        }
        this.profiler.outputNanos = 0;
      } else {
        // Or do it ourself here on the engine thread
        long outputStart = System.nanoTime();
        Frame sendFrame = isDoubleBuffering ? this.buffer.copy : this.buffer.render;
        int[] sendColors = (this.lx.flags.sendCueToOutput && sendFrame.cueOn) ? sendFrame.cue : sendFrame.main;
        this.output.send(sendColors);
        this.profiler.outputNanos = System.nanoTime() - outputStart;
      }
    } else {
      this.profiler.outputNanos = 0;
    }

    // All done running this pass of the engine!
    this.profiler.runNanos = System.nanoTime() - runStart;

    // Debug trace logging
    if (this.logProfiler) {
      _logProfiler();
      this.logProfiler = false;
    }
  }

  private void _logProfiler() {
    StringBuilder sb = new StringBuilder();
    sb.append("LXEngine::run() " + ((int) (this.profiler.runNanos / 1000000)) + "ms\n");
    sb.append("LXEngine::run()::channels " + ((int) (this.profiler.channelNanos / 1000000)) + "ms\n");
    for (LXAbstractChannel channel : this.mixer.channels) {
      sb.append("LXEngine::" + channel.getLabel() + "::loop() " + ((int) (channel.profiler.loopNanos / 1000000)) + "ms\n");
      if (channel instanceof LXChannel) {
        LXPattern pattern = ((LXChannel) channel).getActivePattern();
        sb.append("LXEngine::" + channel.getLabel() + "::" + pattern.getLabel() + "::run() " + ((int) (pattern.profiler.runNanos / 1000000)) + "ms\n");
      }
    }
    LX.log(sb.toString());
  }

  public class NetworkThread extends Thread {

    public class Profiler {
      public long copyNanos = 0;
      public long sendNanos = 0;
    }

    private long lastFrame = System.currentTimeMillis();

    private float actualFrameRate = 0;

    public final Profiler timer = new Profiler();

    private final Frame networkFrame;

    NetworkThread(LX lx) {
      super("LXEngine Network Thread");
      this.networkFrame = new Frame(lx);
    }

    @Override
    public void run() {
      LXOutput.log("LXEngine Network Thread started");
      while (!isInterrupted()) {
        try {
          synchronized(this) {
            wait();
          }
        } catch (InterruptedException ix) {
          break;
        }

        if (output.enabled.isOn()) {
          // Copy from the double-buffer into our local storage and send from here
          long copyStart = System.nanoTime();
          synchronized (buffer) {
            this.networkFrame.copyFrom(buffer.copy);
          }
          long copyEnd = System.nanoTime();
          this.timer.copyNanos = copyEnd - copyStart;
          try {
            output.send(this.networkFrame.main);
          } catch (Exception x) {
            // TODO(mcslee): For now we don't flag these, there could be ConcurrentModificationException
            // or ArrayIndexBounds exceptions if the model/fixtures are being changed in real-time.
            // This is rare and would only occur at a VERY high framerate.
            LX.error("Exception in network thread: " + x.getLocalizedMessage());
          }
          this.timer.sendNanos = System.nanoTime() - copyEnd;
        }

        // Compute network framerate
        long now = System.currentTimeMillis();
        this.actualFrameRate = 1000.f / (now - this.lastFrame);
        this.lastFrame = now;
      }

      LXOutput.log("LXEngine Network Thread finished");
    }

    public float frameRate() {
      return this.actualFrameRate;
    }
  }

  /**
   * This should be used when in threaded mode. It synchronizes on the
   * double-buffer and duplicates the internal copy buffer into the provided
   * buffer.
   *
   * @param frame Frame buffer to copy into
   */
  public void copyFrameThreadSafe(Frame frame) {
    this.buffer.copyTo(frame);
  }

  /**
   * Non-thread safe accessor of the render buffer. Directly copies from it,
   * which if in multi-threaded mode could happen during modification. Basically,
   * never call this from the non-engine thread.
   *
   * @param frame Frame buffer to copy into
   */
  public void getFrameNonThreadSafe(Frame frame) {
    frame.copyFrom(this.buffer.render);
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    // TODO(mcslee): remove loop tasks that other things might have added? maybe
    // need to separate application-owned loop tasks from project-specific ones...

    // Clear all the modulation and mixer content
    this.snapshots.clear();
    this.modulation.clear();
    this.mixer.clear();

    // Invoke super-loader
    super.load(lx, obj);
  }

  @Override
  public void dispose() {
    this.mixer.dispose();
    this.audio.dispose();
    this.midi.dispose();
    this.osc.dispose();
    synchronized (this.networkThread) {
      this.networkThread.interrupt();
    }
    super.dispose();
  }


}
