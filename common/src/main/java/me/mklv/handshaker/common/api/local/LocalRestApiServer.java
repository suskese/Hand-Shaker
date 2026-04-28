package me.mklv.handshaker.common.api.local;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import me.mklv.handshaker.common.utils.Gsons;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LocalRestApiServer {
    private final Gson gson = Gsons.create();
    private final Logger logger;
    private final ApiServerConfig config;
    private final ApiDataProvider provider;

    private HttpServer server;
    private ExecutorService executor;
    private final Map<String, HttpHandler> contributedRoutes = new LinkedHashMap<>();

    /**
     * Registers an additional HTTP route. Must be called before {@link #start()}.
     * The API key authorization check is automatically applied to contributed routes.
     */
    public void addRoute(String path, HttpHandler handler) {
        contributedRoutes.put(path, handler);
    }

    public LocalRestApiServer(ApiServerConfig config, ApiDataProvider provider, Logger logger) {
        this.config = config != null ? config : ApiServerConfig.disabled();
        this.provider = provider;
        this.logger = logger;
    }

    public synchronized void start() throws IOException {
        if (!config.enabled() || provider == null) {
            return;
        }
        if (server != null) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", config.port()), 0);
        executor = Executors.newFixedThreadPool(2);
        server.setExecutor(executor);
        server.createContext("/api/v1", this::handle);
        for (Map.Entry<String, HttpHandler> entry : contributedRoutes.entrySet()) {
            final HttpHandler delegate = entry.getValue();
            server.createContext(entry.getKey(), exchange -> {
                if (!isAuthorized(exchange)) {
                    writeJson(exchange, 401, Map.of("error", "Unauthorized"));
                    return;
                }
                delegate.handle(exchange);
            });
        }
        server.start();

        if (logger != null) {
            logger.info("Local REST API started on port {}", config.port());
            if (!config.requiresAuth()) {
                logger.warn("Local REST API is running without api-key protection (localhost only)");
            }
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (logger != null) {
            logger.info("Local REST API stopped");
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!isAuthorized(exchange)) {
                writeJson(exchange, 401, new ApiModels.ErrorResponse("Unauthorized"));
                return;
            }

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String route = path.substring("/api/v1".length());
            Map<String, String> query = parseQuery(exchange.getRequestURI());

            if ("GET".equalsIgnoreCase(method) && "/health".equals(route)) {
                handleHealth(exchange);
                return;
            }

            if ("GET".equalsIgnoreCase(method) && "/players".equals(route)) {
                handlePlayers(exchange);
                return;
            }

            if ("GET".equalsIgnoreCase(method) && route.startsWith("/players/") && route.endsWith("/mods")) {
                String playerUuid = route.substring("/players/".length(), route.length() - "/mods".length());
                handlePlayerMods(exchange, decode(playerUuid));
                return;
            }

            if ("GET".equalsIgnoreCase(method) && "/mods".equals(route)) {
                handleMods(exchange);
                return;
            }

            if ("GET".equalsIgnoreCase(method) && route.startsWith("/mods/") && route.endsWith("/history")) {
                String modToken = route.substring("/mods/".length(), route.length() - "/history".length());
                handleModHistory(exchange, decode(modToken));
                return;
            }

            if ("POST".equalsIgnoreCase(method) && "/export".equals(route)) {
                handleExport(exchange, query.getOrDefault("format", "csv"));
                return;
            }

            writeJson(exchange, 404, new ApiModels.ErrorResponse("Not Found"));
        } catch (Throwable t) {
            if (logger != null) {
                logger.error("REST API request failed", t);
            }
            writeJson(exchange, 500, new ApiModels.ErrorResponse("Internal Server Error"));
        } finally {
            exchange.close();
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        var pool = provider.getPoolStats();
        ApiModels.HealthResponse response = new ApiModels.HealthResponse(
            true,
            provider.isDatabaseEnabled(),
            provider.getDatabaseType(),
            pool.activeConnections(),
            pool.idleConnections(),
            pool.awaitingThreads(),
            pool.maxPoolSize(),
            Instant.now()
        );
        writeJson(exchange, 200, response);
    }

    private void handlePlayers(HttpExchange exchange) throws IOException {
        List<ApiModels.PlayerSummary> players = provider.getActivePlayers();
        writeJson(exchange, 200, new ApiModels.PlayerListResponse(Instant.now(), players));
    }

    private void handlePlayerMods(HttpExchange exchange, String playerUuid) throws IOException {
        if (playerUuid == null || playerUuid.isBlank()) {
            writeJson(exchange, 400, new ApiModels.ErrorResponse("Missing player UUID"));
            return;
        }
        List<ApiModels.PlayerMod> mods = provider.getPlayerMods(playerUuid);
        writeJson(exchange, 200, new ApiModels.PlayerModsResponse(playerUuid, Instant.now(), mods));
    }

    private void handleMods(HttpExchange exchange) throws IOException {
        List<ApiModels.ModSummary> mods = provider.getAllMods();
        writeJson(exchange, 200, new ApiModels.ModListResponse(Instant.now(), mods));
    }

    private void handleModHistory(HttpExchange exchange, String modToken) throws IOException {
        if (modToken == null || modToken.isBlank()) {
            writeJson(exchange, 400, new ApiModels.ErrorResponse("Missing mod token"));
            return;
        }
            List<ApiModels.ModHistoryPlayer> players = provider.getModHistoryPlayers(modToken);
            writeJson(exchange, 200, new ApiModels.ModHistoryResponse(modToken, Instant.now(), players));
    }

    private void handleExport(HttpExchange exchange, String format) throws IOException {
        Optional<ApiModels.ExportResponse> custom = provider.export(format);
        if (custom.isPresent()) {
            writeJson(exchange, 200, custom.get());
            return;
        }

        String normalized = format == null ? "csv" : format.trim().toLowerCase();
        if (!"csv".equals(normalized) && !"json".equals(normalized)) {
            writeJson(exchange, 400, new ApiModels.ErrorResponse("Unsupported format: " + format));
            return;
        }

        List<ApiModels.ModSummary> mods = provider.getAllMods();
        if ("json".equals(normalized)) {
            writeJson(exchange, 200, new ApiModels.ExportResponse("json", mods.size(), gson.toJson(mods)));
            return;
        }

        StringBuilder csv = new StringBuilder();
        csv.append("mod,players\n");
        for (ApiModels.ModSummary mod : mods) {
            csv.append(escapeCsv(mod.modToken())).append(',').append(mod.players()).append('\n');
        }
        writeJson(exchange, 200, new ApiModels.ExportResponse("csv", mods.size(), csv.toString()));
    }

    private boolean isAuthorized(HttpExchange exchange) {
        if (!config.requiresAuth()) {
            return true;
        }

        String provided = resolveProvidedApiKey(exchange);
        return config.authMatches(provided);
    }

    private static String resolveProvidedApiKey(HttpExchange exchange) {
        String provided = exchange.getRequestHeaders().getFirst("X-Api-Key");
        if (provided != null && !provided.isBlank()) {
            return provided;
        }

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null) {
            return null;
        }

        final String bearerPrefix = "Bearer ";
        if (authHeader.regionMatches(true, 0, bearerPrefix, 0, bearerPrefix.length())) {
            String token = authHeader.substring(bearerPrefix.length()).trim();
            return token.isEmpty() ? null : token;
        }

        return null;
    }

    private void writeJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] body = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private static Map<String, String> parseQuery(URI uri) {
        Map<String, String> values = new HashMap<>();
        if (uri == null || uri.getRawQuery() == null || uri.getRawQuery().isBlank()) {
            return values;
        }

        for (String part : uri.getRawQuery().split("&")) {
            if (part.isBlank()) {
                continue;
            }
            int equals = part.indexOf('=');
            if (equals < 0) {
                values.put(decode(part), "");
                continue;
            }
            String key = decode(part.substring(0, equals));
            String value = decode(part.substring(equals + 1));
            values.put(key, value);
        }

        return values;
    }

    private static String decode(String value) {
        if (value == null) {
            return null;
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuotes ? "\"" + escaped + "\"" : escaped;
    }
}
