package dev.langchain4j.service.tool.search.vector;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.ToolSearchResult;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.toBase64;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * A {@link ToolSearchStrategy} that uses vector similarity search
 * to find relevant tools based on the semantic meaning of their names and descriptions.
 * <p>
 * NOTE: It is important that the tool description ({@link ToolSpecification#description()})
 * is present and comprehensive to ensure that vector search performs effectively.
 * <p>
 * NOTE:
 * By default, embeddings of tool descriptions are cached (since they rarely change).
 * You can disable this by setting {@link Builder#cacheEmbeddings(Boolean)} to {@code false}.
 * The embedding of a query is never cached.
 * The cache is never cleared automatically, as the risk of a memory leak is low:
 * the number of tools in an application is usually limited and does not grow over time.
 * The cache can be cleared manually by calling {@link #clearEmbeddingsCache()}.
 */
@Experimental
public class VectorToolSearchStrategy implements ToolSearchStrategy {

    private static final String DEFAULT_TOOL_NAME = "tool_search_tool";
    private static final String DEFAULT_TOOL_DESCRIPTION = "Finds available tools using semantic vector search";
    private static final String DEFAULT_TOOL_ARGUMENT_NAME = "query";
    private static final String DEFAULT_TOOL_ARGUMENT_DESCRIPTION = "Natural language query describing desired tool";
    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final double DEFAULT_MIN_SCORE = 0.0;
    private static final Function<List<String>, String> DEFAULT_TOOL_RESULT_MESSAGE_TEXT_PROVIDER = foundToolNames -> {
        if (foundToolNames.isEmpty()) {
            return "No matching tools found";
        } else {
            return "Tools found: " + String.join(", ", foundToolNames);
        }
    };
    private static final String METADATA_TOOL_NAME = "toolName";

    private final ToolSpecification toolSearchTool;
    private final EmbeddingModel embeddingModel;
    private final int maxResults;
    private final double minScore;
    private final String toolArgumentName;
    private final boolean throwToolArgumentsExceptions;
    private final Function<List<String>, String> toolResultMessageTextProvider;

    public VectorToolSearchStrategy(EmbeddingModel embeddingModel) {
        this(builder().embeddingModel(embeddingModel));
    }

    public VectorToolSearchStrategy(Builder builder) {
        Boolean cacheEmbeddings = getOrDefault(builder.cacheEmbeddings, true);
        if (cacheEmbeddings) {
            this.embeddingModel = ensureNotNull(new ToolCachingEmbeddingModel(builder.embeddingModel), "embeddingModel");
        } else {
            this.embeddingModel = ensureNotNull(builder.embeddingModel, "embeddingModel");
        }
        this.toolArgumentName = getOrDefault(builder.toolArgumentName, DEFAULT_TOOL_ARGUMENT_NAME);

        this.toolSearchTool = ToolSpecification.builder()
                .name(getOrDefault(builder.toolName, DEFAULT_TOOL_NAME))
                .description(getOrDefault(builder.toolDescription, DEFAULT_TOOL_DESCRIPTION))
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(toolArgumentName, getOrDefault(builder.toolArgumentDescription, DEFAULT_TOOL_ARGUMENT_DESCRIPTION))
                        .required(toolArgumentName)
                        .build())
                .build();

        this.maxResults = getOrDefault(builder.maxResults, DEFAULT_MAX_RESULTS);
        this.minScore = getOrDefault(builder.minScore, DEFAULT_MIN_SCORE);
        this.throwToolArgumentsExceptions = getOrDefault(builder.throwToolArgumentsExceptions, false);
        this.toolResultMessageTextProvider = getOrDefault(builder.toolResultMessageTextProvider, DEFAULT_TOOL_RESULT_MESSAGE_TEXT_PROVIDER);
    }

    @Override
    public List<ToolSpecification> getToolSearchTools(InvocationContext context) {
        return List.of(toolSearchTool);
    }

    @Override
    public ToolSearchResult search(ToolSearchRequest request) {

        String query = extractQuery(request.toolExecutionRequest().arguments());

        List<TextSegment> segments = new ArrayList<>();
        segments.add(TextSegment.from(query));

        request.availableTools().stream()
                .map(tool -> {
                    String text = format(tool);
                    Metadata metadata = Metadata.from(METADATA_TOOL_NAME, tool.name());
                    return TextSegment.from(text, metadata);
                })
                .forEach(segments::add);

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        store.addAll(embeddings.subList(1, embeddings.size()), segments.subList(1, segments.size()));

        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .query(query)
                .queryEmbedding(embeddings.get(0))
                .maxResults(maxResults)
                .minScore(minScore)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = store.search(embeddingSearchRequest);

        List<String> toolNames = searchResult.matches().stream()
                .map(match -> (String) match.embedded().metadata().getString(METADATA_TOOL_NAME))
                .toList();

        String toolResultMessageText = toolResultMessageTextProvider.apply(toolNames);

        return new ToolSearchResult(toolNames, toolResultMessageText);
    }

    private String extractQuery(String argumentsJson) {
        Map<String, Object> map = parseMap(argumentsJson);

        if (isNullOrEmpty(map) || !map.containsKey(toolArgumentName)) {
            String message = "Missing required tool argument '%s'".formatted(toolArgumentName);
            throwException(message, null);
        }

        return map.get(toolArgumentName).toString();
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return Json.fromJson(json, Map.class);
        } catch (Exception e) {
            String message = "Failed to parse tool search arguments: '%s' (base64: '%s')".formatted(json, toBase64(json));
            throwException(message, e);
            return null; // unreachable
        }
    }

    protected String format(ToolSpecification tool) {
        if (isNullOrBlank(tool.description())) {
            return tool.name();
        } else {
            return tool.name() + ": " + tool.description();
        }
    }

    private void throwException(String message, Exception e) {
        if (throwToolArgumentsExceptions) {
            throw e == null
                    ? new ToolArgumentsException(message)
                    : new ToolArgumentsException(message, e);
        } else {
            throw e == null
                    ? new ToolExecutionException(message)
                    : new ToolExecutionException(message, e);
        }
    }

    public void clearEmbeddingsCache() {
        if (embeddingModel instanceof ToolCachingEmbeddingModel cachingEmbeddingModel) {
            cachingEmbeddingModel.clearCache();
        } else {
            throw new IllegalStateException("Not caching embeddings, nothing to clear");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private EmbeddingModel embeddingModel;
        private Integer maxResults;
        private Double minScore;
        private String toolName;
        private String toolDescription;
        private String toolArgumentName;
        private String toolArgumentDescription;
        private Boolean throwToolArgumentsExceptions;
        private Boolean cacheEmbeddings;
        private Function<List<String>, String> toolResultMessageTextProvider;

        /**
         * Sets the {@link EmbeddingModel} used to generate embeddings for the query
         * and available tools.
         * <p>
         * This property is required and has no default value.
         */
        public Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        /**
         * Sets the maximum number of tools to return from the vector similarity search.
         * <p>
         * Default value is {@value VectorToolSearchStrategy#DEFAULT_MAX_RESULTS}.
         */
        public Builder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /**
         * Sets the name of the tool that performs the tool search.
         * <p>
         * Default value is {@value VectorToolSearchStrategy#DEFAULT_TOOL_NAME}.
         */
        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        /**
         * Sets the description of the tool that performs the tool search.
         * <p>
         * Default value is {@value VectorToolSearchStrategy#DEFAULT_TOOL_DESCRIPTION}.
         */
        public Builder toolDescription(String toolDescription) {
            this.toolDescription = toolDescription;
            return this;
        }

        /**
         * Sets the name of the tool argument that contains the natural language query.
         * <p>
         * Default value is {@value VectorToolSearchStrategy#DEFAULT_TOOL_ARGUMENT_NAME}.
         */
        public Builder toolArgumentName(String toolArgumentName) {
            this.toolArgumentName = toolArgumentName;
            return this;
        }

        /**
         * Sets the description of the tool argument that contains the natural language query.
         * <p>
         * Default value is {@value VectorToolSearchStrategy#DEFAULT_TOOL_ARGUMENT_DESCRIPTION}.
         */
        public Builder toolArgumentDescription(String toolArgumentDescription) {
            this.toolArgumentDescription = toolArgumentDescription;
            return this;
        }

        /**
         * Controls which exception type is thrown when tool arguments
         * are missing, invalid, or cannot be parsed.
         * <p>
         * Although all errors produced by this tool are argument-related,
         * this strategy throws {@link ToolExecutionException} by default
         * instead of {@link ToolArgumentsException}.
         * <p>
         * The reason is historical: by default, AI Services fail fast when
         * a {@link ToolArgumentsException} is thrown, whereas
         * {@link ToolExecutionException} allows the error message to be
         * returned to the LLM. For this tool, returning the error message
         * to the LLM is usually the desired behavior.
         * <p>
         * If this flag is set to {@code true}, {@link ToolArgumentsException}
         * will be thrown instead.
         *
         * @param throwToolArgumentsExceptions whether to throw {@link ToolArgumentsException}
         * @return this builder
         */
        public Builder throwToolArgumentsExceptions(Boolean throwToolArgumentsExceptions) {
            this.throwToolArgumentsExceptions = throwToolArgumentsExceptions;
            return this;
        }

        /**
         * Controls whether embeddings generated by the embedding model are cached.
         * <p>
         * Default value is {@code true}.
         */
        public Builder cacheEmbeddings(Boolean cacheEmbeddings) {
            this.cacheEmbeddings = cacheEmbeddings;
            return this;
        }

        /**
         * Sets a function that produces a human-readable message describing
         * the tool search result, based on the list of found tool names.
         * <p>
         * By default, returns:
         * <ul>
         *   <li>{@code "No matching tools found"} when no tools are found</li>
         *   <li>{@code "Tools found: tool_1, tool_2, ..."} otherwise</li>
         * </ul>
         */
        public Builder toolResultMessageTextProvider(Function<List<String>, String> toolResultMessageTextProvider) {
            this.toolResultMessageTextProvider = toolResultMessageTextProvider;
            return this;
        }

        public VectorToolSearchStrategy build() {
            return new VectorToolSearchStrategy(this);
        }
    }
}
