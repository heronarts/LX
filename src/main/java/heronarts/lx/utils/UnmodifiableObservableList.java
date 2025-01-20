package heronarts.lx.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

public class UnmodifiableObservableList<T> implements LXObservableList<T> {

  private final LXObservableList<T> list;
  private final List<T> unmodifiableList;

  private final List<Listener<T>> mutableListeners = new ArrayList<>();
  protected final List<Listener<T>> listeners = Collections.unmodifiableList(this.mutableListeners);

  private final Listener<T> listListener = new Listener<T>() {
    @Override
    public void itemAdded(T item) {
      for (Listener<T> listener : listeners) {
        listener.itemAdded(item);
      }
    }

    @Override
    public void itemRemoved(T item) {
      for (Listener<T> listener : listeners) {
        listener.itemRemoved(item);
      }
    }
  };

  public UnmodifiableObservableList(LXObservableList<T> list) {
    this.list = list;
    this.list.addListener(this.listListener);
    this.unmodifiableList = Collections.unmodifiableList(this.list);
  }

  public UnmodifiableObservableList<T> addListener(Listener<T> listener) {
    Objects.requireNonNull(listener, "May not add null Listener");
    if (this.mutableListeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate Listener: " + listener);
    }
    this.mutableListeners.add(listener);
    return this;
  }

  public UnmodifiableObservableList<T> removeListener(Listener<T> listener) {
    if (!this.mutableListeners.contains(listener)) {
      throw new IllegalStateException("Cannot remove non-existent Listener: " + listener);
    }
    this.mutableListeners.remove(listener);
    return this;
  }

  // List<T> methods

  @Override
  public int size() {
    return this.unmodifiableList.size();
  }

  @Override
  public boolean isEmpty() {
    return this.unmodifiableList.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    return this.unmodifiableList.contains(o);
  }

  @Override
  public Iterator<T> iterator() {
    return this.unmodifiableList.iterator();
  }

  @Override
  public Object[] toArray() {
    return this.unmodifiableList.toArray();
  }

  @Override
  public <T1> T1[] toArray(T1[] a) {
    return this.unmodifiableList.toArray(a);
  }

  @Override
  public boolean add(T t) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return this.unmodifiableList.containsAll(c);
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean addAll(int index, Collection<? extends T> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public T get(int index) {
    return this.unmodifiableList.get(index);
  }

  @Override
  public T set(int index, T element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void add(int index, T element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public T remove(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int indexOf(Object o) {
    return this.unmodifiableList.indexOf(o);
  }

  @Override
  public int lastIndexOf(Object o) {
    return this.unmodifiableList.lastIndexOf(o);
  }

  @Override
  public ListIterator<T> listIterator() {
    return this.unmodifiableList.listIterator();
  }

  @Override
  public ListIterator<T> listIterator(int index) {
    return this.unmodifiableList.listIterator(index);
  }

  @Override
  public List<T> subList(int fromIndex, int toIndex) {
    return this.unmodifiableList.subList(fromIndex, toIndex);
  }
}
