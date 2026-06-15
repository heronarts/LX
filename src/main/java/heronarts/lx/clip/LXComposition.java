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
import heronarts.lx.LXSerializable;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.mixer.LXMasterBus;
import heronarts.lx.mixer.LXMixerEngine;
import heronarts.lx.utils.ObservableList;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class LXComposition extends LXClip {

  public interface Listener extends LXClip.Listener {
    public default void busLaneAdded(LXComposition composition, BusClipLane lane) {}
    public default void busLaneRemoved(LXComposition composition, BusClipLane lane) {}
    public default void audioLaneAdded(LXComposition composition, AudioClipLane lane) {}
    public default void audioLaneRemoved(LXComposition composition, AudioClipLane lane) {}
    public default void notesLaneAdded(LXComposition composition, TextNoteClipLane lane) {}
    public default void notesLaneRemoved(LXComposition composition, TextNoteClipLane lane) {}
  }

  private final List<Listener> listeners = new ArrayList<>();

  private final Map<LXBus, BusClipLane> busLanes = new HashMap<>();
  private final List<AudioClipLane> audioLanes = new CopyOnWriteArrayList<>();
  private final List<TextNoteClipLane> notesLanes = new ArrayList<>();

  private final ObservableList<Locator> mutableLocators = new ObservableList.CopyOnWrite<>();
  public final ObservableList<Locator> locators = this.mutableLocators.asUnmodifiableList();

  private final AudioPlayer audioPlayer;

  public LXComposition(LX lx, LXCompositionEngine composition) {
    super(lx, composition, composition, 0);

    this.audioPlayer = new AudioPlayer(lx);

    // Maintain one lane per mixer channel
    lx.engine.mixer.addListener(this.mixerListener);
    createBusLanes();
  }

  @Override
  public String getPath() {
    return "composition";
  }

  @Override
  public CursorParameter getLaunchPosition() {
    return this.insertMarker;
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
    this.lx.engine.composition.arm.setValue(false);
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

  private void createBusLanes() {
    for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
      _createBusLane(channel);

    }
    _createBusLane(this.lx.engine.mixer.masterBus);
  }

  private void _createBusLane(LXBus bus) {
    if (!this.busLanes.containsKey(bus)) {
      addBusLane(bus);
    }
  }

  /**
   * Re-sort bus lanes to match current mixer channel order.
   */
  private void reorderBusLanes() {
    int i = this.audioLanes.size();
    for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
      BusClipLane lane = this.busLanes.get(channel);
      if (lane != null) {
        int currentIndex = this.mutableLanes.indexOf(lane);
        if (currentIndex != i) {
          // Move and notify listeners
          moveClipLane(lane, i);
        }
        ++i;
      }
    }

    reindexBusLanes();
  }

  private void reindexBusLanes() {
    // TODO: implement lane indexing so notes lanes can be inserted anywhere
    /* int i = this.audioLanes.size();
    for (LXClipLane<?> lane : this.mutableLanes) {
      if (lane instanceof BusLane busLane) {
        busLane.setIndex(i++);
      }
    }*/
  }

  private final LXMixerEngine.Listener mixerListener = new LXMixerEngine.Listener() {
    @Override
    public void channelAdded(LXMixerEngine mixer, LXAbstractChannel channel) {
      addBusLane(channel);
    }

    @Override
    public void channelRemoved(LXMixerEngine mixer, LXAbstractChannel channel) {
      removeBusLane(channel);
    }

    @Override
    public void channelMoved(LXMixerEngine mixer, LXAbstractChannel channel) {
      reorderBusLanes();
    }
  };

  /**
   * Create a new lane based on a mixer channel, inserting it at the
   * correct position to maintain audio-before-bus ordering.
   */
  private BusClipLane addBusLane(LXBus bus) {
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
    } else {
      int index = (bus instanceof LXMasterBus) ? -1 : this.audioLanes.size() + bus.getIndex();
      this.mutableLanes.add(index, lane);
    }
    notifyBusLaneAdded(lane);
    return lane;
  }

  /**
   * Create a new lane from a serialized lane
   */
  private BusClipLane addBusLane(JsonObject laneObj) {
    if (laneObj.has(BusClipLane.KEY_BUS_ID)) {
      int busId;
      try {
        busId = laneObj.get(BusClipLane.KEY_BUS_ID).getAsInt();
      } catch (NumberFormatException ex) {
        LX.error("Cannot restore bus lane. ComponentId for bus was corrupt or missing: " + laneObj.get(BusClipLane.KEY_BUS_ID).toString());
        return null;
      }
      final LXComponent component = this.lx.getProjectComponent(busId);
      if (component instanceof LXBus bus) {
        BusClipLane lane = addBusLane(bus);
        lane.load(this.lx, laneObj);
        return lane;
      } else {
        LX.error("Unable to find bus (componentId=" + busId + ") on composition: " + getLabel());
      }
    } else {
      LX.error("Cannot load bus lane. Unable to find serialized bus componentId.");
    }
    return null;
  }

  private void removeBusLane(LXAbstractChannel channel) {
    if (this.busLanes.containsKey(channel)) {
      removeBusLane(this.busLanes.get(channel));
    } else {
      throw new IllegalStateException("Unable to remove lane, does not exist for channel: " + channel.getLabel());
    }
  }

  private void removeBusLane(BusClipLane lane) {
    if (this.busLanes.remove(lane.bus) != null) {
      this.mutableLanes.remove(lane);
      notifyBusLaneRemoved(lane);
      LX.dispose(lane);
    }
  }

  // Audio Lanes

  /**
   * Add a new audio lane to the composition
   *
   * @return The newly created audio lane
   */
  public AudioClipLane addAudioLane(File file) {
    final AudioClipLane lane = addAudioLane((JsonObject) null);

    // Update composition length to at least audio length
    final AudioClipEvent event = lane.addEvent(file);
    onCompositionEventImport(event);

    return lane;
  }

  /**
   * Add an audio lane from a serialized lane
   *
   * @return The newly created audio lane
   */
  public AudioClipLane addAudioLane(JsonObject laneObj) {
    final AudioClipLane lane = new AudioClipLane(this);
    if (laneObj != null) {
      lane.load(this.lx, laneObj);
    }
    int insertIndex = this.audioLanes.size();
    this.audioLanes.add(lane);
    this.mutableLanes.add(insertIndex, lane);
    notifyAudioLaneAdded(lane);
    return lane;
  }

  /**
   * Remove an audio lane from the composition
   *
   * @param lane The lane to remove
   */
  public void removeAudioLane(AudioClipLane lane) {
    if (this.audioLanes.remove(lane)) {
      this.mutableLanes.remove(lane);
      notifyAudioLaneRemoved(lane);
      LX.dispose(lane);
    }
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
    final TextNoteClipLane lane = new TextNoteClipLane(this);
    if (laneObj != null) {
      lane.load(this.lx, laneObj);
    }
    this.notesLanes.add(lane);
    this.mutableLanes.add(lane);
    notifyTextNoteLaneAdded(lane);
    return lane;
  }

  /**
   * Remove a notes lane from the composition
   *
   * @param lane The lane to remove
   */
  public void removeTextNoteLane(TextNoteClipLane lane) {
    if (this.notesLanes.remove(lane)) {
      this.mutableLanes.remove(lane);
      notifyTextNoteLaneRemoved(lane);
      LX.dispose(lane);
    }
  }

  // Locators

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
    }
  }

  /**
   * Move a locator to a new cursor position and re-sort
   *
   * @param locator The locator to move
   * @param cursor New position
   */
  public void setLocatorCursor(Locator locator, Cursor cursor) {
    locator.setCursor(cursor);
    sortLocators();
  }

  private void sortLocators() {
    // Workaround: sorting is not implemented in ObservableList
    List<Locator> toSort = new ArrayList<>(this.mutableLocators);
    toSort.sort((a, b) -> CursorOp().compare(a.cursor, b.cursor));
    this.mutableLocators.clear();
    this.mutableLocators.addAll(toSort);
  }

  private void clearLocators() {
    List<Locator> toClear = new ArrayList<>(this.mutableLocators);
    for (Locator locator : toClear) {
      removeLocator(locator);
    }
  }

  // Lane removal

  @Override
  LXComposition _removeLane(LXClipLane<?> lane) {
    switch (lane) {
      case BusClipLane busLane -> removeBusLane(busLane);
      case AudioClipLane audioLane -> removeAudioLane(audioLane);
      case TextNoteClipLane notesLane -> removeTextNoteLane(notesLane);
      case null, default -> super._removeLane(lane);
    }
    return this;
  }

  /**
   * Similar to LXClip.clearLanes(), notifies listeners of lane removal
   */
  private void clearLanes() {
    List<BusClipLane> busLanesToClear = new ArrayList<>(this.busLanes.values());
    for (BusClipLane lane : busLanesToClear) {
      removeBusLane(lane);
    }
    List<AudioClipLane> audioLanesToClear = new ArrayList<>(this.audioLanes);
    for (AudioClipLane lane : audioLanesToClear) {
      removeAudioLane(lane);
    }
    List<TextNoteClipLane> notesLanesToClear = new ArrayList<>(this.notesLanes);
    for (TextNoteClipLane lane : notesLanesToClear) {
      removeTextNoteLane(lane);
    }
  }

  // Listeners

  public LXComposition addListener(Listener listener) {
    super.addListener(listener);
    this.listeners.add(listener);
    return this;
  }

  public LXComposition removeListener(Listener listener) {
    super.removeListener(listener);
    this.listeners.remove(listener);
    return this;
  }

  private void notifyBusLaneAdded(BusClipLane lane) {
    this.listeners.forEach(l -> l.busLaneAdded(this, lane));
  }

  private void notifyBusLaneRemoved(BusClipLane lane) {
    this.listeners.forEach(l -> l.busLaneRemoved(this, lane));
  }

  private void notifyAudioLaneAdded(AudioClipLane lane) {
    this.listeners.forEach(l -> l.audioLaneAdded(this, lane));
  }

  private void notifyAudioLaneRemoved(AudioClipLane lane) {
    this.listeners.forEach(l -> l.audioLaneRemoved(this, lane));
  }

  private void notifyTextNoteLaneAdded(TextNoteClipLane lane) {
    this.listeners.forEach(l -> l.notesLaneAdded(this, lane));
  }

  private void notifyTextNoteLaneRemoved(TextNoteClipLane lane) {
    this.listeners.forEach(l -> l.notesLaneRemoved(this, lane));
  }

  // Disposal

  @Override
  public void dispose() {
    this.audioPlayer.dispose();
    this.lx.engine.mixer.removeListener(this.mixerListener);
    super.dispose();
  }

  // Serialization

  private static final String KEY_LOCATORS = "locators";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.add(KEY_LOCATORS, LXSerializable.Utils.toArray(lx, this.mutableLocators));
  }

  @Override
  public void load(LX lx, JsonObject obj) {
    clearLanes();
    clearLocators();
    super.load(lx, obj);

    // LXComposition always has automation playback enabled!
    this.automationEnabled.setValue(true);

    createBusLanes();
    reindexBusLanes();
    if (obj.has(KEY_LOCATORS)) {
      JsonArray locatorsArr = obj.get(KEY_LOCATORS).getAsJsonArray();
      for (JsonElement locatorElement : locatorsArr) {
        addLocator(lx, locatorElement.getAsJsonObject());
      }
    }
  }

  @Override
  public LXClipLane<?> loadLane(LX lx, JsonObject laneObj, int index) {
    return switch (getLaneType(laneObj)) {
      case LXClipLane.VALUE_LANE_TYPE_BUS -> addBusLane(laneObj);
      case LXClipLane.VALUE_LANE_TYPE_AUDIO -> addAudioLane(laneObj);
      case LXClipLane.VALUE_LANE_TYPE_NOTES -> addTextNoteLane(laneObj);
      default -> super.loadLane(lx, laneObj, index);
    };
  }

}
