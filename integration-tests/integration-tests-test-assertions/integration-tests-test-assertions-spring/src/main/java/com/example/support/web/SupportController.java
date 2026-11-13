package com.example.support.web;

import com.example.support.assistant.SupportAssistant;
import java.util.concurrent.CompletableFuture;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/support")
public class SupportController {

    private final SupportAssistant supportAssistant;
    private final AsyncSupportService asyncSupportService;

    public SupportController(SupportAssistant supportAssistant, AsyncSupportService asyncSupportService) {
        this.supportAssistant = supportAssistant;
        this.asyncSupportService = asyncSupportService;
    }

    @PostMapping("/chat")
    public String chat(@RequestBody String message) {
        return supportAssistant.chat(message);
    }

    @PostMapping("/chat-async")
    public CompletableFuture<String> chatAsync(@RequestBody String message) {
        return asyncSupportService.ask(message);
    }
}
