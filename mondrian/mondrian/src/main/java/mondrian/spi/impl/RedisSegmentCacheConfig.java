/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
 */

package mondrian.spi.impl;

import mondrian.olap.MondrianProperties;

import java.time.Duration;

/**
 * Configuration class for RedisSegmentCache.
 * Loads properties from MondrianProperties and provides validation.
 *
 * @author RedisSegmentCache Implementation Team
 */
public class RedisSegmentCacheConfig {

    // Property prefixes
    private static final String PROPERTY_PREFIX = "mondrian.spi.segmentCache.redis.";

    // Default values
    private static final String DEFAULT_REDIS_URI = "redis://localhost:6379";
    private static final String DEFAULT_KEY_PREFIX = "mondrian:segment:";
    private static final long DEFAULT_TTL_SECONDS = 86400; // 24 hours
    private static final boolean DEFAULT_USE_COMPRESSION = true;
    private static final int DEFAULT_COMPRESSION_LEVEL = 6; // GZIP default
    private static final boolean DEFAULT_USE_KEY_EXPIRATION = true;
    private static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofSeconds(30);
    private static final boolean DEFAULT_AUTO_RECONNECT = true;
    private static final boolean DEFAULT_SUPPORTS_RICH_INDEX = true;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofMillis(100);

    // Configuration fields
    private final String redisUri ;
    private final String keyPrefix;
    private final long ttlSeconds   ;
    private final boolean useCompression ;
    private final int compressionLevel ;
    private final boolean useKeyExpiration;
    private final Duration connectionTimeout;
    private final Duration commandTimeout;

    private final boolean autoReconnect ;
    private final boolean supportsRichIndex ;
    private final int maxRetries;
    private final Duration retryDelay; // 100;

    /**
     * Creates a new configuration instance, loading values from MondrianProperties.
     */
    public RedisSegmentCacheConfig() {
        MondrianProperties props = MondrianProperties.instance();

        this.redisUri =  MondrianProperties.instance().redisUri.get();
        this.keyPrefix =    MondrianProperties.instance().redisKeyPrefix.get();
        this.ttlSeconds = MondrianProperties.instance().redisTtlSeconds.get();
        this.useCompression = MondrianProperties.instance().redisUseCompression.get();
        this.compressionLevel = MondrianProperties.instance().redisCompressionLevel.get();
        this.useKeyExpiration =  MondrianProperties.instance().redisUseKeyExpiration.get();
        this.connectionTimeout =  Duration.ofSeconds(MondrianProperties.instance().redisConnectionTimeout.get());
        this.commandTimeout = Duration.ofSeconds(MondrianProperties.instance().redisConnectionTimeout.get());
        this.autoReconnect = true;
        this.supportsRichIndex = true;
        this.maxRetries =3;
        this.retryDelay  = Duration.ofSeconds(100);

        validate();
    }

    /**
     * Creates a configuration with explicit values (useful for testing).
     */
    public RedisSegmentCacheConfig(
            String redisUri,
            String keyPrefix,
            long ttlSeconds,
            boolean useCompression,
            int compressionLevel,
            boolean useKeyExpiration,
            Duration connectionTimeout,
            Duration commandTimeout,
            boolean autoReconnect,
            boolean supportsRichIndex,
            int maxRetries,
            Duration retryDelay) {
        this.redisUri = redisUri != null ? redisUri : DEFAULT_REDIS_URI;
        this.keyPrefix = keyPrefix != null ? keyPrefix : DEFAULT_KEY_PREFIX;
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
        this.useCompression = useCompression;
        this.compressionLevel = compressionLevel > 0 ? compressionLevel : DEFAULT_COMPRESSION_LEVEL;
        this.useKeyExpiration = useKeyExpiration;
        this.connectionTimeout = connectionTimeout != null ? connectionTimeout : DEFAULT_CONNECTION_TIMEOUT;
        this.commandTimeout = commandTimeout != null ? commandTimeout : DEFAULT_COMMAND_TIMEOUT;
        this.autoReconnect = autoReconnect;
        this.supportsRichIndex = supportsRichIndex;
        this.maxRetries = maxRetries > 0 ? maxRetries : DEFAULT_MAX_RETRIES;
        this.retryDelay = retryDelay != null ? retryDelay : DEFAULT_RETRY_DELAY;

        validate();
    }

    private String getProperty(MondrianProperties props, String name, String defaultValue) {
        String fullName = PROPERTY_PREFIX + name;
        String value = props.getProperty(fullName);
        return value != null && !value.trim().isEmpty() ? value.trim() : defaultValue;
    }

    private boolean getBooleanProperty(MondrianProperties props, String name, boolean defaultValue) {
        String value = getProperty(props, name, Boolean.toString(defaultValue));
        return Boolean.parseBoolean(value);
    }

    private int getIntProperty(MondrianProperties props, String name, int defaultValue) {
        String value = getProperty(props, name, Integer.toString(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private long getLongProperty(MondrianProperties props, String name, long defaultValue) {
        String value = getProperty(props, name, Long.toString(defaultValue));
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void validate() {
        if (redisUri == null || redisUri.trim().isEmpty()) {
            throw new IllegalArgumentException("Redis URI cannot be null or empty");
        }

        if (keyPrefix == null || keyPrefix.trim().isEmpty()) {
            throw new IllegalArgumentException("Key prefix cannot be null or empty");
        }

        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("TTL seconds must be positive");
        }

        if (compressionLevel < 1 || compressionLevel > 9) {
            throw new IllegalArgumentException("Compression level must be between 1 and 9");
        }

        if (maxRetries < 0) {
            throw new IllegalArgumentException("Max retries cannot be negative");
        }

        if (connectionTimeout.isNegative() || connectionTimeout.isZero()) {
            throw new IllegalArgumentException("Connection timeout must be positive");
        }

        if (commandTimeout.isNegative() || commandTimeout.isZero()) {
            throw new IllegalArgumentException("Command timeout must be positive");
        }
    }

    // Getters

    public String getRedisUri() {
        return redisUri;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public boolean isUseCompression() {
        return useCompression;
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    public boolean isUseKeyExpiration() {
        return useKeyExpiration;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public Duration getCommandTimeout() {
        return commandTimeout;
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    public boolean isSupportsRichIndex() {
        return supportsRichIndex;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    @Override
    public String toString() {
        return "RedisSegmentCacheConfig{" +
                "redisUri='" + redisUri + '\'' +
                ", keyPrefix='" + keyPrefix + '\'' +
                ", ttlSeconds=" + ttlSeconds +
                ", useCompression=" + useCompression +
                ", compressionLevel=" + compressionLevel +
                ", useKeyExpiration=" + useKeyExpiration +
                ", connectionTimeout=" + connectionTimeout +
                ", commandTimeout=" + commandTimeout +
                ", autoReconnect=" + autoReconnect +
                ", supportsRichIndex=" + supportsRichIndex +
                ", maxRetries=" + maxRetries +
                ", retryDelay=" + retryDelay +
                '}';
    }
}
