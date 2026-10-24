package com.todoapp.security;

import com.todoapp.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256-0123456789";

    private JwtService service() {
        return new JwtService(new JwtProperties(SECRET, 3600000L));
    }

    @Test
    void generatesAndParsesTokenRoundTrip() {
        UUID userId = UUID.randomUUID();
        String token = service().generateToken(userId, "jane@example.com", "Jane Doe");

        Claims claims = service().parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo("jane@example.com");
        assertThat(claims.get("name", String.class)).isEqualTo("Jane Doe");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void extractUserIdReturnsSubject() {
        UUID userId = UUID.randomUUID();
        String token = service().generateToken(userId, "a@b.com", "A");

        assertThat(service().extractUserId(token)).isEqualTo(userId);
    }

    @Test
    void rejectsTamperedToken() {
        String token = service().generateToken(UUID.randomUUID(), "a@b.com", "A");
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThatThrownBy(() -> service().parseClaims(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsGarbageToken() {
        assertThatThrownBy(() -> service().parseClaims("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService expiring = new JwtService(new JwtProperties(SECRET, -1000L));
        String expired = expiring.generateToken(UUID.randomUUID(), "a@b.com", "A");

        assertThatThrownBy(() -> service().parseClaims(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("short", 1000L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 characters");
    }

    @Test
    void tokensSignedWithDifferentSecretsAreRejected() {
        JwtService other = new JwtService(new JwtProperties(
                "another-secret-that-is-also-long-enough-9876543210", 3600000L));
        String token = other.generateToken(UUID.randomUUID(), "a@b.com", "A");

        assertThatThrownBy(() -> service().parseClaims(token))
                .isInstanceOf(JwtException.class);
    }
}
