package org.itnaf.idverse.client.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Service for generating and validating JWT tokens for webhook authentication.
 */
@Service
@Slf4j
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs = 86400000; // 24 hours

    public JwtService(String jwtSecretKey) {
        String paddedKey = padSecretKey(jwtSecretKey);
        this.secretKey = Keys.hmacShaKeyFor(paddedKey.getBytes(StandardCharsets.UTF_8));
        log.info("JWT Service initialized with secret key");
    }

    private String padSecretKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("JWT secret key cannot be null or empty");
        }

        if (key.length() < 32) {
            return String.format("%-32s", key).replace(' ', '0');
        }

        return key;
    }

    /**
     * Generates a JWT token for webhook authentication.
     *
     * @param subject The subject (typically "webhook-complete" or "webhook-event")
     * @return JWT token string
     */
    public String generateToken(String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        String token = Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();

        log.debug("Generated JWT token for subject: {}", subject);
        return token;
    }

    /**
     * Generates a JWT token with custom expiration time.
     *
     * @param subject The subject
     * @param expirationHours Number of hours until token expires
     * @return JWT token string
     */
    public String generateTokenWithExpiration(String subject, long expirationHours) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (expirationHours * 3600 * 1000));

        String token = Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();

        log.debug("Generated JWT token for subject: {} with {}h expiration", subject, expirationHours);
        return token;
    }

    /**
     * Validates a JWT token and returns the claims if valid.
     *
     * @param token The JWT token to validate
     * @return Claims if token is valid
     * @throws io.jsonwebtoken.JwtException if token is invalid or expired
     */
    public Claims validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            log.debug("Token validated successfully for subject: {}", claims.getSubject());
            return claims;
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Checks if a token is valid without throwing exceptions.
     *
     * @param token The JWT token to check
     * @return true if valid, false otherwise
     */
    public boolean isTokenValid(String token) {
        try {
            validateToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
