package com.bustrack.server;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class MainServer {
    private static final int PORT = 9090;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Registering Endpoints
        server.createContext("/login",           new LoginHandler());
        //server.createContext("/register", new RegisterHandler());
        server.createContext("/updateLocation",  new LocationHandler());
        server.createContext("/getLocation",     new GetLocationHandler());
        server.createContext("/getBuses",        new GetBusesHandler());
        server.createContext("/adminAction",     new AdminActionHandler());
        
        // NEW ENDPOINT
        server.createContext("/getNotifications", new GetNotificationsHandler());

        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        // If you DON'T see "VERSION 2.0" in your console, you are running old code!
        System.out.println("============================================");
        System.out.println("  BUS TRACKER BACKEND - VERSION 2.0");
        System.out.println("  Checking Contexts...");
        System.out.println("  Registered: /getNotifications");
        System.out.println("  Listening on: http://localhost:" + PORT);
        System.out.println("============================================");
    }
}