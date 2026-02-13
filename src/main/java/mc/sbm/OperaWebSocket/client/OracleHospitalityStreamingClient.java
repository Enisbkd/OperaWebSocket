package mc.sbm.OperaWebSocket.client;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import mc.sbm.OperaWebSocket.config.OracleHospitalityConfig;
import mc.sbm.OperaWebSocket.websocket.GraphQLWSProtocolHandler;
import mc.sbm.OperaWebSocket.websocket.MessageAssembler;
import mc.sbm.OperaWebSocket.websocket.OracleEventMessageProcessor;
import mc.sbm.OperaWebSocket.websocket.WebSocketConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Oracle Hospitality Integration Platform Streaming API Client
 * <p>
 * Orchestrates WebSocket connection, message handling, and reconnection logic
 */
@Service
public class OracleHospitalityStreamingClient extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(OracleHospitalityStreamingClient.class);
    private static final String PROTOCOL = "graphql-transport-ws";
    private static final Duration STALE_CONNECTION_THRESHOLD = Duration.ofMinutes(5);

    private final OracleHospitalityConfig config;
    private final OracleEventMessageProcessor messageProcessor;
    private final WebSocketConnectionManager connectionManager;
    private final GraphQLWSProtocolHandler protocolHandler;
    private final MessageAssembler messageAssembler;

    private ScheduledExecutorService pingScheduler;
    private ScheduledExecutorService reconnectScheduler;

    public OracleHospitalityStreamingClient(
            OracleHospitalityConfig config,
            OracleEventMessageProcessor messageProcessor) {
        this.config = config;
        this.messageProcessor = messageProcessor;
        this.connectionManager = new WebSocketConnectionManager();
        this.protocolHandler = new GraphQLWSProtocolHandler();
        this.messageAssembler = new MessageAssembler();
    }

    @PostConstruct
    public void init() {
        connectionManager.configureContainer(config.getMaxTextMessageBufferSize());

        if (config.getProxy().isEnabled() && !config.getProxy().getHost().isEmpty()) {
            connectionManager.configureProxy(config.getProxy().getHost(), config.getProxy().getPort());
        }

        initializeSchedulers();

        if (config.isAutoStart()) {
            logger.info("Auto-start enabled. Initiating connection to Oracle Hospitality Streaming API");
            connect();
        } else {
            logger.info("Auto-start disabled. Call connect() manually to establish connection");
        }
    }

    private void initializeSchedulers() {
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
    }

    /**
     * Establishes WebSocket connection
     */
    public synchronized void connect() {
        if (connectionManager.isConnected()) {
            logger.warn("Already connected to streaming API");
            return;
        }

        try {
            logger.info("Connecting to Oracle Hospitality Streaming API at {}", config.getUrl());

            String hashedKey = GraphQLWSProtocolHandler.generateSha256Hash(config.getAppKey());
            String urlWithParams = String.format("%s?key=%s", config.getUrl(), hashedKey);

            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.add("Sec-WebSocket-Protocol", PROTOCOL);

            WebSocketSession session = connectionManager.getWebSocketClient().execute(
                    this,
                    headers,
                    URI.create(urlWithParams)
            ).get(30, TimeUnit.SECONDS);

            connectionManager.setSession(session);
            logger.info("WebSocket connection established. Session ID: {}", session.getId());

            sendAuthenticationMessage();
            reconnectScheduler.schedule(this::sendSubscriptionMessage, 2, TimeUnit.SECONDS);
            startPingScheduler();

        } catch (Exception e) {
            logger.error("Failed to connect to streaming API", e);
            connectionManager.setConnected(false);
            scheduleReconnect();
        }
    }

    /**
     * Gracefully disconnects from the streaming API
     */
    public synchronized void disconnect() {
        logger.info("Disconnecting from Oracle Hospitality Streaming API");
        connectionManager.setShouldReconnect(false);

        // Send complete message before closing connection
        try {
            if (connectionManager.isConnected()) {
                String completeMessage = protocolHandler.createCompleteMessage();
                sendMessage(completeMessage);
                logger.info("Complete message sent for graceful disconnection");

                // Give server a moment to process the complete message
                Thread.sleep(500);
            }
        } catch (Exception e) {
            logger.warn("Failed to send complete message during disconnect", e);
        }

        connectionManager.closeSession();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("WebSocket connection established callback - Session: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        connectionManager.updateLastMessageReceived();

        try {
            String payload = message.getPayload();

            if (!message.isLast()) {
                logger.debug("Received partial message chunk ({} bytes), accumulating...", payload.length());
                messageAssembler.appendFragment(payload);
                return;
            }

            String completePayload;
            if (messageAssembler.hasFragments()) {
                messageAssembler.appendFragment(payload);
                completePayload = messageAssembler.getCompleteMessage();
            } else {
                completePayload = payload;
            }

            logger.debug("Processing complete message: {} bytes", completePayload.length());
            messageProcessor.processMessage(completePayload, protocolHandler.getSessionId());

        } catch (Exception e) {
            logger.error("Error processing received message", e);
            messageAssembler.clear();

            if (e.getMessage().contains("Server error")) {
                connectionManager.closeSession();
                scheduleReconnect();
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.error("WebSocket transport error occurred", exception);
        connectionManager.setConnected(false);
        connectionManager.closeSession();
        scheduleReconnect();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        logger.warn("WebSocket connection closed - Status: {} - Reason: {}",
                status.getCode(), status.getReason());
        connectionManager.setConnected(false);
        connectionManager.setSession(null);

        if (connectionManager.shouldReconnect()) {
            scheduleReconnect();
        } else {
            logger.info("Reconnection disabled. Not attempting to reconnect.");
        }
    }

    private void sendAuthenticationMessage() {
        try {
            String authMessage = protocolHandler.createAuthenticationMessage(
                    config.getOauthToken(),
                    config.getAppKey()
            );
            sendMessage(authMessage);
            logger.info("Authentication message sent");
        } catch (Exception e) {
            logger.error("Failed to send authentication message", e);
            connectionManager.closeSession();
            scheduleReconnect();
        }
    }

    private void sendSubscriptionMessage() {
        try {
            String subscriptionMessage = protocolHandler.createSubscriptionMessage(config.getChainCode());
            sendMessage(subscriptionMessage);
            logger.info("Subscription message sent for chainCode: {}", config.getChainCode());
        } catch (Exception e) {
            logger.error("Failed to send subscription message", e);
            connectionManager.closeSession();
            scheduleReconnect();
        }
    }

    private void sendPingMessage() {
        try {
            if (!connectionManager.isConnected()) {
                logger.debug("Not connected, skipping ping");
                return;
            }

            if (connectionManager.isConnectionStale(STALE_CONNECTION_THRESHOLD)) {
                logger.warn("No messages received in 5+ minutes. Connection may be dead. Reconnecting...");
                connectionManager.closeSession();
                scheduleReconnect();
                return;
            }

            String pingMessage = protocolHandler.createPingMessage();
            sendMessage(pingMessage);
            logger.debug("Ping message sent");

        } catch (Exception e) {
            logger.error("Failed to send ping message", e);
            connectionManager.closeSession();
            scheduleReconnect();
        }
    }

    private void sendMessage(String message) throws IOException {
        WebSocketSession session = connectionManager.getSession();
        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(message));
        } else {
            throw new IOException("WebSocket session is not open");
        }
    }

    private void startPingScheduler() {
        pingScheduler.scheduleAtFixedRate(
                this::sendPingMessage,
                config.getPingIntervalSeconds(),
                config.getPingIntervalSeconds(),
                TimeUnit.SECONDS
        );
        logger.info("Ping scheduler started with interval of {} seconds", config.getPingIntervalSeconds());
    }

    private void scheduleReconnect() {
        if (!connectionManager.shouldReconnect()) {
            logger.info("Reconnection disabled. Not scheduling reconnect.");
            return;
        }

        logger.info("Scheduling reconnection attempt in {} seconds", config.getReconnectDelaySeconds());
        reconnectScheduler.schedule(this::connect, config.getReconnectDelaySeconds(), TimeUnit.SECONDS);
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down Oracle Hospitality Streaming Client");
        connectionManager.setShouldReconnect(false);
        connectionManager.closeSession();

        shutdownScheduler(pingScheduler, "Ping");
        shutdownScheduler(reconnectScheduler, "Reconnect");

        logger.info("Oracle Hospitality Streaming Client shutdown complete");
    }

    private void shutdownScheduler(ScheduledExecutorService scheduler, String name) {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("{} scheduler shut down", name);
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return true;
    }

    // Monitoring getters
    public boolean isConnected() {
        return connectionManager.isConnected();
    }

    public String getSessionId() {
        return protocolHandler.getSessionId();
    }
}