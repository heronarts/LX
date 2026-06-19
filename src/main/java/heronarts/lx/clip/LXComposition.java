/**
 * Copyright 2025- Justin K. Belcher, Heron Arts LLC
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
 * @author Justin K. Belcher <justin@jkb.studio>
 */

package heronarts.lx.clip;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXPath;
import heronarts.lx.LXSerializable;
import heronarts.lx.clip.Cursor.Operator;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXMasterBus;
import heronarts.lx.mixer.LXMixerEngine;
import heronarts.lx.parameter.LXNormalizedParameter;
import heronarts.lx.parameter.TriggerParameter;
import heronarts.lx.utils.LXUtils;
import heronarts.lx.utils.ObservableList;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class LXComposition extends LXClip {

  private final Map<LXBus, BusClipLane> busLanes = new HashMap<>();
  private final List<AudioClipLane> audioLanes = new CopyOnWriteArrayList<>();

  private final ObservableList<Locator> mutableLocators = new ObservableList<>();
  public final ObservableList<Locator> locators = this.mutableLocators.asUnmodifiableList();

  public final TriggerParameter prevLocator =
    new TriggerParameter("Previous Locator", this::goPreviousLocator)
    .setDescription("Moves to the previous locator");

  public final TriggerParameter nextLocator =
    new TriggerParameter("Next Locator", this::goNextLocator)
    .setDescription("Moves to the next locator");

  private final AudioPlayer audioPlayer;

  public LXComposition(LX lx, LXTimelineEngine timeline) {
    super(lx, timeline, 0);

    this.audioPlayer = new AudioPlayer(lx);

    addParameter("prevLocator", this.prevLocator);
    addParameter("nextLocator", this.nextLocator);
    addArray("locator", this.locators);

    // Maintain one lane per mixer channel
    lx.engine.mixer.addListener(this.mixerListener);
    initializeBusLanes();
  }

  @Override
  public String getPath() {
    return "composition";
  }

  @Override
  protected boolean isLaneRecording(LXClipLane<?> lane) {
    LXBus bus = getLaneBus(lane);
    if (bus != null) {
      return bus.arm.isOn();
    }
    return false;
  }

  protected LXBus getLaneBus(LXClipLane<?> lane) {
    return switch(lane) {
    case BusClipLane busLane -> busLane.bus;
    case MidiNoteClipLane midiLane -> midiLane.channel;
    case ParameterClipLane parameterLane -> parameterLane.parameter.getAncestor(LXBus.class);
    case PatternClipLane patternLane -> patternLane.channel;
    default -> null;
    };
  }

  public void toggleBusLaneVisibility(BusClipLane busLane, boolean expanded) {
    for (LXClipLane<?> lane : findAllBusLanes(busLane.bus, false)) {
      lane.uiVisible.setValue(expanded);
    }
  }

  @Override
  public Cursor.Parameter getLaunchPosition() {
    return this.insertMarker;
  }

  @Override
  protected int getParameterLaneInsertIndex(LXNormalizedParameter parameter) {
    boolean next = false;
    int index = 0;
    for (LXClipLane<?> lane : this.lanes) {
      if (next && lane.isCompositionMajorLane()) {
        return index;
      }
      if (lane instanceof BusClipLane busLane) {
        if (parameter.isDescendant(busLane.bus)) {
          next = true;
        }
      }
      ++index;
    }
    return -1;
  }

  // Recording

  @Override
  protected boolean enableLoopOnFirstRecording() {
    return false;
  }

  @Override
  protected void onStartRecording(boolean isOverdub) {
    super.onStartRecording(isOverdub);
  }

  @Override
  protected void onStopRecording() {
    super.onStopRecording();
    this.lx.engine.timeline.arm.setValue(false);
    if (!isRunning()) {
      stopAudioPlayback();
    }
  }

  // Playback

  @Override
  protected void run(double deltaMs) {
    super.run(deltaMs);
    for (BusClipLane busLane : this.busLanes.values()) {
      busLane.run(deltaMs);
    }
    if (!this.audioLanes.isEmpty()) {
      this.audioPlayer.ensureRunning();
    }
  }

  @Override
  protected void onStopPlayback() {
    stopAudioPlayback();
  }

  private void stopAudioPlayback() {
    this.audioPlayer.stop();
    for (AudioClipLane lane : this.audioLanes) {
      lane.clearActiveEvent();
    }
  }

  /**
   * Get the list of audio lanes for playback
   */
  public List<AudioClipLane> getAudioLanes() {
    return this.audioLanes;
  }

  // Bus Lanes

  private void initializeBusLanes() {
    for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
      _createBusLane(channel);
    }
    _createBusLane(this.lx.engine.mixer.masterBus);
  }

  private void _createBusLane(LXBus bus) {
    if (!this.busLanes.containsKey(bus)) {
      addBusLanes(bus, -1);
    }
  }

  private List<LXClipLane<?>> findAllBusLanes(LXBus bus, boolean includeMainBusLane) {
    List<LXClipLane<?>> lanes = new ArrayList<>();
    for (LXClipLane<?> lane : this.lanes) {
      if ((lane.getBus() == bus) && (includeMainBusLane || !(lane instanceof BusClipLane))) {
        lanes.add(lane);
      }
    }
    return lanes;
  }

  /**
   * Re-sort bus lanes to match current mixer channel order.
   */
  private void moveBusLanes(LXAbstractChannel bus) {
    int fromIndex = this.busLanes.get(bus).getIndex();
    int toIndex = getBusLaneInsertIndex(bus);

    if (toIndex < fromIndex) {
      // Moving to the left, increment toIndex as we go
      for (LXClipLane<?> move : findAllBusLanes(bus, true)) {
        moveClipLane(move, toIndex++);
      }
    } else {
      // Moving to the right, sequentially insert before target position
      for (LXClipLane<?> move : findAllBusLanes(bus, true)) {
        moveClipLane(move, toIndex-1);
      }
    }
  }

  private final LXMixerEngine.Listener mixerListener = new LXMixerEngine.Listener() {
    @Override
    public void channelAdded(LXMixerEngine mixer, LXAbstractChannel channel) {
      registerBus(channel);
    }

    @Override
    public void channelRemoved(LXMixerEngine mixer, LXAbstractChannel channel) {
      unregisterBus(channel);
    }

    @Override
    public void channelMoved(LXMixerEngine mixer, LXAbstractChannel channel) {
      moveBusLanes(channel);
    }
  };

  private void registerBus(LXBus bus) {
    addBusLanes(bus, -1);
  }

  private BusClipLane findBusLane(LXBus bus, int index) {
    BusClipLane lane = this.busLanes.get(bus);
    return (lane != null) ? lane : addBusLanes(bus, index);
  }

  private int getBusLaneInsertIndex(LXBus bus) {
    int index = 0;
    for (LXClipLane<?> lane : this.lanes) {
      if (lane instanceof BusClipLane busLane) {
        if (busLane.bus instanceof LXMasterBus ||
            (busLane.bus.getIndex() == bus.getIndex() + 1)) {
          return index;
        }
      }
      ++index;
    }
    return index;
  }

  /**
   * Create a new lane based on a mixer channel, inserting it at the
   * correct position to maintain audio-before-bus ordering.
   */
  private BusClipLane addBusLanes(LXBus bus, int index) {
    if (this.busLanes.containsKey(bus)) {
      throw new IllegalStateException("Cannot create duplicate composition lane for bus: " + bus);
    }

    // TODO: use command engine to add lane?
    // (mcslee: not sure about that... only done automatically by API, user doesn't take
    // the action of adding bus lanes, this is an internal API)
    BusClipLane lane = new BusClipLane(this, bus);
    this.busLanes.put(bus, lane);
    if (bus instanceof LXMasterBus) {
      this.mutableLanes.add(lane);
      onClipLaneAdded(lane);
    } else {
      if (index < 0) {
        index = getBusLaneInsertIndex(bus);
      }
      if (index < 0) {
        this.mutableLanes.add(lane);
      } else {
        this.mutableLanes.add(index, lane);
      }
      onClipLaneAdded(lane);

      // Add MIDI and Pattern lane for mixer channel bus
      if (bus instanceof LXAbstractChannel channel) {
        MidiNoteClipLane midiLane = new MidiNoteClipLane(this, channel);
        this.mutableLanes.add(++index, midiLane);
        onClipLaneAdded(midiLane);
      }
      if (bus instanceof LXChannel channel) {
        PatternClipLane patternLane = new PatternClipLane(this, channel);
        this.mutableLanes.add(++index, patternLane);
        onClipLaneAdded(patternLane);
      }
    }
    return lane;
  }

  private PatternClipLane findPatternLane(LXChannel channel) {
    for (LXClipLane<?> lane : this.lanes) {
      if (lane instanceof PatternClipLane patternLane && patternLane.channel == channel) {
        return patternLane;
      }
    }
    return null;
  }

  private PatternClipLane loadPatternLane(JsonObject laneObj, int index) {
    if (laneObj.has(PatternClipLane.KEY_CHANNEL)) {
      String channelPath = laneObj.get(PatternClipLane.KEY_CHANNEL).getAsString();
      if (channelPath.startsWith(LXPath.ROOT_PREFIX)) {
        LXComponent channelObj = LXPath.getComponent(this.lx, channelPath);
        if (channelObj instanceof LXChannel channel) {
          PatternClipLane patternLane = findPatternLane(channel);
          if (patternLane != null) {
            patternLane.load(this.lx, laneObj);
            return patternLane;
          }
        }
      }
      LX.error("Could not find pattern lane for channel " + channelPath);
    } else if (laneObj.has(PatternClipLane.KEY_RACK)) {
      // TODO(mcslee): implement loading of rack pattern lanes in composition
    } else {
      LX.error("Cannot load pattern lane, no channel or rack specified");
    }
    return null;
  }

  private MidiNoteClipLane findMidiLane(LXAbstractChannel bus) {
    for (LXClipLane<?> lane : this.lanes) {
      if (lane instanceof MidiNoteClipLane midiLane && midiLane.channel == bus) {
        return midiLane;
      }
    }
    return null;
  }

  private MidiNoteClipLane loadMidiNoteLane(JsonObject laneObj) {
    if (laneObj.has(MidiNoteClipLane.KEY_BUS)) {
      String busPath = laneObj.get(MidiNoteClipLane.KEY_BUS).getAsString();
      if (busPath.startsWith(LXPath.ROOT_PREFIX)) {
        LXComponent busObj = LXPath.getComponent(this.lx, busPath);
        if (busObj instanceof LXAbstractChannel bus) {
          MidiNoteClipLane midiLane = findMidiLane(bus);
          if (midiLane != null) {
            midiLane.load(this.lx, laneObj);
            return midiLane;
          }
        }
      }
    }
    return null;
  }

  @Override
  protected int validateMoveClipLaneIndex(LXClipLane<?> lane, int index) {
    if (lane.isCompositionMajorLane()) {
      if (index < lane.getIndex()) {
        while (index > 0 && !this.lanes.get(index).isCompositionMajorLane()) {
          --index;
        }
        return index;
      } else if (index > lane.getIndex()) {
        while (index < this.lanes.size() - 1 && !this.lanes.get(index+1).isCompositionMajorLane()) {
          ++index;
        }
        return index;
      }
    } else {
      // Minor lanes can only move within their parameter holder
      LXBus bus = lane.getBus();
      if (bus == null) {
        return -1;
      }
      BusClipLane busLane = this.busLanes.get(bus);
      int minIndex = busLane.getIndex() + 1;
      int maxIndex = minIndex;
      while (++maxIndex < this.lanes.size()) {
        if (this.lanes.get(maxIndex).isCompositionMajorLane()) {
          break;
        }
      }
      return LXUtils.constrain(index, minIndex, maxIndex-1);
    }
    return -1;
  }

  /**
   * Create a new lane from a serialized lane
   */
  private BusClipLane loadBusLane(JsonObject laneObj, int index) {
    if (!laneObj.has(BusClipLane.KEY_BUS_ID)) {
      LX.error("Cannot load bus lane. Unable to find serialized bus componentId.");
      return null;
    }

    int busId;
    try {
      busId = laneObj.get(BusClipLane.KEY_BUS_ID).getAsInt();
    } catch (NumberFormatException ex) {
      LX.error("Cannot restore bus lane. ComponentId for bus was corrupt or missing: " + laneObj.get(BusClipLane.KEY_BUS_ID).toString());
      return null;
    }

    // Get the bus
    final LXComponent component = this.lx.getProjectComponent(busId);
    if (component instanceof LXBus bus) {
      BusClipLane lane = findBusLane(bus, index);
      lane.load(this.lx, laneObj);
      return lane;
    } else {
      LX.error("Unable to find bus (componentId=" + busId + ") on composition: " + getLabel());
    }
    return null;
  }

  private void unregisterBus(LXAbstractChannel channel) {
    if (this.busLanes.containsKey(channel)) {
      removeClipLane(this.busLanes.get(channel));
    } else {
      throw new IllegalStateException("Unable to remove lane, does not exist for channel: " + channel.getLabel());
    }

    // Remove any lanes associated with this bus
    // TODO(mcslee): this should be handled more elegantly using a system like
    // LXClip.registerComponent/unregsiterComponent
    List<LXClipLane<?>> toRemove = new ArrayList<>();
    for (LXClipLane<?> lane : this.lanes) {
      if (lane.getBus() == channel) {
        toRemove.add(lane);
      }
    }
    for (LXClipLane<?> lane : toRemove) {
      removeClipLane(lane);
    }
  }

  // Audio Lanes

  /**
   * Add a new audio lane to the composition
   *
   * @return The newly created audio lane
   */
  public AudioClipLane addAudioLane(File file) {
    final AudioClipLane lane = addAudioLane((JsonObject) null, 0);

    // Update composition length to at least audio length
    final AudioClipEvent event = lane.addEvent(file);
    onCompositionEventImport(event);

    return lane;
  }

  public AudioClipLane addAudioLane(JsonObject laneObj) {
    return addAudioLane(laneObj, 0);
  }

  private AudioClipLane addAudioLane(JsonObject laneObj, int index) {
    final AudioClipLane lane = new AudioClipLane(this);
    if (laneObj != null) {
      lane.load(this.lx, laneObj);
    }
    this.audioLanes.add(lane);
    if (index < 0) {
      this.mutableLanes.add(lane);
    } else {
      this.mutableLanes.add(index, lane);
    }
    onClipLaneAdded(lane);
    return lane;
  }

  // Notes Lanes

  /**
   * Add a new notes lane to the composition
   *
   * @return The newly created notes lane
   */
  public TextNoteClipLane addTextNoteLane() {
    return addTextNoteLane(null);
  }

  /**
   * Add a notes lane from a serialized lane
   *
   * @param laneObj The serialized lane
   * @return The newly created notes lane
   */
  public TextNoteClipLane addTextNoteLane(JsonObject laneObj) {
    return addTextNoteLane(laneObj, -1);
  }

  /**
   * Add a notes lane from a serialized lane
   *
   * @param laneObj The serialized lane
   * @param index The position at which to add the lane
   * @return The newly created notes lane
   */
  public TextNoteClipLane addTextNoteLane(JsonObject laneObj, int index) {
    final TextNoteClipLane lane = new TextNoteClipLane(this);
    if (laneObj != null) {
      lane.load(this.lx, laneObj);
    }
    if (index < 0) {
      this.mutableLanes.add(lane);
    } else {
      this.mutableLanes.add(index, lane);
    }
    onClipLaneAdded(lane);
    return lane;
  }

  // Locators

  public void goPreviousLocator() {
    Operator CursorOp = CursorOp();
    Cursor from = isRunning() ? this.cursor : this.insertMarker.cursor;
    Cursor prev = Cursor.ZERO;
    for (Locator locator : this.locators) {
      if (CursorOp.isAfterOrEqual(locator.position.cursor, from)) {
        break;
      }
      prev = locator.position.cursor;
    }
    if (isRunning()) {
      launchAutomationFrom(prev);
    } else {
      setInsertMarker(prev);
    }
  }

  public void goNextLocator() {
    Operator CursorOp = CursorOp();
    Cursor from = isRunning() ? this.cursor : this.insertMarker.cursor;
    Cursor next = this.length.cursor;
    for (Locator locator : this.locators) {
      if (CursorOp.isAfter(locator.position.cursor, from)) {
        next = locator.position.cursor;
        break;
      }
    }
    if (isRunning()) {
      launchAutomationFrom(next);
    } else {
      setInsertMarker(next);
    }
  }

  /**
   * Add a locator at the given cursor position
   *
   * @param cursor Position for the new locator
   * @return The newly created locator
   */
  public Locator addLocator(Cursor cursor) {
    Locator locator = new Locator(this, cursor);
    this.mutableLocators.add(locator);
    sortLocators();
    return locator;
  }

  /**
   * Add a locator from a serialized object
   */
  public Locator addLocator(LX lx, JsonObject locatorObj) {
    Locator locator = new Locator(this, Cursor.ZERO);
    locator.load(lx, locatorObj);
    this.mutableLocators.add(locator);
    sortLocators();
    return locator;
  }

  /**
   * Remove a locator
   *
   * @param locator The locator to remove
   */
  public void removeLocator(Locator locator) {
    if (this.mutableLocators.remove(locator)) {
      LX.dispose(locator);
    } else {
      throw new IllegalStateException("Cannot remove locator not present on LXComposition: " + locator);
    }
  }

  /**
   * Move a locator to a new cursor position
   *
   * @param locator The locator to move
   * @param cursor New position
   */
  public void moveLocator(Locator locator, Cursor cursor) {
    locator.setCursor(cursor);
    sortLocators();
  }

  private void sortLocators() {
    // NOTE: this does not notify any listeners!!
    this.mutableLocators.sort((a, b) -> CursorOp().compare(a.position.cursor, b.position.cursor));
    int i = 0;
    for (Locator locator : this.locators) {
      locator.setIndex(i++);
    }
  }

  private void clearLocators() {
    List<Locator> toClear = new ArrayList<>(this.mutableLocators);
    for (Locator locator : toClear) {
      removeLocator(locator);
    }
  }

  // Lane removal

  @Override
  protected void onClipLaneRemoved(LXClipLane<?> lane) {
    switch (lane) {
      case BusClipLane busLane -> this.busLanes.remove(busLane.bus);
      case AudioClipLane audioLane -> this.audioLanes.remove(audioLane);
      default -> {}
    }
    super.onClipLaneRemoved(lane);
  }

  // Disposal

  @Override
  public void dispose() {
    clearLocators();
    this.audioPlayer.dispose();
    this.lx.engine.mixer.removeListener(this.mixerListener);
    super.dispose();
  }

  // Serialization

  private static final String KEY_LOCATORS = "locators";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_LOCATORS, LXSerializable.Utils.toArray(lx, this.locators));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    clearLanes();
    clearLocators();
    super.load(lx, obj);

    // LXComposition always has automation playback enabled!
    this.automationEnabled.setValue(true);

    initializeBusLanes();
    if (obj.has(KEY_LOCATORS)) {
      JsonArray locatorsArr = obj.get(KEY_LOCATORS).getAsJsonArray();
      for (JsonElement locatorElement : locatorsArr) {
        addLocator(lx, locatorElement.getAsJsonObject());
      }
    }
  }

  @Override
  public LXClipLane<?> loadClipLane(LX lx, JsonObject laneObj, int index) {
    return switch (getClipLaneType(laneObj)) {
      case LXClipLane.VALUE_LANE_TYPE_BUS -> loadBusLane(laneObj, index);
      case LXClipLane.VALUE_LANE_TYPE_AUDIO -> addAudioLane(laneObj, index);
      case LXClipLane.VALUE_LANE_TYPE_NOTES -> addTextNoteLane(laneObj, index);
      case LXClipLane.VALUE_LANE_TYPE_PATTERN -> loadPatternLane(laneObj, index);
      case LXClipLane.VALUE_LANE_TYPE_MIDI_NOTE -> loadMidiNoteLane(laneObj);
      default -> super.loadClipLane(lx, laneObj, index);
    };
  }

}
