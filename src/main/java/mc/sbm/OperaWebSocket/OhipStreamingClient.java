package mc.sbm.OperaWebSocket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
public class OhipStreamingClient implements CommandLineRunner {

    // TODO: set these from env/config
    private static final String TOKEN_URL = "https://mucu1ua.hospitality-api.us-ashburn-1.ocs.oc-test.com/oauth/v1/tokens";
    private static final String WEBSOCKET_URL = "wss://mucu1ua.hospitality-api.us-ashburn-1.ocs.oc-test.com/ohip-ws/subscriptions?key=50a5d8576f7288ce63f5b9ce588416a4b710c6cdc0f83a18989cc94f6be4023c";
    private static final String CLIENT_ID = "SBMDEVE-UAT-SBMDEV-mucu1ua-SBMACCELERATOR";
    private static final String CLIENT_SECRET = "8aa5c347-32e2-4d25-8bac-7264b7e1752d";
    private static final String APP_KEY = "21813bcd-a15b-4439-9b9e-66fa3d8fe4df";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        SpringApplication.run(OhipStreamingClient.class, args);
    }

    @Override
    public void run(String... args) throws Exception {

        String token = "eyJ4NXQjUzI1NiI6ImlJV2ZQWU5GZFc3elNIaklkRnRMUHY2R1JKX09jQXFiTVhnVWxSNTlPX00iLCJ4NXQiOiJmYWNlRFR3Uk45WXUyZzEyRkI3akRLZ2d5ZnMiLCJraWQiOiJTSUdOSU5HX0tFWSIsImFsZyI6IlJTMjU2In0.eyJjbGllbnRfb2NpZCI6Im9jaWQxLmRvbWFpbmFwcC5vYzEuZXUtZnJhbmtmdXJ0LTEuYW1hYWFhYWFhcXRwNWJhYXFqbzMza29tN2lweDI0bmV6aGpxcGN2ZjU2NmFqZ2FoYmJ0YnVzZnQ2ZXBhIiwic3ViIjoiU0JNREVWRS1VQVQtU0JNREVWLW11Y3UxdWEtU0JNQUNDRUxFUkFUT1IiLCJzaWRsZSI6MTUsInVzZXIudGVuYW50Lm5hbWUiOiJpZGNzLTQxMGFjOGU5ZTAwYzQ1NDA5MzFjNThiNzJkZTczMGY1IiwiaXNzIjoiaHR0cHM6Ly9pZGVudGl0eS5vcmFjbGVjbG91ZC5jb20vIiwiZG9tYWluX2hvbWUiOiJldS1mcmFua2Z1cnQtMSIsIk9DX0VudGVycHJpc2UiOiJTQk1ERVYiLCJjYV9vY2lkIjoib2NpZDEudGVuYW5jeS5vYzEuLmFhYWFhYWFheXRpbmNjaWtuaDI0dHl5bHp5cWd6NW9ybTJ1enFpZ2t4NGFzdWU2eGsyeHAzY21rYmxqcSIsImNsaWVudF9pZCI6IlNCTURFVkUtVUFULVNCTURFVi1tdWN1MXVhLVNCTUFDQ0VMRVJBVE9SIiwiZG9tYWluX2lkIjoib2NpZDEuZG9tYWluLm9jMS4uYWFhYWFhYWE1dWt3d2w3d3NvbnJrYXBnd3A3ZHN5eXFwYXl4aXVtdGRuZWd3bjMzcXhnbWJhZWdsdmZxIiwic3ViX3R5cGUiOiJjbGllbnQiLCJzY29wZSI6IkM6U0JNREVWRSIsImNsaWVudF90ZW5hbnRuYW1lIjoiaWRjcy00MTBhYzhlOWUwMGM0NTQwOTMxYzU4YjcyZGU3MzBmNSIsInJlZ2lvbl9uYW1lIjoiZXUtZnJhbmtmdXJ0LWlkY3MtMSIsImV4cCI6MTc3MTAwODY3NywiaWF0IjoxNzcwOTc5ODc3LCJjbGllbnRfZ3VpZCI6ImZmZmJkNTlhMTEwMjQzMDQ4M2U0MTUyN2I1ODAyNTZhIiwiY2xpZW50X25hbWUiOiJTQk1BQ0NFTEVSQVRPUiIsInRlbmFudCI6ImlkY3MtNDEwYWM4ZTllMDBjNDU0MDkzMWM1OGI3MmRlNzMwZjUiLCJqdGkiOiIwMzE5ZDliZGRiM2M0YjFkOWQxZmQyY2EzNTU1YzM0ZCIsImd0cCI6ImNjIiwib3BjIjpmYWxzZSwic3ViX21hcHBpbmdhdHRyIjoidXNlck5hbWUiLCJwcmltVGVuYW50IjpmYWxzZSwidG9rX3R5cGUiOiJBVCIsImF1ZCI6InVybjpvcGM6aGdidTp3czoiLCJjYV9uYW1lIjoibW9udGVjYXJsb3NibSIsImRvbWFpbiI6InBtc2QzX29wZXJhX2Nsb3VkX2RldiIsInRlbmFudF9pc3MiOiJodHRwczovL2lkY3MtNDEwYWM4ZTllMDBjNDU0MDkzMWM1OGI3MmRlNzMwZjUuaWRlbnRpdHkub3JhY2xlY2xvdWQuY29tOjQ0MyIsInJlc291cmNlX2FwcF9pZCI6IjFiM2MxYzFiZjE4ZjQ1NTk5NWZkZDZmYjU0NDk1ZTc0In0.B7D6aVNNp3EYoM4j6F6kkBHE2322XqJXj8JTbzga07vimSS1RfFP0EDadOGyJ7cwJXTNz2TGggYkLmbnQxDEfv0lI86ifrvlVWn__tr5QP-pHRgLNe-Jyj9oTyKnqh6-64EYZXIMjvjTImaA-PAWMJtf3CwvWmComZftCVsrtqE_kRYUBVCF01qla4Hjnbd0WZpv7CaUAh8OhnXH5zMCPNFxMY_4Ay3RuIkNAPlzHpZkvPmiycK7zLVXaehbP5D6U7lrKIg47Rzhjh-QKTmtj1n7t-b1hlqvNAYVcUUESF5VdEd0_7jgrEkXLooNxnoPjCrEMNxShKo2iX8IgQXknw";
        System.out.println("OAuth Token: " + token);

        WebSocketClient wsClient = new WebSocketClient(new URI(WEBSOCKET_URL)) {

            @Override
            public void onOpen(ServerHandshake handshakedata) {
                System.out.println("WebSocket OPEN");

                try {
                    // 1) connection_init
                    var initId = UUID.randomUUID().toString();
                    var initMsg = Map.of(
                            "id", initId,
                            "type", "connection_init",
                            "payload", Map.of(
                                    "Authorization", "Bearer " + token,
                                    "x-app-key", APP_KEY
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
                    Map<?,?> msg = objectMapper.readValue(message, Map.class);
                    String type = (String) msg.get("type");

                    // after connection_ack, send subscription
                    if ("connection_ack".equals(type)) {
                        var subId = UUID.randomUUID().toString();
                        var subscription = Map.of(
                                "id", subId,
                                "type", "subscribe",
                                "payload", Map.of(
                                        "query",
                                        "subscription { newEvent(input: { chainCode: \"SBMDEVE\" }) { metadata { offset } moduleName eventName detail { oldValue newValue elementName } } }"
                                )
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
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("10.62.1.10", 3128));
        wsClient.setProxy(proxy);
        wsClient.connect();
    }
    private String fetchOAuthToken() {

        // =======================
        // PROXY FOR REST CALL
        // =======================
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("10.62.1.10", 3128));

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setProxy(proxy);

        RestTemplate rt = new RestTemplate(requestFactory);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", "urn:opc:hgbu:ws:__myscopes__");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(CLIENT_ID, CLIENT_SECRET, StandardCharsets.UTF_8);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        ResponseEntity<Map> response = rt.postForEntity(TOKEN_URL, request, Map.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            return (String) response.getBody().get("access_token");
        }

        throw new RuntimeException("Failed to fetch OAuth token: " + response.getStatusCode());
    }

}
