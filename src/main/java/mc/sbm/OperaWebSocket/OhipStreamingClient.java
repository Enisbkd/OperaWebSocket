
package mc.sbm.OperaWebSocket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.fasterxml.jackson.databind.ObjectMapper;
import mc.sbm.OperaWebSocket.config.OracleStreamingProperties;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@SpringBootApplication
public class OhipStreamingClient {

    public static void main(String[] args) {
        SpringApplication.run(OhipStreamingClient.class, args);
    }

    @Component
    public static class WebSocketRunner implements CommandLineRunner {

        private final OracleStreamingProperties properties;
        private final ObjectMapper objectMapper;

        @Autowired
        public WebSocketRunner(OracleStreamingProperties properties) {
            this.properties = properties;
            this.objectMapper = new ObjectMapper();
        }

        @Override
        public void run(String... args) throws Exception {
            String token = fetchOAuthToken();
            System.out.println("OAuth Token: " + token);

            String wsUrl = properties.getBaseUrl() + "/ohip-ws/subscriptions?key=" + properties.getWebsocketKey();

            WebSocketClient wsClient = new WebSocketClient(new URI(wsUrl)) {

                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    System.out.println("WebSocket OPEN");

                    try {
                        // 1) connection_init
                        String initId = UUID.randomUUID().toString();
                        Map<String, Object> initMsg = Map.of(
                                "id", initId,
                                "type", "connection_init",
                                "payload", Map.of(
                                        "Authorization", "Bearer " + token,
                                        "x-app-key", properties.getApplicationKey()
                                )
                        );

                        send(objectMapper.writeValueAsString(initMsg));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onMessage(String message) {
                    System.out.println("RAW MESSAGE: " + message);

                    try {
                        Map<?, ?> msg = objectMapper.readValue(message, Map.class);
                        String type = (String) msg.get("type");

                        // after connection_ack, send subscription
                        if ("connection_ack".equals(type)) {
                            String subId = UUID.randomUUID().toString();
                            String query = String.format(
                                    "subscription { newEvent(input: { chainCode: \"%s\" }) { metadata { offset } moduleName eventName detail { oldValue newValue elementName } } }",
                                    properties.getChainCode()
                            );

                            Map<String, Object> subscription = Map.of(
                                    "id", subId,
                                    "type", "subscribe",
                                    "payload", Map.of("query", query)
                            );
                            send(objectMapper.writeValueAsString(subscription));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("WebSocket CLOSED: " + code + " " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    ex.printStackTrace();
                }
            };

            // set required subprotocol
            wsClient.addHeader("Sec-WebSocket-Protocol", "graphql-transport-ws");

            // Add proxy if enabled
            if (properties.getProxy() != null && properties.getProxy().isEnabled()) {
                Proxy proxy = new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress(properties.getProxy().getHost(), properties.getProxy().getPort()));
                wsClient.setProxy(proxy);
            }

            wsClient.connect();
        }

        private String fetchOAuthToken() {
            RestTemplate rt;

            // Configure proxy if enabled
            if (properties.getProxy() != null && properties.getProxy().isEnabled()) {
                Proxy proxy = new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress(properties.getProxy().getHost(), properties.getProxy().getPort()));

                SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
                requestFactory.setProxy(proxy);
                rt = new RestTemplate(requestFactory);
            } else {
                rt = new RestTemplate();
            }

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("scope", properties.getOauth().getScope());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(
                    properties.getOauth().getClientId(),
                    properties.getOauth().getClientSecret(),
                    StandardCharsets.UTF_8
            );

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

            ResponseEntity<Map> response = rt.postForEntity(
                    properties.getOauth().getTokenUrl(),
                    request,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }

            throw new RuntimeException("Failed to fetch OAuth token: " + response.getStatusCode());
        }
    }
}