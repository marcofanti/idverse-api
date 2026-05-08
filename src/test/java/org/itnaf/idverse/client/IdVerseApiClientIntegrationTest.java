package org.itnaf.idverse.client;

import org.itnaf.idverse.client.model.OAuthTokenResponse;
import org.itnaf.idverse.client.model.VerificationRequest;
import org.itnaf.idverse.client.service.JwtService;
import org.itnaf.idverse.client.service.OAuthTokenService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for IdVerseApiClient.
 * Loads credentials and endpoints from the .env file in the project root.
 *
 * Run with: mvn test -Dtest=IdVerseApiClientIntegrationTest
 */
class IdVerseApiClientIntegrationTest {

    private static IdVerseApiClient apiClient;
    private static OAuthTokenService oAuthTokenService;
    private static Map<String, String> env;

    @BeforeAll
    static void setUp() throws IOException {
        env = loadEnvFile();

        WebClient webClient = WebClient.builder().build();

        oAuthTokenService = new OAuthTokenService(
                webClient,
                env.get("IDVERSE_CLIENT_ID"),
                env.get("IDVERSE_CLIENT_SECRET"),
                env.get("IDVERSE_OAUTH_URL"),
                env.getOrDefault("VERBOSE", "INFO")
        );

        // Use a fixed test secret key (jwtSecretKey is provided by ApiConfig in the consuming app)
        JwtService jwtService = new JwtService("idverse-integration-test-secret-key-32bytes");

        apiClient = new IdVerseApiClient(
                oAuthTokenService,
                jwtService,
                webClient,
                env.get("IDVERSE_API_URL"),
                env.getOrDefault("VERBOSE", "INFO"),
                env.getOrDefault("NOTIFY_URL_COMPLETE", ""),
                env.getOrDefault("NOTIFY_URL_EVENT", "")
        );
    }

    @Test
    void testOAuthConnection() {
        OAuthTokenResponse response = oAuthTokenService.testConnection();

        assertNotNull(response, "OAuth response should not be null");
        assertTrue(response.isSuccess(),
                "OAuth connection should succeed. Error: " + response.getError()
                + " - " + response.getMessage());

        System.out.println("OAuth connection successful");
        System.out.println("  Token type : " + response.getTokenType());
        System.out.println("  Expires in : " + response.getExpiresIn() + "s");
        System.out.println("  Token      : " + preview(response.getAccessToken()));
    }

    @Test
    void testGetAccessToken() {
        String token = oAuthTokenService.getAccessToken();

        assertNotNull(token, "Access token should not be null");
        assertFalse(token.isBlank(), "Access token should not be blank");

        System.out.println("Access token obtained: " + preview(token));
    }

    @Test
    void testGetAccessTokenIsCached() {
        String first  = oAuthTokenService.getAccessToken();
        String second = oAuthTokenService.getAccessToken();

        assertEquals(first, second, "Second call should return the cached token");
        System.out.println("Token caching verified: both calls returned the same token");
    }

    @Test
    void testSendVerification() {
        VerificationRequest request = new VerificationRequest();
        request.setPhoneCode("+1");
        request.setPhoneNumber("6175550100");          // placeholder — replace with a real test number
        request.setReferenceId("integration-test-001");
        request.setTransactionId(env.getOrDefault("TRANSACTION", "integration-test-txn-001"));
        request.setName(env.getOrDefault("NAME", "Test User"));
        request.setSuppliedFirstName(env.getOrDefault("SUPPLIED_FIRST_NAME", "Test"));

        String response = apiClient.sendVerification(request);

        assertNotNull(response, "Response should not be null");
        assertFalse(response.isBlank(), "Response should not be blank");
        System.out.println("sendVerification response: " + response);
    }

    // -------------------------------------------------------------------------

    /** Returns the first 20 characters of a string followed by "..." */
    private static String preview(String value) {
        if (value == null) return "<null>";
        return value.substring(0, Math.min(20, value.length())) + "...";
    }

    /** Parses the .env file in the project root directory. */
    private static Map<String, String> loadEnvFile() throws IOException {
        Map<String, String> result = new HashMap<>();
        String envPath = Paths.get("").toAbsolutePath() + "/.env";

        try (BufferedReader reader = new BufferedReader(new FileReader(envPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String key   = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();
                    result.put(key, value);
                }
            }
        }

        System.out.println("Loaded .env from: " + envPath);
        System.out.println("  IDVERSE_CLIENT_ID  : " + result.get("IDVERSE_CLIENT_ID"));
        System.out.println("  IDVERSE_OAUTH_URL  : " + result.get("IDVERSE_OAUTH_URL"));
        System.out.println("  IDVERSE_API_URL    : " + result.get("IDVERSE_API_URL"));
        System.out.println("  VERBOSE            : " + result.getOrDefault("VERBOSE", "INFO"));

        return result;
    }
}
