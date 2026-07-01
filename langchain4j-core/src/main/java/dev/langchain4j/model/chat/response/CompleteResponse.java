package dev.langchain4j.model.chat.response;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;

import java.util.Objects;

/**
 * The terminal {@link StreamingEvent} of a streaming chat: the complete, aggregated {@link ChatResponse}.
 * It is the streaming counterpart of {@link PartialResponse} (a single text chunk) — emitted once, last,
 * after all partial events.
 * <p>
 * Wrapping {@link ChatResponse} (rather than having it implement {@link StreamingEvent} directly) keeps
 * {@link ChatResponse} — which is also returned by the non-streaming {@code ChatModel#chat} /
 * {@code ChatModel#chatAsync} — free of the streaming-event marker.
 *
 * @see PartialResponse
 * @since 1.13.0
 */
@Experimental
@JacocoIgnoreCoverageGenerated
public class CompleteResponse implements StreamingEvent { // TODO name

    private final ChatResponse chatResponse;

    public CompleteResponse(ChatResponse chatResponse) {
        this.chatResponse = ensureNotNull(chatResponse, "chatResponse");
    }

    /**
     * The complete, aggregated chat response.
     */
    public ChatResponse chatResponse() {
        return chatResponse;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        CompleteResponse that = (CompleteResponse) object;
        return Objects.equals(chatResponse, that.chatResponse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatResponse);
    }

    @Override
    public String toString() {
        return "CompleteResponse{" + "chatResponse=" + chatResponse + '}';
    }
}
