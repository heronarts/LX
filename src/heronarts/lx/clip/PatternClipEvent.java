package heronarts.lx.clip;

import com.google.gson.JsonObject;

import heronarts.lx.LX;
import heronarts.lx.LXChannel;
import heronarts.lx.LXPattern;

public class PatternClipEvent extends LXClipEvent {

  public final LXPattern pattern;
  public final LXChannel channel;

  PatternClipEvent(LXClipLane lane, LXChannel channel, LXPattern pattern) {
    super(lane, pattern);
    this.pattern = pattern;
    this.channel = channel;
  }

  @Override
  public void execute() {
    this.channel.goPattern(this.pattern);
  }

  protected static final String KEY_PATTERN_INDEX = "patternIndex";

  @Override
  public void save(LX lx, JsonObject obj) {
    super.save(lx, obj);
    obj.addProperty(KEY_PATTERN_INDEX, this.pattern.getIndex());
  }

}
