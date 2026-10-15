package com.miniwsa.analytics.web;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminerGoControllerTest {

    @Test
    void databaseFromJdbcUrlParsesPathAndQuery() {
        assertEquals("miniwsa", AdminerGoController.databaseFromJdbcUrl(
                "jdbc:postgresql://127.0.0.1:5432/miniwsa"));
        assertEquals("analytics", AdminerGoController.databaseFromJdbcUrl(
                "jdbc:postgresql://localhost:5432/analytics?ssl=false"));
    }

    @Test
    void adminerServerUsesJdbcHostAsFallback() {
        assertEquals("127.0.0.1", AdminerGoController.adminerServerFromJdbcUrl(
                "jdbc:postgresql://127.0.0.1:5432/miniwsa"));
        assertEquals("postgres", AdminerGoController.adminerServerFromJdbcUrl(
                "jdbc:postgresql://postgres:5432/miniwsa"));
        assertEquals("db.internal:5433", AdminerGoController.adminerServerFromJdbcUrl(
                "jdbc:postgresql://db.internal:5433/miniwsa"));
    }

    @Test
    void configJsonUsesExplicitServerAndDatasource() {
        AdminerGoController controller = new AdminerGoController(
                "http://localhost:8082/",
                "miniwsa-postgres",
                "miniwsa",
                "change-me",
                "jdbc:postgresql://127.0.0.1:5432/miniwsa",
                "");

        Map<String, String> cfg = controller.config();

        assertEquals("http://localhost:8082/", cfg.get("url"));
        assertEquals("miniwsa-postgres", cfg.get("server"));
        assertEquals("miniwsa", cfg.get("username"));
        assertEquals("change-me", cfg.get("password"));
        assertEquals("miniwsa", cfg.get("database"));
        assertEquals("pgsql", cfg.get("driver"));
    }
}
