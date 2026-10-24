package com.todoapp.service;

import com.todoapp.dto.AuthResponse;
import com.todoapp.dto.LoginRequest;
import com.todoapp.dto.RegisterRequest;
import com.todoapp.dto.UserResponse;
import com.todoapp.entity.User;
import com.todoapp.exception.ConflictException;
import com.todoapp.mapper.UserMapper;
import com.todoapp.repository.UserRepository;
import com.todoapp.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }

        User user = new User(
                request.fullName().trim(),
                email,
                passwordEncoder.encode(request.password()));
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        // Throws BadCredentialsException (mapped to 401) on invalid credentials.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new org.springframework.security.authentication
                        .BadCredentialsException("Invalid email or password"));

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(User user) {
        return UserMapper.toResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getFullName());
        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getExpirationMs() / 1000,
                UserMapper.toResponse(user));
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
