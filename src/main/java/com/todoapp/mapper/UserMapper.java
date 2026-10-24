package com.todoapp.mapper;

import com.todoapp.dto.UserResponse;
import com.todoapp.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getCreatedAt());
    }
}
