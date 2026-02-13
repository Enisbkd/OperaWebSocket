
package mc.sbm.OperaWebSocket.controller;

import mc.sbm.OperaWebSocket.client.OracleHospitalityStreamingClient;
import mc.sbm.OperaWebSocket.dto.ConnectionStatusResponse;
import mc.sbm.OperaWebSocket.dto.HealthResponse;
import mc.sbm.OperaWebSocket.dto.OperationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for monitoring and managing the Oracle Hospitality Streaming Client
 */
@RestController
@RequestMapping("/api/streaming")
public class StreamingMonitorController {

    private static final Logger logger = LoggerFactory.getLogger(StreamingMonitorController.class);

    private final OracleHospitalityStreamingClient streamingClient;

    public StreamingMonitorController(OracleHospitalityStreamingClient streamingClient) {
        this.streamingClient = streamingClient;
    }

    /**
     * Get current connection status
     *
     * @return connection status details
     */
    @GetMapping("/status")
    public ResponseEntity<ConnectionStatusResponse> getStatus() {
        try {
            ConnectionStatusResponse status = ConnectionStatusResponse.from(streamingClient);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("Failed to retrieve connection status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ConnectionStatusResponse.error(e.getMessage()));
        }
    }

    /**
     * Manually trigger connection
     *
     * @return operation result
     */
    @PostMapping("/connect")
    public ResponseEntity<OperationResponse> connect() {
        try {
            if (streamingClient.isConnected()) {
                return ResponseEntity.ok(OperationResponse.success(
                        "Already connected",
                        "Connection is already established"
                ));
            }

            streamingClient.connect();
            logger.info("Manual connection initiated via REST endpoint");

            return ResponseEntity.ok(OperationResponse.success(
                    "Connection initiated",
                    "Connection process has been started"
            ));
        } catch (Exception e) {
            logger.error("Failed to initiate connection via REST endpoint", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OperationResponse.failure("Connection failed", e.getMessage()));
        }
    }

    /**
     * Manually disconnect
     *
     * @return operation result
     */
    @PostMapping("/disconnect")
    public ResponseEntity<OperationResponse> disconnect() {
        try {
            if (!streamingClient.isConnected()) {
                return ResponseEntity.ok(OperationResponse.success(
                        "Already disconnected",
                        "Connection is not active"
                ));
            }

            streamingClient.disconnect();
            logger.info("Manual disconnect initiated via REST endpoint");

            return ResponseEntity.ok(OperationResponse.success(
                    "Disconnected",
                    "Connection has been terminated"
            ));
        } catch (Exception e) {
            logger.error("Failed to disconnect via REST endpoint", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OperationResponse.failure("Disconnect failed", e.getMessage()));
        }
    }

    /**
     * Health check endpoint
     *
     * @return health status
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        try {
            HealthResponse health = HealthResponse.from(streamingClient);

            if (health.isHealthy()) {
                return ResponseEntity.ok(health);
            } else {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve health status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(HealthResponse.error(e.getMessage()));
        }
    }
}