package dev.langchain4j.chain;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.*;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.retriever.Retriever;
import dev.langchain4j.service.AiServices;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * A chain for conversing with a specified {@link ChatLanguageModel}
 * based on the information retrieved by a specified {@link ContentRetriever}.
 * Includes a default {@link ChatMemory} (a message window with maximum 10 messages), which can be overridden.
 * You can fully customize RAG behavior by providing an instance of a {@link RetrievalAugmentor},
 * such as {@link DefaultRetrievalAugmentor}, or your own custom implementation.
 * <br>
 * It is recommended to use {@link AiServices} instead, as it is more powerful.
 */
public class ConversationalRetrievalChain implements Chain<String, String> {

    private final ChatLanguageModel chatLanguageModel;
    private final ChatMemory chatMemory;
    private final RetrievalAugmentor retrievalAugmentor;

    public ConversationalRetrievalChain(ChatLanguageModel chatLanguageModel,
                                        ChatMemory chatMemory,
                                        ContentRetriever contentRetriever) {
        this(
                chatLanguageModel,
                chatMemory,
                DefaultRetrievalAugmentor.builder()
                        .contentRetriever(contentRetriever)
                        .build()
        );
    }

    public ConversationalRetrievalChain(ChatLanguageModel chatLanguageModel,
                                        ChatMemory chatMemory,
                                        RetrievalAugmentor retrievalAugmentor) {
        this.chatLanguageModel = ensureNotNull(chatLanguageModel, "chatLanguageModel");
        this.chatMemory = getOrDefault(chatMemory, () -> MessageWindowChatMemory.withMaxMessages(10));
        this.retrievalAugmentor = ensureNotNull(retrievalAugmentor, "retrievalAugmentor");
    }

    /**
     * Use another constructor with a new {@link ContentRetriever} instead.
     */
    @Deprecated
    public ConversationalRetrievalChain(ChatLanguageModel chatLanguageModel,
                                        ChatMemory chatMemory,
                                        PromptTemplate promptTemplate,
                                        Retriever<TextSegment> retriever) {
        this(
                chatLanguageModel,
                chatMemory,
                DefaultRetrievalAugmentor.builder()
                        .contentRetriever(retriever.toContentRetriever())
                        .contentInjector(DefaultContentInjector.builder()
                                .promptTemplate(toPromptTemplateWithNewVariableNames(promptTemplate))
                                .build())
                        .build()
        );
    }

    @Override
    public String execute(String query) {

        UserMessage userMessage = UserMessage.from(query);
        Metadata metadata = Metadata.from(userMessage, chatMemory.id(), chatMemory.messages());
        userMessage = retrievalAugmentor.augment(userMessage, metadata);
        chatMemory.add(userMessage);

        AiMessage aiMessage = chatLanguageModel.generate(chatMemory.messages()).content();
        chatMemory.add(aiMessage);
        return aiMessage.text();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ChatLanguageModel chatLanguageModel;
        private ChatMemory chatMemory;
        private RetrievalAugmentor retrievalAugmentor;

        @Deprecated
        private dev.langchain4j.retriever.Retriever<TextSegment> retriever;
        @Deprecated
        private PromptTemplate promptTemplate;

        public Builder chatLanguageModel(ChatLanguageModel chatLanguageModel) {
            this.chatLanguageModel = chatLanguageModel;
            return this;
        }

        public Builder chatMemory(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
            return this;
        }

        public Builder contentRetriever(ContentRetriever contentRetriever) {
            if (contentRetriever != null) {
                this.retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                        .contentRetriever(contentRetriever)
                        .build();
            }
            return this;
        }

        public Builder retrievalAugmentor(RetrievalAugmentor retrievalAugmentor) {
            this.retrievalAugmentor = retrievalAugmentor;
            return this;
        }

        /**
         * Deprecated. Use {@link Builder#contentRetriever(ContentRetriever)} instead.
         */
        @Deprecated
        public Builder retriever(dev.langchain4j.retriever.Retriever<TextSegment> retriever) {
            this.retriever = retriever;
            return this;
        }

        /**
         * Deprecated, Use this instead:<pre>
         * .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
         *     .contentInjector(DefaultContentInjector.builder()
         *         .promptTemplate(promptTemplate)
         *         .build())
         *     .build());
         * </pre>
         */
        @Deprecated
        public Builder promptTemplate(PromptTemplate promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        public ConversationalRetrievalChain build() {

            if (retriever != null) {
                retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                        .contentRetriever(retriever.toContentRetriever())
                        .contentInjector(DefaultContentInjector.builder()
                                .promptTemplate(toPromptTemplateWithNewVariableNames(promptTemplate))
                                .build())
                        .build();
            }

            return new ConversationalRetrievalChain(chatLanguageModel, chatMemory, retrievalAugmentor);
        }
    }

    private static PromptTemplate toPromptTemplateWithNewVariableNames(PromptTemplate oldPromptTemplate) {
        if (oldPromptTemplate != null) {
            return PromptTemplate.from(oldPromptTemplate.template()
                    .replaceAll("\\{\\{question}}", "{{userMessage}}")
                    .replaceAll("\\{\\{information}}", "{{contents}}")
            );
        }

        return PromptTemplate.from(
                "Answer the following question to the best of your ability: {{userMessage}}\n" +
                        "\n" +
                        "Base your answer on the following information:\n" +
                        "{{contents}}"
        );
    }
}
