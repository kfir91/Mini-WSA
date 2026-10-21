package com.wsa.analytics.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exposes Adminer connection settings (non-secret) from {@code spring.datasource.*} /
 * {@code wsa.adminer.*} so the client React hub can open Adminer. The DB password is not
 * returned — Adminer's Compose PHP autologin supplies it inside the container.
 */
@RestController
public class AdminerGoController {

    private static final Pattern JDBC_HOST_PORT = Pattern.compile(
            "^jdbc:postgresql://([^:/?]+)(?::(\\d+))?",
            Pattern.CASE_INSENSITIVE);

    private final String adminerUrl;
    private final String server;
    private final String username;
    private final String database;

    public AdminerGoController(
            @Value("${wsa.adminer.url}") String adminerUrl,
            @Value("${wsa.adminer.server:}") String serverOverride,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${wsa.adminer.database:}") String databaseOverride) {
        this.adminerUrl = adminerUrl;
        this.username = username;
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
            return "wsa";
        }
        int slash = jdbcUrl.lastIndexOf('/');
        if (slash < 0 || slash == jdbcUrl.length() - 1) {
            return "wsa";
        }
        String rest = jdbcUrl.substring(slash + 1);
        int q = rest.indexOf('?');
        String db = q >= 0 ? rest.substring(0, q) : rest;
        return db.isBlank() ? "wsa" : db;
    }
}
