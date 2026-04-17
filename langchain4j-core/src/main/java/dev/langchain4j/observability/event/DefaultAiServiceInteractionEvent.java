package dev.langchain4j.observability.event; 

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceInteractionEvent;

import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class DefaultAiServiceInteractionEvent implements AiServiceInteractionEvent {

    private final InvocationContext invocationContext;

    private final List<AiServiceEvent> events;

    public DefaultAiServiceInteractionEvent(AiServiceInteractionEvent.AiServiceInteractionEventBuilder builder){
        this.invocationContext = ensureNotNull(builder.invocationContext(), "invocationContext");
        this.events = List.copyOf(ensureNotNull(builder.events(), "events"));
    }

    @Override 
    public InvocationContext invocationContext(){
        return invocationContext;
    }

    @Override 
    public List<AiServiceEvent> events(){
        return events;
    }

    @Override 
    public String toString(){
        return "DefaultAiServiceInteractionEvent{" + "invocationContext=" + invocationContext + ", events=" + events + "}";
    }

    @Override
    public AiServiceInteractionEventBuilder toBuilder(){
        return new AiServiceInteractionEventBuilder(this);
    }


}
