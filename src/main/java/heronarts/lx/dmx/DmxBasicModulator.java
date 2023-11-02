package heronarts.lx.dmx;

import heronarts.lx.modulator.LXModulator;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.utils.LXUtils;

/**
 * Abstract DMX Modulator specifying a universe, channel, and number of bytes.
 * If universe wrapping is not allowed maximum channel value will be
 * restricted to fit all bytes within one universe.
 */
abstract public class DmxBasicModulator extends LXModulator {

  public final DiscreteParameter universe =
    new DiscreteParameter("Universe", 0, LXDmxEngine.MAX_UNIVERSE)
    .setDescription("DMX universe");

  public final DiscreteParameter channel =
    new DiscreteParameter("Channel", 0, LXDmxEngine.MAX_CHANNEL)
    .setDescription("DMX channel");

  private boolean wrappable;
  private int bytes;

  static private int constrainBytes(int bytes) {
    return LXUtils.max(1, bytes);
  }

  public DmxBasicModulator(String label) {
    this(label, 1);
  }

  public DmxBasicModulator(String label, int bytes) {
    this(label, bytes, false);
  }

  public DmxBasicModulator(String label, int bytes, boolean wrappable) {
    super(label);
    this.wrappable = wrappable;
    this.bytes = constrainBytes(bytes);
    if (bytes != 1) {
      updateMaxChannel();
    }
    addParameter("universe", this.universe);
    addParameter("channel", this.channel);
  }

  protected DmxBasicModulator setBytes(int bytes) {
    this.bytes = bytes;
    updateMaxChannel();
    return this;
  }

  protected DmxBasicModulator setDmxWrappable(boolean wrappable) {
    if (this.wrappable != wrappable) {
      this.wrappable = wrappable;
      updateMaxChannel();
    }
    return this;
  }

  private void updateMaxChannel() {
    this.channel.setRange(0, LXUtils.max(1, LXDmxEngine.MAX_CHANNEL - (this.wrappable ? 0 : (this.bytes - 1))));
  }

}
