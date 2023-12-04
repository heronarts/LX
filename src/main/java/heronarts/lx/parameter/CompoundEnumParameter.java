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

public class CompoundEnumParameter<T extends Enum<?>> extends CompoundObjectParameter<T> {

  public CompoundEnumParameter(String label, T t) {
    super(label, EnumParameter.valuesFor(t), t);
  }

  @Override
  public CompoundEnumParameter<T> setDescription(String description) {
    super.setDescription(description);
    return this;
  }

  @Override
  public CompoundEnumParameter<T> setMappable(boolean mappable) {
    super.setMappable(mappable);
    return this;
  }

  @Override
  public CompoundEnumParameter<T> addListener(LXParameterListener listener) {
    super.addListener(listener);
    return this;
  }

  public T getEnum() {
    return getObject();
  }

  public T getBaseEnum() {
    return getBaseObject();
  }

}
