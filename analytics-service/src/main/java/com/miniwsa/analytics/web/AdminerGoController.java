package com.miniwsa.analytics.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exposes Adminer connection settings from {@code spring.datasource.*} / {@code miniwsa.adminer.*}
 * so the static hub page can POST into Adminer (no HTML in Java).
 */
@RestController
public class AdminerGoController {

    private static final Pattern JDBC_HOST_PORT = Pattern.compile(
            "^jdbc:postgresql://([^:/?]+)(?::(\\d+))?",
            Pattern.CASE_INSENSITIVE);

    private final String adminerUrl;
    private final String server;
    private final String username;
    private final String password;
    private final String database;

    public AdminerGoController(
            @Value("${miniwsa.adminer.url}") String adminerUrl,
            @Value("${miniwsa.adminer.server:}") String serverOverride,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password:}") String password,
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${miniwsa.adminer.database:}") String databaseOverride) {
        this.adminerUrl = adminerUrl;
        this.username = username;
        this.password = password == null ? "" : password;
        this.database = (databaseOverride == null || databaseOverride.isBlank())
                ? databaseFromJdbcUrl(jdbcUrl)
                : databaseOverride;
        this.server = (serverOverride == null || serverOverride.isBlank())
                ? adminerServerFromJdbcUrl(jdbcUrl)
                : serverOverride.trim();
    }

    @GetMapping(value = "/adminer-go/config", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> config() {
        Map<String, String> cfg = new LinkedHashMap<>();
        cfg.put("url", adminerUrl);
        cfg.put("server", server);
        cfg.put("username", username);
        cfg.put("password", password);
        cfg.put("database", database);
        cfg.put("driver", "pgsql");
        return cfg;
    }

    static String adminerServerFromJdbcUrl(String jdbcUrl) {
        Matcher matcher = JDBC_HOST_PORT.matcher(jdbcUrl == null ? "" : jdbcUrl.trim());
        if (!matcher.find()) {
            return "127.0.0.1";
        }
        String host = matcher.group(1);
        String port = matcher.group(2);
        if (port == null || port.isBlank() || "5432".equals(port)) {
            return host;
        }
        return host + ":" + port;
    }

    static String databaseFromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "miniwsa";
        }
        int slash = jdbcUrl.lastIndexOf('/');
        if (slash < 0 || slash == jdbcUrl.length() - 1) {
            return "miniwsa";
        }
        String db = jdbcUrl.substring(slash + 1);
        int query = db.indexOf('?');
        if (query >= 0) {
            db = db.substring(0, query);
        }
        return db.isBlank() ? "miniwsa" : db;
    }
}
