package com.todoapp.security;

import com.todoapp.entity.User;
import com.todoapp.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Loads users by their UUID (used as the JWT subject). Email is used as the
 * username so it can also back form-based login through the AuthenticationManager.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return toUserDetails(user);
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return toUserDetails(user);
    }

    private UserDetails toUserDetails(User user) {
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getId().toString())
                .password(user.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
    }
}
