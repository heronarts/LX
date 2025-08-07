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

package heronarts.lx.parameter;

public class EnumParameter<T extends Enum<T>> extends ObjectParameter<T> implements IEnumParameter<T> {

  @SuppressWarnings("unchecked")
  static <T> T[] valuesFor(T o) {
    try {
      return (T[]) o.getClass().getMethod("values").invoke(null);
    } catch (Exception x) {
      throw new RuntimeException(x);
    }
  }

  public final Class<T> enumClass;

  public EnumParameter(String label, T t) {
    super(label, valuesFor(t), t);
    this.enumClass = t.getDeclaringClass();
  }

  public EnumParameter<T> setEnum(String enumName) {
    setValue(Enum.valueOf(this.enumClass, enumName));
    return this;
  }

  @Override
  public EnumParameter<T> setDescription(String description) {
    super.setDescription(description);
    return this;
  }

  @Override
  public EnumParameter<T> setIncrementMode(IncrementMode incrementMode) {
    super.setIncrementMode(incrementMode);
    return this;
  }

  @Override
  public EnumParameter<T> setMappable(boolean mappable) {
    super.setMappable(mappable);
    return this;
  }

  @Override
  public EnumParameter<T> setWrappable(boolean wrappable) {
    super.setWrappable(wrappable);
    return this;
  }

  @Override
  public EnumParameter<T> addListener(LXParameterListener listener) {
    super.addListener(listener);
    return this;
  }

  public T getBaseEnum() {
    return getBaseObject();
  }

  public T getEnum() {
    return getObject();
  }

}
