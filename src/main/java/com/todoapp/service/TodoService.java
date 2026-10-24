package com.todoapp.service;

import com.todoapp.dto.StatusUpdateRequest;
import com.todoapp.dto.TodoRequest;
import com.todoapp.dto.TodoResponse;
import com.todoapp.dto.TodoStatsResponse;
import com.todoapp.entity.Todo;
import com.todoapp.entity.TodoStatus;
import com.todoapp.entity.User;
import com.todoapp.exception.ResourceNotFoundException;
import com.todoapp.mapper.TodoMapper;
import com.todoapp.repository.TodoRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class TodoService {

    private static final int MAX_PAGE_SIZE = 200;

    private final TodoRepository todoRepository;

    public TodoService(TodoRepository todoRepository) {
        this.todoRepository = todoRepository;
    }

    @Transactional(readOnly = true)
    public Page<TodoResponse> list(User user,
                                   String search,
                                   TodoStatus status,
                                   String sort,
                                   String direction,
                                   int page,
                                   int size) {
        Specification<Todo> specification = buildSpecification(user, search, status);
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                buildSort(sort, direction));
        return todoRepository.findAll(specification, pageable).map(TodoMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public TodoResponse get(User user, UUID id) {
        return TodoMapper.toResponse(findOwned(user, id));
    }

    @Transactional
    public TodoResponse create(User user, TodoRequest request) {
        Todo todo = new Todo(user);
        applyRequest(todo, request);
        return TodoMapper.toResponse(todoRepository.save(todo));
    }

    @Transactional
    public TodoResponse update(User user, UUID id, TodoRequest request) {
        Todo todo = findOwned(user, id);
        applyRequest(todo, request);
        return TodoMapper.toResponse(todoRepository.save(todo));
    }

    @Transactional
    public TodoResponse updateStatus(User user, UUID id, StatusUpdateRequest request) {
        Todo todo = findOwned(user, id);
        todo.setStatus(request.status());
        return TodoMapper.toResponse(todoRepository.save(todo));
    }

    @Transactional
    public void delete(User user, UUID id) {
        Todo todo = findOwned(user, id);
        todoRepository.delete(todo);
    }

    @Transactional(readOnly = true)
    public TodoStatsResponse stats(User user) {
        Map<TodoStatus, Long> counts = new EnumMap<>(TodoStatus.class);
        todoRepository.countByStatusForUser(user.getId())
                .forEach(sc -> counts.put(sc.getStatus(), sc.getTotal()));
        long pending = counts.getOrDefault(TodoStatus.PENDING, 0L);
        long inProgress = counts.getOrDefault(TodoStatus.IN_PROGRESS, 0L);
        long completed = counts.getOrDefault(TodoStatus.COMPLETED, 0L);
        return new TodoStatsResponse(pending + inProgress + completed, pending, inProgress, completed);
    }

    private Todo findOwned(User user, UUID id) {
        return todoRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Todo not found"));
    }

    private void applyRequest(Todo todo, TodoRequest request) {
        todo.setTitle(request.title().trim());
        todo.setDescription(blankToNull(request.description()));
        todo.setDueDate(request.dueDate());
        todo.setPriority(request.priority());
        todo.setStatus(request.status());
    }

    private Specification<Todo> buildSpecification(User user, String search, TodoStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), user.getId()));

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), pattern));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort buildSort(String sort, String direction) {
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String property = switch (sort == null ? "created_at" : sort) {
            case "due_date", "dueDate" -> "dueDate";
            case "priority" -> "priority";
            case "title" -> "title";
            default -> "createdAt";
        };
        // A stable id tiebreaker keeps pagination deterministic.
        return Sort.by(dir, property).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
