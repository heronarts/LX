/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.pattern;

import heronarts.lx.LXCategory;
import heronarts.lx.LX;
import heronarts.lx.color.LXColor;
import heronarts.lx.model.GridModel;
import heronarts.lx.utils.LXUtils;

import java.util.HashMap;

@LXCategory(LXCategory.FORM)
public class LifePattern extends LXModelPattern<GridModel> {

  private enum CellState {
    DEAD, BIRTHING, ALIVE, DYING
  };

  private CellState[] state;
  private CellState[] newState;
  private int spawnCounter = 0;

  private final HashMap<String, Integer> stateCount = new HashMap<String, Integer>();

  public LifePattern(LX lx) {
    super(lx);
    this.state = new CellState[model.size];
    this.newState = new CellState[model.size];
    spawn();
  }

  private boolean isLiveState(CellState state) {
    return (state == CellState.BIRTHING) || (state == CellState.ALIVE);
  }

  private void respawn() {
    boolean anyAlive = false;
    for (int i = 0; i < this.state.length; ++i) {
      if (isLiveState(this.state[i])) {
        anyAlive = true;
        this.state[i] = CellState.DYING;
      }
    }
    this.spawnCounter = anyAlive ? 2 : 1;
  }

  private void spawn() {
    for (int i = 0; i < this.state.length; ++i) {
      this.state[i] = (LXUtils.random(0, 100) > 70) ? CellState.BIRTHING : CellState.DEAD;
    }
  }

  private int neighborsAlive(int i) {
    int x = i % this.model.width;
    int y = i / this.model.width;
    return isAlive(x - 1, y - 1) + isAlive(x, y - 1) + isAlive(x + 1, y - 1)
        + isAlive(x - 1, y) + isAlive(x + 1, y) + isAlive(x - 1, y + 1)
        + isAlive(x, y + 1) + isAlive(x + 1, y + 1);
  }

  private int isAlive(int x, int y) {
    if (x < 0 || x >= this.model.width) {
      return 0;
    }
    if (y < 0 || y >= this.model.height) {
      return 0;
    }
    int idx = x + y * this.model.width;
    if (this.isLiveState(this.state[idx])) {
      return 1;
    }
    return 0;
  }

  private void transition() {
    for (int i = 0; i < state.length; ++i) {
      int nA = neighborsAlive(i);
      switch (state[i]) {
      case DEAD:
      case DYING:
        this.newState[i] = (nA == 3) ? CellState.BIRTHING : CellState.DEAD;
        break;
      case ALIVE:
      case BIRTHING:
        this.newState[i] = (nA == 2 || nA == 3) ? CellState.ALIVE
            : CellState.DYING;
        break;
      }
    }
    CellState[] tmp = this.state;
    this.state = this.newState;
    this.newState = tmp;

    String stateSerial = "";
    for (int i = 0; i < this.state.length; ++i) {
      stateSerial += this.isLiveState(this.state[i]) ? "1" : "0";
    }
    Integer count = 0;
    if (this.stateCount.containsKey(stateSerial)) {
      count = this.stateCount.get(stateSerial);
    }
    if (count.equals(3)) {
      this.stateCount.clear();
      this.respawn();
    } else {
      this.stateCount.put(stateSerial, count + 1);
    }
  }

  @Override
  public void run(double deltaMs) {
    if (this.lx.engine.tempo.beat()) {
      if ((this.spawnCounter > 0) && (--this.spawnCounter == 0)) {
        this.spawn();
      } else {
        this.transition();
      }
    }
    double basis = this.lx.engine.tempo.basis();
    for (int i = 0; i < this.model.size; ++i) {
      double b = 0;
      switch (this.state[i]) {
      case ALIVE:
        b = 100;
        break;
      case BIRTHING:
        b = basis * 100;
        break;
      case DEAD:
        b = 0;
        break;
      case DYING:
        b = 100 * (1 - basis);
        break;
      }
      this.colors[i] = LXColor.gray(b);
    }
  }
}
