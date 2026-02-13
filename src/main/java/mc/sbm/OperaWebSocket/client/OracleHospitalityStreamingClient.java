package mc.sbm.OperaWebSocket.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * Production-ready WebSocket client for Oracle Hospitality Integration Platform Streaming API.
 *
 * Features:
 * - Automatic connection management with reconnection logic
 * - Keep-alive ping mechanism (every 4 minutes to prevent 5-minute timeout)
 * - GraphQL-WS protocol support
 * - OAuth token authentication
 * - Configurable via application properties
 * - Thread-safe operations
 * - Comprehensive error handling and logging
 * - Graceful shutdown
 *
 * Configuration required in application.properties:
 * oracle.hospitality.streaming.url=wss://www.oracle.com/subscriptions
 * oracle.hospitality.streaming.app-key=YOUR_APP_KEY
 * oracle.hospitality.streaming.oauth-token=YOUR_OAUTH_TOKEN
 * oracle.hospitality.streaming.chain-code=OHIPCN
 * oracle.hospitality.streaming.auto-start=true
 * oracle.hospitality.streaming.reconnect-delay-seconds=30
 * oracle.hospitality.streaming.ping-interval-seconds=240
 */
@Service
public class OracleHospitalityStreamingClient extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(OracleHospitalityStreamingClient.class);
    private static final String PROTOCOL = "graphql-transport-ws";

    @Value("${oracle.hospitality.streaming.url}")
    private String streamingUrl;

    @Value("${oracle.hospitality.streaming.app-key}")
    private String applicationKey;

    @Value("${oracle.hospitality.streaming.oauth-token}")
    private String oauthToken;

    @Value("${oracle.hospitality.streaming.chain-code}")
    private String chainCode;

    @Value("${oracle.hospitality.streaming.auto-start:true}")
    private boolean autoStart;

    @Value("${oracle.hospitality.streaming.reconnect-delay-seconds:30}")
    private int reconnectDelaySeconds;

    @Value("${oracle.hospitality.streaming.ping-interval-seconds:240}")
    private int pingIntervalSeconds;

    @Value("${oracle.hospitality.streaming.proxy.enabled:false}")
    private boolean proxyEnabled;

    @Value("${oracle.hospitality.streaming.proxy.host:}")
    private String proxyHost;

    @Value("${oracle.hospitality.streaming.proxy.port:8080}")
    private int proxyPort;

    @Value("${oracle.hospitality.streaming.max-text-message-buffer-size:10485760}")
    private int maxTextMessageBufferSize;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<WebSocketSession> sessionRef = new AtomicReference<>();
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    private final String sessionId = UUID.randomUUID().toString();

    private ScheduledExecutorService pingScheduler;
    private ScheduledExecutorService reconnectScheduler;
    private StandardWebSocketClient webSocketClient;
    private Instant lastMessageReceived;

    @PostConstruct
    public void init() {
        webSocketClient = new StandardWebSocketClient();
        configureWebSocketContainer();

        // Configure proxy if enabled
        if (proxyEnabled && proxyHost != null && !proxyHost.isEmpty()) {
            configureProxy();
        }
        
        pingScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ohip-ping-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ohip-reconnect-scheduler");
            thread.setDaemon(true);
            return thread;
        });

        if (autoStart) {
            logger.info("Auto-start enabled. Initiating connection to Oracle Hospitality Streaming API");
            connect();
        } else {
            logger.info("Auto-start disabled. Call connect() manually to establish connection");
        }
    }

    /**
     * Configures proxy settings for WebSocket client
     */
    private void configureProxy() {
        try {
            logger.info("Configuring proxy: {}:{}", proxyHost, proxyPort);
            
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            
            // Set proxy via system properties (works for most WebSocket implementations)
            System.setProperty("http.proxyHost", proxyHost);
            System.setProperty("http.proxyPort", String.valueOf(proxyPort));
            System.setProperty("https.proxyHost", proxyHost);
            System.setProperty("https.proxyPort", String.valueOf(proxyPort));
            
            // Configure ProxySelector for the JVM
            ProxySelector.setDefault(new ProxySelector() {
                private final List<Proxy> proxies = Collections.singletonList(
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort))
                );
                
                @Override
                public List<Proxy> select(URI uri) {
                    return proxies;
                }
                
                @Override
                public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                    logger.error("Proxy connection failed for URI: {}", uri, ioe);
                }
            });
            
            webSocketClient = new StandardWebSocketClient(container);
            
            logger.info("Proxy configuration completed successfully");
        } catch (Exception e) {
            logger.error("Failed to configure proxy", e);
        }
    }

    /**
     * Establishes WebSocket connection to Oracle Hospitality Streaming API
     */
    public synchronized void connect() {
        if (isConnected.get()) {
            logger.warn("Already connected to streaming API");
            return;
        }

        try {
            logger.info("Connecting to Oracle Hospitality Streaming API at {}", streamingUrl);

            String hashedKey = generateSha256Hash(applicationKey);
            String urlWithParams = String.format("%s?key=%s", streamingUrl, hashedKey);

            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.add("Sec-WebSocket-Protocol", PROTOCOL);

            WebSocketSession session = webSocketClient.execute(
                    this,
                    headers,
                    URI.create(urlWithParams)
            ).get(30, TimeUnit.SECONDS);

            sessionRef.set(session);
            isConnected.set(true);
            lastMessageReceived = Instant.now();

            logger.info("WebSocket connection established. Session ID: {}", session.getId());

            // Send authentication message
            sendAuthenticationMessage();

            // Schedule subscription message (after 2 seconds to allow auth to complete)
            reconnectScheduler.schedule(this::sendSubscriptionMessage, 2, TimeUnit.SECONDS);

            // Start keep-alive ping mechanism
            startPingScheduler();

        } catch (Exception e) {
            logger.error("Failed to connect to streaming API", e);
            isConnected.set(false);
            scheduleReconnect();
        }
    }

    /**
     * Gracefully disconnects from the streaming API
     */
    public synchronized void disconnect() {
        logger.info("Disconnecting from Oracle Hospitality Streaming API");
        shouldReconnect.set(false);
        closeSession();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("WebSocket connection established callback - Session: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        lastMessageReceived = Instant.now();

        try {
            String payload = message.getPayload();

            JsonNode jsonNode = objectMapper.readTree(payload);
            
            // Add null check before calling asText()
            JsonNode typeNode = jsonNode.get("type");
            if (typeNode == null) {
                logger.warn("Received message without 'type' field: {}", payload);
                return;
            }
            
            String messageType = typeNode.asText();

            switch (messageType) {
                case "connection_ack":
                    handleConnectionAck(jsonNode);
                    break;
                case "next":
                    handleEventMessage(jsonNode);
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
            logger.error("Error processing received message", e);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.error("WebSocket transport error occurred", exception);
        isConnected.set(false);
        closeSession();
        scheduleReconnect();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        logger.warn("WebSocket connection closed - Status: {} - Reason: {}",
                status.getCode(), status.getReason());
        isConnected.set(false);
        sessionRef.set(null);

        if (shouldReconnect.get()) {
            scheduleReconnect();
        } else {
            logger.info("Reconnection disabled. Not attempting to reconnect.");
        }
    }

    /**
     * Sends authentication (connection_init) message
     */
    private void sendAuthenticationMessage() {
        try {
            String authMessage = String.format(
                    "{\"id\":\"%s\",\"type\":\"connection_init\",\"payload\":{\"Authorization\":\"Bearer %s\",\"x-app-key\":\"%s\"}}",
                    sessionId, oauthToken, applicationKey
            );

            sendMessage(authMessage);
            logger.info("Authentication message sent");

        } catch (Exception e) {
            logger.error("Failed to send authentication message", e);
            closeSession();
            scheduleReconnect();
        }
    }

    /**
     * Sends subscription message to start receiving events
     */
    private void sendSubscriptionMessage() {
        try {
            String subscriptionQuery = String.format(
                    "subscription { newEvent(input: { chainCode: \\\"%s\\\" offset: \\\"0\\\"}) { " +
                            "metadata { offset } moduleName eventName detail { oldValue newValue elementName } } }",
                    chainCode
            );

            String subscriptionMessage = String.format(
                    "{\"id\":\"%s\",\"type\":\"subscribe\",\"payload\":{\"variables\":{},\"extensions\":{}," +
                            "\"operationName\":null,\"query\":\"%s\"}}",
                    sessionId, subscriptionQuery
            );

            sendMessage(subscriptionMessage);
            logger.info("Subscription message sent for chainCode: {}", chainCode);

        } catch (Exception e) {
            logger.error("Failed to send subscription message", e);
            closeSession();
            scheduleReconnect();
        }
    }

    /**
     * Sends ping message to keep connection alive (every 4 minutes)
     */
    private void sendPingMessage() {
        try {
            if (!isConnected.get()) {
                logger.debug("Not connected, skipping ping");
                return;
            }

            // Check if we've received any message in the last 5 minutes
            if (lastMessageReceived != null) {
                Duration timeSinceLastMessage = Duration.between(lastMessageReceived, Instant.now());
                if (timeSinceLastMessage.toMinutes() >= 5) {
                    logger.warn("No messages received in 5+ minutes. Connection may be dead. Reconnecting...");
                    closeSession();
                    scheduleReconnect();
                    return;
                }
            }

            String pingMessage = "{\"type\":\"ping\"}";
            sendMessage(pingMessage);
            logger.debug("Ping message sent");

        } catch (Exception e) {
            logger.error("Failed to send ping message", e);
            closeSession();
            scheduleReconnect();
        }
    }

    /**
     * Sends a message through the WebSocket session
     */
    private void sendMessage(String message) throws IOException {
        WebSocketSession session = sessionRef.get();
        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(message));
        } else {
            throw new IOException("WebSocket session is not open");
        }
    }

    /**
     * Handles connection acknowledgment message
     */
    private void handleConnectionAck(JsonNode jsonNode) {
        logger.info("Connection acknowledged by server");
        if (jsonNode.has("payload") && jsonNode.get("payload").has("applicationName")) {
            String appName = jsonNode.get("payload").get("applicationName").asText();
            logger.info("Connected application: {}", appName);
        }
    }

    /**
     * Handles incoming event messages
     * Override this method to implement custom event processing logic
     */
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

            // Process event details
            if (newEvent.has("detail")) {
                JsonNode detail = newEvent.get("detail");
                logger.debug("Event detail: {}", detail.toString());

                // TODO: Implement your business logic here
                processBusinessEvent(moduleName, eventName, offset, detail);
            }

        } catch (Exception e) {
            logger.error("Error handling event message", e);
        }
    }

    /**
     * Process business events - Override or implement based on your requirements
     */
    protected void processBusinessEvent(String moduleName, String eventName, long offset, JsonNode detail) {
        // Default implementation - log the event
        logger.info("Processing event: {}.{} at offset {}", moduleName, eventName, offset);

        // Implement your custom business logic here:
        // - Store events in database
        // - Trigger workflows
        // - Send notifications
        // - Update caches
        // etc.
    }

    /**
     * Handles error messages from server
     */
    private void handleErrorMessage(JsonNode jsonNode) {
        JsonNode errors = jsonNode.get("payload");
        logger.error("Error received from server: {}", errors.toString());

        // Depending on error type, may need to reconnect
        closeSession();
        scheduleReconnect();
    }

    /**
     * Handles completion messages
     */
    private void handleCompleteMessage(JsonNode jsonNode) {
        logger.info("Stream completed by server");
        closeSession();
        scheduleReconnect();
    }

    /**
     * Starts the ping scheduler to keep connection alive
     */
    private void startPingScheduler() {
        pingScheduler.scheduleAtFixedRate(
                this::sendPingMessage,
                pingIntervalSeconds,
                pingIntervalSeconds,
                TimeUnit.SECONDS
        );
        logger.info("Ping scheduler started with interval of {} seconds", pingIntervalSeconds);
    }

    /**
     * Schedules a reconnection attempt
     */
    private void scheduleReconnect() {
        if (!shouldReconnect.get()) {
            logger.info("Reconnection disabled. Not scheduling reconnect.");
            return;
        }

        logger.info("Scheduling reconnection attempt in {} seconds", reconnectDelaySeconds);
        reconnectScheduler.schedule(this::connect, reconnectDelaySeconds, TimeUnit.SECONDS);
    }

    /**
     * Closes the current WebSocket session
     */
    private void closeSession() {
        WebSocketSession session = sessionRef.getAndSet(null);
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.NORMAL);
                logger.info("WebSocket session closed");
            } catch (IOException e) {
                logger.error("Error closing WebSocket session", e);
            }
        }
        isConnected.set(false);
    }

    /**
     * Generates SHA-256 hash of the application key (must be lowercase)
     */
    private String generateSha256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Cleanup resources on application shutdown
     */
    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down Oracle Hospitality Streaming Client");
        shouldReconnect.set(false);
        closeSession();

        if (pingScheduler != null && !pingScheduler.isShutdown()) {
            pingScheduler.shutdown();
            try {
                if (!pingScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    pingScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                pingScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (reconnectScheduler != null && !reconnectScheduler.isShutdown()) {
            reconnectScheduler.shutdown();
            try {
                if (!reconnectScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    reconnectScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                reconnectScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("Oracle Hospitality Streaming Client shutdown complete");
    }

    // Getters for monitoring
    public boolean isConnected() {
        return isConnected.get();
    }

    public String getSessionId() {
        return sessionId;
    }

    public Instant getLastMessageReceived() {
        return lastMessageReceived;
    }

    /**
     * Configures WebSocket container with increased buffer sizes
     */
    private void configureWebSocketContainer() {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();

            // Set maximum text message buffer size (configurable)
            container.setDefaultMaxTextMessageBufferSize(maxTextMessageBufferSize);

            // Set maximum binary message buffer size
            container.setDefaultMaxBinaryMessageBufferSize(maxTextMessageBufferSize);

            // Set session idle timeout (5 minutes)
            container.setDefaultMaxSessionIdleTimeout(300000);

            webSocketClient = new StandardWebSocketClient(container);

            logger.info("WebSocket container configured - Max message size: {} bytes", maxTextMessageBufferSize);
        } catch (Exception e) {
            logger.error("Failed to configure WebSocket container", e);
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return true;  // Enable partial message support for large messages
    }
}