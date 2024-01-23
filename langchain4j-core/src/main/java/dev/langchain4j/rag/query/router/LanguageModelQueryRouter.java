package dev.langchain4j.rag.query.router;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.Builder;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.*;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

/**
 * A {@link QueryRouter} that utilizes a {@link ChatLanguageModel} to make a routing decision.
 * <br>
 * Each {@link ContentRetriever} provided in the constructor is accompanied by a description which is
 * provided to the LLM to aid in making a decision on where a {@link Query} should be routed.
 * <br>
 * Refer to {@link #DEFAULT_PROMPT_TEMPLATE} and implementation for more details.
 * <br>
 * <br>
 * Configurable parameters (optional):
 * <br>
 * - {@link #promptTemplate}: The prompt template used to ask the LLM for routing decisions.
 *
 * @see DefaultQueryRouter
 */
public class LanguageModelQueryRouter implements QueryRouter {

    public static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from(
            "Based on the user query, determine the most suitable data source(s) " +
                    "to retrieve relevant information from the following options:\n" +
                    "{{options}}\n" +
                    "It is very important that your answer consists of either a single number " +
                    "or multiple numbers separated by commas and nothing else!\n" +
                    "User query: {{query}}"
    );

    private final ChatLanguageModel chatLanguageModel;
    private final PromptTemplate promptTemplate;
    private final String options;
    private final Map<Integer, ContentRetriever> idToRetriever;

    public LanguageModelQueryRouter(ChatLanguageModel chatLanguageModel,
                                    Map<ContentRetriever, String> retrieverToDescription) {
        this(chatLanguageModel, retrieverToDescription, DEFAULT_PROMPT_TEMPLATE);
    }

    @Builder
    public LanguageModelQueryRouter(ChatLanguageModel chatLanguageModel,
                                    Map<ContentRetriever, String> retrieverToDescription,
                                    PromptTemplate promptTemplate) {
        this.chatLanguageModel = ensureNotNull(chatLanguageModel, "chatLanguageModel");
        ensureNotEmpty(retrieverToDescription, "retrieverToDescription");
        this.promptTemplate = getOrDefault(promptTemplate, DEFAULT_PROMPT_TEMPLATE);

        Map<Integer, ContentRetriever> idToRetriever = new HashMap<>();
        StringBuilder optionsBuilder = new StringBuilder();
        int id = 1;
        for (Map.Entry<ContentRetriever, String> entry : retrieverToDescription.entrySet()) {
            idToRetriever.put(id, ensureNotNull(entry.getKey(), "ContentRetriever"));

            if (id > 1) {
                optionsBuilder.append("\n");
            }
            optionsBuilder.append(id);
            optionsBuilder.append(": ");
            optionsBuilder.append(ensureNotBlank(entry.getValue(), "ContentRetriever description"));

            id++;
        }
        this.idToRetriever = idToRetriever;
        this.options = optionsBuilder.toString();
    }

    @Override
    public Collection<ContentRetriever> route(Query query) {
        Prompt prompt = createPrompt(query);
        String response = chatLanguageModel.generate(prompt.text());
        return parse(response);
    }

    protected Prompt createPrompt(Query query) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("query", query.text());
        variables.put("options", options);
        return promptTemplate.apply(variables);
    }

    protected Collection<ContentRetriever> parse(String choices) {
        return stream(choices.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .map(idToRetriever::get)
                .collect(toList());
    }
}
