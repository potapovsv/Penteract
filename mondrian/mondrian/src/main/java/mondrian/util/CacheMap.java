package mondrian.util;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;

/**
 * Map with limited size to be used as cache.
 *
 * @param <S> the type of keys
 * @param <T> the type of mapped values
 * @author lcanals, www.tasecurity.net
 */
public class CacheMap<S, T> implements Map<S, T> {
    private LinkedNode head;
    private LinkedNode tail;
    private final Map<S, Pair> map;
    private final int maxSize;

    /**
     * Creates an empty map with limited size.
     *
     * @param size Maximum number of mapped elements.
     */
    public CacheMap(final int size) {
        this.head = new LinkedNode(null, null);
        this.tail = new LinkedNode(head, null);
        this.map = new WeakHashMap<>(size);
        this.maxSize = size;
    }

    @Override
    public void clear() {
        this.head = new LinkedNode(null, null);
        this.tail = new LinkedNode(head, null);
        map.clear();
    }

    @Override
    public boolean containsKey(final Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(final Object value) {
        return values().contains(value);
    }

    @Override
    public Set<Map.Entry<S, T>> entrySet() {
        final Set<Map.Entry<S, T>> set = new HashSet<>();
        for (final Map.Entry<S, Pair> entry : map.entrySet()) {
            set.add(new SimpleEntry<>(entry.getKey(), entry.getValue().value));
        }
        return set;
    }

    @Override
    public T get(final Object key) {
        final Pair pair = map.get(key);
        if (pair != null) {
            final LinkedNode node = pair.getNode();
            if (node == null) {
                map.remove(key);
                return null;
            }
            node.moveTo(head);
            return pair.value;
        }
        return null;
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public Set<S> keySet() {
        return map.keySet();
    }

    @Override
    public T put(final S key, final T value) {
        final Pair pair = new Pair(value, new LinkedNode(head, key));
        final Pair oldPair = map.put(key, pair);
        
        // Удаляем LRU элемент только если добавили новый и превысили размер
        if (oldPair == null && map.size() > maxSize) {
            final LinkedNode lruNode = tail.getPrevious();
            if (lruNode != null && lruNode.key != null) {
                lruNode.remove();
                map.remove(lruNode.key);
            }
        }
        
        return oldPair != null ? oldPair.value : null;
    }

    @Override
    public void putAll(final Map<? extends S, ? extends T> t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T remove(final Object key) {
        final Pair pair = map.remove(key);
        if (pair != null) {
            pair.getNode().remove();
            return pair.value;
        }
        return null;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public Collection<T> values() {
        final List<T> vals = new ArrayList<>();
        for (final Pair pair : map.values()) {
            vals.add(pair.value);
        }
        return vals;
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public String toString() {
        return "Ordered keys: " + head + "\nMap:" + map;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof CacheMap)) return false;
        final CacheMap<?, ?> cacheMap = (CacheMap<?, ?>) o;
        return map.equals(cacheMap.map);
    }

    //
    // PRIVATE STUFF ------------------
    //

    /**
     * Pair of linked key - value
     */
    private final class Pair implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private final T value;
        private final WeakReference<LinkedNode> node;

        private Pair(final T value, final LinkedNode node) {
            this.node = new WeakReference<>(node);
            this.value = value;
        }

        private LinkedNode getNode() {
            return node.get();
        }

        @Override
        public boolean equals(final Object o) {
            return o != null && o.equals(this.value);
        }
    }

    /**
     * Represents a node in a linked list.
     */
    private class LinkedNode implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private LinkedNode next, prev;
        private S key;

        public LinkedNode(final LinkedNode prev, final S key) {
            this.key = key;
            insertAfter(prev);
        }

        public void remove() {
            if (this.prev != null) {
                this.prev.next = this.next;
            }
            if (this.next != null) {
                this.next.prev = this.prev;
            }
        }

        public void moveTo(final LinkedNode prev) {
            remove();
            insertAfter(prev);
        }

        public LinkedNode getPrevious() {
            return this.prev;
        }

        private void insertAfter(final LinkedNode prev) {
            if (prev != null) {
                this.next = prev.next;
                if (prev.next != null) {
                    prev.next.prev = this;
                }
                prev.next = this;
            }
            this.prev = prev;
        }

        @Override
        public String toString() {
            if (key != null) {
                return this.next != null ? key + ", " + this.next : key.toString();
            }
            return this.next != null ? "<null>, " + this.next : "<null>";
        }
    }
}