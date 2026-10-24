package com.todoapp.dto;

import com.todoapp.entity.TodoStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(
        @NotNull(message = "Status is required")
        TodoStatus status
) {
}
