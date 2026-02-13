//
//import lombok.Getter;
//import lombok.extern.slf4j.Slf4j;
//import mc.sbm.OperaRestHandler.config.HttpClientProperties;
//import mc.sbm.OperaRestHandler.config.OperaProperties;
//import mc.sbm.OperaRestHandler.dto.OAuthTokenResponse;
//import org.springframework.http.HttpStatusCode;
//import org.springframework.http.MediaType;
//import org.springframework.stereotype.Service;
//import org.springframework.util.LinkedMultiValueMap;
//import org.springframework.util.MultiValueMap;
//import org.springframework.web.reactive.function.BodyInserters;
//import org.springframework.web.reactive.function.client.WebClient;
//import reactor.core.publisher.Mono;
//import reactor.util.retry.Retry;
//
//import java.time.Duration;
//import java.time.Instant;
//import java.util.Base64;
//import java.util.concurrent.atomic.AtomicReference;
//
///**
// * Service responsible for OAuth token management with caching and automatic refresh.
// * Tokens are cached and reused until they expire, with a configurable buffer period.
// */
//@Slf4j
//@Service
//public class OAuthTokenService {
//
//    private final String token = "";
//
//    private static final int TOKEN_EXPIRY_BUFFER_SECONDS = 60;
//    private static final String GRANT_TYPE = "client_credentials";
//    private static final int MAX_RETRY_ATTEMPTS = 0;
//    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(2);
//    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
//
//
//    private final WebClient oauthWebClient;
//    private final HttpClientProperties httpClientProperties;
//    private final OperaProperties operaProperties;
//    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();
//
//    public OAuthTokenService(WebClient oauthWebClient, HttpClientProperties httpClientProperties, OperaProperties operaProperties) {
//        this.oauthWebClient = oauthWebClient;
//        this.httpClientProperties = httpClientProperties;
//        this.operaProperties = operaProperties;
//    }
//
//    /**
//     * Get a valid JWT token. Returns cached token if still valid, otherwise fetches a new one.
//     *
//     * @return Mono with the JWT access token
//     */
//    public Mono<String> getToken() {
//        CachedToken cached = cachedToken.get();
//
//        if (cached != null && cached.isValid()) {
//            log.debug("Using cached OAuth token (expires at: {})", cached.getExpiresAt());
//            return Mono.just(cached.getAccessToken());
//        }
//
//        log.info("Cached token expired or not found, fetching new OAuth token");
//        return fetchNewToken()
//                .onErrorResume(error -> {
//                    log.error("Failed to fetch token, returning error", error);
//                    return Mono.error(new OAuthException(
//                            "Unable to obtain OAuth token: " + error.getMessage(),
//                            500
//                    ));
//                });
//    }
//
//    /**
//     * Force refresh of the token, ignoring cache.
//     *
//     * @return Mono with the JWT access token
//     */
//    public Mono<String> refreshToken() {
//        log.info("Force refreshing OAuth token");
//        cachedToken.set(null);
//        return fetchNewToken();
//    }
//
//    /**
//     * Clear the cached token, forcing a refresh on next request.
//     */
//    public void clearCache() {
//        log.info("Clearing OAuth token cache");
//        cachedToken.set(null);
//    }
//
//    private Mono<String> fetchNewToken() {
//        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
//        formData.add("grant_type", GRANT_TYPE);
//        formData.add("scope", operaProperties.getScope());
//
//        String basicAuth = createBasicAuthHeader(
//                operaProperties.getClientId(),
//                operaProperties.getClientSecret()
//        );
//
//        log.debug("Requesting OAuth token from: {}", httpClientProperties.getBaseUrl() + "/operaproperty/v1/oauth/v1/tokens");
//
//        return oauthWebClient.post()
//                .uri("https://" + httpClientProperties.getBaseUrl() + "/operaproperty/v1/oauth/v1/tokens")
//                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
//                .header("Authorization", basicAuth)
//                .header("x-app-key", operaProperties.getAppKey())
//                .header("x-api-key", httpClientProperties.getApiKey())
//                .header("enterpriseId", operaProperties.getEnterpriseId())
//                .body(BodyInserters.fromFormData(formData))
//                .retrieve()
//                .onStatus(
//                        HttpStatusCode::is4xxClientError,
//                        response -> response.bodyToMono(String.class)
//                                .defaultIfEmpty("No error details provided")
//                                .flatMap(errorBody -> {
//                                    log.error("OAuth authentication failed ({}): {}",
//                                            response.statusCode(), errorBody);
//                                    return Mono.error(new OAuthException(
//                                            "OAuth authentication failed: " + errorBody,
//                                            response.statusCode().value()
//                                    ));
//                                })
//                )
//                .onStatus(
//                        HttpStatusCode::is5xxServerError,
//                        response -> response.bodyToMono(String.class)
//                                .defaultIfEmpty("No error details provided")
//                                .flatMap(errorBody -> {
//                                    log.error("OAuth server error ({}): {}",
//                                            response.statusCode(), errorBody);
//                                    return Mono.error(new OAuthException(
//                                            "OAuth server error: " + errorBody,
//                                            response.statusCode().value()
//                                    ));
//                                })
//                )
//                .bodyToMono(OAuthTokenResponse.class)
//                .timeout(REQUEST_TIMEOUT, Mono.error(new OAuthException(
//                        "OAuth token request timed out after " + REQUEST_TIMEOUT.getSeconds() + " seconds",
//                        504
//                )))
//                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_BACKOFF)
//                        .filter(throwable -> {
//                            // Only retry on timeout or server errors, not on client errors
//                            if (throwable instanceof OAuthException) {
//                                int statusCode = ((OAuthException) throwable).getStatusCode();
//                                return statusCode >= 500 || statusCode == 504;
//                            }
//                            return true; // Retry on other exceptions (network issues, etc.)
//                        })
//                        .doBeforeRetry(retrySignal ->
//                                log.warn("Retrying OAuth token request (attempt {}/{})",
//                                        retrySignal.totalRetries() + 1, MAX_RETRY_ATTEMPTS))
//                )
//                .map(this::cacheAndReturnToken)
//                .doOnSuccess(token -> log.info("Successfully obtained and cached new OAuth token"))
//                .doOnError(error -> log.error("Failed to obtain OAuth token after {} retries: {}",
//                        MAX_RETRY_ATTEMPTS, error.getMessage()));
//    }
//
//    private String cacheAndReturnToken(OAuthTokenResponse response) {
//        if (response == null || response.getAccessToken() == null) {
//            throw new OAuthException("Received null or invalid token response", 500);
//        }
//
//        String token = response.getAccessToken();
//        Instant expiresAt = Instant.now().plusSeconds(response.getExpiresIn());
//
//        cachedToken.set(new CachedToken(token, expiresAt));
//
//        log.debug("Token cached, expires at: {}", expiresAt);
//        return token;
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
//
//    /**
//     * Holds a cached token with its expiration time.
//     */
//    private static class CachedToken {
//        private final String accessToken;
//        private final Instant expiresAt;
//
//        CachedToken(String accessToken, Instant expiresAt) {
//            this.accessToken = accessToken;
//            this.expiresAt = expiresAt;
//        }
//
//        String getAccessToken() {
//            return accessToken;
//        }
//
//        Instant getExpiresAt() {
//            return expiresAt;
//        }
//
//        boolean isValid() {
//            return expiresAt != null &&
//                    Instant.now().plusSeconds(OAuthTokenService.TOKEN_EXPIRY_BUFFER_SECONDS).isBefore(expiresAt);
//        }
//    }
//
//    /**
//     * Custom exception for OAuth-related errors.
//     */
//    @Getter
//    public static class OAuthException extends RuntimeException {
//        private final int statusCode;
//
//        public OAuthException(String message, int statusCode) {
//            super(message);
//            this.statusCode = statusCode;
//        }
//
//    }
//}
