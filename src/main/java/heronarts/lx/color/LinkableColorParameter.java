package heronarts.lx.color;

import heronarts.lx.LX;
import heronarts.lx.parameter.DiscreteParameter;

public class LinkableColorParameter extends ColorParameter {
  public static final int NO_SWATCH = -2;
  public static final int MASTER_SWATCH = -1;

  private final LX lx;
  public final DiscreteParameter swatchNumber;
  public final DiscreteParameter swatchColorNumber;

  public static final String PATH_SWATCH_NUMBER = "swatchNumber";
  public static final String PATH_SWATCH_COLOR_NUMBER = "swatchColorNumber";

  public LinkableColorParameter(LX lx, String label) {
    this(lx, label, 0xff000000);
  }

  public LinkableColorParameter(LX lx, String label, int color) {
    super(label, color);
    this.lx = lx;

    // TODO: There is no maximum number of swatches, so the 8 here is arbitrary.
    this.swatchNumber = new DiscreteParameter("Swatch", NO_SWATCH,-2, 8)
            .setDescription("Which swatch to draw colors from (-1 for main, -2 for none)");

    this.swatchColorNumber = new DiscreteParameter("Index", 0,0,
            LXSwatch.MAX_COLORS)
            .setDescription("Which color in that swatch to draw from");

    this.swatchNumber.addListener(this);
    this.swatchColorNumber.addListener(this);

    this.swatchNumber.parentParameter = this;
    this.swatchColorNumber.parentParameter = this;
  }


  @Override
  public LinkableColorParameter setDescription(String description) {
    return (LinkableColorParameter) super.setDescription(description);
  }

  @Override
  public int getColor() {
    int swatchNum = this.swatchNumber.getValuei();
    if (swatchNum == NO_SWATCH) {
      return this.color;
    }
    LXSwatch swatch;
    int numSwatches = this.lx.engine.palette.swatches.size();
    if (swatchNum == MASTER_SWATCH) {
      swatch = this.lx.engine.palette.swatch;
    } else if (swatchNum < numSwatches) {
      swatch = this.lx.engine.palette.swatches.get(swatchNum);
    } else {
      swatch = this.lx.engine.palette.swatches.get(numSwatches - 1);
    }
    return swatch.getColor(this.swatchColorNumber.getValuei()).color.getColor();
  }

  @Override
  public void dispose() {
    this.swatchNumber.removeListener(this);
    this.swatchColorNumber.removeListener(this);
    super.dispose();
  }

}