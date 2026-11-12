package dev.langchain4j.observability.event;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.observability.api.event.RetrievalAugmentedEvent;
import dev.langchain4j.rag.content.Content;
import java.util.List;

/**
 * Default implementation of {@link RetrievalAugmentedEvent}.
 *
 * @since 1.23.0
 */
@Experimental
public class DefaultRetrievalAugmentedEvent extends AbstractAiServiceEvent implements RetrievalAugmentedEvent {

    private final ChatMessage chatMessage;
    private final List<Content> contents;

    public DefaultRetrievalAugmentedEvent(RetrievalAugmentedEventBuilder builder) {
        super(builder);
        this.chatMessage = ensureNotNull(builder.chatMessage(), "chatMessage");
        this.contents = copy(ensureNotNull(builder.contents(), "contents"));
    }

    @Override
    public ChatMessage chatMessage() {
        return chatMessage;
    }

    @Override
    public List<Content> contents() {
        return contents;
    }
}
