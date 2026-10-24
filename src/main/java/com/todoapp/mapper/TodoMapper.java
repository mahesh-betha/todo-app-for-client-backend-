package com.todoapp.mapper;

import com.todoapp.dto.TodoResponse;
import com.todoapp.entity.Todo;

public final class TodoMapper {

    private TodoMapper() {
    }

    public static TodoResponse toResponse(Todo todo) {
        return new TodoResponse(
                todo.getId(),
                todo.getTitle(),
                todo.getDescription(),
                todo.getStatus(),
                todo.getPriority(),
                todo.getDueDate(),
                todo.getCreatedAt(),
                todo.getUpdatedAt());
    }
}
