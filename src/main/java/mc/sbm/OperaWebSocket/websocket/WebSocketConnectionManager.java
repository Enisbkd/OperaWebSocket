package mc.sbm.OperaWebSocket.websocket;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages WebSocket connection lifecycle and state
 */
@Service
public class WebSocketConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConnectionManager.class);

    private final AtomicReference<WebSocketSession> sessionRef = new AtomicReference<>();
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    private StandardWebSocketClient webSocketClient;
    private Instant lastMessageReceived;

    public WebSocketConnectionManager() {
        this.webSocketClient = new StandardWebSocketClient();
    }

    /**
     * Configures WebSocket container with buffer sizes
     */
    public void configureContainer(int maxTextMessageBufferSize) {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.setDefaultMaxTextMessageBufferSize(maxTextMessageBufferSize);
            container.setDefaultMaxBinaryMessageBufferSize(maxTextMessageBufferSize);
            container.setDefaultMaxSessionIdleTimeout(300000); // 5 minutes

            webSocketClient = new StandardWebSocketClient(container);
            logger.info("WebSocket container configured - Max message size: {} bytes", maxTextMessageBufferSize);
        } catch (Exception e) {
            logger.error("Failed to configure WebSocket container", e);
            throw new RuntimeException("Container configuration failed", e);
        }
    }

    /**
     * Configures proxy settings
     */
    public void configureProxy(String proxyHost, int proxyPort) {
        try {
            logger.info("Configuring proxy: {}:{}", proxyHost, proxyPort);

            System.setProperty("http.proxyHost", proxyHost);
            System.setProperty("http.proxyPort", String.valueOf(proxyPort));
            System.setProperty("https.proxyHost", proxyHost);
            System.setProperty("https.proxyPort", String.valueOf(proxyPort));

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

            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            webSocketClient = new StandardWebSocketClient(container);

            logger.info("Proxy configuration completed successfully");
        } catch (Exception e) {
            logger.error("Failed to configure proxy", e);
            throw new RuntimeException("Proxy configuration failed", e);
        }
    }

    public void setSession(WebSocketSession session) {
        sessionRef.set(session);
        isConnected.set(session != null && session.isOpen());
        if (session != null) {
            lastMessageReceived = Instant.now();
        }
    }

    public WebSocketSession getSession() {
        return sessionRef.get();
    }

    public StandardWebSocketClient getWebSocketClient() {
        return webSocketClient;
    }

    public boolean isConnected() {
        return isConnected.get();
    }

    public void setConnected(boolean connected) {
        isConnected.set(connected);
    }

    public boolean shouldReconnect() {
        return shouldReconnect.get();
    }

    public void setShouldReconnect(boolean reconnect) {
        shouldReconnect.set(reconnect);
    }

    public void updateLastMessageReceived() {
        lastMessageReceived = Instant.now();
    }

    public Instant getLastMessageReceived() {
        return lastMessageReceived;
    }

    /**
     * Checks if connection is stale (no messages received in specified duration)
     */
    public boolean isConnectionStale(Duration maxIdleDuration) {
        if (lastMessageReceived == null) {
            return false;
        }
        Duration timeSinceLastMessage = Duration.between(lastMessageReceived, Instant.now());
        return timeSinceLastMessage.compareTo(maxIdleDuration) > 0;
    }

    /**
     * Closes the current session
     */
    public void closeSession() {
        WebSocketSession session = sessionRef.getAndSet(null);
        if (session != null && session.isOpen()) {
            try {
                session.close();
                logger.info("WebSocket session closed");
            } catch (IOException e) {
                logger.error("Error closing WebSocket session", e);
            }
        }
        isConnected.set(false);
    }
}