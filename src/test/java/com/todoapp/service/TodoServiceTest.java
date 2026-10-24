package com.todoapp.service;

import com.todoapp.dto.StatusUpdateRequest;
import com.todoapp.dto.TodoRequest;
import com.todoapp.dto.TodoResponse;
import com.todoapp.dto.TodoStatsResponse;
import com.todoapp.entity.Todo;
import com.todoapp.entity.TodoPriority;
import com.todoapp.entity.TodoStatus;
import com.todoapp.entity.User;
import com.todoapp.exception.ResourceNotFoundException;
import com.todoapp.repository.TodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock
    private TodoRepository todoRepository;

    private TodoService todoService;
    private User user;

    @BeforeEach
    void setUp() {
        todoService = new TodoService(todoRepository);
        user = new User("Jane Doe", "jane@example.com", "hash");
        user.setId(UUID.randomUUID());
    }

    private Todo todoEntity(TodoStatus status, TodoPriority priority) {
        Todo todo = new Todo(user);
        todo.setId(UUID.randomUUID());
        todo.setTitle("Test todo");
        todo.setDescription("Description");
        todo.setStatus(status);
        todo.setPriority(priority);
        todo.setCreatedAt(Instant.now());
        return todo;
    }

    @Test
    void listReturnsOnlyCurrentUserTodos() {
        Todo todo = todoEntity(TodoStatus.PENDING, TodoPriority.HIGH);
        when(todoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(todo)));

        Page<TodoResponse> page = todoService.list(user, "test", null, "created_at", "desc", 0, 10);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).title()).isEqualTo("Test todo");
        assertThat(page.getContent().get(0).status()).isEqualTo(TodoStatus.PENDING);
        verify(todoRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getReturnsOwnedTodo() {
        Todo todo = todoEntity(TodoStatus.IN_PROGRESS, TodoPriority.MEDIUM);
        when(todoRepository.findByIdAndUserId(todo.getId(), user.getId()))
                .thenReturn(Optional.of(todo));

        TodoResponse response = todoService.get(user, todo.getId());

        assertThat(response.id()).isEqualTo(todo.getId());
    }

    @Test
    void getThrowsWhenTodoBelongsToAnotherUser() {
        UUID foreignId = UUID.randomUUID();
        when(todoRepository.findByIdAndUserId(foreignId, user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.get(user, foreignId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void createPersistsTodoWithTrimmedTitle() {
        when(todoRepository.save(any(Todo.class))).thenAnswer(inv -> {
            Todo t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            t.setCreatedAt(Instant.now());
            return t;
        });

        TodoResponse response = todoService.create(user, new TodoRequest(
                "  Buy milk  ", "2 liters", LocalDate.of(2026, 1, 1),
                TodoPriority.HIGH, TodoStatus.PENDING));

        ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
        verify(todoRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Buy milk");
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(response.title()).isEqualTo("Buy milk");
        assertThat(response.priority()).isEqualTo(TodoPriority.HIGH);
    }

    @Test
    void updateModifiesOwnedTodo() {
        Todo todo = todoEntity(TodoStatus.PENDING, TodoPriority.LOW);
        when(todoRepository.findByIdAndUserId(todo.getId(), user.getId()))
                .thenReturn(Optional.of(todo));
        when(todoRepository.save(any(Todo.class))).thenAnswer(inv -> inv.getArgument(0));

        TodoResponse response = todoService.update(user, todo.getId(), new TodoRequest(
                "Updated", "", LocalDate.of(2026, 6, 1), TodoPriority.HIGH, TodoStatus.COMPLETED));

        assertThat(response.title()).isEqualTo("Updated");
        assertThat(response.status()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(todo.getDescription()).isNull();
    }


    @Test
    void updateThrowsForOtherUsersTodo() {
        UUID foreignId = UUID.randomUUID();
        when(todoRepository.findByIdAndUserId(foreignId, user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.update(user, foreignId, new TodoRequest(
                "X", null, null, TodoPriority.LOW, TodoStatus.PENDING)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(todoRepository, never()).save(any());
    }

    @Test
    void updateStatusPersistsNewStatus() {
        Todo todo = todoEntity(TodoStatus.PENDING, TodoPriority.MEDIUM);
        when(todoRepository.findByIdAndUserId(todo.getId(), user.getId()))
                .thenReturn(Optional.of(todo));
        when(todoRepository.save(any(Todo.class))).thenAnswer(inv -> inv.getArgument(0));

        TodoResponse response = todoService.updateStatus(user, todo.getId(),
                new StatusUpdateRequest(TodoStatus.COMPLETED));

        assertThat(response.status()).isEqualTo(TodoStatus.COMPLETED);
    }

    @Test
    void deleteRemovesOwnedTodo() {
        Todo todo = todoEntity(TodoStatus.PENDING, TodoPriority.LOW);
        when(todoRepository.findByIdAndUserId(todo.getId(), user.getId()))
                .thenReturn(Optional.of(todo));

        todoService.delete(user, todo.getId());

        verify(todoRepository).delete(todo);
    }

    @Test
    void deleteThrowsWhenTodoNotFound() {
        UUID id = UUID.randomUUID();
        when(todoRepository.findByIdAndUserId(id, user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.delete(user, id))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(todoRepository, never()).delete(any(Todo.class));
    }

    @Test
    void statsAggregatesCountsByStatus() {
        when(todoRepository.countByStatusForUser(user.getId())).thenReturn(List.of(
                counted(TodoStatus.PENDING, 3),
                counted(TodoStatus.COMPLETED, 2)));

        TodoStatsResponse stats = todoService.stats(user);

        assertThat(stats.total()).isEqualTo(5);
        assertThat(stats.pending()).isEqualTo(3);
        assertThat(stats.inProgress()).isZero();
        assertThat(stats.completed()).isEqualTo(2);
    }

    private TodoRepository.StatusCount counted(TodoStatus status, long total) {
        return new TodoRepository.StatusCount() {
            @Override
            public TodoStatus getStatus() {
                return status;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
