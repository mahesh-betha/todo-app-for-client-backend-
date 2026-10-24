package com.todoapp.controller;

import com.todoapp.dto.StatusUpdateRequest;
import com.todoapp.dto.TodoRequest;
import com.todoapp.dto.TodoResponse;
import com.todoapp.dto.TodoStatsResponse;
import com.todoapp.entity.TodoStatus;
import com.todoapp.entity.User;
import com.todoapp.security.CurrentUserService;
import com.todoapp.service.TodoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/todos")
public class TodoController {

    private final TodoService todoService;
    private final CurrentUserService currentUserService;

    public TodoController(TodoService todoService, CurrentUserService currentUserService) {
        this.todoService = todoService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<Page<TodoResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TodoStatus status,
            @RequestParam(defaultValue = "created_at") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        User user = currentUserService.getCurrentUser();
        return ResponseEntity.ok(todoService.list(user, search, status, sort, direction, page, size));
    }

    @GetMapping("/stats")
    public ResponseEntity<TodoStatsResponse> stats() {
        User user = currentUserService.getCurrentUser();
        return ResponseEntity.ok(todoService.stats(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TodoResponse> get(@PathVariable UUID id) {
        User user = currentUserService.getCurrentUser();
        return ResponseEntity.ok(todoService.get(user, id));
    }

    @PostMapping
    public ResponseEntity<TodoResponse> create(@Valid @RequestBody TodoRequest request) {
        User user = currentUserService.getCurrentUser();
        TodoResponse created = todoService.create(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TodoResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody TodoRequest request) {
        User user = currentUserService.getCurrentUser();
        return ResponseEntity.ok(todoService.update(user, id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TodoResponse> updateStatus(@PathVariable UUID id,
                                                     @Valid @RequestBody StatusUpdateRequest request) {
        User user = currentUserService.getCurrentUser();
        return ResponseEntity.ok(todoService.updateStatus(user, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        User user = currentUserService.getCurrentUser();
        todoService.delete(user, id);
        return ResponseEntity.noContent().build();
    }
}
