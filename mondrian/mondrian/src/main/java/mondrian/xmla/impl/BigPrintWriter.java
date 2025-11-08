package mondrian.xmla.impl;

import it.unimi.dsi.fastutil.bytes.ByteBigArrayBigList;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class BigPrintWriter extends Writer {
    private final ByteBigArrayBigList buffer;
    private final Charset charset;
    private boolean autoFlush;

    public BigPrintWriter() {
        this(StandardCharsets.UTF_8, false);
    }

    public BigPrintWriter(Charset charset, boolean autoFlush) {
        this.buffer = new ByteBigArrayBigList();
        this.charset = charset;
        this.autoFlush = autoFlush;
    }

    public void print(String s) {
        if (s == null) s = "null";
        byte[] bytes = s.getBytes(charset);
        buffer.ensureCapacity(buffer.size64() + bytes.length); // Резервируем место
        for (byte b : bytes) {
            buffer.add(b); // Добавляем байт за байтом
        }
            if (autoFlush) flush();
    }

    public void println(String s) {
        print(s);
        print("\n");
    }

    public void println() {
        print("\n");
    }

    public void print(char c) {
        print(String.valueOf(c));
    }

    public void print(int i) {
        print(Integer.toString(i));
    }

    public void print(long l) {
        print(Long.toString(l));
    }

    public void print(double d) {
        print(Double.toString(d));
    }

    public void print(Object obj) {
        print(String.valueOf(obj));
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        print(new String(cbuf, off, len));
    }

    @Override
    public void flush() {
        // Заглушка (необязательно)
    }

    @Override
    public void close() {
        buffer.clear();
    }

    public long size() {
        return buffer.size64();
    }

    public byte[] toByteArray() {
        if (size() > Integer.MAX_VALUE) {
            throw new IllegalStateException("Данные слишком большие для toByteArray()");
        }
        return buffer.toByteArray();
    }

    public void writeTo(OutputStream out) throws IOException {
        for (long i = 0; i < buffer.size64(); i++) {
            out.write(buffer.getByte(i));
        }
    }
}
