/**
 * Copyright 2017- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.parameter;

public class ObjectParameter<T> extends DiscreteParameter {

  private T[] objects = null;

  public ObjectParameter(String label, T[] objects) {
    super(label, 0, objects.length);
    setObjects(objects);
  }

  public ObjectParameter(String label, T[] objects, T value) {
    this(label, objects);
    setValue(value);
  }

  @Override
  public ObjectParameter<T> setDescription(String description) {
    super.setDescription(description);
    return this;
  }

  /**
   * Set a list of objects for the parameter
   *
   * @param objects Array of arbitrary object values
   * @return this
   */
  public ObjectParameter<T> setObjects(T[] objects) {
    this.objects = objects;
    String[] options = new String[objects.length];
    for (int i = 0; i < objects.length; ++i) {
      options[i] = (objects[i] == null) ? "null" : objects[i].toString();
    }
    setOptions(options);
    return this;
  }

  @Override
  public DiscreteParameter setRange(int minValue, int maxValue) {
    if (this.objects!= null && (this.objects.length != maxValue - minValue)) {
      throw new UnsupportedOperationException("May not call setRange on an ObjectParameter with Object list of different length");
    }
    return super.setRange(minValue, maxValue);
  }

  public LXParameter setValue(Object object) {
    if (this.objects == null) {
      throw new UnsupportedOperationException("Cannot setValue with an object unless setObjects() was called");
    }
    for (int i = 0; i < this.objects.length; ++i) {
      if (this.objects[i] == object) {
        return setValue(i);
      }
    }
    throw new IllegalArgumentException("Not a valid object for this parameter: " + object.toString());
  }

  public T[] getObjects() {
    return this.objects;
  }

  public T getObject() {
    return this.objects[getValuei()];
  }

}
