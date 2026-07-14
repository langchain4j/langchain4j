package dev.langchain4j.service;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.event.CompensationReason;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.RawStreamingEvent;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import java.util.List;

/**
 * An event emitted by the reactive stream of a non-blocking streaming AI Service — that is, an AI Service method
 * declared to return a {@link java.util.concurrent.Flow.Publisher} of {@code AiServiceStreamingEvent}.
 * <p>
 * This is a <b>high-level</b>, AI-Service-scoped vocabulary, deliberately distinct from the low-level,
 * per-LLM-call {@link dev.langchain4j.model.chat.response.StreamingEvent} emitted by
 * {@link dev.langchain4j.model.chat.StreamingChatModel#chat(dev.langchain4j.model.chat.request.ChatRequest)}.
 * It covers a whole agentic interaction: the token-level chunks of every round, the tool-execution lifecycle,
 * the distinction between an intermediate (tool-calling) round and the final answer, and retrieved RAG content.
 * It mirrors everything the handler-based {@link TokenStream} surfaces today.
 * <p>
 * Every event carries the {@link InvocationContext} of the AI Service invocation that produced it.
 * <p>
 * The set is intentionally <b>not sealed</b>: new event types may be introduced over time. Consumers must
 * therefore handle unrecognized subtypes gracefully (e.g. a {@code default} branch in a type switch) rather
 * than assume the listing below is exhaustive.
 *
 * @since 1.17.0
 */
@Experimental
public interface AiServiceStreamingEvent { // TODO nested events?

    // TODO consistent naming

    /**
     * The {@link InvocationContext} of the AI Service invocation that produced this event.
     */
    InvocationContext invocationContext();

    /**
     * Base class holding the {@link InvocationContext} common to every event.
     */
    abstract class AbstractEvent implements AiServiceStreamingEvent {

        private final InvocationContext invocationContext;

        protected AbstractEvent(InvocationContext invocationContext) {
            this.invocationContext = ensureNotNull(invocationContext, "invocationContext");
        }

        @Override
        public InvocationContext invocationContext() {
            return invocationContext;
        }
    }

    /**
     * A chunk of the model's textual response, as it streams. Corresponds to
     * {@link TokenStream#onPartialResponse}.
     */
    final class PartialResponseEvent extends AbstractEvent {

        private final PartialResponse partialResponse;

        public PartialResponseEvent(PartialResponse partialResponse, InvocationContext invocationContext) {
            super(invocationContext);
            this.partialResponse = partialResponse;
        }

        public PartialResponse partialResponse() {
            return partialResponse;
        }
    }

    /**
     * A chunk of the model's thinking/reasoning, as it streams. Corresponds to
     * {@link TokenStream#onPartialThinking}.
     */
    final class PartialThinkingEvent extends AbstractEvent {

        private final PartialThinking partialThinking;

        public PartialThinkingEvent(PartialThinking partialThinking, InvocationContext invocationContext) {
            super(invocationContext);
            this.partialThinking = partialThinking;
        }

        public PartialThinking partialThinking() {
            return partialThinking;
        }
    }

    /**
     * A chunk of a tool call's arguments, as they stream. Corresponds to {@link TokenStream#onPartialToolCall}.
     */
    final class PartialToolCallEvent extends AbstractEvent {

        private final PartialToolCall partialToolCall;

        public PartialToolCallEvent(PartialToolCall partialToolCall, InvocationContext invocationContext) {
            super(invocationContext);
            this.partialToolCall = partialToolCall;
        }

        public PartialToolCall partialToolCall() {
            return partialToolCall;
        }
    }

    /**
     * A fully assembled tool call requested by the model (before it is executed).
     */
    final class CompleteToolCallEvent extends AbstractEvent {

        private final CompleteToolCall completeToolCall;

        public CompleteToolCallEvent(CompleteToolCall completeToolCall, InvocationContext invocationContext) {
            super(invocationContext);
            this.completeToolCall = completeToolCall;
        }

        public CompleteToolCall completeToolCall() {
            return completeToolCall;
        }
    }

    /**
     * A provider-specific raw event, relayed as-is from the model's reactive stream. Provider-specific and
     * unstable; consumers that do not recognize a given raw event should ignore it.
     */
    final class RawEvent extends AbstractEvent {

        private final RawStreamingEvent rawStreamingEvent;

        public RawEvent(RawStreamingEvent rawStreamingEvent, InvocationContext invocationContext) {
            super(invocationContext);
            this.rawStreamingEvent = rawStreamingEvent;
        }

        public RawStreamingEvent rawStreamingEvent() {
            return rawStreamingEvent;
        }
    }

    /**
     * The content retrieved by RAG for this interaction, emitted once before the first response chunk.
     * Corresponds to {@link TokenStream#onRetrieved}.
     */
    final class RetrievedContentsEvent extends AbstractEvent {

        private final List<Content> contents;

        public RetrievedContentsEvent(List<Content> contents, InvocationContext invocationContext) {
            super(invocationContext);
            this.contents = contents;
        }

        public List<Content> contents() {
            return contents;
        }
    }

    /**
     * A {@link ChatResponse} of an intermediate (tool-calling) round — i.e. not the final answer.
     * Corresponds to {@link TokenStream#onIntermediateResponse}.
     */
    final class IntermediateResponseEvent extends AbstractEvent {

        private final ChatResponse chatResponse;

        public IntermediateResponseEvent(ChatResponse chatResponse, InvocationContext invocationContext) {
            super(invocationContext);
            this.chatResponse = chatResponse;
        }

        public ChatResponse chatResponse() {
            return chatResponse;
        }
    }

    /**
     * Signals that a tool is about to be executed. Corresponds to {@link TokenStream#beforeToolExecution}.
     */
    final class BeforeToolExecutionEvent extends AbstractEvent {

        private final BeforeToolExecution beforeToolExecution;

        public BeforeToolExecutionEvent(BeforeToolExecution beforeToolExecution, InvocationContext invocationContext) {
            super(invocationContext);
            this.beforeToolExecution = beforeToolExecution;
        }

        public BeforeToolExecution beforeToolExecution() {
            return beforeToolExecution;
        }
    }

    /**
     * Signals that a tool has finished executing, carrying its request and result. Corresponds to
     * {@link TokenStream#onToolExecuted}.
     */
    final class AfterToolExecutionEvent extends AbstractEvent { // TODO what about failed tools?

        private final ToolExecution toolExecution;

        public AfterToolExecutionEvent(ToolExecution toolExecution, InvocationContext invocationContext) {
            super(invocationContext);
            this.toolExecution = toolExecution;
        }

        public ToolExecution toolExecution() {
            return toolExecution;
        }
    }

    /**
     * Signals that a successfully-executed tool was compensated (rolled back) — for example because another tool
     * in the same round failed. It carries the {@link ToolExecution} that was rolled back (its request and original
     * successful result) and the {@link CompensationReason}.
     * <p>
     * Note: this event is <b>not</b> emitted on cancellation. Cancelling the subscription also cancels the stream,
     * and the Reactive Streams contract forbids delivering further events after a cancel; a rollback triggered by
     * cancellation is instead observable through the {@code ToolCompensatedEvent} observability listener.
     */
    final class ToolCompensatedEvent extends AbstractEvent {

        private final ToolExecution toolExecution;
        private final CompensationReason reason;

        public ToolCompensatedEvent(
                ToolExecution toolExecution, CompensationReason reason, InvocationContext invocationContext) {
            super(invocationContext);
            this.toolExecution = toolExecution;
            this.reason = reason;
        }

        public ToolExecution toolExecution() {
            return toolExecution;
        }

        public CompensationReason reason() {
            return reason;
        }
    }

    /**
     * The final {@link ChatResponse} of the interaction — the answer. Exactly one is emitted, last, right
     * before the stream completes. Corresponds to {@link TokenStream#onCompleteResponse}.
     */
    final class FinalResponseEvent extends AbstractEvent {

        private final ChatResponse chatResponse;

        public FinalResponseEvent(ChatResponse chatResponse, InvocationContext invocationContext) {
            super(invocationContext);
            this.chatResponse = chatResponse;
        }

        public ChatResponse chatResponse() {
            return chatResponse;
        }
    }
}
