package dev.langchain4j.rag.query.router;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.*;
import static dev.langchain4j.rag.query.router.LanguageModelQueryRouter.FallbackStrategy.DO_NOT_ROUTE;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

/**
 * A {@link QueryRouter} that utilizes a {@link ChatLanguageModel} to make a routing decision.
 * <br>
 * Each {@link ContentRetriever} provided in the constructor should be accompanied by a description which
 * should help the LLM to decide where to route a {@link Query}.
 * <br>
 * Refer to {@link #DEFAULT_PROMPT_TEMPLATE} and implementation for more details.
 * <br>
 * <br>
 * Configurable parameters (optional):
 * <br>
 * - {@link #promptTemplate}: The prompt template used to ask the LLM for routing decisions.
 * <br>
 * - {@link #fallbackStrategy}: The strategy applied if the call to the LLM fails of if LLM does not return a valid response.
 * Please check {@link FallbackStrategy} for more details. Default value: {@link FallbackStrategy#DO_NOT_ROUTE}
 *
 * @see DefaultQueryRouter
 */
public class LanguageModelQueryRouter implements QueryRouter {

    private static final Logger log = LoggerFactory.getLogger(LanguageModelQueryRouter.class);

    public static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from(
            "Based on the user query, determine the most suitable data source(s) " +
                    "to retrieve relevant information from the following options:\n" +
                    "{{options}}\n" +
                    "It is very important that your answer consists of either a single number " +
                    "or multiple numbers separated by commas and nothing else!\n" +
                    "User query: {{query}}"
    );

    protected final ChatLanguageModel chatLanguageModel;
    protected final PromptTemplate promptTemplate;
    protected final String options;
    protected final Map<Integer, ContentRetriever> idToRetriever;
    protected final FallbackStrategy fallbackStrategy;

    public LanguageModelQueryRouter(ChatLanguageModel chatLanguageModel,
                                    Map<ContentRetriever, String> retrieverToDescription) {
        this(chatLanguageModel, retrieverToDescription, DEFAULT_PROMPT_TEMPLATE, DO_NOT_ROUTE);
    }

    @Builder
    public LanguageModelQueryRouter(ChatLanguageModel chatLanguageModel,
                                    Map<ContentRetriever, String> retrieverToDescription,
                                    PromptTemplate promptTemplate,
                                    FallbackStrategy fallbackStrategy) {
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
        this.fallbackStrategy = getOrDefault(fallbackStrategy, DO_NOT_ROUTE);
    }

    @Override
    public Collection<ContentRetriever> route(Query query) {
        Prompt prompt = createPrompt(query);
        try {
            String response = chatLanguageModel.generate(prompt.text());
            return parse(response);
        } catch (Exception e) {
            log.warn("Failed to route query '{}'", query.text(), e);
            return fallback(query, e);
        }
    }

    protected Collection<ContentRetriever> fallback(Query query, Exception e) {
        switch (fallbackStrategy) {
            case DO_NOT_ROUTE:
                log.debug("Fallback: query '{}' will not be routed", query.text());
                return emptyList();
            case ROUTE_TO_ALL:
                log.debug("Fallback: query '{}' will be routed to all available content retrievers", query.text());
                return new ArrayList<>(idToRetriever.values());
            case FAIL:
            default:
                throw new RuntimeException(e);
        }
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

    /**
     * Strategy applied if the call to the LLM fails of if LLM does not return a valid response.
     * It could be because it was formatted improperly, or it is unclear where to route.
     */
    public enum FallbackStrategy {

        /**
         * In this case, the {@link Query} will not be routed to any {@link ContentRetriever},
         * thus skipping the RAG flow. No content will be appended to the original {@link UserMessage}.
         */
        DO_NOT_ROUTE,

        /**
         * In this case, the {@link Query} will be routed to all {@link ContentRetriever}s.
         */
        ROUTE_TO_ALL,

        /**
         * In this case, an original exception will be re-thrown, and the RAG flow will fail.
         */
        FAIL
    }
}
