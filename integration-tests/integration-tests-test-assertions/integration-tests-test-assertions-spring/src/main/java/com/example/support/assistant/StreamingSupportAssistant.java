package com.example.support.assistant;

import dev.langchain4j.service.AiServiceStreamingEvent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import java.util.concurrent.Flow;
import reactor.core.publisher.Flux;

public interface StreamingSupportAssistant {

    @SystemMessage("You are a customer support agent of Miles of Smiles, a car rental company.")
    TokenStream chat(String message);

    @SystemMessage("You are a customer support agent of Miles of Smiles, a car rental company.")
    Flow.Publisher<String> chatFlow(String message);

    @SystemMessage("You are a customer support agent of Miles of Smiles, a car rental company.")
    Flux<String> chatFlux(String message);

    @SystemMessage("You are a customer support agent of Miles of Smiles, a car rental company.")
    Flux<AiServiceStreamingEvent> chatEvents(String message);
}
