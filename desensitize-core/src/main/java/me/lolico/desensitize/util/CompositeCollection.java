package me.lolico.desensitize.util;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 包装多个集合以对外提供统一的视图，不支持任何修改集合的动作。
 *
 * @author l00998
 */
public class CompositeCollection<E> implements Collection<E>, Serializable {

    private static final long serialVersionUID = -697763314245853818L;

    private final List<Collection<E>> collections = new ArrayList<>();

    public CompositeCollection(Collection<Collection<E>> collections) {
        for (Collection<E> collection : collections) {
            if (collection != null) {
                this.collections.add(collection);
            }
        }
    }

    @Override
    public int size() {
        int size = 0;
        for (Collection<E> item : collections) {
            size += item.size();
        }
        return size;
    }

    @Override
    public boolean isEmpty() {
        for (Collection<E> item : collections) {
            if (!item.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean contains(Object o) {
        for (Collection<E> item : collections) {
            if (item.contains(o)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object item : collections) {
            if (!contains(item)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Iterator<E> iterator() {
        if (collections.isEmpty()) {
            return EmptyIterator.getInstance();
        }
        return new CompositeIterator<>(collections.stream().map(Collection::iterator)
                .collect(Collectors.toList()));
    }

    @Override
    public Object[] toArray() {
        final Object[] result = new Object[size()];
        int i = 0;
        for (Iterator<E> it = iterator(); it.hasNext(); i++) {
            result[i] = it.next();
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] array) {
        int size = size();
        Object[] result;
        if (array.length >= size) {
            result = array;
        } else {
            result = (Object[]) Array.newInstance(array.getClass().getComponentType(), size);
        }

        int i = 0;
        for (Iterator<E> it = iterator(); it.hasNext(); i++) {
            result[i] = it.next();
        }

        if (result.length > size) {
            result[size] = null;
        }
        return (T[]) result;
    }

    @Override
    public boolean add(E e) {
        throw new UnsupportedOperationException("add");
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("remove");
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException("addAll");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("removeAll");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("retainAll");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("clear");
    }

    private static class CompositeIterator<E> implements Iterator<E> {

        private final Queue<Iterator<E>> iterators = new LinkedList<>();
        private Iterator<E> current = null;

        public CompositeIterator(Collection<Iterator<E>> iterators) {
            this.iterators.addAll(iterators);
        }

        @Override
        public boolean hasNext() {
            updateCurrent();
            return current.hasNext();
        }

        @Override
        public E next() {
            updateCurrent();
            return current.next();
        }

        private void updateCurrent() {
            if (current == null) {
                if (iterators.isEmpty()) {
                    current = EmptyIterator.getInstance();
                } else {
                    current = iterators.remove();
                }
            }
            while (!current.hasNext() && !iterators.isEmpty()) {
                current = iterators.remove();
            }
        }
    }

    // @formatter:off
    private static class EmptyIterator<E> implements Iterator<E> {
        @SuppressWarnings("rawtypes")
        private static final EmptyIterator INSTANCE = new EmptyIterator<>();
        @Override
        public boolean hasNext() {return false;}
        @Override
        public E next() {throw new NoSuchElementException();}
        @SuppressWarnings("unchecked")
        public static <E> Iterator<E> getInstance() {return INSTANCE;}
    }
    // @formatter:on
}
