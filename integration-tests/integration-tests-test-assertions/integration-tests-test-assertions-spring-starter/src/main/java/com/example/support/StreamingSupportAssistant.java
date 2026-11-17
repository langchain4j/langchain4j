package com.example.support;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

@AiService(wiringMode = EXPLICIT, streamingChatModel = "streamingChatModel")
public interface StreamingSupportAssistant {

    TokenStream chat(String message);

    Flux<String> stream(String message);
}
