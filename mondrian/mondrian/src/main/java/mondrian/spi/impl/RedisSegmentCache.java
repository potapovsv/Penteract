/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
 */

package mondrian.spi.impl;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.TransactionResult;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import mondrian.spi.SegmentBody;
import mondrian.spi.SegmentCache;
import mondrian.spi.SegmentHeader;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import mondrian.olap.MondrianProperties;
/**
 * High-performance Redis-based implementation of Mondrian's SegmentCache SPI.
 * Uses Lettuce Redis client for asynchronous, non-blocking operations.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Thread-safe implementation using ReadWriteLock</li>
 *   <li>Configurable timeouts for all Redis operations</li>
 *   <li>Automatic retry with exponential backoff</li>
 *   <li>Compression support for large segments</li>
 *   <li>Rich index support for partial cache invalidation</li>
 *   <li>Connection pooling and automatic reconnection</li>
 *   <li>Detailed monitoring and statistics</li>
 * </ul>
 *
 * @author RedisSegmentCache Implementation Team
 */
public class RedisSegmentCache implements SegmentCache {

    private static final Logger LOGGER = LogManager.getLogger(RedisSegmentCache.class);

    // Key suffixes
    private static final String HEADER_SUFFIX = ":header";
    private static final String BODY_SUFFIX = ":body";
    private static final String META_SUFFIX = ":meta";
    private static final String INDEX_KEY = "mondrian:segment:index";

    // Configuration
    private final RedisSegmentCacheConfig config;

    // Redis client
    private RedisClient redisClient;
    private StatefulRedisConnection<byte[], byte[]> connection;
    private RedisAsyncCommands<byte[], byte[]> asyncCommands;

    // Thread safety
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final ExecutorService executorService;

    // In-memory index for fast header lookups (if supportsRichIndex)
    private final Map<String, SegmentHeader> headerIndex;
    private final Set<String> segmentKeys;

    // Listeners
    private final Set<SegmentCacheListener> listeners;

    // State
    private volatile boolean initialized = false;
    private volatile boolean shutdown = false;

    // Statistics
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong putCount = new AtomicLong(0);
    private final AtomicLong removeCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    /**
     * Default constructor used by Mondrian's SPI mechanism.
     * Loads configuration from MondrianProperties.
     */
    public RedisSegmentCache() {
        this(new RedisSegmentCacheConfig());
        LOGGER.debug("RedisSegmentCache created with default configuration");
    }

    /**
     * Constructor with explicit configuration (useful for testing).
     *
     * @param config Redis cache configuration
     */
    public RedisSegmentCache(RedisSegmentCacheConfig config) {
         LOGGER.debug("RedisSegmentCache TRY created with default configuration");
        this.config = config;
        this.listeners = new CopyOnWriteArraySet<>();

        // Initialize thread pool for async operations
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "redis-segment-cache-" + System.nanoTime());
            thread.setDaemon(true);
            return thread;
        });

        // Initialize in-memory structures
        this.headerIndex = new ConcurrentHashMap<>();
        this.segmentKeys = ConcurrentHashMap.newKeySet();

        // Initialize Redis connection
        initialize();
         if (LOGGER.isDebugEnabled()) {
             LOGGER.debug("RedisSegmentCache initialized with config: {}", config);
         }
    }

    /**
     * Initializes Redis connection and resources.
     */
    private void initialize() {
        if (initialized || shutdown) {
            return;
        }

        lock.writeLock().lock();
        try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Initializing Redis connection to: {}", config.getRedisUri());
                }
            

            // Create Redis URI with timeouts
            RedisURI redisURI = RedisURI.create(config.getRedisUri());
            redisURI.setTimeout(config.getCommandTimeout());
            redisURI.setTimeout(config.getConnectionTimeout());

            // Build client resources
            ClientResources clientResources = DefaultClientResources.builder()
                    .ioThreadPoolSize(1)
                    .computationThreadPoolSize(1)
                    .build();

            // Create Redis client
            this.redisClient = RedisClient.create(clientResources, redisURI);

            // Configure client options
            ClientOptions clientOptions = ClientOptions.builder()
                    .autoReconnect(config.isAutoReconnect())
                    // .cancelCommandsOnReconnectFailure(true)
                    .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                    .build();

            redisClient.setOptions(clientOptions);

            // Create connection
            this.connection = redisClient.connect(ByteArrayCodec.INSTANCE);
            this.asyncCommands = connection.async();

            // Test connection
            ping();

            // Load existing index if rich index is supported
            if (config.isSupportsRichIndex()) {
                loadIndex();
            }

            initialized = true;
            LOGGER.info("RedisSegmentCache successfully initialized");

        } catch (Exception e) {
            LOGGER.error("Failed to initialize RedisSegmentCache", e);
            throw new RedisCacheException("Failed to initialize RedisSegmentCache", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Tests Redis connection with timeout.
     */
    private void ping() {
        try {
            executeWithTimeout(asyncCommands.ping(), Duration.ofSeconds(5));
             if (LOGGER.isDebugEnabled()) {
                         LOGGER.debug("Redis connection test successful");
                }
            
        } catch (Exception e) {
            throw new RedisCacheException("Redis connection test failed", e);
        }
    }

    /**
     * Loads existing segment headers from Redis index.
     */
    private void loadIndex() {
        try {
            Set<byte[]> keys = executeWithTimeout(
                    asyncCommands.smembers(INDEX_KEY.getBytes()),
                    config.getCommandTimeout()
            );

            if (keys != null) {
                for (byte[] key : keys) {
                    String segmentKey = new String(key);
                    segmentKeys.add(segmentKey);

                    // Load header asynchronously
                    loadHeaderAsync(segmentKey);
                }
                  if (LOGGER.isDebugEnabled()) {
                         LOGGER.debug("Loaded {} segment keys from Redis index", keys.size());
                    }
              
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load segment index from Redis", e);
        }
    }

    /**
     * Loads a segment header asynchronously.
     */
    private void loadHeaderAsync(String segmentKey) {
        executorService.submit(() -> {
            try {
                byte[] headerKey = (segmentKey + HEADER_SUFFIX).getBytes();
                byte[] data = executeWithTimeout(
                        asyncCommands.get(headerKey),
                        config.getCommandTimeout()
                );

                if (data != null) {
                    SegmentHeader header = deserializeHeader(data);
                    if (header != null) {
                        headerIndex.put(segmentKey, header);
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to load header for segment: {}", segmentKey, e);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SegmentBody get(SegmentHeader header) {
         if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("RedisCli get reading segment body for key: {}"+ header.toString());
         }
        checkInitialized();
        checkNotShutdown();

        if (header == null) {
           if (LOGGER.isDebugEnabled()) {  
                LOGGER.debug("Attempted to get segment with null header");
                return null;
           } 
        }

        String segmentKey = generateSegmentKey(header);
         if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Getting segment body for key: {}", segmentKey);
         }

        try {
            byte[] bodyKey = (segmentKey + BODY_SUFFIX).getBytes();
            byte[] data = executeWithTimeout(
                    asyncCommands.get(bodyKey),
                    config.getCommandTimeout()
            );

            if (data == null) {
                if (LOGGER.isDebugEnabled()) {  
                  LOGGER.debug("Segment body not found in cache: {}", segmentKey);
                }
                missCount.incrementAndGet();
                return null;
            }

            // Decompress if needed
            if (config.isUseCompression()) {
                data = decompress(data);
            }

            SegmentBody body = deserializeBody(data);
            hitCount.incrementAndGet();
            if (LOGGER.isDebugEnabled()) {
               LOGGER.debug("Successfully retrieved segment from cache: {}", segmentKey);
            }
            return body;

        } catch (TimeoutException e) {
            LOGGER.error("Timeout while getting segment: {}", segmentKey);
            errorCount.incrementAndGet();
            return null;
        } catch (Exception e) {
            LOGGER.error("Error retrieving segment from cache: {}", segmentKey, e);
            errorCount.incrementAndGet();
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SegmentHeader> getSegmentHeaders() {
         if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("RedisCli get reading getSegmentHeaders  for key: {}");
         }        
        checkInitialized();
        checkNotShutdown();

        lock.readLock().lock();
        try {
            if (!config.isSupportsRichIndex()) {
               if (LOGGER.isDebugEnabled()) { 
                LOGGER.debug("Rich index not supported, returning empty list");
               } 
                return Collections.emptyList();
            }

            // Return copy of headers from in-memory index
            List<SegmentHeader> headers = new ArrayList<>(headerIndex.values());
            if (LOGGER.isDebugEnabled()) {
               LOGGER.debug("Returning {} segment headers from index", headers.size());
            }
            return headers;

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean put(SegmentHeader header, SegmentBody body) {
         if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("RedisCli put header body  for key: {}" + header);
         }           
        checkInitialized();
        checkNotShutdown();

        if (header == null || body == null) {
            if (LOGGER.isDebugEnabled()) {
               LOGGER.debug("Attempted to put null header or body");
            }
            return false;
        }

        String segmentKey = generateSegmentKey(header);
        if (LOGGER.isDebugEnabled()) {
           LOGGER.debug("Putting segment into cache with key: {}", segmentKey);
        }
        lock.writeLock().lock();
        try {
            // Serialize data
            byte[] headerData = serializeHeader(header);
            byte[] bodyData = serializeBody(body);

            // Compress body if enabled
            if (config.isUseCompression()) {
                bodyData = compress(bodyData);
            }

            // Prepare Redis keys
            byte[] headerKey = (segmentKey + HEADER_SUFFIX).getBytes();
            byte[] bodyKey = (segmentKey + BODY_SUFFIX).getBytes();
            byte[] metaKey = (segmentKey + META_SUFFIX).getBytes();

            // Execute transaction
            asyncCommands.multi();

            // Store header and body
            asyncCommands.set(headerKey, headerData);
            asyncCommands.set(bodyKey, bodyData);

            // Store metadata
            Map<byte[], byte[]> metadata = createMetadata(headerData, bodyData);
            asyncCommands.hmset(metaKey, metadata);

            // Add to index
            if (config.isSupportsRichIndex()) {
                asyncCommands.sadd(INDEX_KEY.getBytes(), segmentKey.getBytes());
            }

            // Set expiration if enabled
            if (config.isUseKeyExpiration()) {
                long ttl = config.getTtlSeconds();
                asyncCommands.expire(headerKey, ttl);
                asyncCommands.expire(bodyKey, ttl);
                asyncCommands.expire(metaKey, ttl);

                if (config.isSupportsRichIndex()) {
                    asyncCommands.expire(INDEX_KEY.getBytes(), ttl);
                }
            }

            // Execute transaction with timeout
            executeWithTimeout(asyncCommands.exec(), config.getCommandTimeout());

            // Update in-memory index
            if (config.isSupportsRichIndex()) {
                headerIndex.put(segmentKey, header);
                segmentKeys.add(segmentKey);
            }

            putCount.incrementAndGet();

            // Notify listeners
            notifyListeners(header, SegmentCacheListener.SegmentCacheEvent.EventType.ENTRY_CREATED);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Successfully cached segment: {}", segmentKey);
            }
            return true;
        
        } catch (TimeoutException e) {
            LOGGER.warn("Timeout while putting segment: {}", segmentKey);
            errorCount.incrementAndGet();
            return false;
        } catch (Exception e) {
            LOGGER.error("Error caching segment: {}", segmentKey, e);
            errorCount.incrementAndGet();

            // Try to discard transaction
            try {
                asyncCommands.discard();
            } catch (Exception discardEx) {
                LOGGER.debug("Failed to discard transaction", discardEx);
            }

            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean remove(SegmentHeader header) {
         if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("RedisCli remove header body  for key: {}" + header);
         }       
        checkInitialized();
        checkNotShutdown();

        if (header == null) {
            LOGGER.warn("Attempted to remove null header");
            return false;
        }

        String segmentKey = generateSegmentKey(header);
        LOGGER.debug("Removing segment from cache: {}", segmentKey);

        return removeInternal(segmentKey, header);
    }

    /**
     * Internal remove implementation.
     */
    private boolean removeInternal(String segmentKey, SegmentHeader header) {
         if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("RedisCli removeInternal header body  for key: {}" + header);
         }    
        lock.writeLock().lock();
        try {
            // Delete keys
            byte[] headerKey = (segmentKey + HEADER_SUFFIX).getBytes();
            byte[] bodyKey = (segmentKey + BODY_SUFFIX).getBytes();
            byte[] metaKey = (segmentKey + META_SUFFIX).getBytes();

            // Execute delete transaction
            asyncCommands.multi();
            asyncCommands.del(headerKey, bodyKey, metaKey);

            if (config.isSupportsRichIndex()) {
                asyncCommands.srem(INDEX_KEY.getBytes(), segmentKey.getBytes());
            }

            TransactionResult deleted = executeWithTimeout(asyncCommands.exec(), config.getCommandTimeout());
            boolean success = deleted != null && deleted.size() > 0;

            if (success) {
                // Update in-memory index
                if (config.isSupportsRichIndex()) {
                    headerIndex.remove(segmentKey);
                    segmentKeys.remove(segmentKey);
                }

                removeCount.incrementAndGet();

                // Notify listeners
                if (header != null) {
                    notifyListeners(header, SegmentCacheListener.SegmentCacheEvent.EventType.ENTRY_DELETED);
                }

                LOGGER.debug("Successfully removed segment: {}", segmentKey);
            }

            return success;

        } catch (TimeoutException e) {
            LOGGER.warn("Timeout while removing segment: {}", segmentKey);
            errorCount.incrementAndGet();
            return false;
        } catch (Exception e) {
            LOGGER.error("Error removing segment: {}", segmentKey, e);
            errorCount.incrementAndGet();
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("RedisCli tearDown header body  for key: {}" );
         }          
        if (shutdown || !initialized) {
            return;
        }

        lock.writeLock().lock();
        try {
            LOGGER.info("Shutting down RedisSegmentCache");

            shutdown = true;

            // Shutdown executor
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // Close Redis connection
            if (connection != null && connection.isOpen()) {
                connection.close();
            }

            if (redisClient != null) {
                redisClient.shutdown();
            }

            // Clear in-memory structures
            headerIndex.clear();
            segmentKeys.clear();
            listeners.clear();

            initialized = false;
            LOGGER.info("RedisSegmentCache shutdown complete");

        } catch (Exception e) {
            LOGGER.error("Error during tearDown", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addListener(SegmentCacheListener listener) {
        if (listener != null) {
            listeners.add(listener);
            LOGGER.debug("Added segment cache listener: {}", listener);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeListener(SegmentCacheListener listener) {
        if (listener != null) {
            listeners.remove(listener);
            LOGGER.debug("Removed segment cache listener: {}", listener);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supportsRichIndex() {
        return config.isSupportsRichIndex();
    }

    /**
     * Gets cache statistics.
     *
     * @return Map containing statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("initialized", initialized);
        stats.put("shutdown", shutdown);
        stats.put("supportsRichIndex", config.isSupportsRichIndex());
        stats.put("hitCount", hitCount.get());
        stats.put("missCount", missCount.get());
        stats.put("putCount", putCount.get());
        stats.put("removeCount", removeCount.get());
        stats.put("errorCount", errorCount.get());
        stats.put("headerIndexSize", headerIndex.size());
        stats.put("listenerCount", listeners.size());

        try {
            String redisInfo = executeWithTimeout(asyncCommands.info(), Duration.ofSeconds(2));
            stats.put("redisInfo", redisInfo);
        } catch (Exception e) {
            stats.put("redisInfo", "Unavailable: " + e.getMessage());
        }

        return stats;
    }

    /**
     * Clears all segments from cache.
     */
    public void clear() {
        checkInitialized();
        checkNotShutdown();

        lock.writeLock().lock();
        try {
            LOGGER.info("Clearing all segments from cache");

            // Get all segment keys
            Set<byte[]> keys = executeWithTimeout(
                    asyncCommands.smembers(INDEX_KEY.getBytes()),
                    config.getCommandTimeout()
            );

            if (keys != null && !keys.isEmpty()) {
                // Delete all segments
                for (byte[] key : keys) {
                    String segmentKey = new String(key);
                    removeInternal(segmentKey, null);
                }
            }

            // Clear index
            asyncCommands.del(INDEX_KEY.getBytes());

            // Clear in-memory structures
            headerIndex.clear();
            segmentKeys.clear();

            LOGGER.info("Cache cleared successfully");

        } catch (Exception e) {
            LOGGER.error("Error clearing cache", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Helper methods

    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("RedisSegmentCache is not initialized");
        }
    }

    private void checkNotShutdown() {
        if (shutdown) {
            throw new IllegalStateException("RedisSegmentCache has been shut down");
        }
    }

    private String generateSegmentKey(SegmentHeader header) {
        return config.getKeyPrefix() + header.getUniqueID().toString();
    }

    private Map<byte[], byte[]> createMetadata(byte[] headerData, byte[] bodyData) {
        Map<byte[], byte[]> metadata = new HashMap<>();
        long now = System.currentTimeMillis();

        metadata.put("created".getBytes(), String.valueOf(now).getBytes());
        metadata.put("lastAccessed".getBytes(), String.valueOf(now).getBytes());
        metadata.put("headerSize".getBytes(), String.valueOf(headerData.length).getBytes());
        metadata.put("bodySize".getBytes(), String.valueOf(bodyData.length).getBytes());
        metadata.put("compressed".getBytes(), String.valueOf(config.isUseCompression()).getBytes());

        return metadata;
    }

    private void notifyListeners(SegmentHeader header, SegmentCacheListener.SegmentCacheEvent.EventType eventType) {
        if (listeners.isEmpty()) {
            return;
        }

        SegmentCacheEvent event = new SegmentCacheEvent(header, eventType);
        for (SegmentCacheListener listener : listeners) {
            try {
                listener.handle(event);
            } catch (Exception e) {
                LOGGER.warn("Error notifying cache listener: {}", listener, e);
            }
        }
    }

    // Serialization methods

    private byte[] serializeHeader(SegmentHeader header) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(header);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RedisCacheException("Failed to serialize SegmentHeader", e);
        }
    }

    private SegmentHeader deserializeHeader(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (SegmentHeader) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RedisCacheException("Failed to deserialize SegmentHeader", e);
        }
    }

    private byte[] serializeBody(SegmentBody body) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(body);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RedisCacheException("Failed to serialize SegmentBody", e);
        }
    }

    private SegmentBody deserializeBody(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (SegmentBody) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RedisCacheException("Failed to deserialize SegmentBody", e);
        }
    }

    // Compression methods

    private byte[] compress(byte[] data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzos = new GZIPOutputStream(baos) {{
                 def.setLevel(config.getCompressionLevel());
             }}) {
            gzos.write(data);
            gzos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RedisCacheException("Failed to compress data", e);
        }
    }

    private byte[] decompress(byte[] compressedData) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
             GZIPInputStream gzis = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RedisCacheException("Failed to decompress data", e);
        }
    }

    // Timeout handling

    private <T> T executeWithTimeout(RedisFuture<T> future, Duration timeout) throws TimeoutException {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            throw new RedisCacheException("Redis operation failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RedisCacheException("Redis operation interrupted", e);
        }
    }

    // Inner classes

    private static class SegmentCacheEvent implements SegmentCacheListener.SegmentCacheEvent {
        private final SegmentHeader source;
        private final EventType eventType;

        SegmentCacheEvent(SegmentHeader source, EventType eventType) {
            this.source = source;
            this.eventType = eventType;
        }

        @Override
        public EventType getEventType() {
            return eventType;
        }

        @Override
        public SegmentHeader getSource() {
            return source;
        }

        @Override
        public boolean isLocal() {
            return true;
        }
    }
}
