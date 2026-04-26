package com.bustrack.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;

/**
 * GetBusesHandler — Handles GET /getBuses
 * Returns JSON array of all buses with status.
 */
public class GetBusesHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json");

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }

        try {
            Connection conn = DBConnection.getConnection();
            String sql = "SELECT bus_id, route_id, status FROM buses";
            Statement stmt = conn.createStatement();
            ResultSet rs   = stmt.executeQuery(sql);

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                json.append(String.format(
                    "{\"bus_id\":\"%s\",\"route_id\":\"%s\",\"status\":\"%s\"}",
                    rs.getString("bus_id"),
                    rs.getString("route_id"),
                    rs.getString("status")
                ));
                first = false;
            }
            json.append("]");

            rs.close();
            stmt.close();

            sendResponse(exchange, 200, json.toString());

        } catch (SQLException e) {
            sendResponse(exchange, 500, "{\"error\":\"DB error: " + e.getMessage() + "\"}");
        }
    }

    private void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
