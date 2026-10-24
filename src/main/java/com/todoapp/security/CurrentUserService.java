package com.todoapp.security;

import com.todoapp.entity.User;
import com.todoapp.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the authenticated user from the SecurityContext (populated by
 * {@link JwtAuthenticationFilter} using the user's UUID as the principal name).
 */
@Component
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.security.authentication.BadCredentialsException("Not authenticated");
        }
        return UUID.fromString(authentication.getName());
    }

    public User getCurrentUser() {
        return userRepository.findById(getCurrentUserId())
                .orElseThrow(() -> new org.springframework.security.authentication
                        .BadCredentialsException("Not authenticated"));
    }
}
