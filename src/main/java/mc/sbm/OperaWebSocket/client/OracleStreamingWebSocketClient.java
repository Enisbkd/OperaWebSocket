//package mc.sbm.OperaWebSocket.client;
//
//
//
//import mc.sbm.OperaWebSocket.config.OracleStreamingProperties;
//import mc.sbm.OperaWebSocket.service.EventProcessingService;
//import mc.sbm.OperaWebSocket.service.OAuthService;
//import org.java_websocket.client.WebSocketClient;
//import org.java_websocket.handshake.ServerHandshake;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import jakarta.annotation.PostConstruct;
//import jakarta.annotation.PreDestroy;
//import java.net.URI;
//import java.util.concurrent.atomic.AtomicBoolean;
//import java.util.concurrent.atomic.AtomicInteger;
//
//@Component
//public class OracleStreamingWebSocketClient {
//
//    private static final Logger logger = LoggerFactory.getLogger(OracleStreamingWebSocketClient.class);
//
//    private final OracleStreamingProperties properties;
//    private final OAuthService oAuthService;
//    private final EventProcessingService eventProcessingService;
//
//    private WebSocketClient webSocketClient;
//    private final AtomicBoolean isConnected = new AtomicBoolean(false);
//    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
//
//    @Autowired
//    public OracleStreamingWebSocketClient(
//            OracleStreamingProperties properties,
//            OAuthService oAuthService,
//            EventProcessingService eventProcessingService) {
//        this.properties = properties;
//        this.oAuthService = oAuthService;
//        this.eventProcessingService = eventProcessingService;
//    }
//
//    @PostConstruct
//    public void initialize() {
//        connect();
//    }
//
//    private void connect() {
//        try {
//            String accessToken = oAuthService.getValidAccessToken();
//            String wsUrl = properties.getBaseUrl() + "/subscriptions";
//            URI serverUri = URI.create(wsUrl);
//
//            webSocketClient = new WebSocketClient(serverUri) {
//                @Override
//                public void onOpen(ServerHandshake handshake) {
//                    logger.info("WebSocket connection opened");
//                    isConnected.set(true);
//                    reconnectAttempts.set(0);
//
//                    // Send subscription message with authentication
//                    sendSubscriptionMessage(accessToken);
//                }
//
//                @Override
//                public void onMessage(String message) {
//                    logger.debug("Received message: {}", message);
//                    eventProcessingService.processEvent(message);
//                }
//
//                @Override
//                public void onClose(int code, String reason, boolean remote) {
//                    logger.warn("WebSocket connection closed. Code: {}, Reason: {}, Remote: {}", code, reason, remote);
//                    isConnected.set(false);
//
//                    if (reconnectAttempts.get() < properties.getMaxReconnectAttempts()) {
//                        scheduleReconnect();
//                    } else {
//                        logger.error("Max reconnection attempts reached. Stopping reconnection efforts.");
//                    }
//                }
//
//                @Override
//                public void onError(Exception ex) {
//                    logger.error("WebSocket error occurred", ex);
//                    isConnected.set(false);
//                }
//            };
//
//            // Add authorization header
//            webSocketClient.addHeader("Authorization", "Bearer " + accessToken);
//            webSocketClient.addHeader("Application-Key", properties.getApplicationKey());
//
//            webSocketClient.connect();
//
//        } catch (Exception e) {
//            logger.error("Failed to connect to WebSocket", e);
//            scheduleReconnect();
//        }
//    }
//
//    private void sendSubscriptionMessage(String accessToken) {
//        // Based on Oracle documentation, send subscription query
//        String subscriptionQuery = String.format(
//                "subscription { " +
//                        "applicationKey: \"%s\" " +
//                        "events { " +
//                        "eventHeader { " +
//                        "eventType " +
//                        "timestamp " +
//                        "hotelReference " +
//                        "} " +
//                        "eventPayload " +
//                        "} " +
//                        "}",
//                properties.getApplicationKey()
//        );
//
//        if (webSocketClient != null && webSocketClient.isOpen()) {
//            webSocketClient.send(subscriptionQuery);
//            logger.info("Subscription message sent");
//        }
//    }
//
//    private void scheduleReconnect() {
//        new Thread(() -> {
//            try {
//                int attempts = reconnectAttempts.incrementAndGet();
//                logger.info("Scheduling reconnection attempt {} in {} ms", attempts, properties.getReconnectDelay());
//                Thread.sleep(properties.getReconnectDelay());
//                connect();
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                logger.error("Reconnection thread interrupted", e);
//            }
//        }).start();
//    }
//
//    // Send ping every 15 seconds as required by Oracle [[3]](https://docs.oracle.com/en/industries/hospitality/integration-platform/ohipu/t_keeping_the_stream_open.htm)
//    @Scheduled(fixedDelayString = "#{oracleStreamingProperties.pingInterval}")
//    public void sendPing() {
//        if (isConnected.get() && webSocketClient != null && webSocketClient.isOpen()) {
//            String pingMessage = "{\"ping\": \"" + System.currentTimeMillis() + "\"}";
//            webSocketClient.send(pingMessage);
//            logger.debug("Ping sent to keep connection alive");
//        }
//    }
//
//    @PreDestroy
//    public void disconnect() {
//        if (webSocketClient != null) {
//            webSocketClient.close();
//            logger.info("WebSocket client disconnected");
//        }
//    }
//
//    public boolean isConnected() {
//        return isConnected.get();
//    }
//}