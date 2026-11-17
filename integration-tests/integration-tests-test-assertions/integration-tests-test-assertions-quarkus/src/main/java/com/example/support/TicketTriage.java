package com.example.support;

public record TicketTriage(Priority priority, String category) {

    public enum Priority {
        LOW,
        HIGH
    }
}
