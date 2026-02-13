package mc.sbm.OperaWebSocket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import mc.sbm.OperaWebSocket.client.OracleHospitalityStreamingClient;
import mc.sbm.OperaWebSocket.websocket.WebSocketConnectionManager;

import java.time.Duration;
import java.time.Instant;

/**
 * Response DTO for connection status
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConnectionStatusResponse {

    private boolean connected;
    private String sessionId;
    private String lastMessageReceived;
    private Long secondsSinceLastMessage;
    private String error;

    public ConnectionStatusResponse() {
    }

    private ConnectionStatusResponse(boolean connected, String sessionId,
                                     String lastMessageReceived, Long secondsSinceLastMessage) {
        this.connected = connected;
        this.sessionId = sessionId;
        this.lastMessageReceived = lastMessageReceived;
        this.secondsSinceLastMessage = secondsSinceLastMessage;
    }

    /**
     * Creates status response from streaming client
     */
    public static ConnectionStatusResponse from(OracleHospitalityStreamingClient client) {
        boolean connected = client.isConnected();
        String sessionId = client.getSessionId();

        // Access lastMessageReceived through a public getter method
        // Note: You'll need to add this getter to OracleHospitalityStreamingClient
        Instant lastMessage = getLastMessageReceivedFromClient(client);

        String lastMessageStr = lastMessage != null ? lastMessage.toString() : "Never";
        Long secondsSince = null;

        if (lastMessage != null) {
            secondsSince = Duration.between(lastMessage, Instant.now()).getSeconds();
        }

        return new ConnectionStatusResponse(connected, sessionId, lastMessageStr, secondsSince);
    }

    /**
     * Creates error response
     */
    public static ConnectionStatusResponse error(String errorMessage) {
        ConnectionStatusResponse response = new ConnectionStatusResponse();
        response.error = errorMessage;
        response.connected = false;
        return response;
    }

    // Helper method - you should add a public getter in OracleHospitalityStreamingClient
    private static Instant getLastMessageReceivedFromClient(OracleHospitalityStreamingClient client) {
        // This assumes you add a getLastMessageReceived() method to the client
        // For now, return null as placeholder
        return null; // TODO: Add getter to client
    }

    // Getters and setters
    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getLastMessageReceived() {
        return lastMessageReceived;
    }

    public void setLastMessageReceived(String lastMessageReceived) {
        this.lastMessageReceived = lastMessageReceived;
    }

    public Long getSecondsSinceLastMessage() {
        return secondsSinceLastMessage;
    }

    public void setSecondsSinceLastMessage(Long secondsSinceLastMessage) {
        this.secondsSinceLastMessage = secondsSinceLastMessage;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}