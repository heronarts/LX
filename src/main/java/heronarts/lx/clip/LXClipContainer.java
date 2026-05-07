package heronarts.lx.clip;

import heronarts.lx.LXComponent;
import heronarts.lx.effect.LXEffect;
import heronarts.lx.mixer.LXBus;
import heronarts.lx.parameter.BooleanParameter;

import java.util.List;

/**
 * Provides clip context such as an Arm parameter.
 * Used on Bus and LXCompositionEngine
 */
public interface LXClipContainer {
  public BooleanParameter getArmParameter();

  public List<LXClip> getClips();
  public void onClipStart(LXClip clip);
  public void onClipStop(LXClip clip);

  public List<LXEffect> getEffects();
  public void addEffectsListener(LXBus.Listener listener);
  public void removeEffectsListener(LXBus.Listener listener);

  public LXComponent getComponent();
}
