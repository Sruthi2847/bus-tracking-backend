package com.bustrack.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class AdminActionHandler implements HttpHandler {
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
        String action = params.get("action");

        try (Connection conn = DBConnection.getConnection()) {
            String result = "{\"status\":\"success\"}";
            switch (action) {
                case "send_notification":
                    try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO notifications (title, message) VALUES (?, ?)")) {
                        stmt.setString(1, params.get("title"));
                        stmt.setString(2, params.get("message"));
                        stmt.executeUpdate();
                    }
                    break;
                case "add_bus":
                    try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO buses (bus_id, route_id) VALUES (?, ?)")) {
                        stmt.setString(1, params.get("bus_id"));
                        stmt.setString(2, params.get("route_id"));
                        stmt.executeUpdate();
                    }
                    break;
                case "add_stop":
                    try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO stops (name, latitude, longitude, route_id) VALUES (?,?,?,?)")) {
                        stmt.setString(1, params.get("name"));
                        stmt.setDouble(2, Double.parseDouble(params.get("latitude")));
                        stmt.setDouble(3, Double.parseDouble(params.get("longitude")));
                        stmt.setString(4, params.get("route_id"));
                        stmt.executeUpdate();
                    }
                    break;
            }
            sendResponse(exchange, 200, result);
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