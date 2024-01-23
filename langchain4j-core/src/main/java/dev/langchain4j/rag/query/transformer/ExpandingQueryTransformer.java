package dev.langchain4j.rag.query.transformer;

import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.query.Query;
import lombok.Builder;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

/**
 * A {@link QueryTransformer} that utilizes a {@link ChatLanguageModel} to expand a given {@link Query}.
 * <br>
 * Refer to {@link #DEFAULT_PROMPT_TEMPLATE} and implementation for more details.
 * <br>
 * <br>
 * Configurable parameters (optional):
 * <br>
 * - {@link #promptTemplate}: The prompt template used to instruct the LLM to expand the provided {@link Query}.
 * <br>
 * - {@link #n}: The number of {@link Query}s to generate. Default value is 3.
 *
 * @see DefaultQueryTransformer
 * @see CompressingQueryTransformer
 */
public class ExpandingQueryTransformer implements QueryTransformer {

    public static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from(
            "Generate {{n}} different versions of a provided user query. " +
                    "Each version should be worded differently, using synonyms or alternative sentence structures, " +
                    "but they should all retain the original meaning. " +
                    "These versions will be used to retrieve relevant documents. " +
                    "It is very important to provide each query version on a separate line, " +
                    "without enumerations, hyphens, or any additional formatting!\n" +
                    "User query: {{query}}"
    );
    public static final int DEFAULT_N = 3;

    private final ChatLanguageModel chatLanguageModel;
    private final PromptTemplate promptTemplate;
    private final int n;

    public ExpandingQueryTransformer(ChatLanguageModel chatLanguageModel) {
        this(chatLanguageModel, DEFAULT_PROMPT_TEMPLATE, DEFAULT_N);
    }

    public ExpandingQueryTransformer(ChatLanguageModel chatLanguageModel, int n) {
        this(chatLanguageModel, DEFAULT_PROMPT_TEMPLATE, n);
    }

    public ExpandingQueryTransformer(ChatLanguageModel chatLanguageModel, PromptTemplate promptTemplate) {
        this(chatLanguageModel, ensureNotNull(promptTemplate, "promptTemplate"), DEFAULT_N);
    }

    @Builder
    public ExpandingQueryTransformer(ChatLanguageModel chatLanguageModel, PromptTemplate promptTemplate, Integer n) {
        this.chatLanguageModel = ensureNotNull(chatLanguageModel, "chatLanguageModel");
        this.promptTemplate = getOrDefault(promptTemplate, DEFAULT_PROMPT_TEMPLATE);
        this.n = ensureGreaterThanZero(getOrDefault(n, DEFAULT_N), "n");
    }

    @Override
    public Collection<Query> transform(Query query) {
        Prompt prompt = createPrompt(query);
        String response = chatLanguageModel.generate(prompt.text());
        List<Query> queries = parse(response);
        return queries;
    }

    protected Prompt createPrompt(Query query) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("query", query.text());
        variables.put("n", n);
        return promptTemplate.apply(variables);
    }

    protected List<Query> parse(String queries) {
        return stream(queries.split("\n"))
                .filter(Utils::isNotNullOrBlank)
                .map(Query::from)
                .collect(toList());
    }
}
