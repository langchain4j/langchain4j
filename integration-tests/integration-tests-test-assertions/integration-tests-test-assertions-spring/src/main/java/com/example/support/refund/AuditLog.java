package com.example.support.refund;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

@Component
public class AuditLog {

    private final List<String> entries = new CopyOnWriteArrayList<>();

    void add(String entry) {
        entries.add(entry);
    }

    public List<String> entries() {
        return List.copyOf(entries);
    }
}
