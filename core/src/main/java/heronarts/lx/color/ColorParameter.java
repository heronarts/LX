package heronarts.lx.color;

import heronarts.lx.LXComponent;
import heronarts.lx.parameter.CompoundParameter;
import heronarts.lx.parameter.LXListenableParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.LXParameterListener;

public class ColorParameter extends LXListenableParameter implements LXParameterListener {

  public final CompoundParameter hue;
  public final CompoundParameter saturation;
  public final CompoundParameter brightness;

  private int color;
  private boolean internalValueUpdate = false;
  private boolean internalHsbUpdate = false;

  public ColorParameter(String label) {
    this(label, 0xff000000);
  }

  public ColorParameter(String label, int color) {
    super(label, Double.longBitsToDouble(color));
    double h = LXColor.h(color);
    this.hue = new CompoundParameter("Hue", Double.isNaN(h) ? 0 : h, 0, 359)
      .setDescription("Hue component of the color");
    this.saturation = new CompoundParameter("Saturation", LXColor.s(color), 0, 100)
      .setDescription("Saturation component of the color");
    this.brightness = new CompoundParameter("Brightness", LXColor.b(color), 0, 100)
      .setDescription("Brightness component of the color");
    this.hue.addListener(this);
    this.saturation.addListener(this);
    this.brightness.addListener(this);
    this.color = color;
  }

  @Override
  public ColorParameter setDescription(String description) {
    return (ColorParameter) super.setDescription(description);
  }

  @Override
  public LXParameter setComponent(LXComponent component, String path) {
    super.setComponent(component, path);
    component.addParameter(path + "/brightness", this.brightness);
    component.addParameter(path + "/saturation", this.saturation);
    component.addParameter(path + "/hue", this.hue);
    return this;
  }

  public int getColor() {
    return this.color;
  }

  public ColorParameter setColor(int color) {
    setValue(Double.longBitsToDouble(color));
    return this;
  }

  public String getHexString() {
    return String.format("0x%08x", this.color);
  }

  @Override
  protected double updateValue(double value) {
    this.internalValueUpdate = true;
    this.color = (int) Double.doubleToRawLongBits(value);
    if (!this.internalHsbUpdate) {
      double b = LXColor.b(this.color);
      this.brightness.setValue(b);
      if (b > 0) {
        double s = LXColor.s(this.color);
        this.saturation.setValue(s);
        if (s > 0) {
          this.hue.setValue(LXColor.h(this.color));
        }
      }
    }
    this.internalValueUpdate = false;
    return value;
  }

  @Override
  public void onParameterChanged(LXParameter parameter) {
    if (!this.internalValueUpdate) {
      this.internalHsbUpdate = true;
      setColor(LXColor.hsb(
        this.hue.getValue(),
        this.saturation.getValue(),
        this.brightness.getValue())
      );
      this.internalHsbUpdate = false;
    }
  }

}
