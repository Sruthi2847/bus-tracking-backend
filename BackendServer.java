package com.bustrack.server;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Executors;

public class BackendServer {
    private static final int PORT = 9090;
    private static final String DB_URL = "jdbc:mysql://localhost:3306/college_bus_tracker";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "MiniProject07";

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/register", new RegisterHandler());
        server.createContext("/login", new LoginHandler());
        server.createContext("/updateLocation", new LocationHandler());
        server.createContext("/getLocation", new GetLocationHandler());
        server.createContext("/getNotifications", new GetNotificationsHandler());
        server.createContext("/adminAction", new AdminActionHandler());
        server.createContext("/getBuses", new GetBusesHandler());
        server.setExecutor(Executors.newFixedThreadPool(10));
        System.out.println("Bus Tracker Backend v8.0 running on port 9090...");
        server.start();
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    private static String toJson(String status, String message) {
        return String.format("{\"status\":\"%s\", \"error\":\"%s\"}", status, message.replace("\"", "'"));
    }

    private static Map<String, String> parseParams(InputStream is) throws IOException {
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> map = new HashMap<>();
        if (body.isEmpty()) return map;
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), 
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            } else if (kv.length == 1) {
                map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), "");
            }
        }
        return map;
    }

    private static void sendResponse(HttpExchange exchange, int code, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    // 1. REGISTER (Student Only)
    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                Map<String, String> p = parseParams(ex.getRequestBody());
                try (Connection c = getConnection()) {
                    String sql = "INSERT INTO students (student_id, name, password, bus_id) VALUES (?, ?, ?, ?)";
                    PreparedStatement st = c.prepareStatement(sql);
                    st.setString(1, p.get("user_id"));
                    st.setString(2, p.get("name"));
                    st.setString(3, p.get("password"));
                    st.setString(4, p.getOrDefault("bus_id", "N/A"));
                    st.executeUpdate();
                    sendResponse(ex, 200, "{\"status\":\"success\"}");
                }
            } catch (Exception e) { sendResponse(ex, 200, toJson("failed", e.getMessage())); }
        }
    }

    // 2. ADMIN ACTIONS (Add Bus, Add Driver, Assign)
    static class AdminActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                Map<String, String> p = parseParams(ex.getRequestBody());
                String a = p.get("action");
                try (Connection c = getConnection()) {
                    if ("add_bus".equals(a)) {
                        PreparedStatement st = c.prepareStatement("INSERT IGNORE INTO buses (bus_id,  status) VALUES (?,  'offline')");
                        st.setString(1, p.get("bus_id"));
                        st.executeUpdate();
                    } else if ("add_driver".equals(a)) {
                        PreparedStatement st = c.prepareStatement("INSERT INTO drivers (driver_id, name, password, bus_id) VALUES (?, ?, ?, NULL)");
                        st.setString(1, p.get("driver_id"));
                        st.setString(2, p.get("name"));
                        st.setString(3, p.get("password"));
                        st.executeUpdate();
                    } else if ("assign_driver".equals(a)) {
                        PreparedStatement st = c.prepareStatement("UPDATE drivers SET bus_id=? WHERE driver_id=?");
                        st.setString(1, p.get("bus_id"));
                        st.setString(2, p.get("driver_id"));
                        st.executeUpdate();
                    } else if ("send_notification".equals(a)) {
                        PreparedStatement st = c.prepareStatement("INSERT INTO notifications (title, message) VALUES (?, ?)");
                        st.setString(1, p.get("title"));
                        st.setString(2, p.get("message"));
                        st.executeUpdate();
                    }
                    sendResponse(ex, 200, "{\"status\":\"success\"}");
                }
            } catch (Exception e) { sendResponse(ex, 200, toJson("failed", e.getMessage())); }
        }
    }

    // 3. LOGIN
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                Map<String, String> p = parseParams(ex.getRequestBody());
                String role = p.get("role"), uId = p.get("user_id"), pass = p.get("password");
                String table = "students", idCol = "student_id";
                if ("driver".equalsIgnoreCase(role)) { table = "drivers"; idCol = "driver_id"; }
                else if ("admin".equalsIgnoreCase(role)) { table = "admins"; idCol = "admin_id"; }

                try (Connection c = getConnection()) {
                    String sql = "SELECT name, bus_id FROM " + table + " WHERE " + idCol + "=? AND password=?";
                    if ("admins".equals(table)) sql = "SELECT name FROM admins WHERE admin_id=? AND password=?";
                    
                    PreparedStatement st = c.prepareStatement(sql);
                    st.setString(1, uId); st.setString(2, pass);
                    ResultSet rs = st.executeQuery();
                    if (rs.next()) {
                        String bId = "admins".equals(table) ? "N/A" : rs.getString("bus_id");
                        sendResponse(ex, 200, String.format("{\"status\":\"success\",\"name\":\"%s\",\"bus_id\":\"%s\"}", rs.getString("name"), bId));
                    } else sendResponse(ex, 200, "{\"status\":\"failed\",\"error\":\"Invalid credentials\"}");
                }
            } catch (Exception e) { sendResponse(ex, 200, toJson("failed", e.getMessage())); }
        }
    }

    // (Remaining handlers like LocationHandler, GetLocationHandler, GetNotificationsHandler, GetBusesHandler remain the same)
    static class LocationHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            try {
                Map<String, String> p = parseParams(ex.getRequestBody());
                String b = p.get("bus_id"), s = p.getOrDefault("status", "active");
                try (Connection c = getConnection()) {
                    if ("active".equalsIgnoreCase(s)) {
                        PreparedStatement st = c.prepareStatement("INSERT INTO locations (bus_id, latitude, longitude) VALUES (?,?,?)");
                        st.setString(1, b); st.setDouble(2, Double.parseDouble(p.get("latitude")));
                        st.setDouble(3, Double.parseDouble(p.get("longitude"))); st.executeUpdate();
                    }
                    PreparedStatement st = c.prepareStatement("UPDATE buses SET status=? WHERE bus_id=?");
                    st.setString(1, s); st.setString(2, b); st.executeUpdate();
                    sendResponse(ex, 200, "{\"status\":\"success\"}");
                }
            } catch (Exception e) { sendResponse(ex, 200, toJson("error", e.getMessage())); }
        }
    }

    static class GetLocationHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            String q = ex.getRequestURI().getQuery();
            if (q == null) { sendResponse(ex, 400, "{\"error\":\"Missing query\"}"); return; }
            String b = q.contains("bus_id=") ? q.split("bus_id=")[1].split("&")[0] : "";
            try (Connection c = getConnection()) {
                String sql = "SELECT l.latitude, l.longitude, l.timestamp, b.status FROM locations l " +
                             "JOIN buses b ON l.bus_id = b.bus_id WHERE l.bus_id=? ORDER BY l.timestamp DESC LIMIT 1";
                PreparedStatement st = c.prepareStatement(sql);
                st.setString(1, b); ResultSet rs = st.executeQuery();
                if (rs.next()) {
                    sendResponse(ex, 200, String.format("{\"status\":\"%s\", \"latitude\":%f, \"longitude\":%f, \"last_updated\":\"%s\"}",
                        rs.getString("status"), rs.getDouble("latitude"), rs.getDouble("longitude"), rs.getTimestamp("timestamp")));
                } else sendResponse(ex, 200, "{\"status\":\"offline\"}");
            } catch (Exception e) { sendResponse(ex, 200, toJson("error", e.getMessage())); }
        }
    }

    static class GetNotificationsHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            try (Connection c = getConnection()) {
                ResultSet rs = c.createStatement().executeQuery("SELECT title, message FROM notifications ORDER BY timestamp DESC LIMIT 1");
                if (rs.next()) sendResponse(ex, 200, String.format("{\"title\":\"%s\", \"message\":\"%s\"}", rs.getString("title"), rs.getString("message")));
                else sendResponse(ex, 200, "{}");
            } catch (Exception e) { sendResponse(ex, 200, toJson("error", e.getMessage())); }
        }
    }

    static class GetBusesHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            try (Connection c = getConnection()) {
                ResultSet rs = c.createStatement().executeQuery("SELECT bus_id, route_id, status FROM buses");
                StringBuilder sb = new StringBuilder("[");
                while (rs.next()) {
                    if (sb.length() > 1) sb.append(",");
                    sb.append(String.format("{\"bus_id\":\"%s\",  \"status\":\"%s\"}", 
                        rs.getString("bus_id"),  rs.getString("status")));
                }
                sb.append("]"); sendResponse(ex, 200, sb.toString());
            } catch (Exception e) { sendResponse(ex, 200, toJson("error", e.getMessage())); }
        }
    }
}