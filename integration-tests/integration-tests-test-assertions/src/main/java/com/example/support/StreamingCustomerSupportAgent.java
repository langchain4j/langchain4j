package com.example.support;

import dev.langchain4j.service.AiServiceStreamingEvent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import java.util.concurrent.Flow;

@SystemMessage("You are the customer support agent of Miles of Smiles car rental. Be polite and concise.")
public interface StreamingCustomerSupportAgent {

    TokenStream chat(String message);

    Flow.Publisher<String> chatReactive(String message);

    Flow.Publisher<AiServiceStreamingEvent> chatWithEvents(String message);
}
