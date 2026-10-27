package dev.langchain4j.observability.api.event;

import dev.langchain4j.Experimental;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.observability.event.DefaultAiServiceCancelledEvent;

/**
 * Invoked when an AI Service invocation is cancelled before it completes, for example when the {@code Flow.Publisher}
 * subscription is cancelled, the returned {@code CompletableFuture} is cancelled, or
 * {@link StreamingHandle#cancel()} is called from a {@code TokenStream} callback
 * that receives a context, such as {@code onPartialResponseWithContext}. It means that cancellation was requested: the
 * model provider or a tool may still finish work that was already running. A cancelled invocation fires neither
 * {@link AiServiceCompletedEvent} nor {@link AiServiceErrorEvent}. Events of work that was already running when the
 * invocation was cancelled (for example a {@link ToolExecutedEvent}) can still arrive after this event.
 *
 * @since 1.23.0
 */
@Experimental
public interface AiServiceCancelledEvent extends AiServiceEvent {

    @Override
    default Class<AiServiceCancelledEvent> eventClass() {
        return AiServiceCancelledEvent.class;
    }

    @Override
    default AiServiceCancelledEventBuilder toBuilder() {
        return new AiServiceCancelledEventBuilder(this);
    }

    static AiServiceCancelledEventBuilder builder() {
        return new AiServiceCancelledEventBuilder();
    }

    /**
     * Builder for {@link AiServiceCancelledEvent} instances.
     */
    class AiServiceCancelledEventBuilder extends Builder<AiServiceCancelledEvent> {

        protected AiServiceCancelledEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link AiServiceCancelledEvent}.
         */
        protected AiServiceCancelledEventBuilder(AiServiceCancelledEvent src) {
            super(src);
        }

        /**
         * Sets the invocation context.
         */
        @Override
        public AiServiceCancelledEventBuilder invocationContext(InvocationContext invocationContext) {
            return (AiServiceCancelledEventBuilder) super.invocationContext(invocationContext);
        }

        /**
         * Builds a {@link AiServiceCancelledEvent}.
         */
        @Override
        public AiServiceCancelledEvent build() {
            return new DefaultAiServiceCancelledEvent(this);
        }
    }
}
