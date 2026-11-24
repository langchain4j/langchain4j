package dev.langchain4j.observability.api.event;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.event.DefaultRetrievalAugmentedEvent;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverListener;
import java.util.List;

/**
 * Invoked once per invocation of an AI Service that uses retrieval-augmented generation (RAG), configured with
 * {@code contentRetriever(...)} or {@code retrievalAugmentor(...)}, after the contents to add to the user message were
 * retrieved, also when nothing was retrieved. It carries the user message that was used for the retrieval (the chat
 * message given to the {@link RetrievalAugmentor}) and the final contents, after all queries, retrievers, aggregation
 * and re-ranking. To observe each
 * {@link ContentRetriever} call separately, with the query it received, use a
 * {@link ContentRetrieverListener}.
 *
 * @since 1.23.0
 */
@Experimental
public interface RetrievalAugmentedEvent extends AiServiceEvent {

    /**
     * Returns the chat message given to the {@link RetrievalAugmentor} (see
     * {@link AugmentationRequest#chatMessage()}), before the retrieved contents were added to it.
     * In AI Services this is the user message. It is the input of retrieval, not the queries sent to the content
     * retrievers: the retrieval augmentor can transform it into one or more different queries first.
     *
     * @return the chat message given to the retrieval augmentor, never {@code null}
     */
    ChatMessage chatMessage();

    /**
     * Returns the retrieved contents: the same contents that an AI Service method returning {@code Result<...>}
     * gives as its sources.
     *
     * @return the retrieved contents, empty if nothing was retrieved, never {@code null}
     */
    List<Content> contents();

    @Override
    default Class<RetrievalAugmentedEvent> eventClass() {
        return RetrievalAugmentedEvent.class;
    }

    @Override
    default RetrievalAugmentedEventBuilder toBuilder() {
        return new RetrievalAugmentedEventBuilder(this);
    }

    static RetrievalAugmentedEventBuilder builder() {
        return new RetrievalAugmentedEventBuilder();
    }

    /**
     * Builder for {@link RetrievalAugmentedEvent} instances.
     */
    class RetrievalAugmentedEventBuilder extends Builder<RetrievalAugmentedEvent> {

        private ChatMessage chatMessage;
        private List<Content> contents;

        protected RetrievalAugmentedEventBuilder() {}

        /**
         * Creates a builder initialized from an existing {@link RetrievalAugmentedEvent}.
         */
        protected RetrievalAugmentedEventBuilder(RetrievalAugmentedEvent src) {
            super(src);
            chatMessage(src.chatMessage());
            contents(src.contents());
        }

        /**
         * Sets the invocation context.
         */
        @Override
        public RetrievalAugmentedEventBuilder invocationContext(InvocationContext invocationContext) {
            return (RetrievalAugmentedEventBuilder) super.invocationContext(invocationContext);
        }

        /**
         * Sets the chat message given to the retrieval augmentor.
         */
        public RetrievalAugmentedEventBuilder chatMessage(ChatMessage chatMessage) {
            this.chatMessage = chatMessage;
            return this;
        }

        /**
         * Sets the retrieved contents.
         */
        public RetrievalAugmentedEventBuilder contents(List<Content> contents) {
            this.contents = contents;
            return this;
        }

        /**
         * Returns the chat message set on this builder, or {@code null} if it is not set.
         */
        public ChatMessage chatMessage() {
            return chatMessage;
        }

        /**
         * Returns the contents set on this builder, or {@code null} if they are not set.
         */
        public List<Content> contents() {
            return contents;
        }

        /**
         * Builds a {@link RetrievalAugmentedEvent}.
         */
        @Override
        public RetrievalAugmentedEvent build() {
            return new DefaultRetrievalAugmentedEvent(this);
        }
    }
}
