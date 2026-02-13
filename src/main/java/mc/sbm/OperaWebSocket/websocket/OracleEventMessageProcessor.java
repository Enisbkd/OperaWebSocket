package mc.sbm.OperaWebSocket.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mc.sbm.OperaWebSocket.service.KafkaProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Processes incoming Oracle Hospitality event messages
 */
@Component
public class OracleEventMessageProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OracleEventMessageProcessor.class);

    private final ObjectMapper objectMapper;
    private final KafkaProducerService kafkaProducerService;

    public OracleEventMessageProcessor(ObjectMapper objectMapper, KafkaProducerService kafkaProducerService) {
        this.objectMapper = objectMapper;
        this.kafkaProducerService = kafkaProducerService;
    }

    /**
     * Processes a complete message payload
     */
    public void processMessage(String payload, String sessionId) {
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);

            JsonNode typeNode = jsonNode.get("type");
            if (typeNode == null) {
                logger.warn("Received message without 'type' field. Message length: {} bytes. First 200 chars: {}",
                        payload.length(),
                        payload.substring(0, Math.min(200, payload.length())));
                return;
            }

            String messageType = typeNode.asText();

            switch (messageType) {
                case "connection_ack":
                    handleConnectionAck(jsonNode);
                    break;
                case "next":
                    handleEventMessage(jsonNode);
                    sendToKafka(payload, sessionId);
                    break;
                case "error":
                    handleErrorMessage(jsonNode);
                    break;
                case "complete":
                    handleCompleteMessage(jsonNode);
                    break;
                case "pong":
                    logger.debug("Pong received - connection alive");
                    break;
                default:
                    logger.warn("Unknown message type received: {}", messageType);
            }

        } catch (Exception e) {
            logger.error("Error processing message", e);
            throw new RuntimeException("Message processing failed", e);
        }
    }

    private void handleConnectionAck(JsonNode jsonNode) {
        logger.info("Connection acknowledged by server");
        if (jsonNode.has("payload") && jsonNode.get("payload").has("applicationName")) {
            String appName = jsonNode.get("payload").get("applicationName").asText();
            logger.info("Connected application: {}", appName);
        }
    }

    private void handleEventMessage(JsonNode jsonNode) {
        try {
            JsonNode payload = jsonNode.get("payload");
            JsonNode data = payload.get("data");
            JsonNode newEvent = data.get("newEvent");

            String moduleName = newEvent.get("moduleName").asText();
            String eventName = newEvent.get("eventName").asText();
            long offset = newEvent.get("metadata").get("offset").asLong();

            logger.info("Event received - Module: {}, Event: {}, Offset: {}",
                    moduleName, eventName, offset);

            if (newEvent.has("detail")) {
                JsonNode detail = newEvent.get("detail");
                logger.debug("Event detail: {}", detail.toString());
                processBusinessEvent(moduleName, eventName, offset, detail);
            }

        } catch (Exception e) {
            logger.error("Error handling event message", e);
        }
    }

    private void handleErrorMessage(JsonNode jsonNode) {
        JsonNode errors = jsonNode.get("payload");
        logger.error("Error received from server: {}", errors.toString());
        throw new RuntimeException("Server error: " + errors.toString());
    }

    private void handleCompleteMessage(JsonNode jsonNode) {
        logger.info("Stream completed by server");
    }

    /**
     * Process business events - can be overridden or extended
     */
    protected void processBusinessEvent(String moduleName, String eventName, long offset, JsonNode detail) {
        logger.info("Processing event: {}.{} at offset {}", moduleName, eventName, offset);
        // Implement custom business logic here
    }

    private void sendToKafka(String message, String sessionId) {
        try {
            kafkaProducerService.sendMessage(sessionId, message);
            logger.debug("Message sent to Kafka ({} bytes)", message.length());
        } catch (Exception e) {
            logger.error("Failed to send message to Kafka", e);
            // Don't fail the entire message processing if Kafka is down
        }
    }
}