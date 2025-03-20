/**
 * Copyright 2025- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;
import java.util.concurrent.atomic.AtomicReference;

import heronarts.lx.parameter.MutableParameter;

/**
 * The LXEngineArrayList is a utility class that offers similar semantics to CopyOnWriteArrayList,
 * but for the scenario in which it's understood that only the LXEngine thread ever writes to the
 * list, while the UI thread may be frequently reading the list. A single persistent underlying ArrayList
 * is used to represent the engine's copy of the list, and a copy is prepared for the UI thread
 * on-demand. Transaction-like semantics are available for complex edit operations that may perform
 * multiple changes before generating a new UI-thread copy.
 *
 * @param <T> Type of object stored by the list
 */
public class LXEngineThreadArrayList<T> implements List<T>, RandomAccess {

  private final ArrayList<T> engineList = new ArrayList<>();

  private final AtomicReference<List<T>> uiList = new AtomicReference<>();

  private int semaphore = 0;

  private boolean needsSet = false;

  public final MutableParameter changed = new MutableParameter();

  public LXEngineThreadArrayList() {
    _setUIThreadList(false);
  }

  /**
   * Begin a modification session on this list. Changes will not update
   * the UI copy unil commit() is called.
   *
   * @return this
   */
  public LXEngineThreadArrayList<T> begin() {
    ++this.semaphore;
    return this;
  }

  /**
   * Commit changes to the engine list and create a copy for the UI thread.
   *
   * @return this
   */
  public LXEngineThreadArrayList<T> commit() {
    if (this.semaphore <= 0) {
      throw new IllegalStateException("LXEngineArrayList may not commit changes when semaphore already 0");
    }
    --this.semaphore;
    if ((this.semaphore == 0) && this.needsSet) {
      this.needsSet = false;
      setUIThreadList();
    }
    return this;
  }

  /**
   * Clear the full list and replace its content by the other list
   *
   * @param contents List contents
   */
  public void set(List<T> contents) {
    begin();
    clear();
    addAll(contents);
    commit();
  }


  private void setUIThreadList() {
    if (this.semaphore == 0) {
      _setUIThreadList(true);
    } else {
      this.needsSet = true;
    }
  }

  private void _setUIThreadList(boolean notify) {
    // Make a copy of the engine list for the UI thread to use, and wrap it in
    // unmodifiable to ensure nobody tries to modify it from drawing
    this.uiList.set(Collections.unmodifiableList(new ArrayList<T>(this.engineList)));
    if (notify) {
      this.changed.bang();
    }
  }

  public List<T> getUIThreadList() {
    return this.uiList.get();
  }

  @Override
  public int size() {
    return this.engineList.size();
  }

  @Override
  public boolean isEmpty() {
    return this.engineList.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    return this.engineList.contains(o);
  }

  @Override
  public Iterator<T> iterator() {
    return this.engineList.iterator();
  }

  @Override
  public Object[] toArray() {
    return this.engineList.toArray();
  }

  @Override
  public <V> V[] toArray(V[] a) {
    return this.engineList.toArray(a);
  }

  @Override
  public boolean add(T e) {
    boolean ret = this.engineList.add(e);
    setUIThreadList();
    return ret;
  }

  @Override
  public boolean remove(Object o) {
    if (this.engineList.remove(o)) {
      setUIThreadList();
      return true;
    }
    return false;
  }

  public void removeRange(int fromIndex, int toIndex) {
    this.engineList.subList(fromIndex, toIndex).clear();
    setUIThreadList();
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return this.engineList.containsAll(c);
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    if (this.engineList.addAll(c)) {
      setUIThreadList();
      return true;
    }
    return false;
  }

  @Override
  public boolean addAll(int index, Collection<? extends T> c) {
    if (this.engineList.addAll(index, c)) {
      setUIThreadList();
      return true;
    }
    return false;
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    if (this.engineList.removeAll(c)) {
      setUIThreadList();
      return true;
    }
    return false;
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    if (this.engineList.retainAll(c)) {
      setUIThreadList();
      return true;
    }
    return false;
  }

  @Override
  public void clear() {
    this.engineList.clear();
    setUIThreadList();
  }

  @Override
  public T get(int index) {
    return this.engineList.get(index);
  }

  @Override
  public T set(int index, T element) {
    T ret = this.engineList.set(index, element);
    setUIThreadList();
    return ret;
  }

  @Override
  public void add(int index, T element) {
    this.engineList.add(index, element);
    setUIThreadList();
  }

  @Override
  public T remove(int index) {
    T ret = this.engineList.remove(index);
    setUIThreadList();
    return ret;
  }

  @Override
  public int indexOf(Object o) {
    return this.engineList.indexOf(o);
  }

  @Override
  public int lastIndexOf(Object o) {
    return this.engineList.lastIndexOf(o);
  }

  @Override
  public ListIterator<T> listIterator() {
    return this.engineList.listIterator();
  }

  @Override
  public ListIterator<T> listIterator(int index) {
    return this.engineList.listIterator(index);
  }

  @Override
  public List<T> subList(int fromIndex, int toIndex) {
    return this.engineList.subList(fromIndex, toIndex);
  }

}
