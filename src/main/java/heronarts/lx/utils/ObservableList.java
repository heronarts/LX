package heronarts.lx.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

public class ObservableList<T> implements LXObservableList<T> {

  private final List<T> list;

  private final List<Listener<T>> mutableListeners = new ArrayList<>();
  protected final List<Listener<T>> listeners = Collections.unmodifiableList(this.mutableListeners);

  /**
   * Create a new observable list which has an ArrayList<T> as its inner list
   */
  public ObservableList() {
    this.list = new ArrayList<T>();
  }

  /**
   * Create a new observable list using a given List<T> for its inner list.
   * @param list List<T> to use as the inner list
   */
  public ObservableList(List<T> list) {
    this.list = list;
  }

  public ObservableList<T> addListener(Listener<T> listener) {
    Objects.requireNonNull(listener, "May not add null Listener");
    if (this.mutableListeners.contains(listener)) {
      throw new IllegalStateException("Cannot add duplicate Listener: " + listener);
    }
    this.mutableListeners.add(listener);
    return this;
  }

  public ObservableList<T> removeListener(Listener<T> listener) {
    if (!this.mutableListeners.contains(listener)) {
      throw new IllegalStateException("Cannot remove non-existent Listener: " + listener);
    }
    this.mutableListeners.remove(listener);
    return this;
  }

  private void notifyAdded(T element) {
    for (Listener<T> listener : this.listeners) {
      listener.itemAdded(element);
    }
  }

  private void notifyAdded(Collection<? extends T> elements) {
    for (T element : elements) {
      notifyAdded(element);
    }
  }

  private void notifyRemoved(T element) {
    for (Listener<T> listener : this.listeners) {
      listener.itemRemoved(element);
    }
  }

  private void notifyRemoved(Collection<? extends T> elements) {
    for (T element : elements) {
      notifyRemoved(element);
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
  public boolean add(T t) {
    boolean added = this.list.add(t);
    if (added) {
      notifyAdded(t);
    }
    return added;
  }

  @Override
  public boolean remove(Object o) {
    boolean removed = this.list.remove(o);
    if (removed) {
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
    boolean added = this.list.addAll(c);
    if (added) {
      notifyAdded(c);
    }
    return added;
  }

  @Override
  public boolean addAll(int index, Collection<? extends T> c) {
    boolean added = this.list.addAll(c);
    if (added) {
      notifyAdded(c);
    }
    return added;
  }

  @Override
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
    for (T t : removed) {
      notifyRemoved(t);
    }
  }

  @Override
  public T get(int index) {
    return this.list.get(index);
  }

  @Override
  public T set(int index, T element) {
    T existing = this.list.get(index);
    if (existing != element) {
      this.list.set(index, element);
      notifyRemoved(existing);
      notifyAdded(element);
    }
    return existing;
  }

  @Override
  public void add(int index, T element) {
    this.list.add(index, element);
    notifyAdded(element);
  }

  @Override
  public T remove(int index) {
    int sizePrior = this.list.size();
    T removed = this.list.remove(index);
    // Accommodates removal of null items:
    if (this.list.size() != sizePrior) {
      notifyRemoved(removed);
    }
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
    // TODO: handle add/removed from iterator
    return this.list.listIterator();
  }

  @Override
  public ListIterator<T> listIterator(int index) {
    // TODO: handle add/removed from iterator
    return this.list.listIterator(index);
  }

  @Override
  public List<T> subList(int fromIndex, int toIndex) {
    return this.list.subList(fromIndex, toIndex);
  }
}
