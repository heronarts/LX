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
import heronarts.lx.blend.AddBlend;
import heronarts.lx.blend.LXBlend;
import heronarts.lx.clip.LXClip;
import heronarts.lx.color.LXColor;
import heronarts.lx.midi.LXMidiEngine;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.osc.LXOscComponent;
import heronarts.lx.osc.LXOscEngine;
import heronarts.lx.output.LXOutput;
import heronarts.lx.output.LXOutputGroup;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.MutableParameter;
import heronarts.lx.parameter.ObjectParameter;
import heronarts.lx.pattern.IteratorPattern;
import heronarts.lx.script.LXScriptEngine;
import heronarts.lx.structure.LXFixture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The engine is the core class that runs the internal animations. An engine is
 * comprised of top-level modulators, then a number of channels, each of which
 * has a set of patterns that it may transition between. These channels are
 * blended together, and effects are then applied.
 *
 * <pre>
 *   -----------------------------
 *  | Engine                      |
 *  |  -------------------------  |
 *  | | Modulators              | |
 *  |  -------------------------  |
 *  |  --------    --------       |
 *  | | Chnl 0 |  | Chnl 1 |  ... |
 *  |  --------    --------       |
 *  |  -------------------------  |
 *  | | Effects                 | |
 *  |  -------------------------  |
 *   -----------------------------
 * </pre>
 *
 * The result of all this generates a display buffer of node values.
 */
public class LXEngine extends LXComponent implements LXOscComponent, LXModulationComponent {

  private static final int MAX_SCENES = 5;

  private final LX lx;

  public final LXMidiEngine midi;

  public final LXAudioEngine audio;

  public final LXMappingEngine mapping = new LXMappingEngine();

  public final LXOscEngine osc;

  public final LXScriptEngine script;

  private Dispatch inputDispatch = null;

  private final List<LXLoopTask> loopTasks = new ArrayList<LXLoopTask>();
  private final List<Runnable> threadSafeTaskQueue = Collections.synchronizedList(new ArrayList<Runnable>());
  private final List<Runnable> engineThreadTaskQueue = new ArrayList<Runnable>();
  private final Map<String, LXComponent> components = new HashMap<String, LXComponent>();

  private final List<LXChannelBus> mutableChannels = new ArrayList<LXChannelBus>();
  public final List<LXChannelBus> channels = Collections.unmodifiableList(this.mutableChannels);

  public final LXMasterChannel masterChannel;

  public final Output output;

  private final List<Listener> listeners = new ArrayList<Listener>();
  private final List<MessageListener> messageListeners = new ArrayList<MessageListener>();

  public final DiscreteParameter focusedChannel =
    new DiscreteParameter("Channel", 1)
    .setDescription("Which channel is currently focused in the UI");

  public final BoundedParameter framesPerSecond =
    new BoundedParameter("FPS", 60, 0, 300)
    .setDescription("Number of frames per second the engine runs at");

  private final AddBlend addBlend;

  public final CompoundParameter crossfader = (CompoundParameter)
    new CompoundParameter("Crossfader", 0.5)
    .setDescription("Applies blending between output groups A and B")
    .setPolarity(LXParameter.Polarity.BIPOLAR);

  public final ObjectParameter<LXBlend> crossfaderBlendMode;
  private LXBlend activeCrossfaderBlend;

  public final BooleanParameter cueA =
    new BooleanParameter("Cue-A", false)
    .setDescription("Enables cue preview of crossfade group A");

  public final BooleanParameter cueB =
    new BooleanParameter("Cue-B", false)
    .setDescription("Enables cue preview of crossfade group B");

  public final BoundedParameter speed =
    new BoundedParameter("Speed", 1, 0, 2)
    .setDescription("Overall speed adjustement to the entire engine (does not apply to master tempo and audio)");

  private final BooleanParameter[] scenes = new BooleanParameter[MAX_SCENES];

  public final LXModulationEngine modulation;

  private boolean logTimers = false;

  public class FocusedClipParameter extends MutableParameter {

    private LXClip clip = null;

    private FocusedClipParameter() {
      super("Focused Clip");
      setDescription("Parameter which indicate the globally focused clip");
    }

    public FocusedClipParameter setClip(LXClip clip) {
      if (this.clip != clip) {
        this.clip = clip;
        bang();
      }
      return this;
    }

    public LXClip getClip() {
      return this.clip;
    }
  };

  public final FocusedClipParameter focusedClip = new FocusedClipParameter();

  private float frameRate = 0;

  public class Output extends LXOutputGroup implements LXOscComponent {
    Output(LX lx) {
      super(lx);
    }

    public String getOscAddress() {
      return "/lx/output";
    }
  }

  public interface Dispatch {
    public void dispatch();
  }

  public interface Listener {
    public void channelAdded(LXEngine engine, LXChannelBus channel);
    public void channelRemoved(LXEngine engine, LXChannelBus channel);
    public void channelMoved(LXEngine engine, LXChannelBus channel);
  }

  @Deprecated
  public interface MessageListener {
    public void onMessage(LXEngine engine, String message);
  }

  public final LXEngine addListener(Listener listener) {
    this.listeners.add(listener);
    return this;
  }

  public final LXEngine removeListener(Listener listener) {
    this.listeners.remove(listener);
    return this;
  }

  @Deprecated
  public final LXEngine addMessageListener(MessageListener listener) {
    this.messageListeners.add(listener);
    return this;
  }

  @Deprecated
  public final LXEngine removeMessageListener(MessageListener listener) {
    this.messageListeners.remove(listener);
    return this;
  }

  @Deprecated
  public LXEngine broadcastMessage(String message) {
    for (MessageListener listener : this.messageListeners) {
      listener.onMessage(this, message);
    }
    return this;
  }

  public class Timer {
    public long runNanos = 0;
    public long channelNanos = 0;
    public long inputNanos = 0;
    public long midiNanos = 0;
    public long oscNanos = 0;
    public long outputNanos = 0;
  }

  public final Timer timer = new Timer();

  // Buffer for a single frame, which was rendered with
  // a particular model state, has a main and a cue view,
  // and a cue state
  public static class Frame implements LXBuffer {

    private LXModel model;
    private int[] main = null;
    private int[] cue = null;
    private boolean cueOn = false;

    public Frame(LX lx) {
      setModel(lx.model);
    }

    void setModel(LXModel model) {
      this.model = model;
      if ((this.main == null) || (this.main.length != model.size)) {
        this.main = new int[model.size];
        this.cue = new int[model.size];
      }
    }

    void copyFrom(Frame that) {
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

  final ModelBuffer backgroundBlack;
  final ModelBuffer backgroundTransparent;
  private final ModelBuffer blendBufferLeft;
  private final ModelBuffer blendBufferRight;

  public final BooleanParameter isMultithreaded = new BooleanParameter("Threaded", false)
    .setDescription("Whether the engine and UI are on separate threads");

  public final BooleanParameter isChannelMultithreaded = new BooleanParameter("Channel Threaded", false)
    .setDescription("Whether the engine is multi-threaded per channel");

  public final BooleanParameter isNetworkMultithreaded = new BooleanParameter("Network Threaded", false)
    .setDescription("Whether the network output is on a separate thread");

  private volatile boolean isEngineThreadRunning = false;

  private boolean isNetworkThreadStarted = false;
  public final NetworkThread network;

  private EngineThread engineThread = null;

  boolean hasStarted = false;

  private boolean paused = false;

  private static final long INIT_RUN = -1;
  private long lastMillis = INIT_RUN;

  private double fixedDeltaMs = 0;

  long nowMillis = System.currentTimeMillis();

  LXEngine(final LX lx) {
    super(lx, LXComponent.ID_ENGINE, "Engine");
    LX.initTimer.log("Engine: Init");
    this.lx = lx;

    // Background and blending buffers
    this.buffer = new DoubleBuffer(lx);
    this.backgroundBlack = new ModelBuffer(lx, LXColor.BLACK);
    this.backgroundTransparent = new ModelBuffer(lx, 0);
    this.blendBufferLeft = new ModelBuffer(lx);
    this.blendBufferRight = new ModelBuffer(lx);
    LX.initTimer.log("Engine: Buffers");

    // Initialize network thread (don't start it yet)
    this.network = new NetworkThread(lx);

    // Set up global add blend
    this.addBlend = new AddBlend(lx);
    this.addBlend.onActive();

    // Master crossfader blend modes
    this.crossfaderBlendMode =
      new ObjectParameter<LXBlend>("Crossfader Blend", new LXBlend[1])
      .setDescription("Sets the blend mode used for the master crossfader");
    updateCrossfaderBlendOptions();
    this.crossfaderBlendMode.addListener(new LXParameterListener() {
      public void onParameterChanged(LXParameter p) {
        activeCrossfaderBlend.onInactive();
        activeCrossfaderBlend = crossfaderBlendMode.getObject();
        activeCrossfaderBlend.onActive();
      }
    });
    LX.initTimer.log("Engine: Blends");

    // Modulation matrix
    this.modulation = new LXModulationEngine(lx, this);
    LX.initTimer.log("Engine: Modulation");

    // Master channel
    this.masterChannel = new LXMasterChannel(lx);
    LX.initTimer.log("Engine: Master Channel");

    // Cue setup
    this.cueA.addListener(new LXParameterListener() {
      public void onParameterChanged(LXParameter p) {
        if (cueA.isOn()) {
          cueB.setValue(false);
          for (LXChannelBus channel : channels) {
            channel.cueActive.setValue(false);
          }
        }
      }
    });
    this.cueB.addListener(new LXParameterListener() {
      public void onParameterChanged(LXParameter p) {
        if (cueB.isOn()) {
          cueA.setValue(false);
          for (LXChannelBus channel : channels) {
            channel.cueActive.setValue(false);
          }
        }
      }
    });
    LX.initTimer.log("Engine: Cue");

    // Scenes
    for (int i = 0; i < this.scenes.length; ++i) {
      final int sceneIndex = i;
      this.scenes[i] = new BooleanParameter("Scene-" + (i+1));
      addParameter("scene-" + (i+1), this.scenes[i]);
      this.scenes[i].addListener(new LXParameterListener() {
        public void onParameterChanged(LXParameter p) {
          BooleanParameter scene = (BooleanParameter) p;
          if (scene.isOn()) {
            launchScene(sceneIndex);
            scene.setValue(false);
          }
        }
      });
    }

    // Master output
    this.output = new Output(lx);
    LX.initTimer.log("Engine: Output");

    // Midi engine
    this.midi = new LXMidiEngine(lx);
    LX.initTimer.log("Engine: Midi");

    this.audio = new LXAudioEngine(lx);
    LX.initTimer.log("Engine: Audio");

    // OSC engine
    this.osc = new LXOscEngine(lx);
    LX.initTimer.log("Engine: Osc");

    // Script engine
    this.script = new LXScriptEngine(lx);
    LX.initTimer.log("Engine: Script");

    // Listener
    this.focusedChannel.addListener(new LXParameterListener() {
      public void onParameterChanged(LXParameter p) {
        LXClip clip = focusedClip.getClip();
        if (clip != null && clip.bus != getFocusedChannel()) {
          focusedClip.setClip(null);
        }
      }
    });
    LX.initTimer.log("Engine: Focus Listener");

    lx.addListener(new LX.Listener() {
      @Override
      public void modelChanged(LX lx, LXModel model) {
        for (LXBus bus : channels) {
          bus.setModel(model);
        }
      }
    });

    // Parameters
    addParameter("crossfader", this.crossfader);
    addParameter("crossfaderBlendMode", this.crossfaderBlendMode);
    addParameter("speed", this.speed);
    addParameter("focusedChannel", this.focusedChannel);
    addParameter("cueA", this.cueA);
    addParameter("cueB", this.cueB);
    addParameter("multithreaded", this.isMultithreaded);
    addParameter("channelMultithreaded", this.isChannelMultithreaded);
    addParameter("networkMultithreaded", this.isNetworkMultithreaded);
    addParameter("framesPerSecond", this.framesPerSecond);
  }

  public void logTimers() {
    this.logTimers = true;
  }

  @Override
  public String getOscAddress() {
    return "/lx/engine";
  }

  public LXEngine setInputDispatch(Dispatch inputDispatch) {
    this.inputDispatch = inputDispatch;
    return this;
  }

  void updateCrossfaderBlendOptions() {
    for (LXBlend blend : this.crossfaderBlendMode.getObjects()) {
      if (blend != null) {
        blend.dispose();
      }
    }
    this.crossfaderBlendMode.setObjects(this.lx.instantiateCrossfaderBlends());
    this.activeCrossfaderBlend = this.crossfaderBlendMode.getObject();
    this.activeCrossfaderBlend.onActive();
  }

  void updateChannelBlendOptions() {
    for (LXChannelBus channel : this.channels) {
      channel.updateChannelBlendOptions();
    }
  }

  void updateTransitionBlendOptions() {
    for (LXChannelBus channel : this.channels) {
      if (channel instanceof LXChannel) {
        ((LXChannel) channel).updateTransitionBlendOptions();
      }
    }
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if (p == this.isNetworkMultithreaded) {
      if (this.isNetworkMultithreaded.isOn()) {
        this.buffer.sync();
        if (!this.isNetworkThreadStarted) {
          this.isNetworkThreadStarted = true;
          this.network.start();
        }
      }
    }
  }

  /**
   * Gets the active frame rate of the engine when in threaded mode
   *
   * @return How many FPS the engine is running
   */
  public float frameRate() {
    return this.frameRate;
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
   * Whether the engine is threaded. Generally, this should only be called
   * from the Processing animation thread.
   *
   * @return Whether the engine is threaded
   */
  public boolean isThreaded() {
    return this.isEngineThreadRunning;
  }

  /**
   * Starts the engine thread.
   */
  public void start() {
    if (this.lx.isP3LX) {
      throw new IllegalStateException("LXEngine start() may not be used from P3LX, call setThreaded() instead");
    }
    setThreaded(true);
    _setThreaded(true);
  }

  /**
   * Stops the engine thread.
   */
  public void stop() {
    if (this.lx.isP3LX) {
      throw new IllegalStateException("LXEngine stop() may not be used from P3LX, call setThreaded() instead");
    }
    setThreaded(false);
    _setThreaded(false);
  }

  /**
   * Sets the engine to threaded or non-threaded mode. Should only be called
   * from the Processing animation thread.
   *
   * @param threaded Whether engine should run on its own thread
   * @return this
   */
  public LXEngine setThreaded(boolean threaded) {
    this.isMultithreaded.setValue(threaded);
    return this;
  }

  public void onDraw() {
    if (this.isMultithreaded.isOn() != this.isEngineThreadRunning) {
      _setThreaded(this.isMultithreaded.isOn());
    }
  }

  private synchronized void _setThreaded(boolean threaded) {
    if (threaded == this.isEngineThreadRunning) {
      return;
    }
    if (!threaded) {
      // Set interrupt flag on the engine thread
      EngineThread engineThread = this.engineThread;
      engineThread.interrupt();
      // Called from another thread? If so, wait for engine thread to finish
      if (Thread.currentThread() != engineThread) {
        try {
          engineThread.join();
        } catch (InterruptedException ix) {
          throw new IllegalThreadStateException("Interrupted waiting to join LXEngine thread");
        }
      }
    } else {
      // Synchronize the two buffers, flip so that the engine thread doesn't start
      // rendering over the top of the buffer that the UI thread might be currently
      // working on drawing.
      this.buffer.sync();
      this.buffer.flip();
      this.isEngineThreadRunning = true;
      this.engineThread = new EngineThread();
      this.engineThread.start();
    }
  }

  private class EngineThread extends Thread {

    private EngineThread() {
      super("LXEngine Render Thread");
    }

    @Override
    public void run() {
      System.out.println("LXEngine Render Thread started");
      while (!isInterrupted()) {
        long frameStart = System.currentTimeMillis();
        LXEngine.this.run();
        if (isInterrupted()) {
          break;
        };

        // Sleep until next frame
        long frameMillis = System.currentTimeMillis() - frameStart;
        frameRate = 1000.f / frameMillis;
        float targetFPS = framesPerSecond.getValuef();
        if (targetFPS > 0) {
          long minMillisPerFrame = (long) (1000. / targetFPS);
          if (frameMillis < minMillisPerFrame) {
            frameRate = targetFPS;
            try {
              sleep(minMillisPerFrame - frameMillis);
            } catch (InterruptedException ix) {
              // We're done!
              break;
            }
          }
        }
      }

      // We are done threading
      frameRate = 0;
      engineThread = null;
      isEngineThreadRunning = false;

      System.out.println("LXEngine Render Thread finished");
    }
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
   * @param key Unique path key for saving and loading component
   * @param component Component
   * @return this
   */
  public LXEngine registerComponent(String key, LXComponent component) {
    this.components.put(key, component);
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

  public List<LXChannelBus> getChannels() {
    return this.channels;
  }

  public LXChannel getDefaultChannel() {
    for (LXChannelBus channel : this.channels) {
      if (channel instanceof LXChannel) {
        return (LXChannel) channel;
      }
    }
    return null;
  }

  public LXChannelBus getLastChannel() {
    return this.channels.get(this.channels.size() - 1);
  }

  public LXChannelBus getChannel(int channelIndex) {
    return this.mutableChannels.get(channelIndex);
  }

  public LXChannelBus getChannel(String label) {
    for (LXChannelBus channel : this.mutableChannels) {
      if (channel.getLabel().equals(label)) {
        return channel;
      }
    }
    return null;
  }

  public LXBus getFocusedChannel() {
    if (this.focusedChannel.getValuei() == this.mutableChannels.size()) {
      return this.masterChannel;
    }
    return getChannel(this.focusedChannel.getValuei());
  }

  public LXEngine setFocusedChannel(LXBus channel) {
    if (channel == this.masterChannel) {
      this.focusedChannel.setValue(this.mutableChannels.size());
    } else {
      this.focusedChannel.setValue(this.mutableChannels.indexOf(channel));
    }
    return this;
  }

  public LXEngine deselectChannel(LXBus bus) {
    boolean otherSelected = false;
    for (LXChannelBus channel : this.channels) {
      if (channel != bus && channel.selected.isOn()) {
        otherSelected = true;
        break;
      }
    }
    if (this.masterChannel != bus && this.masterChannel.selected.isOn()) {
      otherSelected = true;
    }
    if (otherSelected) {
      bus.selected.setValue(false);
    }
    return this;
  }

  public LXEngine selectChannel(LXBus bus) {
    return selectChannel(bus, false);
  }

  public LXEngine selectChannel(LXBus bus, boolean multipleSelection) {
    multipleSelection =
      multipleSelection &&
      (this.masterChannel != bus) &&
      !this.masterChannel.selected.isOn();
    if (!multipleSelection) {
      for (LXChannelBus channel : this.channels) {
        if (channel != bus) {
          channel.selected.setValue(false);
        }
      }
      if (this.masterChannel != bus) {
        this.masterChannel.selected.setValue(false);
      }
    } else {
      // In multiple selection mode, de-select anything from another group
      LXGroup busGroup = bus.getGroup();
      for (LXChannelBus channel : this.channels) {
        if (channel.getGroup() != busGroup) {
          channel.selected.setValue(false);
        }
      }
    }
    bus.selected.setValue(true);
    return this;
  }

  public LXEngine selectChannelRange(LXBus destination) {
    LXGroup selectedGroup = null;
    int minIndex = -1, maxIndex = -1;
    int selectIndex = destination.getIndex();

    for (LXChannelBus bus : this.channels) {
      if (bus.selected.isOn()) {
        selectedGroup = bus.getGroup();
        if (minIndex == -1) {
          minIndex = bus.getIndex();
        }
        maxIndex = bus.getIndex();
      }
    }

    if (selectIndex < minIndex) {
      maxIndex = minIndex;
      minIndex = selectIndex;
    } else {
      minIndex = maxIndex;
      maxIndex = selectIndex;
    }

    for (LXChannelBus bus : this.channels) {
      int busIndex = bus.getIndex();
      boolean selected = (bus.getGroup() == selectedGroup) && (busIndex >= minIndex) && (busIndex <= maxIndex);
      bus.selected.setValue(selected);
    }

    this.masterChannel.selected.setValue(false);

    return this;
  }

  public LXChannel addChannel() {
    return addChannel(new LXPattern[] { new IteratorPattern(this.lx) });
  }

  public LXChannel addChannel(LXPattern[] patterns) {
    LXChannel channel = new LXChannel(this.lx, this.mutableChannels.size(), patterns);
    _addChannel(channel);
    return channel;
  }

  public LXEngine ungroup(LXChannel channel) {
    boolean focused = this.focusedChannel.getValuei() == channel.index;
    LXGroup group = channel.getGroup();
    if (group != null) {
      group.removeChannel(channel);
      this.mutableChannels.remove(channel);
      this.mutableChannels.add(group.getIndex() + group.channels.size() + 1, channel);
      _reindexChannels();
      for (Listener listener : this.listeners) {
        listener.channelMoved(this, channel);
      }
      if (focused) {
        this.focusedChannel.setValue(channel.index);
      }
    }
    return this;
  }

  public LXGroup addGroup() {
    return addGroup(true);
  }

  private LXGroup addGroup(boolean fromSelection) {
    if (!fromSelection) {
      LXGroup group = new LXGroup(this.lx, this.mutableChannels.size());
      _addChannel(group, group.getIndex());
      return group;
    }

    List<LXChannel> groupChannels = new ArrayList<LXChannel>();
    int groupIndex = -1;
    for (LXChannelBus channel : this.channels) {
      if (channel.isChannel() && channel.selected.isOn() && !channel.isInGroup()) {
        if (groupIndex < 0) {
          groupIndex = channel.index;
        }
        groupChannels.add((LXChannel) channel);
      }
    }
    if (groupIndex >= 0) {
      LXGroup group = new LXGroup(this.lx, groupIndex);
      int reindex = groupIndex;
      for (LXChannel channel : groupChannels) {
        // Put the group channels in order in their group
        this.mutableChannels.remove(channel);
        this.mutableChannels.add(reindex++, channel);
        group.addChannel(channel);
      }
      _addChannel(group, group.getIndex());

      // Fix indexing on all channels
      _reindexChannels();

      // This new group channel is focused now!
      if (this.focusedChannel.getValuei() == groupIndex) {
        this.focusedChannel.bang();
      } else {
        this.focusedChannel.setValue(groupIndex);
      }
      selectChannel(group);
      return group;
    }

    return null;
  }

  private void _addChannel(LXChannelBus channel) {
    _addChannel(channel, this.mutableChannels.size());
  }

  private void _addChannel(LXChannelBus channel, int index) {
    channel.setParent(this);
    this.mutableChannels.add(index, channel);
    this.focusedChannel.setRange(this.mutableChannels.size() + 1);
    for (Listener listener : this.listeners) {
      listener.channelAdded(this, channel);
    }
    _reindexChannels();
  }

  private void _reindexChannels() {
    int i = 0;
    for (LXChannelBus channelBus : this.mutableChannels) {
      channelBus.setIndex(i++);
    }
  }

  public void removeSelectedChannels() {
    List<LXChannelBus> toRemove = new ArrayList<LXChannelBus>();
    for (LXChannelBus channel : this.mutableChannels) {
      if (channel.selected.isOn() && !toRemove.contains(channel.getGroup())) {
        toRemove.add(channel);
      }
    }
    for (LXChannelBus channel : toRemove) {
      removeChannel(channel);
    }
  }

  public void removeChannel(LXChannelBus channel) {
    if (!this.mutableChannels.contains(channel)) {
      throw new IllegalStateException("Engine does not contain channel: " + channel);
    }

    // Group channel? Remove all of the children first...
    if (channel instanceof LXGroup) {
      LXGroup group = (LXGroup) channel;
      List<LXChannel> removeGroupChannels = new ArrayList<LXChannel>(group.channels);
      for (LXChannel c : removeGroupChannels) {
        removeChannel(c);
      }
    }

    // Are we in a group? Get out of it
    if (channel instanceof LXChannel) {
      LXGroup group = channel.getGroup();
      if (group != null) {
        group.removeChannel((LXChannel) channel);
      }
    }

    // Remove ourselves
    this.mutableChannels.remove(channel);

    // Fix indexing on all channels
    _reindexChannels();

    boolean notified = false;
    if (this.focusedChannel.getValuei() > this.mutableChannels.size()) {
      notified = true;
      this.focusedChannel.decrement();
    }
    this.focusedChannel.setRange(this.mutableChannels.size() + 1);
    if (!notified) {
      this.focusedChannel.bang();
    }
    for (Listener listener : this.listeners) {
      listener.channelRemoved(this, channel);
    }
    channel.dispose();
  }

  public void moveChannel(LXChannelBus channel, int delta) {
    if (delta != 1 && delta != -1) {
      throw new IllegalArgumentException("moveChannel() may only be called with delta of -1 or 1");
    }
    LXBus focused = getFocusedChannel();

    int index = channel.getIndex() + delta;
    if (index < 0 || index >= this.mutableChannels.size()) {
      return;
    }

    LXGroup group = channel.getGroup();
    if (group != null) {
      // Channel is within a group, cannot be moved out of it
      if (index <= group.getIndex() || index > (group.getIndex() + group.channels.size())) {
        return;
      }
      this.mutableChannels.remove(channel);
      this.mutableChannels.add(index, channel);
    } else {
      // Channel is top-level, need to move groups in chunks
      boolean isGroup = channel instanceof LXGroup;
      if (isGroup && delta > 0) {
        delta += ((LXGroup) channel).channels.size();
      }
      int neighborIndex = channel.getIndex() + delta;
      if (neighborIndex < 0 || neighborIndex >= this.mutableChannels.size()) {
        return;
      }

      // Figure out who our neighbor is
      LXChannelBus neighbor = this.mutableChannels.get(neighborIndex);
      LXGroup neighborGroup = (neighbor instanceof LXGroup) ? (LXGroup) neighbor : neighbor.getGroup();
      if (neighborGroup != null) {
        // Our neighbor is a group, flip-flop entirely with them
        if (delta > 0) {
          // Neighboring group is to our right, move their start position to our position
          int startIndex = channel.getIndex();
          this.mutableChannels.remove(neighbor);
          this.mutableChannels.add(startIndex, neighbor);
          for (LXChannel subchannel : ((LXGroup) neighbor).channels) {
            this.mutableChannels.remove(subchannel);
            this.mutableChannels.add(++startIndex, subchannel);
          }
        } else {
          // Neighboring group is to our left, move us to their start position
          int startIndex = neighborGroup.getIndex();
          this.mutableChannels.remove(channel);
          this.mutableChannels.add(startIndex, channel);
          if (isGroup) {
            for (LXChannel subchannel : ((LXGroup) channel).channels) {
              this.mutableChannels.remove(subchannel);
              this.mutableChannels.add(++startIndex, subchannel);
            }
          }
        }
      } else {
        // Our neighbor is a single channel
        if (delta > 0) {
          // Neighbor is to our right, move their start position to our position
          int startIndex = channel.getIndex();
          this.mutableChannels.remove(neighbor);
          this.mutableChannels.add(startIndex, neighbor);
        } else {
          // Neighbor is to our left, move them past us
          int endIndex = channel.getIndex();
          if (isGroup) {
            endIndex += ((LXGroup) channel).channels.size();
          }
          this.mutableChannels.remove(neighbor);
          this.mutableChannels.add(endIndex, neighbor);
        }

      }
    }

    // Fix indexing on all channels
    _reindexChannels();

    // Focused channel may have moved
    this.focusedChannel.setValue(focused.getIndex());

    for (Listener listener : this.listeners) {
      listener.channelMoved(this, channel);
    }
  }

  /**
   * Get the boolean parameter that launches a scene
   *
   * @param index Index of scene
   * @return Scene at index
   */
  public BooleanParameter getScene(int index) {
    return this.scenes[index];
  }

  /**
   * Launches the scene at given index
   *
   * @param index Scene index
   * @return this
   */
  public LXEngine launchScene(int index) {
    LXClip clip;
    for (LXChannelBus channel : this.lx.engine.channels) {
      clip = channel.getClip(index);
      if (clip != null) {
        clip.trigger();
      }
    }
    clip = this.masterChannel.getClip(index);
    if (clip != null) {
      clip.trigger();
    }
    return this;
  }

  /**
   * Stops all running clips
   *
   * @return this
   */
  public LXEngine stopClips() {
    for (LXChannelBus channel : this.lx.engine.channels) {
      for (LXClip clip : channel.clips) {
        if (clip != null) {
          clip.stop();
        }
      }
    }
    for (LXClip clip : this.masterChannel.clips) {
      if (clip != null) {
        clip.stop();
      }
    }
    return this;
  }

  public void setPatterns(LXPattern[] patterns) {
    LXChannel channel = getDefaultChannel();
    if (channel != null) {
      channel.setPatterns(patterns);
    }
  }

  public List<LXPattern> getPatterns() {
    LXChannel channel = getDefaultChannel();
    if (channel != null) {
      return channel.getPatterns();
    }
    return null;
  }

  protected LXPattern getActivePattern() {
    LXChannel channel = getDefaultChannel();
    if (channel != null) {
      return channel.getActivePattern();
    }
    return null;
  }

  protected LXPattern getNextPattern() {
    LXChannel channel = getDefaultChannel();
    if (channel != null) {
      return channel.getNextPattern();
    }
    return null;
  }

  public void goPrev() {
    LXChannel channel = getDefaultChannel();
    if (channel != null) {
      channel.goPrev();
    }
  }

  public final void goNext() {
    LXChannel channel = getDefaultChannel();
    if (channel != null) {
      channel.goNext();
    }
  }

  public void goPattern(LXPattern pattern) {
    LXChannel channel = getDefaultChannel();
    if (channel != null) {
      channel.goPattern(pattern);
    }
  }

  public void goIndex(int index) {
    LXChannel channel = getDefaultChannel();
    if (channel != null) {
      channel.goIndex(index);
    }
  }

  protected void disableAutoCycle() {
    LXChannel channel = getDefaultChannel();
    if (channel != null) {
      channel.disableAutoCycle();
    }
  }

  protected void enableAutoCycle(int autoCycleThreshold) {
    LXChannel channel = getDefaultChannel();
    if (channel != null) {
      channel.enableAutoCycle(autoCycleThreshold);
    }
  }

  @Override
  public LXModulationEngine getModulation() {
    return this.modulation;
  }

  private class BlendStack {

    private int[] destination;
    private int[] output;
    private boolean hasOutput;

    void initialize(int[] destination, int[] output) {
      this.destination = destination;
      this.output = output;
      this.hasOutput = false;
    }

    void blend(LXBlend blend, BlendStack that, double alpha) {
      blend(blend, that.destination, alpha);
    }

    void blend(LXBlend blend, int[] src, double alpha) {
      blend.blend(this.destination, src, alpha, this.output);
      this.destination = this.output;
      this.hasOutput = true;
    }

    void transition(LXBlend blend, int[] src, double lerp) {
      blend.lerp(this.destination, src, lerp, this.output);
      this.destination = this.output;
      this.hasOutput = true;
    }

    void copyFrom(BlendStack that) {
      System.arraycopy(that.destination, 0, this.output, 0, that.destination.length);
      this.destination = this.output;
      this.hasOutput = true;
    }

    void flatten() {
      if (!this.hasOutput) {
        System.arraycopy(this.destination, 0, this.output, 0, this.destination.length);
        this.destination = this.output;
        this.hasOutput = true;
      }
    }

  }

  private final BlendStack blendStackMain = new BlendStack();
  private final BlendStack blendStackCue = new BlendStack();
  private final BlendStack blendStackLeft = new BlendStack();
  private final BlendStack blendStackRight = new BlendStack();

  public void run() {

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

    if (this.paused) {
      this.timer.channelNanos = 0;
      ((LXBus.Timer) this.masterChannel.timer).effectNanos = 0;
      this.timer.runNanos = System.nanoTime() - runStart;
      return;
    }

    // Process MIDI events
    long midiStart = System.nanoTime();
    this.midi.dispatch();
    this.timer.midiNanos = System.nanoTime() - midiStart;

    // Process OSC events
    long oscStart = System.nanoTime();
    this.osc.dispatch();
    this.timer.oscNanos = System.nanoTime() - oscStart;

    // Process UI input events
    if (this.inputDispatch == null) {
      this.timer.inputNanos = 0;
    } else {
      long inputStart = System.nanoTime();
      this.inputDispatch.dispatch();
      this.timer.inputNanos = System.nanoTime() - inputStart;
    }

    // Initialize the model context for this render frame
    this.buffer.render.setModel(this.lx.model);

    // Run tempo and audio, always using real-time
    this.lx.tempo.loop(deltaMs);
    this.audio.loop(deltaMs);

    // Mutate by master speed for everything else
    deltaMs *= this.speed.getValue();

    // Run the modulation engine
    this.modulation.loop(deltaMs);

    // Run the color control
    this.lx.palette.loop(deltaMs);

    // Run top-level loop tasks
    for (LXLoopTask loopTask : this.loopTasks) {
      loopTask.loop(deltaMs);
    }

    // Run once-tasks
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

    // Okay, time for the real work, to run and blend all of our channels
    // First, set up a bunch of state to keep track of which buffers we
    // are rendering into.
    long channelStart = System.nanoTime();

    // Initialize blend stacks
    this.blendStackMain.initialize(this.backgroundBlack.getArray(), this.buffer.render.main);
    this.blendStackCue.initialize(this.backgroundBlack.getArray(), this.buffer.render.cue);
    this.blendStackLeft.initialize(this.backgroundBlack.getArray(), this.blendBufferLeft.getArray());
    this.blendStackRight.initialize(this.backgroundBlack.getArray(), this.blendBufferRight.getArray());

    double crossfadeValue = this.crossfader.getValue();

    boolean leftBusActive = crossfadeValue < 1.;
    boolean rightBusActive = crossfadeValue > 0.;
    boolean cueBusActive = false;

    boolean isChannelMultithreaded = this.isChannelMultithreaded.isOn();

    // Step 1a: Loop all of the channels
    if (isChannelMultithreaded) {
      // If we are in super-threaded mode, run the channels on their own threads!
      for (LXChannelBus channel : this.channels) {
        synchronized (channel.thread) {
          channel.thread.signal.workDone = false;
          channel.thread.deltaMs = deltaMs;
          channel.thread.workReady = true;
          channel.thread.notify();
          if (!channel.thread.hasStarted) {
            channel.thread.hasStarted = true;
            channel.thread.start();
          }
        }
      }

      // Wait for all the channel threads to finish
      for (LXChannelBus channel : this.mutableChannels) {
        synchronized (channel.thread.signal) {
          while (!channel.thread.signal.workDone) {
            try {
              channel.thread.signal.wait();
            } catch (InterruptedException ix) {
              Thread.currentThread().interrupt();
              break;
            }
          }
          channel.thread.signal.workDone = false;
        }
      }
    } else {
      // We are not in super-threaded mode, just loop all the channels
      for (LXChannelBus channel : this.channels) {
        channel.loop(deltaMs);
      }
    }
    // Step 1b: Run the master channel (it may have clips on it)
    this.masterChannel.loop(deltaMs);
    this.timer.channelNanos = System.nanoTime() - channelStart;

    // Step 2: composite any group channels
    for (LXChannelBus channel : this.channels) {
      if (channel instanceof LXGroup && channel.isAnimating) {
        ((LXGroup) channel).afterLoop(deltaMs);
      }
    }

    // Step 3: blend the channel buffers down
    boolean blendLeft = leftBusActive || this.cueA.isOn();
    boolean blendRight = rightBusActive || this.cueB.isOn();
    boolean leftExists = false, rightExists = false;
    for (LXChannelBus channel : this.channels) {
      long blendStart = System.nanoTime();

      // Is this a group sub-channel? Those don't blend, they are already composited
      // into their group
      boolean isSubChannel = channel.getGroup() != null;

      // Blend into the output buffer
      if (!isSubChannel) {
        BlendStack blendStack = null;

        // Which output group is this channel mapped to
        switch (channel.crossfadeGroup.getEnum()) {
        case A:
          leftExists = true;
          blendStack = blendLeft ? this.blendStackLeft : null;
          break;
        case B:
          rightExists = true;
          blendStack = blendRight ? this.blendStackRight : null;
          break;
        default:
        case BYPASS:
          blendStack = blendStackMain;
          break;
        }

        if (blendStack != null && channel.enabled.isOn()) {
          double alpha = channel.fader.getValue();
          if (alpha > 0) {
            blendStack.blend(channel.blendMode.getObject(), channel.getColors(), alpha);
          }
        }
      }

      // Blend into the cue buffer, always a direct add blend for any type of channel
      if (channel.cueActive.isOn()) {
        cueBusActive = true;
        this.blendStackCue.blend(this.addBlend, channel.getColors(), 1);
      }

      ((LXChannelBus.Timer) channel.timer).blendNanos = System.nanoTime() - blendStart;
    }

    // Check if the crossfade group buses are cued
    if (this.cueA.isOn()) {
      this.blendStackCue.copyFrom(this.blendStackLeft);
      cueBusActive = true;
    } else if (this.cueB.isOn()) {
      this.blendStackCue.copyFrom(this.blendStackRight);
      cueBusActive = true;
    }

    // Step 4: now we have three output buses that need mixing... the left/right crossfade
    // groups plus the main buffer. We figure out which of them are active and blend appropriately
    // Note that the A+B crossfade groups are additively mixed AFTER the main buffer
    boolean leftContent = leftBusActive && leftExists;
    boolean rightContent = rightBusActive && rightExists;

    if (leftContent && rightContent) {
      // There are left and right channels assigned!
      LXBlend blend = this.crossfaderBlendMode.getObject();
      blendStackLeft.transition(blend, blendStackRight.destination, crossfadeValue);
      // Add the crossfaded groups to the main buffer
      this.blendStackMain.blend(this.addBlend, blendStackLeft, 1.);
    } else if (leftContent) {
      // Add the left group to the main buffer
      this.blendStackMain.blend(this.addBlend, this.blendStackLeft, Math.min(1, 2. * (1-crossfadeValue)));
    } else if (rightContent) {
      // Add the right group to the main buffer
      this.blendStackMain.blend(this.addBlend, this.blendStackRight, Math.min(1, 2. * crossfadeValue));
    }

    // Check for edge case of all channels being off, don't leave stale data in blend buffer!
    this.blendStackMain.flatten();

    // Time to apply master FX to the main blended output
    long effectStart = System.nanoTime();
    for (LXEffect effect : this.masterChannel.getEffects()) {
      effect.setBuffer(this.buffer.render);
      effect.loop(deltaMs);
    }
    ((LXBus.Timer) this.masterChannel.timer).effectNanos = System.nanoTime() - effectStart;

    // Add fixture identification very last
    int identifyColor = LXColor.hsb(0, 0, Math.abs(-100 + (effectStart / 10000000) % 200));
    for (LXFixture fixture : this.lx.structure.fixtures) {
      if (fixture.identify.isOn()) {
        for (LXPoint p : fixture.points) {
          this.buffer.render.main[p.index] = identifyColor;
          this.buffer.render.cue[p.index] = identifyColor;
        }
      }
    }

    // Step 5: our cue and render frames are ready! Let's get them output
    boolean isNetworkMultithreaded = this.isNetworkMultithreaded.isOn();
    boolean isDoubleBuffering = this.isEngineThreadRunning || isNetworkMultithreaded;
    this.buffer.render.cueOn = cueBusActive;
    if (isDoubleBuffering) {
      // We are multi-threading, lock the double buffer and flip it
      this.buffer.flip();
    }
    if (isNetworkMultithreaded) {
      // Notify the network thread of new work to do!
      synchronized (this.network) {
        this.network.notify();
      }
    } else {
      // Or do it ourself here on the engine thread
      long outputStart = System.nanoTime();
      this.output.send(isDoubleBuffering ? this.buffer.copy.main : this.buffer.render.main);
      this.timer.outputNanos = System.nanoTime() - outputStart;
    }

    // All done running this pass of the engine!
    this.timer.runNanos = System.nanoTime() - runStart;

    // Debug trace logging
    if (this.logTimers) {
      _logTimers();
      this.logTimers = false;
    }
  }

  private void _logTimers() {
    StringBuilder sb = new StringBuilder();
    sb.append("LXEngine::run() " + ((int) (this.timer.runNanos / 1000000)) + "ms\n");
    sb.append("LXEngine::run()::channels " + ((int) (this.timer.channelNanos / 1000000)) + "ms\n");
    for (LXChannelBus channel : this.channels) {
      sb.append("LXEngine::" + channel.getLabel() + "::loop() " + ((int) (channel.timer.loopNanos / 1000000)) + "ms\n");
      if (channel instanceof LXChannel) {
        LXPattern pattern = ((LXChannel) channel).getActivePattern();
        sb.append("LXEngine::" + channel.getLabel() + "::" + pattern.getLabel() + "::run() " + ((int) (pattern.timer.runNanos / 1000000)) + "ms\n");
      }
    }
    System.out.println(sb);
  }

  public class NetworkThread extends Thread {

    public class Timer {
      public long copyNanos = 0;
      public long sendNanos = 0;
    }

    private long lastFrame = System.currentTimeMillis();

    private float frameRate = 0;

    public final Timer timer = new Timer();

    private final Frame networkFrame;

    NetworkThread(LX lx) {
      super("LXEngine Network Thread");
      this.networkFrame = new Frame(lx);
    }

    @Override
    public void run() {
      System.out.println("LXEngine Network Thread started");
      while (!isInterrupted()) {
        try {
          synchronized(this) {
            wait();
          }
        } catch (InterruptedException ix) {
          System.out.println("LXEngine Network Thread interrupted");
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
          output.send(this.networkFrame.main);
          this.timer.sendNanos = System.nanoTime() - copyEnd;
        }

        // Compute network framerate
        long now = System.currentTimeMillis();
        this.frameRate = 1000.f / (now - this.lastFrame);
        this.lastFrame = now;
      }

      System.out.println("LXEngine Network Thread finished");
    }

    public float frameRate() {
      return this.frameRate;
    }
  }

  /**
   * This should be used when in threaded mode. It synchronizes on the
   * double-buffer and duplicates the internal copy buffer into the provided
   * buffer.
   *
   * @param copy Buffer to copy into
   */
  public void copyFrameThreadSafe(Frame frame) {
    this.buffer.copyTo(frame);
  }

  public void getFrameNonThreadSafe(Frame frame) {
    frame.copyFrom(this.buffer.render);
  }

  private static final String KEY_PALETTE = "palette";
  private static final String KEY_CHANNELS = "channels";
  private static final String KEY_MASTER = "master";
  private static final String KEY_TEMPO = "tempo";
  private static final String KEY_AUDIO = "audio";
  private static final String KEY_COMPONENTS = "components";
  private static final String KEY_OUTPUT = "output";
  private static final String KEY_MODULATION = "modulation";
  private static final String KEY_OSC = "osc";
  private static final String KEY_MIDI = "midi";


  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_PALETTE, LXSerializable.Utils.toObject(lx, this.lx.palette));
    obj.add(KEY_CHANNELS, LXSerializable.Utils.toArray(lx, this.mutableChannels));
    obj.add(KEY_MASTER, LXSerializable.Utils.toObject(lx, this.masterChannel));
    obj.add(KEY_TEMPO, LXSerializable.Utils.toObject(lx, this.lx.tempo));
    obj.add(KEY_AUDIO, LXSerializable.Utils.toObject(lx, this.audio));
    obj.add(KEY_OUTPUT, LXSerializable.Utils.toObject(lx, this.output));
    obj.add(KEY_COMPONENTS, LXSerializable.Utils.toObject(lx, this.components));
    obj.add(KEY_MODULATION, LXSerializable.Utils.toObject(lx, this.modulation));
    obj.add(KEY_OSC, LXSerializable.Utils.toObject(lx, this.osc));
    obj.add(KEY_MIDI, LXSerializable.Utils.toObject(lx, this.midi));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    // TODO(mcslee): remove loop tasks that other things might have added? maybe
    // need to separate application-owned loop tasks from project-specific ones...

    // Clear all the modulation
    this.modulation.clear();

    // Remove all channels
    for (int i = this.mutableChannels.size() - 1; i >= 0; --i) {
      removeChannel(this.mutableChannels.get(i));
    }

    // Master channel settings
    this.masterChannel.load(lx, obj.has(KEY_MASTER) ? obj.getAsJsonObject(KEY_MASTER) : new JsonObject());

    // Add the new channels
    if (obj.has(KEY_CHANNELS)) {
      JsonArray channelsArray = obj.getAsJsonArray(KEY_CHANNELS);
      for (JsonElement channelElement : channelsArray) {
        String channelClass = channelElement.getAsJsonObject().get(KEY_CLASS).getAsString();
        LXChannelBus channel;
        if (channelClass.equals("heronarts.lx.LXGroup")) {
          channel = addGroup(false);
        } else {
          // TODO(mcslee): improve efficiency, allow no-patterns in a channel?
          channel = addChannel();
        }
        channel.load(lx, (JsonObject) channelElement);
      }
    } else {
      addChannel().fader.setValue(1);
    }

    // Palette
    if (obj.has(KEY_PALETTE)) {
      lx.palette.load(lx, obj.getAsJsonObject(KEY_PALETTE));
    }

    // Tempo
    if (obj.has(KEY_TEMPO)) {
      lx.tempo.load(lx, obj.getAsJsonObject(KEY_TEMPO));
    }

    // Audio setup
    if (obj.has(KEY_AUDIO)) {
      this.audio.load(lx, obj.getAsJsonObject(KEY_AUDIO));
    }

    // Generic components
    if (obj.has(KEY_COMPONENTS)) {
      JsonObject componentsObj = obj.getAsJsonObject(KEY_COMPONENTS);
      for (String key : this.components.keySet()) {
        if (componentsObj.has(key)) {
          this.components.get(key).load(lx, componentsObj.getAsJsonObject(key));
        }
      }
    }

    // Output setup
    if (obj.has(KEY_OUTPUT)) {
      this.output.load(lx, obj.getAsJsonObject(KEY_OUTPUT));
    }

    // Modulation matrix
    this.modulation.load(lx, obj.has(KEY_MODULATION) ? obj.getAsJsonObject(KEY_MODULATION) : new JsonObject());

    // OSC
    if (obj.has(KEY_OSC)) {
      this.osc.load(lx, obj.getAsJsonObject(KEY_OSC));
    }

    // Midi
    this.midi.load(lx, obj.has(KEY_MIDI) ? obj.getAsJsonObject(KEY_MIDI) : new JsonObject());

    // Parameters etc.
    super.load(lx, obj);

    // Notify all the active patterns
    for (LXChannelBus channel : this.channels) {
      if (channel instanceof LXChannel) {
        ((LXChannel) channel).getActivePattern().onActive();
      }
    }
  }

}
