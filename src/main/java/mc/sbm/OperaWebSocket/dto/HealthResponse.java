
package mc.sbm.OperaWebSocket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import mc.sbm.OperaWebSocket.client.OracleHospitalityStreamingClient;

/**
 * Response DTO for health check
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HealthResponse {

    private String status;
    private boolean connected;
    private String sessionId;
    private String error;

    public HealthResponse() {
    }

    private HealthResponse(String status, boolean connected, String sessionId) {
        this.status = status;
        this.connected = connected;
        this.sessionId = sessionId;
    }

    /**
     * Creates health response from streaming client
     */
    public static HealthResponse from(OracleHospitalityStreamingClient client) {
        boolean connected = client.isConnected();
        String status = connected ? "UP" : "DOWN";
        String sessionId = connected ? client.getSessionId() : null;

        return new HealthResponse(status, connected, sessionId);
    }

    /**
     * Creates error response
     */
    public static HealthResponse error(String errorMessage) {
        HealthResponse response = new HealthResponse();
        response.status = "ERROR";
        response.connected = false;
        response.error = errorMessage;
        return response;
    }

    /**
     * Checks if the service is healthy
     */
    public boolean isHealthy() {
        return "UP".equals(status);
    }

    // Getters and setters
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

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

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}