//package mc.sbm.OperaWebSocket.service;
//
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Service;
//
//@Service
//public class EventProcessingService {
//
//    private static final Logger logger = LoggerFactory.getLogger(EventProcessingService.class);
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//    public void processEvent(String eventMessage) {
//        try {
//            JsonNode eventNode = objectMapper.readTree(eventMessage);
//
//            // Check if it's a ping response
//            if (eventNode.has("pong")) {
//                logger.debug("Received pong response: {}", eventNode.get("pong").asText());
//                return;
//            }
//
//            // Process business events
//            if (eventNode.has("data") && eventNode.get("data").has("subscription")) {
//                JsonNode subscription = eventNode.get("data").get("subscription");
//
//                if (subscription.has("events")) {
//                    JsonNode events = subscription.get("events");
//
//                    if (events.isArray()) {
//                        for (JsonNode event : events) {
//                            processBusinessEvent(event);
//                        }
//                    } else {
//                        processBusinessEvent(events);
//                    }
//                }
//            } else {
//                // Handle other message types
//                logger.info("Received non-business event message: {}", eventMessage);
//            }
//
//        } catch (Exception e) {
//            logger.error("Error processing event message: {}", eventMessage, e);
//        }
//    }
//
//    private void processBusinessEvent(JsonNode event) {
//        try {
//            JsonNode eventHeader = event.get("eventHeader");
//            JsonNode eventPayload = event.get("eventPayload");
//
//            if (eventHeader != null) {
//                String eventType = eventHeader.has("eventType") ? eventHeader.get("eventType").asText() : "unknown";
//                String timestamp = eventHeader.has("timestamp") ? eventHeader.get("timestamp").asText() : "";
//                String hotelReference = eventHeader.has("hotelReference") ? eventHeader.get("hotelReference").asText() : "";
//
//                logger.info("Processing business event - Type: {}, Hotel: {}, Timestamp: {}",
//                        eventType, hotelReference, timestamp);
//
//                // Add your business logic here based on event type
//                switch (eventType.toLowerCase()) {
//                    case "reservation_created":
//                        handleReservationCreated(eventPayload);
//                        break;
//                    case "reservation_updated":
//                        handleReservationUpdated(eventPayload);
//                        break;
//                    case "reservation_cancelled":
//                        handleReservationCancelled(eventPayload);
//                        break;
//                    case "room_status_changed":
//                        handleRoomStatusChanged(eventPayload);
//                        break;
//                    default:
//                        logger.info("Unhandled event type: {}, payload: {}", eventType, eventPayload.toString());
//                }
//            }
//        } catch (Exception e) {
//            logger.error("Error processing business event", e);
//        }
//    }
//
//    private void handleReservationCreated(JsonNode payload) {
//        logger.info("Handling reservation created: {}", payload.toString());
//        // Implement your reservation created logic here
//    }
//
//    private void handleReservationUpdated(JsonNode payload) {
//        logger.info("Handling reservation updated: {}", payload.toString());
//        // Implement your reservation updated logic here
//    }
//
//    private void handleReservationCancelled(JsonNode payload) {
//        logger.info("Handling reservation cancelled: {}", payload.toString());
//        // Implement your reservation cancelled logic here
//    }
//
//    private void handleRoomStatusChanged(JsonNode payload) {
//        logger.info("Handling room status changed: {}", payload.toString());
//        // Implement your room status changed logic here
//    }
//}
