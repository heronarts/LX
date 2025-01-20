package heronarts.lx.utils;

import java.util.List;

public interface LXObservableList<T> extends List<T> {

  public interface Listener<T> {
    public default void itemAdded(T item) {};
    public default void itemRemoved(T item) {};
  }

  public LXObservableList<T> addListener(Listener<T> listener);
  public LXObservableList<T> removeListener(Listener<T> listener);

}
