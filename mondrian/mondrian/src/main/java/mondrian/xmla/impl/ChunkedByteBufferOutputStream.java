package mondrian.xmla.impl;

import java.io.OutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Replacement for ByteArrayOutputStream that avoids a single huge array
 * allocation and eliminates double-copying when calling toByteArray().
 */
public final class ChunkedByteBufferOutputStream extends OutputStream {

    private static final int DEFAULT_CHUNK_SIZE = 64 * 1024;

    private final int chunkSize;
    private final List<byte[]> chunks = new ArrayList<>();
    private byte[] current;
    private int currentPos = 0;
    private long size = 0;

    public ChunkedByteBufferOutputStream() {
        this(DEFAULT_CHUNK_SIZE);
    }

    public ChunkedByteBufferOutputStream(int chunkSize) {
        this.chunkSize = chunkSize;
        this.current = new byte[chunkSize];
        this.chunks.add(current);
    }

    @Override
    public void write(int b) {
        if (currentPos == chunkSize) {
            allocateChunk();
        }
        current[currentPos++] = (byte) b;
        size++;
    }

    @Override
    public void write(byte[] b, int off, int len) {
        while (len > 0) {
            int space = chunkSize - currentPos;
            if (space == 0) {
                allocateChunk();
                space = chunkSize;
            }
            int toWrite = Math.min(len, space);
            System.arraycopy(b, off, current, currentPos, toWrite);

            off += toWrite;
            len -= toWrite;
            currentPos += toWrite;
            size += toWrite;
        }
    }

    private void allocateChunk() {
        current = new byte[chunkSize];
        chunks.add(current);
        currentPos = 0;
    }

    public long size() {
        return size;
    }

    /** Returns all chunks for streaming without merging into a single array */
    public List<byte[]> getChunks() {
        return chunks;
    }

    /** Merge into single byte[] if needed */
    public byte[] toByteArray() {
        byte[] result = new byte[(int) size];
        int pos = 0;

        for (int i = 0; i < chunks.size(); i++) {
            byte[] chunk = chunks.get(i);
            int len = (i == chunks.size() - 1) ? currentPos : chunkSize;
            System.arraycopy(chunk, 0, result, pos, len);
            pos += len;
        }

        return result;
    }

    @Override
    public void flush() throws IOException {
        // Nothing to flush — pure memory buffer
    }

    @Override
    public void close() throws IOException {
        // Nothing to close
    }
}
