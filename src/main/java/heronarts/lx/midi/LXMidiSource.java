/**
 * Copyright 2024- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.midi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import heronarts.lx.parameter.AggregateParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.parameter.ObjectParameter;
import heronarts.lx.parameter.StringParameter;

/**
 * Interface that provides an abstract notion of a MIDI source. This would most
 * typically be a LXMidiInput from a device, but it can also be something virtual
 * like the computer keyboard, OSC input, or the abstract notion of "any MIDI inputs"
 */
public interface LXMidiSource {

  public default String getName() {
    return toString();
  }

  public default boolean matches(LXMidiSource source) {
    return (this == source);
  }

  public default LXMidiInput getInput() {
    return (this instanceof LXMidiInput) ? (LXMidiInput) this : null;
  }

  public final static LXMidiSource UNKNOWN = new LXMidiSource() {
    @Override
    public String toString() {
      return "Unknown";
    }
  };

  public final static LXMidiSource NONE = new LXMidiSource() {
    @Override
    public String toString() {
      return "None";
    }

    public boolean matches(LXMidiSource source) {
      return false;
    }
  };

  public final static LXMidiSource ALL_INS = new LXMidiSource() {
    @Override
    public String toString() {
      return "All Ins";
    }

    public boolean matches(LXMidiSource source) {
      return true;
    }
  };

  public final static LXMidiSource COMPUTER_KEYBOARD = new LXMidiSource() {
    @Override
    public String toString() {
      return "Computer Keyboard";
    }
  };

  public final static LXMidiSource OSC = new LXMidiSource() {
    @Override
    public String toString() {
      return "OSC In";
    }
  };

  static class _Sources {

    static LXMidiSource[] filter = FilterSelector.DEFAULT_FILTERS.toArray(new LXMidiSource[0]);
    static LXMidiSource[] device = new LXMidiSource[] { LXMidiSource.NONE };

    static final List<Selector> selectors = new ArrayList<Selector>();

    protected static void update(List<LXMidiInput> inputs) {
      // Device selectors use all available devices
      device = inputs.isEmpty() ? new LXMidiSource[] { LXMidiSource.NONE } : inputs.toArray(new LXMidiSource[0]);

      // Filter selectors are for channel-enabled devices only
      final ArrayList<LXMidiSource> filterList = new ArrayList<LXMidiSource>(FilterSelector.DEFAULT_FILTERS.size() + inputs.size());
      filterList.addAll(LXMidiSource.FilterSelector.DEFAULT_FILTERS);
      for (LXMidiInput input : inputs) {
        if (input.channelEnabled.isOn()) {
          filterList.add(input);
        }
      }
      filter = filterList.toArray(new LXMidiSource[0]);

      // Update options in all the selectors
      for (LXMidiSource.Selector selector : selectors) {
        selector.update();
      }
    }
  }

  public static abstract class Selector extends AggregateParameter {

    public final StringParameter name =
      new StringParameter("Name", null)
      .setDescription("Name of the MIDI input");

    public final DiscreteParameter index =
      new DiscreteParameter("Index", 0, 128)
      .setDescription("Index of the MIDI input, if there are multiple by the same name");

    public final ObjectParameter<LXMidiSource> source;

    private final LXParameterListener sourceListener;

    public boolean missingDevice = false;

    protected Selector(String label) {
      super(label);
      setDescription("Receive MIDI messages from the given source");

      // NOTE: order important here, name will be restored *after* index when
      // re-loading, and this is handled in onSubparameterUpdate()
      addSubparameter("index", this.index);
      addSubparameter("name", this.name);

      this.source =
        new ObjectParameter<LXMidiSource>("Source", getSourceObjects())
        .setDescription("MIDI Source");
      this.source.addListener(this.sourceListener = this::onSourceChanged);

      _Sources.selectors.add(this);
    }

    protected abstract LXMidiSource[] getSourceObjects();

    /**
     * Whether the saved selector setting matches the given input
     *
     * @param source MIDI source to test for match
     * @return True
     */
    public boolean matches(LXMidiSource source) {
      if (this.missingDevice) {
        return false;
      }
      return this.source.getObject().matches(source);
    }

    private boolean flagOptionsUpdate = false;

    void update() {
      if (this.flagOptionsUpdate) {
        throw new IllegalStateException("Re-entrancy disallowed in LXMidiSource.Selector.updateOptions()");
      }
      this.flagOptionsUpdate = true;
      final LXMidiSource previousSource = this.source.getObject();

      final LXMidiSource[] availableSources = getSourceObjects();
      this.source.setObjects(availableSources);

      if (this.missingDevice) {
        findMissingDevice();
      } else {
        boolean missing = true;
        for (LXMidiSource source : availableSources) {
          if (source == previousSource) {
            missing = false;
            this.source.setValue(source);
            updateNameAndIndex(source);
            break;
          }
        }
        // If The option previously set is no longer found! If it's non-null,
        // leave name/index as they were and flag that this is now a
        // non-existing MIDI device (note that if it was null, it would
        // have been found above, null is always "All Inputs" in the list
        // and wouldn't go away
        this.missingDevice = missing;
        bang();
      }
      this.flagOptionsUpdate = false;
    }

    private boolean flagNameUpdate = false;

    private void updateNameAndIndex(LXMidiSource source) {
      if (this.flagNameUpdate) {
        throw new IllegalStateException("Re-entrancy disallowed in LXMidiSource.Selector.updateNameAndIndex()");
      }
      this.flagNameUpdate = true;
      if (source.getInput() == null) {
        // It is not a LXMidiInput source, it's a special one like All Ins
        // or Computer Keyboard
        this.index.setValue(0);
        this.name.setValue(null);
      } else {
        final String name = source.getName();
        int index = 0;
        for (LXMidiSource search : this.source.getObjects()) {
          if (source == search) {
            break;
          } else if (name.equals(search.getName())) {
            // There's another source by the same name, but not the selected one
            ++index;
          }
        }
        this.index.setValue(index);
        this.name.setValue(name);
      }
      this.flagNameUpdate = false;
    }

    private void findMissingDevice() {
      // Can be invoked either from a file reload, or from the available
      // options changing and we now want to find something set before
      final String name = this.name.getString();
      if (name == null) {
        this.missingDevice = false;
        return;
      }
      final int index = this.index.getValuei();
      int instance = 0;
      for (LXMidiSource source : this.source.getObjects()) {
        if (name.equals(source.getName())) {
          if (instance == index) {
            this.missingDevice = false;
            this.source.setValue(source);
            bang();
            return;
          } else {
            ++instance;
          }
        }
      }
      this.missingDevice = true;
      bang();
    }

    private void onSourceChanged(LXParameter p) {
      if (this.flagOptionsUpdate) {
        // Don't process changes to the device parameter when it's
        // part of the routine of updating available options
        return;
      }
      this.missingDevice = false;
      updateNameAndIndex(this.source.getObject());
      bang();
    }

    @Override
    protected void updateSubparameters(double value) {
      // NO-OP, we don't have a meaningful internal double value
    }

    @Override
    protected void onSubparameterUpdate(LXParameter p) {
      if ((p == this.name) && !this.flagNameUpdate) {
        findMissingDevice();
      }
    }

    @Override
    public void dispose() {
      this.source.removeListener(this.sourceListener);
      _Sources.selectors.remove(this);
      super.dispose();
    }
  }

  /**
   * A FilterSelector contains the hardware inputs plus
   * virtual mappings like All Inputs
   */
  public static class FilterSelector extends Selector {

    public static final List<LXMidiSource> DEFAULT_FILTERS = Arrays.asList(new LXMidiSource[] {
      LXMidiSource.ALL_INS,
      LXMidiSource.COMPUTER_KEYBOARD,
      LXMidiSource.OSC
    });

    public FilterSelector(String label) {
      super(label);
    }

    @Override
    protected LXMidiSource[] getSourceObjects() {
      return _Sources.filter;
    }

  }

  public static class DeviceSelector extends Selector {

    public DeviceSelector(String label) {
      super(label);
    }

    @Override
    protected LXMidiSource[] getSourceObjects() {
      return _Sources.device;
    }

  }

}
