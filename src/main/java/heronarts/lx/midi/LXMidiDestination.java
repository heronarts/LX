package heronarts.lx.midi;

/**
 * Interface that provides an abstract notion of a MIDI destination. This would most
 * typically be a LXMidiOutput to a device, but it can also be something virtual
 * like NONE
 */
public interface LXMidiDestination extends LXMidiTerminal {

  public final static LXMidiDestination NONE = new LXMidiDestination() {
    @Override
    public String toString() {
      return "None";
    }
  };

}
