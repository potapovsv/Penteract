package mondrian.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class CoreDbLoggingFactory {

    private static HikariDataSource dataSource;
    
    static {
        // Инициализация пула соединений (HikariCP, c3p0 и т.д.)
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:clickhouse://dev-bi-ch02.loymax.local:8123/pentaract_logs?user=adm&amp;password=adm&amp;buffer_size=10000000&amp;compress=1&amp;http_compression=zstd");
        config.setUsername("adm");
        config.setPassword("adm");
        dataSource = new HikariDataSource(config);
    }
    
    public static Connection getDatabaseConnection() throws SQLException {
        return dataSource.getConnection();
    }
}