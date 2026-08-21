package dev.langchain4j.model.chat.response;

/**
 * Base type for the events emitted by the reactive stream of a {@link dev.langchain4j.model.chat.StreamingChatModel} —
 * the {@code Flow.Publisher<ChatModelStreamingEvent>} returned by
 * {@link dev.langchain4j.model.chat.StreamingChatModel#chat(dev.langchain4j.model.chat.request.ChatRequest)}.
 * <p>
 * This is the low-level, per-model-call vocabulary: the streamed chunks of a single model response
 * ({@link PartialResponse}, {@link PartialThinking}, {@link PartialToolCall}), the assembled {@link CompleteToolCall}s,
 * any provider-specific {@link RawStreamingEvent}, and finally exactly one {@link CompleteResponse} carrying the full
 * {@link ChatResponse}, emitted last right before the stream completes. The higher-level, whole-interaction counterpart
 * (token chunks of every round, the tool-execution lifecycle, RAG content, intermediate vs. final answer) is
 * {@code dev.langchain4j.service.AiServiceStreamingEvent}.
 * <p>
 * The set is intentionally <b>not sealed</b>: new event types may be added over time, so consumers must handle
 * unrecognized subtypes gracefully (e.g. a {@code default} branch in a type switch) rather than assume it is exhaustive.
 *
 * @since 1.19.0
 */
public interface ChatModelStreamingEvent {}
