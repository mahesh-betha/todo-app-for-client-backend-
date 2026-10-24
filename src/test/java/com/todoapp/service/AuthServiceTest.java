package com.todoapp.service;

import com.todoapp.dto.AuthResponse;
import com.todoapp.dto.LoginRequest;
import com.todoapp.dto.RegisterRequest;
import com.todoapp.entity.User;
import com.todoapp.exception.ConflictException;
import com.todoapp.repository.UserRepository;
import com.todoapp.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, authenticationManager, jwtService);
    }

    private User savedUser() {
        User user = new User("Jane Doe", "jane@example.com", "encoded-hash");
        user.setId(UUID.randomUUID());
        user.setCreatedAt(Instant.now());
        return user;
    }

    private void stubSaveWithId() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            u.setCreatedAt(Instant.now());
            return u;
        });
    }

    @Test
    void registerCreatesUserWithHashedPasswordAndReturnsToken() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-hash");
        stubSaveWithId();
        when(jwtService.generateToken(any(), anyString(), anyString())).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(3600000L);

        AuthResponse response = authService.register(new RegisterRequest(
                " Jane Doe ", "Jane@Example.com", "secret123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPassword()).isEqualTo("encoded-hash");
        assertThat(saved.getPassword()).isNotEqualTo("secret123");
        assertThat(saved.getEmail()).isEqualTo("jane@example.com");
        assertThat(saved.getFullName()).isEqualTo("Jane Doe");

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.user().email()).isEqualTo("jane@example.com");
        assertThat(response.user().fullName()).isEqualTo("Jane Doe");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(
                "Jane Doe", "jane@example.com", "secret123")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void registerNormalizesEmailToLowerCase() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-hash");
        stubSaveWithId();
        when(jwtService.generateToken(any(), anyString(), anyString())).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(3600000L);

        authService.register(new RegisterRequest("Jane", "  JANE@EXAMPLE.COM ", "secret123"));

        verify(userRepository).existsByEmail("jane@example.com");
    }

    @Test
    void loginAuthenticatesAndReturnsToken() {
        User user = savedUser();
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user.getId(), user.getEmail(), user.getFullName()))
                .thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(3600000L);

        AuthResponse response = authService.login(new LoginRequest("jane@example.com", "secret123"));

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.user().id()).isEqualTo(user.getId());
    }

    @Test
    void loginPropagatesBadCredentials() {
        doThrow(new BadCredentialsException("bad"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void passwordIsNeverStoredInPlainText() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$hashed");
        stubSaveWithId();
        when(jwtService.generateToken(any(), anyString(), anyString())).thenReturn("t");
        when(jwtService.getExpirationMs()).thenReturn(1000L);

        authService.register(new RegisterRequest("Jane", "jane@example.com", "secret123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$10$hashed");
    }
}
