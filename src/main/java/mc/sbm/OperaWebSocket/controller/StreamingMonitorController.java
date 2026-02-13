package mc.sbm.OperaWebSocket.controller;


import mc.sbm.OperaWebSocket.client.OracleHospitalityStreamingClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for monitoring and managing the Oracle Hospitality Streaming Client
 *
 * Endpoints:
 * - GET /api/streaming/status - Get connection status
 * - POST /api/streaming/connect - Manually trigger connection
 * - POST /api/streaming/disconnect - Manually disconnect
 */
@RestController
@RequestMapping("/api/streaming")
public class StreamingMonitorController {

    private final OracleHospitalityStreamingClient streamingClient;

    @Autowired
    public StreamingMonitorController(OracleHospitalityStreamingClient streamingClient) {
        this.streamingClient = streamingClient;
    }

    /**
     * Get current connection status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("connected", streamingClient.isConnected());
        status.put("sessionId", streamingClient.getSessionId());

        Instant lastMessage = streamingClient.getLastMessageReceived();
        status.put("lastMessageReceived", lastMessage != null ? lastMessage.toString() : "Never");

        if (lastMessage != null) {
            long secondsSinceLastMessage = java.time.Duration.between(lastMessage, Instant.now()).getSeconds();
            status.put("secondsSinceLastMessage", secondsSinceLastMessage);
        }

        return ResponseEntity.ok(status);
    }

    /**
     * Manually trigger connection
     */
    @PostMapping("/connect")
    public ResponseEntity<Map<String, String>> connect() {
        try {
            streamingClient.connect();
            Map<String, String> response = new HashMap<>();
            response.put("status", "Connection initiated");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "Failed to initiate connection");
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Manually disconnect
     */
    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, String>> disconnect() {
        try {
            streamingClient.disconnect();
            Map<String, String> response = new HashMap<>();
            response.put("status", "Disconnected");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "Failed to disconnect");
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", streamingClient.isConnected() ? "UP" : "DOWN");
        return ResponseEntity.ok(health);
    }
}