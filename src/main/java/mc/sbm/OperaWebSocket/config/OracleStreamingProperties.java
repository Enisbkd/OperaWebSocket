package mc.sbm.OperaWebSocket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "oracle.hospitality.streaming")
public class OracleStreamingProperties {

    private String baseUrl;
    private String applicationKey;
    private String chainCode;
    private OAuth oauth = new OAuth();
    private long pingInterval = 15000;
    private long reconnectDelay = 5000;
    private int maxReconnectAttempts = 10;

    // Getters and setters
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApplicationKey() { return applicationKey; }
    public void setApplicationKey(String applicationKey) { this.applicationKey = applicationKey; }

    public String getChainCode() { return chainCode; }
    public void setChainCode(String chainCode) { this.chainCode = chainCode; }

    public OAuth getOauth() { return oauth; }
    public void setOauth(OAuth oauth) { this.oauth = oauth; }

    public long getPingInterval() { return pingInterval; }
    public void setPingInterval(long pingInterval) { this.pingInterval = pingInterval; }

    public long getReconnectDelay() { return reconnectDelay; }
    public void setReconnectDelay(long reconnectDelay) { this.reconnectDelay = reconnectDelay; }

    public int getMaxReconnectAttempts() { return maxReconnectAttempts; }
    public void setMaxReconnectAttempts(int maxReconnectAttempts) { this.maxReconnectAttempts = maxReconnectAttempts; }

    public static class OAuth {
        private String clientId;
        private String clientSecret;
        private String tokenUrl;

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
    }
}