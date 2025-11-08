package mondrian.util;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.EmptyStackException;

public class FastUtilStack<E> {
    private final ObjectArrayList<E> list;

    public FastUtilStack() {
        this.list = new ObjectArrayList<>();
    }

    public FastUtilStack(FastUtilStack<E> toCopy) {
        this.list = new ObjectArrayList<>(toCopy.list);
    }

    public E push(E item) {
        list.add(item);
        return item;
    }

    public E pop() {
        if (isEmpty()) {
            throw new EmptyStackException();
        }
        return list.remove(list.size() - 1);
    }

    public E peek() {
        if (isEmpty()) {
            throw new EmptyStackException();
        }
        return list.get(list.size() - 1);
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public void add(E item) {
        push(item); // В стеке `add()` аналогичен `push()`
    }

    public long size() {
        return list.size(); // Возвращает long вместо int
    }
    public long indexOf(E item) {
        return list.indexOf(item);
    }
}
