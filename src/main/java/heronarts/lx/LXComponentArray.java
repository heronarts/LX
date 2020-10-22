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

package heronarts.lx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

public class LXComponentArray<T extends LXComponent> implements java.util.List<T> {

  public interface Listener<T> {
    public void itemAdded(T item);
    public void itemRemoved(T item);
    public void itemMoved(T item);
  }

  private final List<Listener<T>> listeners = new ArrayList<Listener<T>>();

  public void addListener(Listener<T> listener) {
    Objects.requireNonNull(listener, "May not add null LXComponentArray.Listener");
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("May not add duplicate LXComponentArray.Listener: " + listener);
    }
    this.listeners.add(listener);
  }

  public void removeListener(Listener<T> listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("May not remove non-registered LXComponentArray.Listener: " + listener);
    }
    this.listeners.remove(listener);
  }

  private final ArrayList<T> list = new ArrayList<T>();

  @Override
  public int size() {
    return this.list.size();
  }

  @Override
  public boolean isEmpty() {
    return this.list.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    return this.list.contains(o);
  }

  @Override
  public Iterator<T> iterator() {
    return this.list.iterator();
  }

  @Override
  public Object[] toArray() {
    return this.list.toArray();
  }

  @Override
  public <U> U[] toArray(U[] a) {
    return this.list.toArray(a);
  }

  @Override
  public boolean add(T e) {
    return this.list.add(e);
  }

  @Override
  public void add(int index, T element) {
    this.list.add(index, element);
    for (Listener<T> listener : this.listeners) {
      listener.itemAdded(element);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean remove(Object o) {
    boolean ret = this.list.remove(o);
    if (ret) {
      for (Listener<T> listener : this.listeners) {
        listener.itemRemoved((T) o);
      }
    }
    return ret;
  }

  @Override
  public T remove(int index) {
    T elem = this.list.remove(index);
    if (elem != null) {
      for (Listener<T> listener : this.listeners) {
        listener.itemRemoved(elem);
      }
    }
    return elem;
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return this.list.containsAll(c);
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    throw new UnsupportedOperationException("LXArray does not support addAll");
  }

  @Override
  public boolean addAll(int index, Collection<? extends T> c) {
    throw new UnsupportedOperationException("LXArray does not support addAll");
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException("LXArray does not support removeAll");
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException("LXArray does not support retainAll");
  }

  @Override
  public void clear() {
    for (int i = this.list.size() - 1; i >= 0; --i) {
      remove(i);
    }
  }

  @Override
  public T get(int index) {
    return this.list.get(index);
  }

  @Override
  public T set(int index, T element) {
    throw new UnsupportedOperationException("LXArray does not support set");
  }

  @Override
  public int indexOf(Object o) {
    return this.list.indexOf(o);
  }

  @Override
  public int lastIndexOf(Object o) {
    return this.list.lastIndexOf(o);
  }

  @Override
  public ListIterator<T> listIterator() {
    return this.list.listIterator();
  }

  @Override
  public ListIterator<T> listIterator(int index) {
    return this.list.listIterator(index);
  }

  @Override
  public List<T> subList(int fromIndex, int toIndex) {
    return this.list.subList(fromIndex, toIndex);
  }

  public void dispose() {
    clear();
    this.listeners.clear();
  }

}
