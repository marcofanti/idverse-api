package org.itnaf.idverse.client.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.itnaf.idverse.client.model.OAuthTokenResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthTokenService {

    private final WebClient webClient;
    private final String idverseClientId;
    private final String idverseClientSecret;
    private final String idverseOAuthUrl;
    private final String verboseMode;

    private String cachedAccessToken;
    private Instant tokenExpiryTime;

    public synchronized String getAccessToken() {
        log.debug("Checking OAuth token status...");

        if (isTokenValid()) {
            long secondsUntilExpiry = Duration.between(Instant.now(), tokenExpiryTime).getSeconds();
            log.debug("✓ Using cached access token (expires in {} seconds)", secondsUntilExpiry);
            String maskedToken = cachedAccessToken.substring(0, Math.min(20, cachedAccessToken.length())) + "...";
            log.debug("Token preview: {}", maskedToken);
            return cachedAccessToken;
        }

        if (cachedAccessToken != null) {
            log.info("Cached token expired or expiring soon, fetching new token...");
        } else {
            log.info("No cached token found, fetching new token...");
        }

        return fetchNewToken();
    }

    private boolean isTokenValid() {
        if (cachedAccessToken == null || tokenExpiryTime == null) {
            log.debug("Token validation: No cached token available");
            return false;
        }

        Instant now = Instant.now();
        boolean valid = now.isBefore(tokenExpiryTime);

        if (!valid) {
            long secondsUntilExpiry = Duration.between(now, tokenExpiryTime).getSeconds();
            log.debug("Token validation: Token expired {} seconds ago", Math.abs(secondsUntilExpiry));
        }

        return valid;
    }

    private String fetchNewToken() {
        try {
            log.debug("=== Fetching OAuth Token ===");
            log.debug("OAuth URL: {}", idverseOAuthUrl);

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "client_credentials");
            formData.add("client_id", idverseClientId);
            formData.add("client_secret", idverseClientSecret);

            if ("SECRET".equalsIgnoreCase(verboseMode)) {
                System.out.println("=== COMPLETE POST REQUEST (SECRET MODE) ===");
                System.out.println("URL: " + idverseOAuthUrl);
                System.out.println("Method: POST");
                System.out.println("Content-Type: application/x-www-form-urlencoded");
                System.out.println("Request Parameters:");
                System.out.println("  grant_type: " + formData.getFirst("grant_type"));
                System.out.println("  client_id: " + idverseClientId);
                System.out.println("  client_secret: " + idverseClientSecret);
                System.out.println("===========================================");
            } else {
                log.debug("Client ID: {}", idverseClientId);
                log.debug("Client Secret: {}", maskSecret(idverseClientSecret));
            }

            log.debug("Sending POST request to OAuth endpoint...");
            OAuthTokenResponse response = webClient.post()
                    .uri(idverseOAuthUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(OAuthTokenResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            log.debug("✓ Received response from OAuth endpoint");

            if (response == null) {
                log.error("✗ OAuth response is null");
                throw new RuntimeException("OAuth token response is null");
            }

            if (response.hasError()) {
                log.error("✗ OAuth request failed with error");
                log.error("Error: {}", response.getError());
                log.error("Description: {}", response.getErrorDescription());
                log.error("Hint: {}", response.getHint());
                log.error("Message: {}", response.getMessage());

                String errorMsg = String.format("OAuth error: %s - %s (hint: %s, message: %s)",
                        response.getError(),
                        response.getErrorDescription(),
                        response.getHint(),
                        response.getMessage());
                throw new RuntimeException(errorMsg);
            }

            if (!response.isSuccess()) {
                log.error("✗ OAuth response does not contain access_token");
                throw new RuntimeException("OAuth token response does not contain access_token");
            }

            cachedAccessToken = response.getAccessToken();
            int expiresIn = 800;
            tokenExpiryTime = Instant.now().plusSeconds(expiresIn);

            String maskedToken = cachedAccessToken.substring(0, Math.min(20, cachedAccessToken.length())) + "...";
            log.info("✓ Successfully obtained access token");
            log.info("Token Type: {}", response.getTokenType());
            log.info("Token cached for: {} seconds ({} minutes)", expiresIn, expiresIn / 60);
            log.info("Token Expiry Time: {}", tokenExpiryTime);
            log.debug("Token Preview: {}", maskedToken);
            log.debug("===========================");

            return cachedAccessToken;

        } catch (WebClientResponseException e) {
            log.error("=== OAuth Request Failed ===");
            log.error("HTTP Status: {}", e.getStatusCode());
            log.error("Status Text: {}", e.getStatusText());
            log.error("Response Headers: {}", e.getHeaders());
            log.error("Response Body: {}", e.getResponseBodyAsString());
            log.error("============================");
            throw new RuntimeException("Failed to obtain OAuth token: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("=== OAuth Request Exception ===");
            log.error("Exception Type: {}", e.getClass().getName());
            log.error("Error Message: {}", e.getMessage());
            log.error("===============================", e);
            throw new RuntimeException("Failed to obtain OAuth token: " + e.getMessage(), e);
        }
    }

    public synchronized void clearToken() {
        log.info("=== Clearing OAuth Token Cache ===");
        if (cachedAccessToken != null) {
            log.debug("Removing cached token: {}...", cachedAccessToken.substring(0, Math.min(20, cachedAccessToken.length())));
            log.debug("Token was set to expire at: {}", tokenExpiryTime);
        } else {
            log.debug("No cached token to clear");
        }
        cachedAccessToken = null;
        tokenExpiryTime = null;
        log.info("✓ Token cache cleared successfully");
        log.info("==================================");
    }

    public OAuthTokenResponse testConnection() {
        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "client_credentials");
            formData.add("client_id", idverseClientId);
            formData.add("client_secret", idverseClientSecret);

            OAuthTokenResponse response = webClient.post()
                    .uri(idverseOAuthUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(OAuthTokenResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            return response;

        } catch (WebClientResponseException e) {
            log.error("OAuth test failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            OAuthTokenResponse errorResponse = new OAuthTokenResponse();
            errorResponse.setError("http_error");
            errorResponse.setMessage("HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
            return errorResponse;
        } catch (Exception e) {
            log.error("OAuth test failed: {}", e.getMessage(), e);
            OAuthTokenResponse errorResponse = new OAuthTokenResponse();
            errorResponse.setError("exception");
            errorResponse.setMessage(e.getMessage());
            return errorResponse;
        }
    }

    public Map<String, Object> testConnectionVerbose(boolean debug) {
        Map<String, Object> result = new HashMap<>();

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", idverseClientId);
        formData.add("client_secret", idverseClientSecret);

        if (debug) {
            Map<String, Object> requestDetails = new HashMap<>();
            requestDetails.put("url", idverseOAuthUrl);
            requestDetails.put("method", "POST");
            requestDetails.put("content_type", "application/x-www-form-urlencoded");

            Map<String, String> requestParams = new HashMap<>();
            requestParams.put("grant_type", "client_credentials");
            requestParams.put("client_id", idverseClientId);
            requestParams.put("client_secret", maskSecret(idverseClientSecret));
            requestDetails.put("parameters", requestParams);

            result.put("request", requestDetails);

            log.info("=== OAuth Request Details ===");
            log.info("URL: {}", idverseOAuthUrl);
            log.info("Method: POST");
            log.info("Content-Type: application/x-www-form-urlencoded");
            log.info("Parameters:");
            log.info("  grant_type: client_credentials");
            log.info("  client_id: {}", idverseClientId);
            log.info("  client_secret: {}", maskSecret(idverseClientSecret));
            log.info("=============================");
        }

        try {
            String rawResponse = webClient.post()
                    .uri(idverseOAuthUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (debug) {
                log.info("=== OAuth Raw Response ===");
                log.info("Response Body: {}", rawResponse);
                log.info("==========================");

                result.put("raw_response", rawResponse);
            }

            OAuthTokenResponse response = webClient.post()
                    .uri(idverseOAuthUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(OAuthTokenResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (response != null && response.isSuccess()) {
                result.put("status", "SUCCESS");
                result.put("message", "OAuth token obtained successfully");
                result.put("token_type", response.getTokenType());
                result.put("expires_in", response.getExpiresIn());
                result.put("access_token_preview", response.getAccessToken().substring(0, Math.min(20, response.getAccessToken().length())) + "...");

                if (debug) {
                    result.put("access_token_full", response.getAccessToken());
                    log.info("OAuth token obtained successfully");
                    log.info("Token Type: {}", response.getTokenType());
                    log.info("Expires In: {} seconds", response.getExpiresIn());
                    log.info("Access Token: {}", response.getAccessToken());
                }
            } else if (response != null) {
                result.put("status", "FAILURE");
                result.put("error", response.getError());
                result.put("error_description", response.getErrorDescription());
                result.put("hint", response.getHint());
                result.put("message", response.getMessage());

                if (debug) {
                    log.error("OAuth request failed:");
                    log.error("Error: {}", response.getError());
                    log.error("Description: {}", response.getErrorDescription());
                    log.error("Hint: {}", response.getHint());
                    log.error("Message: {}", response.getMessage());
                }
            } else {
                result.put("status", "FAILURE");
                result.put("message", "Response is null");
            }

            return result;

        } catch (WebClientResponseException e) {
            String errorBody = e.getResponseBodyAsString();

            if (debug) {
                log.error("=== OAuth Error Response ===");
                log.error("HTTP Status: {}", e.getStatusCode());
                log.error("Response Body: {}", errorBody);
                log.error("============================");

                result.put("raw_error_response", errorBody);
                result.put("http_status", e.getStatusCode().value());
            }

            result.put("status", "FAILURE");
            result.put("error", "http_error");
            result.put("message", "HTTP " + e.getStatusCode() + ": " + errorBody);

            return result;

        } catch (Exception e) {
            if (debug) {
                log.error("=== OAuth Exception ===");
                log.error("Exception Type: {}", e.getClass().getName());
                log.error("Exception Message: {}", e.getMessage());
                log.error("=======================", e);
            }

            result.put("status", "ERROR");
            result.put("error", "exception");
            result.put("exception_type", e.getClass().getName());
            result.put("message", e.getMessage());

            return result;
        }
    }

    private String maskSecret(String secret) {
        if (secret == null || secret.length() <= 8) {
            return "****";
        }
        return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
    }
}
