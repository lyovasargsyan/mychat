package com.classchat.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DatabaseConfigTest {
    @Test
    void convertsRenderConnectionStringToJdbcUrlWithoutEmbeddingCredentials() {
        String connectionString =
                "postgresql://classchat:secret%40password@db.internal:5432/classchat?sslmode=require";

        assertEquals(
                "jdbc:postgresql://db.internal:5432/classchat?sslmode=require",
                DatabaseConfig.toJdbcUrl(connectionString));
    }

    @Test
    void leavesExistingJdbcUrlsUnchanged() {
        String jdbcUrl = "jdbc:postgresql://localhost:5432/classchat";

        assertEquals(jdbcUrl, DatabaseConfig.toJdbcUrl(jdbcUrl));
    }
}
