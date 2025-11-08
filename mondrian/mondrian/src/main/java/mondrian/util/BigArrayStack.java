/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
*/

package mondrian.util;

import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;
import java.util.EmptyStackException;

public class BigArrayStack<E> {
    private final ObjectBigArrayBigList<E> list;

    public BigArrayStack() {
        this.list = new ObjectBigArrayBigList<>();
    }

    public BigArrayStack(BigArrayStack<E> toCopy) {
        this.list = new ObjectBigArrayBigList<>(toCopy.list);
    }

    public E push(E item) {
        list.add(item);
        return item;
    }

    public E pop() {
        if (isEmpty()) {
            throw new EmptyStackException();
        }
        return list.remove(list.size64() - 1);
    }

    public E peek() {
        if (isEmpty()) {
            throw new EmptyStackException();
        }
        return list.get(list.size64() - 1);
    }

    public void add(E item) {
        push(item); // В стеке `add()` аналогичен `push()`
    }
    public boolean isEmpty() {
        return list.isEmpty();
    }

    public long size() {
        return list.size64(); // Возвращает long вместо int
    }
    public long indexOf(E item) {
        return list.indexOf(item);
    }

}
