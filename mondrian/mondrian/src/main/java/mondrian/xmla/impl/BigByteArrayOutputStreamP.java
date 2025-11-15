package mondrian.xmla.impl;

import it.unimi.dsi.fastutil.BigArrays;
import it.unimi.dsi.fastutil.bytes.ByteBigArrayBigList;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BigByteArrayOutputStreamP extends OutputStream {
    private final ByteBigArrayBigList buffer;
    private long size;
    private final long CHUNK_SIZE =  10*1024 * 1024L; // 1МБ чанк
    private long currentCapacity;

  private static final Logger LOGGER = LogManager.getLogger( BigByteArrayOutputStreamP.class );
    public BigByteArrayOutputStreamP(long initialCapacity) {
        this.buffer = new ByteBigArrayBigList(CHUNK_SIZE);
        this.size = 0;
        this.currentCapacity = CHUNK_SIZE;
        if (LOGGER.isDebugEnabled() && size % 10000000 == 0) {
                    LOGGER.debug("AdaptiveBigByteArrayOutputStreamP NEW INIT : capacity: " + currentCapacity + " Size: " + buffer.size64());
        } 
    }

    @Override
    public void write(int b) throws IOException {
        // ensureCapacity(size + 1);
        if (buffer.size64() >= currentCapacity) {
                    currentCapacity += CHUNK_SIZE;
                    buffer.ensureCapacity(currentCapacity); // Расширяем ЗАРАНЕЕ
                if (LOGGER.isDebugEnabled() && size % 10000000 == 0) {
                    LOGGER.debug("AdaptiveBigByteArrayOutputStreamP: capacity: " + currentCapacity + " Size: " + buffer.size64());
                }                     
        }        
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
        if (buffer.size64() >= currentCapacity) {
                    currentCapacity += CHUNK_SIZE;
                    buffer.ensureCapacity(currentCapacity); // Расширяем ЗАРАНЕЕ
                if (LOGGER.isDebugEnabled() && size % 10000000 == 0) {
                    LOGGER.debug("AdaptiveBigByteArrayOutputStreamP: capacity: " + currentCapacity + " Size: " + buffer.size64());
                }                     
        }
        //ensureCapacity(size + len);
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
        byte[] result = new byte[(int) buffer.size64()];
        BigArrays.copyFromBig(buffer.elements(), 0, result, 0,(int) buffer.size64());
        
        // byte[] result = new byte[(int) size];
        // for (int i = 0; i < size; i++) {
        //     result[i] = buffer.getByte(i);
        // }
        // result = buffer.toArray();
        return result;
    }

    @Override
    public void close() throws IOException {
        buffer.clear();
        size = 0;
    }
}


