package com.example.support.web;

import com.example.support.assistant.StreamingSupportAssistant;
import dev.langchain4j.service.TokenStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/support")
public class StreamingSupportController {

    private final StreamingSupportAssistant streamingSupportAssistant;
    private final ReactiveSupportService reactiveSupportService;
    private final TaskExecutor taskExecutor;

    public StreamingSupportController(
            StreamingSupportAssistant streamingSupportAssistant,
            ReactiveSupportService reactiveSupportService,
            TaskExecutor taskExecutor) {
        this.streamingSupportAssistant = streamingSupportAssistant;
        this.reactiveSupportService = reactiveSupportService;
        this.taskExecutor = taskExecutor;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("message") String message) {
        SseEmitter emitter = new SseEmitter();
        TokenStream tokenStream = streamingSupportAssistant.chat(message);
        taskExecutor.execute(() -> tokenStream
                .onPartialResponse(token -> send(emitter, token))
                .onCompleteResponse(response -> emitter.complete())
                .onError(emitter::completeWithError)
                .start());
        return emitter;
    }

    @GetMapping(path = "/flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> flux(@RequestParam("message") String message) {
        return reactiveSupportService.streamAnswer(message);
    }

    private static void send(SseEmitter emitter, String token) {
        try {
            emitter.send(token);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
