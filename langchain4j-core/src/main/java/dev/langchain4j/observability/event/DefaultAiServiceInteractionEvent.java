package dev.langchain4j.observability.event; 


import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceInteractionEvent;


/**
 * Default implementation of {@link AiServiceInteractionEvent}.
 */
public class DefaultAiServiceInteractionEvent 
                                            extends AbstractAiServiceEvent 
                                            implements AiServiceInteractionEvent {

    private final List<AiServiceEvent> events;

    public DefaultAiServiceInteractionEvent(AiServiceInteractionEvent.AiServiceInteractionEventBuilder builder){
        super(builder);
    }
}
