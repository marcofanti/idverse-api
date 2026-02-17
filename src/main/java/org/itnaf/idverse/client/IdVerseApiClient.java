package org.itnaf.idverse.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.itnaf.idverse.client.model.VerificationRequest;
import org.itnaf.idverse.client.service.JwtService;
import org.itnaf.idverse.client.service.OAuthTokenService;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.HashMap;

/**
 * Client for the IDVerse verification API.
 * Handles OAuth authentication, webhook configuration, and HTTP communication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdVerseApiClient {

    private final OAuthTokenService oAuthTokenService;
    private final JwtService jwtService;
    private final WebClient webClient;
    private final String idverseApiUrl;
    private final String verboseMode;
    private final String notifyUrlComplete;
    private final String notifyUrlEvent;

    /**
     * Sends a verification request to the IDVerse API.
     *
     * @param request the verification request (transactionId must already be set)
     * @return the raw JSON response body from the API
     * @throws RuntimeException if the API call fails
     */
    public String sendVerification(VerificationRequest request) {
        try {
            log.debug("=== Preparing API Call to IDVerse ===");

            String accessToken = oAuthTokenService.getAccessToken();
            String maskedToken = accessToken.substring(0, Math.min(20, accessToken.length())) + "...";
            log.debug("✓ Access token obtained: {}", maskedToken);

            HashMap<String, String> requestBody = new HashMap<>();
            requestBody.put("phoneCode", request.getPhoneCode());
            requestBody.put("phoneNumber", request.getPhoneNumber());
            requestBody.put("referenceId", request.getReferenceId());
            requestBody.put("transactionId", request.getTransactionId());

            if (request.getName() != null && !request.getName().trim().isEmpty()) {
                requestBody.put("name", request.getName());
            }
            if (request.getSuppliedFirstName() != null && !request.getSuppliedFirstName().trim().isEmpty()) {
                requestBody.put("suppliedFirstName", request.getSuppliedFirstName());
            }

            if (notifyUrlComplete != null && !notifyUrlComplete.isEmpty()) {
                requestBody.put("notifyUrlComplete", notifyUrlComplete);
                String completeToken = jwtService.generateToken("webhook-complete");
                requestBody.put("notifyUrlCompleteAuthKey", "Bearer " + completeToken);
                requestBody.put("notifyUrlCompleteAuthHeaderName", "Authorization");
                log.debug("Added completion webhook URL: {}", notifyUrlComplete);
            }

            if (notifyUrlEvent != null && !notifyUrlEvent.isEmpty()) {
                requestBody.put("notifyUrlEvent", notifyUrlEvent);
                String eventToken = jwtService.generateToken("webhook-event");
                requestBody.put("notifyUrlEventAuthKey", "Bearer " + eventToken);
                requestBody.put("notifyUrlEventAuthHeaderName", "Authorization");
                log.debug("Added event webhook URL: {}", notifyUrlEvent);
            }

            if ("SECRET".equalsIgnoreCase(verboseMode)) {
                System.out.println("=== COMPLETE POST REQUEST (SECRET MODE) ===");
                System.out.println("URL: " + idverseApiUrl);
                System.out.println("Method: POST");
                System.out.println("Request Headers:");
                System.out.println("  Content-Type: application/json");
                System.out.println("  Accept: application/json");
                System.out.println("  Authorization: Bearer " + accessToken);
                System.out.println("Request Body:");
                System.out.println("  phoneCode: " + request.getPhoneCode());
                System.out.println("  phoneNumber: " + request.getPhoneNumber());
                System.out.println("  referenceId: " + request.getReferenceId());
                System.out.println("  transactionId: " + request.getTransactionId());
                if (request.getName() != null) System.out.println("  name: " + request.getName());
                if (request.getSuppliedFirstName() != null) System.out.println("  suppliedFirstName: " + request.getSuppliedFirstName());
                if (requestBody.containsKey("notifyUrlComplete")) {
                    System.out.println("  notifyUrlComplete: " + requestBody.get("notifyUrlComplete"));
                    System.out.println("  notifyUrlCompleteAuthKey: " + requestBody.get("notifyUrlCompleteAuthKey"));
                    System.out.println("  notifyUrlCompleteAuthHeaderName: " + requestBody.get("notifyUrlCompleteAuthHeaderName"));
                }
                if (requestBody.containsKey("notifyUrlEvent")) {
                    System.out.println("  notifyUrlEvent: " + requestBody.get("notifyUrlEvent"));
                    System.out.println("  notifyUrlEventAuthKey: " + requestBody.get("notifyUrlEventAuthKey"));
                    System.out.println("  notifyUrlEventAuthHeaderName: " + requestBody.get("notifyUrlEventAuthHeaderName"));
                }
                System.out.println("===========================================");
            } else {
                log.debug("API URL: {}", idverseApiUrl);
                log.debug("HTTP Method: POST");
                log.debug("Request Headers:");
                log.debug("  - Content-Type: application/json");
                log.debug("  - Accept: application/json");
                log.debug("  - Authorization: Bearer {}", maskedToken);
                log.debug("Request Body:");
                log.debug("  - phoneCode: {}", request.getPhoneCode());
                log.debug("  - phoneNumber: {}", request.getPhoneNumber());
                log.debug("  - referenceId: {}", request.getReferenceId());
                log.debug("  - transactionId: {}", request.getTransactionId());
                if (request.getName() != null) log.debug("  - name: {}", request.getName());
                if (request.getSuppliedFirstName() != null) log.debug("  - suppliedFirstName: {}", request.getSuppliedFirstName());
                if (requestBody.containsKey("notifyUrlComplete")) {
                    log.debug("  - notifyUrlComplete: {}", requestBody.get("notifyUrlComplete"));
                    log.debug("  - notifyUrlCompleteAuthHeaderName: {}", requestBody.get("notifyUrlCompleteAuthHeaderName"));
                }
                if (requestBody.containsKey("notifyUrlEvent")) {
                    log.debug("  - notifyUrlEvent: {}", requestBody.get("notifyUrlEvent"));
                    log.debug("  - notifyUrlEventAuthHeaderName: {}", requestBody.get("notifyUrlEventAuthHeaderName"));
                }
            }

            log.debug("Sending HTTP request to IDVerse API...");
            String response = webClient.post()
                    .uri(idverseApiUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("accept", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            log.debug("✓ Received response from IDVerse API");
            log.debug("Response body: {}", response);

            if (response != null && response.trim().startsWith("<!DOCTYPE") ||
                (response != null && response.trim().startsWith("<html"))) {
                log.error("✗ API returned HTML instead of JSON - this indicates an error");
                log.error("HTML Response: {}", response.substring(0, Math.min(200, response.length())));
                throw new RuntimeException("API returned HTML error page instead of JSON response");
            }

            log.debug("=== API Call Complete ===");
            return response != null ? response : "{}";

        } catch (WebClientResponseException e) {
            log.error("=== API Call Failed ===");
            log.error("HTTP Status Code: {}", e.getStatusCode());
            log.error("Status Text: {}", e.getStatusText());
            log.error("Response Headers: {}", e.getHeaders());
            log.error("Response Body: {}", e.getResponseBodyAsString());
            log.error("========================");

            String responseBody = e.getResponseBodyAsString();

            if (e.getStatusCode().value() == 403 && responseBody != null &&
                (responseBody.contains("CloudFront") || responseBody.contains("Request blocked"))) {
                log.warn("CloudFront blocked the request - possible IP restriction or rate limiting");
                throw new RuntimeException("Access denied by CloudFront. The request was blocked - please check IP restrictions or contact IDVerse support.");
            }

            if (e.getStatusCode().value() == 422) {
                throw new RuntimeException("API call failed with status " + e.getStatusCode() + ": " + truncateErrorMessage(responseBody, 200));
            }

            if (responseBody != null && (responseBody.trim().startsWith("<!DOCTYPE") || responseBody.trim().startsWith("<HTML"))) {
                throw new RuntimeException("API returned HTTP " + e.getStatusCode().value() + " error. Please contact support.");
            }

            throw new RuntimeException("API call failed with status " + e.getStatusCode() + ": " + responseBody);
        } catch (RuntimeException e) {
            log.error("=== Unexpected Error During API Call ===");
            log.error("Exception Type: {}", e.getClass().getName());
            log.error("Error Message: {}", e.getMessage());
            log.error("=======================================", e);
            throw e;
        } catch (Exception e) {
            log.error("=== Unexpected Error During API Call ===");
            log.error("Exception Type: {}", e.getClass().getName());
            log.error("Error Message: {}", e.getMessage());
            log.error("=======================================", e);
            throw new RuntimeException("Failed to call external API: " + e.getMessage(), e);
        }
    }

    private String truncateErrorMessage(String message, int maxLength) {
        if (message == null || message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength) + "... [truncated]";
    }
}
