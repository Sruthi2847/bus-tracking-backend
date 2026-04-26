package com.bustrack.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * LoginHandler — Handles POST /login
 * Validates credentials based on role and returns user info.
 */
public class LoginHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json");

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }

        String body = new String(
            exchange.getRequestBody().readAllBytes(),
            StandardCharsets.UTF_8
        );
        Map<String, String> params = parseParams(body);

        String userId   = params.get("user_id");
        String password = params.get("password");
        String role     = params.get("role"); // driver / student / admin

        if (userId == null || password == null || role == null) {
            sendResponse(exchange, 400, "{\"error\":\"Missing fields\"}");
            return;
        }

        try {
            Connection conn = DBConnection.getConnection();
            String sql      = "";
            String idColumn = "";
            String table    = "";

            switch (role.toLowerCase()) {
                case "driver":
                    table    = "drivers";
                    idColumn = "driver_id";
                    break;
                case "student":
                    table    = "students";
                    idColumn = "student_id";
                    break;
                case "admin":
                    table    = "admins";
                    idColumn = "admin_id";
                    break;
                default:
                    sendResponse(exchange, 400, "{\"error\":\"Invalid role\"}");
                    return;
            }

            sql = "SELECT * FROM " + table +
                  " WHERE " + idColumn + "=? AND password=?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, userId);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String name   = rs.getString("name");
                String busId  = "N/A";
                try { busId = rs.getString("bus_id"); } catch (Exception ignored) {}

                String json = String.format(
                    "{\"status\":\"success\",\"role\":\"%s\"," +
                    "\"user_id\":\"%s\",\"name\":\"%s\",\"bus_id\":\"%s\"}",
                    role, userId, name, busId
                );
                sendResponse(exchange, 200, json);
            } else {
                sendResponse(exchange, 401, "{\"status\":\"failed\",\"error\":\"Invalid credentials\"}");
            }

            rs.close();
            stmt.close();

        } catch (SQLException e) {
            sendResponse(exchange, 500, "{\"error\":\"DB error: " + e.getMessage() + "\"}");
        }
    }

    private Map<String, String> parseParams(String body) throws UnsupportedEncodingException {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isEmpty()) return map;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2)
                map.put(URLDecoder.decode(kv[0], "UTF-8"), URLDecoder.decode(kv[1], "UTF-8"));
        }
        return map;
    }

    private void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
