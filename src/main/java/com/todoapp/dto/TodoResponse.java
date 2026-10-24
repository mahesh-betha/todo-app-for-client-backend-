package com.todoapp.dto;

import com.todoapp.entity.TodoPriority;
import com.todoapp.entity.TodoStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TodoResponse(
        UUID id,
        String title,
        String description,
        TodoStatus status,
        TodoPriority priority,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt
) {
}
