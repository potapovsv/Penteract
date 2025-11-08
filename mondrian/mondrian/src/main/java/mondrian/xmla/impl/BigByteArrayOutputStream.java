package mondrian.xmla.impl;

import it.unimi.dsi.fastutil.bytes.ByteBigArrayBigList;
import java.io.IOException;
import java.io.OutputStream;

public class BigByteArrayOutputStream extends OutputStream {
    private final ByteBigArrayBigList buffer;
    private long size;

    public BigByteArrayOutputStream(long initialCapacity) {
        this.buffer = new ByteBigArrayBigList(initialCapacity);
        this.size = 0;
    }

    @Override
    public void write(int b) throws IOException {
        ensureCapacity(size + 1);
        buffer.add((byte) b);
        size++;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        ensureCapacity(size + len);
        for (int i = 0; i < len; i++) {
            buffer.add(b[off + i]);
            size++;
        }
    }

    private void ensureCapacity(long minCapacity) {
        if (minCapacity > buffer.size64()) {
            buffer.ensureCapacity(minCapacity * 2);
        }
    }

    public long size() {
        return size;
    }

    public byte[] toByteArray() {
        byte[] result = new byte[(int) size];
        for (int i = 0; i < size; i++) {
            result[i] = buffer.getByte(i);
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        buffer.clear();
        size = 0;
    }
}


