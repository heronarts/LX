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

public class CompoundObjectParameter<T> extends CompoundDiscreteParameter {

  private T[] objects = null;

  public CompoundObjectParameter(String label, T[] objects) {
    this(label, objects, null, (String[]) null);
  }

  public CompoundObjectParameter(String label, T[] objects, T value) {
    this(label, objects, value, (String[]) null);
  }

  public CompoundObjectParameter(String label, T[] objects, String[] options) {
    this(label, objects, null, options);
  }

  public CompoundObjectParameter(String label, T[] objects, T value, String[] options) {
    super(label, ObjectParameter.defaultValue(value, objects), 0, objects.length);
    setObjects(objects, options);
    setIncrementMode(IncrementMode.RELATIVE);
    setWrappable(true);
  }

  @Override
  public CompoundObjectParameter<T> setDescription(String description) {
    super.setDescription(description);
    return this;
  }

  /**
   * Set a list of objects for the parameter
   *
   * @param objects Array of arbitrary object values
   * @return this
   */
  public CompoundObjectParameter<T> setObjects(T[] objects) {
    return setObjects(objects, null);
  }

  public CompoundObjectParameter<T> setObjects(T[] objects, String[] options) {
    this.objects = objects;
    if (options == null) {
      options = ObjectParameter.toOptions(objects);
    }
    setOptions(options);
    return this;
  }

  @Override
  public CompoundObjectParameter<T> setRange(int minValue, int maxValue) {
    if (this.objects!= null && (this.objects.length != maxValue - minValue)) {
      throw new UnsupportedOperationException("May not call setRange on an ObjectParameter with Object list of different length");
    }
    super.setRange(minValue, maxValue);
    return this;
  }

  public CompoundObjectParameter<T> setValue(Object object) {
    if (this.objects == null) {
      throw new UnsupportedOperationException("Cannot setValue with an object unless setObjects() was called");
    }
    int index = ObjectParameter.indexOf(object, this.objects);
    if (index >= 0) {
      setValue(index);
      return this;
    }
    throw new IllegalArgumentException("Not a valid object for this parameter: " + object.toString());
  }

  public T[] getObjects() {
    return this.objects;
  }

  public T getObject() {
    return this.objects[getIndex()];
  }

  public T getBaseObject() {
    return this.objects[getBaseIndex()];
  }

}
