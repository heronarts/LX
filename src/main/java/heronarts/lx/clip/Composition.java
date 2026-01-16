package heronarts.lx.clip;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.LXSerializable;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXMixerEngine;
import heronarts.lx.utils.ObservableList;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Composition extends LXClip {

  public interface Listener extends LXClip.Listener {
    public default void busLaneAdded(Composition composition, BusLane lane) {}
    public default void busLaneRemoved(Composition composition, BusLane lane) {}
    public default void audioLaneAdded(Composition composition, AudioLane lane) {}
    public default void audioLaneRemoved(Composition composition, AudioLane lane) {}
    public default void notesLaneAdded(Composition composition, NotesLane lane) {}
    public default void notesLaneRemoved(Composition composition, NotesLane lane) {}
  }

  private final List<Listener> listeners = new ArrayList<>();

  private final Map<LXAbstractChannel, BusLane> busLanes = new HashMap<>();
  private final List<AudioLane> audioLanes = new ArrayList<>();
  private final List<NotesLane> notesLanes = new ArrayList<>();

  private final ObservableList<Locator> mutableLocators = new ObservableList<>();
  public final ObservableList<Locator> locators = this.mutableLocators.asUnmodifiableList();

  private final AudioPlayer audioPlayback = new AudioPlayer(this);

  public Composition(LX lx) {
    super(lx, lx.engine.composition, lx.engine.composition, 0);

    // Maintain one lane per mixer channel
    lx.engine.mixer.addListener(this.mixerListener);
    createBusLanes();
  }

  @Override
  public String getPath() {
    return "composition";
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
    this.lx.engine.composition.arm.setValue(false);
    stopAudioPlayback();
  }

  // Playback

  @Override
  protected void run(double deltaMs) {
    super.run(deltaMs);
    for (BusLane busLane : this.busLanes.values()) {
      busLane.run(deltaMs);
    }
    if (!this.audioLanes.isEmpty()) {
      this.audioPlayback.ensureRunning();
    }
  }

  @Override
  protected void onStopPlayback() {
    stopAudioPlayback();
  }

  private void stopAudioPlayback() {
    if (!this.audioLanes.isEmpty()) {
      this.audioPlayback.stop();
      for (AudioLane lane : this.audioLanes) {
        lane.stopPlayback();
      }
    }
  }

  /**
   * Get the list of audio lanes for playback
   */
  List<AudioLane> getAudioLanes() {
    return this.audioLanes;
  }

  /**
   * Notify the audio playback system that a position jump has occurred
   */
  void notifyAudioJump() {
    // TODO: this is getting called once per audio lane. Would be cleaner to initiate from here, rather than from lanes.
    this.audioPlayback.notifyJump();
  }

  // Bus Lanes

  private void createBusLanes() {
    for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
      if (!this.busLanes.containsKey(channel)) {
        addBusLane(channel);
      }
    }
  }

  /**
   * Re-sort bus lanes to match current mixer channel order.
   */
  private void reorderBusLanes() {
    int i = this.audioLanes.size();
    for (LXAbstractChannel channel : this.lx.engine.mixer.channels) {
      BusLane lane = this.busLanes.get(channel);
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
  private BusLane addBusLane(LXAbstractChannel channel) {
    // TODO: use command engine to add lane
    BusLane lane = new BusLane(this, channel);
    this.busLanes.put(channel, lane);
    int index = this.audioLanes.size() + channel.getIndex();
    this.mutableLanes.add(index, lane);
    notifyBusLaneAdded(lane);
    return lane;
  }

  /**
   * Create a new lane from a serialized lane
   */
  private BusLane addBusLane(LX lx, JsonObject laneObj) {
    if (laneObj.has(BusLane.KEY_BUS_ID)) {
      int busId;
      try {
        busId = laneObj.get(BusLane.KEY_BUS_ID).getAsInt();
      } catch (NumberFormatException ex) {
        LX.error("Cannot restore bus lane. ComponentId for bus was corrupt or missing: " + laneObj.get(BusLane.KEY_BUS_ID).toString());
        return null;
      }
      LXComponent component = lx.getComponent(busId);
      if (component instanceof LXAbstractChannel busComponent) {
        BusLane lane = addBusLane(busComponent);
        lane.load(lx, laneObj);
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

  private void removeBusLane(BusLane lane) {
    // TODO: use command engine to remove lane
    LXAbstractChannel channel = (LXAbstractChannel)lane.bus;
    if (this.busLanes.remove(channel) != null) {
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
  public AudioLane addAudioLane(File file) {
    AudioLane lane = new AudioLane(this);
    lane.addEvent(file);
    int insertIndex = this.audioLanes.size();
    this.audioLanes.add(lane);
    this.mutableLanes.add(insertIndex, lane);
    notifyAudioLaneAdded(lane);
    return lane;
  }

  /**
   * Add an audio lane from a serialized lane
   *
   * @return The newly created audio lane
   */
  private AudioLane addAudioLane(LX lx, JsonObject laneObj) {
    AudioLane lane = new AudioLane(this);
    int insertIndex = this.audioLanes.size();
    this.audioLanes.add(lane);
    this.mutableLanes.add(insertIndex, lane);
    lane.load(lx, laneObj);
    notifyAudioLaneAdded(lane);
    return lane;
  }

  /**
   * Remove an audio lane from the composition
   *
   * @param lane The lane to remove
   */
  public void removeAudioLane(AudioLane lane) {
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
  public NotesLane addNotesLane() {
    NotesLane lane = new NotesLane(this);
    this.notesLanes.add(lane);
    this.mutableLanes.add(lane);
    notifyNotesLaneAdded(lane);
    return lane;
  }

  /**
   * Add a notes lane from a serialized lane
   *
   * @return The newly created notes lane
   */
  private NotesLane addNotesLane(LX lx, JsonObject laneObj) {
    NotesLane lane = new NotesLane(this);
    this.notesLanes.add(lane);
    this.mutableLanes.add(lane);
    lane.load(lx, laneObj);
    notifyNotesLaneAdded(lane);
    return lane;
  }

  /**
   * Remove a notes lane from the composition
   *
   * @param lane The lane to remove
   */
  public void removeNotesLane(NotesLane lane) {
    if (this.notesLanes.remove(lane)) {
      this.mutableLanes.remove(lane);
      notifyNotesLaneRemoved(lane);
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
  Composition _removeLane(LXClipLane<?> lane) {
    switch (lane) {
      case BusLane busLane -> removeBusLane(busLane);
      case AudioLane audioLane -> removeAudioLane(audioLane);
      case NotesLane notesLane -> removeNotesLane(notesLane);
      case null, default -> super._removeLane(lane);
    }
    return this;
  }

  /**
   * Similar to LXClip.clearLanes(), notifies listeners of lane removal
   */
  private void clearLanes() {
    List<BusLane> busLanesToClear = new ArrayList<>(this.busLanes.values());
    for (BusLane lane : busLanesToClear) {
      removeBusLane(lane);
    }
    List<AudioLane> audioLanesToClear = new ArrayList<>(this.audioLanes);
    for (AudioLane lane : audioLanesToClear) {
      removeAudioLane(lane);
    }
    List<NotesLane> notesLanesToClear = new ArrayList<>(this.notesLanes);
    for (NotesLane lane : notesLanesToClear) {
      removeNotesLane(lane);
    }
  }

  // Listeners

  public Composition addListener(Listener listener) {
    super.addListener(listener);
    this.listeners.add(listener);
    return this;
  }

  public Composition removeListener(Listener listener) {
    super.removeListener(listener);
    this.listeners.remove(listener);
    return this;
  }

  private void notifyBusLaneAdded(BusLane lane) {
    this.listeners.forEach(l -> l.busLaneAdded(this, lane));
  }

  private void notifyBusLaneRemoved(BusLane lane) {
    this.listeners.forEach(l -> l.busLaneRemoved(this, lane));
  }

  private void notifyAudioLaneAdded(AudioLane lane) {
    this.listeners.forEach(l -> l.audioLaneAdded(this, lane));
  }

  private void notifyAudioLaneRemoved(AudioLane lane) {
    this.listeners.forEach(l -> l.audioLaneRemoved(this, lane));
  }

  private void notifyNotesLaneAdded(NotesLane lane) {
    this.listeners.forEach(l -> l.notesLaneAdded(this, lane));
  }

  private void notifyNotesLaneRemoved(NotesLane lane) {
    this.listeners.forEach(l -> l.notesLaneRemoved(this, lane));
  }

  // Disposal

  @Override
  public void dispose() {
    this.audioPlayback.dispose();
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
    switch (getLaneType(laneObj)) {
      case LXClipLane.VALUE_LANE_TYPE_BUS -> {
        return addBusLane(lx, laneObj);
      }
      case LXClipLane.VALUE_LANE_TYPE_AUDIO -> {
        return addAudioLane(lx, laneObj);
      }
      case LXClipLane.VALUE_LANE_TYPE_NOTES -> {
        return addNotesLane(lx, laneObj);
      }
      default -> {
        return super.loadLane(lx, laneObj, index);
      }
    }
  }

}
