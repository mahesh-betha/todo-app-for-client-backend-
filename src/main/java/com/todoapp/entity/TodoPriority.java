package com.todoapp.entity;

public enum TodoPriority {
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    private final int rank;

    TodoPriority(int rank) {
        this.rank = rank;
    }

    /** Numeric weight used for sorting (higher = more important). */
    public int getRank() {
        return rank;
    }
}
