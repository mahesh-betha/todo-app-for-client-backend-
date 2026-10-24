package com.todoapp.security;

import com.todoapp.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Creates and validates JWT access tokens with the HS256 algorithm.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(JwtProperties properties) {
        if (properties.secret() == null || properties.secret().length() < 32) {
            throw new IllegalStateException(
                    "JWT secret must be configured (JWT_SECRET) and be at least 32 characters long");
        }
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = properties.expirationMs();
    }

    public String generateToken(UUID userId, String email, String fullName) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("name", fullName)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    /**
     * Parses and validates the token. Throws {@code JwtException} on invalid/expired tokens.
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
