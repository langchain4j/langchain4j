package dev.langchain4j.chain;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.retriever.Retriever;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.TokenStreamFactory;
import dev.langchain4j.service.context.DefaultStreamingPromptContext;
import dev.langchain4j.service.context.StreamingPromptTemplateContext;
import lombok.Builder;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.joining;

@Getter
public class StreamingConversationalRetrievalChain {

    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from(
            "Answer the following question to the best of your ability: {{question}}\n" +
                    "\n" +
                    "Base your answer on the following information:\n" +
                    "{{information}}");

    private final StreamingPromptTemplateContext context;

    @Builder
    public StreamingConversationalRetrievalChain(StreamingChatLanguageModel streamingChatLanguageModel,
                                                 ChatMemory chatMemory,
                                                 PromptTemplate promptTemplate,
                                                 Retriever<TextSegment> retriever) {
        ensureNotNull(streamingChatLanguageModel, "streamingChatLanguageModel");
        chatMemory = chatMemory == null ? MessageWindowChatMemory.withCapacity(10) : chatMemory;
        promptTemplate = promptTemplate == null ? DEFAULT_PROMPT_TEMPLATE : promptTemplate;
        ensureNotNull(retriever, "retriever");
        this.context = DefaultStreamingPromptContext.builder()
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .chatMemory(chatMemory)
                .promptTemplate(promptTemplate)
                .retriever(retriever)
                .build();
    }

    public TokenStream execute(String question) {
        question = ensureNotBlank(question, "question");


        List<TextSegment> relevantSegments = context.getRetriever().findRelevant(question);

        Map<String, Object> variables = new HashMap<>();
        variables.put("question", question);
        variables.put("information", format(relevantSegments));

        UserMessage userMessage = context.getPromptTemplate().apply(variables).toUserMessage();

        context.getChatMemory().add(userMessage);

        return TokenStreamFactory.of(context.getChatMemory().messages(), context);
    }


    private static String format(List<TextSegment> relevantSegments) {
        return relevantSegments.stream()
                .map(TextSegment::text)
                .map(segment -> "..." + segment + "...")
                .collect(joining("\n\n"));
    }

}
