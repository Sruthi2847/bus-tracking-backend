package com.bustrack.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class LocationHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json");

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = parseParams(body);

        String busId = params.get("bus_id");
        String latStr = params.get("latitude");
        String lngStr = params.get("longitude");
        String status = params.getOrDefault("status", "active");

        if (busId == null) {
            sendResponse(exchange, 400, "{\"error\":\"Missing bus_id\"}");
            return;
        }

        try (Connection conn = DBConnection.getConnection()) {
            // 1. Record location only if active
            if ("active".equalsIgnoreCase(status) && latStr != null && lngStr != null) {
                String sql = "INSERT INTO locations (bus_id, latitude, longitude) VALUES (?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, busId);
                    stmt.setDouble(2, Double.parseDouble(latStr));
                    stmt.setDouble(3, Double.parseDouble(lngStr));
                    stmt.executeUpdate();
                }
            }

            // 2. CRITICAL: Update the status in the buses table
            String updateSql = "UPDATE buses SET status=? WHERE bus_id=?";
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setString(1, status.toLowerCase());
                updateStmt.setString(2, busId);
                updateStmt.executeUpdate();
            }

            sendResponse(exchange, 200, "{\"status\":\"success\"}");
        } catch (Exception e) {
            sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private Map<String, String> parseParams(String body) throws UnsupportedEncodingException {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isEmpty()) return map;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) map.put(URLDecoder.decode(kv[0], "UTF-8"), URLDecoder.decode(kv[1], "UTF-8"));
        }
        return map;
    }

    private void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }
}