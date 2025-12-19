package com.olsc.llm;

import android.util.Log;

import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;
import java.io.InputStream;

public class WebServerService {
    private static final String TAG = "WebServerService";
    private static WebServer webServer;
    private static boolean isServerRunning = false;

    public static void startServer() {
        if (isServerRunning) {
            Log.i(TAG, "Web server is already running");
            return;
        }

        try {
            webServer = new WebServer(8080);
            webServer.start();
            isServerRunning = true;
            Log.i(TAG, "Web server started on port 8080");
        } catch (IOException e) {
            Log.e(TAG, "Failed to start web server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void stopServer() {
        if (webServer != null && isServerRunning) {
            webServer.stop();
            isServerRunning = false;
            Log.i(TAG, "Web server stopped");
        }
    }

    public static boolean isRunning() {
        return isServerRunning;
    }

    private static class WebServer extends NanoHTTPD {
        public WebServer(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            if (uri.equals("/")) {
                uri = "/index.html";
            }
            
            // 根据文件扩展名设置正确的MIME类型
            String mimeType = getMimeType(uri);
            
            try {
                InputStream inputStream = App.getContext().getAssets().open("www" + uri);
                return newChunkedResponse(Response.Status.OK, mimeType, inputStream);
            } catch (IOException e) {
                Log.e(TAG, "File not found: " + uri);
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found: " + uri);
            }
        }

        private String getMimeType(String uri) {
            if (uri.endsWith(".html")) return "text/html";
            if (uri.endsWith(".css")) return "text/css";
            if (uri.endsWith(".js")) return "application/javascript";
            if (uri.endsWith(".png")) return "image/png";
            if (uri.endsWith(".jpg") || uri.endsWith(".jpeg")) return "image/jpeg";
            if (uri.endsWith(".gif")) return "image/gif";
            if (uri.endsWith(".svg")) return "image/svg+xml";
            if (uri.endsWith(".ico")) return "image/x-icon";
            if (uri.endsWith(".json")) return "application/json";
            if (uri.endsWith(".xml")) return "application/xml";
            return "text/plain";
        }
    }
} 