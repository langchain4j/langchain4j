package dev.langchain4j.rag.query.transformer;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.query.Query;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;

/**
 * A {@link QueryTransformer} that leverages a {@link ChatModel} to condense a given {@link Query}
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
            """
                    Read and understand the conversation between the User and the AI. \
                    Then, analyze the new query from the User. \
                    Identify all relevant details, terms, and context from both the conversation and the new query. \
                    Reformulate this query into a clear, concise, and self-contained format suitable for information retrieval.
                    
                    Conversation:
                    {{chatMemory}}
                    
                    User query: {{query}}
                    
                    It is very important that you provide only reformulated query and nothing else! \
                    Do not prepend a query with anything!"""
    );

    protected final PromptTemplate promptTemplate;
    protected final ChatModel chatModel;

    public CompressingQueryTransformer(ChatModel chatModel) {
        this(chatModel, DEFAULT_PROMPT_TEMPLATE);
    }

    public CompressingQueryTransformer(ChatModel chatModel, PromptTemplate promptTemplate) {
        this.chatModel = ensureNotNull(chatModel, "chatModel");
        this.promptTemplate = getOrDefault(promptTemplate, DEFAULT_PROMPT_TEMPLATE);
    }

    public static CompressingQueryTransformerBuilder builder() {
        return new CompressingQueryTransformerBuilder();
    }

    @Override
    public Collection<Query> transform(Query query) {

        List<ChatMessage> chatMemory = query.metadata().chatMemory();
        if (chatMemory.isEmpty()) {
            // no need to compress if there are no previous messages
            return singletonList(query);
        }

        Prompt prompt = createPrompt(query, format(chatMemory));
        String compressedQueryText = chatModel.chat(prompt.text());
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
        if (message instanceof UserMessage userMessage) {
            return "User: " + userMessage.singleText();
        } else if (message instanceof AiMessage aiMessage) {
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

    public static class CompressingQueryTransformerBuilder {
        private ChatModel chatModel;
        private PromptTemplate promptTemplate;

        CompressingQueryTransformerBuilder() {
        }

        public CompressingQueryTransformerBuilder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public CompressingQueryTransformerBuilder promptTemplate(PromptTemplate promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        public CompressingQueryTransformer build() {
            return new CompressingQueryTransformer(this.chatModel, this.promptTemplate);
        }
    }
}
