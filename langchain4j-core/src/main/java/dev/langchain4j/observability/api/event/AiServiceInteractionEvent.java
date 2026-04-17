package dev.langchain4j.observability.api.event;

import dev.langchain4j.observability.event.DefaultAiServiceInteractionEvent;
import java.util.List;
import java.util.ArrayList;
import dev.langchain4j.invocation.InvocationContext;

public interface AiServiceInteractionEvent extends AiServiceEvent {

    List<AiServiceEvent> events();

    @Override
    default Class<AiServiceInteractionEvent> eventClass() {
        return AiServiceInteractionEvent.class;
    }

    @Override
    default AiServiceInteractionEventBuilder toBuilder() {
        return new AiServiceInteractionEventBuilder(this);
    }

    static AiServiceInteractionEventBuilder builder() {
        return new AiServiceInteractionEventBuilder();
    }

    class AiServiceInteractionEventBuilder extends Builder<AiServiceInteractionEvent> {
        private final List<AiServiceEvent> events = new ArrayList<>();

        protected AiServiceInteractionEventBuilder() {}

        public AiServiceInteractionEventBuilder(AiServiceInteractionEvent src) {
            super(src);
            events(src.events());
        }

        public AiServiceInteractionEventBuilder invocationContext(InvocationContext invocationContext) {
            return (AiServiceInteractionEventBuilder) super.invocationContext(invocationContext);
        }

        public AiServiceInteractionEventBuilder events(List<AiServiceEvent> events) {
            if (events != null) {
                this.events.addAll(events);
            }
            return this;
        }

        public AiServiceInteractionEventBuilder event(AiServiceEvent event) {
            if (event != null) {
                this.events.add(event);
            }
            return this;
        }

        public List<AiServiceEvent> events() {
            return List.copyOf(events);
        }

        @Override
        public AiServiceInteractionEvent build() {
            return new DefaultAiServiceInteractionEvent(this);
        }
    }
}