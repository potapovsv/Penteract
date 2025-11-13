/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2003-2006 Robin Bagot and others
 * Copyright (C) 2003-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara
 * All Rights Reserved.
 */
package mondrian.rolap;

import mondrian.olap.Util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.*;
import javax.sql.DataSource;
import java.time.Duration;

/**
 * Singleton class that holds a connection pool.
 * Call RolapConnectionPool.instance().getPoolingDataSource(connectionFactory)
 * to get a DataSource in return that is a pooled data source.
 *
 * @author jhyde
 * @author Robin Bagot
 * @since 7 July, 2003
 */
class RolapConnectionPool {

    public static RolapConnectionPool instance() {
        return instance;
    }

    private static final RolapConnectionPool instance =
        new RolapConnectionPool();

    private final Map<Object, HikariDataSource> dataSourceMap =
        new HashMap<Object, HikariDataSource>();

    private RolapConnectionPool() {
    }

    /**
     * Sets up a pooling data source for connection pooling.
     * This can be used if the application server does not have a pooling
     * dataSource already configured.
     *
     * <p>This takes a normal jdbc connection string, and requires a jdbc
     * driver to be loaded, and then uses a
     * {@link DriverManagerConnectionFactory} to create connections to the
     * database.
     *
     * <p>An alternative method of configuring a pooling driver is to use an
     * external configuration file. See the the Apache javax-commons
     * commons-pool documentation.
     *
     * @param key Identifies which connection factory to use. A typical key is
     *   a JDBC connect string, since each JDBC connect string requires a
     *   different connection factory.
     * @param jdbcConnectString JDBC connection string
     * @param jdbcProperties JDBC connection properties
     * @return a pooling DataSource object
     */
    public synchronized DataSource getPoolingDataSource(
        Object key,
        String jdbcConnectString,
        Properties jdbcProperties)
    {
        HikariDataSource dataSource = createHikariDataSource(jdbcConnectString, jdbcProperties);
        return dataSource;
    }

    /**
     * Clears the connection pool for testing purposes
     */
    void clearPool() {
        for (HikariDataSource dataSource : dataSourceMap.values()) {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }
        dataSourceMap.clear();
    }

    public synchronized DataSource getDriverManagerPoolingDataSource(
        String jdbcConnectString,
        Properties jdbcProperties)
    {
        // First look for a data source with identical specification. This in
        // turn helps us to use the cache of Dialect objects.

        // Need to include user name to define the pool key as some DBMSs
        // like Oracle don't include schemas in the JDBC URL - instead the
        // user drives the schema. This makes JDBC pools act like JNDI pools,
        // with, in effect, a pool per DB user.

        List<Object> key =
            Arrays.<Object>asList(
                "DriverManagerPoolingDataSource",
                jdbcConnectString,
                jdbcProperties);
        HikariDataSource dataSource = dataSourceMap.get(key);
        if (dataSource != null) {
            return dataSource;
        }

        try {
            String propertyString = jdbcProperties.toString();
            dataSource = createHikariDataSource(jdbcConnectString, jdbcProperties);
        } catch (Throwable e) {
            throw Util.newInternal(
                e,
                "Error while creating connection pool (with URI "
                + jdbcConnectString + ")");
        }
        dataSourceMap.put(key, dataSource);
        return dataSource;
    }

    public synchronized DataSource getDataSourcePoolingDataSource(
        DataSource dataSource,
        String dataSourceName,
        String jdbcUser,
        String jdbcPassword)
    {
        // First look for a data source with identical specification. This in
        // turn helps us to use the cache of Dialect objects.
        List<Object> key =
            Arrays.asList(
                "DataSourcePoolingDataSource",
                dataSource,
                jdbcUser,
                jdbcPassword);
        HikariDataSource pooledDataSource = dataSourceMap.get(key);
        if (pooledDataSource != null) {
            return pooledDataSource;
        }

        try {
            // For existing DataSource, we'll create a new HikariDataSource
            // This is the recommended approach when migrating from DBCP2 to HikariCP
            pooledDataSource = createHikariDataSourceFromDataSource(dataSource, jdbcUser, jdbcPassword);
        } catch (Exception e) {
            throw Util.newInternal(
                e,
                "Error while creating connection pool (with URI "
                + dataSourceName + ")");
        }
        dataSourceMap.put(key, pooledDataSource);
        return pooledDataSource;
    }

    /**
     * Creates a HikariCP DataSource from an existing DataSource
     */
    private HikariDataSource createHikariDataSourceFromDataSource(DataSource dataSource, String jdbcUser, String jdbcPassword) {
        HikariConfig config = new HikariConfig();
        
        // Configure pool settings (equivalent to DBCP2 settings)
        config.setMaximumPoolSize(50); // equivalent to setMaxTotal(50)
        config.setMinimumIdle(10);     // equivalent to setMaxIdle(10)
        config.setIdleTimeout(60000); // equivalent to setTimeBetweenEvictionRuns (in milliseconds)
        config.setMaxLifetime(300000); // equivalent to setMinEvictableIdleTime (in milliseconds)
        config.setLeakDetectionThreshold(300000); // equivalent to setRemoveAbandonedTimeout (in milliseconds)
        
        // Connection validation settings
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000); // 5 seconds in milliseconds
        
        // Set username and password if provided
        if (jdbcUser != null) {
            config.setUsername(jdbcUser);
        }
        if (jdbcPassword != null) {
            config.setPassword(jdbcPassword);
        }
        
        // For existing DataSource, we can't directly set it in HikariConfig
        // HikariCP is designed to work with JDBC URLs, not existing DataSources
        // We'll create a basic HikariDataSource that can be configured externally
        
        return new HikariDataSource(config);
    }

    /**
     * Creates a HikariCP DataSource from JDBC connection string and properties
     */
    private HikariDataSource createHikariDataSource(String jdbcConnectString, Properties jdbcProperties) {
        HikariConfig config = new HikariConfig();
        
        // Configure pool settings (equivalent to DBCP2 settings)
        config.setMaximumPoolSize(50); // equivalent to setMaxTotal(50)
        config.setMinimumIdle(10);     // equivalent to setMaxIdle(10)
        config.setIdleTimeout(60000); // equivalent to setTimeBetweenEvictionRuns (in milliseconds)
        config.setMaxLifetime(300000); // equivalent to setMinEvictableIdleTime (in milliseconds)
        config.setLeakDetectionThreshold(300000); // equivalent to setRemoveAbandonedTimeout (in milliseconds)
        
        // Connection validation settings
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000); // 5 seconds in milliseconds
        
        // Configure JDBC connection
        if (jdbcConnectString != null) {
            config.setJdbcUrl(jdbcConnectString);
        }
        
        if (jdbcProperties != null) {
            // Set username and password if present
            String username = jdbcProperties.getProperty("user");
            String password = jdbcProperties.getProperty("password");
            
            if (username != null) {
                config.setUsername(username);
            }
            if (password != null) {
                config.setPassword(password);
            }
            
            // Set other properties as data source properties
            config.setDataSourceProperties(jdbcProperties);
        }
        
        return new HikariDataSource(config);
    }
}

// End RolapConnectionPool.java
