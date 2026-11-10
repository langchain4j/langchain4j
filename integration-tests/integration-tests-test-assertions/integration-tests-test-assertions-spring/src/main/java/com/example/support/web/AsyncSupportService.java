package com.example.support.web;

import com.example.support.assistant.SupportAssistant;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncSupportService {

    private final SupportAssistant supportAssistant;

    public AsyncSupportService(SupportAssistant supportAssistant) {
        this.supportAssistant = supportAssistant;
    }

    @Async
    public CompletableFuture<String> ask(String message) {
        return CompletableFuture.completedFuture(supportAssistant.chat(message));
    }
}
