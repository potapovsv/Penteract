package mondrian.xmla.impl;

import it.unimi.dsi.fastutil.bytes.ByteBigArrayBigList;
import mondrian.olap.Util;
import mondrian.tui.XmlaSupport;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AdaptiveBigByteArrayOutputStream extends OutputStream {
     private static final Logger LOGGER = LogManager.getLogger( AdaptiveBigByteArrayOutputStream.class );
    // ===== ПУЛ ДЛЯ ПЕРЕИСПОЛЬЗОВАНИЯ =====
    private static final int POOL_SIZE = 10;
    private static final ConcurrentLinkedQueue<ByteBigArrayBigList> POOL = new ConcurrentLinkedQueue<>();
    
    // ===== ИСТОРИЯ РАЗМЕРОВ =====
    private static final int HISTORY_SIZE = 100;
    private static final long[] SIZE_HISTORY = new long[HISTORY_SIZE];
    private static int historyIndex = 0;
    
    // ===== ПАРАМЕТРЫ (через -D) =====
    private static final long DEFAULT_INITIAL_SIZE = Long.getLong(
        "mondrian.xmla.buffer.initialSize", 
        512_000_000L  // 50MB
    );
    private static final double GROW_FACTOR = Double.parseDouble(
        System.getProperty("mondrian.xmla.buffer.growFactor", "1.5")
    );
    private static final long MAX_SIZE = Long.getLong(
        "mondrian.xmla.buffer.maxSize", 
        1_512_000_000L  // 1.5GB
    );
    private static final double POOL_MAX_RATIO = Double.parseDouble(
        System.getProperty("mondrian.xmla.buffer.poolMaxRatio", "0.5")
    );
    
    // ===== ЭКЗЕМПЛЯРНЫЕ ПЕРЕМЕННЫЕ =====
    private ByteBigArrayBigList buffer;
    private long size = 0;
    
    // ===== КОНСТРУКТОР (адаптивный) =====
    public AdaptiveBigByteArrayOutputStream() {
        ByteBigArrayBigList pooled = POOL.poll();
        if (pooled != null) {
            this.buffer = pooled;
            this.buffer.clear();
            this.size = 0;
            return;
        }
        
        long initialSize = getRecommendedSize();
        if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("AdaptiveBigByteArrayOutputStream: initialSize = " + initialSize);
                // LOGGER.debug("XmlaHandler.process: sizeresponse " + response.getWriter(). );
            }       
             
        this.buffer = new ByteBigArrayBigList(initialSize);
        buffer.ensureCapacity(initialSize);
        this.size = 0;
      if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("AdaptiveBigByteArrayOutputStream: initialSize64 = " + buffer.size64());
                // LOGGER.debug("XmlaHandler.process: sizeresponse " + response.getWriter(). );
            }           
    }
    
    // ===== СОВМЕСТИМОСТЬ =====
    public AdaptiveBigByteArrayOutputStream(long initialCapacity) {
        this();  // Игнорируем аргумент
    }
    
    // ===== РЕКОМЕНДАЦИЯ РАЗМЕРА =====
    private long getRecommendedSize() {
        if (historyIndex == 0) {
            return DEFAULT_INITIAL_SIZE;
        }
        
        int count = Math.min(historyIndex, HISTORY_SIZE);
        long[] sorted = Arrays.copyOf(SIZE_HISTORY, count);
        Arrays.sort(sorted);
        
        int p95Index = Math.max(0, (int) (sorted.length * 0.95) - 1);
        long p95Size = sorted[p95Index];
        
        return Math.min(
            Math.max(p95Size, DEFAULT_INITIAL_SIZE), 
            MAX_SIZE / 2
        );
    }
    
    // ===== GROW 1.5x =====
    private void ensureCapacity(long minCapacity) {
        if (minCapacity <= buffer.size64()) {
            return;
        }
        
        if (minCapacity > MAX_SIZE) {
            throw new OutOfMemoryError("XML/A buffer exceeds " + MAX_SIZE + " bytes");
        }
        
        long currentCapacity = buffer.size64();
        if (LOGGER.isDebugEnabled()) {
              
                LOGGER.debug("AdaptiveBigByteArrayOutputStream: currentCapacity = " + currentCapacity + "buffer.size64(): " + buffer.size64() + "minCapacity: " + minCapacity);
                // LOGGER.debug("XmlaHandler.process: sizeresponse " + response.getWriter(). );
            }          
        long newCapacity = (long) (currentCapacity * GROW_FACTOR);
        newCapacity = Math.max(newCapacity, minCapacity);
        newCapacity = Math.min(newCapacity, MAX_SIZE);
       
        buffer.ensureCapacity(newCapacity);
        if (LOGGER.isDebugEnabled()) {
              
                LOGGER.debug("AdaptiveBigByteArrayOutputStream: GrowSize = " + newCapacity);
                // LOGGER.debug("XmlaHandler.process: sizeresponse " + response.getWriter(). );
            }    
    }
    
    // ===== WRITE (int) =====
    @Override
    public void write(int b) throws IOException {
        ensureCapacity(size + 1);
        buffer.add((byte) b);
        size++;
    }
    
    // ===== WRITE (массив) =====
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (b == null) throw new NullPointerException();
        if (off < 0 || len < 0 || off + len > b.length) 
            throw new IndexOutOfBoundsException();
        if (len == 0) return;
        
        ensureCapacity(size + len);
        
        // Fastutil не имеет bulk-операций — цикл
        for (int i = 0; i < len; i++) {
            buffer.add(b[off + i]);
        }
        
        size += len;
    }
    
    // ===== SIZE =====
    public long size() {
        return size;
    }
    
    // ===== TO BYTE ARRAY =====
    public byte[] toByteArray() {
        if (size > Integer.MAX_VALUE) {
            throw new OutOfMemoryError("Cannot allocate byte[] > 2GB");
        }
        
        byte[] result = new byte[(int) size];
        
        // Цикл — единственный способ с fastutil
        for (int i = 0; i < size; i++) {
            result[i] = buffer.getByte(i);
        }
        
        return result;
    }
    
    // ===== CLOSE (с возвратом в пул) =====
    @Override
    public void close() throws IOException {
        // Сохраняем в историю
        SIZE_HISTORY[historyIndex % HISTORY_SIZE] = size;
        historyIndex++;
       if (LOGGER.isDebugEnabled()) {
              
                LOGGER.debug("AdaptiveBigByteArrayOutputStream: CloseBuffer historyIndex = " + historyIndex);
                // LOGGER.debug("XmlaHandler.process: sizeresponse " + response.getWriter(). );
            }        
        // Возвращаем в пул если размер адекватный
        if (size < MAX_SIZE * POOL_MAX_RATIO && POOL.size() < POOL_SIZE) {
            buffer.clear();
            POOL.offer(buffer);
        }
        
        size = 0;
        buffer = null;  // Помогаем GC
        
        super.close();
    }
}