//package mc.sbm.OperaWebSocket.service;
//
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import mc.sbm.OperaWebSocket.config.OracleStreamingProperties;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.*;
//import org.springframework.stereotype.Service;
//import org.springframework.util.LinkedMultiValueMap;
//import org.springframework.util.MultiValueMap;
//import org.springframework.web.client.RestTemplate;
//
//import java.time.LocalDateTime;
//import java.util.Base64;
//
//@Service
//public class OAuthService {
//
//    private static final Logger logger = LoggerFactory.getLogger(OAuthService.class);
//    private static final int TOKEN_EXPIRY_BUFFER_MINUTES = 1; // Refresh 1 minute early
//
//    private final OracleStreamingProperties properties;
//    private final RestTemplate restTemplate;
//    private final ObjectMapper objectMapper;
//
//    private String accessToken;
//    private LocalDateTime tokenExpiry;
//
//    @Autowired
//    public OAuthService(OracleStreamingProperties properties) {
//        this.properties = properties;
//        this.restTemplate = new RestTemplate();
//        this.objectMapper = new ObjectMapper();
//    }
//
//    public String getValidAccessToken() {
//        if (accessToken == null || isTokenExpired()) {
//            refreshToken();
//        }
//        return accessToken;
//    }
//
//    private boolean isTokenExpired() {
//        return tokenExpiry == null || LocalDateTime.now().isAfter(tokenExpiry.minusMinutes(TOKEN_EXPIRY_BUFFER_MINUTES));
//    }
//
//    private void refreshToken() {
//        try {
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
//
//            // Use Basic Authentication header instead of sending credentials in form body
//            String basicAuth = createBasicAuthHeader(
//                    properties.getOauth().getClientId(),
//                    properties.getOauth().getClientSecret()
//            );
//            headers.set("Authorization", basicAuth);
//
//            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
//            body.add("grant_type", "client_credentials");
//            // Remove client_id and client_secret from body - they're now in the Authorization header
//
//            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
//
//            logger.debug("Requesting OAuth token from: {}", properties.getOauth().getTokenUrl());
//
//            ResponseEntity<String> response = restTemplate.exchange(
//                    properties.getOauth().getTokenUrl(),
//                    HttpMethod.POST,
//                    request,
//                    String.class
//            );
//
//            if (response.getStatusCode() == HttpStatus.OK) {
//                JsonNode tokenResponse = objectMapper.readTree(response.getBody());
//
//                if (tokenResponse == null || tokenResponse.get("access_token") == null) {
//                    throw new RuntimeException("Received null or invalid token response");
//                }
//
//                accessToken = tokenResponse.get("access_token").asText();
//                int expiresIn = tokenResponse.get("expires_in").asInt();
//                tokenExpiry = LocalDateTime.now().plusSeconds(expiresIn);
//
//                logger.info("OAuth token refreshed successfully, expires in {} seconds", expiresIn);
//            } else {
//                logger.error("Failed to refresh OAuth token: {}", response.getStatusCode());
//                throw new RuntimeException("Failed to get OAuth token - HTTP " + response.getStatusCode());
//            }
//        } catch (Exception e) {
//            logger.error("Error refreshing OAuth token", e);
//            throw new RuntimeException("Failed to get OAuth token", e);
//        }
//    }
//
//    private String createBasicAuthHeader(String username, String password) {
//        if (username == null || password == null) {
//            throw new IllegalArgumentException("Client ID and secret cannot be null");
//        }
//        String credentials = username + ":" + password;
//        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
//        return "Basic " + encoded;
//    }
//}