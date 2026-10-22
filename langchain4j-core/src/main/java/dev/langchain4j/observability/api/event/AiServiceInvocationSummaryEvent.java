package dev.langchain4j.observability.api.event;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.event.DefaultAiServiceInvocationSummaryEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A composite event summarizing a single AI Service invocation: all the events fired for it.
 * <p>
 * It is fired once an invocation is over, right after its {@link AiServiceCompletedEvent}, {@link AiServiceErrorEvent}
 * or {@link AiServiceCancelledEvent}, and contains every {@link AiServiceEvent} fired for that invocation (i.e. every
 * event sharing its {@link InvocationContext#invocationId()}), in the order they were fired, starting with the
 * {@link AiServiceStartedEvent} and ending with the {@link AiServiceCompletedEvent}, {@link AiServiceErrorEvent} or
 * {@link AiServiceCancelledEvent}.
 * <p>
 * A few things to keep in mind:
 * <ul>
 *     <li>
 *         Events are only collected for an invocation if an AiServiceInvocationSummaryListener is registered when its
 *         {@link AiServiceStartedEvent} is fired.
 *     </li>
 *     <li>
 *         The event is only fired once the invocation fires its {@link AiServiceCompletedEvent},
 *         {@link AiServiceErrorEvent} or {@link AiServiceCancelledEvent}. AI Services always fire exactly one of them;
 *         for an AI Service implementation that does not (for example one built by an integration), the collected
 *         events are held until then.
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
 *         If {@code start()} fails because of a configuration error (for example a missing error handler), the
 *         invocation ends with an {@link AiServiceErrorEvent}; calling {@code start()} again once fixed starts a new
 *         invocation.
 *     </li>
 *     <li>
 *         Each subscription to the reactive stream returned by an AI Service method, and each call of
 *         {@code TokenStream.start()}, is a separate invocation with its own invocation id, so it results in its own
 *         AiServiceInvocationSummaryEvent, also when several subscriptions run at the same time.
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
