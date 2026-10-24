package com.todoapp.repository;

import com.todoapp.entity.Todo;
import com.todoapp.entity.TodoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TodoRepository extends JpaRepository<Todo, UUID>, JpaSpecificationExecutor<Todo> {

    /** Ownership-aware lookup: returns empty when the todo belongs to another user. */
    @Query("select t from Todo t where t.id = :id and t.user.id = :userId")
    Optional<Todo> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("select t.status as status, count(t) as total from Todo t where t.user.id = :userId group by t.status")
    List<StatusCount> countByStatusForUser(@Param("userId") UUID userId);

    interface StatusCount {
        TodoStatus getStatus();

        long getTotal();
    }
}
