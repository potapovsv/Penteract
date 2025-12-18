/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
 */

package mondrian.spi.impl;

/**
 * Exception thrown by RedisSegmentCache when cache operations fail.
 * This is a runtime exception to maintain compatibility with Mondrian's
 * SegmentCache interface which does not declare checked exceptions.
 *
 * @author RedisSegmentCache Implementation Team
 */
public class RedisCacheException extends RuntimeException {

    /**
     * Creates a new RedisCacheException with the specified message.
     *
     * @param message The detail message
     */
    public RedisCacheException(String message) {
        super(message);
    }

    /**
     * Creates a new RedisCacheException with the specified message and cause.
     *
     * @param message The detail message
     * @param cause The cause of the exception
     */
    public RedisCacheException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new RedisCacheException with the specified cause.
     *
     * @param cause The cause of the exception
     */
    public RedisCacheException(Throwable cause) {
        super(cause);
    }
}
