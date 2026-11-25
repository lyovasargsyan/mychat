package com.classchat.config;

import java.net.URI;
import javax.sql.DataSource;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DatabaseConfig {
    @Bean
    DataSource dataSource(DataSourceProperties properties) {
        properties.setUrl(toJdbcUrl(properties.determineUrl()));
        return properties.initializeDataSourceBuilder().build();
    }

    static String toJdbcUrl(String databaseUrl) {
        if (databaseUrl == null || !databaseUrl.startsWith("postgresql://")) {
            return databaseUrl;
        }

        URI uri = URI.create(databaseUrl);
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("DATABASE_URL must include a PostgreSQL host");
        }

        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if (uri.getPort() != -1) {
            jdbcUrl.append(':').append(uri.getPort());
        }
        if (uri.getRawPath() != null) {
            jdbcUrl.append(uri.getRawPath());
        }
        if (uri.getRawQuery() != null) {
            jdbcUrl.append('?').append(uri.getRawQuery());
        }
        return jdbcUrl.toString();
    }
}
