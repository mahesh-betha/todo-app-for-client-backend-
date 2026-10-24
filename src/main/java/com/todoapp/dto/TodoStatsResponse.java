package com.todoapp.dto;

public record TodoStatsResponse(
        long total,
        long pending,
        long inProgress,
        long completed
) {
}
