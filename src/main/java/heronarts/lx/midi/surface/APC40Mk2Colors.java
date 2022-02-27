/*
        Based on https://github.com/hepp/akai-apc40-mk2, whose license reads:

        MIT License

        Copyright (c) 2021 Hepp Maccoy

        Permission is hereby granted, free of charge, to any person obtaining a copy
        of this software and associated documentation files (the "Software"), to deal
        in the Software without restriction, including without limitation the rights
        to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
        copies of the Software, and to permit persons to whom the Software is
        furnished to do so, subject to the following conditions:

        The above copyright notice and this permission notice shall be included in all
        copies or substantial portions of the Software.

        THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
        IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
        FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
        AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
        LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
        OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
        SOFTWARE.
        © 2022 GitHub, Inc.
        Terms
        Privacy
*/

package heronarts.lx.midi.surface;

import java.util.*;

import heronarts.lx.color.LXColor;

public class APC40Mk2Colors {
  private static class RGB {
    int id;
    int r;
    int g;
    int b;

    private RGB(int r, int g, int b) {
      this.r = (256 + r) % 256;
      this.g = (256 + g) % 256;
      this.b = (256 + b) % 256;
      this.id = this.r << 16 | this.g << 8 | this.b;
    }

    private RGB(int[] a) {
      this(a[0], a[1], a[2]);
    }

    private RGB(int color) {
      this(LXColor.red(color), LXColor.green(color), LXColor.blue(color));
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if(obj == null || obj.getClass()!= this.getClass()) return false;
      RGB other = (RGB) obj;
      return this.id == other.id;
    }

    @Override
    public int hashCode() {
      return this.id;
    }
  }

  private Map<RGB, Integer> cache;

  public static final int COLORCODE_COUNT = 128;

  public static final int[][] RGB_COLORS = {{0,0,0},{30,30,30},{127,127,127},{255,255,255},{255,76,76},{255,0,0},{89,0,0},{25,0,0},{255,189,108},{255,84,0},{89,29,0}
          ,{39,27,0},{255,255,76},{255,255,0},{89,89,0},{25,25,0},{136,255,76},{84,255,0},{29,89,0},{20,43,0},{76,255,76},{0,255,0},{0,89,0},{0,25,0},{76,255,94},{0,255,25},{0,89,13}
          ,{0,25,2},{76,255,136},{0,255,85},{0,89,29},{0,31,18},{76,255,183},{0,255,153},{0,89,53},{0,25,18},{76,195,255},{0,169,255},{0,65,82},{0,16,25},{76,136,255},{0,85,255}
          ,{0,29,89},{0,8,25},{76,76,255},{0,0,255},{0,0,89},{0,0,25},{135,76,255},{84,0,255},{25,0,100},{15,0,48},{255,76,255},{255,0,255},{89,0,89},{25,0,25},{255,76,135}
          ,{255,0,84},{89,0,29},{34,0,19},{255,21,0},{153,53,0},{121,81,0},{67,100,0},{3,57,0},{0,87,53},{0,84,127},{0,0,255},{0,69,79},{37,0,204},{127,127,127},{32,32,32}
          ,{255,0,0},{189,255,45},{175,237,6},{100,255,9},{16,139,0},{0,255,135},{0,169,255},{0,42,255},{63,0,255},{122,0,255},{178,26,125},{64,33,0},{255,74,0},{136,225,6}
          ,{114,255,21},{0,255,0},{59,255,38},{89,255,113},{56,255,204},{91,138,255},{49,81,198},{135,127,233},{211,29,255},{255,0,93},{255,127,0},{185,176,0},{144,255,0},{131,93,7}
          ,{57,43,0},{20,76,16},{13,80,56},{21,21,42},{22,32,90},{105,60,28},{168,0,10},{222,81,61},{216,106,28},{255,225,38},{158,225,47},{103,181,15},{30,30,48},{220,255,107}
          ,{128,255,189},{154,153,255},{142,102,255},{64,64,64},{117,117,117},{224,255,255},{160,0,0},{53,0,0},{26,208,0},{7,66,0},{185,176,0},{63,49,0},{179,95,0},
          {75,21,18}
  };

  public APC40Mk2Colors() {
    this.cache = new HashMap<>();
  }

  private static double colorDist(RGB rgb0, RGB rgb1) {
    //https://stackoverflow.com/a/5069048
    long rmean = (rgb0.r + rgb1.r) / 2;
    int r = rgb0.r - rgb1.r;
    int g = rgb0.g - rgb1.g;
    int b = rgb0.b - rgb1.b;
    return Math.sqrt((((512 + rmean) * r * r) >> 8) + 4L * g * g + (((767 - rmean) * b * b) >> 8));
  }

  private static RGB rgbFromColorId(int colorId) {
    if (colorId < 0 || colorId >= COLORCODE_COUNT) {
      return new RGB(0);
    }
    return new RGB(RGB_COLORS[colorId]);
  }

  public int nearest(int color) {
    if (color == 0) return 0;
    RGB rgb = new RGB(color);

    if (this.cache.containsKey(rgb)) return this.cache.get(rgb);

    double minDistance = 1000000;
    int nearestId = 0;

    for (int i = 0; i < COLORCODE_COUNT; i++) {
      RGB candidate = rgbFromColorId(i);
      double dist = colorDist(rgb, candidate);
      if (dist < minDistance) {
        minDistance = dist;
        nearestId = i;
      }
    }

    this.cache.put(rgb, nearestId);
    return nearestId;
  }
}
