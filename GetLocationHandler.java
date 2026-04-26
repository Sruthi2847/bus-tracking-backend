package com.bustrack.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class GetLocationHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json");

        String query = exchange.getRequestURI().getQuery();
        Map<String, String> params = parseParams(query);
        String busId = params.get("bus_id");

        try (Connection conn = DBConnection.getConnection()) {
            // JOIN with buses table to get the REAL status (active/offline)
            String sql = "SELECT l.latitude, l.longitude, l.timestamp, b.status " +
                         "FROM locations l JOIN buses b ON l.bus_id = b.bus_id " +
                         "WHERE l.bus_id = ? ORDER BY l.timestamp DESC LIMIT 1";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, busId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String response = String.format(
                        "{\"status\":\"%s\", \"latitude\":%f, \"longitude\":%f, \"last_updated\":\"%s\"}",
                        rs.getString("status"), rs.getDouble("latitude"), rs.getDouble("longitude"), rs.getTimestamp("timestamp").toString()
                    );
                    sendResponse(exchange, 200, response);
                } else {
                    sendResponse(exchange, 200, "{\"status\":\"offline\"}");
                }
            }
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