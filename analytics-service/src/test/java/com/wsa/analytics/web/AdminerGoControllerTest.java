package com.wsa.analytics.web;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminerGoControllerTest {

    @Test
    void databaseFromJdbcUrlParsesPathAndQuery() {
        assertEquals("wsa", AdminerGoController.databaseFromJdbcUrl(
                "jdbc:postgresql://127.0.0.1:5432/wsa"));
        assertEquals("analytics", AdminerGoController.databaseFromJdbcUrl(
                "jdbc:postgresql://localhost:5432/analytics?ssl=false"));
        assertEquals("wsa", AdminerGoController.databaseFromJdbcUrl(null));
        assertEquals("wsa", AdminerGoController.databaseFromJdbcUrl("  "));
        assertEquals("wsa", AdminerGoController.databaseFromJdbcUrl("jdbc:postgresql://host:5432/"));
        assertEquals("wsa", AdminerGoController.databaseFromJdbcUrl("no-slash"));
        assertEquals("wsa", AdminerGoController.databaseFromJdbcUrl("jdbc:postgresql://host:5432/?x=1"));
    }

    @Test
    void adminerServerUsesJdbcHostAsFallback() {
        assertEquals("127.0.0.1", AdminerGoController.adminerServerFromJdbcUrl(
                "jdbc:postgresql://127.0.0.1:5432/wsa"));
        assertEquals("postgres", AdminerGoController.adminerServerFromJdbcUrl(
                "jdbc:postgresql://postgres:5432/wsa"));
        assertEquals("db.internal:5433", AdminerGoController.adminerServerFromJdbcUrl(
                "jdbc:postgresql://db.internal:5433/wsa"));
        assertEquals("127.0.0.1", AdminerGoController.adminerServerFromJdbcUrl(null));
        assertEquals("127.0.0.1", AdminerGoController.adminerServerFromJdbcUrl("not-a-jdbc-url"));
        assertEquals("onlyhost", AdminerGoController.adminerServerFromJdbcUrl(
                "jdbc:postgresql://onlyhost/wsa"));
    }

    @Test
    void configJsonUsesExplicitServerAndDatasource() {
        AdminerGoController controller = new AdminerGoController(
                "http://localhost:8082/",
                "wsa-postgres",
                "wsa",
                "change-me",
                "jdbc:postgresql://127.0.0.1:5432/wsa",
                "");

        Map<String, String> cfg = controller.config();

        assertEquals("http://localhost:8082/", cfg.get("url"));
        assertEquals("wsa-postgres", cfg.get("server"));
        assertEquals("wsa", cfg.get("username"));
        assertEquals("change-me", cfg.get("password"));
        assertEquals("wsa", cfg.get("database"));
        assertEquals("pgsql", cfg.get("driver"));
    }

    @Test
    void configFallsBackToJdbcWhenOverridesBlankAndNullPassword() {
        AdminerGoController controller = new AdminerGoController(
                "http://localhost:8082/",
                "  ",
                "wsa",
                null,
                "jdbc:postgresql://pghost:5433/analytics_db",
                null);

        Map<String, String> cfg = controller.config();
        assertEquals("pghost:5433", cfg.get("server"));
        assertEquals("", cfg.get("password"));
        assertEquals("analytics_db", cfg.get("database"));
    }

    @Test
    void configUsesDatabaseOverride() {
        AdminerGoController controller = new AdminerGoController(
                "http://localhost:8082/",
                "explicit-server",
                "u",
                "p",
                "jdbc:postgresql://127.0.0.1:5432/ignored",
                "override_db");
        assertEquals("override_db", controller.config().get("database"));
        assertEquals("explicit-server", controller.config().get("server"));
    }
}
