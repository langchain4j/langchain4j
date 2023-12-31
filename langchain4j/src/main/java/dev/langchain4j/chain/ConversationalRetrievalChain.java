package dev.langchain4j.chain;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.retriever.Retriever;
import lombok.Builder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.joining;

/**
 * A chain for interacting with a specified {@link ChatLanguageModel} based on the information provided by a specified {@link Retriever}.
 * Includes a default {@link PromptTemplate}, which can be overridden.
 * Includes a default {@link ChatMemory} (a message window with maximum 10 messages), which can be overridden.
 */
public class ConversationalRetrievalChain implements Chain<String, String> {

    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from(
            "Answer the following question to the best of your ability: {{question}}\n" +
                    "\n" +
                    "Base your answer on the following information:\n" +
                    "{{information}}");

    private final ChatLanguageModel chatLanguageModel;
    private final ChatMemory chatMemory;
    private final PromptTemplate promptTemplate;
    private final Retriever<TextSegment> retriever;

    private final List<String> metadata;

    @Builder
    public ConversationalRetrievalChain(ChatLanguageModel chatLanguageModel,
                                        ChatMemory chatMemory,
                                        PromptTemplate promptTemplate,
                                        Retriever<TextSegment> retriever,
                                        List<String> metadata) {
        this.chatLanguageModel = ensureNotNull(chatLanguageModel, "chatLanguageModel");
        this.chatMemory = chatMemory == null ? MessageWindowChatMemory.withMaxMessages(10) : chatMemory;
        this.promptTemplate = promptTemplate == null ? DEFAULT_PROMPT_TEMPLATE : promptTemplate;
        this.retriever = ensureNotNull(retriever, "retriever");
        this.metadata = metadata;
    }

    @Override
    public String execute(String question) {

        question = ensureNotBlank(question, "question");

        List<TextSegment> relevantSegments = retriever.findRelevant(question);

        Map<String, Object> variables = new HashMap<>();
        variables.put("question", question);
        variables.put("information", format(relevantSegments, metadata));

        UserMessage userMessage = promptTemplate.apply(variables).toUserMessage();

        chatMemory.add(userMessage);

        AiMessage aiMessage = chatLanguageModel.generate(chatMemory.messages()).content();

        chatMemory.add(aiMessage);

        return aiMessage.text();
    }

    private static String format(List<TextSegment> relevantSegments, List<String> metadata) {
        return relevantSegments.stream()
                .map(textSegment -> textSegmentToText(metadata, textSegment))
                .map(segment -> "..." + segment + "...")
                .collect(joining("\n\n"));
    }

    private static String textSegmentToText(List<String> metadata, TextSegment textSegment) {
        if (metadata == null) {
            return textSegment.text();
        }

        StringBuilder formattedText = new StringBuilder();

        for (String metadataKey : metadata) {
            String metadataContent = textSegment.metadata(metadataKey);
            if (metadataContent != null) {
                formattedText.append(metadataKey).append(": ").append(metadataContent).append("\n");
            }
        }

        if (!formattedText.toString().isEmpty()) {
            formattedText.append("Content: ");
        }

        formattedText.append(textSegment.text());

        return formattedText.toString();
    }
}
