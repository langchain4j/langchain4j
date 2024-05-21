package dev.langchain4j.rag.query.transformer;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.query.Query;
import lombok.Builder;

import java.util.*;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;

/**
 * A {@link QueryTransformer} that leverages a {@link ChatLanguageModel} to condense a given {@link Query}
 * along with a chat memory (previous conversation history) into a concise {@link Query}.
 * This is applicable only when a {@link ChatMemory} is in use.
 * Refer to {@link #DEFAULT_PROMPT_TEMPLATE} and implementation for more details.
 * <br>
 * <br>
 * Configurable parameters (optional):
 * <br>
 * - {@link #promptTemplate}: The prompt template used to instruct the LLM to compress the specified {@link Query}.
 *
 * @see DefaultQueryTransformer
 * @see ExpandingQueryTransformer
 */
public class CompressingQueryTransformer implements QueryTransformer {

    public static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from(
            "Read and understand the conversation between the User and the AI. " +
                    "Then, analyze the new query from the User. " +
                    "Identify all relevant details, terms, and context from both the conversation and the new query. " +
                    "Reformulate this query into a clear, concise, and self-contained format suitable for information retrieval.\n" +
                    "\n" +
                    "Conversation:\n" +
                    "{{chatMemory}}\n" +
                    "\n" +
                    "User query: {{query}}\n" +
                    "\n" +
                    "It is very important that you provide only reformulated query and nothing else! " +
                    "Do not prepend a query with anything!"
    );

    protected final PromptTemplate promptTemplate;
    protected final ChatLanguageModel chatLanguageModel;

    public CompressingQueryTransformer(ChatLanguageModel chatLanguageModel) {
        this(chatLanguageModel, DEFAULT_PROMPT_TEMPLATE);
    }

    @Builder
    public CompressingQueryTransformer(ChatLanguageModel chatLanguageModel, PromptTemplate promptTemplate) {
        this.chatLanguageModel = ensureNotNull(chatLanguageModel, "chatLanguageModel");
        this.promptTemplate = getOrDefault(promptTemplate, DEFAULT_PROMPT_TEMPLATE);
    }

    @Override
    public Collection<Query> transform(Query query) {

        List<ChatMessage> chatMemory = query.metadata().chatMemory();
        if (chatMemory.isEmpty()) {
            // no need to compress if there are no previous messages
            return singletonList(query);
        }

        Prompt prompt = createPrompt(query, format(chatMemory));
        String compressedQueryText = chatLanguageModel.generate(prompt.text());
        Query compressedQuery = query.metadata() == null
                ? Query.from(compressedQueryText)
                : Query.from(compressedQueryText, query.metadata());
        return singletonList(compressedQuery);
    }

    protected String format(List<ChatMessage> chatMemory) {
        return chatMemory.stream()
                .map(this::format)
                .filter(Objects::nonNull)
                .collect(joining("\n"));
    }

    protected String format(ChatMessage message) {
        if (message instanceof UserMessage) {
            return "User: " + message.text();
        } else if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            if (aiMessage.hasToolExecutionRequests()) {
                return null;
            }
            return "AI: " + aiMessage.text();
        } else {
            return null;
        }
    }

    protected Prompt createPrompt(Query query, String chatMemory) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("query", query.text());
        variables.put("chatMemory", chatMemory);
        return promptTemplate.apply(variables);
    }
}
