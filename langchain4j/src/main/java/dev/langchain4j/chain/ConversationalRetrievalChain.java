package dev.langchain4j.chain;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.service.AiServices;

/**
 * A chain for conversing with a specified {@link ChatModel}
 * based on the information retrieved by a specified {@link ContentRetriever}.
 * Includes a default {@link ChatMemory} (a message window with maximum 10 messages), which can be overridden.
 * You can fully customize RAG behavior by providing an instance of a {@link RetrievalAugmentor},
 * such as {@link DefaultRetrievalAugmentor}, or your own custom implementation.
 * <br>
 * Chains are not going to be developed further, it is recommended to use {@link AiServices} instead.
 */
public class ConversationalRetrievalChain implements Chain<String, String> {

    private final ChatModel chatModel;
    private final ChatMemory chatMemory;
    private final RetrievalAugmentor retrievalAugmentor;

    public ConversationalRetrievalChain(ChatModel chatModel, ChatMemory chatMemory, ContentRetriever contentRetriever) {
        this(
                chatModel,
                chatMemory,
                DefaultRetrievalAugmentor.builder()
                        .contentRetriever(contentRetriever)
                        .build());
    }

    public ConversationalRetrievalChain(
            ChatModel chatModel, ChatMemory chatMemory, RetrievalAugmentor retrievalAugmentor) {
        this.chatModel = ensureNotNull(chatModel, "chatModel");
        this.chatMemory = getOrDefault(chatMemory, () -> MessageWindowChatMemory.withMaxMessages(10));
        this.retrievalAugmentor = ensureNotNull(retrievalAugmentor, "retrievalAugmentor");
    }

    @Override
    public String execute(String query) {

        UserMessage userMessage = UserMessage.from(query);
        userMessage = augment(userMessage);
        chatMemory.add(userMessage);

        AiMessage aiMessage = chatModel.chat(chatMemory.messages()).aiMessage();

        chatMemory.add(aiMessage);
        return aiMessage.text();
    }

    private UserMessage augment(UserMessage userMessage) {
        Metadata metadata = Metadata.from(userMessage, chatMemory.id(), chatMemory.messages());

        AugmentationRequest augmentationRequest = new AugmentationRequest(userMessage, metadata);

        AugmentationResult augmentationResult = retrievalAugmentor.augment(augmentationRequest);

        return (UserMessage) augmentationResult.chatMessage();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ChatModel chatModel;
        private ChatMemory chatMemory;
        private RetrievalAugmentor retrievalAugmentor;

        /**
         * Sets the {@link ChatModel} used to generate responses.
         *
         * @param chatModel the chat model
         * @return {@code this}
         */
        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        /**
         * Sets the {@link ChatMemory} used to store conversation history.
         * Defaults to a {@link MessageWindowChatMemory} with a maximum of 10 messages when not set.
         *
         * @param chatMemory the chat memory
         * @return {@code this}
         */
        public Builder chatMemory(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
            return this;
        }

        /**
         * Sets the {@link ContentRetriever} used to fetch relevant content for each query.
         * Wraps the retriever in a {@link DefaultRetrievalAugmentor}. Mutually exclusive with
         * {@link #retrievalAugmentor(RetrievalAugmentor)}.
         *
         * @param contentRetriever the content retriever
         * @return {@code this}
         */
        public Builder contentRetriever(ContentRetriever contentRetriever) {
            if (contentRetriever != null) {
                this.retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                        .contentRetriever(contentRetriever)
                        .build();
            }
            return this;
        }

        /**
         * Sets a custom {@link RetrievalAugmentor} for full control over RAG behavior.
         * Takes precedence over any value set via {@link #contentRetriever(ContentRetriever)}.
         *
         * @param retrievalAugmentor the retrieval augmentor
         * @return {@code this}
         */
        public Builder retrievalAugmentor(RetrievalAugmentor retrievalAugmentor) {
            this.retrievalAugmentor = retrievalAugmentor;
            return this;
        }

        /**
         * Builds the {@link ConversationalRetrievalChain}.
         *
         * @return the configured {@link ConversationalRetrievalChain}
         */
        public ConversationalRetrievalChain build() {
            return new ConversationalRetrievalChain(chatModel, chatMemory, retrievalAugmentor);
        }
    }
}
