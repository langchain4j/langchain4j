package dev.langchain4j.observability.api.event;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.event.DefaultAiServiceInvocationSummaryEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A composite event summarizing a single AI Service invocation: all the events fired for it.
 * <p>
 * It is fired once an invocation is over, right after its {@link AiServiceCompletedEvent} or
 * {@link AiServiceErrorEvent}, and contains every {@link AiServiceEvent} fired for that invocation (i.e. every event
 * sharing its {@link InvocationContext#invocationId()}), in the order they were fired, starting with the
 * {@link AiServiceStartedEvent} and ending with the {@link AiServiceCompletedEvent} or {@link AiServiceErrorEvent}.
 * <p>
 * A few things to keep in mind:
 * <ul>
 *     <li>
 *         Events are only collected for an invocation if an AiServiceInvocationSummaryListener is registered when its
 *         {@link AiServiceStartedEvent} is fired.
 *     </li>
 *     <li>
 *         If an invocation ends without an {@link AiServiceCompletedEvent} or {@link AiServiceErrorEvent} (for example
 *         because an asynchronous or streaming invocation was canceled), the event is still fired, but its last event
 *         will be whichever event was fired last before the invocation ended.
 *     </li>
 *     <li>
 *         Events fired for an invocation after it has ended (for example {@link ToolCompensatedEvent}s fired once a
 *         canceled invocation has been rolled back) are not included.
 *     </li>
 *     <li>
 *         An AI Service invoked while another AI Service invocation is in progress (for example from within a tool)
 *         is a separate invocation with its own invocation id, so it results in its own AiServiceInvocationSummaryEvent.
 *     </li>
 *     <li>
 *         For an AI Service method returning a {@code TokenStream}, the invocation starts when the method is called,
 *         but only ends once the stream has been started with {@code TokenStream.start()}. Events collected for a
 *         {@code TokenStream} that is never started are never released, so always start the streams you create.
 *     </li>
 *     <li>
 *         All subscriptions to the reactive stream returned by an AI Service method share the same invocation id.
 *         Subscribing to it again after a previous subscription has ended (for example to retry) is fine, but the
 *         events of subscriptions that run at the same time are combined into a single AiServiceInvocationSummaryEvent.
 *     </li>
 * </ul>
 * <p>
 * This event is fired by the {@link dev.langchain4j.observability.api.AiServiceListenerRegistrar} itself and cannot be
 * fired directly.
 */
public interface AiServiceInvocationSummaryEvent extends AiServiceEvent {

    /**
     * Returns all the events fired for this AI Service invocation, in the order they were fired.
     *
     * @return An unmodifiable list of the events, in the order they were fired
     */
    List<AiServiceEvent> events();

    @Override
    default Class<AiServiceInvocationSummaryEvent> eventClass() {
        return AiServiceInvocationSummaryEvent.class;
    }

    @Override
    default AiServiceInvocationSummaryEventBuilder toBuilder() {
        return new AiServiceInvocationSummaryEventBuilder(this);
    }

    /**
     * Creates a new builder for constructing an {@link AiServiceInvocationSummaryEvent}.
     *
     * @return A new builder instance
     */
    static AiServiceInvocationSummaryEventBuilder builder() {
        return new AiServiceInvocationSummaryEventBuilder();
    }

    /**
     * Builder for {@link AiServiceInvocationSummaryEvent} instances.
     */
    class AiServiceInvocationSummaryEventBuilder extends Builder<AiServiceInvocationSummaryEvent> {
        private final List<AiServiceEvent> events = new ArrayList<>();

        protected AiServiceInvocationSummaryEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link AiServiceInvocationSummaryEvent}.
         *
         * @param src The event to copy the values from
         */
        protected AiServiceInvocationSummaryEventBuilder(AiServiceInvocationSummaryEvent src) {
            super(src);
            events(src.events());
        }

        /**
         * Sets the invocation context.
         *
         * @param invocationContext The invocation context
         * @return This builder
         */
        public AiServiceInvocationSummaryEventBuilder invocationContext(InvocationContext invocationContext) {
            return (AiServiceInvocationSummaryEventBuilder) super.invocationContext(invocationContext);
        }

        /**
         * Sets the events, replacing any events previously added to this builder. {@code null} elements are ignored.
         *
         * @param events The events, in the order they were fired. If {@code null}, all events are removed.
         * @return This builder
         */
        public AiServiceInvocationSummaryEventBuilder events(List<AiServiceEvent> events) {
            this.events.clear();

            if (events != null) {
                events.forEach(this::event);
            }

            return this;
        }

        /**
         * Appends a single event to the events previously added to this builder.
         *
         * @param event The event to append. If {@code null}, no action is taken.
         * @return This builder
         */
        public AiServiceInvocationSummaryEventBuilder event(AiServiceEvent event) {
            if (event != null) {
                this.events.add(event);
            }

            return this;
        }

        /**
         * Returns the events added to this builder.
         *
         * @return An unmodifiable copy of the events added to this builder, in the order they were added
         */
        public List<AiServiceEvent> events() {
            return List.copyOf(this.events);
        }

        /**
         * Builds a new {@link AiServiceInvocationSummaryEvent}.
         *
         * @return A new {@link AiServiceInvocationSummaryEvent}
         */
        @Override
        public AiServiceInvocationSummaryEvent build() {
            return new DefaultAiServiceInvocationSummaryEvent(this);
        }
    }
}
