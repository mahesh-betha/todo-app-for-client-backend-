package com.todoapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings bound from the {@code app.jwt.*} prefix.
 * The secret is supplied via the {@code JWT_SECRET} environment variable and
 * is never logged or exposed through the API.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationMs) {
}
