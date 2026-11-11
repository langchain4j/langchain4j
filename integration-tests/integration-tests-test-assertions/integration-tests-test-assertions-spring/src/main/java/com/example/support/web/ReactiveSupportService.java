package com.example.support.web;

import com.example.support.assistant.StreamingSupportAssistant;
import org.springframework.stereotype.Service;
import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Flux;

@Service
public class ReactiveSupportService {

    private final StreamingSupportAssistant streamingSupportAssistant;

    public ReactiveSupportService(StreamingSupportAssistant streamingSupportAssistant) {
        this.streamingSupportAssistant = streamingSupportAssistant;
    }

    public Flux<String> streamAnswer(String message) {
        return JdkFlowAdapter.flowPublisherToFlux(streamingSupportAssistant.chatFlow(message));
    }
}
