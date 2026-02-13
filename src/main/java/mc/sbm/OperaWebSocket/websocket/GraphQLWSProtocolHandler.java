package mc.sbm.OperaWebSocket.websocket;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Handles GraphQL-WS protocol message formatting
 */
public class GraphQLWSProtocolHandler {

    private final String sessionId;

    public GraphQLWSProtocolHandler() {
        this.sessionId = UUID.randomUUID().toString();
    }

    public String getSessionId() {
        return sessionId;
    }

    /**
     * Creates authentication (connection_init) message
     */
    public String createAuthenticationMessage(String oauthToken, String applicationKey) {
        return String.format(
                "{\"id\":\"%s\",\"type\":\"connection_init\",\"payload\":{\"Authorization\":\"Bearer %s\",\"x-app-key\":\"%s\"}}",
                sessionId, oauthToken, applicationKey
        );
    }

    /**
     * Creates subscription message
     */
    public String createSubscriptionMessage(String chainCode) {
        String subscriptionQuery = String.format(
                "subscription { newEvent(input: { chainCode: \\\"%s\\\" offset: \\\"0\\\"}) { " +
                        "metadata { offset } moduleName eventName detail { oldValue newValue elementName } } }",
                chainCode
        );

        return String.format(
                "{\"id\":\"%s\",\"type\":\"subscribe\",\"payload\":{\"variables\":{},\"extensions\":{}," +
                        "\"operationName\":null,\"query\":\"%s\"}}",
                sessionId, subscriptionQuery
        );
    }

    /**
     * Creates ping message
     */
    public String createPingMessage() {
        return "{\"type\":\"ping\"}";
    }

    /**
     * Generates SHA-256 hash of the application key (lowercase)
     */
    public static String generateSha256Hash(String input) {
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
}