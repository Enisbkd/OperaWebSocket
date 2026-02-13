package mc.sbm.OperaWebSocket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Response DTO for operation results
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OperationResponse {

    private boolean success;
    private String status;
    private String message;
    private String error;
    private String timestamp;

    public OperationResponse() {
        this.timestamp = Instant.now().toString();
    }

    private OperationResponse(boolean success, String status, String message, String error) {
        this();
        this.success = success;
        this.status = status;
        this.message = message;
        this.error = error;
    }

    /**
     * Creates success response
     */
    public static OperationResponse success(String status, String message) {
        return new OperationResponse(true, status, message, null);
    }

    /**
     * Creates failure response
     */
    public static OperationResponse failure(String status, String error) {
        return new OperationResponse(false, status, null, error);
    }

    // Getters and setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}