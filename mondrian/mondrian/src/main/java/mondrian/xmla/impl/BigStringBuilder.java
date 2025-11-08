package mondrian.xmla.impl;

import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;

public class BigStringBuilder {
    private final ObjectBigArrayBigList<String> list;
    private long length; // Отслеживаем общий размер строки

    public BigStringBuilder() {
        this.list = new ObjectBigArrayBigList<>();
        this.length = 0;
    }

    public BigStringBuilder append(String str) {
        if (str != null && !str.isEmpty()) {
            list.add(str);
            length += str.length();
        }
        return this;
    }

    public long length() {
        return length;
    }

    public boolean isEmpty() {
        return length == 0;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder((int) Math.min(length, Integer.MAX_VALUE));
        for (String s : list) {
            sb.append(s);
        }
        return sb.toString();
    }

    public void clear() {
        list.clear();
        length = 0;
    }
}
