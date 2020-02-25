package heronarts.lx;

import com.google.gson.JsonObject;

public class LXSnapshotActivePattern extends LXComponent {

  public LXChannel channel;
  public LXPattern pattern;

  public LXSnapshotActivePattern(LX lx, LXChannel channel, LXPattern pattern) {
    super(lx);
    this.channel = channel;
    this.pattern = pattern;
  }

  public LXSnapshotActivePattern(LX lx, JsonObject obj) {
    super(lx);
    this.channel = getChannel(lx, obj.get(KEY_CHANNEL).getAsInt());
    this.pattern = getPattern(this.channel, obj.get(KEY_PATTERN).getAsInt());
  }

  public static LXChannel getChannel(LX lx, int channelIndex) {
    return (LXChannel)lx.engine.getChannel(channelIndex);
  }

  public static LXPattern getPattern(LXChannel channel, int patternIndex) {
    return channel.getPattern(patternIndex);
  }

  protected static final String KEY_CHANNEL = "channel";
  protected static final String KEY_PATTERN = "pattern";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_CHANNEL, this.channel.getIndex());
    obj.addProperty(KEY_PATTERN, this.pattern.getIndex());
  }
}
