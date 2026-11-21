package com.example.support;

import dev.langchain4j.service.AiServiceStreamingEvent;
import dev.langchain4j.service.SystemMessage;
import java.util.concurrent.Flow;

public interface ProductAdvisor {

    @SystemMessage("Recommend products of the online shop that match the wishes of the customer.")
    Flow.Publisher<String> recommend(String wishes);

    @SystemMessage("Recommend products of the online shop that match the wishes of the customer.")
    Flow.Publisher<AiServiceStreamingEvent> recommendWithEvents(String wishes);
}
