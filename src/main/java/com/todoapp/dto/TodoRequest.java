package com.todoapp.dto;

import com.todoapp.entity.TodoPriority;
import com.todoapp.entity.TodoStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TodoRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        LocalDate dueDate,

        @NotNull(message = "Priority is required")
        TodoPriority priority,

        @NotNull(message = "Status is required")
        TodoStatus status
) {
}
