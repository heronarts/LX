/**
 * Copyright 2025- Justin K. Belcher, Mark C. Slee, Heron Arts LLC
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
 * @author Justin K. Belcher <justin@jkb.studio>
 * @author Mark C. Slee <mark@heronarts.com>
 */

package heronarts.lx.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

public class ObservableList<T> implements List<T> {

  private final List<T> list;

  public interface Listener<T> {
    public default void itemAdded(T item) {}
    public default void itemRemoved(T item) {}
  }

  private final List<Listener<T>> listeners;

  /**
   * Create a new observable list which has an ArrayList<T> as its inner list
   */
  public ObservableList() {
    this(new ArrayList<T>());
  }

  /**
   * Create a new observable list using a given List<T> for its inner list.
   * Allows custom inner list types.
   *
   * @param list List<T> to use as the inner list
   */
  public ObservableList(List<T> list) {
    this.list = list;
    this.listeners = new ArrayList<>();
  }

  /**
   * Private constructor that makes an unmodifiable view of
   * the given ObservableList, with the same listeners etc.
   */
  private ObservableList(ObservableList<T> list) {
    this.list = Collections.unmodifiableList(list.list);
    this.listeners = list.listeners;
  }

  /**
   * Get an unmodifiable version of this list
   */
  public ObservableList<T> asUnmodifiableList() {
    return new ObservableList<T>(this);
  }

  public ObservableList<T> addListener(Listener<T> listener) {
    Objects.requireNonNull(listener, "May not add null Listener");
    if (this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate Listener: " + listener);
    }
    this.listeners.add(listener);
    return this;
  }

  public ObservableList<T> removeListener(Listener<T> listener) {
    if (!this.listeners.contains(listener)) {
      throw new IllegalStateException("Cannot remove non-existent Listener: " + listener);
    }
    this.listeners.remove(listener);
    return this;
  }

  private void notifyAdded(T item) {
    for (Listener<T> listener : this.listeners) {
      listener.itemAdded(item);
    }
  }

  private void notifyAdded(Collection<? extends T> items) {
    for (T item : items) {
      notifyAdded(item);
    }
  }

  private void notifyRemoved(T item) {
    for (Listener<T> listener : this.listeners) {
      listener.itemRemoved(item);
    }
  }

  private void notifyRemoved(Collection<? extends T> items) {
    for (T item : items) {
      notifyRemoved(item);
    }
  }

  // List<T> methods

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
    final Iterator<T> iterator = this.list.iterator();
    return new Iterator<T>() {
      T current = null;

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public T next() {
        return current = iterator.next();
      }

      @Override
      public void remove() {
        iterator.remove();
        notifyRemoved(current);
      }
    };
  }

  @Override
  public Object[] toArray() {
    return this.list.toArray();
  }

  @Override
  public <T1> T1[] toArray(T1[] a) {
    return this.list.toArray(a);
  }

  @Override
  public boolean add(T item) {
    this.list.add(item);
    notifyAdded(item);
    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean remove(Object o) {
    if (this.list.remove(o)) {
      notifyRemoved((T) o);
      return true;
    }
    return false;
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return this.list.containsAll(c);
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    if (this.list.addAll(c)) {
      notifyAdded(c);
      return true;
    }
    return false;
  }

  @Override
  public boolean addAll(int index, Collection<? extends T> c) {
    if (this.list.addAll(index, c)) {
      notifyAdded(c);
      return true;
    }
    return false;
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean removeAll(Collection<?> c) {
    boolean removed = false;
    for (Object o : c) {
      boolean itemRemoved = this.list.remove(o);
      if (itemRemoved) {
        notifyRemoved((T) o);
      }
      removed = removed || itemRemoved;
    }
    return removed;
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    return this.list.retainAll(c);
  }

  @Override
  public void clear() {
    List<T> removed = new ArrayList<>(this.list);
    this.list.clear();
    for (T item : removed) {
      notifyRemoved(item);
    }
  }

  @Override
  public T get(int index) {
    return this.list.get(index);
  }

  @Override
  public T set(int index, T item) {
    T existing = this.list.get(index);
    if (existing != item) {
      this.list.set(index, item);
      notifyRemoved(existing);
      notifyAdded(item);
    }
    return existing;
  }

  @Override
  public void add(int index, T item) {
    this.list.add(index, item);
    notifyAdded(item);
  }

  @Override
  public T remove(int index) {
    T removed = this.list.remove(index);
    notifyRemoved(removed);
    return removed;
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
    throw new UnsupportedOperationException("ListIterator not supported in ObservableList");
  }

  @Override
  public ListIterator<T> listIterator(int index) {
    throw new UnsupportedOperationException("ListIterator not supported in ObservableList");
  }

  @Override
  public List<T> subList(int fromIndex, int toIndex) {
    return this.list.subList(fromIndex, toIndex);
  }
}
